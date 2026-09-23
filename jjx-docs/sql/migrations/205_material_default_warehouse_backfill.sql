-- dev-20260923-007：采购入库默认仓修复（C）——按物料类型批量回填 inventory_material.default_warehouse_id
--
-- 背景：采购收货生成入库单时，`resolvePurchaseWarehouseId()` 过去只看物料类型的"原料/成品"聚合，
--       而物料主数据里其实早有 `default_warehouse_id` 字段（此前全库 1601 条全为 NULL，没人填）。
--       本次把口径接上：优先用物料默认仓，缺省再退回类型聚合。本迁移负责把存量物料的默认仓补齐。
--
-- 口径（与仓库表现状对齐）：
--   成品 F              → 成品仓（warehouse_type='finished'，启用）
--   原料 R / 油墨 I / 辅料 A → 原料仓（名称含「原料」或 warehouse_type='normal'，启用；仓库备注即「原材料/辅料仓」）
--   半成品 S            → 不填（外购半成品存哪个仓属业务口径未定，留空会走类型兜底并打 WARN，不静默乱入）
--
-- 幂等：只更新 default_warehouse_id IS NULL 的行；仓库一律按 status='1'（正常）筛选，
--       与 A 修复后的 `InventoryWarehouseMapper.selectAllEnabled()` 口径一致；
--       子查询带 ORDER BY + LIMIT 1，避免多仓命中时的任意选择。

-- 1) 成品 → 成品仓
UPDATE inventory_material m
   SET m.default_warehouse_id = (
        SELECT w.warehouse_id FROM inventory_warehouse w
         WHERE w.status = '1' AND w.warehouse_type = 'finished'
         ORDER BY w.sort_order, w.warehouse_id LIMIT 1)
 WHERE m.material_type = 'F'
   AND m.default_warehouse_id IS NULL;

-- 2) 原料 / 油墨 / 辅料 → 原料仓
UPDATE inventory_material m
   SET m.default_warehouse_id = (
        SELECT w.warehouse_id FROM inventory_warehouse w
         WHERE w.status = '1'
           AND (w.warehouse_name LIKE '%原料%' OR w.warehouse_type = 'normal')
         ORDER BY (CASE WHEN w.warehouse_name LIKE '%原料%' THEN 0 ELSE 1 END), w.sort_order, w.warehouse_id
         LIMIT 1)
 WHERE m.material_type IN ('R', 'I', 'A')
   AND m.default_warehouse_id IS NULL;

-- 结果核对（输出到迁移日志）
SELECT m.material_type,
       COUNT(*) AS total,
       SUM(CASE WHEN m.default_warehouse_id IS NULL THEN 1 ELSE 0 END) AS no_default_warehouse,
       GROUP_CONCAT(DISTINCT m.default_warehouse_id) AS warehouse_ids
  FROM inventory_material m
 GROUP BY m.material_type
 ORDER BY m.material_type;
