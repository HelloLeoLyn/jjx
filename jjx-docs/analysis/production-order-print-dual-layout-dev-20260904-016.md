# 生产工单打印双版式：系统版 + QR-005 制造指令单纸版复刻（dev-20260904-016，任务1417）

状态：⏳待实施（样板参照：任务1414/采购订单双版式，commit b2fa955 起的系列）

## 模板纸版结构（已读 jjx-docs/print_template/QR-005生产指令单.xlsx，Sheet2024）

1. 公司名行：深圳市精捷信塑胶五金电子制品厂（注意：与系统 pdf_template 配置公司名可能不同，
   纸版复刻用系统配置 company 名称/地址做数据源，差异作为观察项报告给用户）
2. 标题：制造指令单
3. 右上：编号 JJX-QR-005 ｜ 日期：____
4. 表头 8 列：NO｜品名｜订单数量｜交期｜机种号｜订单号｜生产批号｜库存
5. 数据行（单行/多行）+ 备注区

## 数据映射（数据源 getProductionOrderDetail 现字段）

| 纸版 | ERP 来源 |
|----|----|
| 编号 | 固定 JJX-QR-005 |
| 日期 | info.planStartDate 或今天（取 planStartDate，空取当前日期） |
| NO | 1（单产品工单） |
| 品名 | info.productName |
| 订单数量 | info.plannedQuantity |
| 交期 | info.planEndDate |
| 机种号 | info.productCode |
| 订单号 | info.salesOrderNo（空 '-'） |
| 生产批号 | 无数据源 → '-'（观察项：批号字段不存在，向用户说明） |
| 库存 | 无数据源 → '-'（观察项同上） |
| 备注 | info.remark |

## 改动（jjx-web/src/views/production/order/print.vue，参照 purchase/order/print.vue 双版式结构）

1. 工具栏加版式选择（el-radio-button：系统版 / 纸版(QR-005)），localStorage key
   `production-order-print-layout`（'system'/'qr005'，默认 system），切换即时重渲染；
2. A 分支 = 现有系统版模板原样包进 v-if（结构/class 不动）；
3. B 分支 = 新增纸版复刻：宋体风格区（参照采购 QR-024 分支样式.qr024-*），公司头居中、
   标题"制造指令单"、右上 JJX-QR-005+日期、8 列细线表（生产批号/库存列显示 '-'）、备注、
   右上角加工单二维码（复用现有 qrDataUrl/genQr，qr 已在系统版标题行，B 分支再放一个 top-right）；
4. A4Canvas + window.print + createQualityTemplatePrintLog(5,...) 两版式共用；
5. 不 git commit；不动其它文件；文件换行保持原样；npx vue-tsc --noEmit 报告。

## 收尾（agent 侧）

- 提交后把 quality_template_registry QR-005 行 print_mode 改 dual（print_component 已是
  views/production/order/print.vue）；任务 1417 → status=2 待审核；
- 交付报告列出观察项：①公司抬头名称差异（模板印厂名 vs 系统配置公司名）
  ②生产批号/库存列无数据源。
