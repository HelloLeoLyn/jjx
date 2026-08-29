# 报价单重构状态魔法值替换（20 处）

`npm run check:status-enums` 当前红灯：20 条新增状态魔法值，全部在报价单重构产物里。
脚本同时报「有 20 条存量违规已消失」——原 index.vue 的同型违规被拆到新组件后指纹变化，
既算新增、又让基线条目失配。按 AGENTS.md：**基线只能删不能加**，必须把这 20 处改成枚举成员。

## 必须使用的枚举

`jjx-web/src/enums/sales/QuotationEnum.ts` 的 `QuotationStatusEnum`
（createNamedEnum，取值用 `QuotationStatusEnum.XXX.value`，与仓库其它页面现有用法保持一致）：

| 成员 | value | 含义 |
|---|---|---|
| DRAFT | 0 | 草稿 |
| SENT | 1 | 已发送 |
| ACCEPTED | 2 | 已确认 |
| REJECTED | 3 | 已拒绝 |
| EXPIRED | 4 | 已过期 |
| PENDING_REVIEW | 5 | 待审核 |
| APPROVED | 6 | 已审核 |
| MODIFYING | 8 | 改单 |
| COMPLETED | 9 | 已完成 |

## 待替换清单（脚本原始输出，逐条对应）

`src/views/sales/quotation/components/QuotationTableColumns.vue`
- :92  `row.quotationStatus === 6` → APPROVED
- :128 `row.quotationStatus === 9` → COMPLETED
- :146 `row.quotationStatus === 1` → SENT
- :155 `row.quotationStatus === 1` → SENT
- :164 `row.quotationStatus === 5` → PENDING_REVIEW
- :173 `row.quotationStatus === 5` → PENDING_REVIEW
- :214 `![1, 2, 3, 4].includes(row.quotationStatus) && row.quotationStatus !== 9`
       → SENT/ACCEPTED/REJECTED/EXPIRED + COMPLETED
- :218 `![1, 2, 5, 6, 8, 9].includes(row.quotationStatus)`
       → SENT/ACCEPTED/PENDING_REVIEW/APPROVED/MODIFYING/COMPLETED

`src/views/sales/quotation/composables/useQuotation.ts`
- :135 `canDelete: ![1, 2, 5, 6, 8, 9].includes(status) && !completed`
- :136 `canEdit: ![1, 2, 3, 4].includes(status) && !completed`

`src/views/sales/quotation/index.vue`
- :134 === 6 → APPROVED
- :170 === 9 → COMPLETED
- :188 === 1 → SENT
- :197 === 1 → SENT
- :206 === 5 → PENDING_REVIEW
- :215 === 5 → PENDING_REVIEW
- :460 `![1, 2, 3, 4].includes(...) && !== 9`
- :464 `![1, 2, 5, 6, 8, 9].includes(...)`
- :507 `canDelete: ![1, 2, 5, 6, 8, 9].includes(status) && !completed`
- :508 `canEdit: ![1, 2, 3, 4].includes(status) && !completed`

## 实施要求

1. **纯机械替换，不改任何业务判断语义**：数值集合、取反、`&& !completed` 等逻辑保持一模一样，
   只把字面量换成枚举成员的 value。
2. 数组集合建议抽成模块内常量（如
   `const NON_DELETABLE = [QuotationStatusEnum.SENT.value, ...]`），避免同一集合在
   index.vue / QuotationTableColumns.vue / useQuotation.ts 里三处重复；若抽公共常量会牵连
   过多文件，则各文件内定义局部常量即可，**不要新建跨模块工具文件**。
3. index.vue 与 QuotationTableColumns.vue 存在同型重复代码（重构过程产物），
   **本次不做去重合并**，只做替换，避免把重构工作接手过来。
4. 不要动 `jjx-web/scripts/status-magic-baseline.json`（禁止新增条目）。替换完成后脚本会报
   「存量违规已消失」，那 20 条基线条目由用户决定何时移除，**你不要自行删除基线条目**。
5. 不要碰 `jjx-web/src/views/engineering/sample-workbench/workbench.vue`（用户正在改）。
6. 不要碰 dev-20260828-048 已改动的文件里与本任务无关的部分（registry.ts、TraceTimeline、
   sample-order/index.vue、后端 controller 等保持原样）。

## 验证

- `cd jjx-web && npm run check:status-enums` → 必须「新增 0 处」。
- `cd jjx-web && vue-tsc --noEmit` 或 `npm run validate` 的类型检查段必须通过。
- 不要 git commit。
