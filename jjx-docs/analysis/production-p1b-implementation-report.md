# JJX Production P1-B Node Read Model & Compatibility Implementation Report

> 版本：v1.0
> 日期：2026-08-19
> 范围：P1-B Node-first 读取模型 + legacy 兼容（不接管写动作、不执行 backfill、不改前端）
> 状态：完成，等待人工验收

---

## 1. 实际修改/新增文件

### 新增（5 个）

| 文件 | 说明 |
|---|---|
| `jjx-server/.../service/DispatchNodeReadService.java` | Node 读取服务接口 |
| `jjx-server/.../service/impl/DispatchNodeReadServiceImpl.java` | Node-first + legacy fallback 实现 |
| `jjx-server/.../domain/vo/DispatchNodeVO.java` | 责任链节点 VO（不暴露 active_guard） |
| `jjx-server/.../domain/vo/DispatchNodeComparisonVO.java` | Node/legacy 一致性诊断结果 VO |
| `jjx-server/src/test/java/com/jjx/production/DispatchNodeReadServiceTest.java` | Node 存在路径测试（6 例） |
| `jjx-server/src/test/java/com/jjx/production/DispatchNodeReadFallbackTest.java` | 数据源切换测试（8 例） |

### 修改（5 个）

| 文件 | 修改点 |
|---|---|
| `DispatchServiceImpl.java` | isDispatched 委托 NodeReadService；page() 数据权限改 Node-first；scope=mine；fillCurrentAssignee；nodes/currentNode/compareNodeAndLegacy 三个只读方法 |
| `DispatchService.java` | 接口 +3 只读方法 |
| `DispatchController.java` | +3 只读端点（nodes/current-node/compare-node-legacy） |
| `DispatchVO.java` | +currentNodeId/currentAssigneeId/currentAssigneeName/currentOrgId/currentOrgName/assigneeSource；operators legacy 标记注释 |
| `DispatchQueryDTO.java` | +scope（ALL/MINE） |
| `ProductionDispatch.java` | operators 字段 legacy 标记注释 |
| `DispatchPermissionTest.java` | 适配新增 nodeReadService 依赖（P0 遗留测试修复） |

**未触碰**：execution 模块、DispatchStatusEnum、Quality、前端、生产表结构。

---

## 2. Node Read Model 架构

```
DispatchNodeReadService（只读）
├── hasNodes(dispatchId)          数据源判定：Node 存在？
├── getResponsibilityChain()      责任链历史（Node-first；legacy fallback 兼容 DTO）
├── getCurrentActiveNode()        当前 ACTIVE 责任人（Node-first；legacy 末位 operator）
├── hasUserParticipated(userId)   全局"参与过"判断（Node exists；无 Node 的 dispatch 才 legacy）
├── isCurrentAssignee(d,u)        当前 ACTIVE 责任人判定
└── compareNodeAndLegacy()        一致性诊断（MATCH/MISMATCH/NODE_ONLY/LEGACY_ONLY/EMPTY）
```

- 所有 Node 查询集中在 ReadService，DispatchServiceImpl 不再自行拼 QueryWrapper 读链
- 写入（ASSIGN/DELEGATE/REASSIGN/RETURN）未实现（P1-C）

## 3. current ACTIVE 实现

- Node 存在 → `WHERE dispatch_id=? AND node_status='ACTIVE' LIMIT 1`（唯一 ACTIVE 由 DB 约束保证）
- Node 不存在 → legacy fallback：operators JSON 末位 operator 作为 current assignee
- 返回 VO 含 currentNodeId/assigneeId/assigneeName/orgId/orgName + `source=NODE|LEGACY`

## 4. responsibility history 实现

- **Responsibility History（A 视图）**：按 `assignedAt → createTime → nodeId` 稳定排序（不是单纯 parent 树），回答"责任按时间先后经过了谁"——P1-D Timeline 展示用
- **Current Responsibility Path（B 视图）**：当前 ACTIVE 节点 + parentNodeId 来源链（RETURN/REASSIGN 复杂动作来源以 DispatchLog 补充，本阶段不改 Node 表）
- legacy fallback：按数组顺序构造虚拟链（末位 ACTIVE、前面 DELEGATED，nodeId=null，仅展示）

## 5. Node-first / legacy fallback 实现

```java
// 核心规则（代码注释已标注 Legacy fallback until P1-E cutover.）
if (hasNodes(dispatchId)) {
    // Node-first：Node 为唯一读取来源，禁止再解析 operators 修正结果
    return nodeMapper.selectList(...);  // 或 selectOne(ACTIVE)
}
// Legacy fallback：仅当完全无 Node 时允许（迁移过渡）
return legacyChain(dispatchId);
```

- 所有业务判断（责任链/当前责任人/参与判定）都走这个分支
- 有 Node 的 dispatch 永不解析 operators（避免双源）

## 6. 四种数据来源 Case 实测结果（真实 MySQL 事务回滚 + Mockito 单测）

| Case | 场景 | 结果 |
|---|---|---|
| Case 1 | 无 Node + operators 有数据 → LEGACY | ✅ dispatch 2（无 Node，96 在 operators）：用户 96 legacy 可见=1；98 不可见=0 |
| Case 2 | 有 Node + operators 有数据 → NODE wins | ✅ 单测：Node 说 104、operators 说 98 → currentAssignee=104（NODE） |
| Case 2b | 有 Node + operators 有数据 → 不在 Node 的用户不可见 | ✅ DB 实测：104 不在 dispatch1 Node → 不可见=0 |
| Case 3 | 有 Node + operators 为空 → NODE 正常 | ✅ 单测：currentAssignee=104（NODE） |
| Case 4 | 无 Node + operators 为空 → 无当前责任人 | ✅ 单测：getCurrentActiveNode()=null |

## 7. isDispatched 改造

- **旧**：`SELECT COUNT(*) FROM production_dispatch WHERE operators LIKE '%"userId":N,%'`（两段 LIKE）
- **新**：`DispatchServiceImpl.isDispatched(userId)` → `nodeReadService.hasUserParticipated(userId)`
  - Node-first：`EXISTS production_dispatch_node WHERE assignee_id=userId`（全局）
  - legacy fallback：**仅对"完全没有 Node 的 dispatch"** 检查 operators（SQL 带 `NOT EXISTS node` 条件）
- 语义明确为："该用户是否曾经作为责任主体参与过任意 dispatch"
- 代码不再有 isDispatched 直接 LIKE 逻辑（已委托）

## 8. page 数据权限改造

**旧**：`d.assigned_by = me OR d.operators LIKE me OR d.operators LIKE me`

**新**（Node-first，避免双源）：

```sql
AND (d.assigned_by = ?
  OR EXISTS (SELECT 1 FROM production_dispatch_node n
             WHERE n.dispatch_id = d.dispatch_id AND n.assignee_id = ?)
  OR (NOT EXISTS (SELECT 1 FROM production_dispatch_node n2
                  WHERE n2.dispatch_id = d.dispatch_id)
      AND (d.operators LIKE ? OR d.operators LIKE ?)))
```

- dispatch 有 Node → Node 判定；无 Node → legacy 判定（NOT EXISTS 保护，不双源）
- DB 实测：用户 96 看 dispatch1（Node assignee）可见=1；104 不可见=0

## 9. "参与过"与"我的当前任务"如何区分

| 概念 | 判定 | 用途 |
|---|---|---|
| 我参与过（历史含当前） | `assigned_by=me OR EXISTS node(assignee_id=me) OR (无Node AND operators LIKE me)` | page 默认数据权限（scope=ALL） |
| 我的当前任务 | `EXISTS node(dispatch_id AND node_status='ACTIVE' AND assignee_id=me)`（无 Node → legacy 末位=me） | `scope=mine` 参数（DispatchQueryDTO） |

- 两个语义独立实现，不混用；"历史参与过"不冒充"当前待办"
- 分页 `page?scope=mine` 即可查我的当前任务（不另开重复 API）

## 10. currentAssignee projection

DispatchVO 新增（分页只带投影，控制 payload；完整责任链走 /nodes 接口）：

```json
{
  "currentNodeId": 5,
  "currentAssigneeId": 104,
  "currentAssigneeName": "印刷一组工人",
  "currentOrgId": 7,
  "currentOrgName": "印刷一组",
  "assigneeSource": "NODE"   // NODE | LEGACY | NONE（内部调试用）
}
```

- 分页循环调用 fillCurrentAssignee（Node 查询），getById 也填充
- operators 字段原样保留（兼容）

## 11. 新增只读 API

| METHOD | PATH | 权限 | 说明 |
|---|---|---|---|
| GET | `/production/dispatch/{id}/nodes` | list(261) | 责任链历史（Node-first，legacy fallback） |
| GET | `/production/dispatch/{id}/current-node` | list(261) | 当前 ACTIVE 责任人 |
| GET | `/production/dispatch/{id}/compare-node-legacy` | list(261) | 一致性诊断（hidden，P1-E 工具） |
| GET | `/production/dispatch/page?scope=mine` | list(261) | 我的当前任务（参数扩展，非新端点） |

全部只读；未添加 assign/delegate/reassign/return（P1-C）。

## 12. operators legacy 标记

- `ProductionDispatch.java` operators 字段 + `DispatchVO.java` operators 字段均加注释：

```
Legacy responsibility-chain representation.
P1 Node (production_dispatch_node) is the new source of truth.
Read fallback only until migration cutover. Do not use in new business rules.
```

- 未用 @Deprecated（避免影响序列化/大量 warning）；未删除字段

## 13. Node/Legacy comparison 工具

`compareNodeAndLegacy(dispatchId)` → DispatchNodeComparisonVO：

| result | 含义 |
|---|---|
| MATCH | Node 链与 legacy operators 一致 |
| MISMATCH | 不一致（Node 为准） |
| NODE_ONLY | 仅 Node（operators 为空） |
| LEGACY_ONLY | 仅 legacy（未 backfill） |
| EMPTY | 无 Node 且 operators 为空 |

- 输出 nodeAssigneeIds/legacyAssigneeIds 供 diff；P1-E cutover 前检查用
- 非业务 API（@Operation hidden）

## 14. 测试及结果

| 测试 | 覆盖 | 结果 |
|---|---|---|
| DispatchNodeReadServiceTest（6 例） | Node 存在时 current ACTIVE、责任历史排序、isCurrentAssignee、hasUserParticipated | ✅ 6/6 |
| DispatchNodeReadFallbackTest（8 例） | 四 Case、isCurrentAssignee legacy 末位、compare（LEGACY_ONLY/MISMATCH/EMPTY） | ✅ 8/8 |
| DispatchPermissionTest（修复 3 例） | P0 遗留适配 nodeReadService | ✅ 3/3 |
| **全量 production 包** | 40 例 | ✅ 40/40 BUILD SUCCESS |

**DB 实测**（事务回滚）：Case 1/2b legacy 可见性、Node 可见性、scope=mine、唯一 ACTIVE、compare 语义——全部 PASS，0 残留。

**说明**：JdbcTemplate 无法被 Mockito mock（JDK25），Node 存在路径走 mock；legacy SQL 路径走真实 MySQL 事务回滚验证（报告 §6/§8）。

## 15. compile/build 结果

| 项 | 结果 |
|---|---|
| `mvn compile` | ✅ EXIT=0 |
| `mvn test`（production 包 40 例） | ✅ BUILD SUCCESS |
| 前端 | 未改动（无前端 build） |
| git | 未提交 |

## 16. 是否发生数据库 schema 变更

**❌ 否。** P1-B 0 schema change（无新 migration、无 DDL；P1-A 的表原样使用）。git 确认只有 P1-A 的 migration 文件。

## 17. 是否执行正式 backfill

**❌ 否。** `DispatchNodeBackfill.backfillAll()` 未调用；正式 3 条 legacy dispatch 无任何 Node 生成。

## 18. production_dispatch_node 正式业务记录数

**0 条**（实测 `SELECT COUNT(*)` = 0；验证数据全部事务回滚）。

## 19. 现有 3 条 legacy dispatch 是否仍正常

**✅ 正常。** dispatch 1/2/3 数据原样（status 1/2/1 未变）；page 查询 legacy fallback 路径正常（Case 1 实测）；dispatch_log 6 条原样；旧页面/旧 API 不受影响（operators 字段保留返回）。

## 20. 是否仍有 operators LIKE 核心判断残留；逐项解释

| 位置 | 状态 | 解释 |
|---|---|---|
| `DispatchServiceImpl.isDispatched()` | ✅ 已消除 | 委托 nodeReadService.hasUserParticipated（Node-first） |
| `DispatchServiceImpl.page()` 数据权限 | ⚠️ 有条件残留 | 仅 `NOT EXISTS node` 前提下的 legacy fallback（P1-E cutover 前必须）；有 Node 的 dispatch 永不走 LIKE |
| `DispatchNodeReadServiceImpl` legacy fallback | ⚠️ 有条件残留 | 同上（仅无 Node 的 dispatch）；代码注释 "Legacy fallback until P1-E cutover" |
| `levelOfUser/mergeChain/describe` 等 | 保留（标注 legacy） | P1-C 前旧写逻辑仍依赖，不删除（P1-B 约束） |

**结论**：无"无条件 LIKE 核心判断"残留；所有 LIKE 均在"该 dispatch 无 Node"保护条件下，符合 P1-B 设计。

## 21. 是否修改任何 Dispatch 写动作

**❌ 否。** assign/appendLevel/mergeChain/reject/start/complete/syncByExecution/updateOrderTeam 全部未动；未实现 ASSIGN/DELEGATE/REASSIGN/RETURN；无 Node 写入/状态切换/closedAt 写入。

## 22. 是否满足进入 P1-C 条件

**✅ 满足。** 对照验收标准：

| 验收项 | 状态 |
|---|---|
| Node 存在时 Node 为唯一读取 Source of Truth | ✅ |
| Node 不存在时 legacy fallback 正常 | ✅ Case 1 实测 |
| Node/legacy 冲突时 Node wins | ✅ Case 2 实测 |
| currentAssignee 正确返回 | ✅（Node + legacy + NONE） |
| 责任历史可查询 | ✅（Responsibility History 稳定排序） |
| isDispatched 基于 Node | ✅ 已委托 |
| legacy-only 数据仍兼容 | ✅ 3 条 dispatch 正常 |
| page 数据权限读取 Node | ✅ SQL 改造 + DB 实测 |
| "历史参与"与"当前 ACTIVE"语义分开 | ✅ scope=ALL vs scope=mine |
| Node/legacy diff 可验证 | ✅ compareNodeAndLegacy |
| 正式 backfill 未执行 | ✅ |
| Node 正式业务记录仍为 0 | ✅ |
| 没有 Node 业务写入 | ✅ |
| 没有 Dispatch 四动作实现 | ✅ |
| 没有数据库 schema 修改 | ✅ |
| 现有派工业务继续可运行 | ✅ 写动作零修改 |
| 测试通过 | ✅ 40/40 |
| compile 通过 | ✅ |
| 没有 Git commit | ✅ |

## 23. 发现的阻塞/风险

| 项 | 等级 | 说明 |
|---|---|---|
| 无阻塞 | — | 未发现阻塞 P1-C 的问题 |
| Mockito/JDK25 限制 | 低 | legacy SQL 路径无法单测，依赖 DB 事务回滚验证（已覆盖）；P1-C 动作测试将同样处理 |
| scope=mine 与数据权限叠加 | 低 | scope=mine 语义本身是"我的当前任务"子集，与默认数据权限（我参与过）交集正确；DB 实测通过 |
| page() SQL 变长 | 低 | 性能可接受（演示数据量级；idx_assignee_status 已覆盖 Node 查询）；未加新索引（0 schema 变更原则） |

---

*报告完。P1-B 完成，停止等待人工验收。*
