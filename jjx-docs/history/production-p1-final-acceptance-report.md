# JJX Production P1 Dispatch V1 Final Migration & Acceptance Report

> 版本：v1.0
> 日期：2026-08-19
> 范围：P1-E Migration Cutover & Regression（P1 Dispatch V1 最后一个 WP）
> 状态：完成，等待人工最终验收

---

## 1. P1-A ~ P1-D 状态摘要

| WP | 内容 | 状态 |
|---|---|---|
| P1-A | production_dispatch_node 建表 + 唯一 ACTIVE（生成列）+ Entity/Enum/Mapper/Backfill 基础设施 | ✅ 已验收 |
| P1-B | Node-first 读取模型 + legacy fallback + currentAssignee + isDispatched/page 权限 Node 化 | ✅ 已验收 |
| P1-C | ASSIGN/DELEGATE/REASSIGN/RETURN + on-write adoption + operators projection | ✅ 已验收 |
| P1-D | 正式 API（assign-v1/delegate/reassign/return）+ allowedActions + 新 V1 UI + level 清理 | ✅ 已验收 |

## 2. P1-E 实际修改文件

| 文件 | 操作 | 说明 |
|---|---|---|
| `migration/DispatchNodeBackfill.java` | 修改 | +dryRunAll()（migration-only 预检，不碰业务 Service） |
| `migration/DispatchNodeBackfillJdbcMain.java` | 新增 | 独立 JDBC backfill 执行器（8080 未运行 + @SpringBootTest 上下文不可用时的最小执行路径，规则与 backfillAll 完全一致） |
| `service/impl/DispatchNodeReadServiceImpl.java` | 修改 | **cutover：移除 legacy fallback**（getResponsibilityChain/getCurrentActiveNode/isCurrentAssignee/hasUserParticipated 全部 Node-only；无 Node = anomaly 记录 warning；删 legacyChain/legacyUserInAnyDispatch） |
| `service/impl/DispatchServiceImpl.java` | 修改 | page() 数据权限/scope=mine **移除 operators LIKE**；batchAssign 改直接委托 ActionService（禁止制造 legacy-only dispatch） |
| `service/impl/DispatchActionServiceImpl.java` | 修改 | on-write adoption 注释：Compatibility safety net after cutover. Not normal business flow. |
| `test/.../DispatchNodeReadCutoverTest.java` | 新增 | cutover 语义测试（6 例） |
| `test/.../DispatchNodeReadFallbackTest.java` | 删除 | 旧 fallback 测试（行为已被 cutover 移除） |
| `test/.../DispatchBackfillRunnerTest.java` | 删除 | @SpringBootTest 方案弃用（knife4j 上下文不可用，改用 JDBC main） |

## 3. 执行前数据库状态

- production_dispatch = **3 条**（1/2/3，status 1/2/1）
- production_dispatch_node = **0 条**（确认）
- production_dispatch_log = 6 条

## 4. 数据备份

- 文件：`jjx-server/sql/backups/p1-backfill-20260819-1700.sql`（mysqldump，10KB）
- 表：production_dispatch / production_dispatch_node / production_dispatch_log
- 时间：2026-08-19 17:00
- 执行前快照（3 条 dispatch）：

| dispatch_id | execution_id | operators | status | assigned_by | assign_time |
|---|---|---|---|---|---|
| 1 | 1 | [{96,冲型车间主任,1}] | 1 | 94 | 12:25:33 |
| 2 | 2 | [{96,冲型车间主任,1}] | 2 | 94 | 11:57:55 |
| 3 | 3 | [{98,组长,1},{104,工人,1}] | 1 | 95 | 12:26:58 |

## 5. Backfill Dry Run（正式执行前）

```
dispatchId=1 operators=[{96,冲型车间主任,1}] → 节点=1 (冲型车间主任 ACTIVE)
dispatchId=2 operators=[{96,冲型车间主任,1}] → 节点=1 (冲型车间主任 ACTIVE)
dispatchId=3 operators=[{98,组长,1},{104,工人,1}] → 节点=2 (印刷一组组长 → 印刷一组工人 ACTIVE) [LEGACY_AMBIGUOUS_ORDER]
汇总: scanned=3, migrated=3, skipped=0, errors=0
```

- 预计 migration=3、node=4、skip=0、error=0 ✅
- dispatch 3 同 level 多人明确标记 **LEGACY_AMBIGUOUS_ORDER**（按 legacy JSON 稳定数组顺序生成兼容责任链）

## 6. Backfill 实际执行结果

```
dispatchId=1 → 创建节点=1
dispatchId=2 → 创建节点=1
dispatchId=3 → 创建节点=2
汇总: scanned=3, migrated=3, skipped=0, errors=0
RESULT=OK
```

**errors=0**，满足"任何 dispatch 失败停止 cutover"前置。

## 7. 3 条 legacy dispatch 逐条迁移结果

### dispatch 1（execution 1，status 1）
```
legacy: [{96,冲型车间主任,1}]
Node:   node 20 | parent=NULL | 96 冲型车间主任 | org=9/冲型车间 | ACTIVE | assigned_by=94 | assigned_at=12:25:33
remark: LEGACY_BACKFILL,ORG_RECONSTRUCTED
```
compareNodeAndLegacy：Node=[96] vs legacy=[96] → **MATCH** ✅

### dispatch 2（execution 2，status 2）
```
legacy: [{96,冲型车间主任,1}]
Node:   node 21 | parent=NULL | 96 冲型车间主任 | org=9/冲型车间 | ACTIVE | assigned_by=94 | assigned_at=11:57:55
remark: LEGACY_BACKFILL,ORG_RECONSTRUCTED
```
compare：**MATCH** ✅

### dispatch 3（execution 3，status 1，同 level 多人）
```
legacy: [{98,组长,1},{104,工人,1}]
Node:   node 22 | parent=NULL | 98 印刷一组组长 | DELEGATED
        node 23 | parent=22 | 104 印刷一组工人 | ACTIVE
remark: LEGACY_BACKFILL,LEGACY_AMBIGUOUS_ORDER,ORG_RECONSTRUCTED
```
compare：Node=[98,104] vs legacy=[98,104] → **MATCH** ✅
**歧义说明**：legacy 同 level:1 两人，按 JSON 数组稳定顺序串链（组长→工人），assignee 顺序一致 → MATCH 成立（compare 定义=assignee 顺序一致）；未修改历史数据。

## 8. Node/Legacy Diff

| dispatch | Node assignees | legacy assignees | result |
|---|---|---|---|
| 1 | [96] | [96] | **MATCH** |
| 2 | [96] | [96] | **MATCH** |
| 3 | [98,104] | [98,104] | **MATCH** |

**不存在无法解释的 MISMATCH** → 允许 cutover。

## 9. Read Cutover

- 正式业务能力全部改为**只读 Node**（不再 fallback operators）：
  - currentAssignee ✅（getCurrentActiveNode Node-only）
  - responsibility history ✅（getResponsibilityChain Node-only）
  - isDispatched/hasParticipated ✅（hasUserParticipated Node-only）
  - isCurrentAssignee ✅（Node-only）
  - page 数据权限 ✅（Node EXISTS，无 LIKE）
  - scope=mine ✅（Node ACTIVE，无 LIKE）
  - allowedActions ✅（基于 Node currentAssignee）
  - 责任链 Timeline ✅（/nodes Node-only）
- 无 Node 的 dispatch → 记录 `[CUTOVER] migration/data integrity anomaly` warning + 返回空/无责任人（不再静默 fallback operators）

## 10. operators LIKE 清理

- page 数据权限：✅ 移除（Node EXISTS）
- isDispatched：✅ 移除（Node-only）
- 我的当前任务 scope=mine：✅ 移除（Node ACTIVE）
- **grep：核心业务 `operators LIKE` = 0 个 SQL 残留**（仅剩注释说明）
- legacy-only 兼容工具保留：compareNodeAndLegacy 的 legacyAssigneeIds（纯诊断，非正式业务路径）——逐项报告：`DispatchNodeReadServiceImpl.legacyAssigneeIds`（compare 诊断用，读 operators 比较，非业务判断）

## 11. legacy read fallback 清理

- ✅ 已移除（legacyChain/legacyUserInAnyDispatch 删除）
- 无 Node = migration/data integrity anomaly：warning + 空结果（不静默 fallback）

## 12. on-write adoption 最终定位

- 保留为兼容保险，注释：`Compatibility safety net after cutover. Not normal business flow.`
- 正式 backfill 后旧 3 条已全部迁移，此路径仅用于未来发现未迁移 legacy dispatch 的异常兼容
- 建议 Final Gate 后评估删除（报告遗留）

## 13. batch-assign 处理结果

- **已 Node 化**：`batchAssign` 直接委托 `dispatchActionService.assign`（跳过 legacy adapter 的 level 语义）
- **cutover 后任何批量派工都产生 Node**，不会制造 legacy-only dispatch（P1 Final Gate 必查项 ✅）
- 前端批量弹窗仍调用 batch-assign（走 Node 化路径）

## 14. legacy API 最终清单

| API | 状态 | 说明 |
|---|---|---|
| POST /assign（DispatchAssignDTO） | Legacy compatibility only | P1-D 前端不用；暂留兼容 |
| POST /batch-assign | 正式（已 Node 化） | 内部调 ActionService |
| POST /{id}/reject | Legacy（整单退回） | 与 RETURN 语义分离 |
| PUT /order/{orderId}/team | Legacy | 暂留 |
| GET /underlings /team-persons /my-persons /my-depts /can-assign /pending | 正式辅助 | 人员/组织查询，不用 operators 作责任事实 |
| GET /{id}/logs | 正式 | 审计流水 |

grep 确认新前端调用：assign-v1/delegate/reassign/return（V1 页面 0 处旧 assign）。

## 15. diagnostic API 最终处理

- `GET /{id}/compare-node-legacy`：**保留 hidden + 标注 migration diagnostic**（方案 B 最小安全），P1 Final Gate 后评估移除

## 16. level 残留 grep

```
后端正式 ActionService：0 固定 level（grep 仅 projection 注释）
正式 V1 DTO：0 level（测试断言）
正式 V1 Controller：0 level
Legacy adapter（DispatchAssignDTO/appendLevel/mergeChain/levelOfUser）：保留，标注 Legacy compatibility only
前端 V1 主页面：0 残留（level/第2级/第3级/追加/transferFrom）
```

## 17. 完整动作回归（真实 MySQL 事务回滚）

```
ASSIGN 94 → DELEGATE 96 → REASSIGN 98(同层) → DELEGATE 104 → RETURN 98(新实例) → DELEGATE 106 → COMPLETE
node 24 94 DELEGATED
node 25 96 REASSIGNED
node 26 98 DELEGATED
node 27 104 RETURNED
node 28 98 DELEGATED (新实例, parent=24 同层, 非激活旧26)
node 29 106 COMPLETED
```

✅ 每步最多一个 ACTIVE；历史节点全保留；**RETURN 不激活旧 parent（26 保持 DELEGATED）**；RETURN 创建新上级责任实例（28 parent=24）；RETURN 后可再 DELEGATE（29 parent=28）；COMPLETE 后 0 ACTIVE、最后节点 COMPLETED；0 残留。

## 18. 权限回归

- 有 assign 权限可初始派工 ✅（P0-04 + 单测）
- 无 assign 权限不可初始派工 ✅（DispatchAllowedActionsTest/DispatchPermissionTest）
- 当前 ACTIVE 可 DELEGATE/RETURN ✅
- 历史参与者非 ACTIVE 不可 DELEGATE ✅（checkNodeOperatorRight 单测）
- 管理员可代操作（DELEGATE/REASSIGN；RETURN 仅本人/超管）✅
- root 不可 RETURN ✅（DispatchReturnActionTest）
- REASSIGN 与 P1-C 一致 ✅；allowedActions 与后端一致 ✅（6 例单测）

## 19. 数据权限回归（DB 实测）

| 查询 | 用户 | 结果 |
|---|---|---|
| scope=mine | 96（ACTIVE） | dispatch 1,2 ✅ |
| scope=mine | 104（ACTIVE） | dispatch 3 ✅ |
| scope=mine | 98（DELEGATED 历史参与） | **0**（不出现在"我的当前任务"）✅ |
| 全部相关 | 98（历史参与） | dispatch 3 ✅ |

**"历史参与"与"当前 ACTIVE 待办"语义分离**；全部基于 Node，不用 operators。

## 20. Order → Dispatch 回归

前端代码验证：order 页"派工"按钮（orderStatus 2/4/5/6 + assign 权限）→ 跳 `/production/dispatch?orderNo=` → V1 页面 orderNo 过滤 → 已派工行显示 currentAssignee、未派工显示初始派工、责任链 drawer 正常。未修改 ProductionOrder 模型。

## 21. Execution 回归

- dispatch.start/complete 联动保留（syncByExecution 未动）
- dispatch.complete：ACTIVE Node → COMPLETED（P1-C 实现，完整动作回归验证）
- Execution 原有状态联动未破坏；未新增 Execution 权限规则

## 22. RETURN/REJECT 回归

- RETURN：责任退回上一级（创建新上级责任实例）——API `/{id}/return`，UI「退回上级」
- REJECT：旧整单拒绝——API `/{id}/reject`，UI「拒绝派工（整单退回）」
- UI 文案/API/Node 行为三者均区分 ✅（P1-D 完成）

## 23. 数据完整性 SQL 结果

| 检查 | 期望 | 结果 |
|---|---|---|
| 1. 一个 dispatch 多 ACTIVE | 0 | ✅ 0 |
| 2. Node 找不到 dispatch | 0 | ✅ 0 |
| 3. parent_node_id 指向不存在 Node | 0 | ✅ 0 |
| 4. parent 属于其他 dispatch | 0 | ✅ 0 |
| 5. ACTIVE closed_at 非空 | 0 | ✅ 0 |
| 6. 非 ACTIVE closed_at 空 | 分析 | ⚠️ dispatch3 node22（DELEGATED）closed_at=NULL——**迁移正常态**（legacy JSON 无历史关闭时间，不伪造）；非业务异常 |
| 7. assignee_type != USER | 0 | ✅ 0 |

## 24. projection consistency

| dispatch | Node 当前路径 | operators | 结果 |
|---|---|---|---|
| 1 | [96] | [96] | ✅ 一致 |
| 2 | [96] | [96] | ✅ 一致 |
| 3 | [98,104] | [98,104] | ✅ 一致 |

COMPLETE 后 projection 保留最后路径（规则已实现，完整动作回归验证）；无无解释 MISMATCH。

## 25. 测试结果

- production 包 **59/59 通过**（P0/P1-A/B/C/D 全部既有测试 + P1-E CutoverTest 6 例）
- 新增：DispatchNodeReadCutoverTest（6 例：无 Node 空链/null/Node 唯一源/isCurrentAssignee/hasUserParticipated）
- 删除：DispatchNodeReadFallbackTest（旧 fallback 行为，已被 cutover 移除——评审 §三十允许更新语义冲突测试）

## 26-28. compile / vue-tsc / build

| 项 | 结果 |
|---|---|
| `mvn compile` | ✅ EXIT=0 |
| `mvn test`（production 59 例） | ✅ BUILD SUCCESS |
| `vue-tsc --noEmit`（dispatch 相关） | ✅ 0 errors |
| `vite build` | ⚠️ 项目全局历史 baseline 失败（MaterialCategory.vue 空文件等 3 个非 P1 文件，git 确认非本次改动）；**Dispatch V1 自身 vue-tsc 0 错误**——历史问题不计入 Dispatch 失败 |

## 29. browser/E2E 结果

**未执行**：browser attach-only（用户 Chrome 未运行），按评审不修改环境。建议用户刷新页面人工 UI 验收（生产管理→派工管理：页面/scope/当前责任人/Timeline/四动作弹窗/无固定三级 UI）。

## 30. 数据库最终记录数

- production_dispatch_node：**4 条**（dispatch1=1, dispatch2=1, dispatch3=2）
- production_dispatch：3 条（原样，operators 未删、status 未变）
- production_dispatch_log：6 条（原样）

## 31-33. 关键状态

- **是否存在 legacy-only dispatch**：❌ **不存在**（3 条全部有 Node）
- **是否存在多个 ACTIVE**：❌ 无（完整性检查 0 行）
- **是否存在无法解释 MISMATCH**：❌ 无（3 条全 MATCH）

## 34. TECH-DEBT

1. **non-ACTIVE closed_at 迁移为空**（dispatch3 node22）：legacy 无历史时间，不伪造；如业务需要可后续统一 backfill closed_at=assigned_at（建议但不强制）
2. **on-write adoption 保留**：兼容保险，Final Gate 后评估删除
3. **旧 reject 整单退回 vs RETURN**：语义已分离，旧 reject 最终弃用时间待定
4. **TEAM_ASSIGNED/ASSIGNED 状态清理**：前端已合并显示"已派工"，DB enum 未动（P2 评估）
5. **compare-node-legacy 端点**：hidden 保留，Final Gate 后评估内部化
6. **legacy adapter（/assign/appendLevel/mergeChain/levelOfUser）**：标注 Legacy compatibility only，P1-D 前端不用，最终删除时间待定
7. **vite build 历史问题**（3 个非 P1 文件）：建议后续单独 Build Baseline Fix
8. **dispatch 表注释过时**（status 0-4 vs 枚举 0-5）：后续清理

## 35. P1 Final Gate 32 项逐条 PASS/FAIL

| # | 项 | 结果 |
|---|---|---|
| 1 | production_dispatch_node 正常建立 | ✅ PASS |
| 2 | legacy dispatch 全部完成 backfill | ✅ PASS（3/3，errors=0） |
| 3 | 每个 dispatch 最多一个 ACTIVE | ✅ PASS（DB 约束 + 完整性 SQL 0 行） |
| 4 | ASSIGN 正确 | ✅ PASS（动作回归 + 单测） |
| 5 | DELEGATE 正确 | ✅ PASS |
| 6 | REASSIGN 不覆盖历史 | ✅ PASS（旧节点 REASSIGNED 保留） |
| 7 | RETURN 创建新责任实例 | ✅ PASS（node 28 新实例） |
| 8 | RETURN 不重新激活旧 parent | ✅ PASS（node 26 保持 DELEGATED） |
| 9 | RETURN 后可再次 DELEGATE | ✅ PASS（node 29 parent=28） |
| 10 | operators 不再是核心 Source of Truth | ✅ PASS（Node-only 读） |
| 11 | isDispatched 不再依赖 operators LIKE | ✅ PASS（grep 0 SQL） |
| 12 | page 数据权限不再依赖 operators LIKE | ✅ PASS（grep 0 SQL） |
| 13 | currentAssignee 只读 Node | ✅ PASS |
| 14 | scope=mine 只读 ACTIVE Node | ✅ PASS（DB 实测） |
| 15 | 新 ActionService 无 level | ✅ PASS（grep） |
| 16 | 新 DTO 无 level | ✅ PASS（测试断言） |
| 17 | 新 UI 无固定 1/2/3 级 | ✅ PASS（grep 0） |
| 18 | 新 UI 不解析 operators 判断当前责任 | ✅ PASS（grep 0） |
| 19 | legacy-only 正常数据已不存在 | ✅ PASS（3 条全有 Node） |
| 20 | batch-assign 不会制造 legacy-only dispatch | ✅ PASS（已 Node 化） |
| 21 | Node/operators projection 一致 | ✅ PASS（3 条 MATCH） |
| 22 | Node 数据完整性通过 | ✅ PASS（SQL 7 项） |
| 23 | Execution 现有流程未破坏 | ✅ PASS（联动保留） |
| 24 | RETURN/REJECT 语义分离 | ✅ PASS |
| 25 | legacy API 已隔离 | ✅ PASS（标注 Legacy only） |
| 26 | backfill 有备份/回滚依据 | ✅ PASS（sql/backups/ + LEGACY_BACKFILL 标记回滚） |
| 27 | production tests 全绿 | ✅ PASS（59/59） |
| 28 | compile 通过 | ✅ PASS |
| 29 | vue-tsc 通过 | ✅ PASS |
| 30 | build 状态有明确结论 | ✅ PASS（Dispatch V1 自身 OK；全局历史 baseline 问题已说明） |
| 31 | 无 P2/P3/P4 越界实现 | ✅ PASS |
| 32 | 未提交 Git | ✅ PASS |

**32/32 PASS**

## 36. 是否建议 P1 正式验收

**✅ 建议正式验收。** 所有 Final Gate 核心项通过，无 BLOCKED 项。

## 37. 是否满足进入 P2 Work Report V1

**✅ 满足。** P1 完成后：
- 责任链结构化（Node）可安全接管生产派工 ✅
- currentAssignee/责任历史可靠（Node 唯一源）✅
- 执行权限联动基础（currentAssignee projection）已就绪 ✅
- P2 可在此之上实现 WorkReport（报工挂 ACTIVE 执行人）

---

*报告完。P1-E 完成，停止等待人工最终验收。*
