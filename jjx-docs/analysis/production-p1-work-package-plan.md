# JJX Production P1 Dispatch V1 Work Package Plan

> 版本：v1.0（实施拆分稿）
> 日期：2026-08-19
> 状态：只读规划，未改代码/未改数据库/未执行 migration/未提交 Git
> 依据：已批准最终架构（人工评审"有条件批准"）+ P1 设计报告 v1.0 + 现有代码/表/数据复核
> 说明：不再讨论已拍板架构；仅输出可独立验收的 Work Package 与执行计划

---

## 1. 最终已批准架构摘要（评审后冻结）

```
ProductionOperationExecution ──1:1── ProductionDispatch ──1:N── ProductionDispatchNode
```

- **ProductionDispatch**：某道 execution 的派工任务/责任链容器；`execution_id UNIQUE`（1:1）保持不变；P1 不引入 DispatchTask。
- **ProductionDispatchNode**：**一次生产任务责任持有实例**（"某个责任主体在某一时间段内正式持有该任务责任的一次历史实例"），`assignedAt/closedAt` 表达责任周期。
- **第一版派工对象**：仅 USER；`assigneeType` 保留但合法值只有 `USER`；组织信息（orgId/orgName/orgPath）为**当时所属组织快照**；ORG/TEAM/WORKSHOP 不能成为 ACTIVE owner；多级组织业务用"责任人+组织快照"表达。
- **Node 字段**：nodeId / dispatchId / parentNodeId / assigneeType / assigneeId / assigneeName / orgId / orgName / orgPath / nodeStatus / assignedBy / assignedByName / assignedAt / closedAt / remark / createBy / createTime / updateBy / updateTime。**不需要** level/root/path/previousNodeId。
- **Node 状态（六态）**：ACTIVE / DELEGATED / REASSIGNED / RETURNED / COMPLETED / CANCELLED。同一 dispatch 正常最多一个 ACTIVE。
- **四动作**：ASSIGN（建 root ACTIVE）/ DELEGATE（向下，旧 ACTIVE→DELEGATED + 新建子节点 ACTIVE）/ REASSIGN（同级换人，旧 ACTIVE→REASSIGNED + 新建同层节点 ACTIVE，parent=旧.parent）/ **RETURN（关闭当前 + 创建新的上级责任实例，禁止重新激活旧 parent）**。
- **核心规则**：任何一次责任重新落到某个人 = 创建新 Node；Node 历史尽量不可变（只允许改 nodeStatus/closedAt/update audit；禁止改 assigneeId/assigneeName/org 快照/assignedAt）。
- **唯一 ACTIVE 双保险**：数据库约束（**生成列方案，实测通过**，见 §2.3）+ 事务内条件 UPDATE（affected rows=1 校验）。
- **operators JSON**：`production_dispatch_node` 是唯一 Source of Truth；`production_dispatch.operators` 仅是 Legacy Projection / 兼容缓存；**所有新业务逻辑禁止读 operators 做核心判断**。
- **RETURN 定稿**：N3(张三,ACTIVE) RETURN → N3 ACTIVE→RETURNED(closedAt=now) → 创建 N4：assignee=N2.assignee、assigneeName=N2.assigneeName、org 快照=N2 当时的快照、**parentNodeId=N2.parentNodeId**（即 N4 是"原上一级责任层的新的责任持有实例"，不是 N3 的子节点）。
- **P1 范围红线**：不做 WorkReport/数量拆分/多人并行/报工汇总/Quality 绑定/Trace 事件/EventBus/APS/产能/设备负荷/工时/成本/OEE/sys_dept 重构/leader 改造/ORG owner/TEAM owner。Execution 状态机不重构，start/pause/complete/report 权限 P1 不强制修改。

---

## 2. 与原设计（v1.0 报告）相比的人工评审调整

| # | 点 | v1 设计 | 评审后最终方案 | 对本 WP 的影响 |
|---|---|---|---|---|
| 1 | RETURN 实现 | 激活旧 parent（方案 A） | **禁止激活旧 parent；创建新的上级责任实例 N4（assignee=原上级 assignee，parentNodeId=原上级.parentNodeId）** | P1-C 动作逻辑按新方案；链展示支持"同一责任层先后多个持有实例" |
| 2 | 唯一 ACTIVE DDL | 函数索引（CASE WHEN） | **优先生成列 active_guard（ACTIVE→1，其他→NULL）+ UNIQUE(dispatch_id, active_guard)**；两方案均须实测 | ✅ 已实测（§2.3），选生成列方案 |
| 3 | operators 定位 | "兼容投影，双写" | **升级为明确声明：Legacy Projection only，禁止核心业务读取**；代码加注释（可 @Deprecated，不影响序列化） | P1-B 承担改造，P1-C 写路径统一走 Node |
| 4 | isDispatched/page 数据权限 | "P1 内完成" | **必须在 P1 内完成**（Node exists 查询替代 LIKE JSON）；并区分"历史上参与过"vs"当前 ACTIVE 待办" | P1-B 明确两个查询语义 |
| 5 | 工作台查询 | 单一列表 | **分别提供：我参与过 / 我的当前任务** | P1-B 数据权限 + P1-D API（my-tasks） |
| 6 | dispatch_log RETURN | content 表达 | **优先不改表；content 中明确 fromNodeId/toNewNodeId/操作人/动作/from-to assignee** | P1-C 写 content 规范 |
| 7 | start/complete 身份校验 | v1 建议加"最终 ACTIVE assignee 本人"校验 | **P1 不强制修改谁可 start/pause/complete/report**；现有 dispatch.start()/complete() 与 execution 联动保留，不扩大 | P1-C 明确不动 start/complete 校验逻辑 |
| 8 | 权限缺口（28/30/32） | 记录待 P2 | 保持记录，P1 不处理 | 无 |
| 9 | TEAM_ASSIGNED/ASSIGNED | 语义微调 | **P1 不做 status 编码迁移**，六态兼容；记录为后续 cleanup | P1-A/E 不动 dispatch.status 值 |
| 10 | 旧 assign/appendLevel API | 改造 | **制定兼容/废弃策略，不直接删除**（旧前端不能崩） | P1-C 兼容策略 + P1-D 切换 |

### 2.1 唯一 ACTIVE DDL —— 生成列方案（选定，实测通过）

```sql
CREATE TABLE `production_dispatch_node` (
  `node_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `dispatch_id`      BIGINT       NOT NULL COMMENT '派工单ID(production_dispatch.dispatch_id)',
  `parent_node_id`   BIGINT       NULL COMMENT '上级节点ID(第1级=NULL，源头主管直派)',
  `assignee_type`    VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '责任主体类型：USER(P1仅支持)',
  `assignee_id`      BIGINT       NOT NULL COMMENT '责任主体ID(用户ID)',
  `assignee_name`    VARCHAR(64)  NOT NULL COMMENT '责任主体姓名快照',
  `org_id`           BIGINT       NULL COMMENT '责任主体所属部门ID(快照)',
  `org_name`         VARCHAR(100) NULL COMMENT '责任主体所属部门名称(快照)',
  `org_path`         VARCHAR(500) NULL COMMENT '部门祖先链快照(如"1/5/6/7")',
  `node_status`      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '节点状态：ACTIVE/DELEGATED/REASSIGNED/RETURNED/COMPLETED/CANCELLED',
  `assigned_by`      BIGINT       NULL COMMENT '指派人用户ID',
  `assigned_by_name` VARCHAR(64)  NULL COMMENT '指派人姓名',
  `assigned_at`      DATETIME     NULL COMMENT '责任持有开始时间',
  `closed_at`        DATETIME     NULL COMMENT '责任持有结束时间(流转/完成/取消)',
  `remark`           VARCHAR(500) NULL COMMENT '备注/退回原因',
  `create_by`        VARCHAR(64)  NULL COMMENT '创建人',
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        VARCHAR(64)  NULL COMMENT '更新人',
  `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  -- 唯一 ACTIVE 保证：生成列 active_guard
  `active_guard`     TINYINT GENERATED ALWAYS AS (CASE WHEN `node_status`='ACTIVE' THEN 1 ELSE NULL END) STORED COMMENT '唯一ACTIVE守卫列',
  PRIMARY KEY (`node_id`),
  UNIQUE KEY `uk_dispatch_active` (`dispatch_id`, `active_guard`),
  KEY `idx_dispatch` (`dispatch_id`),
  KEY `idx_assignee_status` (`assignee_id`, `node_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派工责任链节点(责任持有实例)';
```

### 2.2 为什么选生成列方案（实测结论）

在真实 MySQL 8.4.10 上以 `CREATE TEMPORARY TABLE`（会话级，无持久化副作用）实测：

| 验证项 | 结果 |
|---|---|
| 方案 A：生成列 active_guard + UNIQUE(dispatch_id, active_guard) | ✅ 同一 dispatch 第二个 ACTIVE 插入报 `ERROR 1062 Duplicate entry '1-1'`，正确拒绝 |
| 方案 B：函数索引 `UNIQUE (dispatch_id, (CASE WHEN node_status='ACTIVE' THEN 1 ELSE NULL END))` | ✅ 同样正确拒绝（`ERROR 1062`） |
| UPDATE 释放唯一（模拟 DELEGATE 顺序：先条件 UPDATE 旧 ACTIVE→DELEGATED，再 INSERT 新 ACTIVE） | ✅ UPDATE 后 active_guard 变 NULL（释放），新 ACTIVE 插入成功，两行共存（node1=DELEGATED/guard NULL, node2=ACTIVE/guard 1） |

**选定方案 A（生成列）**，理由：
1. 可读性更好：active_guard 是真实列，`SELECT ... WHERE active_guard IS NOT NULL` 直接可用、可被优化器使用、DBA 可直接查看；
2. 语义显式：表结构一眼看懂"该表同一 dispatch 至多一个 ACTIVE"；
3. 与函数索引约束效果等价（均实测），但生成列方案更符合 JJX 现有表风格（无函数索引先例，避免引入新机制）。

---

## 3. P1-A：Database & Node Foundation

### 3.1 目标
建立 `production_dispatch_node` 数据基础：建表 + 唯一 ACTIVE 约束 + Node 实体/枚举/Mapper + 历史 backfill 脚本（编写，不在本 WP 执行）。

### 3.2 范围
- migration 文件 `jjx-server/sql/migrations/V20260819_001__dispatch_node.sql`（§2.1 DDL：建表 + uk_dispatch_active + idx_dispatch + idx_assignee_status）
- `ProductionDispatchNode.java` 实体（与 ProductionDispatch 风格一致：@TableName/@TableId AUTO/手写审计字段；**无 del_flag**——Node 历史不可变全量保留，同 dispatch_log 先例；active_guard 生成列 @TableField(exist=false) 不映射实体字段）
- `DispatchNodeStatusEnum.java`（ACTIVE/DELEGATED/REASSIGNED/RETURNED/COMPLETED/CANCELLED + fromCode/labelOf）
- `ProductionDispatchNodeMapper.java`（BaseMapper）
- backfill 脚本（**应用层一次性脚本**，Java 工具类或独立 main；见 3.5）
- migration rollback/验证方案文档化

### 3.3 明确不做
- 不改前端
- 不改 production_dispatch 表结构
- 不执行 migration（本 WP 只交付脚本，执行在 P1-E）
- 不做任何 Node 读写业务逻辑（P1-B）
- 不动 DispatchStatusEnum / 不迁移 status 编码

### 3.4 修改文件 / 新增文件
| 类型 | 文件 |
|---|---|
| 新增 | `jjx-server/sql/migrations/V20260819_001__dispatch_node.sql` |
| 新增 | `jjx-server/src/main/java/com/jjx/production/domain/entity/ProductionDispatchNode.java` |
| 新增 | `jjx-server/src/main/java/com/jjx/production/enums/DispatchNodeStatusEnum.java` |
| 新增 | `jjx-server/src/main/java/com/jjx/production/mapper/ProductionDispatchNodeMapper.java` |
| 新增 | backfill 应用层脚本（建议 `jjx-server/src/main/java/com/jjx/production/migration/DispatchNodeBackfill.java`，一次性 main，或独立 Python 脚本——实施时选定其一） |
| 修改 | 无（如实体需要，仅可能动 `DispatchVO` ？否——VO 扩展在 P1-B） |

### 3.5 backfill 脚本设计（评审 §十 定稿）
- **执行前检查**：`SELECT COUNT(*) FROM production_dispatch_node WHERE dispatch_id = ?` 有节点则跳过（幂等，防重复 backfill）
- **空 operators**：不建节点，dispatch 保持待派状态（status 0）
- **1 个 operator**：创建 1 个 ACTIVE node（root）
- **多个 operator**：按 legacy JSON 数组稳定顺序生成链：前 N-1 个 DELEGATED、最后一个 ACTIVE；parentNodeId 顺序串联
- **assignedAt**：优先 `dispatch.assign_time`；无法恢复逐级真实时间则同一 assign_time 或 create_time，remark 标记 `LEGACY_BACKFILL`
- **assignedBy**：root 用 `dispatch.assigned_by`；后续节点无法确定逐级真实操作人时**不编造**，沿用源头主管或 NULL，remark 标记
- **重复 operator**：允许作为历史兼容数据迁移，但脚本输出异常报告
- **同 level 多人**：忽略旧 level 业务含义，按 JSON 顺序生成兼容链，remark 标记 `LEGACY_AMBIGUITY`（如 dispatch 3：98→104 串链，104 ACTIVE）
- **异常**：非法 JSON / 用户不存在 → 跳过该 dispatch + 写迁移日志 + 汇总报告，不中断
- **可回滚**：仅插入新表，`DELETE FROM production_dispatch_node WHERE remark LIKE '%LEGACY_BACKFILL%'` 或直接 DROP 表即可完全回滚

### 3.6 数据库影响
| 项 | 说明 |
|---|---|
| 新表 | `production_dispatch_node`（纯新增） |
| 现有表 | 零改动（production_dispatch/log/execution/order 均不动） |
| 数据 | 零改动（backfill 只读旧表、只写新表） |
| 约束 | uk_dispatch_active（生成列唯一）、idx_dispatch、idx_assignee_status |

### 3.7 API 影响 / 前端影响
无（纯数据层）。

### 3.8 兼容策略
- 新表与旧表完全并存；旧代码/旧页面无感知
- backfill 后 operators 列保持原值不变（双写从 P1-C 开始），旧页面读 operators 不受影响

### 3.9 测试
| 测试 | 方式 |
|---|---|
| DDL 可执行性 + 唯一 ACTIVE 行为 | 已在真实 MySQL 8.4.10 实测通过（§2.2）；P1-A 交付时附实测记录 |
| BackfillTest（空/单/多级/同 level 多人/重复/非法 JSON/用户缺失） | 纯 Java 单测（backfill 逻辑抽成可测函数，不依赖 DB） |
| 幂等测试 | 同一 dispatch 重复执行 backfill → 第二次跳过 |

### 3.10 验收标准
1. `production_dispatch_node` 可正常创建（DDL 实测记录）
2. 唯一 ACTIVE：同 dispatch 插第二个 ACTIVE 报 1062（实测）
3. Node 实体/枚举/Mapper 编译通过
4. backfill 脚本对 3 条现存 dispatch 产出正确链（dry-run 输出可审查：dispatch 1/2 单节点 ACTIVE；dispatch 3 串链 98→104）
5. 无任何现有文件行为变化（mvn compile 全绿）

### 3.11 回滚方式
- 未执行 migration：删除脚本即可
- 已执行（P1-E 阶段）：`DROP TABLE production_dispatch_node`，旧数据零影响

### 3.12 前置依赖
无（P1 第一块）。

### 3.13 风险
| 风险 | 缓解 |
|---|---|
| 生成列与 MyBatis-Plus 交互（insert 时 active_guard 由 DB 计算） | active_guard 实体 @TableField(exist=false)；insert SQL 不含该列，实测确认 |
| backfill 逐级 parentNodeId 依赖自增 ID | 应用层脚本逐 dispatch 处理，每插一条取 last_insert_id，无并发问题（单线程） |

### 3.14 Codex/OpenClaw 可执行性
✅ **适合一次性交付**：边界清晰（一个 migration + 4 个 Java 文件 + 1 个 backfill 工具 + 单测），无跨模块耦合，验收标准客观（编译 + DDL 实测记录 + dry-run 输出）。

---

## 4. P1-B：Node Read Model & Compatibility

### 4.1 目标
建立 Node 读模型：当前 ACTIVE 查询、责任链查询、isDispatched 改造、page 数据权限改 Node、operators legacy projection builder、Node-first/legacy fallback 兼容。

### 4.2 范围
- Node 查询服务（可新建 `DispatchNodeQueryService` 或在 DispatchServiceImpl 内）：`getActiveNode(dispatchId)` / `getChain(dispatchId)`（责任链树/列表，按责任持有顺序） / `getActiveByAssignee(userId)`（我的当前待办） / `existsNodeForAssignee(userId)`（是否历史上参与过）
- **isDispatched 改造**：`DispatchServiceImpl.isDispatched()` 从 operators LIKE → `EXISTS (SELECT 1 FROM production_dispatch_node WHERE assignee_id=?)`（评审 §十七）
- **page() 数据权限改造**：从 `assigned_by OR operators LIKE` → `assigned_by OR EXISTS node WHERE assignee_id=me`（评审 §十七）；**区分两个语义**：
  - "我参与过"（历史含当前）= assigned_by=me OR EXISTS node(assignee_id=me)
  - "我的当前任务"（ACTIVE 待办）= EXISTS node(dispatch_id=d.dispatch_id AND assignee_id=me AND node_status='ACTIVE')
  - DispatchQueryDTO 增加 `viewType: ALL/MINE_PARTICIPATED/MY_TASKS`（默认 ALL 保持现状）
- **operators legacy projection builder**：`buildOperatorsProjection(dispatchId)` 从 Node 链生成 `[{userId,userName}]` JSON（无 level，按责任顺序），供 P1-C 写路径 + P1-E 一致性校验 + 旧 API 返回使用
- **Node-first / legacy fallback**：`getChain()` 优先 Node；Node 不存在（未 backfill 的 dispatch）→ fallback 解析 operators JSON 构造兼容链（仅迁移安全期使用，P1-E 后新核心 API 不依赖 fallback）
- `DispatchVO` 增加 `currentAssignee`（nodeId/assigneeId/assigneeName/orgId/orgName/orgPath/nodeStatus）与 `nodes`（链结构，可选——P1-D 需要时再返回，本 WP 先提供后端能力）

### 4.3 明确不做
- 不实现四动作（P1-C）
- 不改前端
- 不新增 API 端点（Controller 不动；page 查询参数扩展属后端能力，前端传参在 P1-D）
- 不重构 DispatchStatusEnum
- 不删除 operators 列

### 4.4 修改文件 / 新增文件
| 类型 | 文件 |
|---|---|
| 新增 | `jjx-server/src/main/java/com/jjx/production/service/DispatchNodeQueryService.java`（接口） |
| 新增 | `jjx-server/src/main/java/com/jjx/production/service/impl/DispatchNodeQueryServiceImpl.java`（实现，含 Node 查询 + projection builder；或并入 DispatchServiceImpl，实施时按代码规模定） |
| 新增 | `jjx-server/src/main/java/com/jjx/production/domain/vo/DispatchNodeVO.java`（节点 VO） |
| 修改 | `jjx-server/src/main/java/com/jjx/production/service/impl/DispatchServiceImpl.java`（isDispatched、page 数据权限、DispatchQueryDTO 处理） |
| 修改 | `jjx-server/src/main/java/com/jjx/production/domain/dto/DispatchQueryDTO.java`（+ viewType） |
| 修改 | `jjx-server/src/main/java/com/jjx/production/domain/vo/DispatchVO.java`（+ currentAssignee/nodes，后端填充逻辑） |

### 4.5 数据库影响
无（只读 Node 表）。

### 4.6 API 影响
- `GET /production/dispatch/page` 的查询语义增强（viewType 参数，默认行为不变 = 现状兼容）
- 其余端点无变化

### 4.7 前端影响
无（本 WP 不改前端；前端使用新字段在 P1-D）。

### 4.8 兼容策略
- page 默认 viewType=ALL → 行为与现状一致（数据权限逻辑等价替换：LIKE→EXISTS，结果集一致）
- isDispatched 语义等价（LIKE 全表扫 → EXISTS 索引查），P0 报告 TECH-DEBT #5 关闭
- fallback 只在新核心 API 的迁移安全期生效；P1-E 验证后移除 fallback 分支（或保留但默认 Node）

### 4.9 测试
| 测试 | 方式 |
|---|---|
| NodeQueryTest | getActiveNode / getChain 树构建（纯 Java） |
| IsDispatchedNodeTest | exists 查询语义（mock mapper；JdbcTemplate 相关用最小验证，P0 经验） |
| PagePermissionTest | 数据权限三段（ALL/MINE_PARTICIPATED/MY_TASKS）结果过滤正确 |
| ProjectionBuilderTest | Node 链 → operators JSON 与旧格式一致（无 level 或兼容） |
| FallbackTest | 无 Node 时 fallback operators 解析兼容 |

### 4.10 验收标准
1. isDispatched 无 LIKE JSON（grep 验证）
2. page() 数据权限 SQL 无 LIKE JSON（grep 验证）
3. "我参与过"与"我的当前任务"两个语义后端分别可查
4. projection builder 输出与旧 operators 结构兼容（前端 OperatorChain 仍能解析）
5. 默认 page 行为与改造前一致（回归）

### 4.11 回滚方式
git revert 本 WP 改动（纯后端读逻辑，无数据依赖）；Node 表保留无碍。

### 4.12 前置依赖
P1-A（Node 表 + 实体/Mapper）。

### 4.13 风险
| 风险 | 缓解 |
|---|---|
| EXISTS 查询与 LIKE 结果集不一致（历史 operators 含已删用户等） | 演示数据量小；P1-E 做一致性核对（Node vs operators 差异清单） |
| fallback 长期残留 | P1-E 验收项：新核心 API 无 fallback 依赖 |

### 4.14 Codex/OpenClaw 可执行性
✅ **适合一次性交付**：纯读逻辑 + 等价替换，验收客观（grep 无 LIKE + 语义测试）。

---

## 5. P1-C：Dispatch Actions

### 5.1 目标
实现四类派工动作（ASSIGN/DELEGATE/REASSIGN/RETURN）的 Node 化服务层逻辑：事务、并发条件更新、DispatchLog、权限、operators 投影双写、旧 assign/appendLevel 兼容策略。

### 5.2 范围
- **ASSIGN**（改造现有 `assign()` 中"新建"分支）：
  - 前置：execution 存在；dispatch 不存在 或 无 ACTIVE 节点（待派工/整单退回）
  - 权限：超管 OR hasPermission("production:dispatch:assign")（沿用 P0-04）
  - 行为：创建 root Node（parentNodeId=NULL, assignee=第 1 级负责人, ACTIVE）；dispatch 新建（或 REJECTED→ASSIGNED 恢复）；assignedBy/assignedByName/assignedAt=当前操作人/时间；写 log ASSIGN；同步 operators projection
- **DELEGATE**（新方法）：
  - 前置：有 ACTIVE 节点；操作人 = ACTIVE.assigneeId（默认）——管理员/有 assign 权限者代操作：**允许**，但目标必须仍是 ACTIVE assignee 的手下（组织关系校验按 ACTIVE 人的部门，不按操作人）
  - 行为：条件 UPDATE 旧 ACTIVE→DELEGATED(closedAt=now) → 建新 Node(parent=旧.nodeId, assignee=目标手下, ACTIVE, assignedBy=实际操作人) → log DELEGATE → projection 双写
- **REASSIGN**（新方法）：
  - 前置：有 ACTIVE 节点；操作人=ACTIVE.assigneeId / 超管 / 有 assign 权限者
  - 行为：条件 UPDATE 旧 ACTIVE→REASSIGNED(closedAt=now) → 建新 Node(**parentNodeId=旧.parentNodeId** 即同层, assignee=新人, ACTIVE) → re_dispatch_count+1 → log REASSIGN → projection 双写
- **RETURN**（新方法，按评审定稿）：
  - 前置：有 ACTIVE 节点；操作人=ACTIVE.assigneeId / 超管 / 有 assign 权限者
  - 行为：条件 UPDATE 旧 ACTIVE→RETURNED(closedAt=now, remark=原因) → **创建 N4：assignee=N2.assignee、assigneeName=N2.assigneeName、org 快照=N2 的 org 快照、parentNodeId=N2.parentNodeId**（N2=旧 ACTIVE 的 parent 节点）→ log RETURN（content 含 fromNodeId=旧.nodeId, toNewNodeId=N4.nodeId）→ projection 双写
  - **root 退回**（旧 ACTIVE.parentNodeId IS NULL）：语义=整单退回 → 旧 ACTIVE→RETURNED，dispatch status→REJECTED（兼容现有 reject 行为），log REJECT（保留现有 reject 接口作此入口）
- **事务顺序**（评审 §八，统一模板）：
  1. 校验 dispatch/execution 状态
  2. 查当前 ACTIVE node
  3. 校验操作权限
  4. 条件关闭当前 ACTIVE（`UPDATE node SET node_status=? WHERE node_id=? AND node_status='ACTIVE'`）
  5. 检查 affected rows = 1（否则抛"任务已被其他人处理，请刷新后重试"）
  6. 创建新 ACTIVE node
  7. 写 DispatchLog
  8. 重新生成 operators projection
  9. 更新必要 dispatch projection（status/assign_time/re_dispatch_count）
  10. commit；任一步失败全部回滚
- **并发**：`SELECT ... FOR UPDATE` dispatch 行锁 + 条件 UPDATE + uk_dispatch_active 兜底；**不用分布式锁**
- **DispatchLog**：action 增 DELEGATE/RETURN；content 规范（操作人/动作/from assignee→to assignee/nodeId 信息）；不改表结构
- **权限**：新增 `checkNodeRight(dispatchId, operatorId)`（ACTIVE assignee 本人/超管/hasPermission(assign)）；方法级 @SaCheckPermission 照旧
- **旧 assign/appendLevel 兼容策略**（评审 §十六）：
  - `POST /production/dispatch/assign` 保留路径；dispatchId 为空的"新建"分支 → 内部走 ASSIGN（Node 化）
  - dispatchId 非空的"追加/改派"分支（appendLevel）：**兼容期映射**——level=1 且目标是替换第 1 级 → REASSIGN；level>1 或 transferFrom → 映射为 DELEGATE（向下追加一级）；`chainComplete` 语义丢弃（链完整=有 ACTIVE 节点，Node 模型无需此概念）
  - 旧前端在 P1-D 切换前调用这些路径仍可工作（结果一致、Node 生成正确）
  - 代码标注：appendLevel 为 legacy 兼容路径，P1-D 后前端不再调用，后续废弃
- **start()/complete()**：**保持现状**（评审 §十三：P1 不强制修改 start/pause/complete/report 权限）；syncByExecution 联动不变；仅当 dispatch 有 ACTIVE 节点时，complete 可将最终 ACTIVE→COMPLETED（若评审要求 P1 处理，见 5.12 风险）

### 5.3 明确不做
- 不新增 API 端点（Controller 在 P1-D）
- 不改前端
- 不重构 Execution 状态机 / 不强制 execution 操作权限
- 不删除 operators 列 / 不删旧方法（兼容期并存）
- 不做 WorkReport/数量拆分

### 5.4 修改文件 / 新增文件
| 类型 | 文件 |
|---|---|
| 修改 | `jjx-server/src/main/java/com/jjx/production/service/impl/DispatchServiceImpl.java`（核心：四动作 + checkNodeRight + 事务 + projection 双写 + appendLevel 兼容映射） |
| 修改 | `jjx-server/src/main/java/com/jjx/production/service/DispatchService.java`（接口 + delegate/reassign/return 声明） |
| 新增 | `jjx-server/src/main/java/com/jjx/production/domain/dto/DispatchDelegateDTO.java`（toUserId, remark） |
| 新增 | `jjx-server/src/main/java/com/jjx/production/domain/dto/DispatchReassignDTO.java`（toUserId, remark） |
| 新增 | `jjx-server/src/main/java/com/jjx/production/domain/dto/DispatchReturnDTO.java`（reason） |
| 修改 | （如 P1-B 未合并）`DispatchNodeQueryService` 被本 WP 调用 |

### 5.5 数据库影响
写 Node 表（INSERT/UPDATE node_status）；dispatch 表写 status/assign_time/re_dispatch_count/operators（投影）；log 表 INSERT。无 DDL。

### 5.6 API 影响
- 无新端点（Controller 层 P1-D）
- 现有 `POST /assign` 行为：新建分支 Node 化（结果语义等价）；dispatchId 分支走兼容映射（行为与旧一致，Node 同步正确）
- `POST /{id}/reject` 保留（=root 整单退回入口）

### 5.7 前端影响
无（P1-D 前旧前端继续调旧接口，经兼容路径工作）。

### 5.8 兼容策略
- 旧接口/旧前端不崩（assign 兼容映射）
- operators projection 每次动作后同步（旧展示正确）
- 新动作方法（delegate/reassign/return）仅供 P1-D 新前端调用

### 5.9 测试
| 测试 | 方式 |
|---|---|
| AssignTest | 建 root ACTIVE、权限、uk_execution 幂等、REJECTED 恢复指派 |
| DelegateTest | 旧→DELEGATED、新 ACTIVE(parent=旧)、目标手下校验、非 assignee 拒绝、代操作（管理员）允许但按 ACTIVE 人组织关系校验 |
| ReassignTest | 旧→REASSIGNED、新节点 parent=旧.parent（同层）、re_dispatch_count+1 |
| ReturnTest | **旧→RETURNED、N4 assignee=原上级 assignee、N4.parentNodeId=原上级.parentNodeId、不激活旧 parent（断言 N2 仍 DELEGATED）**；root 退回→整单 REJECTED |
| ConcurrencyTest | 条件 UPDATE affected=0 → 抛"已被其他人处理"（mock 行为） |
| LegacyCompatTest | 旧 assign dispatchId 分支映射（appendLevel→REASSIGN/DELEGATE）结果与 Node 一致 |
| LogTest | DELEGATE/RETURN content 含 fromNodeId/toNewNodeId |

### 5.10 验收标准
1. 四动作各自单测全绿；RETURN 测试显式断言"不激活旧 parent、创建新上级责任实例"
2. 并发条件更新生效（affected rows 校验）
3. 每次动作后 operators projection 与 Node 一致（一致性单测）
4. 旧 assign 路径兼容（旧前端调用不崩、Node 正确）
5. start/complete 行为与现状一致（未扩大改动）
6. grep 验证：新动作代码不读 operators 做判断

### 5.11 回滚方式
git revert 本 WP（服务层逻辑）；DB 数据由 P1-E 前备份/或 Node 表可 DROP 重来；旧路径未破坏，可随时切回旧 jar。

### 5.12 前置依赖
P1-A + P1-B。

### 5.13 风险
| 风险 | 缓解 |
|---|---|
| complete 是否关闭最终 ACTIVE 节点（P1 语义边界） | 建议：complete 联动时若存在 ACTIVE 节点 → 置 COMPLETED(closedAt=now)（Node 状态机完整）；实施时与用户确认一次，默认按此 |
| 代操作权限放大 | 限定：代操作仅限有 assign 权限者，且目标校验仍按 ACTIVE 人组织关系（已写入设计） |
| RETURN 链展示复杂（同层多持有实例） | P1-D 责任链 Timeline 按"责任持有顺序"展示（每节点=一次持有实例），前端无需树形严格嵌套 |

### 5.14 Codex/OpenClaw 可执行性
⚠️ **可交付但需详细规格**：核心业务逻辑，本报告 §5.2 已给完整行为（含事务顺序、RETURN 定稿、兼容映射），可直接作为实现规格；建议 Codex 执行 + 用户重点 review RETURN/并发两处。

---

## 6. P1-D：API & Frontend

### 6.1 目标
暴露 V1 API 并改造前端：dispatch 页面显示当前责任人/责任链、四动作按钮、移除旧 level UI、责任链 Timeline。

### 6.2 范围（内部拆两个子阶段，各自可独立验收）
**D1 API（后端端点）**：
- 新增：`GET /production/dispatch/{id}/nodes`（责任链，按责任持有顺序）、`GET /production/dispatch/{id}/current-node`、`GET /production/dispatch/my-tasks`（viewType=MY_TASKS 语义）、`POST /production/dispatch/{id}/delegate`、`POST /production/dispatch/{id}/reassign`、`POST /production/dispatch/{id}/return`
- 改造：`GET /page`（响应含 currentAssignee/nodes 字段）、`GET /{id}`（含 nodes）
- 权限：list(261) 读；assign(262)+节点身份 写；不新增权限点
- 每个 API 定义 Request/Response/权限/前置条件/错误场景（见 §6.6 示例）
- 旧端点保留（assign/batch-assign/reject/start/complete/underlings/team-persons/my-persons/my-depts/logs）

**D2 前端（页面改造）**：
- `dispatch/index.vue`：
  - 列表列调整：工单号/产品/工序/Dispatch 状态/**当前责任人**（ACTIVE 高亮 tag）/**当前责任人所属组织**/设备/更新时间
  - 操作按钮按身份+权限渲染：初始派工（无链且有权限）/ 继续派工 DELEGATE（ACTIVE 本人或有权）/ 改派 REASSIGN / 退回 RETURN / 开始 / 完成 / 流水
  - **移除 level 1/2/3 UI**：删除"追加第2级/第3级"、转派弹窗的级别概念、"链完整性"开关；新弹窗：指派（选第 1 级负责人+设备+备注）、下派（选 ACTIVE 人手下）、改派（同级选人）、退回（原因必填，root 提示整单退回）
  - 责任链详情弹窗：按 Node 历史 Timeline（每节点卡：人/谁派的/何时/状态；ACTIVE 突出），替代旧"执行人链"弹窗
- `OperatorChain/index.vue`：**优先读取 Node chain**（新 prop `nodes`），fallback operators（兼容期）；ACTIVE 节点高亮（★）
- `api/production/dispatch.ts`：新端点封装 + DispatchVO 类型扩展（currentAssignee/nodes）
- 不做：甘特图/APS/拖拽/数量拆分/多人并行/复杂任务池

### 6.3 明确不做
- 不重写整个调度台
- 不引入新状态机 UI
- 不删除旧 API（废弃策略：P1-E 后评估）
- 不做 execution 页改造（currentAssigneeName 展示可选——若做，仅在 execution 页加一列展示，最小化）

### 6.4 修改文件 / 新增文件
| 类型 | 文件 |
|---|---|
| 修改 | `jjx-server/src/main/java/com/jjx/production/controller/DispatchController.java`（+6 端点，改 page/{id} 响应） |
| 修改 | `jjx-server/src/main/java/com/jjx/production/domain/vo/DispatchVO.java`（+currentAssignee/nodes 已 P1-B 就绪） |
| 修改 | `jjx-web/src/api/production/dispatch.ts`（新 API + 类型） |
| 修改 | `jjx-web/src/views/production/dispatch/index.vue`（列表/按钮/弹窗/Timeline） |
| 修改 | `jjx-web/src/components/OperatorChain/index.vue`（nodes prop 优先） |
| 修改（可选） | `jjx-web/src/views/production/execution/index.vue`（+当前责任人列，最小） |

### 6.5 数据库影响
无 DDL（复用 P1-A 表）；写路径由 P1-C 承担。

### 6.6 API 定义示例
| 端点 | 权限 | Request | Response | 前置 | 错误场景 |
|---|---|---|---|---|---|
| POST `/{id}/delegate` | assign(262)+节点身份 | `{toUserId, remark?}` | DispatchVO | 有 ACTIVE；操作人=ACTIVE.assignee 或超管或有 assign 权；toUserId ∈ underlings(ACTIVE.assignee) | 404 派工单不存在 / 409 无 ACTIVE 或已被处理（affected=0）/ 403 非授权 / 400 目标非手下 |
| POST `/{id}/reassign` | assign(262)+节点身份 | `{toUserId, remark?}` | DispatchVO | 有 ACTIVE；同 DELEGATE 授权 | 同上 |
| POST `/{id}/return` | assign(262)+节点身份 | `{reason}` | DispatchVO | 有 ACTIVE；同 DELEGATE 授权；root 退回=整单 REJECTED | 同上；400 reason 必填 |
| GET `/{id}/nodes` | list(261) | — | List<DispatchNodeVO>（责任持有顺序） | 存在 | 404 |
| GET `/{id}/current-node` | list(261) | — | DispatchNodeVO 或 null | 存在 | 404 |
| GET `/my-tasks` | list(261) | page 参数 | PageResult<DispatchVO>（MY_TASKS 过滤） | 登录 | — |
| GET `/page` | list(261) | +viewType | PageResult<DispatchVO>（含 currentAssignee/nodes） | 登录 | — |

### 6.7 前端影响
见 §6.2 D2。旧 level UI 全部从新页面消失；旧页面入口（order 页"派工"按钮跳转）保持可用。

### 6.8 兼容策略
- 新前端调新 API；旧 API 保留（P1-E 后评估废弃）
- OperatorChain 双模式（nodes 优先/operators fallback）
- 旧浏览器缓存刷新即可（无路由变化）

### 6.9 测试
| 测试 | 方式 |
|---|---|
| Controller 层新端点冒烟（可省，P0 先例未做 controller 测试） | 可选 |
| vue-tsc --noEmit | 必做 |
| 前端手测脚本（指派→下派→改派→退回→开始→完成全流程） | P1-D 验收手测 |
| 旧 level UI 无残留（grep：level 1/2/3、追加第2级 等关键词） | grep 验证 |

### 6.10 验收标准
1. 列表显示当前责任人 + 所属组织 + 设备 + 更新时间
2. 四动作按钮按身份/权限正确渲染（不是 ACTIVE 人不显示 DELEGATE/RETURN）
3. 责任链 Timeline 正确（含 RETURN 后"原上级重新持责"新实例显示）
4. 页面无 level 1/2/3 概念（grep 验证）
5. vue-tsc 0 errors；旧功能（批量/流水/开始/完成）不回归

### 6.11 回滚方式
前端 git revert（旧前端与旧 API 兼容，随时可回）；后端 D1 revert（新端点消失，旧端点仍在）。

### 6.12 前置依赖
P1-A + P1-B + P1-C（动作后端就绪）。

### 6.13 风险
| 风险 | 缓解 |
|---|---|
| 前端改动面大（列表/弹窗/组件/API 四处） | 拆 D1/D2 子阶段，D2 内按"列表→详情→操作→Timeline"渐进；每步 vue-tsc + 手测 |
| RETURN 后链展示语义（同层两个持有实例） | Timeline 按时间顺序平铺，每卡标注"第 N 次持责"；ACTIVE 高亮 |
| 旧用户习惯（转派=追加级别）改变 | 下派弹窗明确"派给你的手下"；操作提示语清晰 |

### 6.14 Codex/OpenClaw 可执行性
⚠️ **建议拆为两次交付**：D1（后端 API，适合 Codex，规格清晰）→ 人工验收 → D2（前端，适合 Codex 但需用户提供页面验收，建议 Codex 出代码 + 用户手测）。D2 不建议与 D1 合并一次交付。

---

## 7. P1-E：Migration Cutover & Regression

### 7.1 目标
正式执行历史 backfill、验证 Node 与 operators 投影一致、全链路回归、清理 fallback 范围、输出验收报告。

### 7.2 范围
- 执行 migration（`V20260819_001__dispatch_node.sql`）——**本 WP 才允许执行**（用户批准后）
- 执行 backfill（P1-A 脚本，dry-run 先行 → 正式执行 → 汇总报告）
- 验证：
  - 3 条旧 dispatch 的 Node 链正确（dispatch 1/2 单 ACTIVE；dispatch 3 串链 98→104 ACTIVE）
  - 每个 dispatch 至多一个 ACTIVE（SQL：`SELECT dispatch_id, COUNT(*) FROM node WHERE node_status='ACTIVE' GROUP BY dispatch_id HAVING COUNT(*)>1` 应为空）
  - Node 与 operators projection 一致性：P1-C 起每次写都同步；本 WP 做全量比对（Node 链 → projection builder → 与 operators 列 diff）
  - 旧页面回归：order 页→dispatch 页入口、dispatch 列表/流水/开始/完成、execution 页、批量派工
- 清理 fallback 范围：新核心 API 移除 legacy fallback 分支（或确认默认 Node 且无 fallback 调用）
- 完整 P1 测试套件执行（A-E 全部测试）
- 输出《P1 Dispatch V1 验收报告》（含 18 项 Final Gate 逐项结果）

### 7.3 明确不做
- 不删除 operators 列（后续 cleanup）
- 不废弃旧 API（仅标注）
- 不做 P2/P3/P4 任何实现

### 7.4 修改文件 / 新增文件
| 类型 | 文件 |
|---|---|
| 修改 | 执行类：无代码新增；可能微调 backfill 脚本 bug |
| 新增 | `jjx-docs/analysis/production-p1-dispatch-v1-acceptance-report.md`（验收报告） |

### 7.5 数据库影响
执行 migration（建表）+ backfill（写 Node 表）。现有表零改动。

### 7.6 API 影响 / 前端影响
无新增；验证回归。

### 7.7 兼容策略
- backfill 前备份 Node 表可回滚（或未 backfill 前 DROP）
- fallback 清理仅限新核心 API；旧 API 行为不变

### 7.8 测试
| 测试 | 方式 |
|---|---|
| 3 条 dispatch 迁移正确性 | SQL + 人工核对清单 |
| 唯一 ACTIVE 空结果验证 | SQL 聚合查询 |
| projection 一致性 | 比对脚本（Node→builder→operators diff） |
| 全链路回归（order→dispatch→execution） | 手测脚本 |
| A-E 全部测试套件 | mvn test + vue-tsc |

### 7.9 验收标准
§13 P1 Final Gate 18 项全部通过。

### 7.10 回滚方式
- 数据：`DROP TABLE production_dispatch_node`（若需要保留已生成投影则先停止服务）；旧页面/旧代码不受影响
- 代码：git revert A-D
- **回滚顺序**：前端 → 后端 → DROP 表

### 7.11 前置依赖
P1-A/B/C/D 全部验收通过。

### 7.12 风险
| 风险 | 缓解 |
|---|---|
| backfill 后发现旧数据歧义 | dry-run 先行，报告可审查；数据量小（3 条）可人工确认 |
| fallback 清理影响旧页面 | 仅清理新核心 API；旧 API 读 operators 投影（P1-C 起双写保证正确） |

### 7.13 Codex/OpenClaw 可执行性
⚠️ **不适合纯自动执行**：涉及生产数据变更，建议**人工监督执行**（用户批准后逐命令执行 + 核对）；Codex 可准备脚本与报告框架，执行与验收由人把关。

---

## 8. Migration 顺序

```
P1-A: 编写 V20260819_001__dispatch_node.sql（建表+索引）  [不执行]
  ↓
P1-E: 人工批准后执行 migration  → dry-run backfill → 正式 backfill → 验证
  ↓
P1-C 起: 写路径双写 projection（Node + operators）
  ↓
P1-E 末: 一致性核对（Node vs operators diff）
```

- 建表与 backfill 之间隔了 P1-B/P1-C（代码就绪后数据才切换），避免"表在但代码未支持"的空窗
- 若需提前 backfill 验证（可选）：在 P1-A 验收后单独执行 dry-run，不写库

## 9. API 演进顺序

```
现状: 13 端点（assign 一接口三语义）
  ↓ P1-B: page 支持 viewType（默认兼容）
  ↓ P1-C: assign 内部 Node 化 + 兼容映射（对外无感）
  ↓ P1-D1: +delegate/reassign/return/nodes/current-node/my-tasks（新端点）
  ↓ P1-E 后: 评估废弃 appendLevel/level 相关语义（旧端点保留）
```

## 10. 前端演进顺序

```
现状: 列表 + 转派(level) + 指派/改派 + 退回 + 开始/完成 + 流水
  ↓ P1-D2a: OperatorChain nodes 支持（后端字段就绪后可先行，双模式）
  ↓ P1-D2b: 列表增加"当前责任人/所属组织"列
  ↓ P1-D2c: 操作按钮按身份/权限 + 四动作弹窗（下派/改派/退回新语义）
  ↓ P1-D2d: 责任链 Timeline 弹窗 + 移除 level UI
  ↓ P1-E: 回归验证
```

## 11. 测试矩阵（P1 全量）

| WP | 测试 | 类型 |
|---|---|---|
| A | DDL 唯一 ACTIVE 实测（已过）/ BackfillTest / 幂等 | 实测+单测 |
| B | NodeQuery / IsDispatchedNode / PagePermission / ProjectionBuilder / Fallback | 单测 |
| C | Assign / Delegate / Reassign / Return / Concurrency / LegacyCompat / Log | 单测 |
| D | vue-tsc / 前端手测 / grep level 残留 | 静态+手测 |
| E | 3 条迁移核对 / 唯一 ACTIVE SQL / projection diff / 全链路回归 / 全套件 | 集成+手测 |

## 12. 回滚方案（P1 整体）

| 层 | 回滚 |
|---|---|
| 前端 | git revert P1-D（旧前端与旧 API 兼容，立即恢复） |
| 后端 | git revert P1-A~D（旧 jar 重启）；新端点消失，旧端点行为不变 |
| 数据库 | `DROP TABLE production_dispatch_node`（纯新增，零影响）；backfill 数据随表删除 |
| 顺序 | 前端 → 后端 → DROP 表；或整体 revert + DROP |
| 前提 | P1-A~D 每 WP 独立提交（用户自行提交），commit 边界清晰 |

## 13. P1 Final Gate（18 项验收）

| # | 条件 | 验证方式 |
|---|---|---|
| 1 | production_dispatch_node 正常建立 | SHOW CREATE TABLE + DDL 实测记录 |
| 2 | 历史 dispatch 成功 backfill | 3 条核对清单 |
| 3 | 每个 dispatch 最多一个 ACTIVE | SQL 聚合 HAVING COUNT(*)>1 为空 + 唯一约束实测 |
| 4 | ASSIGN 正确 | AssignTest + 手测 |
| 5 | DELEGATE 正确 | DelegateTest + 手测 |
| 6 | REASSIGN 不覆盖历史 | ReassignTest（旧节点 REASSIGNED 保留） |
| 7 | RETURN 创建新责任节点、不激活旧 parent | ReturnTest 显式断言（N2 保持 DELEGATED，N4 为新实例） |
| 8 | operators 不再作为核心业务 Source of Truth | grep：新业务代码无 operators 判断；代码注释 Legacy projection only |
| 9 | isDispatched 不再 LIKE JSON | grep 验证 |
| 10 | page 数据权限不再 LIKE JSON | grep 验证 |
| 11 | 三级硬编码完全退出新流程 | grep：新动作代码无 level 1-3 校验（appendLevel 兼容路径除外，标注 legacy） |
| 12 | 前端不再显示固定 1/2/3 级逻辑 | grep level/追加第2级 等关键词为空 + 手测 |
| 13 | 旧页面/旧数据兼容 | 旧 API 调用回归 + 3 条旧 dispatch 可查 |
| 14 | execution 现有流程未被破坏 | execution 页回归 + syncByExecution 联动测试 |
| 15 | 数据库 migration 可验证 | migration 脚本 + 执行记录 + 回滚演练记录 |
| 16 | 测试通过 | 全量测试矩阵绿 |
| 17 | build/compile 通过 | mvn compile + vue-tsc 0 errors |
| 18 | 不包含 P2/P3/P4 越界实现 | 代码审查：无 WorkReport/数量拆分/质量绑定/Trace/EventBus 等 |

## 14. 风险（P1 整体）

| 风险 | 等级 | 缓解 |
|---|---|---|
| RETURN 语义复杂（链展示/同层多持有实例） | 中 | 设计已定稿 + ReturnTest 显式断言 + Timeline 按持有顺序平铺 |
| 并发边界（两人同时操作同一 dispatch） | 中 | 行锁 + 条件更新 + DB 唯一约束三层；ConcurrencyTest |
| 旧前端与 Node 化后端不一致 | 中 | assign 兼容映射 + projection 双写 + 旧 API 回归 |
| fallback 长期残留 | 低 | P1-E 明确清理新核心 API fallback |
| 权限缺口（28/30/32）被操作触发 | 低 | 现状已存在；P1 不处理，报告记录 |
| 范围蔓延（顺手做 Execution/WorkReport） | 中 | §1 红线 + Final Gate #18 + 每 WP 明确"不做" |

## 15. 建议第一个正式实施的 WP

**P1-A（Database & Node Foundation）**。

理由：
1. 无前置依赖，边界最清晰，可独立验收（编译 + DDL 实测 + dry-run 输出）
2. 唯一 ACTIVE DDL 已实测通过（生成列方案），实施风险最低
3. 后续 B/C/D 全部依赖 Node 表与实体，先行打底符合"顺序施工"原则
4. 适合一次性交给 Codex/OpenClaw 执行，验收客观

**建议实施节奏**：P1-A → 人工验收 → P1-B → 人工验收 → P1-C → 人工验收 → P1-D（D1→D2）→ 人工验收 → P1-E（人工监督执行）→ P1 Final Gate。

---

*报告完。本轮只读，未改代码/未改数据库/未执行 migration/未提交 Git。等待人工评审。*
