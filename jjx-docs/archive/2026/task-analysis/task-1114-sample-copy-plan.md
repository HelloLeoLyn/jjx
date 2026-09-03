# 任务 1114 实现方案：样品单管理新增复制功能

> 任务：dev-1787657318506（kanban_module=dev）
> 优先级：normal | 状态：未开始(0) | 负责人：未分配 | 截止：2026-08-25
> 方案日期：2026-08-26 | 方案人：大黄 | 状态：待确认（未写代码）

---

## 一、需求

样品单管理新增"复制"功能，**只针对已完成或已取消的样品单**。

## 二、现状参考（代码库已有同款实现）

**标准销售订单已有 copyOrder 功能**（`OrderServiceImpl.copyOrder`，2026-08-18 任务 1047 后实现），语义完全一致："已取消/已完成等终态订单一键重新生成新草稿单"：

- 后端：`POST /sales/orders/{orderId}/copy` → 返回新 orderId
- 新单：新单号、DRAFT 状态、新 traceId、remark 标注"复制自订单[xxx]"、复制产品明细（全字段）、**双向写操作日志**（新单一条 + 原单一条，各自 traceId，双向可查）
- 前端：工具栏"复制"按钮 + confirm 弹窗 → 调接口 → 刷新列表

**样品单复制照此模式扩展即可**，风险低。

## 三、方案设计（推荐：一键复制直接生成新草稿单）

### 3.1 后端

**接口**：`POST /sales/sample-order/copy/{orderId}`（权限 `sales:sample:add`，与新增一致）

**实现**：`ISampleOrderService.copySampleOrder(Long orderId) -> SalesOrder`（返回新单对象，含 orderId/orderNo 供前端提示）

1. **校验**：
   - 原单存在，且 `orderType = SAMPLE`
   - `sampleStatus ∈ {7已转量产, 8已关闭, 10已取消}`（终态），否则报"仅已完成/已取消的样品单可复制"——后端强校验，前端只是入口
2. **新单 header**（参照 copyOrder + createSample）：
   - 新单号：`redisSequenceService.generateBusinessNumber("SP", "样品单号")`（不沿用 COPY_ 前缀，走正常序列，避免与 SP 号格式冲突）
   - 继承：customerId/customerName、contactPerson/contactPhone、currency/exchangeRate、deliveryDate、engineeringNote（技术要求，打样关键字段）
   - 重置：sampleStatus=CREATED(1)、sampleRound=1、orderType=SAMPLE、orderDate=now、quotationId=null（新单不挂报价单）、convertedOrderId=null、confirm/tracking 相关字段全空、traceId=新 UUID（独立链路）
   - remark = `复制自样品单[原单号]\n` + 原 remark
   - totalQuantity/sampleQty：先置 0，复制明细后按明细求和刷新（复用 updateTotalQuantityByItems + sampleQty 同步，与 createSample 一致）
3. **复制明细**：`orderProductService.getListByOrderId(orderId)` → `batchAdd`，全字段复制（productId/productCode/productName/quantity/unit/unitPrice/amount/specification/customerMaterialNo/lineRemark/remark）
4. **不复制**：打样轮次（SalesSampleRound）、工序（SalesSampleProcess）、BOM（SalesSampleBom）——这些属于原单打样过程数据，新单重新打样；流水日志不迁移
5. **双向操作日志**（完全对齐 copyOrder）：新单一条（bizType='sample'，traceId=新单 traceId，operParam 记 sourceOrderNo）+ 原单一条（traceId=原单 traceId，operParam 记 newOrderNo/newOrderId）
6. `@Event("sample.created")` 事件（与 createSample 一致，触发通知/任务联动）
7. `@Transactional`

### 3.2 前端

- `src/api/sales/sampleOrder.ts` 增加 `copy(orderId)` → `POST /sales/sample-order/copy/{orderId}`
- `src/views/sales/sample-order/index.vue` 操作列增加"复制"按钮：
  - `v-hasPermi="['sales:sample:add']"`
  - 仅 `sampleStatus ∈ [7,8,10]` 显示（与需求"只针对已完成或已取消"一致）
  - 点击 → `ElMessageBox.confirm("确定复制样品单【xxx】生成一张新的样品单吗？")` → 调接口 → 成功提示"复制成功，新样品单已生成" → `getList()` 刷新
- 交互与标准订单复制完全一致（一键生成，不做编辑弹窗）

## 四、备选方案（不推荐）

**复制到新增弹窗可编辑再存**（对齐报价单复制 UX）：后端提供"复制预览"返回 header+items，前端填 `createForm` 用户可改，再走 `createSample` 落库。

- 优点：可改完再存
- 缺点：需新增预览接口（或拼 getInfo+getProducts 两个接口）、要绕过 createSample 的 quotationId 逻辑、样品单创建弹窗本身字段很少（客户+明细+备注），可编辑价值有限；且与标准订单复制 UX 不一致
- 结论：**推荐一键复制**，保持全系统一致

## 五、边界与风险

- 原单明细为空：复制成功但新单无明细，remark 不特殊处理（与 copyOrder 一致，不拦截）
- 新单号唯一性：走 Redis 序列服务，无冲突
- 已转量产(7)的样品单复制后转出的新样品单与量产单无关联（quotationId/convertedOrderId 均不继承），符合"重新打样"语义
- 权限复用 `sales:sample:add`，不加新权限点

## 六、待确认（确认后才动工）

1. **"已完成"状态口径**：建议 `{7已转量产, 8已关闭, 10已取消}` 全放开（都是终态）。还是只允许 8+10？
2. **复制 UX**：一键直接生成（推荐）vs 复制到弹窗可编辑
3. **销售负责人**：当前登录人（推荐，与 createSample 一致）还是继承原单（与 copyOrder 一致）？
4. **金额字段**（totalAmount/finalAmount）：继承原值（推荐，与 copyOrder 一致）还是清零（样品无价）？

## 七、改动清单预估

| 文件 | 改动 |
|---|---|
| `SampleOrderServiceImpl.java` | +copySampleOrder 约 90 行（含双向日志） |
| `ISampleOrderService.java` | +1 方法声明 |
| `SampleOrderController.java` | +1 端点 |
| `sampleOrder.ts`（前端 api） | +1 方法 |
| `sample-order/index.vue` | 操作列 +复制按钮 +handleCopy |
