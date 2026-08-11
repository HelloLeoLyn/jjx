# 生产订单 · 库存管理 · 采购管理 详细业务（修复后目标态）

> 版本: v1.0 | 日期: 2026-08-10 | 状态: 目标态设计
> 关联主文档: `flows/order-to-production-target-flow.md`
> 关联任务: dev-20260810-019 ~ 034
> 说明: 本文是主链文档的三大支撑模块详细展开，含主数据、状态机、分步流程、校验规则、联动与修复任务对照

---

## 第一篇 生产订单（工单）管理

### 1.1 工单主数据

**表**: `production_order`

| 字段组 | 字段 | 说明 |
|---|---|---|
| 身份 | orderNo / traceId / parentOrderId / orderType | 单号 / 链路追踪ID / 父计划ID / PLAN或WORK_ORDER |
| 来源 | salesOrderId / salesOrderNo | 关联销售订单 |
| 产品 | productId / Code / Name / Spec / Unit | 生产什么 |
| 工艺 | bomId / bomCode / routingId / routingCode | 创建时快照（追溯关键）|
| 数量 | plannedQuantity / completedQuantity / remainingQuantity | 计划/完成/剩余 |
| 计划 | planStartDate / planEndDate / actualStartTime / actualEndTime | 排产与实绩 |
| 状态 | orderStatus / approvalStatus | 状态机见下 |
| 成本 | materialCost / laborCost / totalCost | 成本核算 |

### 1.2 工单状态机（修复后统一）

```
0 草稿 DRAFT
  → 1 待审核 PENDING_APPROVAL       ← 提交
  → 2 已审核 APPROVED               ← 审批通过
  → 3 已驳回 REJECTED               ← 审批驳回（可改后重提）
  → 4 已计划 PLANNED                ← 计划转工单后
  → 5 待开始 PENDING_START          ← 排产完成，未到开工时间
  → 6 进行中 IN_PROGRESS            ← 开工 start
  → 7 已暂停 PAUSED                 ← 暂停（可恢复）
  → 8 已完成 COMPLETED              ← 完工（修复后：过质检门）
  → 9 已取消 CANCELLED
  → 10 已关闭 CLOSED
  → 11 已超期 OVERDUE               ← 超计划完成时间（定时任务判定）
```

### 1.3 工单创建来源（三种）

| 来源 | 入口 | 数量来源 | 说明 |
|---|---|---|---|
| 手动创建 | `POST /production/order` | 手工填写 | 无上游关联 |
| 计划转工单 | `POST /production/order/convert-plan-to-work-orders` | 子工单自填数量 | **大单拆小单入口**（020 补校验 Σ≤计划量）|
| 销售订单转生产 | 订单"开始生产" | 缺货量（订单量-成品预留）| 自动创建，库存满足的不建 |

### 1.4 开工流程（修复后，含多次领料 033/034）

```
开工 start
  ├─ ① 前置校验：BOM 已审批、工艺路线已审批（已有）
  ├─ ② 生成首张领料单（PICK-{工单号}-{序号}）
  │     - 本次领料数量（默认=剩余需求，可改小）
  │     - 领料预检：按本次数量查可用量（DEV-651 改造：不足允许部分领）
  │     - 替代料兜底（034）：首选料不足→按 substitute_json 优先级试替代料
  │     - 替代料换算：需求 × (替代单价/原单价)，留痕记录
  ├─ ③ 领料单确认 confirm → FIFO 扣库存 → 刷新汇总 → 释放原料占用
  ├─ ④ 工单置 IN_PROGRESS，激活首道工序
  └─ ⑤ 生产过程中【追加领料】随时可发起（回到②，累计领料≤BOM需求）
```

**校验规则**:
- 累计领料量 ≤ BOM 需求量（Σ用量×数量×(1+损耗率)），超量拒绝
- 替代料也缺 → 提示缺口 + 人工确认（强制领/改期）
- 领料状态 materialStatus：0未领 / 1部分领（新增） / 2已领

### 1.5 生产执行与报工

```
工序执行（production_operation_execution）
  ├─ 按工艺路线生成工序执行记录（开工时）
  ├─ 每工序：投料 → 报工（产出/合格/不良数量）
  ├─ 首检/巡检：PASS 继续 / FAIL 自动暂停工序（已有）
  └─ 完成工序 → 汇总合格数量 → 更新工单 completedQuantity / remainingQuantity
```

### 1.6 完工质检门（031 修复后）

```
完工 complete
  ├─ ① 校验（全部满足）：
  │     - 工单状态 = IN_PROGRESS
  │     - 全部工序执行记录 = COMPLETED
  │     - FQC 质检单已生成且质检通过
  │     - completedQuantity ≥ plannedQuantity
  ├─ ② 自动创建 FQC 质检单（若无）
  ├─ ③ 自动生成成品入库单 FINISH-{工单号}（DEV-579）
  ├─ ④ 入库单确认 → 成品库存 + （失败不再静默：置"待入库"可重试）
  └─ ⑤ 订单进度回写 produced_quantity += 入库量（020）
```

**异常**：质检 FAIL → 不可完工，转返工/报废；入库失败 → 工单"已完工待入库"状态可重试。

### 1.7 成本核算（032 修复后）

```
工单成本 = 材料成本 + 人工成本
  ├─ 材料成本 = Σ(领料明细 × 单价)      ← 按实际领料核算（修复后）
  ├─ 人工成本 = Σ(工序报工工时 × 工价)   ← 按报工核算（修复后）
  ├─ BOM标准成本 = Σ(用量 × 物料标准单价) ← 修正原"只加数量"错误（032）
  └─ 差异分析 = 实际单位成本 vs 标准成本（costDiff）
```

### 1.8 关闭与追溯

- 工单 COMPLETED/CANCELLED 后可 CLOSED 关闭
- 追溯：traceId 贯穿 销售订单→工单→领料/入库/出库单据（DEV-568）
- 批次号统一规则（029）：库存批次号 = 来源单据批次号，单据明细与库存批次一一对应

---

## 第二篇 库存管理

### 2.1 主数据

| 数据 | 表 | 关键字段 | 说明 |
|---|---|---|---|
| 仓库 | inventory_warehouse | warehouseCode(WH)/Name/Type(raw原料/finished成品/semi半成品)/status | 原材料/成品/半成品分仓 |
| 库位 | inventory_storage_location | locationCode/Type(normal/quality待检/frozen冻结)/capacity | 库位级管理 |
| 物料分类 | inventory_material_category | tree | 分类树 |
| 物料 | inventory_material | materialCode(MAT)/Type(R原料/F成品/P半成品/S辅料)/batchControl/shelfLife/safeStock/maxStock/reorderPoint/standardPrice/leadTime/supplierId/defaultWarehouseId | 🔑 主数据，预警参数在此 |

### 2.2 库存结构（三级）

```
inventory_stock（汇总，按物料一行）
  ├─ total_quantity 总量
  ├─ total_reserved 预留量
  └─ available_quantity 可用量 = 总量 - 预留（生成列，全链路唯一口径 027）

inventory_stock_item（批次，按 物料+仓库+库位+批次 一行）
  ├─ quantity 批次数量
  ├─ reserved_quantity 批次预留
  ├─ batch_no / production_date / expiry_date
  ├─ unit_cost 批次单价（移动加权）
  └─ last_inbound_time / last_outbound_time（FIFO/呆滞判断依据）

inventory_transaction（流水，每次出入库一行，只增不改）
  ├─ transaction_type: inbound/outbound/transfer_in/transfer_out/adjust
  ├─ source_type/source_id/source_no（来源单据）
  ├─ batch_no / quantity / unit_cost / before/after
  └─ operator / time（全链路追溯基础）
```

### 2.3 入库业务（四类 + 一类补充）

| 类型 | 来源 | 流程 | 修复点 |
|---|---|---|---|
| 采购入库 | 采购到货 | 到货登记→检验PASS→加库存→自动生成入库单 | 超收校验（030）|
| 生产入库 | 工单完工 | 完工→自动建入库单→confirm 加库存 | 失败不静默（031）|
| 退货入库 | 销售退货/采购退货 | 退货单→入库 | — |
| 调拨入库 | 调拨单 confirmIn | 调入仓库加库存 | — |
| 盘盈入库 | 盘点盈亏处理 | 差异→adjust 入库单 | — |

**入库单状态机**（修复后统一）:
```
0 草稿 → 1 待审批 → 2 已批准 → 10 已完成(confirm 加库存) 
       → 3 已驳回 → 9 已取消
```
关键：**库存变动只发生在 confirm**（DEV-651 单路径），approve 只做状态流转。

### 2.4 出库业务

| 类型 | 来源 | 流程 | 修复点 |
|---|---|---|---|
| 生产领料 | 工单领料单 | 领料单→confirm→FIFO扣减 | 多次领料（033）|
| 销售出库 | 销售发货 | 发货单→出库单→confirm 扣库存 | **必扣库存（021）**|
| 调拨出库 | 调拨单 confirmOut | 调出仓库减库存 | — |
| 盘亏出库 | 盘点差异 | adjust 出库单 | — |

**出库单状态机**：同入库单（草稿→待审批→已批准→已完成，confirm 扣库存）。

**销售出库修复要点（021/020/025）**:
- `createFromSales` 去掉 SHIP-{orderNo} 唯一限制，允许分批多张（020）
- 出库单号 `SHIP-{orderNo}-{序号}`
- confirm 必被调用（修复 InventoryEventBridge 断链 022/026），库存真实扣减
- 扣减前先释放该订单成品预留（已有 DEV-580），再 FIFO 扣减
- confirm 后回写订单 shipped_quantity（020）

### 2.5 调拨

```
创建调拨单 → 审批 → confirmOut（调出-库存）→ confirmIn（调入+库存，新批次）
```
- 双向确认 + 乐观锁（已有）
- 批次随单流转：调出批次号带入调入批次（029 统一规则）

### 2.6 盘点

```
创建盘点单 → 开始 → 录入实盘数 → 确认结果（差异=实盘-系统）
→ 盈亏处理（盘盈→adjust入库 / 盘亏→adjust出库）→ 关闭
```
- 差异金额 = 差异量 × 批次单价（已有）
- 修复点：盘点期间库存变动处理策略需明确（锁盘/差异重算）

### 2.7 库存预警（修复后口径统一）

| 预警类型 | 判断口径 | 修复点 |
|---|---|---|
| 安全库存 safe_stock | **available_quantity < safe_stock** | 027（原用 total）|
| 最高库存 max_stock | available_quantity > max_stock | 同口径 |
| 临期 expiry | earliest_expiry ≤ 30天 | 已有 |
| 呆滞 obsolete | 批次 180天未出库 | 028（物料级→批次级可选）|
| 订单缺料 order_shortage | 单订单需求 vs 可用+在途 | 已有 |
| 需求汇总缺料 demand_shortage | 在途订单合计需求 vs 可用+在途 | 019 新增 |

预警表 `inventory_alert_log`：alert_type / level(info/warning/urgent) / material / current_stock / suggestion / status(0新增/1已读/2已处理) / order_no。

---

## 第三篇 采购管理

### 3.1 供应商

- 表 `purchase_supplier`：供应商档案、联系人、结算信息、状态
- 物料档案可关联 defaultSupplier（供应来源）

### 3.2 采购订单

**表**: `purchase_order` + `purchase_order_item`

| 字段组 | 字段 |
|---|---|
| 主表 | orderNo(PCO) / supplierId / orderDate / expectedDeliveryDate / orderAmount / tax / totalAmount / approvalStatus / receiptStatus / paymentStatus / paidAmount |
| 明细 | materialId / quantity / receivedQuantity / unitPrice / amount / receiptStatus / inspectionResult |

**审批状态机**:
```
1 草稿 → 3 待审批（提交）→ 4 已批准（通过）→ 收货流程
       → 5 已拒绝（驳回，可改后重提）
2 已取消（草稿/待审批/已批准 可取消）
```

**收货状态机**:
```
0 待收货 → 1 部分收货 → 2 已收货
```

**创建来源**（修复后补全）:
1. 手动创建
2. 缺料预警联动（order_shortage / demand_shortage → 建议补货 → 一键生成采购单）
3. 采购建议/再订货点（reorder_point 触发）

### 3.3 到货收货流程（修复后）

```
到货登记 receive
  ├─ ① 校验：订单状态 ∈ (待审批, 已批准)
  ├─ ② 超收校验（030 新增）：
  │     receivedQuantity > 0
  │     且 累计收货 ≤ 明细数量 → 否则拒绝（RECEIVE_QUANTITY_EXCEEDS）
  ├─ ③ 检验：PASS 或空 → 入良品库存；FAIL → 不入库（已有）
  ├─ ④ 加库存：FIFO 新批次（批次号 PO-{单号}-{序号}，029 统一）
  ├─ ⑤ 自动生成入库单（DEV-624，幂等：PO-{单号} 已存在跳过）
  └─ ⑥ 更新订单收货状态（部分/完成）
```

### 3.4 在途口径（023 修复后）

```
在途量 = Σ(采购单未收货量)
      仅统计 approval_status ∈ (3 待审批, 4 已批准) 的单
```
- **排除草稿(1)、已取消(2)、已拒绝(5)**（修复点）
- 取消/拒绝采购单时同步刷新在途（防止虚高掩盖缺料）
- 使用方：齐套检查、汇总缺料检查、采购建议（口径一致）

### 3.5 采购发票与付款

```
发票登记（invoice）→ 付款登记（payment）
  ├─ 付款金额 ≤ 订单总金额（校验）
  ├─ 已付金额累计，paymentStatus 推进
  └─ 发票/付款与订单关联，财务对账基础
```

### 3.6 采购与缺料联动（修复后）

```
缺料预警（demand_shortage / order_shortage）
  ├─ 建议补货量 = 缺口量（已扣在途）
  ├─ 一键转采购单：物料、数量、建议交期
  ├─ 采购单批准 → 在途量生效 → 缺料预警自动消解（重算）
  └─ 收货入库 → 库存增加 → 可用量恢复 → 领料可继续
```

---

## 附录 A：三模块联动全景（修复后）

```
销售订单确认
  ├─ 成品预留（库存模块：reserved +）
  ├─ 原料占用（库存模块：原料可用量 -）
  └─ 全局缺料检查（019）→ 缺料预警 → 采购建议（采购模块）

生产计划（PLAN）
  └─ 转工单（生产模块）→ 多次领料（库存模块出库，033/034）
       ├─ 领料预检（可用量）→ 不足 → 缺料预警 → 采购（采购模块）
       └─ FIFO 扣减 → 流水 → 批次追溯

工单完工（生产模块）
  ├─ 质检门（031）→ 成品入库（库存模块）
  ├─ 入库 confirm → 可用量 + → 订单 produced_quantity 回写（020）
  └─ 成本核算（032）：领料成本 + 报工人工

销售发货（销售模块）
  ├─ 发货单 → 出库单（库存模块，分批 020）
  ├─ confirm 必扣库存（021）→ 流水
  ├─ 订单 shipped_quantity 回写 → 全部交付自动 COMPLETED（020/025）
  └─ 状态机统一（024）
```

## 附录 B：修复任务 → 模块对照

| 任务 | 模块 | 内容 |
|---|---|---|
| 019 | 库存+销售 | 需求汇总缺料检查 |
| 020 | 销售+库存 | 分批交付/进度回写 |
| 021 | 库存 | 销售出库必扣库存 |
| 022 | 库存+销售 | 发货事件触发补全 |
| 023 | 采购 | 在途排除取消单 |
| 024 | 全局 | 状态枚举统一 |
| 025 | 销售+库存 | SHIPPED 入口 |
| 026 | 前端 | 发货入口 |
| 027 | 库存 | 预警用可用量 |
| 029 | 库存+采购+生产 | 批次号统一 |
| 030 | 采购 | 收货超收校验 |
| 031 | 生产 | 完工质检门 |
| 032 | 生产+库存 | BOM成本算法 |
| 033 | 生产+库存 | 多次领料 |
| 034 | 生产+库存 | 材料替换 |
