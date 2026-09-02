# JJX ERP 生产模块实证差距分析报告

- 报告日期：2026-09-02
- 方法：数据库实查 + 代码实扫 + 事件核对
- 红线：旧派工模型（Dispatch/DispatchNode）已废弃（2026-08-21 定），新基线 = ProductionOperationExecution → ProductionTaskNode 树 → WorkReport；旧代码缺失不算缺口

---

## 1. 现状盘点（实证）

### 1.1 表清单与行数

| 表 | 行数 | 判定 |
|---|---|---|
| production_tooling | 7274 | 真实数据（模具/工装主数据量大） |
| production_operation_execution | 4 | 测试数据 |
| production_task | 4 | 测试数据 |
| production_operation_record | 3 | 测试数据 |
| production_work_report | 2 | 测试数据 |
| production_order | 1 | 测试数据 |
| production_equipment | 2 | 测试数据 |
| production_quality_inspection(-item) | 0 / 0 | 空转 |
| production_task_event | 0 | 空转（事件流水） |
| production_trace_log | 0 | 空转（read-model，无写入方=死表） |
| quality_template_registry / print_log | 100 / 0 | 模板 100 全量（2026-09-02 已迁文档管理菜单 316） |

### 1.2 后端 Controller（production 包 14 个）

ProductionOrderController(28 端点，含 schedule/gantt 排程、convert-plan-to-work-orders)/ProductionTaskController(任务树 15 端点)/ProductionOperationExecutionController(19)/WorkReportController/QualityInspectionController(11)/Equipment/Tooling/Cost/LabelPrint/OperationRecord/Trace/QualityTemplateRegistry/Report/ProductionTraceQuery。CRUD+审批+状态机全覆盖。completeExecution/auto-FQC 已实现（08-27 TODO 断点已修，P1-P4 验收）。

### 1.3 前端页面与菜单

生产菜单(43)：生产订单/派工/工序执行/质检/追溯/设备/报工查询/报工审批/代报工。**派工管理(261) component='production/dispatch/index'——旧派工模型页面**：红线说明旧模型废弃，但菜单仍指向旧页面——查 views/production/dispatch 是否存在；若不存在=死菜单（有入口无页面，需清理或指新 TaskNode 页）；若存在=废弃功能残留入口。质量记录模板(315)已迁文档管理(316)（2026-09-02）。质检报告(265)/质检管理(264) 有页 ✅。

### 1.4 事件配置（production.* 9 条）

配置：production.completed/started/work-report.submitted/approved/rejected + 设备/工装类无。
代码触发（@Event 注解 1 条 + 手动 fire 核实）：
- production.started：@Event 有
- production.completed：**已核实通**——ProductionOrderServiceImpl:435 completeOrder 内 `eventPublisher.fire("production.completed")`（手动 fire 不走 @Event 注解，grep @Event 查不到属正常），InventoryEventBridge.onProductionCompleted @EventListener 条件匹配可收到 → 完工入库链路通
- work-report.submitted/approved/rejected ×3：配置启用，代码**无 fire 方**（grep fire 仅 :435 一处）→ 空转（1246 已登记 P2）
- 移动端扫码相关（暂停/完工/质检判定 072/073）：无事件配置——移动操作不产生通知

### 1.5 库代码一致性

循环依赖已修（3e3bb94）；无脱节。

---

## 2. 业务闭环验证

| 环节 | 判定 |
|---|---|
| 计划→工单（plan→work order） | ✅通（含 gantt 排程） |
| 工单启动→回写销售订单 | ✅通（ProductionOrderServiceImpl:295 回写 IN_PRODUCTION） |
| TaskNode 树派工→报工→审批 | ✅通（P4.5 规则：首派仅生产管理者、已派仅 assignee 可改派） |
| 工序执行（start/pause/quality-check/complete） | ✅通（completeExecution 已实现+auto-FQC 接通） |
| 完工→入库联动 | ✅通（completeOrder:435 手动 fire production.completed → 桥接器自动入库） |
| 质检（IPQC/FQC/首件） | ✅通（QualityInspection 11 端点+judge/reinspect） |
| 追溯 | ✅通（P4 生产履历验收） |
| 报工通知 | ❌断（1246 已登记：3 条配置空转） |

## 3. 与行业基准对照

覆盖：工单✅ 排程✅ 派工✅ 报工✅ 质检✅ 追溯✅ 设备/工装✅。
缺失/薄弱：
- 报工单号/工票打印（1247 已登记 P3：无 report_no）
- 报工通知闭环（1246）
- 生产日报/首件记录打印已实现（quality-print 5 张 data 联动）
- 报工数量精度（work_report DECIMAL(18,4) vs task DECIMAL(14,2)）08-27 遗留——完工门禁精确相等可能卡死，需复核是否修复

## 4. 缺口与死代码清单

| 类型 | 项 | 证据 | 影响 | 建议 |
|---|---|---|---|---|
| 空转事件 | work-report ×3 | 1246 已登记，grep fire 无报工调用 | 中 | 接 @Event |
| 死菜单 | 派工管理 261 指向旧模型页 | component production/dispatch/index | 中 | 清理或指向任务树页 |
| 死表 | production_trace_log | 无写入方（P4 用事件流非此表） | 低 | 冻结/删除 |
| 精度遗留 | work_report vs task DECIMAL | 08-27 报告 | 中 | 复核门禁是否已容差 |
| 数据缺口 | 移动端操作无事件通知 | 072/073 无事件配置 | 低 | 随 064 一起 |

## 5. 优先级结论

| 优先级 | 事项 | 理由 |
|---|---|---|
| P1 | 1246 报工事件接入 | 提交/审批无通知（唯一断链点） |
| P2 | 派工死菜单清理 | 误导入口 |
| P2 | 精度遗留复核 | 完工门禁可能卡死 |
| P3 | 1247 报工单号/打印 | 工票凭据 |
