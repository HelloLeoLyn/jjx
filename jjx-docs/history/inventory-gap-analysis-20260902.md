# JJX ERP 库存模块实证差距分析报告

- 报告日期：2026-09-02
- 方法：数据库实查 + 代码实扫 + 事件配置对照
- 红线：成品物料(inventory_material F型)≠产品(product)；库存只有物料

---

## 1. 现状盘点（实证）

### 1.1 表清单与行数

| 表 | 行数 | 判定 |
|---|---|---|
| inventory_material | 1536 | 有真实数据（R原料/F成品/P半成品/S辅料） |
| inventory_warehouse | 2 | 真实数据 |
| inventory_material_category | 0 | 空转 |
| inventory_stock / inventory_stock_item | 0 / 0 | 空转（核心账实表 0 行——无任何出入库发生） |
| inventory_inbound_order / item | 0 / 0 | 空转 |
| inventory_outbound_order / item | 0 / 0 | 空转 |
| inventory_stocktake_order / item | 0 / 0 | 空转 |
| inventory_transfer_order / item | 0 / 0 | 空转 |
| inventory_transaction | 0 | 空转（库存流水账） |
| inventory_alert_log | 0 | 空转 |
| inventory_storage_location | 0 | 空转 |
| order_material_reserve | 0 | 空转（订单材料预留） |

物料 1536 条有数据但库存 0 行——主数据在、账实空（与生产/采购 0 行一致，dev 清洗后未跑交易）。

### 1.2 后端 Controller（inventory 包 15 个）

InventoryInboundController(19 端点)/OutboundController(21)/StocktakeController/TransferController/StockController/AlertController(17)/MaterialController(17+import)/Warehouse/StorageLocation/MaterialCategory/Transaction/StockItem/Report/OrderMaterialReserve/ProductStock。CRUD+审批流（submit/approve/reject/confirm/cancel）全覆盖，无只读空壳（inbound/outbound export-pdf 端点存在，参照销售同为前端 A4Canvas 替代）。

### 1.3 前端页面与菜单

库存菜单（18 库存/19 物料/23 仓库/28 入库/33 出库/242 目录）：库存/预警/盘点/调拨/物料/分类/详情/仓库/库位/入库/出库全部有 C 菜单指向真实 views 文件 ✅。views/inventory 下 alert/inbound/material/outbound/report/stock/stocktake/transfer/warehouse 全部有页。**inventory/report 页有 views 但 sys_menu 未见对应 C 菜单**（同采购 report 情形，需核实）。

### 1.4 事件配置（inventory.* 42 条启用 vs @Event 代码 28 条）

代码有触发：inbound/outbound 各 7（created/submitted/approved/rejected/confirmed/cancelled——material.*3/warehouse.deleted/alert.processed/stocktake 6/transfer 6）。

空转（配置启用代码无 @Event fire）：
- inbound/outbound.created_from_production / created_from_purchase ×4（跨模块联动事件，实际由 InventoryEventBridge @EventListener 直调 service，事件配置无触发方）
- stocktake.data_inputted / diff_processed / result_confirmed ×3
- transfer.confirmed_in / confirmed_out ×2
- material_category.deleted/status_updated、material.status_updated、storage_location.deleted/status_updated、warehouse.status_updated ×7（主数据通知事件）
- stock.low/over/expiry/obsolete/shortage ×5：**非空转**——049 修复后 InventoryAlertServiceImpl 手动 LocalEventPublisher.fire（不走 @Event 注解，grep 查不到属正常），stock.over/expiry/obsolete 已注册（is_enabled=0 待界面启用）

### 1.5 库代码一致性

InventoryInboundItem/Order 等实体与表一致；无脱节。

---

## 2. 业务闭环验证

| 环节 | 判定 |
|---|---|
| 入库（采购/生产/退货来源联动） | ✅通（create-from-purchase/production + 1235 退货销售来源） |
| 出库（销售/生产领料来源） | ✅通（create-from-sales 走 shipOrder 事件、create-from-production） |
| 库存流水（transaction） | ✅通（有实体/写入逻辑，0 行待跑） |
| 盘点→差异→调账 | ✅通（代码走查：stocktake 状态机 8 态+diff_processed） |
| 调拨 | ✅通 |
| 预警（低库存/超储/效期/呆滞） | ✅通（049 修复，配置注册，数据层 safe_stock 全 0 未触发） |
| 预留（order_material_reserve） | ✅通（三方调用：订单/出库/工单） |

全部代码走查通，0 行数据未实测。

---

## 3. 与行业基准对照

覆盖：物料主数据✅ 仓库库位✅ 出入库✅ 盘点✅ 调拨✅ 预警✅ 流水✅。
缺失/薄弱：
- 安全库存/再订货点参数分散（material.safe_stock 全 0 未维护——**主数据没配齐**，预警形同虚设）
- 批次/序列管理（有 batch_no 字段但无批次台账页）
- 库存成本核算（移动加权/月加权）无——与财务对账断层

## 4. 缺口与死代码清单

| 类型 | 项 | 证据 | 影响 | 建议 |
|---|---|---|---|---|
| 数据缺口 | safe_stock 全 0 | SELECT safe_stock 全 0（1536 物料） | 高 | 主数据维护任务（预警前提） |
| 空转事件 | created_from_* ×4 / stocktake 3 / transfer 2 / 主数据 7 | 配置 vs @Event | 低 | 清理或按需启用 |
| 孤儿页 | views/inventory/report | 无 C 菜单 | 低 | 挂菜单或删除 |
| 业务缺失 | 批次台账/成本核算 | 无接口/页面 | 中 | 后置（财务阶段） |
| 空壳 | inbound/outbound export-pdf | 返回 success 无文件 | 低 | 前端 A4Canvas 已替代 |

## 5. 优先级结论

| 优先级 | 事项 | 理由 |
|---|---|---|
| P1 | safe_stock/再订货点主数据维护 | 1536 物料全 0，预警不生效=库存风险不可见 |
| P2 | 事件空转清理 | 配置噪音+误导 |
| P2 | report 页入口 | 孤儿页面 |
| P3 | 批次/成本核算 | 财务阶段 |
