-- ============================================================================
-- 116: 补齐 IQC 隔离：不良品不得计入允收入库（PO202609160003 / RM001563）
-- 任务码: dev-20260916-009
-- 背景: 采购入库单 PO202609160003（inbound_id=2）行 item_id=2 / RM001563 蓝色PU：
--       收货 500、抽检 500、合格 499、不良 1、判定不合格、处置=部分接收(PARTIAL_ACCEPT)。
--       处置联动把"允收入库数量"写成了整批 500（应为良品 499），隔离数量口径 = 收货 − 接收 = 0，
--       于是不良 1 件被当良品入库（已 16:46:07 确认入库），隔离处置里看不到那 1 件。
-- 代码侧: 已在 dev-20260916-009 修正（同步处置联动默认/上限 = 良品数量 + 后端守卫 accepted ≤ 收货 − 不良）。
-- 本迁移: 把这一单的账补齐（仅此一单，其它环境无此单时各语句匹配 0 行）。
--   ① 检验行：允收入库 / 已入库 500 → 499
--   ② 良品库存：批次 PO202609160003-1 数量 500 → 499（预占同步下调）
--   ③ 补 1 条 IQC 隔离行（不良 1 件，状态待处置，处置方式沿用部分接收）
--   ④ 补流水留痕：IQC_QUARANTINE +1（与系统 createIqcQuarantine 写法一致）+ ADJUST −1（不良从良品库存调出）
-- 幂等: 每条语句都带"当前值/不存在"条件，重复执行匹配 0 行。
-- ============================================================================
USE `jjx_erp_db`;

-- ① 检验行：不良不得计入允收入库
UPDATE inventory_inbound_item
SET accepted_quantity = 499,
    posted_quantity = 499
WHERE item_id = 2
  AND material_code = 'RM001563'
  AND rejected_quantity = 1
  AND accepted_quantity = 500
  AND posted_quantity = 500;

-- ② 良品库存下调 1（不良件从良品库存调出）
UPDATE inventory_stock_item
SET quantity = 499,
    reserved_quantity = LEAST(reserved_quantity, 499)
WHERE item_id = 2
  AND batch_no = 'PO202609160003-1'
  AND quantity = 500;

-- ③ 补隔离行（不良数量 = rejected_quantity）
INSERT INTO inventory_iqc_quarantine
    (inbound_id, inbound_item_id, inspection_id, material_id, material_code, material_name, batch_no,
     quantity, remaining_quantity, disposition, status, operator_id, operator_name, create_time, update_time)
SELECT i.inbound_id, i.item_id, i.inspection_id, i.material_id, i.material_code, i.material_name, i.batch_no,
       i.rejected_quantity, i.rejected_quantity, i.disposition, 'PENDING', 1, '系统管理员', NOW(), NOW()
FROM inventory_inbound_item i
WHERE i.item_id = 2
  AND i.rejected_quantity > 0
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT inbound_item_id, inspection_id FROM inventory_iqc_quarantine) q
      WHERE q.inbound_item_id = i.item_id AND q.inspection_id = i.inspection_id
  );

-- ④-a 流水：IQC 隔离登记 +1（照系统 createIqcQuarantine 的写法）
INSERT INTO inventory_transaction
    (inventory_item_id, material_id, material_code, material_name, warehouse_id, location_id, transaction_type,
     source_type, source_id, source_no, batch_no, quantity, before_quantity, after_quantity, unit_cost, amount,
     transaction_time, operator_id, operator_name, remark, create_by, create_time)
SELECT s.inventory_item_id, q.material_id, q.material_code, q.material_name, s.warehouse_id, s.location_id,
       'IQC_QUARANTINE', 'INBOUND_IQC', q.inbound_id, o.inbound_no, q.batch_no,
       q.quantity, 0, q.quantity, s.unit_cost, ROUND(s.unit_cost * q.quantity, 2),
       NOW(), q.operator_id, q.operator_name, 'IQC 不合格品隔离（dev-20260916-009 补齐）', 'Hermes', NOW()
FROM inventory_iqc_quarantine q
JOIN inventory_inbound_order o ON o.inbound_id = q.inbound_id
LEFT JOIN inventory_stock_item s ON s.item_id = 2
WHERE q.inbound_item_id = 2
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT transaction_type, source_no, batch_no FROM inventory_transaction) t
      WHERE t.transaction_type = 'IQC_QUARANTINE'
        AND t.source_no = o.inbound_no COLLATE utf8mb4_unicode_ci
        AND t.batch_no = q.batch_no COLLATE utf8mb4_unicode_ci
  );

-- ④-b 流水：良品库存 ADJUST −1（把不良件从良品库存调出，账实一致）
INSERT INTO inventory_transaction
    (inventory_item_id, material_id, material_code, material_name, warehouse_id, location_id, transaction_type,
     source_type, source_id, source_no, batch_no, quantity, before_quantity, after_quantity, unit_cost, amount,
     transaction_time, operator_id, operator_name, remark, create_by, create_time)
SELECT s.inventory_item_id, s.material_id, s.material_code, s.material_name, s.warehouse_id, s.location_id,
       'ADJUST', 'INBOUND_IQC', 2, 'PO202609160003', s.batch_no,
       -1, 500, 499, s.unit_cost, -ROUND(s.unit_cost * 1, 2),
       NOW(), 1, '系统管理员', 'IQC 不良品从良品库存调出（dev-20260916-009 补齐）', 'Hermes', NOW()
FROM inventory_stock_item s
WHERE s.item_id = 2
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT remark FROM inventory_transaction) t
      WHERE t.remark LIKE '%dev-20260916-009 补齐%' AND t.remark LIKE '%调出%'
  );

-- ⑤ 核验
SELECT '核验：检验行 / 库存 / 隔离 应分别为 499 / 499 / 1' AS check_point;
SELECT i.accepted_quantity, i.posted_quantity, s.quantity AS stock_quantity, s.reserved_quantity,
       (SELECT COALESCE(SUM(q.quantity), 0) FROM inventory_iqc_quarantine q WHERE q.inbound_item_id = 2) AS quarantine_qty
FROM inventory_inbound_item i
LEFT JOIN inventory_stock_item s ON s.item_id = i.item_id
WHERE i.item_id = 2;

SELECT '核验：该单流水（应有 INBOUND 500 ×2 + IQC_QUARANTINE 1 + ADJUST -1）' AS check_point;
SELECT transaction_id, transaction_type, material_code, batch_no, quantity, before_quantity, after_quantity, remark
FROM inventory_transaction
WHERE source_no = 'PO202609160003' OR (source_type = 'INBOUND_IQC' AND source_id = 2)
ORDER BY transaction_id;
