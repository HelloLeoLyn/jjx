# 工序执行页「全部工序」视图树化 + 责任汇总视图移除（任务 1666 / dev-20260909-004，试点段）

## 背景与决策（用户 2026-09-09 拍板）
- 责任汇总视图：整视图连带其列一起删
- 树层级：两层（工序 → 任务）
- 实施顺序：先做「全部工序」视图改树看效果（试点），「我的生产任务」等效果反馈后再改

## 本轮范围（只动 execution/index.vue）
1. 删除「责任汇总」：顶部 scope 按钮（约 31-37 行）、整块 v-if="viewMode === 'responsibility'" el-card（约 223-390 行）、viewMode 类型去掉 'responsibility'、switchView 分支、仅责任汇总使用的常量/变量/样式（RESPONSIBILITY_CONSERVATION 等，先 grep 确认无其他使用再删；无残留引用即可，vue-tsc 不报 unused 变量错则保留无妨）
2. 「全部工序」（v-if="viewMode === 'all'"，约 392-520 行 el-card）el-table 改**懒加载树**：
   - row-key 用 `row.executionId ?? row.taskId`
   - 第一层数据仍是 operationExecutionApi.globalList(queryParams) 分页结果（executionList），行附加 hasChildren=true
   - :tree-props="{ children: 'children', hasChildren: 'hasChildren' }" + :lazy="true" + :load="loadAllChildren"
   - loadAllChildren(row)：若 row.executionId 且无 taskId（工序层）→ 调 getExecutionRootTask(row.executionId)（api/production/task.ts:47）取该工序 First Task：有则返回 [rootTask]（root 自带 hasChildren/children 语义），无则返回 []；若 row.taskId（任务层）→ 调 getTaskChildren(row.taskId)（task.ts:52）真懒加载下一层（与派工管理同款）
   - 列展示按行类型分支（统一列集合，单元格内 v-if 区分工序行/任务行）：
     - 工单/工序列：工序行显示 工单号+工序名(+序)；任务行显示 任务号 taskNo（缩进由树自带）
     - 工序行保留原 all 视图信息列：设备/计划数量/累计合格(已审批)/累计不良(已审批)/累计产出(已审批)/待审批/状态
     - 任务行显示：执行人 assigneeName / 任务数量 taskQuantity / 已完成 completedQuantity / 待审批 pendingQuantity / 剩余 remainingQuantity / 状态(StatusLabel 逻辑复用 dispatch 风格但用本页 statusTag/statusLabel)
     - 操作列：工序行沿用现有 详情(handleView)/去审批(有 pending)/报工(canReportInAllView) 等按钮；任务行给 完成明细(getTaskCompletionDetails 弹窗可复用本页 openCompletionDetails 或链接到详情) —— 简单优先：任务行操作给「详情」（沿用 handleView？任务行无 execution 详情时改为完成明细弹窗）—— 以最小可用为准，Codex 自行选择复用现有弹窗，不要新建复杂交互
   - 保持现有筛选/分页/刷新/加载交互与 canViewAll 权限逻辑
3. 不动：「我的生产任务」视图任何内容；移动端 /m；dispatch/index.vue；后端零改动；我的任务相关 myExecutionList 只服务 mine 视图（删 responsibility 后确认无残留引用）

## 验证
- cd jjx-web && npx vue-tsc --noEmit（本项目文件不得报错）
- 人工：全部工序(管理账号 prod_manager)→树形展开工序→任务逐层懒加载；责任汇总按钮与视图消失；我的生产任务视图原样
- 后端不编译（无 java 改动）

## V2（用户 2026-09-09 定稿：全部工序直接参考派工管理，字段同款，操作不同）
在第一版基础上把「全部工序」视图再收敛成派工同款：
- 第一层数据源从 operationExecutionApi.globalList(executionList) 换成 getTaskTreePage（api/production/task.ts:37，根任务分页 parent_task_id IS NULL），行即 TaskTreeRow，不再有工序分组行；children 沿用 getTaskChildren 真懒加载（与 dispatch/index.vue 完全同构）
- 列照抄派工管理（dispatch/index.vue 71-133 行）：任务号(工序单号)/工序/执行人/任务数量/已完成(可点完成明细)/待审批/已分配/剩余/状态 —— 列宽与对齐同派工，任务状态标签映射参照 dispatch 的 statusLabel/statusTag（PENDING/ACTIVE/COMPLETED/CANCELLED 等）
- 操作列（与派工不同，用执行页自身操作）：
  - 去审批（行待审批 pendingQuantity>0 或存在待审批报工时）
  - 报工（该任务 executionId 在 myTaskExecutionIds 且工序执行中——沿用现有 canReportInAllView 思路）
  - 详情（executionId → 打开现有执行详情弹窗 handleView 同款）
  - 完成明细（已完成>0 时，沿用 v1 已接的完成明细弹窗）
  - 不做：分配/退回/收回（那是派工管理的活）
- 筛选与分页适配任务查询：全部工序视图下用 keyword（工单号/工序/任务号）+ 任务状态 + 分页（仿派工 queryParams），我的生产任务视图筛选不变；若两视图筛选控件冲突，按 viewMode 条件渲染各自控件
- 删除全部工序视图下不再使用的执行汇总列与逻辑（设备/计划/累计合格/累计不良/累计产出等，仅在该视图使用的部分），globalList 若仅全部工序用则一并移除 import/调用
- 保留 canViewAll 权限 gate；我的生产任务视图/移动端/dispatch 页/后端均不动
- 完成后自跑 npx vue-tsc --noEmit 确认零错误

## 明确不做 / 禁碰
- 不改后端、不新增接口（复用 getTaskTreePage/getTaskChildren）
- 不 git commit；不动工作区无关脏文件；不要顺手重构其他区块
