# ATT-V1B-10 报告：状态枚举门禁存量清零 + 基线收窄（dev-20261012-035）

> 任务码：dev-20261012-035（ATT-V1B-10）｜ 日期：2026-10-12 ｜ 状态：已完成（`npm run check:status-enums` 退出码 **0**）
> 关联：dev-20261012-002（公共附件总任务）

## 0. 白名单改动（git diff --stat 仅此 3 个）

| 文件 | 变更 |
|---|---|
| `jjx-web/src/composables/usePrintFieldSuggest.ts` | 删本地状态映射，改公共枚举 |
| `jjx-web/src/views/production/order/components/OrderBatchActions.vue` | 5 处数字字面量 → 枚举成员 |
| `jjx-web/scripts/status-magic-baseline.json` | 收窄（−8 行） |

## 1. 改前门禁（退出码 1）

```
检测到新增状态魔法值：
- src/composables/usePrintFieldSuggest.ts:18 [local-status-map] const FRAME_STATUS_LABEL: Record<string, string> = {
- src/composables/usePrintFieldSuggest.ts:30 [local-status-map] const FRAME_STATUS_TAG: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
- src/views/production/order/components/OrderBatchActions.vue:105 [status-comparison] return props.selectedRows.every((row) => row.orderType === 'plan' && row.orderStatus === 1)
- src/views/production/order/components/OrderBatchActions.vue:110 [status-comparison] return props.selectedRows.every((row) => row.orderType === 'work_order' && row.orderStatus === 4)
- src/views/production/order/components/OrderBatchActions.vue:115 [status-comparison] return props.selectedRows.every((row) => row.orderType === 'work_order' && row.orderStatus === 6)
- src/views/production/order/components/OrderBatchActions.vue:120 [status-comparison] return props.selectedRows.every((row) => row.orderStatus !== 8 && row.orderStatus !== 9)
- src/views/production/order/components/OrderBatchActions.vue:125 [status-comparison] return props.selectedRows.every((row) => row.orderStatus === 0 || row.orderStatus === 9)
有 8 条存量违规已消失，请审核后更新基线，防止其重新进入。
EXIT=1
```

## 2. 改动说明

### 2.1 `usePrintFieldSuggest.ts`

- 删除本地 `FRAME_STATUS_LABEL` / `FRAME_STATUS_TAG`；改用 `ScreenFrameStatusEnum`（`@/enums/engineering/resource`）。
- `frameStatusLabel(s)`：`enum.canDo(s) ? enum.getLabel(s) : (s || '')` —— 未知值仍回退原值（与旧行为一致）。
- `frameStatusOf(...)`：改用 `enum.getLabel/getTagProps`；**EMPTY 显式覆盖**为 `{label:'未制版', type:'warning'}`。

**界面文案保持说明（关键）**：EMPTY 在两处语境文案不同——
- label 语境（`frameStatusLabel`，网框编号下拉提示）：**"空框"**（= 枚举默认 label，保持）；
- tag 语境（`frameStatusOf`，工序行"变动可感知"高亮）：**"未制版" + warning**（≠ 枚举默认"空框"/info）→ 在调用处**显式覆盖**并加注释保留。
- 其余 PLATED/MAINTENANCE/SCRAPPED 两处文案与 tag 色与枚举**完全一致** → 无变化。

### 2.2 `OrderBatchActions.vue`

5 处数字字面量 → `ProductionOrderStatusEnum` 成员：`PENDING_APPROVAL(1)` / `PLANNED(4)` / `IN_PROGRESS(6)` / `COMPLETED(8)` / `CANCELLED(9)` / `DRAFT(0)`。

**plan / work_order 词表核对结论：二者共用同一状态词表**（非独立值域），故可按同一枚举替换（未按中文标签猜）：
- 前端 `src/types/production/order.ts`：`OrderStatus` 枚举（注释「统一订单状态」），`ProductionPlan`(plan) 与 `WorkOrder` 均声明 `orderStatus: OrderStatus`。
- 后端 `ProductionOrderServiceImpl`：PLAN 与 WORK_ORDER 行统一用 `ProductionOrderStatusEnum`（:1285 工单置 `PLANNED`；:778 计划按 `PLANNED` 过滤）；:97 注释「orderType 统一大写（PLAN/WORK_ORDER）」。
- 后端枚举 `ProductionOrderStatusEnum`：0 DRAFT / 1 PENDING_APPROVAL / 4 PLANNED / 6 IN_PROGRESS / 8 COMPLETED / 9 CANCELLED —— 与前端值一致。

## 3. 基线收窄（只允许缩小）

- `node scripts/check-status-magic-values.mjs --write-baseline` → **122 处**（原 **130**）。
- `git diff scripts/status-magic-baseline.json`：**仅 8 行删除、0 新增**（只缩小）。删除 8 条：

```
- src/views/inventory/outbound/index.vue|local-status-map|const statusMap: Record<number, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
- src/views/inventory/outbound/index.vue|status-comparison|<el-button v-if="row.status === 0" ... @click="handleEdit(row)"
- src/views/inventory/outbound/index.vue|status-comparison|v-if="row.status === 1 || row.status === 2"
- src/views/production/order/components/OrderBatchActions.vue|status-comparison|(row) => row.orderStatus !== 8 && row.orderStatus !== 9
- src/views/production/order/components/OrderBatchActions.vue|status-comparison|(row) => row.orderStatus === 0 || row.orderStatus === 9
- src/views/production/order/components/OrderBatchActions.vue|status-comparison|(row) => row.orderType === 'plan' && row.orderStatus === 1
- src/views/production/order/components/OrderBatchActions.vue|status-comparison|(row) => row.orderType === 'work_order' && row.orderStatus === 4
- src/views/production/order/components/OrderBatchActions.vue|status-comparison|(row) => row.orderType === 'work_order' && row.orderStatus === 6
```

（5 条 OrderBatchActions 为旧文本指纹，本次重写后自然消失；3 条 inventory/outbound 为存量已消失项，一并收窄。）

## 4. 改后验证（真实输出）

- `npm run check:status-enums` → `状态魔法值门禁通过（存量基线 122 处，新增 0 处）` **EXIT=0**
- `npx vue-tsc --noEmit` → **EXIT=0**
- `npm run check:docs` → 通过
- `git diff --stat` → 仅 §0 三个白名单文件

## 5. 未执行声明

未扩豁免、未新增基线条目、未改后端/数据库、未改无关页面、未全量测试、未新增测试类、未 commit/push。
