# JJX ERP 销售模块业务闭环分析报告

- **报告日期**：2026-08-31
- **范围**：销售模块 19 张表 / 11 个 Controller / 37 条事件配置 / 38 个前端页面
- **数据来源**：`jjx_erp_db` 实查 + `jjx-server` / `jjx-web` 代码实扫

---

## 一、结论

**销售模块不闭环。主流程存在 1 处硬断链、3 个环节完全缺失、2 处回写缺失。**

设计层面的完整度很高（37 条事件全启用、状态机 10 态齐备、跨模块联动通），但**落地存在明显空洞**，且**整条主流程从未跑通过一次真实数据**（17 张业务表 0 行）。

| 判定项 | 结果 |
|---|---|
| 询价 → 报价 → 订单 主链 | ✅ 闭环 |
| 报价 → 样品 → 转量产 支链 | ✅ 闭环（最完整） |
| 订单 → 生产工单 → 回写订单状态 | ✅ 闭环 |
| 订单 → 发货 → 库存出库 | ⚠️ 半闭环（跳过发货单据） |
| **发货单据本身** | ❌ **断链，无法创建** |
| 收款 → 订单付款状态 | ❌ 无回写 |
| 退货 / 退款 | ❌ 完全缺失 |
| 销售合同 | ❌ 死表 |
| 销售业绩 | ❌ 死表 |

---

## 二、硬断链：发货单只读，系统内无法创建

这是本次分析发现的**最严重问题**，且与打印分析报告的结论相互印证。

### 2.1 事实

**后端 `SalesDeliveryController` 全部端点（4 个，全是 GET）：**

| 行号 | 端点 | 类型 |
|---|---|---|
| 29 | `GET /sales/deliveries` | 分页查询 |
| 36 | `GET /sales/deliveries/{deliveryId}` | 详情 |
| 44 | `GET /sales/deliveries/by-order/{orderId}` | 按订单查 |
| 51 | `GET /sales/deliveries/export-pdf/{deliveryId}` | PDF 导出（**空壳**） |

**零 POST / 零 PUT / 零 DELETE。**

**`SalesDeliveryServiceImpl` 全部方法（4 个）：**
`pageQuery` / `getById` / `listByOrderId` / `exportPdf` —— **纯只读服务**。

### 2.2 导致的后果

**发货动作实际绕过了发货单**。`OrderStatusServiceImpl:527 shipOrder()` 的真实行为：

```
shipOrder(orderId)
  ├─ 校验状态（仅 IN_PRODUCTION 可发货）
  ├─ 更新订单状态 → SHIPPED
  └─ @Event("order.delivering")
       └─ InventoryEventBridge.onSalesDelivery()
            └─ outboundService.createFromSales(orderId)  ← 直接建库存出库单
```

**全程不碰 `sales_delivery` 表。** 实测该表 **0 行**，且永远会是 0 行。

### 2.3 连带影响

1. **订单跟踪页面永久空白**：`views/sales/tracking/index.vue:326` 引用了 `deliveryApi`，页面第 242 行的 `<el-empty description="暂无发货信息" />` 会永远显示。
2. **无发货单菜单/页面**：`views/sales/` 下无 `delivery` 目录，`sys_menu` 里 12 条销售菜单无发货单入口。
3. **送货单无法打印**：`exportPdf` 空壳 + 无 print.vue（打印报告已列为 P0）。
4. **无客户签收闭环**：`SalesDeliveryVO` 里 `receiverName` / `receiverPhone` / `receiveTime` / `receiveRemark` / `deliveryStatus` 五个签收字段全部定义了，**但没有任何写入路径**。
5. **发货明细不可追溯**：出库单是按订单整单生成，无分批发货能力。实际制造业分批交货是常态。

### 2.4 判断

发货环节是**表结构 + VO + 查询接口 + 前端 API 客户端全部就绪，唯独没做写入侧**。看起来像是当初设计好了但实施到一半改走了"事件直连库存"的捷径，留下了半成品。

**这不是小缺口 —— 送货单是客户签收凭证，没有单据就没有交付证据链。**

---

## 三、完全缺失的 3 个环节

### 3.1 退货 / 退款（`sales_return`）

| 检查项 | 结果 |
|---|---|
| 表 | ✅ 存在，0 行 |
| Entity / Mapper / Service / Controller | ❌ 销售侧零代码 |
| 前端页面 / 菜单 | ❌ 无 |
| 事件配置 | ❌ 无 |
| 库存侧 | ⚠️ `InboundTypeEnum` / `OutboundTypeEnum` 有"退货"类型 |

**现状**：退货只能走库存模块手工入库，没有销售退货单据，没有退款流程。

**连带死代码**：`SalesPaymentStatusEnum.REFUNDED(5, "已退款")` 定义了但**全系统无处使用**。

**影响**：薄膜开关行业客户批退（尺寸偏差/丝印不良/按键手感）不算低频。目前退货完全在系统外，账实必然分离。

### 3.2 销售合同（`sales_contract`）

- 表存在，0 行，**代码零引用**（完全死表）
- 但质量记录模板里有 1 份挂了 `biz_type='sales_contract'`（打印报告中的占位标识）
- 判断：要么补齐，要么删表并清掉模板占位标识 —— 不该留着模糊状态

### 3.3 销售业绩（`sales_performance`）

- 表存在，0 行，**代码零引用**（完全死表）
- 销售报表页 `views/sales/report/index.vue` 走的是三个实时统计接口（`orders/statistics`、`customers/statistics`、`quotation/statistics`，后端均存在 ✅），**不读这张表**
- 判断：实时统计已够用，这张表建议直接删

---

## 四、回写缺失：收款不影响订单付款状态

### 事实

- `SalesPaymentStatusEnum` 定义 5 态（未支付/支付中/已支付/部分支付/已退款）
- 全系统仅 **2 处**引用：枚举自身 + `SalesOrderConverter`（做展示层转换）
- **`SalesReceiptServiceImpl` 零引用** —— 创建收款单后不回写订单付款状态

### 后果

收款单和销售订单是两条平行线。系统无法回答：
- 这张订单收了多少钱、还欠多少
- 哪些订单逾期未收款
- 应收账款账龄

**这是财务侧的实质缺口**，比发货断链更隐蔽但同样影响经营。

---

## 五、CRUD 不完整

| Controller | GET | POST | PUT | DELETE | 问题 |
|---|---|---|---|---|---|
| `SalesReceiptController` | ✅ page/{id}/export | ✅ create | ❌ | ❌ | **收款单开错了改不了也删不了** |
| `SalesInvoiceController` | ✅ page/{id}/export | ✅ create | ❌ | ✅ | **发票改不了**（只能删了重开） |
| `SalesDeliveryController` | ✅ ×4 | ❌ | ❌ | ❌ | 见第二节 |

**衍生问题**：事件配置 `id=131 sales.invoice.updated`（发票修改）已启用，但**后端没有 update 端点** → 这条事件永远不会触发，属于死配置。

---

## 六、事件链核对（37 条配置 vs 34 处代码）

### 空转事件（配置启用但代码无触发点）

| id | event_code | 状态 |
|---|---|---|
| 19 | `order.sent_to_customer` | ❌ 代码零引用，**死事件** |
| 131 | `sales.invoice.updated` | ❌ 无 update 端点，永不触发 |

### 已验证落地（部分）

`inquiry.converted` / `quotation.submitted` / `quotation.reviewed` / `quotation.sent` / `quotation.converted` / `quotation.confirmed` / `quotation.rejected` / `order.submitted` / `order.review_started` / `order.approved` / `order.rejected` / `order.resubmitted` / `order.cancelled` / `order.delivering` / `order.production_started` / `order.confirmed` / `sample.*`（13 条全落地）/ `sales.customer.*`（5 条全落地）

**评价**：事件覆盖率约 **95%**（35/37），这是销售模块做得最好的部分。

---

## 七、亮点（不能只说问题）

### 7.1 样品单是全模块最完整的闭环

- 10 态状态机（含 `REJECTED` 客户退回 → 回工程重打样的回环）
- 13 条事件全部落地
- `EngineeringWorkbench.vue` 工程工作台
- `SampleConvertCheckDialog.vue` 转量产前置校验
- `SampleTransferDialog.vue` + `transfer-edit.vue` 资料转移
- 3 张子表（`sales_sample_bom` / `sales_sample_round` / `sales_sample_process`）支撑轮次/BOM/工序历史

### 7.2 订单 → 生产 双向联动已通

**正向**：`OrderStatusServiceImpl:426-455` generate-plan → `productionOrderService.createOrder()` → 自动提交审批

**反向**：`ProductionOrderServiceImpl:295-303` 工单启动 → 自动回写销售订单 `APPROVED/CONFIRMED(4/6) → IN_PRODUCTION(7)`

**取消联动**：`OrderStatusServiceImpl:319` 订单取消 → `cancelBySalesOrderId()` 级联取消工单；:407 有活跃工单时阻止重复生成

这块设计是扎实的。

### 7.3 库存预留链路三方打通

`sales_order_stock_reserve` 被 `OrderStatusServiceImpl` / `InventoryOutboundServiceImpl` / `ProductionOrderServiceImpl` 三方调用，预留 → 消耗 → 释放链路完整（虽然表 0 行未经数据验证）。

### 7.4 订单状态机覆盖完整

10 态 + `OrderStatusController` 12 个端点（submit / review / approval / rejection / resubmit / cancel / generate-plan / ship / complete / confirm / 审核状态查询 / 审核历史），无遗漏态。

---

## 八、数据零验证（不可忽视的风险）

| 表 | 行数 |
|---|---|
| `sales_customer` | 11 |
| `sales_inquiry` | 1 |
| **其余 17 张全部** | **0** |

包括：报价单、报价明细、报价流转、订单、订单产品、订单审核、库存预留、发货、收款、发票、退货、合同、业绩、样品 4 张表。

**含义**：所有上述"已闭环"判断**均为代码静态分析结论，未经运行时验证**。实际跑起来大概率会暴露：字段缺失、事件参数不匹配、事务边界、并发状态覆盖等问题。

**建议**：优先造一套贯穿主流程的演示数据（询价 → 报价 → 订单 → 工单 → 发货 → 出库 → 收款 → 发票 → 完成），跑通一遍再谈补功能。这比继续加功能价值高。

---

## 九、优先级建议

| 优先级 | 事项 | 理由 |
|---|---|---|
| **P0** | 主流程贯穿演示数据 + 跑通一遍 | 17 张空表意味着所有闭环结论未验证，先证伪再动工 |
| **P0** | 发货单写入侧（create/update/签收） | 客户签收凭证缺失，交付无证据链 |
| **P0** | 送货单打印页 `sales/delivery/print.vue` | 与打印报告 P0 同一件事 |
| **P1** | 收款 → 订单付款状态回写 | 应收账款不可见，财务侧硬缺口 |
| **P1** | 收款单 update/delete、发票 update | 开错单无法纠正，实际使用会卡死 |
| **P1** | 退货/退款流程 | 批退在系统外，账实分离 |
| **P2** | 清理 2 条空转事件（19 / 131） | 死配置 |
| **P2** | `sales_contract` / `sales_performance` 定去留 | 死表，且合同还有模板占位标识挂着 |
| **P2** | 样品 3 张子表补管理入口 | 目前只能内部调用，无独立界面 |
| **P3** | 发货分批能力 | 制造业常态，当前是整单出库 |

---

## 十、风险提示

1. **交付证据链风险（高）**：无发货单据 + 无签收记录 + 无送货单打印。一旦客户否认收货，系统内拿不出任何凭证。
2. **应收账款失控风险（高）**：收款不回写订单，无法统计欠款和账龄。
3. **账实分离风险（中）**：退货走库存手工入库，销售侧无记录，对账时销售出货量与库存出库量会出现无法解释的差异。
4. **"看起来能用"的假象风险（中）**：37 条事件全启用、状态机齐备、页面数量多，容易给人"销售模块已完成"的印象。实际 17 张核心表空置，未经任何真实数据验证。
5. **发票不可修改的合规风险（中）**：只能删除重开。若发票号已报税或已交付客户，删除会造成断号。

---

*报告基于 2026-08-31 数据库与代码实际状态生成，所有数量、行号、端点均为实查结果。*
