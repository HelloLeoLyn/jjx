# 事件 → 任务状态联动通用方案（task_effects）

- 任务码：dev-20260921-016
- 状态：设计稿（待实现）
- 关联：dev-20260921-017（样品链止血，已落地 迁移 154）、dev-20260921-012/013/014（事件信封 bizNo / 模板迁移 / 配置页体验）
- 替代/升级对象：`sys_event_config.close_source_events`（单一"关闭"动作）

---

## 0. 一句话

把"业务状态变化 → 任务状态变化"从当前**单一的 `close_source_events`（只能关闭→10）**升级为**声明式 `task_effects`**（可推进/关闭/改派/写审计），并使**业务状态机成为唯一真源**，任务状态可由映射推导 —— 新单据只需登记映射，不必再逐个事件重复配动作。

## 1. 触发与问题

**实例（2026-09-21 用户报）**：样品单 `SP260921001` 的业务任务是
`sample.created-…`「样品单【SP260921001】已创建，请安排打样」（assign_role=16、biz_id=1），
15:30:13 创建 → **15:31:27 工程接单（业务状态 2→打样中3）时被直接置为已完成(10)**，
`completed_time=15:31:28`、`update_by=NULL`、`remark=NULL`。

**语义错配**：
- 「接单 / 开始打样」= 任务应 **进行中(1)**，却被当成"办结"直接关闭；
- 「打样完成」(`sample.ready`) 才该关闭，却因为已经关掉了而**不再关闭**（该关的不关）。

**机制**：`sys_event_config.sample.accepted.close_source_events = sample.created` →
`LocalEventPublisher` 的"办结关闭"把同业务未办结待办直接置 10。

**这不是孤例**：全库已有 **33 条**事件配了 `close_source_events`（见附录 A；迁移 154 落地前 32 条），
会建任务的事件 **61 条**（`both`），而实现只有 `LocalEventPublisher` 里约 20 行 +
`SampleOrderServiceImpl` 1 处手写。→ 必须做成公共通用机制。

## 2. 现状机制（代码事实）

```
@Event(注解) → EventAspect（事务 afterCommit）→ LocalEventPublisher.fire(eventCode, payload)
   ├─ 通知：target_role 展开角色 → 用户；或 receiverId 直发
   ├─ 任务：assignRole = target_role[0]；kanbanModule 默认 biz；status=0
   └─ 办结关闭：close_source_events（逗号分隔事件码）
        · 任务：同 bizType+bizId、kanbanModule ∈ (biz,office)、sourceEvent=该码、status ∈ {0,1} → 置 10 + completed_time=now
        · 通知：bizType=该码、bizId 相同、未读 → 置已读
```

**六个结构性缺陷**：
1. 只有"关闭"一种动作，表达不出"推进到进行中/待审核"；
2. 可改状态写死 `{0,1}`，无法处理"已完成任务被退回"等；
3. **无审计**：自动变更不留痕（`update_by`/`remark` 空），看板上"系统自动办结"与"人工完成"无法区分；
4. 通知被**静默标已读**（收件人可能根本没看到就被"消费"）；
5. 匹配条件只有 `sourceEvent`，无法按角色/当前状态/来源模块匹配；
6. 每个事件重复配置同一套语义，33 处复制粘贴，改一次要动 33 行。

## 3. 目标 / 非目标

**目标**
- 声明式：一次配置能表达"谁（匹配）→ 变什么（动作）"，支持状态推进与关闭；
- 通用：所有单据共用同一个执行组件，事件配置只是数据；
- 可审计：每次自动变更可追溯到"哪个事件、什么原因、从什么状态到什么状态"；
- 向后兼容：既有 32 条配置零改动继续工作，可平滑迁移。

**非目标（本期不做）**
- 多渠道通知引擎（邮件/钉钉/企微/聚合）→ 归 dev-20260921-015；
- 业务状态机本身的重构（只做"任务跟随业务状态"）。

## 4. 设计

### 4.1 配置 schema：`sys_event_config.task_effects`（JSON 数组，可空）

```json
[
  {
    "match": { "sourceEvent": ["sample.created"], "assignRole": [16], "status": [0, 1] },
    "set":   { "status": 1, "reason": "工程接单，任务转进行中" }
  },
  {
    "match": { "sourceEvent": ["sample.created"] },
    "set":   { "status": 10, "completedTime": true, "notifyRead": true,
               "reason": "打样完成，办结【请安排打样】待办" }
  }
]
```

| 字段 | 取值 | 说明 |
|---|---|---|
| `match.sourceEvent[]` | 事件码 | 匹配任务的 `source_event`；省略=匹配同业务全部任务 |
| `match.assignRole[]` | 角色ID | 匹配任务 `assign_role` |
| `match.status[]` | 0/1/2/3/4/10 | 只处理处于这些状态的任务（默认 `[0,1]`，与旧行为一致） |
| `match.bizType` | sales/inventory/… | 默认取当前事件的 bizModule |
| `set.status` | 0待开始 1进行中 2待审核 3阻塞 4已废弃 10已完成 | 目标状态 |
| `set.completedTime` | true/false | true=置 now；false=清空（用于"已完成退回进行中"） |
| `set.assigneeRole/Id/Name` | — | 改派（可选） |
| `set.priority` | urgent/high/normal/low | 可选 |
| `set.remark` | 文本 | **追加**到任务备注（审计留痕） |
| `set.notifyRead` | true/false | 是否把同源未读通知置已读（默认 true，保持旧行为） |
| `set.reason` | 文本 | 写入审计日志的变更原因 |

### 4.2 兼容策略（关键）

- `close_source_events` **保留但标记 @Deprecated**；读取时等价映射为
  `[{ "match": {"sourceEvent": [旧值]}, "set": {"status": 10, "completedTime": true, "notifyRead": true} }]`，
  与 `task_effects` 合并执行 → **既有 33 条配置零改动继续生效**；
- 提供幂等迁移脚本：`close_source_events` → `task_effects`（转换后把旧字段置空），
  并保留附录 A 的原始值以便回滚；
- 双写期：两者同时存在时以 `task_effects` 优先、`close_source_events` 兜底（避免重复执行同一效果：按 `set.status` 幂等处理）。

### 4.3 执行组件 `TaskStateSynchronizer`（公共）

放在 `com.jjx.event` 包，`LocalEventPublisher` 只负责调用：

```
apply(bizType, bizId, eventCode, payload):
  1. 取事件配置 → effects = task_effects ∪ legacyCloseMapping
  2. 查同业务任务：bizType + bizId（+ 可选 kanbanModule 范围）
  3. 逐 effect：全条件命中（sourceEvent/assignRole/status）→ 计算目标状态
  4. 单条 UPDATE（status / completed_time / assignee / priority / remark 追加）
  5. 审计：写 sys_task_log（新增表）
     task_id, from_status, to_status, caused_by_event, biz_type, biz_id,
     actor='system', reason, create_time
  6. 通知：按 set.notifyRead 决定是否置已读
```

### 4.4 语义真源（第二阶段）：业务状态驱动

新增 `sys_biz_task_state_map(biz_type, biz_status, task_status, note)`，并让单据状态变更走统一入口 →
任务状态**推导**而非"逐个事件配动作"。事件只保留"通知谁"的职责。
新单据接入成本 = 登记几行映射，而不是复制 3~5 条 close 配置。

### 4.5 状态对照示例：样品链

| 业务状态 | 事件 | 任务动作 |
|---|---|---|
| 已创建(1) | `sample.created` | 建任务 → **待开始(0)** |
| 待打样(2) | `sample.submitted` | 保持待开始（可评估：此处才是派任务的合适时点） |
| 打样中(3) | `sample.accepted` / `sample.started` | **推进 进行中(1)** |
| 待送样(4) | `sample.ready` | **完成(10)**（办结「请安排打样」） |
| 待送样(4) 通知 | `sample.ready` | 另建「样品已完成，请安排送样」任务（现状保留） |
| 已送样(5)/确认(6) | `sample.sent` / `sample.confirmed` | 关闭/推进上一环节 |
| 作废(10) | `sample.cancelled` | 关闭 + 备注原因 |

### 4.6 前端（事件配置页）

「办结关闭事件」字段升级为**「任务动作」编辑器**：动作（推进/关闭/改派）+ 目标状态 + 匹配条件（来源事件/角色/当前状态）+ **试渲染/试算预览**（给一条最近实际事件，预览会改哪些任务）。
→ 与 dev-20260921-014（事件配置页体验升级，阶段2）是同一块地，**建议合并实现**。

## 5. 迁移清单（首批）

- **附录 A 的 33 条**：语义不变，自动映射为 `close` 动作即可（脚本化、幂等）。
- **需要改成 `advance` 的（本次发现，共 3 处）**：
  - `sample.accepted`：`close(sample.created)` → `advance → 1(进行中)`
  - `sample.started`：`close(sample.created)` → `advance → 1(进行中)`
  - `sample.ready`：新增 `close(sample.created)`
- **建议一并评估的（同类"中间态"事件，目前只能关闭或什么都不做）**：
  `inventory.inbound.submitted→approved`、`inventory.outbound.submitted→approved`、
  `inventory.transfer.stocktake.submitted→approved`、`purchase.submitted→approved`、
  `order.submitted→review_started→approved`、`quality.iqc.submitted→approved`、
  `production.work-report.submitted→approved`、`product.film/routing.submitted→approved`、
  `bom.submitted→approved`、`product.submitted→approved`
  → 统一问一句："这一环是**推进**还是**办结**？"决定了任务状态是 1 还是 10。

## 6. 分期实施与分工

| 阶段 | 内容 | 归属 |
|---|---|---|
| A 止血 | 样品链不再提前办结（迁移 154，已落地） | dev-20260921-017 ✅ |
| B 通用机制 | 配置 schema + `TaskStateSynchronizer` + 兼容映射 + 审计表 | **建议并入 dev-20260921-012~014 那条线**（同文件：`LocalEventPublisher`、`sys_event_config`、事件配置页），避免撞车 |
| C 真源化 | `sys_biz_task_state_map` + 33 条全量迁移 + 前端试算 | 后续独立任务 |

依赖关系：与 012（payload 带 `bizNo`）**不冲突**，可并行；与 014（配置页）**强重叠**，必须同一人做。

## 7. 验收与测试要点

1. **样品链**：报价转样品单 → 工程接单 → 任务 = **进行中(1)**（不再 10）；打样完成 → 任务 = **已完成(10)**，`completed_time` 非空；
2. **回归**：入库/出库/调拨/盘点/采购/报价/订单/产品/BOM/菲林/工艺/报工等 33 条旧配置行为**完全不变**（关闭 + 通知已读）；
3. **审计**：`sys_task_log` 有记录（from/to/caused_by_event/reason），`remark` 有留痕；
4. **兼容**：老字段与新字段并存时结果一致、不重复执行；
5. **幂等**：同一事件重复触发不产生重复变更（按目标状态幂等）。

## 8. 风险与回滚

- 风险：旧配置映射错误导致任务状态乱 → 用 `db-migrate --status` + 迁移前 guard 备份；**灰度**：先样品链，观察一个完整流程后再全量；
- 回滚：① 代码开关 `event.task-effect.enabled=false` 回退到 `close_source_events` 逻辑；② 迁移反写（附录 A 保存了原始值）；
- 新增 `sys_task_log` 属**新增表**，风险低；对 `sys_task` 的写入仍是单条 UPDATE。

## 附录 A：现有 32 条 `close_source_events` 原值（迁移基线，勿手改）

| 事件ID | 事件编码 | 关闭的上一环节 |
|---|---|---|
| 35 | `bom.approved` | `bom.submitted` |
| 86 | `inventory.alert.processed` | `stock.shortage,stock.low,stock.over` |
| 47 | `inventory.inbound.approved` | `inventory.inbound.submitted` |
| 50 | `inventory.inbound.cancelled` | `inventory.inbound.submitted` |
| 49 | `inventory.inbound.confirmed` | `quality.iqc.approved` |
| 48 | `inventory.inbound.rejected` | `inventory.inbound.submitted` |
| 55 | `inventory.outbound.approved` | `inventory.outbound.submitted` |
| 58 | `inventory.outbound.cancelled` | `inventory.outbound.submitted` |
| 56 | `inventory.outbound.rejected` | `inventory.outbound.submitted` |
| 75 | `inventory.stocktake.approved` | `inventory.stocktake.submitted` |
| 63 | `inventory.transfer.approved` | `inventory.transfer.submitted` |
| 67 | `inventory.transfer.cancelled` | `inventory.transfer.submitted` |
| 64 | `inventory.transfer.rejected` | `inventory.transfer.submitted` |
| 15 | `order.approved` | `order.submitted,order.review_started` |
| 18 | `order.cancelled` | `order.submitted` |
| 16 | `order.rejected` | `order.submitted,order.review_started` |
| 33 | `product.approved` | `product.submitted` |
| 92 | `product.film.approved` | `product.film.submitted` |
| 93 | `product.film.rejected` | `product.film.submitted` |
| 97 | `product.routing.approved` | `product.routing.submitted` |
| 98 | `product.routing.rejected` | `product.routing.submitted` |
| 155 | `production.work-report.approved` | `production.work-report.submitted` |
| 156 | `production.work-report.rejected` | `production.work-report.submitted` |
| 108 | `purchase.approved` | `purchase.submitted` |
| 167 | `quality.iqc.submitted` | `quality.iqc.approved` |
| 3 | `quotation.reviewed` | `quotation.submitted` |
| 7 | `sample.approved` | `sample.submitted` |
| 11 | `sample.confirmed` | `sample.ready` |
| 9 | `sample.ready` | `sample.created` |
| 8 | `sample.rejected` | `sample.submitted` |
| 12 | `sample.rejected_by_customer` | `sample.ready` |
| 10 | `sample.sent` | `sample.ready` |
| 135 | `sample.transferred` | `sample.transfer.remind,sample.confirmed` |

> 共 33 条（导出于 2026-09-21，参与迁移前请再次导出核对）。
