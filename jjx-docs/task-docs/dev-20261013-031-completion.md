# 作业规范发行信息：发行单位/发行日期录入入口

> 任务码：dev-20261013-031。关联：dev-20261013-029（口径定稿）。交付录入入口；真实接口/浏览器/打印未验证。

## 1. 目标与口径

在作业规范「变更内容 · 发行单位」区块的**编辑态**提供两个可录入字段：
- **发行单位**（`issueUnit`，默认“工程部”，可改）；
- **发行日期**（`issueDate`，默认今天，可改；**前端禁止选未来日期**）。

口径（沿用 029 定稿）：发布（`publishedAt`）/批准（`confirmedAt`）/发行（`issueDate`）三者分列；发行日期展示 = `issueDate ?? (approved ? confirmedAt : null)`。

## 2. 改动（前端，最小）

`ProductWorkSpecPanel.vue`：
1）发行块：编辑态用 `el-input`（发行单位）+ `el-date-picker`（发行日期，`value-format=YYYY-MM-DD`、`:disabled-date` 禁未来）；只读态显示 发行单位/发行日期（生效口径）/发布日期。
2）`startIssuanceEdit`：进入编辑时若 `issueDate` 为空**预填今天**。
3）新增 `effectiveIssueDate` 计算量（展示口径，与纸张一致）；`isFutureDate`、`todayString` 工具。

**集成点**：走现有通用保存 `productWorkSpecApi.save(productId, spec)`（`ProductWorkSpecDTO` 已含 `issueUnit/issueDate`）→ **不改 confirm 接口、无迁移、无后端改动**。保存经 `@Log` 落 `sys_oper_log`。

## 3. 约束遵循

- **不启用“计划未来生效”**：前端禁未来日期；不改取版逻辑（现状仍按“最新已发布版本”取版）。
- 已批准后随版本冻结（P2 快照 `confirmation.issueUnit/issueDate`）。
- 编辑入口沿用现有 `canEdit`（`product:edit`）门禁与保存接口自身权限；未扩大权限。

## 4. 验证

- `npx vue-tsc --noEmit` → 0 error；`npm run check:docs` 通过。
- 说明：模板/表单规则无独立纯逻辑模块（`__tests__/` 无对应可测单元），以类型检查 + 代码复核为准。

## 5. 未验证 / 遗留

- 真实接口、浏览器、实际打印未验证（沿用“仅代码与模拟测试”边界）。
- 若后续要“计划未来生效”，需评估取版本逻辑（按 `issueDate` 生效区间取版）——本卡未做。
