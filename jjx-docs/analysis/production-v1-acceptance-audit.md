# JJX Production V1 Acceptance Audit

> 版本：v1.0 ｜ 日期：2026-08-20 ｜ 基线：3da532c + Release Fix（未提交）｜ 严格只读：未改代码/数据/Git
> 验收主对象：PLAN PL2608200001 → WORK_ORDER -01/-02(CANCELLED) / -03(当前)

---

## 0. 当前真实数据快照（只读查询结果）

| 对象 | 状态 |
|---|---|
| PLAN PL2608200001 | planned=1000, remaining=550, completed=0, status=2(APPROVED) |
| WO-...-01 | CANCELLED(9), 550 |
| WO-...-02 | CANCELLED(9), 450 |
| WO-...-03 | status=4(PLANNED), planned=450, **无 dispatch** |
| Execution 7/8/9 | inputQuantity=**450✅**, process_name=**NULL**, 状态 2/0/0 |
| production_dispatch | **0 行** |
| production_dispatch_node | **4 行孤儿**（dispatch_id 1/2/3 无对应 dispatch） |
| production_dispatch_log | 0 行（-03 无） |
| work_report / quality_inspection | 0 行（-03 无） |

---

## 1. P0 — 主链阻塞

### P0-1：派工页无"初始派工"按钮（主链第一环节卡死）

- **现象**：-03 三道工序显示"未派工/待派工"，但操作栏只有"流水"，无"初始派工"；prod_manager 与 admin 均如此
- **真实根因**：`DispatchServiceImpl.page()` 分页循环：
  ```java
  for (DispatchVO vo : vos) {
      if (vo.getDispatchId() != null) { fillCurrentAssignee(vo); }  // ← 只有已有 dispatch 的行才计算
  }
  ```
  无 dispatch 的行（dispatchId=null）**不调用 fillCurrentAssignee → allowedActions 为 null**。前端按钮条件 `v-if="!row.dispatchId && hasAction(row,'ASSIGN')"` 依赖 `row.allowedActions.includes('ASSIGN')`，allowedActions=null → false → 按钮不渲染 → 落入 `v-else` 显示"流水"。
  **后端 allowedActions 对"待派工序"完全没算**（buildAllowedActions 的 cur==null 分支写了 ASSIGN 逻辑，但从未被无 dispatch 行触发）。
- **涉及文件**：`DispatchServiceImpl.java`（page 循环）、`dispatch/index.vue`（按钮条件）
- **正确业务规则**：无 dispatch 的工序行也应生成 allowedActions=["ASSIGN"]（当用户有 dispatch:assign 权限时），前端据此显示"初始派工"
- **建议修复**：page 循环改为 `fillCurrentAssignee(vo)` 无条件调用（方法内部已处理 dispatchId=null 场景）；或对 dispatchId==null 的行单独 `vo.setAllowedActions(buildAllowedActions(vo, null))`
- **是否影响当前验收**：✅ **是——派工环节完全无法操作，主链断**

### P0-2：批量派工"该工序已派工"误报（同一主链问题）

- **现象**：批量派工此前报"该工序已派工，应使用继续派工/改派"
- **真实根因**：dispatch 表当前 0 行，batchAssign 对每个 execution 查 exist dispatch（无）→ 调 `dispatchActionService.assign`；assign 内"已有 ACTIVE Node → 拒绝重复 ASSIGN"检查依赖 `nodeReadService.getCurrentActiveNode`——**孤儿 node（dispatch_id 1/2/3 无对应 dispatch）若通过 fallback 路径被读到，或用户此前选中的是历史 CANCELLED 工单（其 execution 曾绑定 dispatch），会触发误判**。当前 -03 数据下 batchAssign 理论可成功，但**单行初始派工按钮缺失（P0-1）使批量成为唯一入口，且存在误报路径**
- **涉及文件**：`DispatchServiceImpl.batchAssign` / `DispatchActionServiceImpl.assign`
- **正确业务规则**：初始派工只依据"该 execution 是否存在有效 dispatch/node"，不读孤儿数据；batch 与单行必须同一规则（当前同一——但都受 P0-1 与孤儿数据影响）
- **建议修复**：先修 P0-1（恢复单行入口）；清理孤儿 node；batchAssign 成功路径补充幂等日志
- **是否影响当前验收**：✅ 是（与 P0-1 联合阻塞派工）

---

## 2. P1 — 业务错误

### P1-1：CANCELLED 工单仍出现在生产操作页面

- **现象**：-01/-02（CANCELLED）仍出现在派工管理页
- **真实根因**：派工页 SQL `execution LEFT JOIN dispatch` 未过滤 `production_order.order_status`；工序执行页按 order_id 查 execution 也未排除 CANCELLED 工单；批量派工下拉 `getProductionOrderList({orderType:'all'})` 含 PLAN/CANCELLED
- **涉及文件**：`DispatchServiceImpl.page`（SQL）、`ProductionOperationExecutionServiceImpl`（查询）、`dispatch/index.vue`（下拉）、`ProductionOrderServiceImpl.buildQueryWrapper`
- **正确业务规则**：默认生产操作范围 = `order_type='WORK_ORDER' AND order_status NOT IN (CANCELLED)`；COMPLETED/CLOSED 仅历史查询
- **建议修复**：三处统一过滤（派工页 SQL 加 `AND o.order_status <> 9`；execution 查询 join order 过滤；下拉排除 PLAN+CANCELLED）
- **是否影响当前验收**：✅ 是（易选错工单、界面污染）

### P1-2：批量派工下拉可选 PLAN 计划

- **现象**：下拉 orderType='all' 含 PLAN → 选 PLAN 报"该工单没有工序"或误导
- **真实根因**：前端 `orderType:'all'` 透传，后端不过滤 order_type
- **建议修复**：下拉固定 `orderType:'WORK_ORDER'` + 排除 CANCELLED
- **是否影响当前验收**：✅ 是（UX 阻断批量派工）

---

## 3. P2 — 数据/显示问题

### P2-1：计划转工单弹窗仍显示 1000/1000

- **现象**：弹窗显示"计划数量 1000"，可转数量校验用 plannedQuantity（1000）而非 remaining（550）
- **真实根因**：前端 `handleConvertOrder` 用 `order.plannedQuantity`；后端转单校验已用 `remaining_quantity`（Release Fix 已改）→ **后端对、前端展示错**（DTO/VO 有 remainingQuantity 字段，前端未用）
- **涉及文件**：`order/index.vue`（弹窗）
- **正确业务规则**：弹窗应显示/校验 remaining_quantity（可下达数量）
- **建议修复**：前端改用 `order.remainingQuantity`；后端已一致
- **是否影响当前验收**：⚠️ 部分（后端已拦超量，前端显示误导）

### P2-2：随工单/工序名称显示"-"

- **现象**：-03 三道 execution process_name=NULL，页面显示"-"或依赖 major_category
- **真实根因**：`generateOperationExecutions` 从 routing_item 复制 processName，但 **routing_item.process_name 本身为 NULL**（engineering_routing_item 表该行 process_name 空）→ execution.process_name 空；派工页 SQL 有 `COALESCE(process_name, sp.process_name, major_category)` 兜底，随工单/执行页未统一
- **涉及文件**：`ProductionOrderServiceImpl.generateOperationExecutions`、前端展示组件
- **正确业务规则**：工序名唯一来源 = engineering_standard_process（process_id 关联）；execution 冗余字段缺失时展示层统一 COALESCE
- **建议修复**：展示层统一 COALESCE(execution.process_name, sp.process_name, major_category)；数据层可回填（可选）
- **是否影响当前验收**：⚠️ 部分（显示降级，不阻塞流程）

### P2-3：orphan dispatch_node（4 行孤儿）

- **现象**：dispatch_node 有 4 行（dispatch_id 1/2/3），对应 dispatch 不存在；dispatch 表 0 行
- **真实根因**：测试环境清理顺序问题（删 dispatch 未删 node）或旧数据重建；**schema 无外键**（信息_schema 确认无 FK 约束）
- **涉及文件**：`production_dispatch_node` 表（无 FK）
- **正确业务规则**：dispatch_node.dispatch_id 必须引用存在的 dispatch；清理顺序 node→dispatch
- **建议修复**：本轮不删；建议后续加 FK（或至少清理顺序规范）+ 验收环境一次性清理孤儿 node
- **是否影响当前验收**：⚠️ 不阻塞 -03 流程（无 dispatch 无影响），但影响 node 全局查询正确性

### P2-4：Execution 9 process_id=NULL

- **现象**：第三道工序 process_id=NULL、process_name=NULL
- **真实根因**：routing 关联的 standard_process 数据缺失或 routing_item 无对应 process
- **建议修复**：数据层核查 engineering_routing_item/standard_process；展示降级已存在
- **是否影响当前验收**：⚠️ 部分

---

## 4. P3 — UX/清理问题

### P3-1：首检/巡检旧双轨入口仍在执行页
- 现象：execution 页"首检/巡检"按钮走旧 qualityCheck（JSON 双轨），与质检工作台 IPQC/FQC 并存
- 结论：V1 Final Review 已标记 MUST 决策（C. 保留职责收敛入口）；不阻塞验收，但用户会困惑
- **是否影响当前验收**：否（但建议 Fix Pack 内说明）

### P3-2：Quality 判定无独立权限点
- 现象：judge/reinspect 仅需 `production:quality:view`（查看即可判定）
- 结论：SHOULD CLEAN（V1 Review 已列）
- **是否影响当前验收**：否

### P3-3：订单页"查看流水"与"生产履历"按钮并存
- 结论：SHOULD CLEAN；语义不同（操作日志 vs 生产事实），暂保留
- **是否影响当前验收**：否

---

## 5. G/H/I 环节静态分析结论（代码正确性）

| 环节 | 结论 |
|---|---|
| G 报工链 | ✅ canReport = work-report:add 权限 + ACTIVE node assignee 本人（P2-D 统一）；Projection 只写 output/qualified/defective/labor/machine，**不覆盖 inputQuantity**（已测试确认） |
| G 开始链 | ✅ dispatch start 状态机 ASSIGNED/REJECTED→EXECUTING + 联动 execution；权限点 dispatch:start |
| H 完工→FQC | ✅ 最后有效 Execution 完成 → 自动创建 PENDING FQC（P3-C 幂等）；completeOrder 需最新 FQC PASS；报工≠完工、完工≠自动合格 边界正确 |
| I Trace | ✅ TraceQueryService 按 orderId 聚合 Order/Execution/DispatchLog/WorkReport/Quality；ID 链完整（order_id→execution.order_id→dispatch.order_id→node.dispatch_id→report.order_id→quality.order_id）；-03 当前可显示 ORDER_CREATED + EXECUTION_STARTED |

---

## 6. Production V1 Acceptance Fix Pack（按依赖顺序）

| 序 | 任务 | 级别 | 依赖 | 内容 |
|---|---|---|---|---|
| 1 | **FIX-1 派工初始入口** | P0 | - | page 循环无条件 fillCurrentAssignee（或 null 行补 ASSIGN allowedActions）→ 恢复"初始派工"按钮 |
| 2 | **FIX-2 清理孤儿 node** | P0 | FIX-1 | 验收环境删除 4 行孤儿 dispatch_node（无对应 dispatch）；后续 schema 加 FK 或规范清理顺序 |
| 3 | **FIX-3 生产范围统一过滤** | P1 | - | 派工页 SQL / execution 查询 / 批量派工下拉：仅 WORK_ORDER 且非 CANCELLED |
| 4 | **FIX-4 转单弹窗显示 remaining** | P2 | - | 前端弹窗用 remainingQuantity（后端已一致） |
| 5 | **FIX-5 工序名展示统一 COALESCE** | P2 | - | 执行页/随工单展示统一 COALESCE(process_name, sp.process_name, major_category) |
| 6 | **FIX-6 批量派工幂等+防误报** | P1 | FIX-2 | batchAssign 成功路径日志 + 对孤儿数据容错 |

依赖链：FIX-1 → FIX-2 → FIX-6；FIX-3/4/5 可并行。
**验收前置**：FIX-1 + FIX-2 + FIX-3 完成后，重新走通"干净 PLAN → 部分转单 → 派工 → 开工 → 报工 → 完工 → FQC → PASS → 完成 → Trace"全链。

---

## 7. 结论

- **主链代码正确性**：G/H/I 及转单/扣减/释放/编号（Release Fix 已修）均正确
- **当前验收阻塞点集中在派工入口**：P0-1（无初始派工按钮）是唯一硬阻塞，根因是分页投影遗漏无 dispatch 行，属单点代码缺陷（约 3-5 行修复）
- **建议**：按 Fix Pack 顺序修复 6 项后重新验收；修复后应从干净 PLAN 全链走通

*只读审计完成，未修改任何代码/数据/Git。*
