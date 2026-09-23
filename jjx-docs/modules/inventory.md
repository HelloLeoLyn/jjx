# 库存管理 · 现行真相

> 状态：✅已实施（dev-20260909-006 ~ 010） | 本文是"现在是什么样"，历史看文末指针
> 最后复核：**2026-09-23**（补 2026-09-22~23 定案口径）

## 一句话

材料与产品**共用一套库存身份（`inventory_item`）和一套库存引擎**；不存在成品双账本，也没有 F 类产品镜像物料。

## 入口（菜单 18 → /inventory）

| 菜单 | menu_id | path | perms |
|---|---|---|---|
| 物料档案 | 19 | material | inventory:material:view |
| 仓库与库位 | 23 | warehouse | inventory:warehouse:view |
| 库存台账 | 26 | stock | inventory:stock:view |
| 入库作业 | 28 | inbound | inventory:inbound:view |
| 出库作业 | 33 | outbound | inventory:outbound:view |
| 盘点作业 | 34 | stocktake | inventory:stocktake:view |
| 调拨作业 | 35 | transfer | inventory:transfer:view |
| 库存预警 | 27 | alert | inventory:alert:view |

出库作业承接**领料 / 普通出库 / 销售发货**三类业务（本次重组不拆页面、不拆接口）。

## 当前生效的规则

- **统一身份**：`inventory_item(inventory_item_id, item_type=MATERIAL|PRODUCT, source_id)`，唯一键 `(item_type, source_id)` 与 `item_code`；`source_id` 指向 `material_id` 或 `product_id`。
- 以下 8 张表都带 `inventory_item_id`：`inventory_stock` / `inventory_stock_item` / `inventory_inbound_item` / `inventory_outbound_item` / `inventory_transaction` / `inventory_stocktake_item` / `inventory_transfer_item` / `sales_order_stock_reserve`。其中 5 张有指向 `inventory_item` 的外键（stock、stock_item、inbound_item、outbound_item、transaction）。
- 上述表里 `material_id` 降级为**材料来源兼容字段**：产品库存行为 NULL。
- **`product_stock` 表已删除**（迁移 75），`ProductStock` 实体/Mapper/Service/Controller 整体移除；库存列表按 `itemType=MATERIAL|PRODUCT` 筛选。
- 完工入库直接记 PRODUCT 库存身份，**不再生成 F 类镜像物料、不再双写**。
- 安全库存阈值从 `inventory_item` 读取，材料与产品共用一条预警链路。

## 关键代码

- 后端：`com.jjx.inventory.{controller, service.impl}`：`InventoryItemService`、`InventoryStockService`、`InventoryStockItemServiceImpl`、`InventoryAlertServiceImpl`、`InventoryInboundServiceImpl`、`InventoryOutboundServiceImpl`、`OrderStockReserveServiceImpl`
- 前端：`jjx-web/src/views/inventory/**`、`enums/inventory/StockEnum.ts`、`enums/inventory/InboundEnum.ts`、`types/inventory/stock.ts`
- 接口样例：`GET /inventory/stock/summary?itemType=`、`GET /inventory/stock/list`、`GET /inventory/stock/{stockId}`

## 近期变更

- 迁移 `73_unified_inventory_item_foundation.sql`（建身份 + 回填）→ `74_unified_inventory_item_engine_cutover.sql`（引擎切到 inventory_item_id）→ `75_remove_legacy_product_stock.sql`（破坏性：删镜像物料 + DROP product_stock）
- 迁移 `77` / `78` 完成库存菜单扁平化（临时分组已清理，IQC 与隔离台账迁出到质量域）
- 当前数据量：MATERIAL 1535 / PRODUCT 1；身份列无空值

## 口径（2026-09-22 ~ 23 定案）

### 1. 库存三本账铁律（唯一真源：`standards/CONVENTIONS.md §13`）

- **单据不可改**（入库单/出库单/调拨/盘点一旦过账不可逆改，只能红冲/反向单）；
- **流水只增不改**（`inventory_transaction` 一进一留痕，带 `before_quantity/after_quantity`）；
- **余额是派生值**（`inventory_stock` / `inventory_stock_item` 由流水派生，实时聚合）；
- **变动唯一入口** `InventoryStockMutationService.applyDelta`（改批次明细 + 重算汇总 + 写流水一次做完，禁止旁路直接 UPDATE 余额）；
- **批次可追溯**（业务批次与流水都带批次号，可正查到单、反查到批）；
- ⛔ **不给批次表加「累计入库/累计出库」列**（避免第二真源，收发一律由流水实时聚合）。

### 2. 入库过账只发生在「确认入库」

- 采购/生产来源生成入库单只是**单据**，库存不动；仓库在「库存管理 → 入库作业」点**确认入库**才过账（写流水 + 加余额）；
- 成品侧同规则（口径 B，见 `quality.md`）：判定/复检只维护「应入数量」，减量走**冲减** `reducePostedStock`；
- 红冲单（`-R`、负数量）同样要仓库确认后才产生 `ADJUST` 负额流水。

### 3. 入库单 ↔ 检验批 关联与门禁

- 生产来源入库明细必须挂 `lot_id`；同一 lot 被多张未取消单据重复计账会被拦；已过账量必须 = 入库侧流水（`INBOUND` + `ADJUST`）；
- 门禁：`scripts/check-inbound-lot-integrity.sh --strict`（八查，含数量守恒）→ `npm run check:lot:strict`；
- 对账：`scripts/check-stock-summary.sh --strict`（汇总 = 批次明细合计 = 流水派生）→ `npm run check:stock:strict`。

### 4. 仓库与默认仓

- 入库选仓优先级：物料 `default_warehouse_id`（需启用仓）→ 按物料类型聚合兑底（R/A/I → 原料仓、F → 成品仓）；都不能定才兜底并 WARN；
- ⚠️ `inventory_warehouse.status` 代码用 `'1'=启用`（列注释仍写「0正常 1停用」，待清理）。

### 5. 单号

- 业务单号一律走 `sys_number_sequence`，**默认 3 位流水 + 溢出告警**（规则总表见 `design/doc-no-rules-dev-20260922-023.md`）；巡检 `scripts/check-doc-no.sh` 已进 `npm run validate`。

## 历史（"当年为什么这样"才看）

`analysis/unified-inventory-item-dev-20260909-006.md`（方案A与实施记录）、`analysis/inventory-menu-reorg-dev-20260909-010.md`（职责重组）、`analysis/inventory-gap-analysis-20260902.md`（改造前盘点）
