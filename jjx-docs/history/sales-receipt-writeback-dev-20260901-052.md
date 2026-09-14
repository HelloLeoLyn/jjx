# 收款回写订单付款状态（dev-20260901-052）

- 任务来源：sys_task dev-20260901-052（2026-09-01 登记）
- 依据：jjx-docs/analysis/sales-module-closure-analysis-20260831.md（第四节 回写缺失：收款不影响订单付款状态）

## 一、根因

1. `SalesPaymentStatusEnum`（1未支付/2支付中/3已支付/4部分支付/5已退款）全系统仅 2 处引用：枚举自身 + `SalesOrderConverter`（展示层转换）。**没有任何写入方**。
2. `SalesReceiptServiceImpl.create()`（39 行）插入收款单后直接返回，不碰订单——收款单和销售订单是两条平行线，系统无法回答"这张订单收了多少钱、还欠多少"。
3. `sales_order` 表已有 `payment_status / paid_amount / unpaid_amount` 三列（建单时 Converter:126 默认 UNPAID=1，paid/unpaid 从未被写）——**设计意图就是收款回写订单，只是没人实现**。
4. 前端收款页 `views/sales/receipt/index.vue` **没有新建入口**（:11 注释"扩展位：后续新增/编辑收款单按钮放在此处"），收款单只能 API 创建——回写逻辑需要新建表单才能触发和验证。

## 二、设计决策（已定，不要改）

1. **回写时机 = 收款单创建时**：`create()` insert 成功后立即回写关联订单。收款单无草稿/确认流程（status 直接 1 正常），无独立 confirm 端点，不做"确认后回写"。
2. **累计口径**：`paid = SUM(actual_amount)`，仅统计该订单下 `status = 1`（正常）的收款单。`actual_amount` 为 null 时按 `receipt_amount` 兜底（create 已有此兜底逻辑，实际都会写入）。
3. **应收基数 = `final_amount`**（订单最终金额），null 时依次 fallback `total_amount_with_tax` → `total_amount`。
4. **状态判定**：paid <= 0 → UNPAID(1)；paid >= 应收 → PAID(3)；否则 PARTIAL_PAID(4)。**REFUNDED(5) 不自动写**（留给退货任务 dev-20260901-053）。
5. 回写写三列：`payment_status / paid_amount / unpaid_amount`（unpaid = max(应收 - paid, 0)）。
6. **无表结构变更、无 migration**。
7. 前端补"新增收款"表单（052 的必要组成部分——否则回写不可达）。**不做**收款单 update/delete（独立事项，未登记）。

## 三、后端改动

### 3.1 `SalesReceiptServiceImpl.java`（service/impl/）

- `create()` 加 `@Transactional(rollbackFor = Exception.class)`；insert 成功后，若 `receipt.getOrderId() != null && !Integer.valueOf(0).equals(receipt.getStatus())`（status 0=作废不参与）→ 调 `updateOrderPaymentStatus(orderId)`。
- 新增私有方法 `updateOrderPaymentStatus(Long orderId)`：
  1. `salesOrderMapper.selectById(orderId)`，不存在则 warn 返回（不阻断收款）；
  2. 累计已收：`receiptMapper` 按 `orderId` + `status=1` 查收款单列表，`paid = sum(COALESCE(actualAmount, receiptAmount))`（用 BigDecimal 累加，null 元素跳过）；
  3. 应收基数 `target = finalAmount != null ? finalAmount : (totalAmountWithTax != null ? totalAmountWithTax : totalAmount)`，全 null 则只更新 payment_status 不写金额；
  4. 计算 `paymentStatus`（见设计决策 4）、`paidAmount = paid`、`unpaidAmount = max(target - paid, 0)`；
  5. `updateById` 更新订单这三列（构造一个只含 orderId + 三列的 SalesOrder 更新对象，避免全字段覆盖）。
- 注入 `SalesOrderMapper`（构造变化——已确认测试目录无任何类引用 SalesReceiptServiceImpl，零测试影响）。

### 3.2 `SalesReceiptController.java`（controller/）

- 不动。create 端点已有 @Log。

## 四、前端改动

### 4.1 `jjx-web/src/views/sales/receipt/index.vue` 加"新增收款"

- 工具栏（:11 扩展位）放"新增收款"按钮 → 弹窗表单：
  - **选订单**：下拉选择（调 `orderApi` 分页/搜索接口，选项显示 orderNo + customerName）；选完调订单详情接口，展示只读三数：应收(finalAmount) / 已收(paidAmount) / 欠款(unpaidAmount)（金额用现有 money 格式化；字段缺失显示 '-'）
  - 表单字段：receiptDate（默认今天）、receiptType（下拉：1定金/2进度款/3尾款）、paymentMethod（下拉：复用 SalesReceiptPaymentMethodEnum）、receiptAmount、actualAmount（默认=receiptAmount）、currency（默认 CNY）、remark
  - 提交调 `salesReceiptApi.create`，成功提示 + 刷新列表
- **枚举合规**：receiptType 若前端无现成枚举，新建 `jjx-web/src/enums/sales/ReceiptTypeEnum.ts`（createNamedEnum，1定金/2进度款/3尾款），并在 `enums/sales/index.ts` 导出。**禁止页面内 STATUS_MAP/写死数字**（AGENTS.md）。
- 列表可加"订单号"列（row.orderId 存在即可显示，不加联查）。

### 4.2 其他前端

- 订单列表付款状态 tag 已有（PaymentStatusEnum + Converter paymentStatusDesc），不动。
- 不动 tracking / order 页。

## 五、已知风险（必须遵守）

1. **工作区有用户 WIP，不要碰**：`git status` 里被删除/修改的测试文件（OrderStatusReviewFlowTest.java、OperLogAspectTest.java、Inventory*InvariantTest.java 等）和 `jjx-docs/analysis/` 下 20260831 两个分析文档都是用户的东西。只改本 spec 列出的文件。
2. **不要 git commit**。
3. 本次**无 migration SQL**（无表结构变更），不需要写 SQL 文件。
4. 前端枚举遵守 AGENTS.md；vue-tsc 报错若来自用户 WIP 文件（order.ts/OrderForm.vue 等）过滤掉，保证自己文件零错误。
5. 收款单 status 字段语义：1 正常 / 0 作废（SalesReceiptController:112-118 statusText），回写只统计 1。
6. `check:status-enums` 门禁：新枚举文件必须走 createNamedEnum 模式，不许裸数字。

## 六、验证

Codex 内：
1. `cd jjx-server && mvn -o clean test-compile`（必须含 clean）
2. `cd jjx-web && npm run validate`（按风险 4 过滤用户 WIP 错误）

我外面：
3. 无 migration 可应用；后端 compile + 前端 vue-tsc + check:status-enums 复查
4. git diff review

## 七、明确不做

- 收款单 update / delete / 作废回写（独立事项）
- REFUNDED 状态自动回写（dev-20260901-053 退货任务做）
- 逾期应收报表、账龄统计（后续再说）
- 收款打印页改动（已有 print 页）
