# JJX ERP 采购模块实证差距分析报告

- 报告日期：2026-09-02
- 方法：数据库实查 + 代码实扫 + 事件核对
- 前置说明：061 采购发票页（9f12647）/062 付款页（6086e0b）/063 收货页（17aa4ee）2026-09-01 已交付待验收，本文含其现状

---

## 1. 现状盘点（实证）

### 1.1 表清单与行数

| 表 | 行数 | 判定 |
|---|---|---|
| purchase_supplier | 54 | 有真实数据 |
| purchase_order / purchase_order_item | 0 / 0 | 空转 |
| purchase_document（发票/收货共用文档模型） | 0 | 空转 |
| purchase_payment | 0 | 空转 |
| purchase_material_inquiry（采购询价） | 0 | 空转 |
| 网版/物料等关联主数据不属本模块 | - | - |

供应商有数据，其余 0 行（dev 清洗）。

### 1.2 后端 Controller（purchase 包 6 个 + OrderExport）

| Controller | 端点 | CRUD 判定 |
|---|---|---|
| PurchaseSupplierController | list/detail/POST/PUT/DELETE/export/status/evaluation/statistics/import | ✅ 全 |
| PurchaseOrderController | CRUD+submit/approve/receive/return/cancel/copy/export-pdf/plan-suggestions×4 | ✅ 全（plan 建议/生成计划/复制/PDF 导出） |
| PurchaseReceiptController | list/detail/POST/PUT/DELETE/inspect/confirm/batch/batch-inspect/import | ✅ 全（收货操作作用于订单明细行，无独立收货单表——063 页面按订单+展开明细实现） |
| PurchaseInvoiceController | 40+ 端点（CRUD/verify/batch-verify/statistics×10/upload-temp/disk-files/batch-confirm/import） | ✅ 全（061 修复 page() 空壳+batchConfirm 硬编码 receipt） |
| PurchasePaymentController | CRUD/export/approve/confirm/upload-voucher/statistics×N | ✅ 全 |
| OrderExportController | POST /order/export | 单点 |

### 1.3 前端页面与菜单

采购目录(36) C 菜单：供应商/采购订单/采购计划/采购发票(165)/采购付款(172)/采购收货(180)，component 全部指向 views/purchase/ 真实文件（061-063 补全后无缺页）。views/purchase 下还有 report/（无菜单？sys_menu 未见采购报表 C 菜单——views/purchase/report 存在但菜单 36 下无——需核实是孤儿页面还是走其他菜单）。

### 1.4 事件配置（purchase.* 17 条启用）

代码 @Event 触发对照（grep purchase 包）：
- 配置有：purchase.approved/submitted/item_received/received/payment.created/approved/confirmed/deleted/payment_updated/document.created/deleted/verified/supplier.*×4/material_inquiry.*×3
- 代码触发点（2026-09-02 扫）：purchase.payment.approved（PurchasePaymentServiceImpl:163）、其余 payment.*/document.*/supplier.*/material_inquiry.* 需逐一核对——**标注：部分事件配置与代码未全量比对，列入复核**

### 1.5 库代码一致性

采购文档模型 PurchaseDocument(document_type='invoice'/'receipt') 与 061 修复后一致；无脱节。

---

## 2. 业务闭环验证

| 环节 | 判定 |
|---|---|
| 供应商建档→评估 | ✅通（CRUD+evaluation） |
| 采购计划（plan-suggestions 生成建议→confirm→生成订单） | ✅通（DEV-664 工作台） |
| 采购订单→审批（approve/reject，review_flow） | ✅通 |
| 订单→收货（明细行 receiveOrderItem，063 页面） | ✅通 |
| 收货→入库联动（create-from-purchase） | ✅通（InventoryInboundController） |
| 发票（核销 verify/批量）→付款（approve/confirm） | ✅通（061-063 后前后端齐） |
| 采购退货（returnGoods FIFO 扣库存） | ⚠️半通：只扣库存+订单 remark 记录，无退货单据留痕 |

**注：采购主链 0 行数据，全部为代码走查，未实测。**

---

## 3. 与行业基准对照

覆盖：供应商管理✅ 采购计划✅ 订单✅ 收货✅ 发票✅ 付款✅。
缺失/薄弱：
- 采购退货单据化（returnGoods 无独立退货单/审核/红冲，仅扣库存）——行业标准有退货单+对账
- 供应商对账（应付款账龄/对账单）无
- 采购询价（purchase_material_inquiry 表 0 行，MaterialInquiry 代码存在但业务链路未走通——询比价流程无前端入口？需核实 views/purchase 下无 inquiry 页）

---

## 4. 缺口与死代码清单

| 类型 | 项 | 证据 | 影响 | 建议 |
|---|---|---|---|---|
| 业务缺失 | 采购退货单据化 | returnGoods 仅扣库存（PurchaseOrderServiceImpl:1228） | 中 | 参照销售退货 1235 模式补单据 |
| 业务缺失 | 应付款/供应商对账视图 | 无接口/页面 | 中 | 后置 |
| 半成品 | purchase_material_inquiry 链路 | 表 0 行+无前端页 | 低 | 核实后补询比价或删 |
| 孤儿页 | views/purchase/report | sys_menu 36 下无报表菜单 | 低 | 挂菜单或删除 |
| 待复核 | 事件配置与代码全量对照 | 17 条配置仅 1 条确认触发 | 中 | 逐条核对（与 sales 同法） |

## 5. 优先级结论

| 优先级 | 事项 | 理由 |
|---|---|---|
| P1 | 事件配置全量核对 | 采购通知/任务可能大面积空响 |
| P2 | 采购退货单据化 | 对账依据缺失 |
| P2 | purchase/report 页挂菜单或清理 | 孤儿页面 |
| P3 | 询比价流程 | 当前供应商询价可走线下 |
