# 采购订单打印页：版式二选一（系统版 / QR-024 纸版复刻）样板（dev-20260904-015）

状态：⏳待实施（2026-09-04 用户拍板：采购打印先做样板看效果；系统自绘版保留，纸版模板样式复刻一份，用户打印时挑）

## 需求一句话

采购订单打印页(print.vue)加"版式"选择：A 系统版(现状简化 A4)/ B 纸版复刻(JJX-QR-024 采购单.xlsx
的版式套活数据)，默认记忆上次选择。纯前端样板，后续其它单据按同一套路复刻。

## 纸版版式结构（已读模板文件 quality_template/2026-09-04/b926652e….xlsx 实测）

1. 公司头：深圳市精捷信科技有限公司 / 地址 / 电话 传真 / E-mail QQ（多行，顶部）
2. 标题：订  购  单
3. 单据信息行：订单号码 | 厂商 | 联系人（含 TEL FAX）| 订货时间 | 交货时间 | 交易方式
4. 明细表 8 列：项次 品名 规格 单位 数量 单价 金额 备注
5. 合计行（合计金额）
6. 交易条款 8 条（固定文案）
7. 右下角：JJX-QR-024
8. 签名行：供应商回签：___ 经理审核：___ 制表人：___

## 数据映射（字段已核实）

| 纸版 | ERP 来源 |
|----|----|
| 公司头（名称/地址/电话/邮箱） | 与 PrintCompanyHeader 同源 pdf_template company_* 运行态配置（/config/module/pdf_template，免权限档） |
| 订单号码 | info.orderNo |
| 厂商 | info.supplierName |
| 联系人 / TEL / FAX | supplierId → 供应商资料（purchase_supplier.contact_person / phone；**无 FAX 列 → 该段留空不显示**）。供应商详情前端现成 api（api/purchase/supplier.ts 附近，Codex 现场核对；若订单详情接口已带联系人字段则直接用，不再多查一次） |
| 订货时间 | info.orderDate |
| 交货时间 | info.expectedDeliveryDate |
| 交易方式 | supplier.payment_terms（无则 '-'；不要自行拼接币种/税种文案） |
| 明细 8 列 | 项次=序号；品名=materialName（纸版品名列印品名/规格两行，ERP 拆列：规格=materialSpec）；单位/数量/单价/金额 照旧；**备注列 ERP 明细无对应字段 → 空** |
| 合计 | info.orderTotalAmount（现有合计口径） |
| 8 条交易条款 | 固定文案，照抄后端 ExcelExportService.java 交易条款常量（读该文件逐条拷贝，保持标点/编号一致，前端存 const，勿改文案） |
| JJX-QR-024 | 固定文本，表格右下 |
| 制表人 | info.createBy（无则当前登录人姓名，前端 user store 有 realName/name 则用之，其次 '-'） |

## 改动清单（纯前端，采购订单样板）

文件：jjx-web/src/views/purchase/order/print.vue
1. 工具栏加版式选择（el-radio-group 或 el-select：系统版 / 纸版(QR-024)），
   localStorage key=`purchase-order-print-layout`（'a4'/'qr024'），默认 'a4'；切换即时重渲染。
2. A 分支 = 现有模板原样保留（div 包裹，别动现有结构与 class）。
3. B 分支 = 新增纸版复刻块（同一 A4Canvas 内另一 section 或同页 v-if 二选一）：
   按上表版式结构与映射实现，样式贴近纸版（公司头居左多行、标题居中放大、信息区紧凑、
   明细 8 列表格、条款小字段落、右下 QR 编号、底部三栏签名行）。纸张/打印仍走 A4Canvas + window.print，
   打印留痕 createQualityTemplatePrintLog(24,...) 两种版式都打。
4. 不改后端、不改列表页、不改路由；ExcelExportService.java 只读不写。
5. 不要 git commit；工作区其它脏文件（并行会话产物）禁止触碰。

## 明确不做

- 不做 xlsx 解析自动渲染（人工复刻样板）；不做其它单据的 B 版式（等样板验收后复用套路）。
- 供应商 FAX/明细备注 无数据源 → 纸版对应处留空，不做字段补表/拼接猜测。
- 版式选择组件暂不抽公共组件（样板内联即可，验收满意后再组件化复用）。

## 验证

Codex：`npx vue-tsc --noEmit`（只报本次相关错误，仓库既有无关报错列出不修）。
人工验收（用户）：采购订单 → 操作栏打印 → 打印页工具栏切换"系统版/纸版(QR-024)"：
纸版与模板文件版式对齐（公司头/信息区/8 列/条款/QR 编号/签名行），数据正确落位；刷新页面后记住上次选择。
