# 📩 销售模块完整业务分析（从询价开始）

> 基于实际代码逐条梳理，颗粒度到每个端点、状态跳转、校验规则、交叉联动。
> 
> 代码路径: `jjx-server/src/main/java/com/jjx/sales/`
> 
> 最后更新: 2026-07-29

---

## 一、整体模块结构（11 个 Controller）

| Controller | 路径 | 职责 |
|-----------|------|------|
| `InquiryController` | `/sales/inquiry` | 询价单 CRUD + 转报价 |
| `QuotationController` | `/sales/quotation` | 报价单 CRUD + 审核/发送/转订单 |
| `SampleOrderController` | — | 样品单 10 态独立生命周期 |
| `OrderController` | `/sales/orders` | 销售订单 CRUD + createInstances/export |
| `OrderStatusController` | `/sales/orders/{id}/status` | 订单状态推进（提交/审核/驳回/确认/生产） |
| `OrderReviewController` | `/sales/order/review` | 审核记录管理 + 客户确认 + 转交 + 批量 |
| `CustomerController` | — | 客户管理 |
| `SalesDeliveryController` | `/sales/deliveries` | 销售发货 |
| `SalesLogController` | — | 销售日志 |
| `SalesInvoiceController` | — | 销售发票 |
| `SalesReceiptController` | — | 销售收款 |

---

## 二、A — 询价单（SalesInquiry）

### 数据表

`sales_inquiry`

**字段**: inquiryId, inquiryNo(INQ前缀), customerId/Name, contactPerson/Phone, inquiryDate, expectedQuantity, productDescription, keyCount, sizeDescription, materialRequirements, circuitRequirements, connectorRequirements, specialRequirements, hasDrawing(0/1), inquiryStatus(6态), convertedQuotationId, convertTime, remark, salesPersonId/Name

### 端点清单

```
GET    /sales/inquiry/list              — 分页列表（编号/客户名称/状态/销售员筛选）
GET    /sales/inquiry/{inquiryId}       — 详情
POST   /sales/inquiry                   — 新增
PUT    /sales/inquiry                   — 修改
DELETE /sales/inquiry/{inquiryIds}      — 批量删除
POST   /sales/inquiry/convert/{inquiryId}  — 🔑 询价转报价
GET    /sales/inquiry/status-options    — 状态枚举
GET    /sales/inquiry/export            — 导出
```

### 状态机（6 态）

```
draft(草稿) → pending(待处理) → sent(已发送) → accepted(已确认)
                                                    ↓ rejected(已拒绝)
draft ──【convertToQuotation()】──→ converted(已转报价)
```

### 业务规则

- **自动编号**: `redisSequenceService.generateBusinessNumber("INQ", "询价单号")`
- **自动分配**: `salesPersonId = SecurityUtils.getUserId()`（当前登录用户）
- **默认值**: status=draft, date=今天, hasDrawing=0
- **约束**: converted 状态不可修改、不可重复转换

### 询价转报价（核心链路）

```java
InquiryServiceImpl.convertToQuotation(Long inquiryId) {
    // 1. 校验询价单存在且未删除
    // 2. 校验未转报价（converted 不可重复转）
    // 3. 创建报价单，字段从询价单承继：
    //    customerId/customerName/contactPerson/contactPhone
    //    salesPersonId/salesPersonName
    //    默认 currency=CNY, exchangeRate=1, validUntil=+30天
    //    备注："由询价单[INQxxxx]自动创建"
    // 4. 更新询价单: status=converted, convertedQuotationId, convertTime
    // 5. 返回 quotationId
}
```

---

## 三、B — 报价单（SalesQuotation）

### 数据表

`sales_quotation`

**字段**: quotationId, quotationNo(QT前缀), customerId/Name, contactPerson/Phone, quotationDate, validUntil(+30天), currency(CNY/USD/EUR/JPY/HKD), exchangeRate, quotationStatus(7态), subtotalAmount, taxRate/Amount, totalAmount, discountAmount, finalAmount, remark, salesPersonId/Name, approverId/Name/Time/Remark, sendTime/Method/Remark, convertedOrderId, convertTime

### 端点清单（25 个端点）

**CRUD**:
```
GET    /list                   — 分页列表
GET    /{quotationId}          — 详情
POST   /                       — 新增
PUT    /                       — 修改
DELETE /{quotationIds}         — 批量删除
```

**状态/操作**:
```
PUT    /send/{quotationId}               — 📤 发送给客户 (draft→sent)
POST   /convert/{quotationId}            — 🔑 报价转订单 (accepted→order)
PUT    /submit-review/{quotationId}      — 提交审核 (draft→pending_review)
PUT    /review/{quotationId}             — 审核 (pending_review→approved/rejected)
PUT    /status/{quotationId}             — 直接更新状态
POST   /copy/{quotationId}               — 复制报价单
```

**工具**:
```
GET    /status-options                   — 状态枚举
GET    /currency-options                 — 币种枚举
GET    /templates                        — 模板列表
POST   /template/{templateId}            — 从模板创建
POST   /quick                            — 快速报价
GET    /customer/{customerId}/history    — 客户历史报价
GET    /check-quotation-no-unique        — 号唯一性
GET    /statistics                       — 统计
GET    /export                           — 导出列表
GET    /export-pdf/{quotationId}         — 导出PDF
```

### 状态机（7 态 + 转换规则）

```
draft(草稿) ──── submitReview ────→ pending_review(待审核)
    │                                       │
    │                                  ┌────┴────┐
    │                              approved    rejected
    │                               (已审核)    (已驳回)
    │                                  │
    └──────── send ──────────→ sent(已发送)
                                    │
                               ┌────┴────┐
                            accepted  rejected / expired
                                │
                           【convertToOrder】
```

**代码中定义的转换规则**（`QuotationServiceImpl.validateStatusTransition`）:

```
draft         → pending_review, sent          // 可提交审核或直接发送
pending_review → approved, rejected            // 审核决定
approved      → sent                           // 审核通过后发送
sent          → accepted, rejected, expired    // 客户反馈
accepted/rejected/expired → 终态               // 不可再转
```

### 业务规则

- **发送校验**（`sendQuotation`）:
  - 只有 draft 可发送 → sent
  - 记录 sendTime / sendMethod（默认 "email"）
- **审核前校验**（`submitReview` → `validateQuotationForReview`）:
  - customerId 不能为空
  - quotationDate 不能为空
  - totalAmount > 0
  - finalAmount > 0
- **转订单校验**（`convertToOrder`）:
  - 必须 status = "accepted"
  - 调用 `IOrderService.insertOrder()` 创建订单
  - 返回 orderId
- **删除限制**: sent/accepted 不可删除（逻辑删除）
- **复制**: 复制后状态重置为 draft

---

## 四、C — 样品单（SampleOrder · 独立生命线）

### 数据实体

`SalesOrder`（orderType=2 时为样品单，使用 sampleStatus 字段）

### Service 接口

`ISampleOrderService`（10 个方法）

### 完整状态机（10 态）

```
1 CREATED(已创建) ── submitReview ──→ 2 PENDING_REVIEW(待审核)
    │                                       │
    │                               ┌───────┴───────┐
    │                          3 ENGINEERING      9 REJECTED
    │                          (工程打样中)       (审核驳回)
    │                               │               │
    │                          4 SAMPLE_READY       │
    │                          (待送样)             │
    │                               │               │
    │                          5 SAMPLE_SENT        │
    │                          (已送样待确认)        │
    │                               │               │
    │                       ┌───────┴───────┐       │
    │                   6 CONFIRMED     9 REJECTED ─┘
    │                   (样品确认)      (退回→回到3)
    │                       │
    │               ┌───────┴───────┐
    │           7 TRANSFERRED   8 CLOSED
    │           (已转量产)      (已关闭)
    │
    └── 任意 → 10 CANCELLED(已取消)
```

### 业务操作映射

| 方法 | 说明 |
|------|------|
| `createFromQuotation(quotationId, sampleQty, remark)` | 从报价单创建样品单（编号 SP 前缀）→ sampleStatus=1, sampleRound=1 |
| `submitReview(orderId)` | CREATED→PENDING_REVIEW（"提交审核"） |
| `approveReview(orderId, remark)` | PENDING_REVIEW→ENGINEERING（"审核通过"） |
| `rejectReview(orderId, remark)` | PENDING_REVIEW→REJECTED（"审核驳回"） |
| `startEngineering(orderId, engineeringNote)` | ENGINEERING 时记录工程备注 |
| `markSampleReady(orderId, sampleQty)` | ENGINEERING→SAMPLE_READY（"样品完成"） |
| `sendSample(orderId, trackingNo)` | SAMPLE_READY→SAMPLE_SENT（"送样"，记录快递单号+日期） |
| `confirmSample(orderId, clientName)` | SAMPLE_SENT→CONFIRMED（"客户确认"，记录clientName+日期） |
| `rejectSample(orderId, rejectReason)` | 必须 SAMPLE_SENT→REJECTED；sampleRound+1（退回重新打样） |
| `convertToProduction(orderId)` | CONFIRMED→TRANSFERRED；创建标准订单+复制产品明细 |

### 转量产核心链路

```java
SampleOrderServiceImpl.convertToProduction(orderId) {
    // 1. safeTransition(6→7, "转量产")
    // 2. 创建新标准订单（编号 SO 前缀）
    // 3. copyOrderProducts() — 复制样品单产品明细到新订单
    // 4. 回写样品单: convertedOrderId, convertOrderTime
    // 5. 更新报价单: convertedOrderId, convertTime
}
```

### 防并发

```java
// 乐观锁 updateSampleStatus(orderId, fromCode, toCode)
// WHERE sample_status = fromCode
// affected==0 → "状态已变更，请刷新后重试"
```

### 多轮迭代

```
rejectSample → sampleRound+1 → 工程重新打样 → 送样 → 确认
可循环多轮
```

---

## 五、D — 销售订单（SalesOrder · 核心）

### 数据表

`sales_order`

**关键字段**:
```
orderId           — 订单ID
orderNo           — 编号
quotationId       — 关联报价单
customerId/Name   — 客户
contactPerson/Phone — 联系人
orderDate/deliveryDate — 日期

orderType         — 1=标准订单, 2=样品订单
orderStatus       — 12态（见下方）
prodStatus        — 1无生产 2部分生产中 3全部生产中 4完成
sampleStatus      — 样品单专用10态
sampleRound       — 样品迭代轮次

totalAmount/finalAmount  — 金额
taxRate/taxAmount        — 税率/税额
discountRate/Amount      — 折扣
totalQuantity/producedQuantity/shippedQuantity  — 数量
paymentStatus            — 1未支付 2支付中 3已支付 4部分支付 5已退款
paidAmount/unpaidAmount  — 已付/未付

salesManagerId/Name      — 销售负责人
isUrgent/urgentReason    — 是否急单

currency/exchangeRate   — 币种
paymentTerms/deliveryTerms/deliveryAddress — 条款
remark                  — 备注
```

### 端点清单

#### OrderController → `/sales/orders`

```
GET    /sales/orders                              — 分页列表（12种筛选条件）
GET    /sales/orders/{orderId}                    — 详情（含产品明细）
GET    /sales/orders/{orderId}/validation          — 校验信息（客户+产品）
POST   /sales/orders                              — 新增（含产品明细校验）
PUT    /sales/orders/{orderId}                    — 修改
DELETE /sales/orders/{orderIds}                   — 批量删除
GET    /sales/orders/export                       — 导出
PUT    /sales/orders/create-instances/{orderId}   — 创建产品实例
PUT    /sales/orders/payment/{orderId}            — 更新付款
GET    /sales/orders/customer/{customerId}        — 按客户查订单
GET    /sales/orders/quotation/{quotationId}       — 按报价单查订单
GET    /sales/orders/order-no/next                — 生成编号
GET    /sales/orders/order-no/{orderNo}/unique    — 查重
GET    /sales/orders/statistics                   — 统计
```

#### OrderStatusController → `/sales/orders/{orderId}/status`（状态推进）

```
PUT    /{orderId}/status/submissions      — 提交审核 (DRAFT→PENDING_REVIEW)
PUT    /{orderId}/status/review           — 开始审核 (PENDING_REVIEW→REVIEWING)
PUT    /{orderId}/status/approval         — 审核通过 (REVIEWING→APPROVED)
PUT    /{orderId}/status/rejection        — 审核驳回 (REVIEWING→REJECTED)
PUT    /{orderId}/status/resubmissions    — 重新提交 (REJECTED→PENDING_REVIEW)
PUT    /{orderId}/status/send-to-customer — 发送给客户确认
PUT    /{orderId}/status/start-production — 🔑 开始生产 (CONFIRMED→IN_PRODUCTION + 创建工单)
DELETE /{orderId}/status                  — 取消订单（原因）
PUT    /{orderId}/confirm                 — 客户确认 (#129)
GET    /{orderId}/reviews/status          — 审核状态
GET    /{orderId}/reviews/history         — 审核历史
```

#### OrderReviewController → `/sales/order/review`（审核管理，19 个端点）

```
审查操作:
POST /submit/{orderId}        — 提交审核 (submitterId/Name/comment)
POST /start/{orderId}         — 开始审核 (reviewerId/Name/role)
POST /approve/{orderId}       — 审核通过 (reviewerId/Name/comment/attachments)
POST /reject/{orderId}        — 审核驳回 (rejectReason + improvementSuggestions)
POST /return/{orderId}        — 退回修改 (returnReason + modificationRequirements)
POST /transfer/{orderId}      — 转交审核
POST /customer/confirm/{orderId} — 客户确认 (customerId/Name/feedback)
POST /cancel/{orderId}        — 取消审核

查询:
GET  /records/{orderId}       — 审核记录列表
GET  /history/{orderId}       — 审核历史
GET  /current/{orderId}       — 当前审核信息
GET  /pending/{reviewerId}    — 待我审核列表
GET  /submitted/{submitterId} — 我提交的列表
GET  /canSubmit/{orderId}     — 是否可提交
GET  /canReview/{orderId}     — 是否可审核
GET  /canConfirm/{orderId}    — 是否可客户确认
GET  /timeout                 — 超时订单

批量:
POST /batch/submit            — 批量提交
POST /batch/approve           — 批量审核通过
POST /batch/reject            — 批量驳回
```

### 完整 12 态状态机

```
 1 DRAFT(草稿) ── submitReview ──→ 2 PENDING_REVIEW(待审核)
      │                                    │
      ├── cancel ──→ 11 CANCELLED           ├── startReview ──→ 3 REVIEWING(审核中)
      │                                    │
      │                              ┌─────┴──────┐
      │                            4 APPROVED   5 REJECTED
      │                           (已审核)     (已驳回)
      │                              │            │
      │                        sendToCustomer    resubmit
      │                              │            ↓
      │                          6 CONFIRMED ←──┘ (回 2)
      │                          或 → 12 EXPIRED
      │                              │
      │                         startProduction
      │                              │
      │                          8 IN_PRODUCTION(生产中)
      │                              │
      │                          9 SHIPPED(已发货)
      │                              │
      │                          10 COMPLETED(已完成)
      │
      └── 终态: 10/11/12 不可再转
```

### 每个状态转换的校验规则

| 操作 | 端点 | 校验规则 |
|------|------|----------|
| **提交审核** | `submitReview` | `orderProductService.isExists(orderId)`；负责人才可操作；只有 DRAFT/REJECTED 可提交 |
| **开始审核** | `startReview` | 必须 PENDING_REVIEW；必须 `sales:order:review` 权限 |
| **审核通过** | `approveOrder` | 必须 REVIEWING |
| **审核驳回** | `rejectOrder` | 必须 REVIEWING；驳回原因不能为空 |
| **重新提交** | `resubmit` | 必须 REJECTED；负责人才可操作 |
| **客户确认** (#129) | `confirmOrder` | 必须 APPROVED(4)；记录 confirmedBy/confirmMethod/remark；fire 联动事件 |
| **开始生产** | `startProduction` | 🔴 必须 CONFIRMED(7)；有产品；每个产品必须有当前 BOM 且已审批(approve_status=3)；每个产品必须有当前路线且已审批(approve_status=3) |
| **取消** | `cancelOrder` | 终态(COMPLETED/CANCELLED)不可取消；负责人才可操作 |
| **创建订单** | `insertOrder` | 🔴 **标准单**: 产品必须 RELEASED(productStatus=6)；🔴 **样品单**: 无 productId 时自动创建产品；明细不能为空 |
| **删除订单** | `deleteOrderById` | status≥2 不可删；负责人才可操作 |

### 创建订单产品明细校验（核心）

```java
OrderServiceImpl.validateOrderItems(items, orderType) {
    // 标准单(orderType=1):
    //   - productId 不能为空
    //   - productCode 不能为空
    //   - 产品必须已发布: productStatus == RELEASED(6)
    // 样品单(orderType=2):
    //   - productId 可为空（自定义产品）
    //   - productCode 可为空则自动生成 "SAMPLE-xxx"
    //   - 自动创建产品记录(ensureProductIds)
}
```

### 开始生产全链路

```java
OrderStatusServiceImpl.startProduction(orderId) {
    // 1. 校验: orderStatus == CONFIRMED(7)
    // 2. 校验: 订单产品存在(isExists)
    // 3. 遍历每个产品:
    //    a. 查 BOM: is_current=1 AND approve_status=3
    //       → 没有: "请先完成BOM审批"
    //    b. 查路线: is_current=1 AND approve_status=3
    //       → 没有: "请先完成路线审批"
    // 4. 全部通过 → 为每个产品创建 ProductionOrderCreateDTO:
    //    salesOrderId/no, productId/code/name, plannedQuantity
    //    planStartDate=today, planEndDate=deliveryDate
    //    priority = isUrgent ? HIGH : MEDIUM
    //    🔴 记录 BOM ID + 路线 ID (追溯用): bomId/routingId
    // 5. productionOrderService.createOrder(createDTO)
    // 6. 更新 sales_order: status=8(IN_PRODUCTION), prodStatus=3(全部生产中)
    // 7. fire("order.confirmed") 事件
}
```

### 防并发设计

```java
// 乐观锁 updateStatusWithCheck(orderId, newStatus, oldStatus)
// UPDATE sales_order SET orderStatus=? WHERE orderId=? AND orderStatus=?
// affected==0 → "订单状态已被修改，请刷新后重试"
```

### 事件联动

```java
// 提交审核时 (注解方式)
@Event("order.submitted")
submitReview(orderId)

// 客户确认时 (手动fire)
eventPublisher.fire("order.confirmed", Map.of(
    "orderNo", orderNo,
    "orderId", orderId,
    "confirmedBy", confirmedBy,
    "confirmMethod", confirmMethod
));

// 开始生产时
fire("order.confirmed", Map.of(
    "orderNo", order.getOrderNo(),
    "orderId", String.valueOf(orderId)
));

// 日志记录 (每个状态变更)
saveOrderLog(orderNo, action, description, status)
// 写入 sys_oper_log: bizType="ORDER", bizId=orderNo, module="sales_order"
```

---

## 六、E — 客户管理

**Controller**: `CustomerController`

**端点**:
```
GET    /sales/customers/page         — 分页查询
GET    /sales/customers/{id}         — 详情
POST   /sales/customers              — 新增
PUT    /sales/customers              — 修改
DELETE /sales/customers/{ids}        — 删除
GET    /sales/customers/list         — 列表选项
GET    /sales/customers/export       — 导出
GET    /sales/customers/statistics   — 客户统计
```

---

## 七、F — 销售发货

**Controller**: `SalesDeliveryController` → `/sales/deliveries`

**端点**:
```
GET    /sales/deliveries               — 发货列表
GET    /sales/deliveries/{deliveryId}  — 详情
GET    /sales/deliveries/by-order/{orderId} — 按订单查发货
```

---

## 八、销售模块完整数据流图

```
                   ┌─────────────────────┐
                   │    客户 (Customer)    │
                   └─────────┬───────────┘
                             │
              ┌──────────────┴──────────────┐
              │                              │
     ┌────────▼────────┐                    │
     │  询价单 INQ      │                    │
     │ draft→converted │                    │
     └────────┬────────┘                    │
              │ convertToQuotation           │
              ▼                              │
     ┌──────────────────┐                   │
     │  报价单 QT         │                   │
     │ draft→sent→accept │◄──────────────────┘
     │ pending_review→   │   直接创建报价
     │   approved→sent   │
     └────┬──────┬──────┘
          │      │
   convertToOrder  createFromQuotation
          │      │
          ▼      ▼
  ┌──────────┐  ┌─────────────────┐
  │ 标准订单SO │  │  样品单 SP       │
  │ 12态机    │  │  10态机         │
  │ 订单审核  │  │ 工程打样→送样→确认│
  │ 客户确认  │  │ 转量产→标准订单  │
  │ 提交生产  │  │ 多轮迭代(round) │
  │→创建工单  │  └────────┬────────┘
  └────┬─────┘           │
       │                 └── convertToProduction
       ▼                      (创建标准订单)
  ┌──────────┐
  │ 生产工单  │  ←── 校验 BOM+路线已审批
  │ 待排产→   │
  │ 已排产→   │
  │ 生产中→   │
  │ 已完成→   │
  │ 已关闭    │
  └──────────┘
```

---

## 九、全部校验要点汇总

| 校验点 | 代码位置 | 规则 |
|--------|----------|------|
| 创建订单选产品 | `OrderServiceImpl.validateOrderItems()` | 标准单: productStatus 必须=6(RELEASED) |
| 样品单无产品 | `OrderServiceImpl.ensureProductIds()` | 自动创建产品记录 |
| 提交审核 | `OrderStatusServiceImpl.submitReview()` | 产品明细必须存在; 只有 DRAFT/REJECTED 可提交 |
| 提交审核完整性 | `OrderServiceImpl.validateOrderForReview()` | customerId/date/amount 必须 > 0 |
| 开始审核 | `OrderStatusServiceImpl.startReview()` | 必须 PENDING_REVIEW; 有审核权限 |
| 审核通过 | `OrderStatusServiceImpl.approveOrder()` | 必须 REVIEWING |
| 审核驳回 | `OrderStatusServiceImpl.rejectOrder()` | 必须 REVIEWING; 驳回原因必填 |
| 重新提交 | `OrderStatusServiceImpl.resubmit()` | 必须 REJECTED; 本人负责 |
| 客户确认(#129) | `OrderStatusServiceImpl.confirmOrder()` | 必须 APPROVED(4) |
| 开始生产 | `OrderStatusServiceImpl.startProduction()` | 必须 CONFIRMED(7) + 有产品 + BOM审批 + 路线审批 |
| 负责人操作 | `OrderStatusServiceImpl` 多处 | 非超管只能操作本人负责的订单 |
| 防并发 | `updateStatusWithCheck()` 乐观锁 | WHERE status=旧值 |
| 删除订单 | `OrderServiceImpl.deleteOrderById()` | status≥2 不可删 |
| 报价转订单 | `QuotationServiceImpl.convertToOrder()` | 必须 status=accepted |
| 询价转报价 | `InquiryServiceImpl.convertToQuotation()` | 不能已转换(converted) |
