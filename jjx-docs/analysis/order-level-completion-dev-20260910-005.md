# 工单级统一收口（PC + 移动端）实施记录

- 任务码：`dev-20260910-005`（关联派生任务 `dev-20260910-006` 补单测）
- 日期：2026-09-10
- 范围：生产「工序执行」页 / 移动端 `/m/order`；后端工序执行服务

## 1. 背景

2026-09-10 Leo 定：生产完工口径为「**一级负责人对该工单全工序统一收口**」——
- **工人**：只做 开始 / 报工，**没有「完工」动作**
- **报工审批**：执行人的上一级审批（一层派工=一级负责人；多级派工=直接班组长）
- **收口**：一级负责人**对整张工单一次完成**全部工序，系统前置校验

改造前的差距：
- 移动端 `/m/order` 是**逐工序**「✓ 完工」，一级负责人要按 5 道工序点 5 次
- **PC 端完全没有完工入口**：菜单「工序执行」（`views/production/execution/index.vue`）只有报工/审批/质检/打印；
  唯一带「完成」按钮的 `views/production/production-operation/index.vue` **无任何菜单挂载**（孤儿页，DB `sys_menu` 与代码双向确认）

## 2. 方案与前置口径

**收口粒度 = 工单**：新增工单级接口，内部逐道置已完成并收口各工序根任务。

**前置校验（逐工序聚合，任一未满足整体拒绝）**：
1. 状态 = 执行中（未终态工序若仍为待执行/准备中 → 阻断）
2. 无待审批报工
3. 合格数量达标
4. 无未分配剩余
5. 已派任务无未完成（子树全完成）

口径复用既有 `completionBlockers`（生产任务完成前置唯一真源），不新造判断。

**权限**：该工单全部待完工工序的根任务负责人（=一级负责人）本人，或超级管理员。
（本项目实测 5 道工序根任务负责人均为「生产中心主任 prod_manager」，天然满足）

**FQC**：全部收口后由最后一道工序自动创建完工检验（沿用 `createFqcForExecution`）。
成品数量仍按口径Y由 FQC PASS 写回，工序完工不写成品数。

## 3. 改动清单

**后端**
| 文件 | 改动 |
|---|---|
| `ProductionOperationExecutionController` | 新增 `PUT /production/operation-execution/order/{orderId}/complete`；新增 `GET /production/operation-execution/order-completion-status?orderIds=` |
| `ProductionOperationExecutionService(Impl)` | 新增 `completeOrderExecutions(orderId)`（权限+前置聚合+逐条收口+FQC，事务整体回滚）；新增 `getOrderCompletionStatus(orderIds)`（批量轻量投影，1 条聚合 SQL 避免 N+1） |
| `ProductionTaskService(Impl)` | 抽出 `executionCompletionBlockers(executionId)`（返回阻断清单、不抛异常）；`assertExecutionCompletable` 改为复用它 |
| `OrderCompletionStatusVO`（新增） | `{orderId, orderNo, pendingExecutionCount, authorized, canComplete}` |

**前端**
| 文件 | 改动 |
|---|---|
| `views/production/execution/components/WorkOrderPanel.vue` | 工单面板每行新增「完成」按钮；页面加载后批量取收口状态，仅授权用户可见；确认弹窗→收口→刷新 |
| `views/production/execution/index.vue` | 接收面板 `completed` 事件刷新下区任务树 |
| `views/mobile/order.vue` | **删除逐工序「✓ 完工」**；新增工单级卡片「✓ 完成整单」 |
| `views/production/production-operation/index.vue` | 删除孤儿页的逐工序「完成」按钮与 `handleComplete`（旧动作下线） |
| `api/production/operationExecution.ts` | 新增 `completeOrder` / `getOrderCompletionStatus` |
| `types/production/operationExecution.ts` | 新增 `OrderCompletionStatusVO` |

## 4. 验证

- `mvn -o compile`（JDK 21）通过
- `npm run validate`（状态魔法值门禁 + 文档门禁 + `vue-tsc --noEmit`）通过
- **未打包、未重启**（后端改动需用户重启 8080 生效；前端 vite HMR）

人工验证路径：
1. PC：生产管理 → 工序执行 → 上区选工单 → 行尾「完成」→ 一次收口全部工序 → 自动生成 FQC
2. 生产质检 → 判定 PASS（检验数/合格数）→ 成品数累计 + 自动完工入库
3. 生产订单 → 「完成工单」→ 通过四项门禁 → 已完成(8)
4. 移动端 `/m/order`：一级负责人只见「完成整单」；工人无完工动作

## 5. 待确认 / 后续

- **单测缺失**（衍生任务 `dev-20260910-006`）：工单级收口的权限、前置聚合、整体拒绝、FQC 触发无单测覆盖
- 旧接口 `PUT /{executionId}/complete` 保留（作为底层能力），界面不再暴露
- 测试工作台 P7 中「逐工序完工」口径的用例待同步为「工单级收口」（Leo 本次指示跳过）
