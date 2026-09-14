# JJX Production P4-A Production Trace V1 Domain Design

> 版本：v1.0 ｜ 日期：2026-08-20 ｜ 基线：HEAD=51dbba6 ｜ 本轮只读：未改代码/未改库/未提交 Git

## 1. 当前可追溯生产事实（逐项核实，非按文档推断）

| 事实源 | 表 | 当前数据 | 可用于追溯的字段 |
|---|---|---|---|
| ProductionOrder | production_order | 4 行 | order_status、actual_start_time、actual_end_time、completed_by、approval_time、finished_quantity、create_by/create_time |
| OperationExecution | production_operation_execution | 9 行 | execution_status(0-6)、actual_start_time、actual_end_time、operator_id/name、qualified/defective_quantity、equipment 快照、process_order |
| Dispatch | production_dispatch | 3 行 | status(0-5)、assign_time、assigned_by_name、team_name、operators 投影、re_dispatch_count |
| DispatchNode | production_dispatch_node | 4 行 | node_status(ACTIVE/DELEGATED/REASSIGNED/RETURNED/COMPLETED/CANCELLED)、assignee_name、assigned_by_name、assigned_at、closed_at、parent_node_id（真实委派链） |
| **DispatchLog** | production_dispatch_log | **6 行** | **action(ASSIGN/DELEGATE/REASSIGN/RETURN/REJECT/START/COMPLETE)、content、operator_name、create_time —— 现成的责任事件日志** |
| WorkReport | production_work_report | 0 行 | report_status(SUBMITTED/CANCELLED)、report_time、cancelled_at、cancelled_by_name、reporter_name、work_start/end_time、qualified/defective_quantity |
| QualityInspection | production_quality_inspection | 0 行 | result(pending/pass/fail)、inspect_time、inspector、inspection_type(IQC/IPQC/FQC/OQC)、execution_id/work_report_id/order_id、pass_qty/fail_qty |
| OperationRecord | production_operation_record | 0 行 | record_type(START/PAUSE/...)、record_time、operator_name —— **未接线（见 §3）** |
| TraceLog | production_trace_log | 0 行 | 旧"追溯码"概念空壳（MATERIAL/ORDER/PRODUCT），无写入方 —— **不接入** |

**关键发现**：`production_dispatch_log` 是 P1 就存在的天然责任事件日志（action + 中文 content + 操作人快照 + 时间），Trace 责任流转段**直接复用，无需新表**。

## 2. 哪些事件能可靠推导（已逐个核对写服务源码）

### ✅ 可靠（有真实业务时间戳 + 状态佐证）

| eventType | 推导来源 | 时间字段 |
|---|---|---|
| ORDER_STARTED | order.status=IN_PROGRESS(6) + actual_start_time | actual_start_time（start 时 now） |
| ORDER_COMPLETED | order.status=COMPLETED(8) + actual_end_time（过 FQC gate 后写入） | actual_end_time |
| EXECUTION_STARTED | execution.status=EXECUTING(2) + actual_start_time | actual_start_time |
| EXECUTION_COMPLETED | execution.status=COMPLETED(4) + actual_end_time（P3-C 同时自动创建 FQC） | actual_end_time |
| DISPATCH_ASSIGNED | dispatch_log action=ASSIGN（或 node.assigned_at） | node.assigned_at |
| DISPATCH_DELEGATED | dispatch_log action=DELEGATE + node 链（父 DELEGATED，子 ACTIVE 且 parent_node_id 指向父） | 父节点 assigned_at |
| DISPATCH_REASSIGNED | dispatch_log action=REASSIGN | create_time |
| DISPATCH_RETURNED | dispatch_log action=RETURN | create_time |
| DISPATCH_REJECTED | dispatch_log action=REJECT（整单退回） | create_time |
| DISPATCH_STARTED / COMPLETED | dispatch_log action=START / COMPLETE（DispatchServiceImpl 有写入） | create_time |
| WORK_REPORT_SUBMITTED | report.status=SUBMITTED + report_time | report_time（submit 时 now） |
| WORK_REPORT_CANCELLED | report.status=CANCELLED + cancelled_at | cancelled_at（cancel 时 now） |
| QUALITY_CREATED | inspection.result=pending | create_time（无独立创建时间字段，创建即插入） |
| QUALITY_PASSED | inspection.result=pass | inspect_time（judge 时 now） |
| QUALITY_FAILED | inspection.result=fail | inspect_time |
| ORDER_CREATED | order.create_time | create_time（审计时间，但订单创建无业务时间字段，可用并标注） |

### ⚠️ 不可靠推导（V1 建议不做，或仅状态展示）

- **EXECUTION_PAUSED / EXECUTION_CANCELLED / EXECUTION_SKIPPED**：写服务只改 execution_status，**没有写入 pause/cancelled 时间戳**（update_time 是审计时间且会被任意更新覆盖，不可作为事件时间）。→ 状态由 execution 列/详情展示，不进时间线。
- **QUALITY_REINSPECTION_CREATED**：reinspect 只是复制创建新 PENDING 记录，**无 parent_inspection_id 关联**。只能启发式推断（同 execution 同 type、前一条 FAIL、后一条 PENDING）。→ 建议启发式标记 + UI 注明"复检(推断)"，或干脆不标（见 §9 拍板项）。

## 3. ProductionOperationRecord 处置

**现状**：表 0 行；CRUD API 全套存在（/production/operation-record 10 个端点）但**无任何业务服务调用**；前端页面存在但空；无 create_time/update_time 审计字段、无 del_flag、无 order_id 直连字段。

**判断**：
- 设计重叠：START/COMPLETE/QUALITY 已被 Execution 状态 + WorkReport + QualityInspection 覆盖；数量事实已由 WorkReport 投影（P2-C recalculate 权威）。
- 独有能力（PAUSE/RESUME/ISSUE/PARAMETER）**从未被写入过**，而 Execution 写服务本身连 pause 时间戳都不记录 → 问题在 P1 写服务缺口，不在 Trace 层。
- 表设计不完整（无审计字段/无 del_flag）。

**结论**：**P4 V1 不启用、不接线、不删除**。若未来需要"过程事件"，更合理的路径是给 execution 表补 pause_at/cancelled_at（改 P1 写服务）或重构 OperationRecord 后再启用。**不为了利用旧表强行接线。**

## 4. 是否需要新 production_trace_event 表

**不需要。** 分析：
- 责任流转段：dispatch_log 现成；
- 其余各段：事实表自带业务时间，TraceQueryService 查询投影即可；
- 唯一"缺失"（pause/cancel 时间戳）属于**写服务未捕获**，不是查询侧能补的——建事件表也补不了历史，反而引入双写一致性负担。

**结论：无新 Trace 事实表。** 现有 production_trace_log 表及旧 Trace 页（views/production/trace）为遗留空壳（0 行、无写入方、概念过时），P4 不接入、不修改。

## 5. TraceEventVO 最终字段（15 个，全部有实际用途）

```
eventType           必填  事件类型（见 §6）
eventTime           必填  业务时间（无业务时间的事件不进时间线）
orderId             必填  所有事件都挂订单
executionId         可空  执行段事件填
dispatchId          可空  责任段事件填
dispatchNodeId      可空  责任段事件填（node 级）
workReportId        可空  报工事件填
qualityInspectionId 可空  质量事件填
actorId             可空  快照 ID（部分来源只有姓名）
actorName           可空  快照姓名（优先，不反查用户表）
title               必填  短标题（如"工序完成"）
description         可空  详情（报工数量/判定结果/派工内容）
status              可空  事件后状态（COMPLETED/pass/SUBMITTED...）
sourceType          必填  ORDER/EXECUTION/DISPATCH/DISPATCH_NODE/DISPATCH_LOG/WORK_REPORT/QUALITY
sourceId            必填  来源表主键（可反查详情）
```

不加 quantity/passQty/failQty 独立字段——数量事实放 description 结构化文本（如"合格 100 / 不良 2"），避免 VO 膨胀。

## 6. eventType 最终集合（建议 16 个）

**执行（2）**：EXECUTION_STARTED、EXECUTION_COMPLETED
**责任流转（6）**：DISPATCH_ASSIGNED、DISPATCH_DELEGATED、DISPATCH_REASSIGNED、DISPATCH_RETURNED、DISPATCH_REJECTED、DISPATCH_COMPLETED
**报工（2）**：WORK_REPORT_SUBMITTED、WORK_REPORT_CANCELLED
**质量（4）**：QUALITY_CREATED、QUALITY_PASSED、QUALITY_FAILED、QUALITY_REINSPECTION_CREATED（启发式，待拍板）
**订单（2）**：ORDER_STARTED、ORDER_COMPLETED（ORDER_CREATED 可选，用 create_time）

砍掉项及理由：EXECUTION_PAUSED/CANCELLED/SKIPPED（无业务时间戳，V1 不做）；DISPATCH_STARTED（与 EXECUTION_STARTED 重复度高，若需可后续加）。

## 7. Timeline 排序规则

- **时间优先**：真实业务时间（actual_start_time / actual_end_time / report_time / cancelled_at / inspect_time / assigned_at / create_time(日志与创建)）。
- **稳定排序**：`ORDER BY eventTime ASC, sourceRank ASC, sourceId ASC`。sourceRank 固定权重：ORDER(1) < EXECUTION(2) < DISPATCH/DISPATCH_LOG(3) < WORK_REPORT(4) < QUALITY(5)，同时间点顺序确定、可复现。
- **升序展示**（时间线自上而下推进），与前端 timeline 语义一致。

## 8. actor 来源（全部用历史快照，不反查 sys_user）

| 段 | actorName 来源 | actorId 来源 |
|---|---|---|
| 责任 | dispatch_node.assignee_name（责任人）、assigned_by_name（操作人）；dispatch_log.operator_name | assignee_id / assigned_by / operator_id |
| 报工 | work_report.reporter_name / cancelled_by_name | reporter_id / cancelled_by |
| 质量 | quality_inspection.inspector（仅姓名，无 ID） | 无（标 null） |
| 订单 | order.completed_by | 无 |
| 执行 | execution.operator_name（**当前历史数据为 NULL**，展示降级为"—"） | operator_id |

✅ 姓名漂移风险：无（全部快照字段）。

## 9. 需要人工拍板的关键决策

1. **EXECUTION_PAUSED/CANCELLED 不进 V1 时间线**（无业务时间戳）——建议确认；未来要的话补 execution 字段。
2. **QUALITY_REINSPECTION_CREATED 用启发式推断**（同 execution 前 FAIL 后 PENDING）——建议做，UI 标注"复检(推断)"；或 V1 不标（FQC 历史列表自然可见多条记录）。
3. **ORDER_CREATED 是否进时间线**——建议进（create_time 标注审计时间），构成"订单创建→开始→执行→报工→质检→完成"完整链。
4. **前端入口**：订单页操作列新增"生产履历"按钮 + 独立 Drawer（不动现有"随工单详情抽屉"）——建议此方案。

## 10. API 设计（V1 单主入口）

```
GET /production/trace/order/{orderId}      → { orderHeader, events[] }
   可选 query：?executionId= / ?category=  （同一接口过滤，不建重复端点）
```

**不做**：execution/{id}、work-report/{id}、quality/{id} 独立 trace 端点——跳转后最终仍是订单时间线，V1 不重复建 API。

**复用现有读模型（不重复造轮子）**：execution.getExecutionsByOrderId、DispatchNodeReadService.getResponsibilityChain、WorkReportReadService.listByExecutionId、QualityInspectionService.listByOrderId/listByExecutionId、DispatchLog（mapper 直查）。新增仅 TraceQueryService 聚合层 + TraceEventVO。

## 11. 前端设计（生产履历 Drawer，非 Trace Center）

- **入口**：Production Order 页操作列 → "生产履历"按钮 → `el-drawer`（宽 820px）
- **结构**：订单头摘要（订单号/产品/状态/数量）→ 分组 Tab（全部 / 生产执行 / 责任流转 / 报工 / 质量）→ `el-timeline`
- **每条事件**：类别图标 + title + description + actorName + 时间 + 状态 tag + source 来源标识（点击可跳现有详情：质检→质量工作台，报工→暂无详情则只展示）
- 事件类别明显区分：生产执行（蓝）/ 责任流转（橙）/ 数量事实（报工条目内嵌数量描述）/ 质量事实（绿/红 tag）
- 不做图谱、拓扑、流程引擎 UI

## 12. 历史兼容

- 老订单缺 DispatchNode/WorkReport/Quality → 时间线自然缺失对应段，**不伪造**；sourceType/sourceId 保证每一条可溯源。
- 历史 execution.process_name 为 **NULL**（当前 9 行数据均为 NULL）→ Trace 展示需 join product_routing 补工序名，或降级显示"工序{process_order}"（现有 VO 已支持 processName 关联查询，复用）。
- 无 dispatch 的 execution → 责任段缺失，正常展示。

## 13. Trace 只读约束（不破坏 P1/P2/P3）

- TraceQueryService **仅 select**，零写入；
- 不修改 DispatchNode / WorkReport / QualityInspection / Execution / Order 任何字段；
- 不重新定义 qualified/finished（直接展示现有值）；
- 不引入第二套状态机（eventType 仅为投影标签，不写状态）。

## 14. P4 V1 明确不做（确认清单）

全局 MES genealogy、原材料批次追溯、供应商批次、成品批次、序列号级追溯、二维码追溯、设备 IoT、SPC、OEE、质量成本、复杂流程图、图数据库、Event Sourcing、CQRS 重构 —— 全部不做，有真实需求再做。

## 15. P4 Work Package 拆分（3 个）

- **P4-A Trace Domain Design**（本轮，本报告）
- **P4-B Trace Read Model & API**：TraceEventVO + TraceQueryService（order 聚合/排序/sourceRank）+ `GET /production/trace/order/{orderId}` + 单测（含空数据/历史兼容用例）
- **P4-C Frontend Timeline & Final Regression**：订单页"生产履历"入口 + Drawer Timeline + 分组 Tab + vue-tsc + 后端全量测试回归

## 16. 发现的真实阻塞（非阻塞性缺口）

1. EXECUTION_PAUSED/CANCELLED 无业务时间戳（写服务未捕获）→ V1 时间线缺暂停/取消段，需拍板接受；
2. QUALITY_REINSPECTION 无父记录关联 → 只能启发式标记；
3. 历史 execution.operator_name / process_name 为 NULL → 展示需降级处理；
4. work_report / quality 当前 0 行 → P4-B 单测需构造内存/独立测试数据，不污染生产库。

---
*本轮只读完成，未修改任何代码/数据库/Git。等待人工评审，未实施 P4-B。*
