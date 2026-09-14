# 📦 库存模块完整业务分析

> 基于实际代码逐条梳理，颗粒度到每个端点、状态跳转、校验规则、交叉联动。
>
> 代码路径: `jjx-server/src/main/java/com/jjx/inventory/`
>
> 最后更新: 2026-08-01

---

## 一、整体模块结构（13 个 Controller）

| Controller | 路径 | 职责 |
|-----------|------|------|
| `InventoryWarehouseController` | `/inventory/warehouse` | 仓库管理 |
| `InventoryStorageLocationController` | `/inventory/storage-location` | 库位管理 |
| `InventoryMaterialCategoryController` | `/inventory/material-category` | 物料分类 |
| `InventoryMaterialController` | `/inventory/material` | 物料主数据 |
| `InventoryStockController` | `/inventory/stock` | 库存汇总 |
| `InventoryStockItemController` | `/inventory/stock-item` | 库存批次明细 |
| `InventoryInboundController` | `/inventory/inbound` | 入库单 |
| `InventoryOutboundController` | `/inventory/outbound` | 出库单 |
| `InventoryTransferController` | `/inventory/transfer` | 调拨单 |
| `InventoryStocktakeController` | `/inventory/stocktake` | 盘点单 |
| `InventoryAlertController` | `/inventory/alert` | 库存预警 |
| `InventoryTransactionController` | `/inventory/transaction` | 库存流水 |
| `InventoryReportController` | `/inventory/report` | 报表统计 |

---

## 二、A — 基础主数据（仓库/库位/分类/物料）

### 仓库（/inventory/warehouse）
```
GET    /warehouse/page | /list | /{id} | /enabled | /by-type | /options
DELETE /warehouse/{id}
PUT    /warehouse/{id}/status | /batch-status
GET    /warehouse/check-code
```
**字段**: warehouseId, warehouseCode(WH前缀), warehouseName, warehouseType(raw原材料/finished成品/semi半成品), location, manager, contactPhone, status(0启用/1停用?)

### 库位（/inventory/storage-location）
```
GET    /storage-location/page | /warehouse/{whId} | /{id} | /check-code | /recommend | /enabled | /by-type
DELETE /storage-location/{id}
PUT    /storage-location/{id}/status | /batch-status
POST   /storage-location/import
```
**字段**: locationId, warehouseId, locationCode, locationName, locationType(normal/quality待检/frozen冻结), capacity, usedCapacity, width/height/depth, status

### 物料分类（/inventory/material-category）
```
GET    /material-category/tree | /list | /{id}
DELETE /material-category/{id}
PUT    /material-category/{id}/status
```

### 物料（/inventory/material）🔑 主数据
```
GET    /material/count | /page | /search | /list | /code | /{id} | /check-code | /options
DELETE /material/{id}
PUT    /material/{id}/status | /batch-status
POST   /material/check | /import | /importTemplate
```
**字段**: materialId, materialCode(MAT前缀), materialName, materialNameEn, materialType(R原材料/F成品/P半成品/S辅料), processGroup(M), categoryId, specification, unit(PCS/M), unitConv, unitAlt, batchControl, shelfLife, expiryAlertDays, safeStock, maxStock, reorderPoint, standardPrice, leadTime, supplierId/Name, defaultWarehouseId/LocationId, status(1启用/0停用)

**预警参数**（安全库存/最高库存/再订货点）→ 供 InventoryAlert 使用

---

## 三、B — 库存与批次（Stock / StockItem）

### 库存汇总（/inventory/stock）
```
GET    /stock/list | /summary | /{stockId} | /material/{mid} | /warehouse/{wid}
GET    /stock/alert | /low-stock | /expiring | /obsolete | /dashboard
POST   /stock/check | /batch-import
```
**字段**: stockId, materialId, warehouseId, quantity, availableQuantity(可用), reservedQuantity(占用), status

### 批次明细（/inventory/stock-item）
```
GET    /stock-item/list | /{itemId} | /material/{mid} | /material/{mid}/warehouse/{wid}
```
**字段**: itemId, materialId/Code/Name, warehouseId, locationId, batchNo, productionDate, expiryDate, quantity, reservedQuantity, unitCost, status

**批次控制**：物料启用了 batchControl 才强制批次；有保质期物料（shelfLife）按批次管理过期预警

---

## 四、C — 入库单（InventoryInbound）🔑 核心

### 端点清单

```
GET    /inbound/list                          — 分页列表
GET    /inbound/{inboundId}                   — 详情
POST   /inbound/create                        — 创建
POST   /inbound/confirm/{inboundId}           — 确认入库（✅ 加库存）
POST   /inbound/cancel/{inboundId}            — 取消
POST   /inbound/submit-approve/{inboundId}    — 提交审批
POST   /inbound/approve/{inboundId}           — 审批通过
POST   /inbound/reject/{inboundId}            — 审批驳回
POST   /inbound/create-from-purchase/{poId}   — 🔑 从采购订单生成
POST   /inbound/create-from-production/{woId} — 从生产工单生成
GET    /inbound/pending-approval              — 待审批
GET    /inbound/date-range                    — 日期范围
GET    /inbound/source                        — 按来源单据查
GET    /inbound/dashboard                     — 仪表板
```

### 状态机
```
草稿 → 提交审批(PENDING) → 审批通过(APPROVED) → 确认入库(COMPLETED ✅加库存)
                        → 审批驳回(REJECTED)
草稿 → 取消(CANCELLED)
```

### 核心逻辑
- **确认入库才加库存**：更新 inventory_stock 数量 + 写 inventory_transaction 流水 + 安全库存检查
- 来源：采购订单（create-from-purchase）、生产工单（create-from-production）
- ⚠️ 采购到货不自动触发入库（需手动，TC-64 缺口）

---

## 五、D — 出库单（InventoryOutbound）

### 端点清单

```
GET    /outbound/list                         — 分页列表
GET    /outbound/{outboundId}                 — 详情
POST   /outbound/create                       — 创建
POST   /outbound/confirm/{outboundId}         — 确认出库（✅ 扣库存）
POST   /outbound/cancel/{outboundId}          — 取消
POST   /outbound/submit-approve/{outboundId}  — 提交审批
POST   /outbound/approve/{outboundId}         — 审批通过
POST   /outbound/reject/{outboundId}          — 审批驳回
POST   /outbound/create-from-production/{woId} — 🔑 生产领料（工单→出库单）
POST   /outbound/create-from-sales/{soId}     — 🔑 销售发货（订单→出库单）
GET    /outbound/pending-approval             — 待审批
GET    /outbound/date-range                   — 日期范围
GET    /outbound/source                       — 按来源查
GET    /outbound/dashboard                    — 仪表板
```

### 出库类型（OutboundTypeEnum）
- **PRODUCTION**（production 生产领料）—— 工单领料用
- SALES（销售发货）
- OTHER（其他）

### 核心逻辑
- **确认出库才扣库存**：扣减库存 + 写流水
- **生产领料（两步，DEV-472 已实现）**：
  1. 工单"生成领料单"（create-from-production）→ 按生效BOM生成物料清单 → 领料单状态=待发料(1)，工单领料状态=待发料
  2. 仓库"确认发料"（confirm）→ 扣库存+写流水 → 领料单状态=已发料(10)，工单领料状态=已领料(2)
- 领料单=生产领料类型出库单（outbound_type=production，source_type=work_order），重复生成自动拦截
- 工单领料状态字段：production_order.material_status（0未领料/1待发料/2已领料，静态枚举）

---

## 六、E — 调拨单（InventoryTransfer）

### 端点清单

```
GET    /transfer/list | /{transferId} | /pending-approval | /processing | /dashboard
POST   /transfer/create                       — 创建
POST   /transfer/submit-approve/{id}          — 提交审批
POST   /transfer/approve/{id}                 — 审批通过
POST   /transfer/reject/{id}                  — 审批驳回
POST   /transfer/confirm-out/{id}             — 调出确认（扣调出仓库存）
POST   /transfer/confirm-in/{id}              — 调入确认（加调入仓库存）
POST   /transfer/cancel/{id}                  — 取消
POST   /transfer/update-status/{id}           — 更新状态
```

### 状态机
```
草稿 → 待审批 → 已批准 → 调出确认 → 调入确认(完成)
               → 已拒绝
草稿 → 已取消
```

---

## 七、F — 盘点单（InventoryStocktake）

### 端点清单

```
GET    /stocktake/list | /{id} | /processing | /pending-approval | /dashboard
POST   /stocktake/create                      — 创建
POST   /stocktake/start/{id}                  — 开始盘点
POST   /stocktake/input-data/{id}             — 录入盘点数据
GET    /stocktake/calculate-diff/{id}         — 计算差异
POST   /stocktake/confirm-result/{id}         — 确认结果
POST   /stocktake/process-diff/{id}           — 处理盈亏（调整库存）
POST   /stocktake/close/{id}                  — 关闭
POST   /stocktake/submit-approve/{id}         — 提交审批
POST   /stocktake/approve/{id}                — 审批通过
POST   /stocktake/update-status/{id}          — 更新状态
```

### 状态机
```
草稿 → 盘点中(开始) → 已录入 → 已确认 → 已处理(盈亏调整库存) → 已关闭
     → 待审批 → 已批准/已驳回
```

---

## 八、G — 库存预警（InventoryAlert）

### 端点清单

```
GET    /alert/list | /unprocessed | /exists-unprocessed | /purchase-suggestions | /dashboard
POST   /alert/execute-check                   — 执行全部预警检查
POST   /alert/check-safe-stock                — 安全库存（低于再订货点）
POST   /alert/check-max-stock                 — 最高库存（超储）
POST   /alert/check-expiry                    — 保质期（临期/过期）
POST   /alert/check-obsolete                  — 呆滞料（长期无出入库）
POST   /alert/mark-read/{id} | /batch-mark-read
POST   /alert/process/{id}                    — 处理预警（生成采购建议）
```

### 预警类型
- safe_stock（安全库存不足）
- max_stock（超储）
- expiry（临期/过期）
- obsolete（呆滞）

### 联动
- **采购建议**：purchase-suggestions 按低库存物料生成建议采购量
- 预警状态：未处理 → 已读 → 已处理

---

## 九、H — 库存流水（InventoryTransaction）

### 端点清单

```
GET    /transaction/list | /{id} | /source | /material/{mid} | /time-range
GET    /transaction/stat/material/{mid}       — 物料出入库统计
GET    /transaction/dashboard
```

### 流水类型（TransactionType）
- INBOUND（入库）
- OUTBOUND（出库）
- TRANSFER_IN / TRANSFER_OUT（调拨入/出）
- STOCKTAKE_ADJUST（盘点调整）

**每条出入库/调拨/盘点都会写流水**——追溯基础

---

## 十、I — 报表（InventoryReport）

```
GET    /report/stock-summary       — 库存概览
GET    /report/turnover            — 周转率
GET    /report/cost                — 库存成本
GET    /report/in-out-stat         — 出入库统计
GET    /report/abc-analysis        — ABC 分析
GET    /report/warehouse-stock-stat — 仓库库存
GET    /report/material-trend      — 物料趋势
GET    /report/alert-stat          — 预警统计
GET    /report/stocktake-diff-stat — 盘点差异
GET    /report/transfer-stat       — 调拨统计
GET    /report/category-stock-stat — 分类库存
GET    /report/obsolete-analysis   — 呆滞分析
GET    /report/expiry-analysis     — 保质期分析
GET    /report/dashboard           — 仪表板
```

---

## 十一、状态流转总图

```
物料主数据(1启用/0停用) + 安全库存参数
        │
        ▼
仓库/库位(正常/待检/冻结)
        │
        ▼
┌─ 入库单: 草稿→待审批→已批准→确认入库(✅加库存+流水)
│           └─ 来源: 采购单/生产工单(手动联动)
├─ 出库单: 草稿→待审批→已批准→确认出库(✅扣库存+流水)
│           └─ 来源: 生产领料/销售发货
├─ 调拨单: 草稿→待审批→已批准→调出确认→调入确认(✅增减库存+流水)
├─ 盘点单: 草稿→盘点中→录入→确认→处理盈亏(✅调整库存+流水)→关闭
└─ 预警:  安全库存/超储/临期/呆滞 → 采购建议
```

## 十二、与其他模块联动

| 联动 | 方式 | 状态 |
|---|---|---|
| 采购→入库 | create-from-purchase（手动）| ⚠️ 未自动 |
| 生产→领料 | create-from-production 出库单（DEV-472：按BOM生成物料清单→待发料→确认发料扣库存→已发料） | ✅ 已实现 |
| 销售→发货 | create-from-sales 出库单 | ✅ |
| 库存→事件 | 入库/出库/调拨/盘点 44 个事件（仓管[11]）| ✅ |

## 十三、已知问题

1. **采购到货→库存断链**（DEV-471）：到货登记不加库存
2. **生产领料单**（DEV-472）：无"工单→按BOM生成领料物料清单"功能 —— ✅ **已完成**（2026-08-04）：工单"生成领料单"按BOM生成物料清单→待发料；仓库"确认发料"扣库存→已发料→工单领料状态更新
3. **库存流水完整性**：依赖各单据确认时正确写流水，需 E2E 验证
