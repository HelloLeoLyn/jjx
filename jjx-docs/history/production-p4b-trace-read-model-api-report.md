# JJX Production P4-B Trace Read Model & API Implementation Report

> 版本：v1.0 ｜ 日期：2026-08-20 ｜ 分支：dev ｜ 基线：51dbba6 ｜ 未提交 Git

## 1. 新增文件（7 个，全部未提交）

| 文件 | 说明 |
|---|---|
| `jjx-server/.../domain/vo/TraceEventVO.java` | 统一时间线事件 VO（15 字段，P4-A 定稿最小实现） |
| `jjx-server/.../domain/vo/OrderTraceVO.java` | 订单履历返回体（orderHeader + events） |
| `jjx-server/.../enums/TraceEventType.java` | eventType 常量集合（16 个，V1 定稿） |
| `jjx-server/.../service/TraceQueryService.java` | 只读查询接口（getOrderTrace x2） |
| `jjx-server/.../service/impl/TraceQueryServiceImpl.java` | 只读聚合实现 |
| `jjx-server/.../controller/TraceQueryController.java` | `GET /production/trace/order/{orderId}` |
| `jjx-server/src/test/java/com/jjx/production/TraceQueryServiceTest.java` | 10 个测试用例 |

修改文件：**无**（未触碰任何 P1/P2/P3 业务代码）。

## 2. TraceEventVO（15 字段，全有实际用途）

`eventType / eventTime / orderId / executionId / dispatchId / dispatchNodeId / workReportId / qualityInspectionId / actorId / actorName / title / description / status / sourceType / sourceId`

数量、质量结果等展示信息放入 description（如"合格 100 / 不良 2"），未增加冗余业务字段。

## 3. 最终 eventType（16 个，全部可靠推导）

- 订单：ORDER_CREATED、ORDER_STARTED、ORDER_COMPLETED
- 执行：EXECUTION_STARTED、EXECUTION_COMPLETED
- 责任：DISPATCH_ASSIGNED、DISPATCH_DELEGATED、DISPATCH_REASSIGNED、DISPATCH_RETURNED、DISPATCH_REJECTED、DISPATCH_COMPLETED
- 报工：WORK_REPORT_SUBMITTED、WORK_REPORT_CANCELLED
- 质量：QUALITY_CREATED、QUALITY_PASSED、QUALITY_FAILED

未实现（按要求）：EXECUTION_PAUSED/CANCELLED/SKIPPED（无可靠业务时间）、QUALITY_REINSPECTION_CREATED（无父子关联）。复检自然呈现为 FAILED → 新 CREATED → PASSED。

## 4. TraceQueryService 聚合方式

`getOrderTrace(orderId, category?, executionId?)` → 五段只读聚合 → 过滤 → 稳定排序：

1. **Order 段**：实体 `ProductionOrder`（orderMapper.selectById）——createBy/completedBy 快照 VO 未映射，故用实体；orderHeader 复用 `orderService.getOrderById`（VO 完整展示）
2. **Execution 段**：复用 `executionService.getExecutionsByOrderId`（现有 XML 关联查询）
3. **责任段**：`dispatch_log`（orderId 查）→ action 映射 eventType；`START` 无对应类型直接跳过；`dispatchNodeId=null`（log 无 node 关联）
4. **报工段**：`workReportMapper`（orderId 查）——SUBMITTED 用 reportTime；CANCELLED 用 cancelledAt（非空才生成）
5. **质量段**：复用 `qualityInspectionService.listByOrderId`（P3-B 读模型）——CREATED 用 createTime；PASS/FAIL 用 inspectTime

**排序**：`eventTime ASC → sourceRank ASC → sourceId ASC`（ORDER=1 < EXECUTION=2 < DISPATCH=3 < WORK_REPORT=4 < QUALITY=5），同时间稳定可复现。

## 5. actor / 时间 / 工序名处理

- **actor**：全部历史快照——dispatch_log.operator_name、work_report.reporter_name/cancelled_by_name、quality.inspector、order.create_by/completed_by、execution.operator_name。不反查 sys_user，无姓名漂移；无快照返回 null
- **时间**：业务时间优先（actualStartTime/actualEndTime/reportTime/cancelledAt/inspectTime/assignedAt→log.createTime）；ORDER_CREATED 用 createTime（审计时间，P4-A 拍板标注）；**禁止 updateTime 推断任何事件**
- **工序名**：优先 execution VO 的 processName；NULL 降级 `工序 {processOrder}`（未改历史数据）

## 6. API

```
GET /production/trace/order/{orderId}
  可选参数：category（ORDER/EXECUTION/DISPATCH/WORK_REPORT/QUALITY）、executionId
  返回：{ orderHeader: ProductionOrderVO, events: TraceEventVO[] }
```

未建 execution/work-report/quality 独立 trace 端点（V1 统一订单履历）。权限 `production:order:view`。

## 7. 历史兼容

- 老订单缺 WorkReport/Quality/Dispatch → 时间线自然缺失，不补造（真实验证：order 2 无报工/质检，仅 ORDER_CREATED + EXECUTION_STARTED + 6 条责任事件）
- 完全无子事实 → 至少显示 ORDER_CREATED
- 历史 execution process_name 全 NULL → 全部降级"工序 N"正常展示

## 8. 数据库结构

❌ **未新增任何表/字段/migration**（未碰 production_trace_event / trace_log / operation_record）。

## 9. 业务数据

❌ **未修改任何业务数据**。TraceQueryServiceImpl 仅 SELECT；测试中 verify 断言所有 insert/update/delete 零调用。

## 10. 测试 / compile

- `mvn test`（Java 21）：**149 run, 0 failures, 0 errors, 3 skipped — BUILD SUCCESS**（新增 10 用例）
- 覆盖：完整聚合、时间排序、同时间 sourceRank 稳定排序、空 WorkReport/Quality、无 Dispatch、processName 降级、SUBMITTED/CANCELLED、CREATED/PASS/FAIL、DispatchLog 动作映射（含 START 跳过）、category/executionId 过滤、只读零写入
- 真实 DB 只读验证（order 2）：8 事件推导正确，历史兼容路径走通

## 11. 是否满足进入 P4-C

✅ **满足**。Read Model + API 已就绪且全绿，P4-C 可基于 `GET /production/trace/order/{orderId}` 实现前端履历 Drawer。

## 12. 真正发现的风险

1. **orderHeader 与事件源不一致隐患**：VO 未映射 completedBy/finishedQuantity 等字段，orderHeader 展示完整、事件构建走实体——两套数据源需保持同步（当前正确，未来改 VO 映射时注意）
2. **dispatch_log 无 node_id**：责任事件 dispatchNodeId 恒为 null，若 P4-C 想从事件跳责任链详情，需靠 dispatchId 二次查询
3. **QUALITY_CREATED 用 createTime（审计）**：创建质检无独立业务时间字段，同秒批量创建时靠 sourceId 排序，语义可接受
4. **processName 缺失是普遍现象**（现有 9 条 execution 全 NULL）：履历展示以"工序 N"为主，工序名美观度受限（属历史数据问题，非 Trace 缺陷）

---
未提交 Git，未实施 P4-C，等待人工验收。
