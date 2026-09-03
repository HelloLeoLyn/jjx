# 《JJX Production Task Tree Final Implementation & E2E Report》（2026-08-21）

## 一、Final 卡状态

| 卡 | 状态 | 说明 |
|----|------|------|
| TT-FINAL-01 计划转工单前端剩余额度 | DONE | 前端 PLAN 行改读后端动态 remainingQuantity；后端 Gate 已正确 |
| TT-FINAL-02 人员选择器真实组织/部门 | DONE | 部门树/已选人员/候选全部使用真实 deptName；删除 部门{id} 兜底 |
| TT-FINAL-03 Execution Complete 闭环 Gate | DONE | completeExecution 增加 Task Tree 闭环 Gate + 最小定向测试 |
| TT-FINAL-04 WorkReport×TaskNode 并发保护 | DONE | submit 对节点行 FOR UPDATE（lockNode），与 assign/recall/return 锁顺序一致 |
| TT-FINAL-05 派工管理 UI Final 对齐 | DONE | 三色区分/节点详情/分配后剩余/独立收回弹窗/我已完成修正/术语收敛/报工管理入口 |
| TT-FINAL-06 Task Tree 完整操作流水 | DONE | 按 executionId 聚合：分配/收回/退回(sys_oper_log)+报工/撤销报工(work_report)；Drawer 任务树|流水 Tab |

## 二、每张卡修改文件

- TT-FINAL-01：`jjx-web/src/views/production/order/composables/useProductionOrder.ts`
- TT-FINAL-02：`jjx-web/src/components/OperatorPicker/index.vue`、`jjx-web/src/views/production/dispatch/components/AssignTaskDialog.vue`
- TT-FINAL-03：`jjx-server/.../TaskNodeService.java`、`TaskNodeServiceImpl.java`（新增 `isExecutionTreeClosed`）、`ProductionOperationExecutionServiceImpl.java`（completeExecution Gate）、测试 `ExecutionCompleteTreeGateTest.java`
- TT-FINAL-04：`TaskNodeService.java`、`TaskNodeServiceImpl.java`（新增 `lockNode` FOR UPDATE）、`WorkReportActionServiceImpl.java`（submit 先锁节点再读 remaining）、测试 `WorkReportSubmitTest`/`TaskNodeP2Test`
- TT-FINAL-05：`jjx-web/src/views/production/dispatch/index.vue`、`components/TaskTreeDrawer.vue`、`components/AssignTaskDialog.vue`、新增 `components/NodeDetailDialog.vue`、`components/RecallDialog.vue`、`jjx-web/src/views/production/execution/index.vue`、`jjx-server/.../TaskNodeServiceImpl.java`（myTaskNodes 闭环状态投影）
- TT-FINAL-06：`jjx-server/.../TaskNodeController.java`（@Log + events 端点）、`TaskNodeService.java`/`TaskNodeServiceImpl.java`（`executionEvents`）、`TaskNodeQuantityDTO.java`（备注）、新增 `TaskTreeEventVO.java`、`jjx-web/src/api/production/taskNode.ts`、`types/production/taskNode.ts`、`TaskTreeDrawer.vue`（流水 Tab）、测试 `TaskTreeFlowProjectionTest.java`、`TaskTreeFinalE2ETest.java`

## 三、最终 Task Tree 数量公式（未变，单一事实源）

- effective = taskQuantity − recalledQuantity
- childOccupied = Σ 直接子节点 effective
- selfReported = Σ 该节点有效 SUBMITTED WorkReport（qualified+defective，动态汇总，不落 TaskNode）
- selfRemaining / availableToAssign = max(0, effective − childOccupied − selfReported)
- 状态投影：CANCELLED = effective=0 且无有效报工；COMPLETED = selfRemaining=0 且子树全部闭环；否则 ACTIVE
- Execution 完成量/输出量 = WorkReport SUM 投影（recalculate），禁止把计划当实际

## 四、最终按钮显示规则

- 派工列表「分配任务」：无 root 或 root 无人员子节点 → production:task:dispatch；已有人员节点 → 本人持有节点 myAssignableNodeId>0 + production:task:assign
- Drawer「分配任务」：节点持有人（或 admin/task:admin）+ production:task:assign + availableToAssign>0
- Drawer「退回剩余」（更多菜单）：本人非 Root 节点 + production:task:return + selfRemaining>0
- 当前分配「收回」：父节点持有人 + production:task:recall + 子节点 selfRemaining>0（独立收回弹窗）
- 「报工」：节点持有人本人 + production:work-report:add（上限 selfRemaining）
- 系统 Root 永远不作为人员节点显示、不进入我的任务/报工

## 五、最终权限 + 节点身份规则

- assign：父节点持有人（Root 无人员放行首次分配）或 admin/task:admin；Controller 兜底 production:task:assign
- recall：仅直接子节点的父节点持有人（不可越级）或 admin/task:admin；production:task:recall
- return：仅节点本人（Root 不可退）；production:task:return
- 报工：仅节点持有人本人；production:work-report:add / cancel
- RBAC 只决定“动作能力”，真实身份边界仍由服务端本人/父节点关系校验

## 六、最终页面结构

- 派工管理主列表：工单号/工序名称/工序总量/已完成/待完成/我的任务/已分给下级/我自己剩余/任务链/状态/操作（任务链、分配任务、报工管理）
- 分配任务弹窗：当前任务摘要（任务节点/任务数量/已完成/已分给下级/我自己剩余/当前可分配）、当前分配（查看/收回）、本次分配（添加人员/逐人数量/本次合计/分配后我自己剩余）
- 收回任务弹窗：人员/原任务数量/已完成/剩余未完成/可收回范围/收回数量/备注
- Task Tree Drawer：任务树 | 流水 两个 Tab；树行三色（我的=绿/我分配的下级=蓝/其他=灰）；节点查看详情弹窗；更多→退回剩余
- 节点详情：人员/所属工单/所属工序/所属上级/任务数量/已完成/待完成/已分给下级/自己持有/节点状态/直接下级
- 工序执行：全部任务/我的当前任务/我已完成；「我已完成」= 真实闭环语义（status=COMPLETED）

## 七、真实组织/部门数据展示结果

- 候选人员 deptName 来自 sys_dept（candidates 后端 deptNameMap）；部门树节点来自 deptApi.treeselect 真实 deptName
- 无部门人员 → 「未设置部门」；未知人员 → 「未知人员」
- 已删除 Task Tree UI 内所有 `部门${deptId}` / `用户${userId}` fallback（grep 复核无残留）
- 正式 UI 是否还存在 部门 + 数字ID：**NO**

## 八、Final Gate 结果

| 项目 | 结果 |
|------|------|
| mvn compile | PASS |
| mvn test-compile | PASS |
| 定向测试（TaskNodeServiceTest/TaskNodeP2Test/WorkReportSubmitTest/WorkReportCancelTest/ExecutionCompleteTreeGateTest/PlanQuotaDynamicTest/PlanQuotaReleaseTest/TaskAssignableProjectionTest/PlanToWorkOrderExecutionStatusTest/TaskTreeFlowProjectionTest/TaskTreeFinalE2ETest） | 74/74 PASS |
| npx vue-tsc --noEmit | PASS |
| git diff --check | PASS |

## 九、TT-FINAL-E2E 结果：PASS（Service 级自动 + MANUAL_REQUIRED 人工项）

| 场景 | 结果 | 使用数据 | Bug | 是否人工复验 |
|------|------|----------|-----|--------------|
| E2E-01 Root→主任 部分分配（保留50） | PASS | execution 500 / 工序总量 200 | - | NO |
| E2E-02 主任→组长 部分分配 | PASS | execution 500 | - | NO |
| E2E-03 组长→多工人 | PASS | execution 500 | - | NO |
| E2E-04 工人A 报工10 | PASS | execution 500 | - | NO |
| E2E-05 工人B 视角不串数据 | PASS | execution 500 | - | NO |
| E2E-06 班组长收回工人B / 已报不可收回 | PASS | execution 500 | - | NO |
| E2E-07 越级收回被拒 | PASS | execution 500 | - | NO |
| E2E-08 Return 部分退回/身份边界 | PASS | execution 500 | - | NO |
| E2E-09 多用户视角一致性 | PASS | execution 500 | - | NO |
| E2E-10 Complete Gate（未闭环拒绝→闭环允许） | PASS | execution 500 | - | NO |
| E2E-11 流水聚合（分配×4+报工+收回） | PASS | execution 500 | - | NO |
| E2E-12 真实组织名称 | PASS（代码审计） | - | - | YES（UI 人工） |

汇总：PASS 11 / FAIL_NON_BLOCKING 0 / BLOCKED 0 / 登记 Bug 4（含 MANUAL_REQUIRED 类）/ 自动准备测试数据 0（内存模拟，未写库）

## 十、本轮发现并自行修复的问题

1. `TaskAssignableProjectionTest`/`PlanToWorkOrderExecutionStatusTest` 因 `ProductionOperationExecutionServiceImpl` 新增 TaskNodeService 依赖需要补第 6 个构造 mock —— 已修
2. `myTaskNodes` 状态投影把 selfRemaining=0 误判为完成 —— 改为真实子树闭环投影（TT-FINAL-05 H）
3. WorkReport submit 与 assign/recall/return 并发读旧 selfRemaining —— 加节点行锁（TT-FINAL-04）
4. 前端 `vue-tsc`/类型问题在实现过程中全部现场收敛

## 十一、MANUAL_REQUIRED（需人工登录/UI/真实 DB 验证）

1. 真实多账号登录切换（生产管理员/车间主任/班组长/工人A/工人B）走一遍 E2E-01~11 的 UI 操作
2. 部门树 UI 渲染核对（sys_dept 真实 deptName；确认无 部门+数字ID）
3. 真实 DB 并发压测：同节点并发报工/分配（验证 FOR UPDATE 串行化；单测只验证锁顺序与数量守恒）
4. 流水在真实 sys_oper_log 上的展示（确认 @Log 写入与时间排序）

最短人工验收路径：
1. 登录生产管理员 → 派工管理 → 工单「WO-E2E-001」→ 任务链 → 查看任务树/流水
2. 登录车间主任 → 派工管理 → 分配任务 → 添加人员（核对部门树真实名称）→ 分配 100
3. 登录班组长 → 分配任务 → 添加工人A/B → 报工
4. 登录工人A → 工序执行 → 我的当前任务 → 报工
5. 回班组长 → 收回 → 退回 → 流水核对
6. 所有步骤检查部门名称（禁止 部门{id}）

## 十二、剩余已知问题

- TT-E2E-BUG-01：工序执行详情 Drawer「操作记录」Tab 空占位（P2）
- TT-E2E-BUG-02：流水对历史动作（@Log 上线前）无回溯（P1）
- TT-E2E-BUG-03：主列表无「工单状态」筛选（P2，已按业务收口）
- TT-E2E-BUG-04：真实多账号/UI 需人工复验（MANUAL_REQUIRED）

## 十三、最终结论

**是否可以进入人工 Final E2E：YES**

阻塞项：无。领域模型未改变，数量公式未改变，未引入第二事实源，未恢复旧 Dispatch/Assignment。
