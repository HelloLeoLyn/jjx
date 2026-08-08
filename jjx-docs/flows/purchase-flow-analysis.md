# 🛒 采购模块完整业务分析

> 基于实际代码逐条梳理，颗粒度到每个端点、状态跳转、校验规则、交叉联动。
>
> 代码路径: `jjx-server/src/main/java/com/jjx/purchase/`
>
> 最后更新: 2026-08-01

---

## 一、整体模块结构（6 个 Controller）

| Controller | 路径 | 职责 |
|-----------|------|------|
| `PurchaseSupplierController` | `/purchase/supplier` | 供应商管理（含评估/启停/导入） |
| `PurchaseOrderController` | `/purchase/order` | 采购订单 CRUD + 审批 + 到货 + 退货 |
| `PurchaseReceiptController` | `/purchase/receipt` | 采购收货（到货登记/检验） |
| `PurchaseInvoiceController` | `/purchase/invoice` | 采购发票（收票/核验/统计） |
| `PurchasePaymentController` | `/purchase/payment` | 采购付款（登记/审批/凭证） |
| `OrderExportController` | `/order/export` | 订购单 Excel 导出 |

---

## 二、A — 供应商（PurchaseSupplier）

### 数据表

`purchase_supplier`

**字段**: supplierId, supplierCode(SUP前缀), supplierName, supplierType(M原材料/E设备/其他), contactPerson, phone, email, address, paymentTerms(NET_30等), bankAccount, taxNumber, evaluationScore/qualityScore/deliveryScore/priceScore, lastEvaluationDate, status(1启用/0停用), delFlag

### 端点清单

```
GET    /purchase/supplier/list              — 列表
GET    /purchase/supplier/{supplierIds}?    — 删除（DELETE）
PUT    /purchase/supplier/status/{id}       — 启停用
PUT    /purchase/supplier/evaluation/{id}   — 供应商评估打分
GET    /purchase/supplier/type/{type}       — 按类型筛选
GET    /purchase/supplier/active            — 启用供应商
GET    /purchase/supplier/high-quality      — 优质供应商
GET    /purchase/supplier/check-supplier-code-unique   — 编码唯一校验
GET    /purchase/supplier/check-supplier-name-unique   — 名称唯一校验
GET    /purchase/supplier/statistics        — 统计
POST   /purchase/supplier/import            — Excel 导入
GET    /purchase/supplier/importTemplate    — 导入模板
```

### 状态/校验
- supplier_code、supplier_name 唯一
- status: 1=启用, 0=停用
- 评估打分：quality/delivery/price 三项 + 综合分，用于"优质供应商"筛选

---

## 三、B — 采购订单（PurchaseOrder）🔑 核心

### 数据表

`purchase_order` + `purchase_order_item`

**主表字段**: orderId, orderNo(PCO前缀), supplierId/Name, orderType(normal), orderDate, expectedDeliveryDate, actualDeliveryDate, orderAmount, orderTax, orderTotalAmount, currency(CNY), approvalStatus, receiptStatus, approverId/Name, approvalTime, approvalComment, paymentStatus, paidAmount, contractNo, deliveryMethod/Address, remark, urgentFlag/Reason

**明细字段**: itemId, orderId, materialId/Code/Name, quantity, receivedQuantity, unitPrice, amount, receiptStatus, inspectionResult(PASS/FAIL), inspectionRemark

### 端点清单

```
GET    /purchase/order/count                        — 数量统计
GET    /purchase/order/list                         — 分页列表
GET    /purchase/order/{orderId}                    — 详情
GET    /purchase/order/{orderId}/items              — 明细
PUT    /purchase/order/cancel/{orderId}             — 取消
POST   /purchase/order/return/{orderId}             — 退货
PUT    /purchase/order/submit/{orderId}             — 提交审批
PUT    /purchase/order/batch-submit                 — 批量提交
PUT    /purchase/order/approve                      — 审批（通过/驳回）
PUT    /purchase/order/status                       — 更新状态
POST   /purchase/order/{orderId}/receive            — 到货登记
PUT    /purchase/order/receiptStatus                — 更新收货状态
PUT    /purchase/order/payment                      — 登记付款
GET    /purchase/order/statistics                   — 统计
GET    /purchase/order/generate-order-no            — 生成单号
POST   /purchase/order/copy/{orderId}               — 复制订单
POST   /purchase/order/export                       — 导出
DELETE /purchase/order/{orderId}                    — 删除
```

### 状态机

**审批状态 approvalStatus**（ApprovalStatusEnum）:
```
1 草稿(DRAFT) → 2 已取消(CANCELLED)
             → 3 待审批(PENDING)   ← 提交(submit)
3 待审批     → 4 已批准(APPROVED)  ← 审批通过
             → 5 已拒绝(REJECTED)  ← 审批驳回
4 已批准     → 2 已取消            ← 取消
```

**收货状态 receiptStatus**（ReceiptStatusEnum）:
```
0 未收货 → 1 部分收货(PARTIALLY_RECEIVED) → 2 已完成(COMPLETED)
```

### 核心联动（⚠️ 重要）

| 动作 | 做了什么 | 缺口 |
|---|---|---|
| 到货登记 receive | 更新明细 receivedQuantity + receiptStatus + inspectionResult | **❌ 不加库存**（DEV-471 待修） |
| 审批通过 approve | 更新 approvalStatus | 通知提交人（事件 purchase.approved）|
| 提交审批 submit | 状态 1→3 | 通知审核员（事件 purchase.submitted）|

---

## 四、C — 采购收货（PurchaseReceipt）

### 端点清单

```
GET    /purchase/receipt/list                  — 待收货订单列表
GET    /purchase/receipt/{receiptId}           — 收货详情
DELETE /purchase/receipt/{receiptIds}          — 删除（接口拒绝："收货记录不可删除"）
POST   /purchase/receipt                       — 新增收货（orderId+itemId+receivedQuantity+inspectionResult）
PUT    /purchase/receipt                       — 修改收货
PUT    /purchase/receipt/inspect/{receiptId}   — 检验
PUT    /purchase/receipt/confirm/{receiptId}   — 确认收货
GET    /purchase/receipt/pending-orders        — 待收货订单
GET    /purchase/receipt/order/{orderId}       — 按订单查
GET    /purchase/receipt/material/{materialId} — 按物料查
GET    /purchase/receipt/supplier/{supplierId} — 按供应商查
GET    /purchase/receipt/pending-inspection    — 待检验
GET    /purchase/receipt/inspected             — 已检验
POST   /purchase/receipt/batch                 — 批量收货
POST   /purchase/receipt/batch-inspect         — 批量检验
POST   /purchase/receipt/import                — 导入
```

### 关键逻辑
- **收货不可删除**（业务上收货记录是凭证）
- 检验结果 PASS/FAIL 影响后续入库（FAIL 不进良品库）
- ⚠️ 到货登记不自动创建入库单（需手动调 `/inventory/inbound/create-from-purchase`，TC-64 缺口）

---

## 五、D — 采购发票（PurchaseInvoice）

### 端点清单（摘要）

```
GET    /purchase/invoice/list                  — 列表
GET    /purchase/invoice/{invoiceId}           — 详情
DELETE /purchase/invoice/{invoiceIds}          — 删除
PUT    /purchase/invoice/verify/{invoiceId}    — 核验
POST   /purchase/invoice/batch-verify          — 批量核验
POST   /purchase/invoice/batch-confirm         — 批量确认
GET    /purchase/invoice/pending-orders        — 待开票订单
GET    /purchase/invoice/order/{orderId}       — 按订单查发票
GET    /purchase/invoice/supplier/{supplierId} — 按供应商查
POST   /purchase/invoice/upload-temp/{orderId} — 上传临时发票文件
GET    /purchase/invoice/disk-files/{orderId}  — 订单发票文件
POST   /purchase/invoice/import                — 导入
GET    /purchase/invoice/statistics            — 统计（类型/状态/月度/季度/年度）
```

### 状态
核验状态：待核验 → 已核验（verify）→ 已确认（batch-confirm）
发票号唯一校验（check-invoice-no-unique）

---

## 六、E — 采购付款（PurchasePayment）

### 端点清单（摘要）

```
GET    /purchase/payment/list                  — 列表
DELETE /purchase/payment/{paymentIds}          — 删除
PUT    /purchase/payment/approve/{paymentId}   — 审批
POST   /purchase/payment/confirm               — 确认付款
POST   /purchase/payment/upload-voucher        — 上传付款凭证
GET    /purchase/payment/pending-approval      — 待审批
POST   /purchase/payment/batch                 — 批量登记
POST   /purchase/payment/batch-approve         — 批量审批
POST   /purchase/payment/import                — 导入
GET    /purchase/payment/statistics            — 统计
```

### 与订单联动
- 付款登记 → 更新 purchase_order.paymentStatus + paidAmount
- 应付账款视角：pending-orders = 已到货未付款的订单

---

## 七、状态流转总图

```
供应商建档
   │
   ▼
采购订单(1草稿) ──提交──► (3待审批) ──通过──► (4已批准) ──到货──► 收货记录
   │                      │                              │
   │                      ▼                              ▼
   │                  (5已拒绝)←─驳回              部分/完成收货
   │                                                 │
   ▼                                                 ▼
(2已取消)←────────────────────────────          检验(PASS/FAIL)
                                                      │
                              ┌───────────────────────┘
                              ▼
                        入库单(手动联动) → 库存增加 ⚠️

发票：订单→收票→核验→确认
付款：订单→登记付款→审批→确认（更新订单付款状态）
退货：已到货订单 → 退货单 → 库存扣减
```

## 八、与其他模块联动

| 联动 | 方式 | 状态 |
|---|---|---|
| 采购→库存 | 入库单 create-from-purchase（手动）| ⚠️ 未自动 |
| 采购→事件 | purchase.submitted/approved/rejected 等 20 个事件 | ✅ 已配（审核员[8]）|
| 采购→应付 | 付款登记更新订单付款状态 | ✅ |

## 九、已知问题

1. **到货不加库存**（DEV-471 urgent）：receiveOrderItem 只更新收货字段，库存不涨
2. **收货不自动建入库单**（TC-64）：需手动调 create-from-purchase
3. **导出功能部分空壳**：`/purchase/receipt/export` 返回"导出功能待实现"
