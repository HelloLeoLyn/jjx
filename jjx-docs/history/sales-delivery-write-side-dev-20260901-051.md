# 发货单写入侧补齐：单据+签收+打印（dev-20260901-051）

- 任务来源：sys_task dev-20260901-051（2026-09-01 登记）
- 依据：jjx-docs/analysis/sales-module-closure-analysis-20260831.md（第二节硬断链）+ print-system-analysis-20260831.md（P1 送货单缺打印页）

## 一、根因

1. `SalesDeliveryController` 只有 4 个 GET（分页/详情/by-order/export-pdf 空壳），零写入。
2. `OrderStatusServiceImpl.shipOrder(:527)` 发货时直接发 `order.delivering` 事件 → `InventoryEventBridge.onSalesDelivery` 创建库存出库单，**全程不写 sales_delivery 表**（表永远 0 行）。
3. 无发货单 = 无客户签收凭证；`SalesDeliveryVO` 的 receiverName/receiverPhone/receiveTime/receiveRemark/deliveryStatus 5 个签收字段无任何写入路径。
4. 订单跟踪页 `views/sales/tracking/index.vue:539,555` 引用 deliveryApi（listByOrderId / exportPdf 空壳），永远无数据/打印失败。
5. sys_menu 218（发货管理，parent 13，perms sales:delivery:view）component/route_name 均为 NULL，无页面。

## 二、设计决策（已定，不要改）

1. **整单发货，不加明细表**：发货单只存头信息，明细实时读订单明细（sales_order_product）。分批发货是 P3（backlog 已有），本任务不做。
2. **发货动作从订单发起**：不提供独立 POST 新建发货单（避免双入口）。订单页"发货"按钮 → 弹窗填交货信息 → `PUT /sales/orders/{orderId}/status/ship`（body 带交货信息）→ 后端**先建发货单，再发 order.delivering 事件出库**。发货单创建失败则整个发货失败（保证凭证先行）。
3. **打印走前端 A4Canvas 路线**（现有 20 个打印页同款），**不实现后端 PDF**。exportPdf 空壳接口保留不动。
4. **不加按钮级权限**（用户后续自行设置），沿用 sales:delivery:view。
5. deliveryStatus 枚举（SalesDeliveryQueryDTO:26）：1待发货 2已发货 3运输中 4已签收 5已拒收。建单时默认 2。

## 三、后端改动

### 3.1 `SalesDeliveryController.java`（controller/）新增 2 个端点

- `PUT /sales/deliveries/{deliveryId}/receive`（签收）
  - body：receiverName / receiverPhone / receiveRemark（均可空，receiverName 建议非空校验）
  - 逻辑：校验存在 + deliveryStatus != 4（已签收）→ 写 receiverName/receiverPhone/receiveRemark/receiveTime=NOW/receiveBy=当前用户id/receiveName=当前用户姓名/deliveryStatus=4
  - 加 `@Log(module = "销售发货", businessType = BusinessType.UPDATE, bizType = "'sales_delivery'", bizId = "#deliveryId")`（照抄本模块其他 @Log 的写法，含 detail 可选）
- 不需要 POST create（发货单由 ship 创建）。4 个 GET 保持不动。

### 3.2 `OrderStatusServiceImpl.java` 改造 shipOrder

- 现状：`:527 public void shipOrder(Long orderId)`，`@Event("order.delivering", bizId="#orderId", bizType="'order'")`，校验仅 IN_PRODUCTION 可发货（:527 附近，保持现有校验不变）。
- 改为：`shipOrder(Long orderId, SalesDelivery delivery)`（delivery 可空，兼容旧调用）：
  1. 现有状态校验不变；
  2. 用订单 + 客户信息组装 SalesDelivery：deliveryNo 生成（参照 SalesInvoiceServiceImpl / SalesReceiptServiceImpl 的单号生成方式，biz_no_rule 如有 sales_delivery 规则则用之，否则照抄发票单号模式）、orderId、customerId/customerName（从订单带出）、deliveryDate（默认今天）、deliveryStatus=2、totalQuantity/totalAmount 从订单汇总、body 传入的 deliveryMethod/contactPerson/contactPhone/deliveryAddress/carrier/trackingNo/remark/deliveryDate 直接采用；
  3. 插入 sales_delivery，**插入失败则抛异常中止发货**；
  4. 原有 @Event 逻辑保持不变（事件在方法上，自动触发）。
- 依赖注入：OrderStatusServiceImpl 新增 `SalesDeliveryService`（或 SalesDeliveryMapper + 单号服务）依赖 → **constructor 变化会破坏现有测试**，必须同步更新受影响的测试类（git grep 找出 `new OrderStatusServiceImpl(` 或 `@InjectMocks` 它的测试）。

### 3.3 单号生成

参照现有模块（sales_invoice 或 sales_receipt）的 deliveryNo 生成实现，保持一致风格。若 biz_no_rule 无 sales_delivery 规则，用与发票一致的规则生成（前缀 DL 或类似，与现有单据区分）。

## 四、前端改动

### 4.1 新建 `jjx-web/src/views/sales/delivery/index.vue`（发货单列表页）

- 筛选：deliveryNo / customerName / deliveryStatus（下拉 1-5）/ deliveryDateStart / deliveryDateEnd（对应 SalesDeliveryQueryDTO）
- 列表列：单号 / 订单号 / 客户 / 交货方式 / 发货日期 / 发货状态 tag / 签收人 / 签收时间 / 操作（详情、签收、打印）
- 操作：
  - 详情：弹窗或抽屉，显示头信息 + 订单明细（调订单明细接口，参照 tracking 页的取数）+ 签收信息
  - 签收：弹窗填 receiverName / receiverPhone / receiveRemark → `PUT /sales/deliveries/{id}/receive`，成功后刷新并提示
  - 打印：跳转 `/sales/delivery/print?deliveryId=xxx`
- 状态文案用枚举：检查 jjx-web/src/enums/ 下是否已有 sales delivery 状态枚举，有则复用，无则在本页顶部建一个 `DeliveryStatusEnum` 文件（**建在 src/enums/sales/ 下，不许建页面内 STATUS_MAP**，遵守 AGENTS.md）。

### 4.2 新建 `jjx-web/src/views/sales/delivery/print.vue`（送货单打印页）

- 参照 `views/sales/invoice/print.vue` + `components/A4Canvas` + `components/PrintCompanyHeader`
- 版式：公司抬头 + "送 货 单"标题 + 单号/日期/客户/收货地址/联系人 + 明细表（产品/规格/数量/单价/金额，数据取订单明细）+ 合计金额 + 签收签名区（收货人/日期，已签收则回显 receiverName/receiveTime）
- 数据：`deliveryApi.getById(deliveryId)`（路由参数）+ 订单明细接口（参照 invoice/print.vue 的明细取数方式）
- 打印留痕：调 `createQualityTemplatePrintLog(26)`（api/production/qualityTemplate.ts 已有该函数；26 = quality_template_registry 里 record_no=JJX-QR-026 送货单 的 id，加注释说明）
- 路由注册：参照 sales/invoice/print.vue 在 `jjx-web/src/router/index.ts` 的注册方式（独立静态路由，路径 `/sales/delivery/print`）

### 4.3 订单页发货改造 `jjx-web/src/views/sales/order/index.vue`

- 现状 `:709`：`await orderStatusApi.shipOrder(row.orderId)` 无参直接调
- 改为：点击发货 → 弹窗（交货方式/收货人/收货电话/收货地址/承运商/物流单号/备注/发货日期）→ 确认后 `orderStatusApi.shipOrder(orderId, body)` 带 body 提交；失败提示后端错误信息
- `jjx-web/src/api/sales/orderStatus.ts:49` shipOrder 签名加可选 body 参数

### 4.4 订单跟踪页 `jjx-web/src/views/sales/tracking/index.vue`

- `:555` 打印按钮现在调 `deliveryApi.exportPdf`（空壳）→ 改为跳转 `/sales/delivery/print?deliveryId=row.deliveryId`

### 4.5 菜单挂载

- migration SQL（见第五节）给 menu 218 补 route_name='SalesDelivery' + component='views/sales/delivery/index.vue'
- 前端 RouterHelper 按 sys_menu 动态生成路由，无需手写路由（列表页）；print.vue 是静态路由（4.2）

## 五、迁移 SQL（新文件 jjx-docs/sql/migrations/26_sales_delivery_write_side.sql）

```sql
-- dev-20260901-051 发货单写入侧：挂载发货管理菜单页面
-- 幂等：component 为 NULL 才更新
UPDATE sys_menu SET route_name = 'SalesDelivery', component = 'views/sales/delivery/index.vue'
WHERE menu_id = 218 AND component IS NULL;
```

- 无表结构变更（不加明细表、不加列）。
- **Codex 沙箱连不上 MySQL：只写这个 SQL 文件，不要执行**（由我在外面执行并验证幂等）。

## 六、已知风险（必须遵守）

1. **工作区有用户 WIP，不要碰**：`git status` 里被删除/修改的测试文件（如 OrderStatusReviewFlowTest.java、OperLogAspectTest.java、Inventory*InvariantTest.java 等）和 `jjx-docs/analysis/` 下两个 20260831 分析文档都是用户的东西。只改本 spec 列出的文件。
2. **不要 git commit**。
3. OrderStatusServiceImpl constructor 变化 → 更新受影响测试的构造调用和 stubbing（照抄新依赖的 mock），跑**受影响的测试类**而不是只跑自己新写的。
4. shipOrder 的 body 参数 required=false，保持旧调用兼容。
5. 状态文案/枚举遵守 AGENTS.md：不许页面内 STATUS_MAP；前端状态枚举放 src/enums/sales/ 下。
6. 前端 `npm run validate` 若报错来自用户 WIP 文件（order.ts / OrderForm.vue 等），过滤掉，**保证你自己改的文件零错误**即可，报告里列出剩余错误归属。

## 七、验证（Codex 自测 + 我外面验证）

Codex 内：
1. `cd jjx-server && mvn -o clean test-compile`（必须含 clean）
2. 受影响的测试类：`mvn -o clean test -Dtest=<受影响类> -DfailIfNoTests=false`（类名在 3.2 里 git grep 找出）
3. `cd jjx-web && npm run validate`（按风险 6 过滤用户 WIP 错误）

我外面：
4. 应用 migration + 幂等证明（跑两遍，diff）
5. `scripts/check-menu-integrity.sh` 三查（菜单完整性）
6. git diff review

## 八、明确不做

- 分批发货 / 发货单明细表（P3 backlog）
- 删除、取消发货单
- exportPdf 后端 PDF 实现（保留空壳，前端走 A4Canvas）
- 按钮级权限（用户后续自设）
- 独立 POST 新建发货单入口（发货从订单发起，见设计决策 2）
