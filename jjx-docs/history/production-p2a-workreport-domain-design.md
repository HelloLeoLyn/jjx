# JJX Production P2-A WorkReport Domain Design

> 版本：v1.0（评审稿）
> 日期：2026-08-19
> 状态：只读分析与领域设计，未改代码/未改数据库/未执行 migration/未提交 Git
> 范围：P2-A WorkReport 领域设计（不实施 P2-B/C/D，不实施 P3 Quality / P4 Trace）
> 依据：Execution/Dispatch/Quality/数量模型全量复核（真实代码/表/字段证据）

---

## 1. Executive Summary

P2 引入 **ProductionWorkReport = 一次不可覆盖的生产报工事实**，解决"谁在什么时间、针对哪道工序、基于哪个责任节点、实际生产了多少、合格多少、不良多少"。

**核心结论（关键决策预览）：**

| # | 决策点 | 结论 |
|---|---|---|
| 1 | 当前报工是什么 | **覆盖模型**：前端"记录"→ `PUT /production/operation-execution` → 直接覆盖 execution 数量字段（二次报工=覆盖非累计）——**P2 需改造** |
| 2 | production_operation_record | **死代码/未接线**（无任何 Service 调用写入）；定位=过程事件，与 WorkReport 彻底分开 |
| 3 | WorkReport 定义 | 一次不可覆盖的报工事实；每次报工新增一行，**不 UPDATE 数量** |
| 4 | 数量字段 | `qualifiedQuantity + defectiveQuantity`（与 execution 字段命名一致）；V1 不单独存 inputQuantity |
| 5 | finished/completed | WorkReport 聚合到 execution.qualified/defective；order.finished 仍走"最后有效工序合格数"规则（052 口径，P0 锁定） |
| 6 | 防双重累计 | **核心规则**：旧 updateExecution 数量写入路径 P2 后禁用/仅系统 projection 更新 |
| 7 | 报工 ≠ 完成 | WorkReport 只加事实；Execution complete 独立动作（柔性完成规则） |
| 8 | 权限 | 默认仅当前 ACTIVE DispatchNode assignee 可报工；库层不强制 reporter==assignee（留代报扩展） |
| 9 | 状态机 | SUBMITTED / CANCELLED（极简，不做审批） |
| 10 | 表结构 | production_work_report 新表（无物理 FK，遵循项目风格） |
| 11 | report_no | **不引入**（无追溯/打印需求，用 reportId） |
| 12 | dispatchId 冗余 | **冗余保存**（查询便利/历史稳定/报表，虽可推导但值得） |

---

## 2. 当前 Execution 数据模型（全量复核）

### 2.1 表（production_operation_execution）

| 字段 | 类型 | 语义 |
|---|---|---|
| input_quantity | DECIMAL(18,4) | 投入数量（create 时=planned_quantity） |
| output_quantity | DECIMAL(18,4) | 产出数量 |
| qualified_quantity | DECIMAL(18,4) | 合格数量 |
| defective_quantity | DECIMAL(18,4) | 不良数量 |
| defective_reason | VARCHAR(500) | 不良原因 |
| actual_labor_hours / actual_machine_hours | DECIMAL(10,2) | 人工/机器工时 |
| operator_id/operator_name | — | 操作员 |
| equipment_id/equipment_code/equipment_name | — | 设备 |
| actual_start_time / actual_end_time | DATETIME | 实际起止 |
| execution_status | TINYINT | 0待执行/1准备中/2执行中/3已暂停/4已完成/5已跳过/6已取消/7已超期/8异常中/9待确认 |
| planned_start_time / planned_end_time | DATETIME | 计划起止 |

### 2.2 逐字段：谁写入、什么时候写、是否累计

| 字段 | 写入点（真实代码） | 累计？ | 覆盖？ | 用于 |
|---|---|---|---|---|
| input_quantity | `convertCreateDTOToEntity`（ExecutionServiceImpl:844）：create 时 = planned_quantity | 否 | 否 | 投入基数 |
| output_quantity | ① completeExecution（417）：未设置时=输入量；② updateExecution（866）：`actualCompletedQuantity` 覆盖 | 否 | **是（覆盖）** | 产出展示 |
| qualified_quantity | ① completeExecution（421）：默认=output；② updateExecution（869）：覆盖 | 否 | **是（覆盖）** | **完工判断核心（052）** |
| defective_quantity | ① completeExecution（425）：默认 0；② updateExecution（872）：覆盖 | 否 | **是（覆盖）** | 不良展示 |
| actual_labor_hours / machine_hours | updateExecution（860/863）：覆盖 | 否 | **是（覆盖）** | 成本/报表 |
| defective_reason | updateExecution（P0-03 修复后：defectiveReason→defective_reason） | — | 覆盖 | 不良说明 |
| actual_start/end_time | start/complete/pause 事件写入 | — | 覆盖 | 工时/状态 |
| execution_status | start/pause/complete/cancel 状态机 | — | — | 执行状态 |

### 2.3 数量关键结论

**当前数量字段 = 覆盖式汇总字段**，不是增量事实：
- 前端"记录"弹窗（execution/index.vue:590-613）→ `operationExecutionApi.edit` → updateExecution → **直接覆盖** execution 的 output/qualified/defective
- 第二次报工 200 会**覆盖**第一次 100，最终=200（不是 300）
- completeExecution 默认补全（output=qualified=input 当未设置时）

### 2.4 完工联动（updateOrderCompletedQuantity，ExecutionServiceImpl:605-630）

```
completed_quantity = SUM(已完成工序的 qualified_quantity)
finished_quantity  = 最后一道工序（process_order 最大）的 qualified_quantity
```

- P0 已锁定：finished=成品完工唯一口径（052），completed=工序汇总展示
- P2 遵守：WorkReport 聚合后此规则继续成立（见 §10）

---

## 3. 当前"报工"实际行为（真实代码证据）

| 证据 | 位置 |
|---|---|
| 前端"记录"按钮 | execution/index.vue:150 `handleRecord`（权限 production:operation-record:add） |
| 记录弹窗字段 | input/output/qualified/defective/labor/machine/reason/params（269-347） |
| 提交 | submitRecord（590-613）→ `operationExecutionApi.edit` → `PUT /production/operation-execution` |
| 后端处理 | updateExecution + `updateEntityFromUpdateDTO`（866-872）：actualCompletedQuantity→outputQuantity 等 |
| **行为确认** | **二次报工 = 覆盖**（updateById 直接覆盖，非累计） |

**结论**：当前所谓"报工"= 一次 UPDATE execution 汇总字段（覆盖模型）。P2 WorkReport 引入后改造为"新增事实行 + 汇总 projection"。

---

## 4. production_operation_record 定位（复核）

### 4.1 现状

- Entity 完整（record_type：START/PAUSE/RESUME/COMPLETE/QUALITY/ISSUE/PARAM/STATUS），有 quantity/parameters/qualityData 等字段
- **但全项目 grep：无任何 Service 调用写入该表**（`recordMapper`/`operationRecordService` 无使用点）
- Controller 存在（ProductionOperationRecordController），但无数据流入

### 4.2 结论

- 该表当前 = **未接线的历史遗留设计**（定位：Execution Timeline/操作事件历史）
- **不适合承担 WorkReport 职责**（它是过程事件模型，不是数量事实模型）
- P2 原则：**OperationRecord = 过程事件（未来接线），WorkReport = 生产数量/工时事实（新表）**——两者彻底分开

---

## 5. DispatchNode 集成

### 5.1 从 executionId 找到责任链（现状查询路径）

```
executionId → production_dispatch.execution_id（UNIQUE 1:1，DispatchServiceImpl:801 已用）
            → production_dispatch_node.dispatch_id + node_status='ACTIVE'（当前责任人）
            → node.assignee_id/assignee_name/org_*（责任快照）
```

### 5.2 dispatchId 是否冗余保存到 WorkReport

**✅ 冗余保存**。理由：
| 角度 | 分析 |
|---|---|
| 查询便利 | 报工列表/按工单统计直接 join dispatch_id，免两层推导 |
| 历史稳定 | WorkReport 是事实，未来 dispatch 若被清理/重构，事实仍需可独立查询 |
| Trace | 追溯链路 execution→dispatch→node→report 每层有锚点 |
| 成本 | 冗余一列（BIGINT）几乎零成本 |
| 一致性 | dispatchNode 由 dispatch 生成，二者天然一致；冗余仅便利 |

**结论**：WorkReport 同时保存 `execution_id + dispatch_id + dispatch_node_id`。

---

## 6. WorkReport 正式领域定义

```
ProductionWorkReport
= 一次不可覆盖的生产报工事实。

示例：
张三，在冲型工序，08:00-10:00，基于 DispatchNode N100，报工：合格 950、不良 50。

第二次报工 → 新增一条；不 UPDATE 第一条数量。
```

**✅ 适合 JJX 当前业务**：
- 当前业务就是"工人干了活，填个数"（execution 记录弹窗），只是用覆盖模型实现
- 改成"每次报工=一行事实"后，天然支持多次报工（分批生产/试机/返工补录），且审计完整
- 与 P1 DispatchNode"责任持有实例"模型一致：责任流转期间可多次报工

---

## 7. reporter / responsibility 关系

| 概念 | 字段 | 说明 |
|---|---|---|
| 实际责任主体 | dispatch_node_id（+ 可推导 assignee） | 谁当时负责（P1 Node 快照） |
| 报工提交人 | reporter_id / reporter_name | 谁实际操作提交 |

- **P2 V1 权限层**：默认仅当前 ACTIVE assignee 可报工（reporter==assignee）
- **数据库不强制** reporter_id == dispatch_node.assignee_id（未来班组长代报/扫码/集中报工/多人生产）
- 两个概念并存：`responsibleNodeId`（责任）+ `reporterId`（提交人）

---

## 8. 数量模型

### 8.1 字段选择

**采用（与 execution 字段命名一致）：**

```
qualified_quantity  合格数量
defective_quantity  不良数量
```

**不引入** reportedQuantity 独立字段——`qualified + defective` 即本次加工数量（简单、命名统一）。

### 8.2 校验规则（P2 V1）

| 规则 | 处理 |
|---|---|
| 负数 | 拒绝（前端 min=0 + 后端校验） |
| 0 | 允许（0 报工=记录了"干了 0"的事实，但见 §24 完成规则） |
| 小数 | 允许（现有 DECIMAL(18,4)，薄膜行业按片可整数，允许小数兼容） |
| 超计划 | **允许**（返工/试机/损耗/重复加工存在，不默认 <= planned）——但记录 warning 可选 |
| 累计超过计划 | 允许（不做硬 gate；P2 柔性） |
| 不良 > 本次加工 | 拒绝（defective <= qualified+defective 恒成立，即 defective 单独非法上限=本次加工量） |
| 合格+不良关系 | 不强制 sum 约束（允许合格+不良 < 加工量=损耗；**不引入 input 时无"=加工量"要求**） |

**明确不做**：复杂返工体系、缺陷分类字典（P3）、Allocation（多人拆分）。

---

## 9. input/output/qualified/defective 决策

### 9.1 WorkReport 是否存 inputQuantity

**V1 不存**。依据：
- 当前 execution 的 input_quantity 是**计划投入基数**（create 时=planned），不是每批实际投入
- 当前前端记录弹窗虽有"投入数量"输入，但提交只传 actualCompleted/Qualified/Defective（inputQuantity 未传——见 submitRecord 只提交 5 字段）
- 当前业务不存在"投入≠产出"的 WIP 半成品流转模型（薄膜行业整单投入产出）

**结论**：V1 只存 `qualified_quantity + defective_quantity`；若未来出现损耗/WIP 需求，加 input_quantity（P2 后评估）。

### 9.2 execution 汇总字段保留

execution.output/qualified/defective 字段**不删除**，P2 后定位 = **Projection/Aggregate**（由 WorkReport 重算，见 §22）。

---

## 10. finished / completed 关系（P0 锁定遵守）

### 10.1 现有规则（P0 确认）

- `finished_quantity` = 最后有效工序（052 口径）合格数 = **成品完工唯一判断口径**
- `completed_quantity` = 已完成工序合格数汇总 = 展示口径

### 10.2 P2 设计

| 数据 | 更新方式 |
|---|---|
| execution.qualified/defective | WorkReport 聚合（SUM SUBMITTED）——事务内重算 |
| execution.labor/machine_hours | WorkReport 聚合（SUM） |
| order.completed_quantity | **沿用现有逻辑**（updateOrderCompletedQuantity：SUM 已完成工序 qualified）——基于 projection |
| order.finished_quantity | **沿用现有逻辑**（最后有效工序合格数）——052 口径，**禁止**简单 SUM 所有报工合格数 |
| 质检 gate | **等 P3**（WorkReport 不触发质检；P2 柔性完成） |

---

## 11. 工时模型

### 11.1 V1 字段

WorkReport 保存：
```
labor_hours   人工工时（手动填写）
machine_hours 机器工时（手动填写）
```

**不从 start/end 自动计算**。依据：
- 人工工时 ≠ 时间差（多人/停机/等待）
- 机器工时 ≠ 人工工时
- 当前 execution 就是手动填写（前端 input-number 0.1 步进）
- P2 V1 简单优先

### 11.2 计算方式

- 报工表单人工填写（与现状一致）
- 校验：>=0，允许 0

### 11.3 后续扩展方向

- workStart/workEnd 记录后，可提供"建议工时"提示（前端可选）
- 多人/设备自动采集（P2 明确不做 MES IoT）

---

## 12. 时间模型

### 12.1 V1 字段

```
work_start_time  本次报工对应生产开始（可选）
work_end_time    本次报工对应生产结束（可选）
report_time      报工提交时间（必填，DB 默认 CURRENT_TIMESTAMP）
```

### 12.2 决策依据

- 现场实际：工人连续生产，报工是间歇动作——**单次报工的工作区间有业务价值**（责任人持责时间/设备使用时间计算）
- 但 V1 不强制造填（可选），前端提供默认值（上次报工 end → 本次 start）
- 不做过度设计：不引入排班/日历

---

## 13. 设备关系

**✅ WorkReport 保存 `equipment_id + equipment_name`（本次实际使用设备快照）。**

依据：
- Execution 有默认设备（create 带入）；Dispatch 有设备（可空=不限）
- **实际生产可能换设备**（设备故障换机/多机轮流）——WorkReport 必须记录"本次实际"而非"默认"
- 前端报工表单提供设备选择（默认带出 execution 设备，可改）

---

## 14. 组织快照

**❌ WorkReport 不冗余 org 快照**。

依据：
- dispatch_node 已保存历史 org 快照（org_id/org_name/org_path）
- WorkReport 有 dispatch_node_id → 可推导
- 冗余 reporter org 无意义（报工事实不因组织调整改变；责任组织已由 Node 快照锁定）

**结论**：通过 dispatchNodeId 关联即可，不加 org_id/org_name 字段。

---

## 15. WorkReport 状态机

**SUBMITTED / CANCELLED（极简）** ✅ 足够。

| 状态 | 含义 | 进入方式 |
|---|---|---|
| SUBMITTED | 已提交，计入汇总 | 创建 |
| CANCELLED | 已撤销，不计入汇总 | 撤销动作 |

**无 DRAFT/APPROVED/REJECTED 依据**：当前 JJX 无报工审批流程（execution 记录直接保存生效），无质量门（P3 才有）。不做提前审批。

**规则**：已提交报工不允许直接编辑数量；更正 = 原报工 CANCELLED + 新增正确报工。

---

## 16. 撤销模型

| 项 | 设计 |
|---|---|
| 谁可以撤销 | 提交人本人 / 超管 / 生产主管（有 assign 权限）——与 P1 权限模型一致 |
| 撤销后汇总 | 事务内重算 execution projection（SUM 剩余 SUBMITTED） |
| 已完成工序 | **允许撤销**（P2 柔性；P3 Quality 接入后若已质检可能加强限制） |
| 已进入质检 | P2 无质检绑定（P3 后评估） |
| cancellationReason | 必填（审计） |
| cancelled_by / cancelled_by_name / cancelled_at | 记录 |

**API**：`POST /production/work-report/{id}/cancel`（body: reason）

---

## 17. 不良原因模型

**方案 A（V1）**：`defect_reason VARCHAR(500)` 单字段。

**不做方案 B（defect 子表）**：
- 当前 execution 已有 defective_reason 单字段先例
- P3 Quality 才设计正式缺陷明细/分类（quality_check_result JSON 已存在）
- P2 不提前建 defect catalog

---

## 18. 不可变规则

SUBMITTED WorkReport 以下字段**禁止修改**：
```
execution_id / dispatch_id / dispatch_node_id
reporter_id / reporter_name
qualified_quantity / defective_quantity
labor_hours / machine_hours
equipment_id / equipment_name
work_start_time / work_end_time
report_time
```

允许：
```
report_status（SUBMITTED → CANCELLED）
cancelled_* 字段
```

**remark 也尽量不可变**（审计一致性）：V1 不允许编辑；如需更正走 cancel+new。

---

## 19. 推荐表结构（DDL，未执行）

```sql
CREATE TABLE `production_work_report` (
  `report_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '报工ID',
  `order_id`           BIGINT       NOT NULL COMMENT '生产订单ID',
  `order_no`           VARCHAR(50)  NULL COMMENT '工单编号(冗余)',
  `execution_id`       BIGINT       NOT NULL COMMENT '工序执行记录ID',
  `dispatch_id`        BIGINT       NULL COMMENT '派工单ID(冗余，可推导但利于查询/追溯)',
  `dispatch_node_id`   BIGINT       NOT NULL COMMENT '责任节点ID(报工时 ACTIVE 节点；补录=当时节点)',
  `reporter_id`        BIGINT       NOT NULL COMMENT '报工提交人ID',
  `reporter_name`      VARCHAR(64)  NOT NULL COMMENT '报工提交人姓名快照',
  `equipment_id`       BIGINT       NULL COMMENT '本次实际使用设备ID',
  `equipment_name`     VARCHAR(200) NULL COMMENT '本次实际使用设备名称(快照)',
  `qualified_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '本次合格数量',
  `defective_quantity` DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '本次不良数量',
  `labor_hours`        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '本次人工工时',
  `machine_hours`      DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '本次机器工时',
  `work_start_time`    DATETIME     NULL COMMENT '本次生产开始时间(可选)',
  `work_end_time`      DATETIME     NULL COMMENT '本次生产结束时间(可选)',
  `report_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报工提交时间',
  `defect_reason`      VARCHAR(500) NULL COMMENT '不良原因(单字段，P3 再细化)',
  `remark`             VARCHAR(500) NULL COMMENT '备注(提交后不可变)',
  `report_status`      VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态：SUBMITTED/CANCELLED',
  `cancelled_by`       BIGINT       NULL COMMENT '撤销人ID',
  `cancelled_by_name`  VARCHAR(64)  NULL COMMENT '撤销人姓名',
  `cancelled_at`       DATETIME     NULL COMMENT '撤销时间',
  `cancel_reason`      VARCHAR(500) NULL COMMENT '撤销原因',
  `create_by`          VARCHAR(64)  NULL COMMENT '创建人',
  `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          VARCHAR(64)  NULL COMMENT '更新人',
  `update_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`report_id`),
  KEY `idx_execution` (`execution_id`),
  KEY `idx_dispatch_node` (`dispatch_node_id`),
  KEY `idx_reporter_status` (`reporter_id`, `report_status`),
  KEY `idx_report_time` (`report_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产报工(一次不可覆盖的生产数量/工时事实)';
```

### 字段逐项决定

| 字段 | 决定 | 理由 |
|---|---|---|
| report_id | 必须 | 主键 |
| report_no | **不需要** | 无追溯/打印/现场沟通编号需求；用 reportId 即可（评审 §十九：不为 ERP 风格强加编号） |
| order_id/order_no | 必须/可选 | order_id 必须（统计/追溯）；order_no 冗余可选（列表展示便利） |
| execution_id | 必须 | 关联工序 |
| dispatch_id | 冗余保存 | §5.2 |
| dispatch_node_id | 必须 | 责任锚点 |
| reporter_id/name | 必须 | 提交人 |
| equipment_id/name | 必须 | 本次实际设备 |
| input_quantity | **不需要** | §9.1 |
| output_quantity | **不需要** | 合格+不良即本次加工量；不引入第三口径 |
| qualified/defective | 必须 | 核心事实 |
| labor/machine_hours | 必须 | 工时事实 |
| work_start/end | 可选 | §12 |
| report_time | 必须 | 事实时间 |
| defect_reason | 可选 | 单字段 |
| remark | 可选 | 不可变 |
| report_status | 必须 | SUBMITTED/CANCELLED |
| cancelled_* | 可选 | 撤销审计 |
| create/update audit | 必须 | 项目风格 |

---

## 20. 索引设计（最小）

```
idx_execution (execution_id)                  → execution 报工历史查询
idx_dispatch_node (dispatch_node_id)          → 责任节点报工查询
idx_reporter_status (reporter_id, report_status) → 我的报工（未来）
idx_report_time (report_time)                 → 时间范围
```

**不做**：报表组合索引（P2 无独立报工报表）；execution+status 组合可后续按需加。

---

## 21. 外键策略

**无物理 FK**（遵循项目现状——production_dispatch/log/node 均无 FK，全逻辑关联）。execution_id/dispatch_id/dispatch_node_id 用索引 + Service 校验。

---

## 22. Execution projection 汇总模型

### 22.1 汇总规则

```
execution.qualified_quantity = SUM(SUBMITTED work_report.qualified_quantity WHERE execution_id=?)
execution.defective_quantity = SUM(SUBMITTED work_report.defective_quantity ...)
execution.actual_labor_hours = SUM(SUBMITTED work_report.labor_hours ...)
execution.actual_machine_hours = SUM(SUBMITTED ... machine_hours ...)
execution.output_quantity = qualified + defective（投影口径，不再独立输入）
```

### 22.2 更新时机

**✅ 每次提交/撤销后事务内重算（SUM）**：
- 正确性优先（V1 数据量小：单工序报工几条）
- 增量 +/- 易错（撤销/并发边界复杂），不采用

### 22.3 实现位置

- WorkReport Service 事务内：INSERT report → UPDATE execution projection（重算）
- 与 P1 DispatchActionService 模式一致（Node 写 + dispatch projection 同步）

---

## 23. 防双重累计策略（P2 最重要规则）

### 23.1 当前写 execution 数量字段的旧路径（全量清单）

| 路径 | 位置 | P2 处理 |
|---|---|---|
| ① 前端"记录"弹窗 → `PUT /production/operation-execution`（updateExecution） | ExecutionController:44 / ExecutionServiceImpl:866-872 | **禁用数量写入**（updateDTO 的 actualCompletedQuantity/Qualified/Defective 改为拒绝或忽略，报错提示用报工） |
| ② completeExecution 默认补全（output=qualified=input） | ExecutionServiceImpl:417-425 | **改为**：若 execution 无 projection 则保持 0/由报工决定；不自动伪造数量 |
| ③ updateOrderCompletedQuantity | ExecutionServiceImpl:605-630 | **保留**（基于 projection 汇总 order，非直接写 execution）——本身不是双重累计源 |
| ④ 工单完工冻结（053） | ExecutionServiceImpl:393-398 | **保留**（工单完工后禁报工/改数量） |

### 23.2 核心规则

```
P2 上线后：
execution 数量/工时字段 = 仅由 WorkReport 汇总（系统 projection）写入
旧 updateExecution 数量写入 → 拒绝（BusinessException："请使用报工记录生产数据"）
```

- 这样 WorkReport+100 不会与旧 Service +=100 双重累计（旧路径被禁）
- 兼容期：`PUT /operation-execution` 保留其他字段（operator/equipment/status/异常），仅数量字段拒绝

---

## 24. Execution 完成规则（P2 推荐）

### 24.1 原则：报工 ≠ 完成

- WorkReport 只增加事实
- Execution complete 独立动作（现有 `PUT /{id}/complete`，状态机 canCompleteExecution：EXECUTING → COMPLETED）

### 24.2 柔性完成规则（V1）

| 场景 | 处理 |
|---|---|
| 0 报工允许完成 | 允许（V1 不强制"必须先报工"；提示可选） |
| 累计合格 < 计划 | 允许（不硬 gate；P3 Quality 后可加） |
| 累计数量 > 计划 | 允许（返工/损耗场景） |
| 存在不良 | 允许（P3 质检门处理） |
| Quality 未接入 | 不替 P3 做质检 gate |

**明确不做**：P2 不替 P3 做质检 gate（评审 §二十四）。

---

## 25. 权限（reporter 规则）

### 25.1 提交报工

```
必须：execution 已 START（status=2 EXECUTING 或 3 PAUSED）才能报工
默认：仅当前 ACTIVE DispatchNode assignee 可报工
权限点：复用 production:operation-execution:edit 或新增 production:work-report:add
       （推荐复用现有 edit 权限点，0 权限数据变更）
```

### 25.2 管理员代报

- **V1 不开放代报**（无 actual operator 模型，开放会造成责任混乱）
- 未来需求（班组长代报/扫码）→ reporter 与 assignee 分离已预留，届时放开

### 25.3 撤销

- 提交人本人 / 超管 / 生产主管（有 assign 权限）

---

## 26. DispatchNode 状态与报工关系

### 26.1 V1 边界

| Node 状态 | 允许新报工？ |
|---|---|
| ACTIVE | ✅ 当前责任人可报工 |
| DELEGATED/REASSIGNED/RETURNED/COMPLETED | ❌ 普通用户不可对非 ACTIVE 节点报工（当前 ACTIVE 是唯一报工锚点） |
| 历史补录 | 管理员未来需求（V1 不做） |

### 26.2 报工后 Node 不变

**提交 WorkReport 不改变 DispatchNode 状态**：
- 报工一次 ≠ Node COMPLETED
- Node COMPLETED 跟工序/派工最终完成（dispatch.complete → ACTIVE Node → COMPLETED，P1-C 已实现）

---

## 27. 当前前端 Execution 分析（复用评估）

### 27.1 现状（execution/index.vue）

| 部分 | 现状 |
|---|---|
| 筛选 | 工单/工序/状态 Tab（全部/我的——operatorName='当前用户' 过滤）+ 关键字 |
| 表格列 | 工单/工序/计划量/合格量/不良量/操作员/设备/状态/操作 |
| 操作按钮 | 开始（status=0）/暂停（2）/完成（2）/详情/记录/质检（2/4） |
| 记录弹窗 | 全量表单：input/output/qualified/defective/labor/machine/reason/params（**P2 报工 Drawer 前身**） |
| 详情 | 基本信息 + 合格率/不良率 + 质检区 |
| 我的 Tab | operatorName='当前用户'（**旧逻辑：execution.operator_name 过滤，非 Node ACTIVE——P2 应改用 currentAssignee**） |

### 27.2 P2 改造点

- **"记录"按钮 → "报工"按钮**（Drawer），复用记录弹窗的 数量/工时/设备/不良原因 字段
- **"我的"Tab 过滤改为** currentAssignee（Node projection）——当前 operatorName='当前用户' 只匹配 execution.operator_name（可能为空/过时）
- 表格列加"当前责任人"（currentAssigneeName，P1-D 已提供）
- 详情加"报工记录"区（按 executionId 查 work-reports）

---

## 28. P2 前端目标预设计（本轮不开发）

**Execution 工作台目标：**

```
筛选：工单/工序/状态/当前责任人（Node ACTIVE）
列：工单/产品/工序/当前责任人/设备/计划量/累计合格/累计不良/执行状态
操作：开始 | 报工 | 暂停 | 恢复 | 完成 | 详情
报工 Drawer：
  当前责任人（Node）展示
  本次合格/不良
  工时（人工/机器）
  设备（默认 execution，可改）
  不良原因
  生产时间区间（可选，默认上次报工末→现在）
  提交 → POST /work-report
详情 Drawer：基本信息 + 报工记录（Timeline）+ 操作记录 + 派工责任链
```

**改造评估**：中等（复用现有记录弹窗表单 + P1 已有的 currentAssignee/allowedActions 基础），不做大重构。

---

## 29. 报工历史 API

```
GET /production/work-report/execution/{executionId}
  返回：report_id / reporter / 合格/不良 / 设备 / work time / report_time / status / cancel info
  默认：SUBMITTED + CANCELLED 全展示，CANCELLED 明确标记
```

---

## 30. WorkReport API V1（最小）

| METHOD | PATH | 说明 |
|---|---|---|
| POST | `/production/work-report` | 创建报工（executionId + 数量/工时/设备/原因） |
| POST | `/production/work-report/{id}/cancel` | 撤销（reason 必填） |
| GET | `/production/work-report/execution/{executionId}` | 报工历史 |
| GET | `/production/work-report/{id}` | 单条详情 |

**不做**：通用 CRUD（无独立报工菜单）；无全局 /page。

---

## 31. Migration 影响

- **新表** `production_work_report`（V20260819_002 或 P2-B 实施时定）
- **0 表结构修改**：execution 字段保留（改投影语义，不改列）
- 旧数据兼容：现有 execution 数量值保留为"历史投影"（无 WorkReport 行的 execution 沿用现值；P2 首次报工时重算）

---

## 32. 历史数据兼容

| 场景 | 处理 |
|---|---|
| 已有 execution 数量（覆盖模型遗留） | 保留现值作为投影（不迁移为 WorkReport 行——无法恢复真实逐次报工历史） |
| 已完工工单 | 不再报工（053 冻结保留） |
| 现有 execution 页"我的"过滤 | 从 operatorName 改为 currentAssignee（P2-D） |

---

## 33. P2 Work Package 拆分

**✅ 确认 A/B/C/D 四 WP 合理**（P2-A 已含 domain design，无需再拆 database foundation 独立 WP——表结构在 P2-B 实施）：

| WP | 内容 | 验收 |
|---|---|---|
| **P2-A**（本轮） | 领域设计（本报告） | 评审通过 |
| **P2-B Actions** | migration 建表 + WorkReport Entity/Enum/Mapper + Service（create/cancel/projection 重算）+ 权限 + 防双重累计（禁用旧 update 数量路径） | 单测 + 事务验证 |
| **P2-C Execution Integration** | Execution 汇总 projection 接入 + 完工规则复核 + 旧记录弹窗 API 兼容处理 + finished/completed 联动 | 回归 |
| **P2-D Frontend & Regression** | execution 页报工 Drawer + 报工历史 + 当前责任人列 + 全量回归 | vue-tsc + 手测 |

---

## 34. 明确 P2 不做

多级审批报工 / 计件工资 / 扫码 PDA / 自动设备采集 / MES IoT / 多人并行数量 Allocation / 复杂返工流程 / 缺陷明细字典 / Quality Integration（P3）/ Trace 自动事件（P4）/ 成本核算 / OEE / APS / input 独立投入模型 / WorkReport 全局分页 CRUD。

---

## 35. P2-A 最终需要人工拍板的决策点

| # | 决策点 | 推荐 | 说明 |
|---|---|---|---|
| 1 | WorkReport 是否存 input_quantity | **不存**（V1） | 现业务无投入≠产出模型 |
| 2 | dispatchId 冗余 | **冗余** | §5.2 |
| 3 | 数量字段命名 | **qualified/defective**（与 execution 一致） | §8 |
| 4 | report_no | **不引入** | §19 |
| 5 | 超计划报工 | **允许**（柔性） | 返工/试机场景 |
| 6 | 状态机 | **SUBMITTED/CANCELLED** | 无审批依据 |
| 7 | 完成规则 | **柔性**（0/超/不良均允许） | P3 质检 gate |
| 8 | 管理员代报 | **V1 不开放** | 无 actual operator 模型 |
| 9 | 非 ACTIVE 节点补录 | **V1 不允许** | 管理员补录未来需求 |
| 10 | 权限点 | 复用 production:operation-execution:edit（0 权限数据变更）或新增 work-report:add | 待定 |
| 11 | 旧 updateExecution 数量字段 | **禁用**（拒绝+提示用报工） | 防双重累计 |
| 12 | execution 页"我的"Tab | 改 currentAssignee 过滤 | P2-D |

---

## 附录 A：关键现状依据索引（复核时点 2026-08-19）

| 依据 | 位置 |
|---|---|
| Execution 数量字段 | `ProductionOperationExecution.java:84-96`；表 `production_operation_execution` |
| completeExecution 默认补全 | `ProductionOperationExecutionServiceImpl.java:417-425` |
| updateExecution 数量覆盖 | `ProductionOperationExecutionServiceImpl.java:866-872` |
| updateOrderCompletedQuantity | `ProductionOperationExecutionServiceImpl.java:605-630` |
| 前端记录弹窗/提交 | `jjx-web/src/views/production/execution/index.vue:269-347, 590-613` |
| 前端"我的"Tab operatorName 过滤 | execution/index.vue:462-485 |
| OperationRecord 无写入 | 全项目 grep：无 recordMapper 调用 |
| Dispatch→execution 查询 | `DispatchServiceImpl.java:801` |
| P1 Node ACTIVE 查询 | `DispatchNodeReadServiceImpl.getCurrentActiveNode` |
| 工单完工冻结（053） | `ProductionOperationExecutionServiceImpl.java:393-398` |
| P0 完工口径锁定 | P0 报告 §3（finished=052 唯一口径） |

---

*报告完。本轮只读，未改代码/未改数据库/未执行 migration/未提交 Git。等待人工评审。*
