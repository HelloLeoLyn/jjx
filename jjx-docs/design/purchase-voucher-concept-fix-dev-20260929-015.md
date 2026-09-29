# 采购凭证 / 付款凭证 概念订正与改造（A 方案）（dev-20260929-015）

> 日期：2026-09-29 ｜ 任务码：dev-20260929-015（P1，登记人 dahuang）｜ 起草：大黄(OpenClaw)
> 来源：用户 2026-09-29 14:10「核查付款业务流程，付款凭证到底是什么」；14:15/14:23/14:25 拍板 A 方案
> 类型：**方案 + P1 实施记录**
> 关联：`dev-20260929-008`（采购链 trace）｜`design/iqc-pages-industry-alignment-dev-20260924-024.md`（Tab 形态）

---

## 1. 问题（实测）

采购订单 → [付款] 弹窗里的「**付款凭证**」上传，实际调的是**采购发票接口**：

- `OrderPaymentDialog.vue`：`handleImageUpload → uploadTempReceiptFile()`，而 `api/purchase/order.ts` 里该函数 URL = **`/purchase/invoice/upload-temp/{orderId}`**；`loadImages → /purchase/invoice/disk-files`；提交时 `confirmReceiptDocuments() → /purchase/invoice/batch-confirm`（后端固定 `documentType='invoice'`）。函数名却叫 `uploadTempReceiptFile`/`confirmReceiptDocuments`（收货票据）——**名实不符**。
- 结果：付款弹窗传的图**落成了 `purchase_document(document_type='invoice')`（采购发票）**，与「采购发票管理」页共用同一份数据。
- **付款单自己的凭证字段恒空**：`purchase_payment.voucher_no/voucher_file_url` 库中为空，`document_id=NULL`。
- **`/purchase/payment/upload-voucher` 是错实现**：方法体直接 `paymentService.confirmPayment(dto)` —— 调一次等于"确认付款"。

## 2. 概念订正（用户拍板）

| 术语 | 定义 | 归属 | 载体 |
|---|---|---|---|
| **采购凭证** | 供应商给的发票/收据/送货单（**可以是发票、也可以不是**），**由采购人员上传** | 采购订单/采购票据 | 统一附件表（`bizType=purchase_order`, `category=purchase_voucher`）|
| **付款凭证** | **我方转账回单/银行流水**（转账相关记录附件） | 付款单 | 统一附件表（`bizType=purchase_payment`, `category=payment_voucher`）|
| **入库凭证** | 收货生成的**入库单**（系统单据，非文件） | 入库单 | `inventory_inbound_order` |
| 采购发票台账 | 需要发票号/查验状态时的结构化登记（可选） | 采购票据 | `purchase_document(document_type='invoice')`，与凭证解耦 |

## 3. 业内对比

| 维度 | 业内 | JJX 原状 |
|---|---|---|
| 付款依据 | 发票/三单匹配后付款 | 按订单金额付，不校验发票/收货 |
| 采购凭证 | 挂发票/采购单 | 与"付款弹窗上传"混用 `purchase_document(invoice)` |
| 付款凭证 | 银行回单，挂**付款单** | **无**；付款单 voucher 恒空 |
| 付款单附件 | 多附件、按付款单归属 | 无归属；详情按订单目录扫盘 |

## 4. 方案（A）与分期

**P1（本次实施）**
1. 后端 `/purchase/payment/upload-voucher` 改为**真实上传**（附件表 `bizType=purchase_payment`/`bizId=paymentId`），**移除**误调 `confirmPayment`。
2. 前端「采购付款管理 → 确认付款」弹窗新增「**转账回单**」多文件上传/列表（付款凭证），详情抽屉可查看。
3. 前端采购订单「付款」弹窗**移除**错位的「付款凭证」上传块（它写的是发票台账）。
4. 文案：付款「凭证号」→「**转账流水号**」。

**P2（后续）**
- 采购凭证正规上传入口（采购人员）归位到**采购订单详情**；
- 采购订单详情「凭证」区做**三个页签**：采购凭证 / 入库凭证 / 付款凭证（付款凭证**按付款单分组**，能对号入座）；
- 凭证与 trace 串联（复用 `dev-20260929-008` 的采购单 trace）。

## 5. 实施记录（P1）

- 后端：`PurchasePaymentController.uploadVoucher(paymentId, file)` → `attachmentService.uploadAttachment(file,"purchase_payment",paymentId,null)`。
- 前端：`views/purchase/payment/index.vue`（确认付款加转账回单上传/列表、详情展示、文案）；`OrderPaymentDialog.vue`（移除错位上传块及相关代码）；`api/purchase/payment.ts`（`uploadVoucher(paymentId, file)`）。
- 验证：`mvn -o compile` + `npx vue-tsc --noEmit` 通过（仅静态自查）；**需重启后端生效**。
- 遗留：P2 项；旧数据（历史误传入 invoice 的图）无法自动区分，测试期可清。
