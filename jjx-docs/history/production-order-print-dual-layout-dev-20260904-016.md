# 生产工单打印（列表批量+列勾选，内容对齐 QR-005）双版式（dev-20260904-016，任务1417）

状态：⏳待实施（2026-09-04 需求更正：打印针对【列表】勾选，非单详情；内容严格对齐模板，不得自造字段）

## 需求（用户复述确认 2026-09-04）

1. 入口在生产工单【列表】页：勾选 N 行 → 点"打印指令单"→ 选几行打几行（每勾选工单占一行）；
2. 打印页可【勾选列】：列集=QR-005 模板表头（NO/品名/订单数量/交期/机种号/订单号/生产批号/库存），
   勾了哪些打哪些，默认全选，选择记忆（localStorage）；
3. 输出"制造指令单"：双版式（系统版/纸版 QR-005）都输出同一套模板内容——系统版内容也必须
   严格对齐模板（8 列表格），不许自造字段/自造版块；
4. 单工单详情打印保留（单 id 进入 = 一行）。

## 模板纸版结构（已读 jjx-docs/print_template/QR-005生产指令单.xlsx）

公司名行｜制造指令单｜右上 编号 JJX-QR-005 + 日期｜表头 8 列（NO 品名 订单数量 交期 机种号
订单号 生产批号 库存）｜多行数据｜备注区

## 数据映射（每行=一个工单）

| 纸版列 | ERP 来源 |
|----|----|
| NO | 序号（1..N） |
| 品名 | info.productName |
| 订单数量 | info.plannedQuantity |
| 交期 | info.planEndDate |
| 机种号 | info.productCode |
| 订单号 | info.salesOrderNo（空 '-'） |
| 生产批号 | 无数据源 → '-'（观察项待用户口径） |
| 库存 | 无数据源 → '-'（观察项待用户口径） |
| 备注区 | info.remark（仅第一个工单或汇总？— 默认取所勾选工单 remark 非空的拼接，Codex 以最简单合理实现并报告） |

## 改动

1. jjx-web/src/views/production/order/index.vue：列表操作栏加"打印指令单"按钮（批量区），
   未勾选点击提示先勾选；勾选后跳打印路由（多 id，如 query ids=1,2,3 或批量参数，Codex 按现有路由最简方式）；
2. jjx-web/src/views/production/order/print.vue：支持多工单（single :id 兼容 = 一行）；
   工具栏（no-print）加：①版式选择（系统版/纸版 QR-005，localStorage production-order-print-layout）
   ②列勾选 checkbox 组（8 列，默认全选，localStorage production-order-print-cols）；
   系统版与纸版分支渲染【同一列集】的行式表格，只换样式风格（纸版=宋体细线仿原版；系统版=现有简洁风），
   内容与列 = 模板 8 列，不多不少；多行场景顶部大二维码去掉（无单一编码对象），纸版右上角保留
   JJX-QR-005+日期；打印留痕 createQualityTemplatePrintLog(5,...)；
3. 不 git commit；不动其它文件；npx vue-tsc --noEmit 报告。

## 收尾

QR-005 行 print_mode=dual 已置；1417 → status=2 待审核；观察项（公司头名称差异/生产批号/库存列无源）
