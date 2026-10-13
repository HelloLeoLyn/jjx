# 作业规范发行信息口径对齐（页面发行单位/日期与快照契约）

> 任务码：dev-20261013-029。父：dev-20261012-015（已核销，口径=现状）。依赖：P2/P3 快照（DOCSET_JSON）。
> 交付口径定稿 + 最小改动；真实接口/浏览器/实际打印未验证。

## 1. 口径定稿（发布 / 批准 / 发行 三者分列，不混用）

| 概念 | 字段 | 含义 | 页面 | 纸张 |
|---|---|---|---|---|
| 发布 | `publishedAt` / `publishedBy` | 系统创建不可变版本的时刻 | 「发布日期」= 最新发布版本 `publishedAt` | 不显示 |
| 批准 | `approved` / `confirmedBy` / `confirmedAt` | 源头确认指纹匹配 | 各页面按需 | 已批准才盖圆章 |
| 发行 | `issueUnit` / `issueDate` | 规范正式发车间的单位与日期 | 「发行单位」= `issueUnit` | 「发行日期」= 生效发行日 |

**发行日期规则**（统一）：`发行日期 = issueDate ?? (approved ? confirmedAt : null)`，即**优先 `issueDate`；未填且已批准回退 `confirmedAt`；未批准留空**。
（不用“confirmedAt 直接替换 issueDate”：那是回退，不是替换，将来填了 `issueDate` 仍优先。）

## 2. 改动（最小）

1）`ProductWorkSpecPanel.vue` 发行块：
   - 「发行单位」由硬编码 `工程部` 改为读 `issueUnit`（单一真源）；
   - 「发行日期」→ 改标签为「**发布日期**」（值仍为最新发布版本 `publishedAt`），与纸张「发行日期」不再同名。
2）`WorkSpecPaper.vue`：新增 `effectiveIssueDate` 计算量（上述规则），**页脚「发行日期」与「已批准」圆章统一**用它。

## 3. 效果

- 页面：「发行单位 = issueUnit」「发布日期 = 最新发布版本」；
- 纸张：「发行日期 = issueDate，空则已批准回退 confirmedAt，未批准为空」；
- V1/V2（未批准、`issueDate=null`）→ 纸张发行日期为空（合理：尚未发行）；
- 已批准规范 → 发行日期显示 `confirmedAt`（定稿发行日）。

## 4. 验证

- `npx vue-tsc --noEmit` → 0 error；`npm run check:docs` 通过。
- 说明：本改动为模板取值/标签规则，无独立纯逻辑模块（`__tests__/` 无对应可测单元），故以类型检查 + 代码复核为准。

## 5. 未验证 / 遗留

- 真实接口、浏览器、实际打印未验证（沿用“仅代码与模拟测试”边界）。
- `issueDate` **当前无录入入口**（全库为 null）→ 未批准规范纸张发行日期恒空；若要“严格区分发行≠批准”，需**另立卡**加 `issueDate` 录入入口（本卡不做）。
