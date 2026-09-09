# 工序执行页工作台化+组件化（任务 1666 / dev-20260909-004，重构分两步）

## 用户定稿（2026-09-09）
- 上区=工单列表：tab「当前工单」(在制) /「历史工单」(已完工)；范围=我的(默认)/全部(管理，canViewAll)
- 点上区工单 → 下区=该工单的任务树（现在这套派工同款树：我的工序含子任务，列与操作照旧）
- 下区行样式 = 现状任务树（A 方案）；代码必须组件化、尽量复用（现 index.vue 1700+ 行，且 mine/all 两份树几乎重复）
- 原两视图（当前/历史各一整棵树）被上区 tab 取代

## Step1：纯重构拆组件（行为零变化，先落地）
只动 jjx-web/src/views/production/execution/ 下文件，**不改变任何现有行为/数据/文案/权限**：
1. 新建 components/TaskTreePanel.vue：把现有两棵树表（mine 卡片与 all 卡片）合并为一份组件
   - 内置：el-table 懒加载树（tree-props/lazy/load children）、派工同款列（工序单号/工序/执行人/任务数量/已完成(可点)/待审批/已分配/剩余/状态）、操作列（去审批/报工/详情/完成明细，条件与现状一致）、任务状态标签映射、loading、筛选由父传 query 或 props 化
   - props：数据加载策略注入（props.loadRoot / props.loadChildren 函数，或 strategy: 'mine'|'all' + query），事件向上抛（approval/report/detail/completion），不内聚对话框
   - mine 卡片与 all 卡片当前差异仅：首层数据源（getMyTasks vs getTaskTreePage+分页）、筛选栏、分页；组件 props 收敛这些差异
2. index.vue 瘦身：树表模板替换为 <TaskTreePanel>；保留页面状态（viewMode、query、分页、myTaskExecutionIds 等装配逻辑）与全部对话框（完成明细/报工/详情/去审批）在壳层；仅删重复模板与仅模板用的样式/函数
3. 纯函数（fmtQty/状态标签/taskAsExecution/canReportInAllView 等）留在壳或就近组件，避免循环依赖；不得复制两份
4. 完成后行为与现在逐像素一致：两个视图切换、筛选、懒加载、操作全部照旧
5. 验证：npx vue-tsc --noEmit 零错误；git diff --check；人工 prod_manager/punch_op1 各过一遍两视图

## Step2：上区工单主从壳（Step1 验收后再做，另行 spec 或本文件续 V2）
- WorkOrderPanel 上区（新组件）等细节 Step2 时定稿（工单状态口径用现成枚举，不得写死数字；管理"全部"范围；我的范围工单数据源若现成接口不足，允许最小后端补充并在 spec 注明）

## 禁碰
- 后端、移动端 /m、dispatch 页、其他页面；不 git commit；不动无关脏文件
