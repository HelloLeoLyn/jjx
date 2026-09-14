# JJX Production P1-C Dispatch Actions Implementation Report

> 版本：v1.0
> 日期：2026-08-19
> 范围：P1-C Node 化写模型（ASSIGN/DELEGATE/REASSIGN/RETURN + on-write adoption + projection）
> 状态：完成，等待人工验收

---

## 1. 修改/新增文件

### 新增（7 个 Java + 4 个测试）

| 文件 | 说明 |
|---|---|
| `service/DispatchActionService.java` | 四动作接口 |
| `service/impl/DispatchActionServiceImpl.java` | 核心实现（行锁/条件更新/adoption/projection） |
| `domain/dto/DispatchDelegateDTO.java` | DELEGATE 入参（targetUserId/remark，无 level） |
| `domain/dto/DispatchReassignDTO.java` | REASSIGN 入参（targetUserId/reason，无 level） |
| `domain/dto/DispatchReturnDTO.java` | RETURN 入参（reason，无 targetUser） |
| `enums/DispatchLogActionEnum.java` | 动作枚举（ASSIGN/DELEGATE/REASSIGN/RETURN/REJECT/START/COMPLETE） |
| `test/.../DispatchReturnActionTest.java` | RETURN 核心规则（3 例） |
| `test/.../DispatchLegacyAdoptionTest.java` | on-write adoption（4 例） |
| `test/.../DispatchOperatorsProjectionTest.java` | projection 算法（4 例） |

### 修改（4 个）

| 文件 | 修改点 |
|---|---|
| `DispatchServiceImpl.java` | assign() 改为 legacy adapter（映射新动作）；complete() 加 Node 同步；+3 动作转发方法；+nodeMapper/dispatchActionService 依赖 |
| `DispatchService.java` | 接口 +3 动作 |
| `DispatchController.java` | +3 端点（delegate/reassign/return） |
| `DispatchPermissionTest.java` | 自动适配（反射构造 null 数组动态长度） |

---

## 2. DispatchActionService 架构

```
DispatchActionService（写模型，独立于 850 行 DispatchServiceImpl）
├── assign(executionId, orderId, targetUserId, equipmentId, remark, ...)
├── delegate(dispatchId, targetUserId, remark, ...)
├── reassign(dispatchId, targetUserId, reason, ...)
└── returnTask(dispatchId, reason, ...)

DispatchServiceImpl = facade/legacy adapter（旧 /assign 入口映射新动作）
```

- 核心动作不再塞进 DispatchServiceImpl；避免膨胀到 1200 行（评审要求）
- 不做大规模分层重构（最小拆出）

## 3. dispatch 行锁实现

```java
private void lockDispatch(Long dispatchId) {
    // SELECT ... FOR UPDATE：锁 dispatch 容器行，同一 dispatch 责任流转串行
    dispatchMapper.selectOne(Wrappers.<ProductionDispatch>lambdaQuery()
            .eq(ProductionDispatch::getDispatchId, dispatchId)
            .last("FOR UPDATE"));
}
```

- 四动作统一先锁 dispatch 行 → 同一 dispatch 写动作天然串行
- 不同 dispatch 互不阻塞；不用分布式锁
- 事务模板（评审 §三 11 步）全部实现

## 4. ASSIGN 实现

- 权限：超管 OR `production:dispatch:assign`（不用 isDispatched/deptId——P0-04 定稿）
- 前置：execution 有效（完成/取消拒绝）；无 ACTIVE Node（有则报"该工序已派工，应使用继续派工/改派"）
- 容器：execution 无 dispatch → 新建；已有 → 复用（1:1）
- Node：parentNodeId=null、assigneeType=USER、assignee/org 快照、ACTIVE、assignedBy/assignedAt=now
- DispatchLog=ASSIGN；projection 同步

## 5. DELEGATE 实现

- 前置：有 ACTIVE；目标用户有效；不能派给自己；**目标须在当前责任人手下**（沿用现有组织规则，underlings 递归，不重构）
- 权限：ACTIVE assignee 本人 / 超管 / 有 assign 权限者（代操作，log 记录实际 operator）
- 行为：ACTIVE→DELEGATED(closedAt=now) → 新 ACTIVE(parent=旧.nodeId)
- **不用 isDispatched 作为继续派工权限**（评审禁止"历史参与者继续派工"）

## 6. REASSIGN 实现

- 前置：有 ACTIVE；目标有效；不能派给自己
- 权限：超管 / 有 assign 权限者 / ACTIVE assignee 本人（旧业务允许自行改派，保留）
- 行为：ACTIVE→REASSIGNED(closedAt=now) → 新 ACTIVE(**parent=旧.parentNodeId** 同层)；re_dispatch_count+1
- **禁止 UPDATE 当前 ACTIVE 的 assigneeId**（历史不可覆盖）

## 7. RETURN 实现（评审定稿模型）

```
N3(张三 ACTIVE) RETURN →
  N3: ACTIVE→RETURNED, closedAt=now
  创建 N4: assignee=N2.assignee(班组长), assigneeName=N2.assigneeName,
          org 快照=N2 原始快照, parentNodeId=N2.parentNodeId, ACTIVE
```

- **禁止 UPDATE N2 SET ACTIVE**（N2 保持 DELEGATED，历史不可变）
- **禁止 N4.parentNodeId = N3.nodeId**（避免错误表达"张三向班组长下派"）
- root 节点（parent=null）RETURN → 拒绝："当前任务已是最上级责任节点，无法继续退回"
- 权限：ACTIVE assignee 本人 / 超管
- DispatchLog=RETURN，content 完整表达：fromNodeId/原责任人/返回到的 parent/toNewNodeId/原因（不改 log 表结构）

## 8. RETURN 不重新激活 parent 的证据

**单元测试**（DispatchReturnActionTest）显式断言：
1. `verify(nodeMapper, times(1)).update(...)` —— update 只调用一次（目标=N3），N2 从未被 update
2. N2 的 assigneeId 保持 98、nodeStatus 保持 DELEGATED（不被修改）
3. N4.parentNodeId=1L（=N2.parent），`assertNotEquals(3L, ...)`（≠N3.nodeId）
4. N4.assigneeId=98（班组长第二次持责的新实例）

**DB 实测**（SQL 动作链，§19）：N3(100 组长B) DELEGATED 在 RETURN 后仍 DELEGATED（未激活），N5 为新实例 parent=N1。

## 9. Node 历史不可覆盖实现

- `closeActiveNode()` 条件更新只改 nodeStatus/closedAt/remark
- `createActiveNode()` 每次创建新 Node（assignee/org/assignedAt 全快照）
- 无任何业务路径 UPDATE 历史节点的 assigneeId/assigneeName/org*/assignedAt/parentNodeId

## 10. operators projection 算法

```
从当前 ACTIVE 出发，沿 parentNodeId 向上追溯，反转得到当前有效责任路径
→ 生成 [{userId,userName,level}]（level 按路径顺序 1,2,3...）
```

- **不是**完整责任历史（RETURN/REASSIGN 历史节点不进入 projection）
- 例：N1→N2→N3 RETURN，N4 ACTIVE(parent=N1) → operators 输出 [N1, N4]，**不含** N2/N3
- 无 ACTIVE（整单退回后）→ 不更新 projection（保留最后链供旧页面展示，不生成空数组）
- level 仅 Legacy Projection 兼容（不形成业务三级限制）
- 从 Node 数据生成，不从请求 DTO 拼

## 11. legacy on-write adoption

- `adoptLegacyIfNeeded(dispatchId)`：四动作统一在锁后调用
- 无 Node + operators 有数据 → 同一事务内用 BackfillParser 生成 legacy Node 链（末位 ACTIVE）→ 继续执行本次动作
- remark = **`LEGACY_ON_WRITE_ADOPTION`**（与 P1-E 的 LEGACY_BACKFILL 区分，避免回滚混淆）
- 非法 JSON → 抛"遗留派工数据无法解析，请先修复"→ **阻止写操作，不产生半链**（测试断言 insert/update never）
- 无 Node + operators 空 → 不 adopt，ASSIGN 直接建首 Node

## 12. adoption 并发/幂等

- 幂等：`hasNodes()` 前置检查，有 Node 不 adopt（测试覆盖）
- 并发：dispatch 行锁（SELECT FOR UPDATE）先行 → 同一 dispatch adopt 串行；唯一 ACTIVE 兜底
- DB 实测：并发条件更新 affectedRows=0 + 唯一约束 1062

## 13. 权限模型

| 动作 | 权限 |
|---|---|
| ASSIGN | 超管 OR production:dispatch:assign |
| DELEGATE | ACTIVE assignee 本人 OR 超管 OR 有 assign 权限（代操作） |
| REASSIGN | 超管 OR 有 assign 权限 OR ACTIVE assignee 本人（旧业务允许自改派，保留） |
| RETURN | ACTIVE assignee 本人 OR 超管 |
| 禁止 | isDispatched（参与过≠当前有权）；历史参与者不能冒充当前责任人操作 |

## 14. DispatchLog

- 复用 production_dispatch_log（无 schema 变更）
- 新增 DispatchLogActionEnum（ASSIGN/DELEGATE/REASSIGN/RETURN/REJECT/START/COMPLETE）
- content 中文风格：DELEGATE="XX 将责任从 A 下派给 B"；RETURN 含 fromNodeId/toNewNodeId/parentNodeId/原因

## 15. 旧 assign/appendLevel 兼容方式

`assign()` 变为 **legacy adapter**（P1-D 前旧前端继续可用）：
- 无 dispatchId → 新 ASSIGN（建容器 + root Node）
- 有 dispatchId + transferFrom → 旧转派语义 → **DELEGATE**
- 有 dispatchId 无 transferFrom → 旧改派语义（level=1 换第1级）→ **REASSIGN**
- level 只在 adapter 中读取理解旧意图；**新 Action Service 不接收 level**
- appendLevel/mergeChain/levelOfUser 旧方法保留（未删，标注 legacy）

## 16. DispatchStatus 兼容方式

- 未重构 DispatchStatusEnum
- 动作后统一置 ASSIGNED(2)（最接近兼容值）；**不用 Node 数量决定 TEAM_ASSIGNED/ASSIGNED**
- complete() 保留置 COMPLETED；reject() 保留旧整单退回语义（不映射 RETURN）
- 状态清理留 P1-D/Final Gate

## 17. Execution 是否被改动

**❌ 未改动。** start/complete/syncByExecution 联动保留；dispatch.complete() 仅加了 Node 同步（ACTIVE→COMPLETED，legacy-only 保持旧行为）；四动作不修改 execution 状态（责任转移不自动变 EXECUTING）。

## 18. 测试矩阵及结果

| 测试 | 覆盖 | 结果 |
|---|---|---|
| DispatchReturnActionTest（3 例） | RETURN 关旧建新、不激活 parent、root 拒绝、非本人拒绝 | ✅ 3/3 |
| DispatchLegacyAdoptionTest（4 例） | adoption 转链+标记、非法 JSON 阻止、ASSIGN 前置（完成/已有 ACTIVE 拒绝） | ✅ 4/4 |
| DispatchOperatorsProjectionTest（4 例） | RETURN/REASSIGN 后 projection=当前路径、正常链全路径、无 ACTIVE 不更新 | ✅ 4/4 |
| 既有 P0/P1-A/P1-B 测试 | 全部保持通过（未删旧测试） | ✅ |
| **全量 production 包** | 51 例 | ✅ 51/51 BUILD SUCCESS |

## 19. concurrency/transaction 实测（真实 MySQL 事务回滚）

**动作链 SQL 验证**（模拟 ActionService 全部行为）：
```
ASSIGN N1(96 ACTIVE) → DELEGATE N2(98,parent=N1)
→ REASSIGN N3(100,parent=N1 同层) → DELEGATE N4(104,parent=N3)
→ RETURN N5(100 组长B 再次持责,parent=N1) → DELEGATE N6(106,parent=N5)
```
✅ 责任历史时间线正确（11→12→13→14→15→16）
✅ 唯一 ACTIVE=N6
✅ 旧 N3/N5 未被重新激活（保持 DELEGATED）
✅ RETURN 后再次 DELEGATE 正常（N6 parent=N5）
✅ 0 残留（ROLLBACK）

**并发保护实测**：
- 第一请求条件关闭 ACTIVE → affectedRows=1
- 第二请求带旧 node_id 再关 → **affectedRows=0**（应抛"任务已被其他人处理"）
- 插入第二个 ACTIVE → **ERROR 1062**（唯一约束兜底）

## 20. compile/test 结果

| 项 | 结果 |
|---|---|
| `mvn compile` | ✅ EXIT=0 |
| `mvn test`（production 包 51 例） | ✅ BUILD SUCCESS |
| 前端 | 未改动 |
| git | 未提交 |

## 21. schema 是否变化

**❌ 否。** P1-C 0 schema migration（无新 DDL/无新 migration 文件；git 确认仅 P1-A 的 V20260819_001）。

## 22. 是否执行正式 backfill

**❌ 否。** `DispatchNodeBackfill.backfillAll()` 未调用；只有测试中的 on-write adoption 逻辑验证（事务回滚未落库）。

## 23. production_dispatch_node 正式业务记录数

**0 条**（实测 COUNT=0；所有验证事务回滚）。

## 24. 现有 3 条 legacy dispatch 是否仍能继续操作

**✅ 能。** 旧前端调 /assign 走 legacy adapter（ASSIGN/DELEGATE/REASSIGN 映射）；这些 legacy-only dispatch 首次新写动作时会自动 on-write adoption（事务内转 Node 再执行）——评审 §二十三 lazy adoption 已实现；非法 operators 会被拒绝并提示修复（不静默丢失历史）。

## 25. operators 是否只作为 projection

**✅ 是。** DispatchActionServiceImpl 中 operators 只读用于 adoption 判定（`getOperators()==null/isBlank` 判断 + BackfillParser 读取），**不用于任何新业务判断**；写入一律由 syncOperatorsProjection 从 Node 生成。

## 26. 是否还有新业务逻辑读取 operators

**新动作服务：否。** 唯一读取点 = adoption 转换器（BackfillParser，合法用途）。page()/isDispatched 的 legacy LIKE 仅存在于"无 Node 的 dispatch"保护条件下（P1-B 设计，P1-E cutover 后消除）。

## 27. 是否满足进入 P1-D

**✅ 满足。** 对照验收标准（§35 全绿）：ASSIGN/DELEGATE/REASSIGN/RETURN 正确；RETURN 不激活旧 parent（单测+DB 双证）；RETURN 后可再 DELEGATE（DB 实测 N6 parent=N5）；唯一 ACTIVE（DB 约束+条件更新+行锁三层）；并发安全（affectedRows=0/1062 实测）；历史参与者不能冒充（权限模型）；初始派工 assign 权限；legacy-only on-write adoption；非法 legacy 不产生半链；Node 为写 Source of Truth；operators 仅 Node 生成 projection；旧 API/旧前端兼容（adapter）；未执行 backfillAll；无新 schema；无 P2/P3/P4；测试 51/51；compile 通过；未提交 Git。

## 28. 风险/遗留

| 项 | 等级 | 说明 |
|---|---|---|
| DELEGATE 目标范围校验依赖组织树 leader 机制 | 低 | 沿用现有 underlings（leader=userName 递归），与旧行为一致；查询失败兜底不阻塞（前端已限制） |
| legacy adapter 的 level 语义 | 低 | 仅用于理解旧前端意图；新动作无 level；P1-D 切换后移除 adapter |
| 旧 reject 仍整单退回（非 RETURN） | 低 | 评审明确"不要把 REJECT 直接等同 RETURN"；保持兼容，P1-D 前端换 RETURN 按钮 |
| complete 时 Node 同步 | 低 | 最小同步（ACTIVE→COMPLETED）；legacy-only 保持旧行为，P1-E 后统一 |
| DispatchStatus TEAM_ASSIGNED/ASSIGNED 旧语义 | 低 | 未迁移（评审要求）；P1-D/Final Gate 后评估清理 |

---

*报告完。P1-C 完成，停止等待人工验收。*
