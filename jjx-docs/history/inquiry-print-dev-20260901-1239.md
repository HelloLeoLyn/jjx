# 询价-样品需求单联动打印：QR-065（dev-20260901-1239）

- 任务来源：sys_task dev-20260901-1239（P2）
- 依据：jjx-docs/analysis/print-system-analysis-20260831.md（sales_inquiry 1 份无打印页，有 quotation/print.vue 可参照）

## 根因

QR-065（样品需求单）挂 biz_type=sales_inquiry 但 category=blank，只能打空白表；询价单数据无打印出口。

## 数据源（已核实）

- 询价单：sales_inquiry，已有 GET /sales/inquiry/{inquiryId} 详情接口（InquiryController:54）
- 前端 api：jjx-web/src/api/sales/inquiry.ts（getInquiry/inquiryDetail 之类方法，Codex 先看现有方法名）

## 改动（前端 3 个文件，零后端改动）

1. 新建 `jjx-web/src/views/sales/inquiry/print.vue`（A4Canvas 打印页，参照 sales/quotation/print.vue 模式）：
   - 路由：`/sales/inquiry/print?inquiryId=xxx`（静态路由注册 router/index.ts，hidden，参照 SalesDeliveryPrint）
   - 版式：PrintCompanyHeader + "样品需求单"标题 + 询价单信息（单号/客户/日期/联系人）+ 需求内容区（产品描述/特殊要求/图纸标识/数量/备注，字段名以 inquiry 详情接口返回为准）+ 签字区
   - 打印留痕：createQualityTemplatePrintLog(65, 'sales_inquiry', inquiryId)（65 = QR-065 模板 id，1237 已支持 biz 参数）
   - 数据：inquiry 详情接口（api/sales/inquiry.ts 现有方法）
2. `jjx-web/src/router/index.ts`：注册 /sales/inquiry/print 静态路由
3. `jjx-web/src/views/sales/inquiry/index.vue`：操作列加"打印"按钮 → router.push /sales/inquiry/print?inquiryId=（若操作列已有按钮区则追加一行，最小改动）

## 风险

- 工作区有用户 WIP，只改本 spec 列出的 3 个文件
- 不要 git commit；无 migration、无后端改动
- vue-tsc 报错先区分用户 WIP
- inquiry 详情接口返回字段名以实际为准（先读 api/sales/inquiry.ts 和详情接口响应再写页面）

## 验证

- npx vue-tsc --noEmit（自己文件零错误）
- npm run check:status-enums
- 报告剩余问题
