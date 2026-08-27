# 🏭 生产模块完整业务分析

> 基于实际代码逐条梳理，颗粒度到每个端点、状态跳转、校验规则、交叉联动。
>
> 代码路径: `jjx-server/src/main/java/com/jjx/production/`
>
> 最后更新: 2026-08-01

---

## 一、整体模块结构（8 个 Controller）

| Controller | 路径 | 职责 |
|-----------|------|------|
| `ProductionOrderController` | `/production/order` | 生产工单（排产/开工/完工/取消）|
| `ProductionOperationExecutionController` | `/production/operation-execution` | 工序执行（开始/暂停/完成/取消）|
| `ProductionOperationRecordController` | `/production/operation-record` | 工序报工记录 |
| `QualityInspectionController` | `/production/quality` | 完工质检 |
| `ProductionCostController` | `/production/cost` | 生产成本 |
| `ProductionTraceController` | `/production/trace` | 产品追溯 |
| `ProductionReportController` | `/production/report` | 生产报表 |
| `EquipmentController` | `/production/equipment` | 设备管理 |

---

## 二、A — 生产工单（ProductionOrder）🔑 核心

### 数据表

`production_order`

**字段**: orderId, traceId, orderNo, orderType(WORK_ORDER), parentOrderId, salesOrderId/No, productId/Code/Name/Spec/Unit, **bomId/bomCode, routingId/routingCode**（追溯关键）, plannedQuantity, completedQuantity, remainingQuantity, planStartDate/EndDate, actualStartTime/EndTime, orderStatus, approvalStatus, approverId/Name/Time/Remark, priority, departmentId/Name, materialCost/laborCost/totalCost

### 端点清单

```
GET    /order/page | /list | /{orderId} | /code/{orderCode} | /product/{pid} | /routing/{rid}
GET    /order/statistics | /schedule/gantt
DELETE /order/{orderId} | /batch
PUT    /order/{orderId}/start        — 开始生产
PUT    /order/{orderId}/pause        — 暂停
PUT    /order/{orderId}/complete     — 完工 ⚠️（不触发质检）
PUT    /order/{orderId}/cancel       — 取消
PUT    /order/{orderId}/close        — 关闭
PUT    /order/status | /batch-status — 批量状态
POST   /order/copy                   — 复制工单
POST   /order/import | /export       — 导入导出
POST   /order/convert-plan-to-work-orders — 计划转工单
```

### 状态机（OrderStatusEnum）

```
0 草稿(DRAFT) → 1 待排产(PENDING_SCHEDULE)
1 待排产 → 2 已排产(SCHEDULED)
2 已排产 → 3 生产中(IN_PROGRESS)   ← start
3 生产中 → 4 已完成(COMPLETED)     ← complete ⚠️
         → 5 已取消(CANCELLED)
         → 6 已暂停(PAUSED)
         → 7 已关闭(CLOSED)
```

### 工单创建来源
1. **手动创建**（createOrder + createDTO）
2. **计划转工单**（convert-plan-to-work-orders，按生产计划生成 WO-{计划号}-{序号}）
3. **销售订单提交生产**（DEV-470 已修：`/sales/order/create-instances/{id}` 遍历订单明细创建工单，带 BOM/路线校验）

### 关键联动（⚠️ 重要）

| 动作 | 做了什么 | 缺口 |
|---|---|---|
| complete 完工 | 状态→已完成 | **❌ 不触发质检**（DEV-473 待修）|
| start 开工 | 状态→生产中 | 首道工序激活 |
| 创建时 | 记录 bomId/routingId 快照 | ✅（追溯基础）|

---

## 三、B — 工序执行（OperationExecution）🔑 核心

### 端点清单

```
GET    /operation-execution/page | /list | /{id} | /order/{orderId} | /process/{processId} | /statistics
DELETE /operation-execution/{id} | /batch
PUT    /operation-execution/{id}/start     — 开始工序
PUT    /operation-execution/{id}/pause     — 暂停工序
PUT    /operation-execution/{id}/complete  — 完成工序（✅ 激活下一道）
PUT    /operation-execution/{id}/cancel    — 取消工序
POST   /operation-execution/import | /export
```

### 工序执行状态
```
待执行 → 执行中(start) → 已完成(complete)
                       → 已暂停(pause) → 执行中(恢复)
                       → 已取消(cancel)
```

### 核心逻辑
- **顺序流转**：完成当前工序 → 自动激活下一道（processOrder）
- 每道工序关联：routingId + processId + processOrder
- 暂停/取消需填原因

---

## 四、C — 工序报工记录（OperationRecord）

### 端点清单

```
GET    /operation-record/page | /list | /{id} | /execution/{execId} | /order/{orderId} | /process/{pid} | /statistics
DELETE /operation-record/{id} | /batch
POST   /operation-record/import | /export
```

### 字段
recordId, executionId, orderId, processId, operatorId/Name, reportTime, completedQty, scrapQty, workHours, machineHours, remark

### 用途
- 工时统计（工单/工序/时间段）
- 成本核算（laborCost 依据）
- 质量追溯（谁做的、什么时间）

---

## 五、D — 完工质检（QualityInspection）

### 端点清单

```
GET    /quality/page | /{id} | /statistics
DELETE /quality/{id}
```

### ⚠️ 现状
- 质检单 CRUD 存在（page/详情/删除/统计）
- **无"创建质检单"接口**（只有查询）——质检单从哪来？未打通
- **完工不自动触发质检**（DEV-473）
- 测试工作台 TC-57~60（创建质检单/检验PASS/FAIL/复检）基本未实现

---

## 六、E — 其他（成本/追溯/报表/设备）

### 生产成本（/production/cost）
```
GET    /cost/list      — 成本列表
GET    /cost/summary   — 成本汇总
```

### 产品追溯（/production/trace）
```
GET    /trace/page
GET    /trace/forward/{traceCode}   — 正向追溯（原料→成品）
GET    /trace/backward/{traceCode}  — 反向追溯（成品→原料）
```
**追溯基础**：traceId 贯穿 销售订单→工单→工序→批次

### 生产报表（/production/report）
```
GET    /report/output      — 产量报表
GET    /report/efficiency   — 效率报表
GET    /report/quality      — 质量报表
```

### 设备（/production/equipment）
```
GET    /equipment/page | /list | /{id}
DELETE /equipment/{id}
```
⚠️ 只有查询+删除，无维护记录功能（TC-79/80 缺口）

---

## 七、状态流转总图

```
销售订单(已确认) ──提交生产──► 生产工单(待排产)   ← DEV-470 打通
                                    │ 排产
                                    ▼
                               (已排产)
                                    │ 开工
                                    ▼
                               (生产中)
                                    │ 工序执行: 待执行→执行中→已完成(逐道激活)
                                    │ 暂停/取消/恢复
                                    ▼
                               (已完成) ──► 质检 ⚠️ 未联动(DEV-473)
                                    │
                                    ▼
                            入库(成品) ← create-from-production
```

## 八、与其他模块联动

| 联动 | 方式 | 状态 |
|---|---|---|
| 销售→工单 | create-instances（DEV-470 已修）| ✅ |
| 工单→领料 | create-from-production 出库单 | ⚠️ 物料清单生成待补（DEV-472）|
| 完工→质检 | — | ❌ 未联动（DEV-473）|
| 完工→入库 | create-from-production 入库单 | ✅ 手动 |
| 生产→事件 | production 事件仅 1 条（覆盖严重不足）| ⚠️ |

## 九、已知问题

1. **完工→质检断链**（DEV-473）：completeOrder 不创建质检单
2. **质检单无创建接口**：QualityInspection 只有查询，TC-57~60 未实现
3. **生产事件覆盖不足**：sys_event_config 里 production 模块仅 1 条事件（vs 销售32/库存44）
4. **设备管理单薄**：无维护记录（TC-79/80）
