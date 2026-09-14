# JJX Production P1-A Database & Node Foundation Implementation Report

> 版本：v1.0
> 日期：2026-08-19
> 范围：P1-A 数据库 + Node 后端基础设施（不接入业务、不执行 backfill、不改前端）
> 状态：完成，等待人工验收

---

## 1. 实际修改/新增文件

### 新增（8 个 Java/测试 + 1 个 migration）

| 文件 | 说明 |
|---|---|
| `jjx-server/sql/migrations/V20260819_001__dispatch_node.sql` | 建表 migration（已执行） |
| `jjx-server/src/main/java/com/jjx/production/domain/entity/ProductionDispatchNode.java` | Node 实体 |
| `jjx-server/src/main/java/com/jjx/production/enums/DispatchNodeStatusEnum.java` | 节点六态枚举 |
| `jjx-server/src/main/java/com/jjx/production/enums/DispatchAssigneeTypeEnum.java` | 责任主体类型枚举（仅 USER） |
| `jjx-server/src/main/java/com/jjx/production/mapper/ProductionDispatchNodeMapper.java` | Mapper（BaseMapper） |
| `jjx-server/src/main/java/com/jjx/production/migration/DispatchNodeBackfillParser.java` | backfill 解析器（纯函数） |
| `jjx-server/src/main/java/com/jjx/production/migration/DispatchNodeBackfill.java` | backfill 执行器（幂等/异常/回滚） |
| `jjx-server/src/test/java/com/jjx/production/DispatchNodeStatusEnumTest.java` | 枚举测试 |
| `jjx-server/src/test/java/com/jjx/production/DispatchNodeBackfillParserTest.java` | 解析器测试 |
| `jjx-server/src/test/java/com/jjx/production/ProductionDispatchNodeMapperTest.java` | Mapper 能力测试 |

### 修改
**零修改**。未触碰 DispatchServiceImpl/Service/Controller/DTO/VO/枚举/前端/Execution/Quality（git 时间戳验证：旧文件最后修改 15:02 = P0 时段遗留，本次 P1-A 15:41 起仅新增）。

---

## 2. Migration 文件

`jjx-server/sql/migrations/V20260819_001__dispatch_node.sql`（遵循现有 V 命名规范，手动执行，无 Flyway/Liquibase——已核实项目现状）。

**已执行**（P1-A 允许的唯一 DDL），执行成功（EXIT=0）。

---

## 3. production_dispatch_node 最终 DDL（实测 SHOW CREATE TABLE）

```sql
CREATE TABLE `production_dispatch_node` (
  `node_id`          bigint NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `dispatch_id`      bigint NOT NULL COMMENT '派工单ID(production_dispatch.dispatch_id)',
  `parent_node_id`   bigint DEFAULT NULL COMMENT '上级节点ID(第1级=NULL，表示源头主管直派；责任来源节点)',
  `assignee_type`    varchar(20) NOT NULL DEFAULT 'USER' COMMENT '责任主体类型：USER(P1第一版仅支持)',
  `assignee_id`      bigint NOT NULL COMMENT '责任主体ID(用户ID)',
  `assignee_name`    varchar(64) NOT NULL COMMENT '责任主体姓名快照(改昵称不影响历史)',
  `org_id`           bigint DEFAULT NULL COMMENT '责任主体当时所属组织ID快照',
  `org_name`         varchar(100) DEFAULT NULL COMMENT '责任主体当时所属组织名称快照',
  `org_path`         varchar(500) DEFAULT NULL COMMENT '责任主体当时所属组织祖先路径快照(如"1/5/6/7")',
  `node_status`      varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '节点状态：ACTIVE/DELEGATED/REASSIGNED/RETURNED/COMPLETED/CANCELLED',
  `assigned_by`      bigint DEFAULT NULL COMMENT '本次责任由谁指派(用户ID)',
  `assigned_by_name` varchar(64) DEFAULT NULL COMMENT '指派人姓名快照',
  `assigned_at`      datetime DEFAULT NULL COMMENT '本次责任正式生效时间',
  `closed_at`        datetime DEFAULT NULL COMMENT '本次责任周期结束时间(流转走/完成/取消)',
  `remark`           varchar(500) DEFAULT NULL COMMENT '备注/退回原因/迁移说明(LEGACY_BACKFILL)',
  `create_by`        varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time`      datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time`      datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `active_guard`     tinyint GENERATED ALWAYS AS ((case when (`node_status` = _utf8mb4'ACTIVE') then 1 else NULL end)) STORED COMMENT '唯一ACTIVE守卫列(ACTIVE→1，其他→NULL；DB生成，Java不写)',
  PRIMARY KEY (`node_id`),
  UNIQUE KEY `uk_dispatch_active` (`dispatch_id`,`active_guard`),
  KEY `idx_dispatch` (`dispatch_id`),
  KEY `idx_assignee_status` (`assignee_id`,`node_status`),
  KEY `idx_parent` (`parent_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='派工责任链节点(责任持有实例)'
```

**外键策略**：无物理 FOREIGN KEY（遵循项目现状——production_dispatch/log 均无 FK，dispatch_id/parent_node_id 逻辑关联）。

---

## 4. 为什么选择 generated active_guard

在真实 MySQL 8.4.10 上以 `CREATE TEMPORARY TABLE` 实测过两种方案：

| 方案 | 实测结果 |
|---|---|
| A：生成列 `active_guard`（ACTIVE→1 其他→NULL）+ UNIQUE(dispatch_id, active_guard) | ✅ 同 dispatch 第二个 ACTIVE 报 `1062 Duplicate entry '1-1'`；UPDATE 释放后（guard→NULL）可再插 ACTIVE |
| B：函数索引 `UNIQUE(dispatch_id, (CASE WHEN node_status='ACTIVE' THEN 1 ELSE NULL END))` | ✅ 行为等价，同样正确拒绝 |

**选定方案 A（生成列）**：
1. 可读性更好——active_guard 是真实列，`WHERE active_guard IS NOT NULL` 直接可用、DBA 可直接查看、可被优化器利用；
2. 语义显式——表结构一眼看懂"同一 dispatch 至多一个 ACTIVE"；
3. 与函数索引约束效果等价（均实测），生成列方案更符合 JJX 现有表风格（项目无函数索引先例，不引入新机制）；
4. 用户评审明确"优先考虑可读性更好的方案：生成列 active_guard"，实测后选定。

---

## 5. Entity 最终字段

`ProductionDispatchNode.java`（@Data + @TableName，MyBatis-Plus 规范，与 ProductionDispatch 风格一致）：

| 字段 | 类型 | 语义 |
|---|---|---|
| nodeId | Long | @TableId(AUTO) 节点主键 |
| dispatchId | Long | 所属派工单 |
| parentNodeId | Long | 责任来源节点（第 1 级=NULL） |
| assigneeType | String | 责任主体类型（P1=USER） |
| assigneeId | Long | 责任用户 ID |
| assigneeName | String | 责任用户姓名快照 |
| orgId / orgName / orgPath | Long/String/String | 当时所属组织快照 |
| nodeStatus | String | 责任实例当前历史状态 |
| assignedBy / assignedByName | Long/String | 本次责任由谁指派 |
| assignedAt | LocalDateTime | 本次责任正式生效时间 |
| closedAt | LocalDateTime | 本次责任周期结束时间 |
| remark | String | 备注/迁移说明 |
| createBy / createTime / updateBy / updateTime | — | 审计字段 |

**active_guard 不映射**（DB 生成列，Java 不写，未暴露给业务；测试 `entityHasNoActiveGuardField` 显式断言）。

---

## 6. NodeStatusEnum

`DispatchNodeStatusEnum`（@Getter + code/label + fromCode/labelOf，与 DispatchStatusEnum 同风格）：

| code | label |
|---|---|
| ACTIVE | 当前责任持有中 |
| DELEGATED | 已向下委派 |
| REASSIGNED | 已被同级改派 |
| RETURNED | 已退回上级责任层 |
| COMPLETED | 责任链最终完成 |
| CANCELLED | 任务取消 |

- 未知值 `labelOf` 原样返回（兼容历史，不抛异常）
- **与 DispatchStatusEnum / ExecutionStatusEnum 完全分离**（测试断言值域不同）
- 未修改 DispatchStatusEnum（git diff 0 行）

`DispatchAssigneeTypeEnum`：仅 `USER("USER","用户")`；ORG/TEAM/WORKSHOP 未实现（fromCode 返回 null）。

---

## 7. Mapper 能力

`ProductionDispatchNodeMapper extends BaseMapper<ProductionDispatchNode>`：
- 基础能力（MyBatis-Plus 自带）：insert / selectById / selectList / selectOne / selectCount / delete（wrapper）
- P1-A 覆盖的查询场景（测试验证）：按 dispatchId 查询、按 dispatchId+nodeStatus 查询（当前 ACTIVE）
- 按 parentNodeId 查询（idx_parent 已建，wrapper 直接支持）
- 未实现任何业务动作 Service（ASSIGN/DELEGATE/REASSIGN/RETURN 属 P1-C）
- 项目 Mapper 无 XML 风格（ProductionDispatchMapper 亦无 XML），保持一致

---

## 8. Backfill 实现方式

`migration/` 包两个类：

**DispatchNodeBackfillParser（纯函数，可单测）**
- `parseChain(operatorsJson)` → `List<NodeDraft>`：按 legacy JSON 数组稳定顺序生成链草稿
- 空/null/`[]` → 空链；非法 JSON → `BackfillParseException`；非数组 JSON → 异常（保守，不静默）
- 同 level 多人检测：`ambiguous` 标记（如 dispatch 3 的 98/104 同 level:1）
- 无 userId 项跳过

**DispatchNodeBackfill（@Component 执行器）**
- `backfillAll()`：扫描所有 operators 非空 dispatch → 逐条 backfill → 输出统计（scanned/migrated/skipped/errors + 错误明细）
- `backfillDispatch(dispatchId)`：单条
- 规则：
  - 空 operators → 不建节点（dispatch 保持待派）
  - 1 个 operator → 1 个 ACTIVE node
  - 多个 operator → 前 N-1 DELEGATED、最后 1 ACTIVE，parentNodeId 顺序串联
  - assignedAt = dispatch.assignTime（缺失用 createTime）；assignedBy = dispatch.assignedBy（不编造逐级历史）
  - org 快照 = 按当前 user/dept 重建（非真实历史），remark 标记 `ORG_RECONSTRUCTED`
  - remark = `LEGACY_BACKFILL`（+ 歧义时 `LEGACY_AMBIGUOUS_ORDER`）
- `rollbackBackfilled()` / `rollbackDispatch(id)`：按 `remark LIKE %LEGACY_BACKFILL%` 删除

---

## 9. Backfill 幂等策略

- 执行前 `selectCount(dispatchId)` 检查：**已有节点 → 跳过**（返回 -1，计入 skipped）
- 重复运行安全：不会生成第二条责任链
- backfillAll 输出统计：扫描数 / 迁移数 / 跳过数 / 异常数

---

## 10. Backfill 异常处理

- 非法 JSON / 非数组 JSON → 捕获 `BackfillParseException` → 记录 dispatchId + 异常信息 → **跳过该 dispatch → 继续处理其他**（不中断整体）
- 异常计入 errors + errorMessages 明细（P1-E 正式执行时人工处理异常项）

---

## 11. Backfill 回滚方案

- 无需新增 migration batch 字段（遵守"不为回滚额外加字段"约束）
- 回滚依据：`remark` 含 `LEGACY_BACKFILL` 标记
- `rollbackBackfilled()`：`DELETE FROM production_dispatch_node WHERE remark LIKE '%LEGACY_BACKFILL%'`
- **未来 P1 业务生成的 Node remark 不含该标记 → 不受回滚影响**
- 另有最简兜底：直接 DROP 表（纯新增表，对旧数据零影响）

---

## 12. 测试列表及结果

| 测试 | 覆盖 | 结果 |
|---|---|---|
| DispatchNodeStatusEnumTest（4 例） | 六态映射、fromCode/labelOf、与 Dispatch/Execution 分离、AssigneeType 仅 USER | ✅ 4/4 |
| DispatchNodeBackfillParserTest（9 例） | 空/单/多级/同 level 多人/缺 level/非法 JSON/非数组/无 userId/标记常量 | ✅ 9/9 |
| ProductionDispatchNodeMapperTest（4 例） | insert 核心字段、按 dispatch 查询、按状态查 ACTIVE、实体无 activeGuard | ✅ 4/4 |
| **P1-A 新增小计** | | **17/17 通过** |
| 全量 production 包测试（含 P0 遗留 9 个） | | **26/26 通过** |

**说明**：项目测试基建为纯 Mockito（JDK25 下无 Spring 集成测试），Mapper 真实 SQL 行为（insert/唯一约束/条件更新）已通过"真实 MySQL 事务回滚验证"覆盖（见 §13），不留任何测试数据。

---

## 13. MySQL 唯一 ACTIVE 实测结果（真实 MySQL 8.4.10，事务回滚）

| # | 场景 | 结果 |
|---|---|---|
| 1 | 不同 dispatch 各自插入 ACTIVE | ✅ 成功 |
| 2 | 同一 dispatch 第二个 ACTIVE | ✅ **ERROR 1062 Duplicate entry '99901-1' for key 'uk_dispatch_active'** |
| 3 | 第一个 ACTIVE → DELEGATED（条件 UPDATE） | ✅ ROW_COUNT=1，active_guard 变 NULL |
| 4 | 关闭后再插新 ACTIVE | ✅ 成功（guard=1） |
| 5 | 多个非 ACTIVE 共存（DELEGATED+RETURNED+REASSIGNED） | ✅ 成功（guard 全 NULL） |
| 6 | 无 ACTIVE 时条件更新 | ✅ ROW_COUNT=0（并发守卫语义正确） |
| 7 | 验证后 ROLLBACK | ✅ **残留 0 行** |

生成列验证：DELEGATED→NULL、ACTIVE→1、RETURNED/REASSIGNED→NULL，全部符合设计。

---

## 14. compile/test 结果

| 项 | 结果 |
|---|---|
| `mvn compile` | ✅ EXIT=0（0 ERROR） |
| `mvn test`（production 包 26 例） | ✅ BUILD SUCCESS，26/26 通过 |
| 前端 | 未改动（无需 build） |
| git | **未提交**（全部为 untracked 新文件） |

---

## 15. 是否执行了正式 backfill

**❌ 否。** backfill 工具仅编写 + 单测（解析器纯函数测试 + 幂等逻辑），未对现有 3 条 dispatch 执行任何 backfill。

---

## 16. production_dispatch 原数据是否变化

**❌ 完全没变化。** 实测确认：

```
dispatch_id  execution_id  status  operators
1            1             1       [{"userId":96,"userName":"冲型车间主任","level":1}]
2            2             2       [{"userId":96,"userName":"冲型车间主任","level":1}]
3            3             1       [{"userId":98,...},{"userId":104,...}]
```

与 P1 设计复核时点完全一致（3 行原样）。

---

## 17. production_dispatch_node 当前实际记录数

**0 条业务数据**（验证用测试数据已全部 ROLLBACK；实测 `SELECT COUNT(*)` = 0）。

---

## 18. 是否有数据库 schema 之外的数据变更

**无。** 唯一执行的是建表 DDL；验证数据全部在事务内回滚；无 DML 落到正式表；production_dispatch/log/execution/order 等零变更。

---

## 19. 是否发现阻塞 P1-B 的问题

**否。** 未发现阻塞项。备注两个观察（非阻塞）：
1. Mapper 真实 SQL 行为验证依赖"事务回滚 + 手动 SQL"（项目无 Spring 集成测试基建）——P1-B 若需 mapper 集成测试，建议届时评估是否引入轻量集成测试，或继续沿用本报告验证方式；
2. backfill 的 org 快照为"迁移时重建"（非真实历史），已在设计中明确并标记 `ORG_RECONSTRUCTED`——符合"不要伪造无法确定的真实历史"原则。

---

## 20. 是否满足进入 P1-B 的条件

**✅ 满足。** 对照验收标准：

| 验收项 | 状态 |
|---|---|
| production_dispatch_node 表成功建立 | ✅ |
| NodeStatus 正确 | ✅ 6 态 + 未知值兼容 |
| 一个 dispatch 最多一个 ACTIVE | ✅ DB 唯一约束（1062 实测） |
| 多个历史 Node 可以存在 | ✅ 非 ACTIVE 多节点共存实测 |
| Node Entity/Mapper 可工作 | ✅ 测试 + 真实 SQL 验证 |
| backfill 工具已完成但未执行正式数据 | ✅ |
| backfill 可幂等 | ✅ selectCount 前置检查 |
| 异常 JSON 不影响整体处理 | ✅ BackfillParseException 捕获跳过 |
| 旧 dispatch 数据原样 | ✅ §16 |
| 旧业务仍能运行 | ✅ 零修改旧代码（git 时间戳验证） |
| 0 P1-B/C/D 越界实现 | ✅ 未接入 Service、未改 operators、未改 page 权限、未改 isDispatched、未动前端 |
| 测试通过 | ✅ 26/26 |
| compile 通过 | ✅ |
| 没有提交 Git | ✅ |

---

*报告完。P1-A 完成，停止等待人工验收。*
