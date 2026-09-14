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

## Step2：上区工单主从壳（工作台化）
用户定稿：上区工单列表（tab 当前工单=在制 / 历史工单=已完工；范围 我的=默认 / 全部=管理 canViewAll），点工单 → 下区 TaskTreePanel 展示该工单的任务树（现有组件复用，mine 语义=我的工序含子任务）。Step1 组件继续复用。

### 后端最小支持（仅加可选参数，不影响现有调用）
1. `ProductionOrderQueryDTO`（/production/order/list 用）新增两个可选过滤，服务/Mapper 动态 SQL：
   - `List<Integer> orderStatuses`：order_status IN 列表（多个状态一次查，现单值 orderStatus 保留不动）
   - `Boolean myAssigned`：为 true 时限定"我有分配任务的工单"——EXISTS(SELECT 1 FROM production_task t JOIN production_operation_execution e ON e.execution_id=t.execution_id JOIN production_order o ON o.order_id=e.order_id WHERE o.order_id = 主表.order_id AND t.assignee_id = 当前登录人 AND t.status <> 'CANCELLED')，当前登录人取自安全上下文
   - 类型仅 WORK_ORDER（order_type='WORK_ORDER' 或按现有查询默认类型逻辑，保持与订单页一致）
2. 现有生产订单页/其他调用零影响（可选参数缺省不生效）

### 前端
3. 新建 `components/WorkOrderPanel.vue`（execution/components/）：
   - tab：当前工单（order_status ∈ {ProductionOrderStatusEnum.PENDING_START, IN_PROGRESS, PAUSED}.value，禁止写死数字）、历史工单（∈ {COMPLETED, CANCELLED, CLOSED}.value）
   - 范围切换：我的（默认）/ 全部（仅 canViewAll 显示，沿用现有权限判断）
   - 查询走 getProductionOrderList 带 orderStatuses + (myAssigned: scope==='我的')，分页 10/页，列：工单号/产品名称/计划数量/完工数量/计划交期(plan_end_date)/状态(ProductionOrderStatusEnum label+tag)；行点击 emit select(order)，选中高亮，数据变化后自动选中第一行
4. `index.vue` 工作台化：
   - 顶部 <WorkOrderPanel>，下方 <TaskTreePanel :key="selectedOrder+scope+tab">
   - 下区数据按选中工单收敛：我的范围 = getMyTasks() 结果按 row.orderNo===选中工单号客户端过滤（本人任务量小）；全部范围 = getTaskTreePage({...allQueryParams, keyword: 选中工单号})（后端 keyword 已支持工单号）；子任务懒加载不变
   - 移除原 viewMode 双视图切换/currentFilterForm/视图相关 query 装配，保留对话框/权限/报工装配与 myTaskExecutionIds 计算（从 getMyTasks 全量算，与工单无关）
   - 空态：未选工单时下区提示"请选择上方工单"；我的范围无工单时提示
5. 约束：状态一律用 ProductionOrderStatusEnum 具名成员，不得出现数字字面量；行为只做加法与重组，报工/审批/详情/完成明细对话框逻辑不动

### 验证
- 后端 mvn -o clean compile；前端 npx vue-tsc --noEmit 零错误、npm run check:status-enums 通过、git diff --check
- 人工（用户统一验证）：punch_op1 上区只见我的工单（在制/历史），点开下区出该单我的任务树可报工；prod_manager 我的+全部可切，历史 tab 点完工单可看任务/完成明细复盘

## 禁碰
- 后端、移动端 /m、dispatch 页、其他页面；不 git commit；不动无关脏文件
