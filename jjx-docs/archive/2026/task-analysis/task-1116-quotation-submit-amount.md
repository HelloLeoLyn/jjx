# 任务 1116 分析：报价提交审核提示"报价金额必须大于0"

> 任务：dev-1787711335167（kanban_module=dev）
> 优先级：normal | 状态：未开始(0) | 负责人：未分配 | 截止：2026-08-26
> 分析日期：2026-08-26 | 分析人：大黄 | 状态：仅分析，未改动代码

---

## 一、现象

报价单产品明细已有金额且大于 0，点"提交审核"却提示 **"报价金额必须大于0"**。

## 二、根因分析

### 校验逻辑

`QuotationServiceImpl.validateQuotationForReview()`（submitReview / sendQuotation 共用）只校验报价单**表头**金额：

```java
if (quotation.getTotalAmount() == null || quotation.getTotalAmount().compareTo(ZERO) <= 0) {
    throw new BusinessException("报价金额必须大于0");
}
if (quotation.getFinalAmount() == null || quotation.getFinalAmount().compareTo(ZERO) <= 0) {
    throw new BusinessException("最终金额必须大于0");
}
```

表头金额（subtotal/tax/total/final）**只在"保存报价单"时**由明细汇总刷新（`saveQuotationItems` 尾部自动汇总，参考销售订单口径）。提交审核时校验的是 **DB 里的旧值**。

### 根因链条（DB 实锤）

数据库中现有 2 张报价单**都是"由询价单自动创建"**（remark 确认），关键缺陷在**询价转报价入口**：

1. **`InquiryServiceImpl.convertToQuotation()` 建单时表头金额全为空**：
   - 创建 SalesQuotation 时**不设置** subtotalAmount/taxAmount/totalAmount/finalAmount（insert 为 null）
   - 明细由 `createQuotationItemFromInquiry` 生成，**单价/金额默认 0**（注释明示"待销售定价后修改"）
   - **不调用 saveQuotationItems** → 表头金额永远不会自动算出来
   - → 转换生成的报价单表头金额恒为 null/0，**直到销售在编辑表单里点过一次"保存"** 才被重算

2. **提交审核校验的是 DB 旧值，且不会先保存表单**：
   - 销售打开报价单表单填写单价 → **表单里明细金额 > 0**（用户看到的就是这个）
   - 点"提交审核"走列表入口（DEV-706 详情确认弹窗），**不会先保存当前表单**
   - 后端 submitReview 从 DB 读表头（仍是 null/0）→ 报"报价金额必须大于0"
   - → 用户看到"明细有金额"却报"金额必须大于0"，误以为 bug

3. **数据佐证**：QT2608260001 创建于 2026-08-26 10:26:30（询价转报价），任务 10:28:55 提交，表头金额直到 **10:42:36 保存后才**变成 8.00。任务提交时表头就是 0。

### 附带隐患

- `updateQuotation()` 采用"先删后插"：若 payload `items` 为 null/空 → `saveQuotationItems` 直接 return（不重算），但**旧明细已被删除** → 明细被清空 + 表头保留旧值，存在数据丢失风险
- 错误信息"报价金额必须大于0"未提示"需保存/完善明细单价"，误导性强

## 三、修复方案

### 后端（核心，改动小）

1. **提交/发送前兜底重算**：`submitReview`、`sendQuotation` 在 validate 之前，先按 DB 当前明细重算表头金额（把 `saveQuotationItems` 尾部的汇总段抽成独立方法 `recalcQuotationAmounts(quotationId)` 复用）
   - 效果：明细有金额 → 表头自动修正，不再误报；明细金额真为 0 → 依然正常报错（业务正确）
2. **`convertToQuotation` 建单后调同一重算**：口径统一（当时明细为 0 结果 0，但后续保存/提交自动收敛）
3. **`updateQuotation` 空明细保护**：items 为 null/empty 时跳过"先删后插"，避免误删明细 + 表头失真
4. **错误信息优化**：明细非空但表头金额 ≤ 0 时提示"报价金额未正确汇总：请完善明细单价并保存后再提交"

### 前端（配套，推荐但可选）

5. **提交审核前自动保存**：`handleSubmitReview` 时若报价单编辑表单存在未保存改动（或简化：提交确认前先调一次 `quotationApi.edit` 再 `submitReview`）
   - 解决"改了价没保存直接提交审核"这个最常踩的坑

### 重算口径（与现有 saveQuotationItems 完全一致）

```
subtotal = Σ 行金额(数量×单价)
tax      = subtotal × 税率% ÷ 100（四舍五入2位）
total    = subtotal + tax（含税）
final    = max(total - 折扣, 0)
```

## 四、待确认

1. 是否只做后端兜底（方案 1-4），还是连前端自动保存（方案 5）一起做？
2. 重算口径沿用现有口径，无异议？
