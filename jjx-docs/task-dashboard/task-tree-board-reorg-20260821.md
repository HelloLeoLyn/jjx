# JJX Task Tree 看板整理（2026-08-21）

> 依据：《JJX Production Task Tree Current State Audit》（同目录 `jjx-production-task-tree-audit-20260821.md`，本会话保存）。
> 说明：任务看板（sys_task / kanban）存于 DB，当前环境无法直接读写；本文件为整理登记结果，供应用到看板。
> 本轮只做登记/整理，未实施代码、未执行 migration、未提交 Git。

## 一、登记 6 张新卡（TT-FINAL-01 ~ 06）

### TT-FINAL-01 ｜ P0 ｜ 计划转工单前端剩余额度修复
- 目标：计划 1000、已有效转工单 200，前端“剩余可下达”必须使用后端动态 `remainingQuantity=800`。
- 禁止前端再次用 `planned - completed` 覆盖后端值。
- 前端展示、弹窗预填、后端 Gate 三者统一。
- 证据：`useProductionOrder.ts:109` 覆盖 `remainingQuantity`；`order/index.vue:616-620` convertRemaining。

### TT-FINAL-02 ｜ P0 ｜ 人员选择器真实部门树/部门名称
- 目标：删除“部门{deptId}”fallback 的正式业务展示。
- `AssignTaskDialog` / `OperatorPicker` 使用真实 `sys_dept` 部门树和 `deptName`。
- 保持现有候选人员范围规则不变（当前用户部门子树）。
- 证据：`OperatorPicker/index.vue` 平铺兜底 L62-73；`AssignTaskDialog.vue` 未传 deptTree。

### TT-FINAL-03 ｜ P1 ｜ Execution Complete 接 Task Tree 闭环 Gate
- 目标：普通完成不能再仅判断“至少一条 SUBMITTED WorkReport”。
- 必须确认该 Execution 的有效 Task Tree 已闭环，不能带未完成/未释放任务直接完成。
- 证据：`ProductionOperationExecutionServiceImpl.completeExecution` L588 hasAnySubmitted gate。

### TT-FINAL-04 ｜ P1 ｜ WorkReport 与 TaskNode 并发数量保护
- 目标：解决报工 submit 与 assign/recall/return 并发时可能同时读取旧 selfRemaining 导致数量超限的问题。
- 保持现有数量公式和单一事实源不变。
- 证据：`WorkReportActionServiceImpl.submit` 读 `taskNodeService.remaining()` 无节点行锁。

### TT-FINAL-05 ｜ P1 ｜ 派工管理 UI Final 对齐设计稿
- 范围：
  - Task Tree 本人/本人下级/其他节点三色区分
  - 节点详情
  - 分配弹窗“分配后我自己剩余”
  - 当前分配“查看”
  - 独立收回弹窗：可收回范围/数量/备注
  - 主列表剩余设计稿差距合理收口
  - 修正“我已完成”视图：无剩余不等于真正完成
- 不要重新实现已经完成的：列表三数量投影、Task Tree Core、分配按钮、收回/退回后端。

### TT-FINAL-06 ｜ P1 ｜ Task Tree 完整操作流水
- 按 executionId 聚合：分配 / 收回 / 退回 / 报工 / 撤销报工。
- 前端 Task Tree 提供“任务树 / 流水”查看。
- 不要建立第二套业务事实源（报工/撤销已有 trace 派生，任务动作需补可靠事件来源）。
- 证据：`TraceEventType.java` 无 task 事件；`TaskNodeController` 无 @Log。

## 二、标记关闭（审计确认 DONE，有代码/迁移证据）

| 卡 | 关闭依据 |
|----|----------|
| TT-UI-02 生产菜单清理 | `V20260821_004__production_menu_cleanup.sql` 已落地（收敛 45/261/48/264/52/49 六项、删孤儿按钮） |
| TT-UI-03 Task Tree 权限矩阵 | `V20260821_005__task_tree_permission_cleanup.sql` 已落地（最终矩阵 1/28/29/30/31/32） |
| TT-UI-04 质检判定权限 | `V20260821_006__quality_judge_permission.sql` + `QualityInspectionController` judge/reinspect `@SaCheckPermission("production:quality:judge")` + `quality/index.vue` |
| TT-E2E-03 派工列表分配任务按钮 | `ProductionOperationExecutionServiceImpl` myAssignableNodeId 投影修复 + `TaskAssignableProjectionTest` 6/6 通过 |
| 派工管理列表增强（列表三数量投影） | `ProductionOperationExecutionVO` myTaskQuantity/myChildOccupied/myOwnHeld + `dispatch/index.vue` 列 + 投影测试通过（本会话完成） |
| 旧 Dispatch/DispatchNode/Assignment 实现类卡（如存在） | 正式代码无引用；migration 003 删旧权限；实体已移除 dispatch 字段 |

## 三、明显重复卡合并规则（看板人工核对后执行）

- 凡标题含“派工管理 UI/页面/Drawer/弹窗 设计稿补齐/对齐”且未含“列表三投影”的卡 → 合并进 TT-FINAL-05。
- 凡标题含“人员选择器/部门名/部门树 fallback”的卡 → 合并进 TT-FINAL-02。
- 凡标题含“转工单/剩余可下达/剩余额度”的卡 → 合并进 TT-FINAL-01。
- 凡标题含“操作记录/流水/完整链路（Task Tree 相关）”的卡 → 合并进 TT-FINAL-06。
- 已包含在“派工管理列表增强”卡内的三投影工作不重复建卡（TT-FINAL-05 明确不再实现）。

## 四、无法确认（不删除，保留人工确认）

- TT-UI-01：内容未知，本次审计无直接代码/迁移证据 → 保留，人工确认后处理。
- 看板中其他未出现在本审计证据内的 Task Tree 卡 → 一律保留，不猜测关闭。
- DB 实际菜单树/角色授权/旧权限行现状 UNKNOWN → 需要时人工核对，不在本轮处理。

## 五、最终待办顺序

```
01 → 02 → 03 → 04 → 05 → 06
```

即：TT-FINAL-01 → TT-FINAL-02 → TT-FINAL-03 → TT-FINAL-04 → TT-FINAL-05 → TT-FINAL-06。
