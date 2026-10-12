# BOM 材料「整单通用」标记 + 源头校验（不静默 / 不丢数据）—— 实施记录

> 任务码：dev-20261012-039 ｜ 日期：2026-10-12 ｜ 状态：已实现（**迁移 274 待应用**）
> 决策：Leo 2026-10-12「按你的建议来」

---

## 1. 背景

- 「成品方案」带出（dev-20261012-012）发现：BOM 材料可以 `process_id` 为空（例：SP261012001 产品的「透明易撕贴」RM001612），下游「按工序挂料」会**静默丢弃**。
- 已在 dev-20261012-012 做了**带出兜底**（不静默：警告 + 暂挂首道工序 + 弹窗）。
- 本任务做**源头治理**：**未挂工序 ≠ 数据缺失**，应成为一种**显式选择**。

## 2. 决策（Leo）

认「**整单通用材料**」为**一等概念**：BOM 材料可合法地不挂工序（带通用标记）；未挂工序且未标通用 → **提交审核被拦**（不处理不给操作），并给出处理方式（指定工序 / 勾选通用）。

## 3. 落地

### 3.1 DB

- 迁移：`jjx-docs/sql/migrations/274_bom_item_common_material.sql`（**幂等**）
  - `engineering_bom_item` 新增 `is_common tinyint(1) NOT NULL DEFAULT 0`（0 否 / 1 是「整单通用」）。
- 执行通道：`scripts/db-migrate.sh`（写模式须**手工全库备份** + 真实任务码）。

### 3.2 后端

- 实体 `EngineeringBomItem`、DTO `EngineeringBomItemDTO` 增 `isCommon`；`EngineeringBomServiceImpl.toBomItemEntity` 映射（null→false）。
- `EngineeringBomServiceImpl.checkBomForSubmit`：明细 `process_id` 为空**且**未标通用 → 产出问题项「未挂工序（请在「项目」选择工序，或勾选「整单通用」）」。
  - `submitApprove` 原本就依据 `checkBomForSubmit` 拦截，故**提交审核即被拦**（不静默）。

### 3.3 前端

- `types/product/bom.ts`：`EngineeringBomItem.isCommon`。
- `components/BomItemEditor.vue`：新增「**通用**」列（复选框）。勾选→清空项目；选项目→自动取消通用（互斥）。
- `views/product/bom/components/BomFormDialog.vue`：Excel 导入后，若存在未匹配「项目」的行 → **弹窗明确提示**处理方式（不静默）。
- 提交前预检 `views/product/bom/index.vue`（`checkEngineeringBom`）会自动展示上述问题。

## 4. 验证

- 后端：`JAVA_HOME=java-21 mvn -o compile` ✅
- 前端：`npx vue-tsc --noEmit` ✅
- 迁移预览：`bash scripts/db-migrate.sh 274_bom_item_common_material.sql` ✅（仅预览，未执行）

## 5. 待办 / 依赖（重要）

1. ⚠️ **迁移 274 尚未应用**。需先**手工全库备份**，再：
   ```
   bash scripts/db-migrate.sh 274_bom_item_common_material.sql --yes \
        --task dev-20261012-039 --backup <手工全库备份.sql>
   ```
   **应用前不要用新代码重启后端**（否则查询引用 `is_common` 会报错）。
2. 存量治理（可选）：全量体检「`process_id` 空 且 `is_common=0`」的明细，列出供人工逐条处理（指定工序或标通用）。
