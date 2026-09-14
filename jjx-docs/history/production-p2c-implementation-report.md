# JJX Production P2-C WorkReport Actions & Execution Projection Implementation Report

> 版本：v1.0
> 日期：2026-08-19
> 范围：P2-C WorkReport 动作（SUBMIT/CANCEL）+ Execution Projection + 旧写封锁
> 状态：完成，等待人工验收

---

## 1. 修改/新增文件

### 新增（8 个 Java + 5 个测试）
| 文件 | 说明 |
|---|---|
| `domain/dto/WorkReportSubmitDTO.java` | 报工提交入参（客户端不传 order/dispatch/node/reporter/status） |
| `domain/dto/WorkReportCancelDTO.java` | 撤销入参（仅 cancelReason） |
| `service/WorkReportActionService.java` | SUBMIT/CANCEL 接口 |
| `service/impl/WorkReportActionServiceImpl.java` | 核心动作实现 |
| `service/WorkReportProjectionService.java` | 投影服务接口 |
| `service/impl/WorkReportProjectionServiceImpl.java` | SUM 投影实现（DB SUM，EXPLAIN 验证） |
| `test/.../WorkReportSubmitTest.java` | 6 例 |
| `test/.../WorkReportCancelTest.java` | 6 例 |
| `test/.../LegacyExecutionWriteBlockTest.java` | 4 例 |
| `test/.../WorkReportProjectionTest.java` | 3 例 |

### 修改（2 个）
| 文件 | 修改点 |
|---|---|
| `WorkReportController.java` | +POST submit / POST {id}/cancel（只读保留） |
| `ProductionOperationExecutionServiceImpl.java` | updateEntityFromUpdateDTO 封锁数量/工时写入；completeExecution 移除自动补值 + 完工 gate（至少 1 条报工）+ 注入 projectionService |

### 数据库 DML（权限点）
sys_menu +280（work-report:add）/ +281（work-report:cancel），授权角色 1/28/29（与 operation-execution:edit 一致）。

## 2. work-report add/cancel 权限

| menu_id | perms | 角色 |
|---|---|---|
| 280 | production:work-report:add | 1 超管 / 28 PRODUCTION 全权限 / 29 PRODUCTION 业务操作 |
| 281 | production:work-report:cancel | 同上 |

**独立于 operation-execution:edit**（不再复用作为报工写权限）；只读仍复用 operation-execution:view。

## 3-4. SUBMIT API + ActionService

`POST /production/work-report`（@SaCheckPermission work-report:add）→ `WorkReportActionServiceImpl.submit`：
校验顺序（评审 §四 14 步）→ insert WorkReport（SUBMITTED，reportTime=now 后端设置）→ `projectionService.recalculate`（同事务）→ 返回 VO。

## 5. ACTIVE Node 权限规则

- 业务关系：`operatorId == ACTIVE Node.assigneeId` 才可报工（**P2 V1 不允许代报，超管也不默认放行**——无 actualOperator 模型）
- 权限点：work-report:add（缺权限拒绝，即使有 ACTIVE 身份）

## 6-9. 校验规则

- **数量**：>=0；合格+不良>0（0+0 拒绝）；**超计划允许**（不校验 <= planned）；**defective>0 → defectReason 必填**
- **工时**：>=0（可空=0）；不从时间差自动计算
- **时间**：要么都不传要么同时传；end>=start
- **设备**：客户端传→验证存在保存本次实际；不传→默认 execution.equipmentId/equipmentName（不改 execution 默认设备）
- **锚点**：dispatchId=dispatch.dispatchId、dispatchNodeId=ACTIVE node.nodeId、reporterId/Name=当前登录用户；关系一致性校验（execution↔dispatch↔node）

## 10-11. CANCEL API + 并发/幂等

`POST /production/work-report/{id}/cancel`（@SaCheckPermission work-report:cancel）：
- 前置：报告存在；execution 未 COMPLETED（**已完成禁止撤销**）；权限点 + 业务关系（reporter 本人或超管）
- **条件更新**：`UPDATE ... SET report_status='CANCELLED', cancelled_* WHERE report_id=? AND report_status='SUBMITTED'`
- affectedRows=0 → 重读：已 CANCELLED → **幂等返回**（不重算 projection）；仍 SUBMITTED → 并发冲突异常
- 取消后重算 projection（同事务）

## 12. 不可变实现

- 只暴露 SUBMIT/CANCEL；无 PUT/DELETE/通用 update
- CANCEL 只改 reportStatus + cancelled_*（原数量/reporter/node/equipment/work time/remark/defectReason 全部保留）
- Mapper.updateById 仅 ActionService 内部用于 SUBMITTED→CANCELLED，不被 Controller 暴露

## 13-14. Execution Projection 实现 + SQL/EXPLAIN

```sql
SELECT COALESCE(SUM(qualified_quantity),0), COALESCE(SUM(defective_quantity),0),
       COALESCE(SUM(qualified_quantity+defective_quantity),0),
       COALESCE(SUM(labor_hours),0), COALESCE(SUM(machine_hours),0)
FROM production_work_report WHERE execution_id=? AND report_status='SUBMITTED'
```

- **事务内重新 SUM**（幂等/取消容易/重试不双加/可修复重算），不用增量 +/-
- EXPLAIN 实测：走 idx_execution（ref const，2 行，Using where）✅；idx_execution_status 可用（查询带 status 过滤时）
- recalculate 写 execution 的 output/qualified/defective/labor/machine——**内部投影更新**（不触发普通用户 updateExecution）

## 15-17. 旧 Execution 数量写入封锁

`updateEntityFromUpdateDTO` 开头：
```java
if (updateDTO.getActualLaborHours() != null || ... || getActualDefectiveQuantity() != null) {
    throw new BusinessException("生产数量/工时已切换为报工记录，请使用报工功能维护");
}
```
- **明确 BusinessException 而非静默忽略**（避免用户以为成功）
- 非数量字段（状态/操作员/设备/时间/异常）正常更新不受影响（测试验证）
- defectiveReason：不再由普通 update 数量路径顺带维护（真正不良原因=WorkReport.defectReason；execution.defective_reason 保留为历史兼容字段）

## 18. completeExecution 默认补值清理 + 完工 gate

- **移除自动补值**：不再 `setOutputQuantity(inputQuantity)` / `setQualifiedQuantity(output)` / `setDefectiveQuantity(0)`——禁止把计划当实际（P2-C 测试断言依赖投影服务 + 代码审查）
- **完工 gate**：`hasAnySubmitted(executionId)` 为 false → 拒绝完成："当前工序尚无有效报工记录，不能完成"
- 允许：qualified<planned / qualified>planned / defective>0（P2 不做 Quality gate）
- 报工≠开工（SUBMIT 不改 executionStatus；须已 start=2 或 paused=3 才能报工）

## 19. finished/completed 是否保持 P0 语义

**✅ 保持**。updateOrderCompletedQuantity 未改（基于已完成工序的 qualified 投影 → completed；最后有效工序 → finished 052 口径）；WorkReport 只更新 execution projection，**不在每次报工时更新 order**（order 更新仍在 execution COMPLETE 时触发）。

## 20. 历史 execution 兼容策略（实测确认）

现有 9 条 execution **全部数量为 0**（input/output/qualified/defective 均 0）——**不存在"未完成 + 非零 legacy 数量 + 0 WorkReport"场景**，评审 §三十的"第一次报工前阻止并提示迁移"策略无需触发。历史 execution 冻结不迁移；WorkReport 从 P2 上线后积累真实事实。

## 21-22. 新增/更新测试 + 结果

| 测试 | 覆盖 | 结果 |
|---|---|---|
| WorkReportSubmitTest（6 例） | ACTIVE 提交成功锚点/非 ACTIVE 拒绝/无权限拒绝/状态前置（WAITING+COMPLETED 拒）/数量校验（负/0+0/超计划允许）/不良缺原因 | ✅ 6/6 |
| WorkReportCancelTest（6 例） | 本人撤销/无权限拒/非本人拒/已完成拒/重复撤销幂等/并发冲突 | ✅ 6/6 |
| LegacyExecutionWriteBlockTest（4 例） | 数量字段全部拒绝+提示/非数量字段正常/complete 依赖投影 gate | ✅ 4/4 |
| WorkReportProjectionTest（3 例） | 累计规则/取消回算（纯逻辑） | ✅ 3/3 |
| **全量 production 包** | 85 例 | ✅ 85/85 BUILD SUCCESS |

**真实 DB 验证（事务回滚，0 残留）**：两次报工 SUM q=300 d=15 o=315 lh=4.0 mh=3.0（累计非覆盖）→ 取消第一条 q=200 d=5 o=205 → 第一条 CANCELLED（原事实保留 + cancel_reason）第二条 SUBMITTED → EXPLAIN 走索引 → ROLLBACK 0 残留。

## 23-28. 关键状态

- **compile**：✅ EXIT=0
- **正式 WorkReport 业务数据**：0 条（验证全部回滚）
- **schema 变化**：❌ 否（0 migration；唯一 DB DML=权限点 280/281）
- **Dispatch/Node 模型修改**：❌ 否（SUBMIT/CANCEL 不改 Node 状态；Node COMPLETED 仍跟 dispatch.complete 联动）
- **前端修改**：❌ 否（旧记录弹窗本阶段收到 BusinessException 提示"请使用报工功能"——P2-D 预期中间态）
- **P3/P4 越界**：❌ 否（无 Quality gate/缺陷字典/Trace/成本等）

## 29. 是否满足进入 P2-D

**✅ 满足。** 验收标准全绿：SUBMIT 正确（新增不覆盖）/ACTIVE assignee 才能报/独立权限生效/数量校验（0+0 拒、超计划允许、不良原因规则）/锚点正确/projection=SUM/两次累计正确/取消回算正确/事实不可编辑/CANCEL 保留历史/旧写入封锁/complete 不伪造数量/至少 1 条报工 gate/finished-completed 保持 P0/无历史假报工/Node 不破坏/0 schema/无 P3P4/85-85/compile/未提交 Git。

## 30. 风险/遗留

| 项 | 等级 | 说明 |
|---|---|---|
| 旧记录弹窗短暂不可用 | 中 | P2-C 预期（提示语明确"请使用报工功能"）；P2-D 改 UI |
| SUBMIT 重复点击无幂等 key | 低 | P2 V1 前端禁用按钮兜底；HTTP 重试风险记录技术债，不引入 Redis 幂等 |
| 权限 DML 手动执行 | 低 | 280/281 已插入 + 角色 1/28/29 授权（与 execution edit 一致）；未做 migration 文件（权限属业务 DML，按项目现状直接 SQL） |
| execution.defective_reason 保留 | 低 | 历史兼容字段；真正不良原因走 WorkReport.defect_reason |
| idx_execution vs idx_execution_status | 低 | EXPLAIN 实测 idx_execution 命中；两索引均保留（组合覆盖 status 过滤） |

---

*报告完。P2-C 完成，停止等待人工验收。*
