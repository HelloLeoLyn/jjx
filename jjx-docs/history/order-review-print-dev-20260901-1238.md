# 订单评审模板联动打印：QR-047 合同评审记录表 + QR-053 合同更改评审单（dev-20260901-1238）

- 任务来源：sys_task dev-20260901-1238（P2）
- 依据：jjx-docs/analysis/print-system-analysis-20260831.md（sales_order_review 2 份无打印页）

## 根因

QR-047/QR-053 挂 biz_type=sales_order_review 但 category=blank，只能打空白表；订单评审数据（OrderReviewRecord + review_flow）无打印出口。

## 数据源（已核实）

- 订单评审记录：`OrderReviewRecord`（sales_order_review 表：orderId/orderNo/reviewStage/stageName/reviewerName/reviewComment/reviewTime/reviewResult）
- 审核流水：`review_flow`（biz_type='sales_order'，bizId=orderId：actionCode/actionName/operatorName/comment/createTime）
- 订单信息：sales_order（orderNo/customerName/金额等）

## 改动

### 后端（1 处）

`jjx-server/src/main/java/com/jjx/sales/controller/OrderReviewController.java` 加评审记录查询端点：
```java
@Operation(summary = "订单评审记录列表")
@SaCheckPermission("sales:order:view")
@GetMapping("/records/{orderId}")
public Result<List<OrderReviewRecord>> records(@PathVariable Long orderId) { ... }
```
实现：OrderReviewServiceImpl 加 listByOrder(orderId)（按 orderId 查 OrderReviewRecord 按 reviewTime 倒序）；Controller 注入 IOrderReviewService 调用。若 IOrderReviewService 已有等价方法则复用。

### 前端（2 个文件）

1. 新建 `jjx-web/src/views/sales/order/review-print.vue`（A4Canvas 打印页，参照 sales/delivery/print.vue 模式）：
   - 路由：`/sales/order/review-print?orderId=xxx&templateId=47|53`（静态路由注册到 router/index.ts，hidden，参照 SalesDeliveryPrint）
   - 版式：PrintCompanyHeader + 标题（templateId=53 显示"合同更改评审单"，否则"合同评审记录表"）+ 订单信息区（订单号/客户/订单日期/金额）+ 评审记录表（阶段/审核人/审核时间/审核结果/意见，数据=records 接口）+ 审核历史表（动作/操作人/时间/意见，数据=/api/trace/reviews?bizType=order&bizId=orderId）+ 签字区
   - 打印留痕：调 createQualityTemplatePrintLog(templateId, 'sales_order_review', orderId)（1237 已支持 biz 参数）
   - 数据获取：orderApi.getOrder(orderId) + records 接口 + reviews 接口（api/sales/order.ts 或直接 request）
2. `jjx-web/src/api/sales/order.ts` 加 reviewRecords(orderId) 方法（GET /sales/order/review/records/{orderId}）

### 入口

订单页操作区（views/sales/order/index.vue 操作列，有评审历史的行）加"评审表打印"按钮 → router.push review-print?orderId=&templateId=47。若订单页操作列改动风险大，改为在 OrderDetailDrawer 或订单跟踪页加入口（Codex 判断哪个最小改动，报告说明）。

## 风险

- 工作区有用户 WIP（sales OrderController/OrderServiceImpl/order.ts/OrderForm.vue 是用户 WIP 高发文件——order.ts 要加方法，注意不要动用户未提交的改动；若 order.ts 有未提交改动，改用独立 api 文件或报告）
- 只改本 spec 列出的文件（OrderReviewController、OrderReviewServiceImpl、IOrderReviewService、router/index.ts、order.ts 或新 api、review-print.vue、订单页入口文件）
- 不要 git commit；无 migration
- AGENTS.md：状态展示用枚举
- vue-tsc 报错先区分用户 WIP（order.ts/OrderForm.vue 等）

## 验证

- mvn -o clean test-compile
- npx vue-tsc --noEmit（自己文件零错误）
- npm run check:status-enums
- 报告剩余问题
