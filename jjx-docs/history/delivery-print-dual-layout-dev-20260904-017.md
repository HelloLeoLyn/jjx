# 送货单打印双版式：系统版 + QR-026 纸版复刻（dev-20260904-017，任务1418）

状态：⏳待实施（2026-09-04；样板参考：1414/1417；本任务必须使用打印公共层 dev-20260904-019 的组件）

## 前置（已处理）

- QR-026 送货单台账行 file_id 原错指采购单附件 → 已把本地 送货单.xlsx 挂上（attachment 11，registry file_id=11）。

## 纸版结构（已读 送货单.xlsx，Sheet QF 新26年）

- 左上：深圳精捷信塑胶五金电子制品厂（公司名）；右上：地址
- 标题：送  货  单（中部）；NO:xxxxx（右侧上行）｜DATE:yyyy-mm-dd
- TO: 客户名｜Attm: 客户采购联系人
- 表头 9 列：NO｜品名(料号)｜规格｜单位｜数量｜单价｜金额｜订单号码｜备注
- 底部条款：如上列货品有不符问题，请在10天内通知。方便我司处理，过期恕不负责。
- 签名：送货单位经手人：＿＿ 收货单位经手人：＿＿（底部再印公司名）

## 数据映射（数据源 sales_delivery 详情 + 关联订单明细，现状同款）

| 纸版 | ERP |
|----|----|
| 公司名/地址 | 系统 pdf_template company 配置（差异观察项：纸版印厂名） |
| NO | info.deliveryNo |
| DATE | info.deliveryDate |
| TO | info.customerName |
| Attm | info.contactPerson（联系电话可并排） |
| NO列 | 序号 |
| 品名(料号) | item.productName \|\| item.productCode |
| 规格 | item.specification |
| 单位 | item.unit |
| 数量 | item.quantity |
| 单价 | item.unitPrice |
| 金额 | item.amount（合计金额行保留） |
| 订单号码 | orderNo（现状每行同一订单号，保留现状口径） |
| 备注 | item.remark \|\| item.lineRemark |
| 条款/经手人 | 固定文案（10天通知条款 + 送货/收货经手人 + 底部公司名） |

## 改动（jjx-web/src/views/sales/delivery/print.vue，用公共层）

1. 用公共组件：PrintToolbar（返回+标题"送货单打印-{deliveryNo}"+打印）、PrintQrCode（text=deliveryNo，
   右上角，系统版/纸版共用一份二维码逻辑）、usePrintLayout('delivery-print-layout', ['system','qr026'])、
   usePrintLog('sales_delivery') 替代硬编码 createQualityTemplatePrintLog(26,...)（公共件按 bizType
   自动定位注册表行=26）；
2. A 分支：现有系统版结构保留（含现有 9 列表与样式），仅套公共件重构外壳；
3. B 分支：纸版复刻 section（宋体细线风格，参照 purchase/order/print.vue 的 .qr024-* 写法）：
   公司名左/地址右、送货单标题、右侧 NO/DATE、TO/Attm、9 列表（内容同 A，同一 items 数据源）、
   10天通知条款、送货单位经手人/收货单位经手人、底部公司名；
4. 内容严格对齐模板，不自造字段/版块；A/B 内容一致只换风格；
5. 不 git commit；不动其它文件；npx vue-tsc --noEmit 报告。

## 收尾

registry QR-026 行 print_component=views/sales/delivery/print.vue、print_mode=dual（print_component 之前未填）；
任务 1418 → status=2 待审核。
