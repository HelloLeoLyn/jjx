# JJX Production Assignment & Responsibility Domain Design

> 版本：v1.0 ｜ 日期：2026-08-20 ｜ WP-A 只读设计 ｜ 未改代码/库/Git ｜ 未实现 Assignment

---

## 1. 当前真实模型复核（代码+库确认）

| 对象 | 当前事实 |
|---|---|
| Execution 计划数量 | `input_quantity`（-03 三道=450）；`qualified/defective` 由 WorkReport Projection 汇总（P2-C，禁止直接改） |
| ACTIVE DispatchNode | 唯一约束 `uk_dispatch_active (dispatch_id, active_guard)`（generated 列，ACTIVE→1 其余 NULL）——**同一 dispatch 同一时刻仅一个 ACTIVE** ✅ |
| WorkReport | 绑定 `executionId / dispatchId / dispatchNodeId / reporterId/Name`；**无 assignment_id** |
| canReport | `work-report:add` 权限 + 当前用户 == ACTIVE Node assignee（P2 V1 不允许代报，超管也不默认放行） |
| completeExecution | gate：状态允许 + **至少 1 条 SUBMITTED WorkReport**（柔性：不强制数量达标）+ 完工冻结（工单完成后禁报工） |
| 我的任务 scope=mine | 过滤条件 = canReport 或 currentAssigneeId==我（即 **ACTIVE Node assignee 视角**，非"我分配了多少"） |
| WorkReport 数量校验 | 负数拒绝；qualified+defective 必须 >0；**无"累计超 assigned"校验**（因为当前无 assigned 概念） |
| 报工权限链 | submit：execution 状态(执行中/暂停) + dispatch 存在 + ACTIVE node 存在 + 本人是 ACTIVE assignee |
| 派工页操作项 | 初始派工/继续派工(DELEGATE)/改派/退回上级/拒绝派工(REJECT)/开始/完成/责任链/流水 |
| 执行页操作项 | 开始/暂停/报工/详情/完成/首检巡检(旧双轨)/质检记录 |
| allowedActions | 后端 buildAllowedActions 计算（cur==null→ASSIGN；有 ACTIVE→DELEGATE/REASSIGN/RETURN 按身份），前端只消费不猜 |
| 责任人改派权限 | 当前实现：超管/有 assign 权限/ACTIVE assignee 本人均可 REASSIGN（旧业务保留） |

**关键结论**：当前模型是"一人负责一道工序"（ACTIVE node 唯一 assignee + 本人报工）。要支持"按数量拆分给多人"，**不能改 ACTIVE node 唯一约束**（责任链语义），必须新增 Assignment 层承载"谁做多少"。

---

## 2. 四个概念正式定义

```
Execution（工序整体生产任务）
  计划数量 = input_quantity（例：贴膜 1000）
  └─ Dispatch（1:1 execution，容器，历史兼容）
      └─ DispatchNode（责任链节点，同一时刻仅一个 ACTIVE）
          当前 ACTIVE 节点 = 当前对这道工序负管理责任的人
          ├─ 创建 ExecutionAssignment（把作业数量分给人）
          └─ 责任动作：下派/改派/退回（P1 语义保留）
  ├─ ExecutionAssignment（作业份额）
  │    某执行人（assignee）被分配多少作业数量（例：张三 300）
  │    ACTIVE 多个并存（张三300/李四300/王五200）
  └─ WorkReport（报工事实）
        执行人针对某个 Assignment 实际报了多少（qualified/defective/labor/machine）
```

**职责边界（拍板确认）**：
- Dispatch/DispatchNode = 责任链（谁负责这道工序）
- ExecutionAssignment = 具体谁做多少（数量拆分）
- WorkReport = 实际报了多少（事实）
- **Assignment 不是第二套 DispatchNode**：它不表达"责任转移"，只表达"数量份额"

---

## 3. Assignment 最小数据模型

| 字段 | 类型 | 用途 | 必选 |
|---|---|---|---|
| assignment_id | BIGINT PK | 主键 | ✅ |
| execution_id | BIGINT NOT NULL | 属于哪道工序 | ✅ |
| order_id | BIGINT NOT NULL | 冗余：工单维度查询/过滤（CANCELLED 排除） | ✅ |
| dispatch_id | BIGINT 可空 | 关联派工容器（当前 1:1 execution；保留以便统计） | ⭕ 建议保留 |
| dispatch_node_id | BIGINT 可空 | **记录"这份作业由哪个责任节点分配"**（分配时 ACTIVE node 快照） | ✅ 保留 |
| assignee_id | BIGINT NOT NULL | 执行人 | ✅ |
| assignee_name | VARCHAR 快照 | 历史姓名不漂移 | ✅ |
| assigned_quantity | DECIMAL(18,4) | 分配作业数量 | ✅ |
| assignment_status | VARCHAR(20) | ACTIVE/COMPLETED/CANCELLED | ✅ |
| assigned_by | BIGINT | 分配人（创建者） | ✅ |
| assigned_by_name | VARCHAR 快照 | 分配人姓名 | ✅ |
| assigned_at | DATETIME | 分配时间（业务时间） | ✅ |
| cancelled_by / cancelled_at / cancel_reason | - | 取消/释放时记录 | ⭕ 可空 |
| create_time / update_time | DATETIME | 审计 | ✅ |

**dispatch_node_id 保留理由**：分配动作必然发生在某个责任节点下（当前 ACTIVE node），记录它能回答"谁授权的这份作业"，且与 WorkReport.dispatchNodeId 对齐（报工同样挂 node）。不加物理 FK（与 P1/P2/P3 一致，不加 FK migration）。

---

## 4. Assignment 状态机（3 态，无过度设计）

```
ACTIVE → COMPLETED（数量事实推导，非人工点按钮）
ACTIVE → CANCELLED（释放剩余：负责人操作，记录 reason）
```

- **无 PARTIAL**：部分完成 = `reported < assigned` 数量表达，不建 Enum
- **无 RELEASED**：释放 = CANCELLED + 剩余回到 unassigned pool（见 §6）
- **COMPLETED 自动推导**：`SUM(有效 WorkReport.output) >= assigned_quantity` → 标记 COMPLETED（由查询投影或写时更新均可，推荐**查询时推导 + 惰性写回**）

---

## 5. 数量规则（核心）

```
execution.input_quantity = 1000（计划）
assigned_quantity   = SUM(ACTIVE assignment.assignedQuantity)   // 已分配
unassigned_quantity = input_quantity - assigned_quantity         // 未分配
reported_quantity   = SUM(有效 WorkReport.output)               // 已报（跨 assignment 累计到 execution）
```

**部分分配**：允许一次只分 600 → unassigned=400 → 后续再分 400（不要求一次分完）。创建 Assignment 时校验 `assigned_quantity <= unassigned_quantity`。

---

## 6. 超分与释放/重新分配

**超分：默认禁止**
```
创建/增加 Assignment 时：SUM(ACTIVE assigned) + 新增 <= input_quantity
```
**补产/返工场景**：不通过"无约束超分"实现。推荐：补产 = 新建返工 Execution（或调整 Execution.input_quantity，走订单变更流程）——V1 不开放 Assignment 超分。

**释放剩余（关键动作）**：不 UPDATE 已报工 Assignment 的数量（防历史失真），用正式动作：
```
releaseRemaining(assignmentId, reason)
  → 校验：assignment ACTIVE
  → 新开一条 CANCELLED（或直接标记该 assignment CANCELLED + 记录 cancel_reason）
  → 剩余量（assigned - reported）回到 unassigned pool
  → 原 assignment 的 WorkReport 历史保留
```
**重新分配**：释放后 unassigned += 剩余 → 给李四新建 Assignment（新行，assigned=120）。

**推荐模型**：Assignment 行**不可变数量**（创建后 assigned_quantity 永不变），状态只 ACTIVE→CANCELLED/COMPLETED。任何数量调整 = 取消旧行 + 新建行。简单、防审计混乱。

---

## 7. WorkReport 改造

- 新增 `assignment_id BIGINT NULL`（V1 新链路必填，历史 NULL 兼容）
- **历史兼容**：旧 WorkReport assignment_id=NULL 保留；查询/投影按"有 assignment 按 assignment 算，无则按 execution 聚合（旧语义）"
- **不伪造**：不给旧记录补 assignment
- 报工入口：新 UI 必须选 Assignment（后端校验 assignment 存在且 ACTIVE、assignee=当前用户）

---

## 8. 报工权限与数量规则

**报工权限（新规则）**：
```
可报工 = work-report:add 权限
      + 存在当前用户的有效 ACTIVE Assignment（assignee_id = 我）
      + Execution 状态允许（执行中/暂停）
      + 该 Assignment 未 CANCELLED/COMPLETED
```
- **责任人本人无 Assignment 不能报工**（即使是 ACTIVE node assignee）——责任人不等于生产执行人
- 责任人也生产 → 给自己建 Assignment（允许）
- **旧规则兼容**：历史 execution 无 Assignment 时，回退旧 canReport（ACTIVE assignee 本人可报）——过渡期

**报工数量 vs Assignment**：
- **默认禁止累计报工 > assigned_quantity**（超 = 业务异常）
- **qualified + defective 都占用 Assignment 数量**：
  ```
  assigned=300；报 qualified 280 + defective 20 → 累计 300 → Assignment COMPLETED
  ```
  （产出 = qualified+defective，与 P2 的 output=qualified+defective 语义一致）

---

## 9. Assignment 完成

- **推荐：数量事实自动推导**——`SUM(有效 WorkReport.output) >= assigned_quantity → COMPLETED`，不提供"完成 Assignment"按钮
- **撤销报工回退**：WorkReport 撤销后，Assignment 累计减少；若曾 COMPLETED 但撤销后 < assigned → **状态回退 ACTIVE**（派生状态，查询时计算；不存状态字段或惰性写回）

---

## 10. Execution 完成条件（最小 gate）

```
completeExecution gate（V1 建议）：
  ① 状态允许
  ② 至少 1 条 SUBMITTED WorkReport（保留 P2 柔性基础）
  ③ 所有 ACTIVE Assignment 的 remaining <= 0（即每个份额都已报完）——或全部 CANCELLED
  ④ unassigned_quantity == 0（计划全部分配完）
```
- **低于计划完成**：保留柔性（P2 现状），但需**当前 ACTIVE 责任人确认**（在完成动作上加二次确认/备注），不彻底丢掉
- 若工厂要求严格：可配置 gate 级别（V1 用推荐柔性规则，不做配置化）

---

## 11. Dispatch 责任链动作收口

| 动作 | UI 名 | 规则（V1 定稿） |
|---|---|---|
| ASSIGN | 初始派工 | 无 ACTIVE 责任人 + production:dispatch:assign 权限 |
| DELEGATE | **下派**（原名"继续派工"） | 必须当前 ACTIVE assignee 本人/超管 + delegate 权限，目标 = 其手下 |
| REASSIGN | 改派 | **当前责任人本人不能自改派**；由超管或有 assign 权限的上层执行（修复现"本人可改派"旧规则） |
| RETURN | 退回上级 | 必须当前 ACTIVE assignee 本人/超管 + 有上级节点 |
| （COMPLETED Execution） | - | 责任链冻结只读，不再提供任何动作 |

**派工页职责收口**：派工页移除"开始/完成/拒绝派工"（这些属执行页/整单退回），派工页 = 初始派工/下派/改派/退回/分配作业/责任链。旧 start/complete/reject API 保留兼容（执行页仍可能用 start；reject 由 RETURN 替代语义），前端逐步隐藏。

---

## 12. allowedActions 统一设计（后端 Projection，前端不猜）

```
allowedActions = f(状态, RBAC permission, 责任链身份, 工序责任域, Assignment 身份)

示例（责任链动作）：
  ASSIGN    = 无 ACTIVE && (super || dispatch:assign)
  DELEGATE  = 有 ACTIVE && (本人 || super || dispatch:assign) && 目标域允许
  REASSIGN  = 有 ACTIVE && (super || dispatch:assign) && 非本人（本人禁自改派）
  RETURN    = 有 ACTIVE && parent 存在 && (本人 || super)
  ASSIGN_WORK = 有 ACTIVE && (本人 || super || dispatch:assign)   // 分配作业按钮

示例（Assignment 相关，执行页）：
  REPORT    = work-report:add && 我有 ACTIVE assignment && execution 状态允许
  RELEASE   = 我有 ACTIVE assignment && 有 work-report:cancel/assignment 权限
```

**修复人工验收发现**：
- "冲型组长能操作 PRINT" → 增加**工序责任域**（见 §13）
- "prod_manager 能看到 START/REJECT" → START 从派工页移除；REJECT 仅整单退回入口
- "已完成工序还能继续派工" → Execution COMPLETED → 责任链冻结（allowedActions 全空）
- "非当前责任人仍看到写按钮" → 身份校验统一走 ACTIVE node + assignment

---

## 13. 组织/工序操作范围（最小可落地方案）

**现状**：无"工序属于哪个车间"的可靠字段（engineering_routing_item 无 workshop 字段；execution.major_category 仅类别）。部门树（sys_dept）+ 人员上下级存在。

**最小可落地方案（V1）**：
- 可见范围：生产相关角色可看全部生产工单工序（现状）——**可见 ≠ 可操作**
- 可操作范围：**基于 ACTIVE node 责任链身份**（本人/上层/授权）+ **RBAC 权限点**，不引入"工序-车间归属"新模型
- 工序责任域：若需要"冲型组长不能动 PRINT"，V1 用 `execution.major_category` 与班组部门映射做**软约束提示**（前端置灰），不做硬权限——**待 P 系列后续有可靠字段再加**（本轮明确：不伪造复杂权限模型）
- **明确拍板项**：V1 可操作边界 = 责任链身份 + RBAC，责任域为后续增强

---

## 14. 人员选择器

- **责任链动作**（初始派工/下派/改派）：**单选责任人**（现状已单选，保持）
- **Assignment 分配**：多人 + 数量：
  ```
  ┌──────────┬───────────┐
  │ 张三     │ [300]     │
  │ 李四     │ [300]     │
  │ 王五     │ [200]     │
  │ ＋ 添加执行人          │
  └──────────┴───────────┘
  合计 800 / 剩余可分配 200
  ```
  不是单纯勾选多人 checkbox。

---

## 15. 我的任务（三视图）

| 视图 | 数据源 |
|---|---|
| 全部任务 | execution 全量（WORK_ORDER 非 CANCELLED） |
| 我的当前任务（操作员） | **基于 Assignment**：`execution JOIN assignment WHERE assignee_id=我 AND status=ACTIVE`；展示"我的分配/已报/剩余" |
| 我的当前任务（责任人/管理者） | ACTIVE node assignee=我（负责视图：可下派/分配作业）+ 我的 assignment（生产视图）——双视角 |
| 我已完成 | 有 assignment 且 `SUM(WorkReport.output) >= assigned` 的 execution（派生）；或 WorkReport 历史归属我的 execution |

**最小可靠模型**：操作员"我的任务" = 我的 ACTIVE Assignment 集合（+execution 状态）；"我已完成" = 我的 assignment 全部 COMPLETED 的 execution（派生查询，不存状态）。

---

## 16. Quality / Trace 影响（只分析）

- **Quality 不绑定 Assignment**：`Quality → WorkReport → Assignment`（质检单挂 execution，通过 WorkReport 间接关联 assignment）。理由：FQC 是工序级/订单级判定，不按人拆；IPQC 同理。保持 P3 不变。
- **Trace**：现有 TraceQueryService 聚合 Order/Execution/DispatchLog/WorkReport/Quality——Assignment 事件（创建/释放）**可作为新事件源**（复用 DispatchLog 模式：新建 assignment_log 或复用 dispatch_log？**推荐不建 Trace 新表**，Assignment 变化写入 `production_assignment` 行本身（create_time/assigned_at/cancelled_at 即事件时间），Trace 从 Assignment 表投影新事件类型（ASSIGNMENT_CREATED/ASSIGNMENT_CANCELLED）——P4 Trace 只读投影扩展，不动事实表。

---

## 17. 数据库迁移策略

```sql
-- V20260820_001__production_assignment.sql（WP-B 实施）
CREATE TABLE production_execution_assignment (
  assignment_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  execution_id        BIGINT NOT NULL,
  order_id            BIGINT NOT NULL,
  dispatch_id         BIGINT NULL,
  dispatch_node_id    BIGINT NULL,
  assignee_id         BIGINT NOT NULL,
  assignee_name       VARCHAR(64) NOT NULL,
  assigned_quantity   DECIMAL(18,4) NOT NULL,
  assignment_status   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  assigned_by         BIGINT NULL,
  assigned_by_name    VARCHAR(64) NULL,
  assigned_at         DATETIME NULL,
  cancelled_by        BIGINT NULL,
  cancelled_at        DATETIME NULL,
  cancel_reason       VARCHAR(500) NULL,
  create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_execution (execution_id),
  KEY idx_assignee_status (assignee_id, assignment_status),
  KEY idx_order (order_id)
) COMMENT='工序作业分配（谁做多少）';

-- WorkReport 增加 assignment_id（可空，历史兼容）
ALTER TABLE production_work_report
  ADD COLUMN assignment_id BIGINT NULL COMMENT '关联作业分配(新链路必填,历史NULL)' AFTER dispatch_node_id,
  ADD KEY idx_assignment (assignment_id);
```
- 不加物理 FK（与 P1/P2/P3 一致）
- **历史数据**：旧 WorkReport assignment_id=NULL 保留，不 backfill
- **测试数据建议**：验收环境保留 -03（其 3 道 execution 无 assignment 属正常过渡）；E2E 新建一条干净 PLAN 走全链

---

## 18. 前端草图（文字说明）

1. **派工页"分配作业"Drawer**（新按钮，仅 allowedActions 含 ASSIGN_WORK 时显示）：
   顶部：工序计划数量 / 已分配 / 剩余可分配；中间：执行人+数量行（可增删）；底部：合计校验 + 提交
2. **工序执行"我的分配数量"卡片**（操作员视角）：我的分配 300 / 已报 250 / 剩余 50 / 本次报工
3. **报工 Drawer 增强**：工序计划 450 / 我的分配 300 / 已报 250 / 剩余 50 / 本次合格+不良（不良原因必填）
4. **已完成任务页**：我的 assignment 全部 COMPLETED 的 execution 列表（派生查询）

---

## 19. Work Package 建议

```
WP-B Assignment Backend      （表 + Service：创建/释放/查询/数量校验 + WorkReport.assignment_id + 报工规则改造）
WP-C Responsibility & Perm   （责任链动作收口：REASSIGN 禁自改派/下派改名/派工页职责收口/allowedActions 扩展/责任域软约束）
WP-D Assignment Frontend     （分配作业 Drawer + 执行页我的分配 + 报工 Drawer 增强 + 我的任务三视图）
WP-E Final E2E               （干净 PLAN 全链人工验收）
```
**B/C 顺序理由**：先建数据与报工规则（B），再收口责任链权限（C）——因为 allowedActions 依赖 Assignment 状态；C 中"派工页移除开始/完成"依赖 B 的分配入口就绪。D/E 依赖前两者。不拆分更多。

---

## 20. 需要人工拍板的关键决策

1. **Assignment 行不可变数量**（调整=取消旧行+新建行）——推荐 ✅
2. **释放剩余 = CANCELLED 动作**（不 UPDATE 已报工行）——推荐 ✅
3. **报工超 assigned 默认禁止**；补产走返工 Execution——推荐 ✅
4. **Execution 完成 gate**：保留柔性（至少 1 条报工 + 全部 assignment 完成/unassigned=0），低于计划由 ACTIVE 责任人确认——推荐 ✅
5. **REASSIGN 禁本人自改派**（修复旧规则）——推荐 ✅（行为变化，需确认）
6. **报工权限改为"有有效 Assignment"**（责任人无 assignment 不能报工），旧无 assignment 数据过渡期回退旧规则——推荐 ✅
7. **派工页移除开始/完成/拒绝派工**（职责收口）——推荐 ✅
8. **工序责任域 V1 不做硬权限**（仅软提示），等有可靠字段——推荐 ✅

---

## 21. 对 P1/P2/P3/P4 影响

- P1 Dispatch/DispatchNode：**不动核心**；仅 REASSIGN 规则收口 + 动作 UI 改名
- P2 WorkReport：加 assignment_id（可空）+ 报工权限/数量校验增强；Projection 不变
- P3 Quality：**零影响**（不绑 assignment）
- P4 Trace：只读投影扩展（新事件类型从 assignment 表读出），无新表
- 当前 -03 数据：无需清理，过渡兼容

---
*WP-A 只读设计完成，未实施任何代码/数据/Git。等待人工评审。*
