# JJX Production P1 Dispatch V1 Design & Migration Plan

> 版本：v1.0（评审稿）
> 日期：2026-08-19
> 状态：只读设计，未改代码/未改数据库/未执行 migration/未提交 Git
> 范围：P1 责任链改造（DispatchNode），不含 P2 WorkReport / P3 Quality / P4 Trace
> 依据：生产模块现状复核（代码/表/数据全量盘点）+ dispatch-redesign-v2.md（设计资料，不照抄）

---

## 1. Executive Summary

P1 把当前"**execution → dispatch → operators JSON → 最多三级链**"改造为"**execution → dispatch → dispatch_node → 动态多级责任链**"。

核心结论（本报告的关键决策预览）：

| # | 决策点 | 结论 |
|---|---|---|
| 1 | Node 表是否新建 | ✅ 新建 `production_dispatch_node`，责任链结构化存储 |
| 2 | Dispatch 是否保留 | ✅ 保留，定位"责任链容器"（一道 execution 一条 dispatch，1:1 不变） |
| 3 | 第一版派工对象 | **只支持 USER**（方案 A），组织作为 assignee 的属性快照，不做 ORG/TEAM 节点 |
| 4 | level 字段 | **不需要**，parent_node_id 推导层级 |
| 5 | 唯一 ACTIVE 保证 | **数据库函数索引唯一约束**（MySQL 8.4 支持）+ 事务内条件更新，双保险 |
| 6 | REASSIGN 实现 | **关闭旧节点 + 新建节点**（历史不可变），不覆盖原节点 |
| 7 | RETURN 实现 | **重新激活 parent 节点**（旧节点置 RETURNED），不创建 RETURN 节点 |
| 8 | operators 兼容 | **双写投影**（写 Node 同步更新 operators JSON），渐进迁移，未来删除 |
| 9 | 历史数据 | 3 条现存 dispatch 迁移为节点链，脚本可重复执行、可回滚 |
| 10 | 迁移方式 | 新表 + backfill，**不改 production_dispatch 表结构**，0 破坏 |
| 11 | Execution 联动 | P1 只提供 currentAssignee projection，**不做严格执行权限联动** |
| 12 | DispatchLog | 复用，action 增 DELEGATE/RETURN，表结构不动 |

---

## 2. 当前 Dispatch 问题（现状复核结论）

### 2.1 代码/表结构全景（已全量复核）

**表**（`jjx_erp_db`，MySQL 8.4.10）：

| 表 | 关键字段 | 备注 |
|---|---|---|
| `production_dispatch` | dispatch_id, order_id, order_no, execution_id(**UNIQUE uk_execution**), process_name, process_order, team_id/team_name, equipment_id/equipment_name, **operators(JSON字符串)**, assigned_by/assigned_by_name, assign_time, **status(0-5)**, reject_reason, re_dispatch_count, remark, del_flag | 表注释仍写"0-4 静态枚举"，与枚举不一致（注释过时） |
| `production_dispatch_log` | log_id, dispatch_id, order_id, action(ASSIGN/REASSIGN/REJECT/START/COMPLETE), content, operator_id, operator_name, create_time | 无 del_flag（全量保留） |

**实体/枚举**（`jjx-server/.../production/`）：
- `ProductionDispatch.java`：与表一致；`delFlag` 带 `@TableLogic`
- `ProductionDispatchLog.java`：无 del_flag
- `DispatchStatusEnum.java`：PENDING(0)/TEAM_ASSIGNED(1)/ASSIGNED(2)/EXECUTING(3)/COMPLETED(4)/REJECTED(5) —— 注意 1=已派班组（链未完整）、2=已派工（链完整），**这个区分是 operators JSON 时代的产物**
- `ExecutionStatusEnum.java`：0-9 完整执行状态机（P1 不动）

**Service**（`DispatchServiceImpl.java`，约 850 行）：
- `page()`：JdbcTemplate 手写 SQL，execution LEFT JOIN dispatch；数据权限按 `assigned_by` / `operators LIKE`（95-101 行）
- `assign()`：新建 dispatch + 第 1 级执行人；已有 dispatchId → `appendLevel()`（追加/替换级别）
- `appendLevel()`：**1-3 级硬编码**（`lv < 1 || lv > 3` 报错）；转派校验 `transferFrom` 必须在链上、新人在其手下
- `mergeChain()`：TreeMap 按 level 合并 operators JSON
- `reject()`：**整单退回**（status→REJECTED），不是退回上一级
- `start()/complete()`：改 dispatch 状态 + 直改 execution 状态（syncExecutionStatus 2/4）
- `syncByExecution()`：execution 模块回调（2=执行中 4=已完成）
- `isDispatched()`：**operators LIKE 查询**（P0-04 保留项，P1 应换 exists 查询）
- `underlings()`：WITH RECURSIVE 按 `sys_dept.leader=userName` 递归（leader 存用户名，TECH-DEBT）
- `myDeptTree()/teamPersons()/myPersons()`：部门树/候选人（组织树驱动）

**Controller**（`DispatchController.java`）：`/production/dispatch` 下 13 个端点，权限 `production:dispatch:list/assign/start`。

**前端**：
- `jjx-web/src/views/production/dispatch/index.vue`：工作台（execution LEFT JOIN dispatch 展示）+ 指派/改派/批量/转派/退回/开始/完成弹窗 + 流水时间线
- `jjx-web/src/components/OperatorChain/index.vue`：解析 operators JSON 展示链（列表 firstOnly 显示第 1 级 + "＋N级"）
- `jjx-web/src/components/OperatorPicker/index.vue`：部门树勾选执行人
- `jjx-web/src/api/production/dispatch.ts`：13 个 API 封装
- execution 页面**无派工入口**；production order 页面"派工"按钮（`OrderTableActions.vue:73`）→ 跳 dispatch 页按 orderNo 过滤

**现存数据**（演示数据）：

| dispatch_id | execution_id | operators JSON | status | 备注 |
|---|---|---|---|---|
| 1 | 1 | `[{"userId":96,"userName":"冲型车间主任","level":1}]` | 1(已派班组) | 流水：ASSIGN→REJECT→REASSIGN |
| 2 | 2 | `[{"userId":96,...}]` | 2(已派工) | 单级链 |
| 3 | 3 | `[{"userId":98,"userName":"印刷一组组长","level":1},{"userId":104,"userName":"印刷一组工人","level":1}]` | 1(已派班组) | **同一 level 两人**（同级多人） |

### 2.2 operators 字段所有读写位置（真实代码位置清单）

**写（4 处）**：
1. `DispatchServiceImpl.java:254` `d.setOperators(buildOperatorsJson(dto.getOperatorIds(), lv))` — assign 新建
2. `DispatchServiceImpl.java:356` `d.setOperators(mergeChain(d.getOperators(), dto.getOperatorIds(), lv))` — appendLevel 合并
3. `DispatchServiceImpl.java:589` `describeLevel()` 读（仅展示）
4. `DispatchServiceImpl.java:846-848` `describe()` 读（仅展示）

**读（关键依赖点）**：
1. **`page()` 数据权限**（`DispatchServiceImpl.java:97-100`）：`d.operators LIKE '%"userId":N,%' OR '%"userId":N}%'` — 判断"我是否在执行人链上"
2. **`isDispatched()`**（`DispatchServiceImpl.java:581-590`）：同 LIKE 查询 — 判断"是否被派工过"
3. `levelOfUser()`（330 行）：解析 JSON 找 transferFrom 的级别
4. `mergeChain()`（约 340-365 行）：解析+合并
5. `describeLevel()/describe()`（589/846）：解析展示
6. `DispatchVO.fromEntity()`（66 行）：operators 原样返回
7. `page()` SQL SELECT（139 行）：`d.operators` 列直接返回

**前端读（2 处）**：
1. `dispatch/index.vue` `parseOperators()`：解析 JSON 排序展示/转派
2. `OperatorChain/index.vue` `chain` computed：JSON.parse + sort by level

**所有依赖 operators 的语义**：
- 第一个 operator = 第 1 级（负责人/源头）
- 最后一个 operator = 当前执行人（`OperatorChain` 中 `★ 实际干活（报工挂此级）`）
- operator 数组长度 = 链级别数（前端显示"共 N 级"）
- level 1/2/3 = 三级硬编码
- 当前责任人 = 链上最后一级（隐含，无显式字段）
- 已派工用户判断 = `isDispatched()` LIKE 查询
- LIKE JSON 查询 = `page()` 数据权限 + `isDispatched()`

### 2.3 核心问题

1. **operators JSON 非结构化**：责任链只存在于字符串里，无法 SQL 查询"谁的活"（只能 LIKE）、无法可靠表达"当前责任人"、同 level 多人/链序靠前端排序猜
2. **三级硬编码**：`lv < 1 || lv > 3`（appendLevel），组织树实际 3 层（生产中心→车间→班组），一旦需要更深就崩
3. **一个 assign 接口干三件事**：新建/追加级别/改派（`appendLevel` 里 level 决定一切），语义混杂
4. **reject 是整单退回**：没有"退回上一级"概念；退回后整单 REJECTED，重新指派才能继续
5. **无"当前责任人"显式概念**：全靠"链上最后一人"推断，`current_operator` 不存在
6. **isDispatched/数据权限用 LIKE JSON**：性能差、语义绕（"被派工过才能派工"）
7. **dispatch status 与 execution status 联动但边界模糊**：start/complete 直接改 execution 状态（syncExecutionStatus），绕过 execution 模块校验

---

## 3. P1 目标

1. 责任链结构化：`production_dispatch_node` 每级一行，动态多级（不再 1-3 硬编码）
2. 派工层级由 parent_node_id 推导，组织树与派工树分离
3. 初始派工由权限决定（复用 `production:dispatch:assign`，P0-04 已定）
4. 后续派工由当前 ACTIVE 节点责任主体决定（节点身份，不是固定部门 leader）
5. 四动作语义互斥：ASSIGN（建链）/ DELEGATE（向下）/ REASSIGN（同级换人）/ RETURN（向上退回）
6. 保证"同一 dispatch 最多一个 ACTIVE 节点"（DB 约束 + 事务）
7. 保留历史兼容：operators 双写投影，旧页面不炸；生产数据不动
8. 明确 P1 不做：数量拆分 / WorkReport / 多人并行 / 质量绑定 / Trace / APS（见 §19）

---

## 4. ProductionDispatch 定位（P1 后）

**ProductionDispatch = 某一道 ProductionOperationExecution 的派工任务/责任链容器。**

- **保持 `execution_id UNIQUE`（1:1）**：一道工序实例（execution）只有一条 dispatch，内部通过多个 node 表达责任流转。
  - 论证：P1 不做数量拆分，1:1 与现状一致（uk_execution 已存在）；拆分数量（同一工序按量分给多人）是 P2+ 的事，届时再评估（可能改为 dispatch 内多链或多 dispatch）。
- dispatch 表保留并承载：工单/工序冗余、设备、源头主管（assigned_by/assigned_at）、整体状态（status 0-5）、审计字段。
- **不新建 DispatchTask**：dispatch 即任务容器，避免概念重复（用户原则 1）。

---

## 5. ProductionDispatchNode 数据模型

### 5.1 表结构设计（建议 DDL，未执行）

```sql
CREATE TABLE `production_dispatch_node` (
  `node_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `dispatch_id`      BIGINT       NOT NULL COMMENT '派工单ID(production_dispatch.dispatch_id)',
  `parent_node_id`   BIGINT       NULL COMMENT '上级节点ID(第1级=NULL，表示源头主管直派)',
  `assignee_type`    VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '责任主体类型：USER(第一版仅支持)',
  `assignee_id`      BIGINT       NOT NULL COMMENT '责任主体ID(用户ID)',
  `assignee_name`    VARCHAR(64)  NOT NULL COMMENT '责任主体姓名快照(改昵称不影响历史)',
  `org_id`           BIGINT       NULL COMMENT '责任主体所属部门ID(快照)',
  `org_name`         VARCHAR(100) NULL COMMENT '责任主体所属部门名称(快照)',
  `org_path`         VARCHAR(500) NULL COMMENT '部门祖先链快照(如"1/5/6/7"，组织调整不影响历史)',
  `node_status`      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '节点状态：ACTIVE/DELEGATED/REASSIGNED/RETURNED/COMPLETED/CANCELLED',
  `assigned_by`      BIGINT       NULL COMMENT '指派人用户ID',
  `assigned_by_name` VARCHAR(64)  NULL COMMENT '指派人姓名',
  `assigned_at`      DATETIME     NULL COMMENT '指派时间',
  `closed_at`        DATETIME     NULL COMMENT '节点关闭时间(流转走/完成/取消)',
  `remark`           VARCHAR(500) NULL COMMENT '备注/退回原因',
  `create_by`        VARCHAR(64)  NULL COMMENT '创建人',
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        VARCHAR(64)  NULL COMMENT '更新人',
  `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`node_id`),
  UNIQUE KEY `uk_dispatch_active` (`dispatch_id`, (CASE WHEN `node_status` = 'ACTIVE' THEN 1 ELSE NULL END)),
  KEY `idx_dispatch` (`dispatch_id`),
  KEY `idx_assignee_status` (`assignee_id`, `node_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派工责任链节点';
```

### 5.2 逐项分析（用户 10 问）

| # | 问题 | 结论 | 理由 |
|---|---|---|---|
| 1 | assignee 第一版只支持 USER？ | ✅ **只支持 USER** | 见 §10 论证 |
| 2 | 现在就需要 ORG/TEAM？ | ❌ 不需要 | 组织作为 assignee 属性快照；ORG 节点的"谁代表组织下派"问题 P1 无解 |
| 3 | org_id 表示责任主体所属组织还是被派组织 | **责任主体所属组织**（assignee 的部门快照） | 被派组织 = 派给谁 = 人；部门是人的属性。历史不可变靠快照 |
| 4 | 是否真的需要 level 字段 | ❌ **不需要** | parent_node_id 链可推导层级；加 level 反而与旧 operators.level 混淆、需维护一致性 |
| 5 | 是否需要 root 标记 | ❌ 不需要 | parent_node_id IS NULL 即 root |
| 6 | 是否需要 path 字段 | ❌ 不需要节点 path | 链路径由 parent 递归；org_path 是**部门**祖先链快照（≠节点路径） |
| 7 | parent_node_id 是否足以表达整个责任链 | ✅ 足够 | 单链模型（唯一 ACTIVE）下，parent 链即全部历史 |
| 8 | 是否需要 previous_node_id | ❌ 不需要 | parent + 状态 + 时间戳足够还原"谁传给谁" |
| 9 | 是否需要 version/乐观锁 | ⚠️ 不引入 version 字段 | 用 **DB 函数索引唯一约束 + 事务内条件 UPDATE（WHERE node_status='ACTIVE'）** 双保险；乐观锁多余 |
| 10 | 是否需要 deleted 逻辑删除 | ❌ **不需要** | 节点是历史审计数据，全量保留（同 dispatch_log 无 del_flag 先例）；REASSIGN/RETURN 用状态表达，不删行 |

### 5.3 唯一 ACTIVE 的数据库保证（MySQL 8.4 实测支持）

```sql
UNIQUE KEY `uk_dispatch_active` (`dispatch_id`, (CASE WHEN `node_status`='ACTIVE' THEN 1 ELSE NULL END))
```

- **原理**：函数索引中非 ACTIVE 行计算结果为 NULL，**MySQL 唯一索引允许多个 NULL**，不冲突；同一 dispatch 下两个 ACTIVE 行都算 1，唯一冲突 → 插入失败。
- 这是**数据库级硬约束**，即使应用层并发漏判，也无法产生两个 ACTIVE 节点。
- MySQL 8.4.10（实测 `SELECT VERSION()`）支持函数索引（8.0.13+ 引入）。

**Service 层配合**（事务边界见 §16）：
- 所有流转动作在**一个事务**内：`SELECT ... FOR UPDATE` 锁 dispatch 行 → 校验当前 ACTIVE → 条件 UPDATE 旧节点（`UPDATE ... SET node_status=... WHERE node_id=? AND node_status='ACTIVE'`，影响行数=0 则并发冲突报错）→ INSERT 新节点。

---

## 6. 节点状态机（最小集）

按 ASSIGN/DELEGATE/REASSIGN/RETURN 实际流转设计，不机械加状态：

| 状态 | 含义 | 进入方式 | 去向 |
|---|---|---|---|
| **ACTIVE** | 当前责任节点（谁的手上） | ASSIGN 建 root / DELEGATE 新建 / RETURN 激活 parent / REASSIGN 新建 | DELEGATED / REASSIGNED / RETURNED / COMPLETED / CANCELLED |
| **DELEGATED** | 已向下派出（监督中） | DELEGATE：旧 ACTIVE 变 DELEGATED | RETURN 时重新激活为 ACTIVE |
| **REASSIGNED** | 已被同级改派（历史不可变） | REASSIGN：旧 ACTIVE 关闭 | 终态（不再激活） |
| **RETURNED** | 已被退回上一级 | RETURN：旧 ACTIVE 关闭 | 终态（不重新激活，退回激活的是 parent） |
| **COMPLETED** | 任务完成（最终节点） | complete 联动 | 终态 |
| **CANCELLED** | 任务取消 | execution 取消/订单取消联动 | 终态 |

**不引入**：CLOSED（与 COMPLETED/CANCELLED 重叠）、PENDING（节点不存在=待派工，不需要节点级 pending）、REJECTED（与 RETURNED 重复，RETURNED 更精确）。

**不变式**：正常流转中同一 dispatch **有且仅有一个 ACTIVE 节点**；无 ACTIVE = 待派工（无节点）或整单退回（root RETURNED）。

---

## 7. 四类派工动作（正式拆分）

### 7.1 ASSIGN（初始派工）

| 项 | 设计 |
|---|---|
| 前置条件 | execution 存在；该 execution 无 dispatch 或 dispatch 无 ACTIVE 节点（待派工/整单退回后重新指派） |
| 操作人权限 | 超管 `*:*:*` 或 `production:dispatch:assign`（P0-04 已定复用） |
| 当前 ACTIVE 节点要求 | 无（新建链） |
| 创建什么节点 | **root 节点**（parent_node_id=NULL，assignee=第 1 级负责人，ACTIVE） |
| 旧节点 | 无 |
| dispatch_log | `ASSIGN`（内容：指派给 XXX） |
| production_dispatch | 新建（或整单退回后 status REJECTED→ASSIGNED）；assigned_by/assigned_at 记录源头主管 |
| execution 状态 | 不变（开工由 start 触发） |
| 失败场景 | execution 不存在 / 已有 ACTIVE（重复指派）/ assignee 不存在 |

### 7.2 DELEGATE（当前责任人向下派工）

| 项 | 设计 |
|---|---|
| 前置条件 | dispatch 有 ACTIVE 节点；操作人是 ACTIVE assignee **本人**（或管理员代操作，见权限） |
| 操作人权限 | **ACTIVE 节点 assignee 本人**；超管；有 `production:dispatch:assign` 权限者可**代操作**（必须选 ACTIVE 人的手下，校验按 ACTIVE assignee 的组织关系，不是操作人的） |
| 当前 ACTIVE 节点要求 | 存在且操作人 = assignee（或授权代操作） |
| 创建什么节点 | 新节点（parent=当前 ACTIVE 节点，assignee=目标手下，ACTIVE） |
| 旧节点 | ACTIVE → **DELEGATED**（监督中） |
| dispatch_log | `DELEGATE`（新增 action；内容：XX 下派给 YY） |
| production_dispatch | status 保持 ASSIGNED（已派工）；assign_time 更新 |
| execution 状态 | 不变 |
| 失败场景 | 无 ACTIVE / 非 assignee 操作 / 目标不在 assignee 手下（组织树校验）/ 重复点击（ACTIVE 已流转） |

### 7.3 REASSIGN（改派，同级换人）

| 项 | 设计 |
|---|---|
| 前置条件 | dispatch 有 ACTIVE 节点；操作人 = ACTIVE assignee 本人 / 超管 / 有 assign 权限者 |
| 操作人权限 | 同 DELEGATE（本人/超管/有权限代操作） |
| 当前 ACTIVE 节点要求 | 存在 |
| 创建什么节点 | **新节点**（parent=**旧节点的 parent**，即同级替换，assignee=新人，ACTIVE） |
| 旧节点 | ACTIVE → **REASSIGNED**（closed_at=now，历史不可变，不覆盖责任人） |
| dispatch_log | `REASSIGN`（内容：第N级 XX 改派为 YY） |
| production_dispatch | status 保持；re_dispatch_count+1 |
| execution 状态 | 不变 |
| 失败场景 | 无 ACTIVE / 非授权 / 新人不存在 / 重复点击 |

**为什么关旧建新（不直接改 assignee）**：审计完整性——原负责人何时接手、何时被换必须可查；直接覆盖会丢失"谁曾经负责"的历史，违背"责任链可靠表达谁传给谁"的 P1 第一目标。

### 7.4 RETURN（退回上一级）

| 项 | 设计 |
|---|---|
| 前置条件 | dispatch 有 ACTIVE 节点且 **parent_node_id 非空**；操作人 = ACTIVE assignee 本人 / 超管 / 有 assign 权限者 |
| 操作人权限 | 同 DELEGATE |
| 当前 ACTIVE 节点要求 | 存在 |
| 创建什么节点 | **不创建新节点** |
| 旧节点 | ACTIVE → **RETURNED**（closed_at=now，remark=退回原因） |
| parent 节点 | DELEGATED → **重新 ACTIVE**（激活上一级） |
| dispatch_log | `RETURN`（新增 action；内容：退回原因） |
| production_dispatch | status 保持 ASSIGNED（链仍在，只是责任上移） |
| execution 状态 | 不变 |
| 失败场景 | 无 ACTIVE / root 节点退回（无上级）→ 特殊处理为整单退回 |

**方案比较（用户问）**：
- 方案 A（激活 parent）：链长度不变，审计靠"旧节点 RETURNED + log"；复杂度低、UI 直观（退回=责任回到上一级）。✅ **推荐**
- 方案 B（创建 RETURN 节点）：链上多一个"退回动作节点"，审计更"显式"但链膨胀、状态机复杂（RETURN 节点自己也需状态）、查询麻烦。不推荐。

**root 节点（第 1 级）被退回**：无 parent 可激活 → 语义 = **整单退回**（dispatch status→REJECTED，root 节点 RETURNED），保留现有 `reject` 接口作为此场景入口（兼容现状），重新 ASSIGN 即可。

---

## 8. 权限模型

| 动作 | 权限 | 依据（现有数据） |
|---|---|---|
| ASSIGN（初始派工） | 超管 OR `production:dispatch:assign` | sys_menu 262 → role 1/29/30/31（P0-04 已定） |
| DELEGATE | ACTIVE assignee 本人 OR 超管 OR 有 assign 权限者（代操作，按 ACTIVE 人组织关系校验） | 复用 262；节点身份动态判定 |
| REASSIGN | 同 DELEGATE | 复用 262 |
| RETURN | 同 DELEGATE | 复用 262 |
| START/COMPLETE | `production:dispatch:start`（263）+ 最终 ACTIVE assignee 本人（P1 建议加节点身份校验，见 §14） | sys_menu 263 → role 1/29/31（注意 **30 派工主管无 263**） |
| 查看 | `production:dispatch:list`（261） | sys_menu 261 → role 1/29/30/31 |

**关键设计**：P1 **不新增权限点**（0 数据库变更，除新表外）——"谁能操作这张派工单"由**节点身份**决定（ACTIVE assignee），这是 P1 相对 P0 的本质升级：从"固定部门 leader 判断"和"被派工过 LIKE 判断" → "当前责任节点身份判断"。

**已知权限数据缺口（发现，不在 P1 处理，仅报告）**：
- 角色 28（PRODUCTION 全权限）**没有** 261/262/263（菜单缺失，查 sys_role_menu 为空）——与"全权限"名不符实
- 角色 30（派工主管）无 263（start/complete）——派工主管不能开工/完工
- 角色 32（操作工）无任何 production 权限——报工需操作工登录时无权限（P2 WorkReport 必改）

---

## 9. 组织模型关系

**sys_dept 不重构、不改 leader 类型**（用户约束）。

### 9.1 节点保存组织信息（历史快照）

```sql
org_id     = assignee 当前所属部门 dept_id
org_name   = 该部门 dept_name 快照
org_path   = 部门祖先链快照，如 "1/5/6/7"
```

- **为什么快照**：组织结构调整（部门改名/移动/删除）后，历史派工必须不变。节点存快照，展示永远用快照，不 join 当前 sys_dept。
- **org_path 从哪来**：⚠️ **sys_dept 表没有 ancestors 列**（实体 `SysDept.ancestors` 是 `@TableField(exist=false)` 假字段，不落库）。因此 org_path 必须**写节点时用 parent_id 递归计算**（如 7←6←5←1），写入快照即可。

### 9.2 当前用户信息来源（写入 assigned_by/org 快照时）

| 信息 | 来源 |
|---|---|
| userId / username | `SecurityUtils.getUserId()/getUsername()`（LoginVO.userInfo） |
| nickName / realName | `SecurityUtils.getRealName()` 或 `sysUserMapper.selectById(userId).getNickName()` |
| deptId | `LoginUser.deptId` 或 sys_user.dept_id |
| deptName | `sysDeptMapper.selectById(deptId).getDeptName()`（LoginUser 无 deptName，需查） |
| ancestors | **递归计算**（sys_dept 无 ancestors 列） |

### 9.3 组织树 → 派工树分离

- 组织树（sys_dept/sys_user）：只用于**候选人列表**（underlings/teamPersons/myPersons，现状已实现）
- 派工树（dispatch_node）：只表达**责任流转**（谁派给谁）
- 两者通过"delegate 目标必须是 ACTIVE assignee 的手下"这一**写入时校验**关联，读时完全独立——组织调整不影响已存在的派工链（快照）。

---

## 10. 第一版派工对象范围（方案论证）

**推荐：方案 A —— 只派给 USER。**

| 维度 | 分析 |
|---|---|
| 当前真实页面 | 指派/改派/转派全部选人（OperatorPicker 树勾选人、OperatorChain 显示人名），无"选部门/班组"提交逻辑（班组已改为自动推导=第 1 级执行人所属部门，2026-08-19 已砍独立班组选择） |
| 当前数据 | 3 条 dispatch operators 全部是 userId/userName（96/98/104） |
| 权限复杂度 | ORG 节点需要回答"谁代表组织下派"——P1 无法定义；USER 节点天然有"本人操作"语义 |
| P2 报工 | 报工（WorkReport）挂**执行人**，组织无法报工；USER 节点直接支持 |
| 车间→班组→人员 | 中间层用**负责人用户代表组织**（现状即如此：车间主任/班组长是用户，leader 存用户名），不需要 ORG 节点 |
| 结论 | **第一版 assignee_type 固定 USER**；org_id/org_name/org_path 作为 assignee 的部门快照随行保存，既满足"组织维度展示/筛选"，又不引入 ORG 节点复杂度 |

**如果 P1 支持组织节点**（明确不推荐，但回答问题）：
- 组织节点作为 ACTIVE owner 时，谁代表组织下派？需要"组织委托权"概念（如部门 leader 自动有权），引入第二套权限判定，且与现有"操作人=用户"模型冲突。
- 结论：P2/P3 有真实需求（如"派给车间"）时，加 `assignee_type='ORG'` + 委托规则，属增量演进，表结构已预留 assignee_type 字段。

---

## 11. 旧字段兼容策略（production_dispatch 逐项）

| 字段 | 分类 | P1 处理 |
|---|---|---|
| `operators` | **B（兼容投影，暂保留）** | 写 Node 时**同步双写** operators JSON（一个方法生成）；读方优先 Node；未来（P2 稳定后）删除 |
| `operator_names` | 不存在（表无此字段） | —（用户列表中概念字段，当前表无，无需处理） |
| `operator_count` | 不存在 | — |
| `current_operator` | 不存在（当前责任人隐含在链末级） | P1 由 ACTIVE 节点显式提供（Node.currentAssignee） |
| `status` | **A（正式字段）** | 保留 0-5 枚举，语义微调（见下） |
| `assigned_by/assigned_by_name/assign_time` | **A** | 保留=源头主管（root 节点 assigned_by 冗余一致） |
| `team_id/team_name` | **B（兼容投影）** | 保留；写入时 = 第 1 级 assignee 部门快照（现状逻辑），未来可删（Node 可推导） |
| `equipment_id/equipment_name` | **A** | 工序级属性，与链无关，保留 |
| `reject_reason` | **B** | 整单退回时保留；RETURN 原因进 node.remark + log |
| `re_dispatch_count` | **B** | 保留计数；Node REASSIGNED 可统计（未来替代） |
| `remark` | A | 保留 |
| `del_flag/create_*/update_*` | A | 保留（审计） |

**status 语义微调（P1 后）**：
- 0 待派工：无节点
- 1 已派班组：**兼容遗留**（迁移后不再产生，旧数据保留；展示仍"已派班组"）
- 2 已派工：有 ACTIVE 节点（链存在）
- 3 执行中：execution 开始联动
- 4 已完成：execution 完成联动
- 5 已退回：root 被退回（整单退回）

**原则**：渐进迁移——Node 为 source of truth，旧字段双写兼容，稳定后删除（不在 P1 激进删字段）。

---

## 12. 历史派工数据迁移（backfill 设计）

### 12.1 迁移规则（3 条现存 dispatch）

```
operators = [{"userId":张三,"level":1},{"userId":李四,"level":1},{"userId":王五,"level":2}]
→
Node1: assignee=张三 level隐含1, parent=NULL,        status=DELEGATED (被下派)  — 按数组顺序
Node2: assignee=李四 level隐含1, parent=Node1,       status=DELEGATED 或 ACTIVE
Node3: assignee=王五 level隐含2, parent=Node2,       status=ACTIVE (末级=当前责任人)
```

| 迁移点 | 规则 | 依据 |
|---|---|---|
| 节点顺序 | **按 operators 数组顺序**（level 升序作为辅助排序键；同 level 保持数组序） | mergeChain 已按 level 排序写 JSON |
| 同 level 多人（dispatch 3 案例：98/104 同 level:1） | 按数组顺序串成链（98→104），**末位 ACTIVE**，前面 DELEGATED；remark 标注"迁移：原同级别多人" | 现状模型下同 level 多人无 ACTIVE 语义，串链最接近"组长→工人"真实意图 |
| assigned_at 缺失 | 用 `dispatch.assign_time`；再缺失用 `create_time` | 现存 3 条均有 assign_time |
| assigned_by 恢复 | root 节点 = `dispatch.assigned_by/assigned_by_name`；后续节点优先从 `dispatch_log` 的 REASSIGN/ASSIGN 操作人推断（log 有 operator_id/name），找不到则沿用源头主管 + remark 标注 | dispatch 3 的 REASSIGN log（print_mgr 95 操作）可推断 98/104 由 95 派 |
| parent_node_id 生成 | 前一个节点 id（按顺序），root=NULL | — |
| 哪个节点 ACTIVE | **末级节点**（数组最后一人） | 当前执行人=链末级（OperatorChain ★ 语义） |
| 老 dispatch status 映射 | 0→无节点（待派工）；1/2→有链（ACTIVE 存在）；3→有链+execution 联动（status 保留 3）；4→末级 COMPLETED；5→无 ACTIVE（root RETURNED） | 与现有状态语义一致 |
| 空 operators | **不建节点**，dispatch 保持待派工（status 0） | 现状：未派工工序无 dispatch 或 status=0 |
| 一个 operator | 单节点 root，ACTIVE | dispatch 1/2 案例 |
| 重复 operator | 同一 user 出现多次：按顺序各建节点（历史如此，不合并）；同 level 内重复去重取首个 | 保守迁移 |
| 数据异常 | operators 非法 JSON / 用户不存在：跳过该 dispatch，写入迁移日志，**不中断**；报告列出 | 失败可回滚（见下） |

### 12.2 可重复执行/安全

- backfill 写成**幂等**：`INSERT ... SELECT` 前先检查 `production_dispatch_node` 是否已有该 dispatch 的节点（`NOT EXISTS`），有则跳过。
- 失败回滚：整个迁移在一个事务里；Node 表是新增表，DROP 即完全回滚，**不触碰 production_dispatch/log 任何数据**。
- 旧页面可用性：backfill 后 operators 列原样保留（双写投影未开始前不变化），旧页面读 operators 不受影响。

---

## 13. 读写切换策略

**推荐：方案 2 简化版 —— 双写投影（Node 写 + operators 同步），读优先 Node。**

| 方案 | 评估 |
|---|---|
| 1 一次性切换读写 | 风险高：旧前端/旧接口（page 数据权限、isDispatched、OperatorChain）全部依赖 operators，一次性切换必须同步改所有读点，违反"不能导致旧页面立即不可用" |
| 2 双写一段时间 | ✅ **推荐（简化版）**：写 Node 时**一个方法**同步生成 operators JSON 写入 dispatch（成本≈10 行）；读方新逻辑（currentAssignee/责任链）用 Node，旧展示（operators 列/OperatorChain）继续可用。系统小（3 条数据），双写成本可忽略 |
| 3 先 backfill 随后完全 Node | 读方立即全部切 Node，旧读点（page 数据权限 LIKE、isDispatched）必须同步改——P1 本来就计划改（见下），但 operators 展示投影仍保留给前端 |

**执行细节**：
- backfill（§12）先行 → Node 数据就绪
- 写路径：`assign/delegate/reassign/return` 统一走 Node 写入 + 调 `syncOperatorsProjection(dispatchId)` 刷新 operators JSON（保持旧字段正确）
- 读路径：`page()` 数据权限/isDispatched **改为 Node 查询**（`EXISTS (SELECT 1 FROM production_dispatch_node WHERE assignee_id=?)`），消除 LIKE JSON（P0 报告 TECH-DEBT #5 的落实）
- `DispatchVO` 增加 `nodes`/`currentAssignee` 字段（Node 树 + 当前责任人），`operators` 保留返回
- **不引入长期双写**：P2 验收后评估删除 operators 列（单独 migration，届时报告）

---

## 14. API V1 设计

基于现有 REST 风格（`/production/dispatch`，Result<T> 包装，SaCheckPermission）。

### 14.1 新/改端点

| METHOD | PATH | 权限 | 说明 |
|---|---|---|---|
| GET | `/production/dispatch/page` | list(261) | **改造**：VO 增加 nodes（责任链树）+ currentAssignee（当前责任人）；operators 保留 |
| GET | `/production/dispatch/{id}` | list(261) | **改造**：detail 含 nodes 树 |
| GET | `/production/dispatch/{id}/nodes` | list(261) | **新增**：责任链节点树（层级结构） |
| GET | `/production/dispatch/{id}/current-node` | list(261) | **新增**：当前 ACTIVE 节点（含 assignee 信息） |
| POST | `/production/dispatch/assign` | assign(262) | **改造**：仅 ASSIGN 初始派工（建 root 节点）；去掉"追加级别"分支 |
| POST | `/production/dispatch/{id}/delegate` | assign(262)+节点身份 | **新增**：DELEGATE（body: { toUserId, remark }） |
| POST | `/production/dispatch/{id}/reassign` | assign(262)+节点身份 | **新增**：REASSIGN（body: { toUserId, remark }） |
| POST | `/production/dispatch/{id}/return` | assign(262)+节点身份 | **新增**：RETURN（body: { reason }） |
| POST | `/production/dispatch/{id}/reject` | assign(262) | **保留**：= root 退回（整单退回）兼容入口 |
| POST | `/production/dispatch/batch-assign` | assign(262) | **保留**：内部改为调 ASSIGN（批量建 root） |
| POST | `/production/dispatch/{id}/start` `/complete` | start(263) | **改造**：加"最终 ACTIVE assignee 本人"节点身份校验（P1 起生效，见 §14.2） |
| GET | `/production/dispatch/my-tasks` | list(261) | **新增**：当前用户待办（ACTIVE assignee=me 的 dispatch 列表） |
| GET | `/production/dispatch/underlings/{uid}` `/my-persons` `/team-persons/{tid}` `/my-depts` | list(261) | 保留（候选人来源） |

### 14.2 权限校验位置

- 方法级：`@SaCheckPermission`（现状）
- 节点级：Service 内 `checkNodeRight(dispatchId, operatorId)` —— 操作人 = 当前 ACTIVE assignee 或超管或 hasPermission(assign)
- **不新增菜单/权限点**（0 数据变更）

### 14.3 Request/Response 示例

```jsonc
// POST /production/dispatch/{id}/delegate
{ "toUserId": 104, "remark": "组长派给工人" }

// Response (DispatchVO 扩展)
{
  "dispatchId": 3, "executionId": 3, "status": 2,
  "currentAssignee": { "nodeId": 5, "assigneeId": 104, "assigneeName": "印刷一组工人", "orgName": "印刷一组" },
  "nodes": [
    { "nodeId": 3, "assigneeName": "印刷一组组长", "status": "DELEGATED", "children": [
      { "nodeId": 5, "assigneeName": "印刷一组工人", "status": "ACTIVE", "children": [] }
    ]}
  ],
  "operators": "[{\"userId\":98,...},{\"userId\":104,...}]" // 兼容投影
}
```

---

## 15. 前端 Dispatch V1（最小改造）

基于现有 `/production/dispatch` 页面（已复核 index.vue），不推倒重写。

### 15.1 页面结构（现状保留 + 增强）

```
筛选区（保留）
主表（保留 + 2 个变化）：
  原"执行人链"列 → OperatorChain 组件升级：支持 nodes 树结构渲染，末级 ACTIVE 高亮（★）
  新增"当前待办人"列：currentAssignee（ACTIVE 节点，高亮 tag）
详情弹窗 → 增加"责任链"区：时间线式节点卡（每级：人、谁派的、何时、状态），ACTIVE 高亮
操作列 → 按"我是谁"动态渲染：
  有 assign 权限：指派（无链）/ 改派（REASSIGN）/ 下派（DELEGATE）/ 退回（RETURN）/ 批量
  是 ACTIVE assignee：下派（DELEGATE 给手下）/ 改派（REASSIGN）/ 退回（RETURN）/ 开始 / 完成
```

### 15.2 弹窗变化

| 弹窗 | 变化 |
|---|---|
| 指派（ASSIGN） | 保留（选第 1 级负责人+设备+备注），去掉"链完整性"开关（链完整=有 ACTIVE 节点，概念由节点取代） |
| 下派（DELEGATE） | 新弹窗：选 ACTIVE 人的手下（underlings），备注 |
| 改派（REASSIGN） | 现有"改派"改语义：同级换人（选人，展示当前 ACTIVE 及链上下文） |
| 退回（RETURN） | 现有"退回"改语义：退回上一级（原因必填）；root 退回提示"将整单退回" |
| 批量派工 | 保留（调 batch-assign） |
| 流水 | 保留 + 新 action 标签（DELEGATE/RETURN） |

### 15.3 OperatorChain 复用评估

✅ **可改造复用**：`OperatorChain/index.vue` 目前解析 `operators` JSON（`chain` computed + `JSON.parse`）。改造方案：增加 `nodes?: NodeItem[]` prop 优先渲染（层级树/箭头链），`operators` 保留兜底（兼容旧数据/旧调用）。改动集中在 computed + 模板，组件接口向后兼容。

### 15.4 明确不做（用户约束）

甘特图 / APS / 拖拽排程 / 产能算法 / 数量拆分 UI / 复杂组织调度台。

---

## 16. 与 Execution 的集成

**P1 最小方案：只提供 currentAssignee projection，不做严格执行权限联动。**

- execution 页面/VO 增加 `currentAssigneeName`（查 dispatch 的 ACTIVE 节点）——让执行页知道"当前谁负责这个工序"，仅展示。
- **不限制**开始/暂停/完成的操作人（现状 execution 模块无执行人校验，`canStartExecution` 只看状态）——强联动会破坏现有流程（execution 页面已能直接操作），且 P1 无 WorkReport 支撑"必须由执行人报工"。
- `start/complete` 的 dispatch 侧**保留现状联动**（syncByExecution），但 P1 建议 dispatch 的 start/complete 接口加"最终 ACTIVE assignee 本人"校验（与 execution 模块直接操作互不影响，两入口并存）。
- P2 WorkReport 时再做严格执行权限联动（报工=执行人本人），P1 只埋 projection。

---

## 17. Dispatch Log 与 Node 的关系

| 维度 | DispatchNode（结构） | DispatchLog（审计） |
|---|---|---|
| 表达 | 责任链结构：谁传给谁（张三→李四） | 动作轨迹：谁在何时执行了什么动作（张三 12:30 DELEGATE 给李四） |
| 变更 | DELEGATE/REASSIGN/RETURN 都会产生节点状态变化 | 每个动作一行记录 |
| 查询 | 责任链展示（树） | 时间线展示（流水） |

**结论**：Node 建立后 dispatch_log 仍有价值（动作级审计：操作人/时间/原因/内容），**两者并存、职责分离**。

**现有 dispatch_log 复用**：
- 表结构不动（action VARCHAR(20) 够用）
- action 新增 `DELEGATE`、`RETURN`（现有 ASSIGN/REASSIGN/REJECT/START/COMPLETE 保留）
- content 按动作写（下派：`XX 下派给 YY`；退回：`退回原因`；改派：`第N级 XX 改派为 YY`）
- 前端 ACTION_LABELS 增加两个标签

---

## 18. 并发与幂等

### 18.1 场景分析

| 场景 | 保护 |
|---|---|
| 重复 ASSIGN | `uk_execution`（execution_id UNIQUE）已挡；Service 再查一次 |
| 重复 DELEGATE（双击） | 第一个成功 → ACTIVE 已变 DELEGATED；第二个条件 UPDATE `WHERE node_status='ACTIVE'` 影响 0 行 → 抛"已在流转中" |
| 两人同时 DELEGATE/REASSIGN/RETURN | `SELECT ... FOR UPDATE` dispatch 行锁 → 串行化；后到者条件更新失败 |
| 已 RETURN 节点再操作 | 节点状态非 ACTIVE → 拒绝（校验当前 ACTIVE 节点） |
| 已完成 execution 再派工 | execution_status=COMPLETED → 拒绝（assign 前校验 execution 状态） |
| 取消订单后继续派工 | execution_status=CANCELLED → 拒绝 |

### 18.2 事务边界

```
@Transactional(rollbackFor = Exception.class)
delegate(dispatchId, toUserId, ...):
  1. dispatch = SELECT ... FOR UPDATE（锁容器行）
  2. active = SELECT node WHERE dispatch_id=? AND node_status='ACTIVE'
  3. 校验：active != null；操作人权限；toUserId ∈ underlings(active.assignee)
  4. UPDATE node SET node_status='DELEGATED', closed_at=now WHERE node_id=active.id AND node_status='ACTIVE'
     （影响行数=0 → 抛并发冲突）
  5. INSERT 新节点 (parent=active.id, assignee=toUserId, ACTIVE)
  6. 双写 operators 投影 + dispatch.assign_time 更新
  7. INSERT dispatch_log(DELEGATE)
```

### 18.3 约束/索引汇总

- **函数索引唯一约束 `uk_dispatch_active`**（DB 级唯一 ACTIVE，§5.3）——防并发兜底
- `idx_dispatch`（dispatch_id）——节点按 dispatch 查询
- `idx_assignee_status`（assignee_id, node_status）——my-tasks 待办查询
- 事务：Spring `@Transactional`；锁：`SELECT ... FOR UPDATE`（dispatch 行）
- **不用分布式锁**（单体应用，用户约束）

---

## 19. 数据库 Migration（设计，未执行）

### 19.1 Migration 文件

`jjx-server/sql/migrations/V20260819_001__dispatch_node.sql`（遵循现有 V 命名规范）

### 19.2 内容

```sql
-- 1. 建表 production_dispatch_node（§5.1 DDL）
-- 2. 索引：uk_dispatch_active（函数索引唯一）、idx_dispatch、idx_assignee_status
-- 3. backfill：INSERT INTO production_dispatch_node ... （从 production_dispatch.operators 解析，幂等 NOT EXISTS）
-- 4. 不修改 production_dispatch 表结构；operators/status 原样保留
```

### 19.3 外键策略

**不加物理外键**（现状 production_dispatch/log 均无 FK，全逻辑关联；加 FK 会阻碍未来重构/删除）。用索引 + Service 校验。

### 19.4 backfill 实现（MySQL 8.4 JSON_TABLE）

```sql
-- 思路：JSON_TABLE 展开 operators → 按 dispatch 分组、数组序 → 生成链
INSERT INTO production_dispatch_node (dispatch_id, parent_node_id, assignee_type, assignee_id, assignee_name,
    org_id, org_name, org_path, node_status, assigned_by, assigned_by_name, assigned_at, remark)
SELECT d.dispatch_id, NULL, 'USER', jt.userId, jt.userName, ... , 'ACTIVE', d.assigned_by, d.assigned_by_name, d.assign_time, '迁移'
FROM production_dispatch d
JOIN JSON_TABLE(d.operators, '$[*]' COLUMNS (
    userId BIGINT PATH '$.userId',
    userName VARCHAR(64) PATH '$.userName',
    level INT PATH '$.level'
)) jt
WHERE d.operators IS NOT NULL AND d.operators != ''
  AND NOT EXISTS (SELECT 1 FROM production_dispatch_node n WHERE n.dispatch_id = d.dispatch_id);
-- parent_node_id 需要在应用层/存储过程里逐行回填（JSON_TABLE 无法直接引用同批自增 ID），
-- 建议：迁移脚本用存储过程逐 dispatch 处理（循环：INSERT → 记录 last_insert_id → 下一条 parent=上一条），
-- 或应用层一次性脚本（推荐，逻辑清晰可测）
```

> 说明：parent_node_id 回填因依赖自增 ID 链，**推荐应用层迁移脚本**（Java/Python 一次性任务，逐 dispatch 建链），SQL 脚本负责建表+索引+校验。报告中给两种方式，实施时选应用层脚本（可测、可日志）。

### 19.5 rollback

```sql
DROP TABLE production_dispatch_node;  -- Node 表是纯新增，生产数据零影响
```

回滚后：operators 投影仍正确（双写期间同步维护，或回滚时用旧数据）；dispatch/log 未动。**可完全回滚**。

---

## 20. 测试计划

基于现有测试体系（JUnit5 + Mockito，`src/test/java/com/jjx/{sales,inventory}` 先例；注意 **JDK25 下 Mockito 无法 mock JdbcTemplate**（P0 已验证），JdbcTemplate 相关用真实查询或最小验证）。

| 测试 | 覆盖 | 方式 |
|---|---|---|
| NodeTreeTest | 节点树构建/遍历（parent 链→树） | 纯 Java（Node 树组装工具类） |
| UniqueActiveTest | 同一 dispatch 两个 ACTIVE 被 DB 拒绝 | 真实 MySQL 集成测试（可选，P0 经验：Mockito 限制多）或函数索引语义单测 + 文档说明 |
| AssignTest | ASSIGN：建 root ACTIVE、权限、execution 校验、uk_execution 幂等 | Mockito（mock mapper） |
| DelegateTest | DELEGATE：旧→DELEGATED、新 ACTIVE、目标手下校验、非 assignee 拒绝、并发条件更新失败 | Mockito |
| ReassignTest | REASSIGN：旧→REASSIGNED、新节点 parent=旧.parent（同级）、re_dispatch_count | Mockito |
| ReturnTest | RETURN：旧→RETURNED、parent 重新 ACTIVE、root 退回=整单 | Mockito |
| PermissionTest | 节点身份判定（本人/超管/有权限代操作/无关人拒绝） | Mockito + mockStatic(SecurityUtils) |
| MigrationBackfillTest | operators JSON→节点：空/单/多级/同 level 多人/非法 JSON/用户缺失 | 纯 Java 迁移函数单测（推荐应用层迁移脚本的原因） |
| CompatTest | 旧接口 operators 投影仍返回、page 数据权限改 Node 查询 | Mockito |
| ApiTest | Controller 层新端点（可省，成本高，P0 先例未做 controller 测试） | 可选 |

**执行范围**：`mvn compile` + 上述新增测试；前端 `vue-tsc --noEmit`。全量回归（sales/inventory）与本轮无交集，按 P0 惯例不强制。

---

## 21. 文件影响清单

### 后端（新增）
| 文件 | 内容 |
|---|---|
| `domain/entity/ProductionDispatchNode.java` | 节点实体 |
| `domain/vo/DispatchNodeVO.java` | 节点树 VO |
| `domain/dto/DispatchDelegateDTO.java` / `DispatchReassignDTO.java` / `DispatchReturnDTO.java` | 动作入参 |
| `enums/DispatchNodeStatusEnum.java` | ACTIVE/DELEGATED/REASSIGNED/RETURNED/COMPLETED/CANCELLED |
| `mapper/ProductionDispatchNodeMapper.java` | BaseMapper |
| `sql/migrations/V20260819_001__dispatch_node.sql` | 建表+索引 |
| 应用层迁移脚本（如 `sql/migrations/backfill_dispatch_node.sql` 或工具类） | backfill |

### 后端（修改）
| 文件 | 修改点 |
|---|---|
| `DispatchServiceImpl.java` | assign 拆 ASSIGN；新增 delegate/reassign/return；Node 读写；isDispatched/page 数据权限改 Node 查询；operators 双写投影；start/complete 加节点身份校验 |
| `DispatchService.java` | 接口新增 3 动作 + my-tasks |
| `DispatchController.java` | 新增 4 端点（nodes/current-node/delegate/reassign/return/my-tasks） |
| `DispatchVO.java` | + nodes/currentAssignee |
| `ProductionOperationExecutionServiceImpl.java` | VO 带 currentAssigneeName |
| `ProductionOperationExecutionVO.java` | + currentAssigneeName |

### 前端（修改）
| 文件 | 修改点 |
|---|---|
| `views/production/dispatch/index.vue` | 列表当前待办人列、责任链弹窗、4 动作弹窗、按钮按身份渲染 |
| `components/OperatorChain/index.vue` | 支持 nodes 树 prop（operators 兜底） |
| `api/production/dispatch.ts` | 新端点封装 + VO 类型扩展 |

---

## 22. 风险

| 风险 | 等级 | 缓解 |
|---|---|---|
| 迁移破坏旧页面 | 中 | operators 双写投影，旧读点不动；backfill 幂等可回滚 |
| 函数索引唯一约束理解偏差 | 低 | MySQL 8.4 实测支持；先建表验证再实施 |
| isDispatched 语义变化（LIKE→Node exists） | 低 | 查询结果等价（"是否在任一链上"→"是否有任一节点"）；P0 已确认 isDispatched 保留待 P1 换 |
| dispatch 3 同 level 两人迁移语义 | 低 | 串链（组长→工人）+ remark 标注；演示数据，可人工核对 |
| start/complete 加节点身份校验影响现有操作 | 中 | 仅 dispatch 侧接口加校验；execution 模块直接操作不受影响；兼容期可配置开关（默认开） |
| 角色 28/30/32 权限缺口暴露 | 低 | 现状已存在，非 P1 引入；报告记录，P2 权限方案处理 |
| Node 表查询性能 | 低 | 数据量小（演示 3 条）；idx_dispatch/idx_assignee_status 已建 |

---

## 23. 回滚方案

1. **数据库**：`DROP TABLE production_dispatch_node`（Node 纯新增，dispatch/log/operators 未动）→ 完全回滚
2. **后端代码**：git revert（P1 单独分支/提交）；或保留旧 jar 重启
3. **前端代码**：git revert；旧前端与旧接口天然兼容（operators 仍在）
4. **顺序**：先回滚前端（无 Node 依赖）→ 再回滚后端 → 最后 DROP 表；或整体 revert + DROP

---

## 24. P1 验收标准

1. ✅ 责任链结构化：`production_dispatch_node` 表存在，3 条历史 dispatch 迁移为节点链（可查）
2. ✅ 动态多级：能派 4 级链（生产中心→车间→班组→工人），无 1-3 硬编码
3. ✅ 唯一 ACTIVE：同一 dispatch 数据库层不可能两个 ACTIVE（函数索引验证）
4. ✅ 四动作：ASSIGN/DELEGATE/REASSIGN/RETURN 语义互斥、各自校验、各自记流水
5. ✅ 当前责任人：列表/详情/execution 页均显示 currentAssignee（ACTIVE 节点）
6. ✅ 权限：初始派工=assign 权限；继续派工=ACTIVE 节点身份（本人/超管/有权限代操作）
7. ✅ 兼容：旧页面（dispatch 列表/OperatorChain/流水）不炸；operators 双写正确
8. ✅ 测试：新增测试全绿，`mvn compile` + `vue-tsc` 通过
9. ✅ 回滚：DROP node 表即完全回滚，生产数据零破坏
10. ✅ 明确不做的：无 WorkReport/数量拆分/多人并行/质量绑定/Trace（范围外）

---

## 25. 推荐实施 Work Package

| WP | 内容 | 依赖 | 验收 |
|---|---|---|---|
| **WP1 数据层** | 建表 migration + 函数索引 + 应用层 backfill 脚本 + Node 实体/枚举/Mapper | 无 | 表结构评审通过；3 条 dispatch 迁移正确 |
| **WP2 服务层** | DispatchServiceImpl 重构：ASSIGN 拆分 + delegate/reassign/return + Node 读写 + isDispatched/page 改 Node + operators 双写 + start/complete 身份校验 | WP1 | 4 动作单测通过；并发条件更新生效 |
| **WP3 接口层** | Controller 新端点 + DispatchVO 扩展 + my-tasks + API 测试 | WP2 | 接口文档/测试通过 |
| **WP4 前端** | dispatch 页列表/详情/4 动作弹窗 + OperatorChain nodes 支持 + execution 页 currentAssignee 展示 | WP2/WP3 | 页面全流程手测（指派→下派→改派→退回→开始→完成） |
| **WP5 回归收尾** | 全链路验证 + 兼容验证 + 报告更新 | WP1-4 | 验收标准 §24 全绿 |

**建议顺序**：WP1 → WP2 → WP3 → WP4 → WP5，每 WP 独立提交（用户自行提交），每步可回滚。

---

## 附录 A：关键现状依据索引（复核时点 2026-08-19）

| 依据 | 位置 |
|---|---|
| dispatch 表结构 | `mysql SHOW CREATE TABLE production_dispatch`（uk_execution 唯一键） |
| dispatch_log 表结构 | `SHOW CREATE TABLE production_dispatch_log` |
| DispatchStatusEnum 六态 | `jjx-server/.../production/enums/DispatchStatusEnum.java` |
| 三级硬编码 | `DispatchServiceImpl.java` appendLevel `lv<1||lv>3` |
| operators 全部读写点 | §2.2 清单（Service 4 处 + VO 1 处 + SQL 2 处 + 前端 2 处） |
| isDispatched LIKE | `DispatchServiceImpl.java:581-590` |
| page 数据权限 LIKE | `DispatchServiceImpl.java:95-101` |
| reject 整单退回 | `DispatchServiceImpl.java` reject() |
| syncByExecution | `DispatchServiceImpl.java:719` + `ProductionOperationExecutionServiceImpl.java:286/439` |
| 权限数据 | `sys_menu` 261/262/263 → `sys_role_menu` 角色 1/29/30/31；28/32 缺失 |
| 组织数据 | `sys_dept` 1/5/6/7/9（leader 存用户名）；`sys_user` 94-109 |
| sys_dept 无 ancestors 列 | `SHOW COLUMNS FROM sys_dept`；`SysDept.ancestors` 为 `@TableField(exist=false)` |
| 现存 dispatch 数据 | 3 条（1/2/3），含 dispatch 3 同 level 两人案例 |
| MySQL 版本 | 8.4.10（函数索引可用） |
| migration 规范 | `jjx-server/sql/migrations/V20260819_001__*.sql`（手动执行，无 Flyway） |
| v2 设计稿 | `jjx-docs/specs/dispatch-redesign-v2.md`（205 行，作设计资料，部分决策被本报告采纳/修正） |

## 附录 B：与 v2 设计稿的差异说明

| 点 | v2 稿 | 本报告 | 原因 |
|---|---|---|---|
| 退回语义 | 退回=激活 parent | 同（方案 A）✅ 采纳 | 复杂度低、审计充分 |
| 旧数据 | "演示数据直接清空重建" | **写迁移脚本**（幂等可回滚） | 用户 P1 明确要求迁移设计；3 条数据成本可忽略，且保留可比对 |
| 链深度 | 不封顶 | 同 ✅ | 组织树驱动 |
| 工单级责任字段 | "砍掉" | **保留暂不动** | P1 范围收敛：不动 production_order；P2 评估 |
| TEAM_ASSIGNED(1) | 砍掉 | **保留兼容**（不再产生） | 渐进迁移，避免旧数据/旧页面破坏 |
| 改派 | 同级换人 | 同（关旧建新）✅ | 历史不可变 |
| 批量派工 | 保留 | 保留（内部改调 ASSIGN）✅ | 源头主管快捷入口有价值 |

---

*报告完。本轮只读，未改代码/未改数据库/未执行 migration/未提交 Git。等待人工评审。*
