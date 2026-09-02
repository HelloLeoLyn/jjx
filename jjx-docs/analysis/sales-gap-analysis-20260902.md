# JJX ERP 销售模块实证差距分析报告

- 报告日期：2026-09-02
- 方法：数据库实查（jjx_erp_db 行数）+ 代码实扫（jjx-server/jjx-web）+ 事件配置核对
- 前置参考：jjx-docs/analysis/sales-module-closure-analysis-20260831.md（08-31 基线，本文更新其后改动：051 发货写入侧/052 收款回写/1235 退货/1237-1240 打印）
- 红线遵守：成品物料≠产品；旧派工模型废弃不列入缺口；仅只读查询

---

## 1. 现状盘点（实证）

### 1.1 表清单与行数（2026-09-02 实查）

| 表 | 行数 | 判定 |
|---|---|---|
| sales_customer | 11 | 有真实数据 |
| sales_inquiry | 1 | 测试数据 |
| sales_quotation / sales_quotation_item / sales_quotation_flow | 0 / 0 / 0 | 空转（代码完整） |
| sales_order / sales_order_product | 0 / 0 | 空转 |
| sales_order_review | 0 | 空转 |
| sales_order_stock_reserve | 0 | 空转（inventory 模块代码引用） |
| sales_delivery | 0 | 空转（051 已补写入侧，2026-09-01 提交 3439991，待验收） |
| sales_receipt | 0 | 空转（052 已补回写+新建，8529129 待验收） |
| sales_invoice | 0 | 空转 |
| sales_return / sales_return_item | 0 / 0 | 空转（1235 已实现，6b865f1+95a2d96 待验收） |
| sales_sample_bom / round / process / transfer | 0 / 0 / 0 / 0 | 空转（表结构齐全） |

结论：除 customer(11)/inquiry(1) 外全部 0 行——dev 库清洗过，业务主链从未真实跑通（与 08-31 结论一致，P0 演示数据任务 1232 仍待执行）。

### 1.2 后端 Controller（sales 包 12 个 controller）

CustomerController / InquiryController / QuotationController / OrderController / OrderReviewController / OrderStatusController / SalesDeliveryController / SalesReceiptController / SalesInvoiceController / SalesReturnController / SampleOrderController / SampleTransferController。

CRUD 完整性判定：
- Customer/Inquiry/Quotation/Order/SampleOrder：GET/POST/PUT/DELETE 齐全 ✅
- SalesDeliveryController：051 后 5 GET + 1 PUT（receive）——仍无独立 POST create（发货单由 shipOrder 内建，属设计决策非缺口）
- SalesReceiptController：GET/POST 有，**无 PUT/DELETE**（开错单无法纠正，08-31 P1 遗留，未登记）
- SalesInvoiceController：GET/POST/DELETE 有，**无 PUT**（发票只能删了重开，08-31 P1 遗留，未登记）
- SalesReturnController（1235 新建）：GET/POST/PUT 全 ✅
- SampleTransferController：GET/POST 全 ✅

### 1.3 前端页面与菜单（sys_menu C 菜单 vs views 实查）

销售目录(13)下 C 菜单：客户/询价/报价/样品单/销售订单/订单跟踪/销售报表/发货管理/收款单/发票/退货管理，component 全部指向 views/sales/ 下真实文件 ✅（退货管理 310 为 1235 新增）。
- 缺菜单的页面：quality-template 维护页已迁文档管理(316)（migration 37，2026-09-02）
- 无页面菜单：无（全部 C 菜单有页面）

### 1.4 事件配置（sys_event_config 销售域 55 条启用 vs @Event 代码引用）

代码触发点（grep @Event sales 包）：inquiry.converted、order.approved/cancelled/delivering/rejected/resubmitted/submitted、quotation.confirmed/converted/rejected/reviewed/sent/submitted、sales.customer.approved/created/deleted/updated、sales.invoice.deleted/updated、sample.approved/cancelled/confirmed/converted/created/ready/rejected/restarted/sent/submitted/transferred。

对照空转/缺口：
- **order.delivering：代码 fire 但 sys_event_config 无此配置行** → 空响（发事件无人接——出库联动 InventoryEventBridge 是 @EventListener 不走 config，但通知/任务侧无配置）→ 与 049 同类问题，需登记
- order.confirmed / order.production_started / order.review_started / order.sent_to_customer：配置启用但代码零引用（08-31 已列 19/131，sent_to_customer 确认死事件）
- sales.customer.status_updated：配置启用代码无 fire
- sample.rejected_by_customer：配置启用，代码用 sample.rejected（rejectReview）替代，无独立 fire —— 语义漂移待确认

### 1.5 库代码一致性

sales_return/sales_return_item（1235 migration 31/32）实体与表字段一致；sales_delivery 补 receive_by/receive_name（migration 26）一致。无脱节。

---

## 2. 业务闭环验证

主链状态机（各 StatusEnum 代码走查）：

| 环节 | 状态 | 判定 |
|---|---|---|
| 客户→询价 | inquiry 0草稿→3已转报价 | ✅通 |
| 询价→报价 | quotation 0→5待审核→6已审核→SENT→确认/拒绝 | ✅通 |
| 报价→订单 | 确认后转标准订单/样品单 | ✅通 |
| 订单评审 | 1草稿→2待审→3审核中→4已审核/5驳回→6确认→7生产→8发货→9完成 | ✅通（review_flow 留痕） |
| 订单→生产 | generate-plan 建工单、工单启动回写 7 | ✅通 |
| 发货 | 7→8 shipOrder 建发货单+出库事件 | ✅通（051 后） |
| 收款 | receipt create→回写订单 payment_status | ✅通（052 后） |
| 退货 | 申请→审核→收货(入库)→退款(回写) | ✅通（1235 两阶段后） |
| 发票 | 无 update 端点 | ⚠️半通 |
| 收款单 | 无 update/delete 端点 | ⚠️半通 |

代码走查结论：主链代码层闭环（08-31 断点 051/052/1235 已补），**未实测**（表 0 行，需 1232 演示数据跑通）。

---

## 3. 与行业基准对照

薄膜开关链：接单→打样→客户确认→转量产→备料→生产→检验→出货→对账→售后。
覆盖：接单✅ 打样✅ 转量产✅ 出货✅ 对账(收款/发票)⚠️（无应收账款账龄视图，收款单无改删） 售后(退货)✅(2026-09-01 后)。
缺失：客户合同（sales_contract 已于 1236 删除，订单评审+确认书 PDF 替代——业务如需正式合同需重建）；销售业绩考核快照（sales_performance 已删，实时报表替代）。

---

## 4. 缺口与死代码清单

| 类型 | 项 | 证据 | 影响 | 建议 |
|---|---|---|---|---|
| 空壳接口 | SalesDeliveryController exportPdf | 仅返回 success 不生成 PDF（:export-pdf GET） | 低 | 删除或实现（前端已走 A4Canvas） |
| CRUD 缺口 | SalesInvoiceController 无 PUT | controller 无 update 端点（08-31 遗留） | 高 | 补 update（发票改错只能删，已报税删除断号合规风险） |
| CRUD 缺口 | SalesReceiptController 无 PUT/DELETE | 同上 | 高 | 补 update/delete（开错单无法纠正） |
| 空转事件 | order.delivering | 代码 fire 无配置行（grep config 无） | 中 | 注册配置或确认出库走监听器即可 |
| 死事件 | order.confirmed / production_started / review_started / sent_to_customer / customer.status_updated | 配置启用代码零引用 | 低 | 清理配置 |
| 事件漂移 | sample.rejected_by_customer | 配置 vs 代码用 sample.rejected | 低 | 统一语义 |
| 无验证 | 17 张业务表 0 行 | COUNT(*) | 高 | 1232 演示数据跑通 |

## 5. 优先级结论

| 优先级 | 事项 | 理由 |
|---|---|---|
| P0 | 1232 演示数据跑通 | 主链代码闭环但 0 数据未实测，先证伪 |
| P0 | 发票/收款单 update（delete） | 财务单据开错无法纠正，合规风险 |
| P1 | order.delivering 事件配置核对 | 通知/任务侧空响 |
| P2 | 死事件清理 | 配置噪音 |
| P3 | exportPdf 空壳处理 | 前端已替代 |
