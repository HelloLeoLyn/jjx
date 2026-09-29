# 库存「待检」载体三选一：收货 → 待检库存对齐业内（dev-20260929-005）

> 日期：2026-09-29 ｜ 任务码：dev-20260929-005（P1，登记人 dahuang）｜ 起草：大黄(OpenClaw)
> 类型：**方案对比 + 待拍板**（未改代码、未改数据；只读核查）
> 状态：用户 2026-09-29 拍板「**先定方案③**；方案①/② 后续再议；**本轮不做代码实施**」
> 关联：`design/iqc-pages-industry-alignment-dev-20260924-024.md`（页面层对齐业内）｜`history/iqc-disposition-truth-rootfix-dev-20260929-003.md`（IQC 处置真源收敛）｜`design/stock-ledger-receiving-issuing-balance-dev-20260923-016.md`｜`design/purchase-receiving-migration-dev-20260923-003.md`

---

## 1. 结论先行

- **现状**：JJX 是「收货 ≠ 入（可用）库」——收货只更新采购明细并生成一张待确认入库单，**不写库存、不写流水**；检验在独立的隔离台账 `inventory_iqc_quarantine` 里做；仓库「确认入库」时才写第一笔流水、加可用库存。
- **业内**：三步式——收货（GR）**即写库存 + 流水**，但货先落「**待检库存**」格（不可用）；检验判定后，合格部分转「可用」，不合格走退货/报废/让步。每一格挪动都留一条流水。
- **关键更正（用户 11:03 指出）**：方案 B/三步式与「收货≠入库」的 2026-08-11 定稿**不冲突**。该定稿的本意是「收货 ≠ 进**可用**库存」；三步式恰好保留了这一点。**JJX 的隔离台账其实就是"待检格"，只是没进库存表、没有流水。**
- **待拍板对象**：把「待检」正式化的**载体**选谁——①库存状态格 / ②独立待检仓 / ③隔离表升格。
- **推荐与决策**：目标态倾向 **①**（单账、最贴现有模型）；**落定先做 ③**（补链路、零可用口径回归）；**②不推荐**。

---

## 2. 现状证据（代码 + 库，2026-09-29 实测）

### 2.1 收货不写库存/流水

- `PurchaseOrderServiceImpl.receiveOrderItem()`（`jjx-server/.../purchase/service/impl/PurchaseOrderServiceImpl.java:495-544`）：只更新 `purchase_order_item.received_quantity` + 收货状态，并调 `inboundService.createInboundRecordFromPurchase(orderId)`。
- `createInboundRecordFromPurchase()`（`inventory/service/impl/InventoryInboundServiceImpl.java:2281-2341`）：生成状态为**待审批/待确认**的入库单（注释：`状态=待审批，仓库确认后才加库存（2026-08-11 业务定稿：收货≠入库）`）。
- 流水只在 `confirm() → addStock()`（`:1989-2067`）写：`inventory_transaction(transaction_type=INBOUND)`；**采购来源只按 `accepted_quantity`（允收量）过账**（`:2001-2006`），允收 0 则 `continue`（不写流水）。

### 2.2 隔离台账 = 事实上的"待检格"

- `inventory_iqc_quarantine`（`quarantine_id / inbound_id / inbound_item_id / lot_id / iqc_batch_id / quantity / remaining_quantity / status`）就是"在检不可用"的账，只是**不在库存表里、无库存行、无流水**。
- 实测（IN260929005 链路）：quarantine 1（RM001572）10 件 / remaining 0 / status RELEASED；quarantine 2（RM001592）3 件 / remaining 0 / status PENDING。

### 2.3 库存模型的三条硬约束（决定改造成本）

1. `inventory_stock` 是**按 `inventory_item` 跨仓汇总**的一张表（`refreshSummary`/`refreshSummaryByInventoryItemId` SQL 见 `inventory/mapper/InventoryStockMapper.java:35-61`，`SUM(si.quantity) ... WHERE si.status = 1`）；
2. `inventory_stock_item.status` 是**有效行标志（=1）**，不是质量状态；`inventory_stock.available_quantity` 是 **STORED GENERATED = total_quantity − total_reserved**；
3. 出库/预留/盘点的取货走 `selectFIFOAvailable*`（`InventoryOutboundServiceImpl` :429/:439/:776/:834/:1202/:1230；`OrderStockReserveServiceImpl` :98/:226）——**默认从"可用"里 FIFO**。

→ 结论：任何把"待检"塞进 `inventory_stock*` 的方案（① 和 ②），都必须同时改"汇总口径 + FIFO 取货过滤"，这正是回归面的来源。

---

## 3. 三选一对比

| 维度 | ① 库存状态格 | ② 独立待检仓 | ③ 隔离表升格 |
|---|---|---|---|
| 一句话 | 批次库存加「可用/待检/冻结」状态 | 新增「待检仓」，货先入它 | 现有隔离台账正式当"待检库存" |
| 待检放在哪 | `inventory_stock_item` 内 | `inventory_stock_item` 内（另一仓） | 隔离表（不进库存表） |
| 表结构改动 | stock_item + stock + transaction 加列 | 基本不加列（加仓库数据/类型） | transaction 加类型（或新增待检视图） |
| 可用口径要动 | **要**（汇总按状态算） | **要**（汇总按仓类型排） | **不用** |
| 出库/预留/预警回归 | **大**（FIFO/预留全加过滤） | **中**（FIFO 排待检仓 + 转移流程） | **几乎零** |
| 是否单账 | ✅ | ✅（按仓拆） | ⚠️ 并行账（靠隔离表当唯一真源） |
| 与现有代码贴合度 | 高（本就按物料汇总 + FIFO） | 低（汇总是跨仓的，仓维度弱） | 高（隔离表已在跑） |
| 后端文件量（粗估） | 25~35 | 15~20 + 转移 | 8~12 |
| 前端 | 库存/流水/仓储 13 模块 | 库存/仓储/转移 + 仓库页 | 库存/检验 2~3 处 |
| 工时（粗估） | 1~2 周 | ~1 周 | **1~3 天** |
| 主要风险 | 动地基，回归面最大 | 可用口径照样要改，还多一套转移概念 | 待检不在库存表，"全库存"要看两处 |

---

## 4. 逐个说明

### 方案① 库存状态格（业内 SAP 那种）
`inventory_stock_item` 加 `stock_status`（AVAILABLE / QI / HOLD / REWORK）；`inventory_stock` 汇总拆出"待检量"，`available = 可用 − 预留`。
- 收货 → 写"待检"库存行 + 流水；判定 → 待检转可用（合格/让步）或待检出库（退货/报废，负流水）。
- 代价：出库/预留/安全库存预警/盘点/调拨取的都是"可用"，`selectFIFOAvailable*` 全要加状态过滤。
- 最契合本系统（按物料汇总 + FIFO），是**目标态首选**。

### 方案② 独立待检仓
`inventory_warehouse.warehouse_type` 已有（现两仓：WH01 成品仓 finished / WH02 原料仓 normal），加 `待检仓(inspection)` 即可：收货入待检仓，判定后转移至原料仓。
- 看着直观，但**库存汇总是按物料跨仓**，待检仓的量会直接混进"可用" → **照样要改汇总口径**；等于承担 ① 的回归成本，还多引入"仓类型 + 转移单"两套概念。
- 本系统仓库不是可用性的强维度，**不推荐**。

### 方案③ 隔离表升格为待检库存
不动库存表、不动可用口径；把已在跑的 `inventory_iqc_quarantine` 正式当"待检库存"：
- 收货即建待检行（不再等审核）+ 写一条「待检入库」流水；
- 判定 → 合格/让步写「待检转可用」（即现在的确认入库）、退货/报废写「待检出库」负流水；
- 前端把它挂到"库存 → 待检库存"入口，与流水打通。
- 代价：待检仍不在 `inventory_stock` 里，是"并行账"；必须立规矩 **"隔离表 = 待检唯一真源"**，否则又是双账。

---

## 5. 推荐与决策

- **目标态**：倾向 **①库存状态格**（单账、贴合现有模型）。
- **落定（2026-09-29 用户）**：**先做 ③**（隔离表升格）——1~3 天补上"收货就有待检流水"的追溯链，**零可用口径回归**；做完隔离表已是"事实上的待检库存"，后续要并到 ① 属渐进演进。
- **② 不推荐**：省不了改中口径的活，还多一层概念。

---

## 6. 拍板点（实施前必须定）

1. **待检的"唯一真源"定谁**：库存表状态（①/②）vs 隔离表（③）——不定清楚，做哪个都容易变双账（呼应 `dev-20260928-022/023`）。
2. **可用口径一旦要动（①/②），出库/预留/预警/看板要不要跟着做回归**。
3. 流水类型扩展的命名与口径：待检入库 / 待检转可用 / 待检出库（是否沿用 `transaction_type` 枚举，还是新增"库存状态变更"类型）。

---

## 7. 后续（本轮不做代码实施）

- 若上 ③：需 `jjx-docs/sql/migrations/NN_*.sql`（流水类型/字典）、`sys_dict` 库存状态、前端"库存→待检库存"入口、报表口径、回归用例；新表/新列按 §CONVENTIONS 同步清理归属。
- 数据侧无历史包袱（测试期可清库重造）。
- 本文件仅方案与结论，**不代表已实施**。
