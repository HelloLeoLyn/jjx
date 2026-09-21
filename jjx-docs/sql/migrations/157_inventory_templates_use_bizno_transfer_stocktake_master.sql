-- ============================================================================
-- 157_inventory_templates_use_bizno_transfer_stocktake_master.sql
-- 任务码：dev-20260921-013（库存批 2/3 + 3/3）
--
-- 目的：
--   ① 调拨 7 条、盘点 8 条：{bizId} → {bizNo}（调拨单号 / 盘点单号）
--   ② 物料 created/updated/deleted：{bizId} → {bizNo}（物料编码）
--   ③ 仓库/库位/物料分类/预警 7 条：标题原本没有对象标识（如「仓库状态已变更」），补上【{bizNo}】
--
-- 前置（同任务代码侧，需重启后端生效）：
--   transfer 7 个动作、stocktake 8 个动作改 publishTransferEvent/publishStocktakeEvent（bizNo=transferNo/stocktakeNo）；
--   material 3 个动作、warehouse/location/category 各 2 个动作、alert.processed 改手写 payload
--   （bizNo 依次取 materialCode/warehouseCode/locationCode/categoryCode/物料编码）。
--
-- 未动：inventory.material.status_updated（批量改状态，发射点仍是 @Event 注解且无单对象，保持原标题）。
--
-- 幂等：显式 SET 以原标题为守卫；批量 REPLACE 以 LIKE 守卫。
-- ============================================================================

-- ① 调拨 / 盘点
UPDATE sys_event_config
   SET title = REPLACE(title, '{bizId}', '{bizNo}'),
       content = REPLACE(content, '{bizId}', '{bizNo}')
 WHERE (event_code LIKE 'inventory.transfer.%' OR event_code LIKE 'inventory.stocktake.%')
   AND (title LIKE '%{bizId}%' OR content LIKE '%{bizId}%');

-- ② 物料主数据
UPDATE sys_event_config
   SET title = REPLACE(title, '{bizId}', '{bizNo}'),
       content = REPLACE(content, '{bizId}', '{bizNo}')
 WHERE event_code IN ('inventory.material.created', 'inventory.material.updated', 'inventory.material.deleted')
   AND (title LIKE '%{bizId}%' OR content LIKE '%{bizId}%');

-- ③ 标题补对象标识
UPDATE sys_event_config SET title='仓库【{bizNo}】已删除'           WHERE event_code='inventory.warehouse.deleted'             AND title='仓库已删除';
UPDATE sys_event_config SET title='仓库【{bizNo}】状态已变更'       WHERE event_code='inventory.warehouse.status_updated'      AND title='仓库状态已变更';
UPDATE sys_event_config SET title='库位【{bizNo}】已删除'           WHERE event_code='inventory.storage_location.deleted'      AND title='库位已删除';
UPDATE sys_event_config SET title='库位【{bizNo}】状态已变更'       WHERE event_code='inventory.storage_location.status_updated' AND title='库位状态已变更';
UPDATE sys_event_config SET title='物料分类【{bizNo}】已删除'       WHERE event_code='inventory.material_category.deleted'     AND title='物料分类已删除';
UPDATE sys_event_config SET title='物料分类【{bizNo}】状态已变更'   WHERE event_code='inventory.material_category.status_updated' AND title='物料分类状态已变更';
UPDATE sys_event_config SET title='库存预警【{bizNo}】已处理'       WHERE event_code='inventory.alert.processed'               AND title='库存预警已处理';

-- 自检：
--   SELECT event_code,title FROM sys_event_config WHERE event_code LIKE 'inventory.%' ORDER BY event_code;
