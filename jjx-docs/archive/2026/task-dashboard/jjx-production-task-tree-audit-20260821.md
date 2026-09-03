# 《JJX Production Task Tree Current State Audit》

> 审计日期：2026-08-21
> 依据：当前工作区代码（含未提交改动）、`jjx-docs/sql/migrations/V20260821_001~006`、UI 设计稿（派工管理图）。DB 实际状态无法连接验证，相关项标注 UNKNOWN。
> 约束：只读审计，未改代码/数据库/migration/jjx_task，未跑全量测试，未提交 Git。

## 逐项检查

| # | 模块 | 设计目标 | 当前实现 | 状态 | 证据 | 缺口 | 优先级 |
|---|------|----------|----------|------|------|------|--------|
| 1 | Task Tree 核心模型 | 系统Root无人员；真实人员节点；部分分配；可继续分配；收回；退回；数量公式；并发保护 | Root 恒为系统根（assigneeId=NULL）；assignChildren 支持多人+部分分配且合计≤available；recall/return 限 selfRemaining；公式统一 effective=taskQty−recalledQty，childOccupied=Σ直接子effective，selfRemaining=effective−childOcc−selfReported（下限0） | PARTIAL | `TaskNodeServiceImpl.java`：ensureRoot(L91-97)、assignChildren(L165-235)、recall(L238-266)、returnNode(L271-296)、剩余公式(L409-414,494-499)；FOR UPDATE 行锁（L174,247,283） | 并发保护：assign/recall/return 有父/子节点行锁，但 WorkReport submit 读 remaining 无节点锁，并发报工+分配理论可超剩余（未做压测验证） | P1 |
| 2 | 派工管理主列表 | 列：工单号/工序名称/工序总量/已完成/待完成/我的任务/已分给下级/我自己剩余/状态/操作 | 列表已对齐目标列（另保留“任务链”入口列）；数据源=execution + WorkReport projection + enrichTaskNodeChain 三投影；操作列=任务链+分配任务 | PARTIAL | `dispatch/index.vue`（列 L22-68）；`ProductionOperationExecutionVO` 新增 myTaskQuantity/myChildOccupied/myOwnHeld（L128-135）；`ProductionOperationExecutionServiceImpl.enrichTaskNodeChain`（L382-404） | 设计稿操作列缺“报工管理”“更多”；筛选区缺“工序名下拉/工单状态”；头部无“当前角色”；无角色可见范围/权限说明图例 | P2 |
| 3 | 分配任务按钮 | Root首次分配/本人继续分配/部分分配后继续分；task:dispatch/assign 权限；前后端一致 | 无root或taskNodeCount≤1 → task:dispatch（点按建系统根再分配）；已有人员子节点 → myAssignableNodeId+task:assign；taskNodeCount>1 且持有节点有剩余 → 投影到 myAssignableNodeId | DONE | `dispatch/index.vue` canDispatch(L169-172)、handleAssign(L182-207)；`TaskNodeController` @SaCheckPermission assign/recall/return；`enrichTaskNodeChain` myAssignableNodeId 投影（L357-380） | task:dispatch 仅前端按钮级，无对应后端接口校验（首次分配实际走 view+assign 接口）；若存在“root 为真实人员的历史脏数据”时 taskNodeCount≤1 分支会短路 assign（当前模型下不出现） | P2 |
| 4 | 分配任务弹窗 | 当前任务摘要/当前分配/多人+数量/部分分配/分配后自己剩余/直接子节点收回/部分收回/收回后继续分配 | 多人+数量、部分分配（合计≤available）、直接子节点收回、部分收回（prompt 1..max）、收回后 load() 刷新可继续分配，均可用 | PARTIAL | `AssignTaskDialog.vue`：drafts 表(L43-75)、canSubmit(L170-180)、handleRecall ElMessageBox.prompt(L206-226)、handleAssign(L228-250) | 无“分配后我自己剩余”展示；无独立收回弹窗（可收回范围+备注+确认取回）；当前分配无“查看”；摘要文案与设计稿（我的任务/已分给下级/我自己剩余/已完成/当前可分配）不一致 | P2 |
| 5 | Task Tree Drawer | 系统Root隐藏/真实人员树/三色区分/数量列/节点详情/任务树+流水Tab | 系统根隐藏（displayTree 跳过 assigneeId=NULL 根）；真实人员树完整；数量列齐全（已完成/已下分/自己持有/待完成） | PARTIAL | `TaskTreeDrawer.vue`：displayTree(L59-68)、表列(L24-47)、退回剩余(L149-158) | 无绿/蓝/灰三色视觉区分；无节点详情（查看）；无流水 Tab（操作记录无数据源） | P1 |
| 6 | 人员选择器 | 候选范围=部门子树；真实部门名；多选；真实 sys_dept 树 | 后端候选=当前用户部门子树（sys_dept 真实树），deptName 取自 sys_dept；多选+已选人数正常 | PARTIAL（含BUG） | `TaskNodeServiceImpl.candidates()`（L384-415）；`OperatorPicker/index.vue` treeData 兜底（L62-73）；`AssignTaskDialog.vue` 引用（L93-99）未传 deptTree | **BUG**：AssignTaskDialog 未传 deptTree，picker 走平铺兜底，部门名显示“部门{deptId}”（部门6/部门9），忽略候选的 deptName；未使用真实 sys_dept 树渲染 | P1 |
| 7 | 工序执行 | 全部任务/我的当前任务/我已完成；本人TaskNode投影；分配入口；报工入口；开始/暂停/完成 | 三视图切换；我的任务用 /task-node/my 动态投影（taskQuantity/selfReported/childOccupied/selfRemaining/availableToAssign）；mine 视图有报工+分配入口；开始/暂停/完成齐全 | DONE | `execution/index.vue`：视图(L64-95)、myCurrentTasks/myDoneTasks(L385-392)、openNodeReport/openNodeAssign(L404-425)、开始/暂停/完成按钮(L61-66)；`TaskNodeServiceImpl.myTaskNodes()`（L317-370） | “我已完成”= selfRemaining≤0 且非CANCELLED（含“全部分下未报工”节点，语义是“无剩余”而非“已完成”） | P2 |
| 8 | WorkReport | taskNodeId绑定/本人报工/selfRemaining上限/撤销恢复/无旧模型依赖 | 必须绑定 taskNodeId 且属同 execution；operatorId=节点持有人；数量≤remaining；撤销条件UPDATE→projection重算；实体仅 taskNodeId（dispatch_node_id 历史列不再写入） | DONE | `WorkReportActionServiceImpl.submit`（L53-142）、cancel（L144-185）；`WorkReportProjectionServiceImpl.recalculate`（L40-58）；`ProductionWorkReport` 实体（L39-40） | submit 未对 TaskNode 行加锁（与 #1 并发保护同源，报工并发超报未验证） | P1 |
| 9 | Execution 状态 | 转工单PENDING/分配不自动开始/START权限/COMPLETE接Task Tree完成Gate | 转工单生成一律 PENDING；assignChildren 不改执行状态；START=production:operation-execution:edit；COMPLETE gate=至少1条SUBMITTED报工 | PARTIAL | `ProductionOrderServiceImpl.generateOperationExecutions`（L1391-1430，WP-E2E-BUG-01）；`ProductionOperationExecutionServiceImpl.startExecution(L444)/completeExecution(L562)` gate hasAnySubmitted(L588)；Controller /start @SaCheckPermission(L103-105) | COMPLETE 未要求 TaskNode 子树闭环/selfRemaining=0，可带剩余任务完成；非严格 Task Tree 完成 Gate | P1 |
| 10 | PLAN→WORK_ORDER | 计划1000已转200后剩余显示/校验=800 | 后端已修：availableQty=计划−Σ有效子工单（动态），查询 VO 同口径，取消工单释放额度；前端展示/预填仍用 planned−completed | PARTIAL（后端DONE/前端BUG） | 后端：`ProductionOrderServiceImpl.convertPlanToWorkOrders`（L1163-1180）、planRemainingQuota(L1326-1338)、fillPlanRemainingQuota(L260-266)；前端：`useProductionOrder.ts` 覆盖 remainingQuantity(L109)、`order/index.vue` convertRemaining(L616-620) | **BUG**：前端把 PLAN 行 remainingQuantity 覆盖为 planned−completed（PLAN 恒=1000），转工单弹窗“剩余可下达”显示 1000 而非 800，提交被后端 Gate 拒绝，展示与 Gate 不一致 | P0 |
| 11 | 菜单和权限 | 生产管理仅6项核心入口；无重复派工；task:* 权限树；无旧 dispatch/assignment 权限 | 迁移已定义收敛：004 收敛 45/261/48/264/52/49 六项并删孤儿按钮；003 定义 261+5 子按钮并删旧权限；005 定角色矩阵（1全/28全-无admin/29/30/31=view+assign+recall+return/32=view）；006 加 quality:judge | PARTIAL（DB 状态 UNKNOWN） | `V20260821_003/004/005/006__*.sql`；`TaskNodeController` 注解；前端权限引用 task:view/assign/recall/return/admin、quality:judge | DB 实际菜单树/角色授权/重复项无法连接确认；旧 SQL 脚本（docs）仍含 production:dispatch:* 定义（历史文档，非正式代码） | P2 |
| 12 | Trace/操作记录 | 分配/收回/退回/报工/撤销按 executionId 成完整流水 | Trace 查询时派生：ORDER/EXECUTION_STARTED/COMPLETED、WORK_REPORT_SUBMITTED/CANCELLED、QUALITY_*；TaskNodeController 无 @Log，分配/收回/退回无任何流水来源 | PARTIAL | `TraceQueryServiceImpl.buildWorkReportEvents(L171-215)`、`TraceEventType.java`（无 task 事件）；`TaskNodeController.java`（无 @Log） | 分配/收回/退回无 trace 事件、无 sys_oper_log 记录；设计稿“操作记录（完整链路）”缺任务链路部分 | P1 |
| 13 | 旧模型残留（正式代码） | 无 Dispatch/DispatchNode/Assignment 正式依赖 | Java/Vue 无功能引用（仅注释与命名：ProductionDispatch 组件名、canDispatch 函数名、javadoc 提及）；实体已移除 dispatch 字段 | DONE | `rg Dispatch/Assignment`：仅 `ProductionTaskNode.java:14`、`ProductionWorkReport.java:39` 注释；`taskNode.ts:1` 注释 | DB 中旧 dispatch_node 表/WorkReport.dispatch_node_id 列历史保留未删（V20260821_002 注释明确保留）——实际存在性 UNKNOWN | P3 |

## 汇总

### 1. 已完成项
- Task Tree 核心动作：系统根无人员、真实人员节点、部分分配、可继续分配、收回、退回、统一数量公式、父子身份边界（本人/父持有人校验）
- 派工列表三投影列（我的任务/已分给下级/我自己剩余）后端投影 + 前端列表列
- WorkReport：taskNodeId 绑定、本人报工、selfRemaining 上限、撤销条件更新与投影恢复
- Execution：转工单一律 PENDING、分配不自动开始、START 权限、完成至少需 1 条有效报工
- PLAN→WORK_ORDER 后端 Gate（动态口径、取消释放额度）
- 权限迁移 003/004/005/006 已编写；旧模型正式代码引用清除

### 2. 部分完成项
- 派工列表 vs 设计稿：操作列（报工管理/更多）、筛选区（工单状态/工序名下拉）、头部当前角色、图例
- 分配弹窗：独立收回弹窗（可收回范围+备注）、分配后自己剩余、查看节点详情
- Drawer：三色视觉区分、节点详情、流水 Tab
- 工序执行“我已完成”视图语义（无剩余≠已完成）
- COMPLETE 的 Task Tree 闭环 Gate（当前仅报工事实 gate）
- 并发保护（报工无节点行锁）
- Trace：分配/收回/退回无事件来源

### 3. 未完成项
- 节点详情抽屉、任务树/流水 Tab、操作记录完整链路（分配/收回/退回）
- 独立收回弹窗（含备注、可收回范围 UI）
- 任务树三色图例（我的节点/我分出的节点/无权节点）

### 4. 已确认 Bug
- P0：PLAN→WORK_ORDER 前端“剩余可下达”显示计划全量（1000），与后端动态 Gate（800）不一致（`useProductionOrder.ts:109` 覆盖后端动态值）
- P1：人员选择器部门名 fallback 显示“部门{deptId}”，未用真实部门名/部门树（`AssignTaskDialog.vue` 未传 `deptTree`）

### 5. 与 UI 设计稿主要差距
- 列表：缺 报工管理、更多（异常改派）按钮；缺 工单状态 筛选；缺“当前角色”头部
- 任务树：缺三色区分、节点详情（查看）、流水 Tab
- 分配弹窗：缺 分配后自己剩余、独立收回弹窗、当前分配“查看”
- 页面装饰：缺 角色可见范围、权限说明（task:assign/recall/return/admin）图例

### 6. 建议登记 jjx_task 的新任务清单
- P0：转工单前端剩余可下达口径对齐后端动态值
- P1：人员选择器接入真实 sys_dept 部门树/部门名
- P1：操作记录链路（分配/收回/退回）——方案：TaskNode 操作落流水或 sys_oper_log 补 @Log + 前端时间线
- P1：COMPLETE 接 Task Tree 闭环 Gate（全部 TaskNode 闭环/selfRemaining=0 才可完成）
- P1：WorkReport submit 并发保护（TaskNode 行锁或乐观校验）
- P2：派工列表设计稿补齐（报工管理/更多按钮、工单状态筛选、当前角色头部、图例）
- P2：分配弹窗/Drawer 设计稿补齐（独立收回弹窗、分配后剩余、三色、节点详情、流水 Tab）
- P2：工序执行“我已完成”按真实完成口径过滤
- P3：历史 dispatch_node 表/旧列清理（确认无历史追溯依赖后）

### 7. 可关闭/删除/合并的旧任务建议
- 基于代码事实：旧 Dispatch/DispatchNode/Assignment 相关实现任务（若看板仍存在）应标记为已完成/可关闭——正式代码已无引用，migration 003 已删旧权限，剩余仅是历史 DB 对象与文档
- “派工管理列表三列投影”“TT-E2E-03 分配按钮”“TT-UI-03 权限矩阵”“TT-UI-04 质检判定权限”等已完成事项对应卡可关闭
- 看板（jjx_task）内容不可读，无法核对具体卡号；以上仅为建议，需以看板实际状态为准

## UNKNOWN（无法从代码确认，需人工/DB 验证）
- DB 实际菜单树（是否仍有重复派工管理/孤儿按钮）、sys_role_menu 实际授权矩阵、旧 dispatch 权限行是否已清
- 旧 dispatch_node 表/WorkReport.dispatch_node_id 列在库中的实际存在与数据量
- 并发场景（同时报工+分配/收回）实际行为未压测
- 真实用户→角色→权限映射（如 punch_mgr 是否确持 production:task:assign）
