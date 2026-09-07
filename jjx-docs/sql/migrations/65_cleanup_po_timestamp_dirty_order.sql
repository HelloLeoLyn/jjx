-- dev-20260907-010：清理前端自造单号 PO-1788774497671 及其关联链。
-- 先执行以下 SELECT，核对目标采购单、计划单及子表记录；确认无收货、库存、质量关联后再执行事务。
SELECT order_id, order_no, plan_status
FROM purchase_order
WHERE order_no = 'PO-1788774497671';

SELECT item_id, order_id
FROM purchase_order_item
WHERE order_id = 1;

SELECT inbound_id, inbound_no, source_type, source_id
FROM inventory_inbound_order
WHERE source_type = 'PURCHASE'
  AND source_id = 1;

SELECT item_id, inbound_id
FROM inventory_inbound_item
WHERE inbound_id = 1;

SELECT order_id, order_no, plan_status
FROM purchase_order
WHERE order_no LIKE '%1788774497671%'
  AND plan_status = 1;

START TRANSACTION;

DELETE FROM inventory_inbound_item
WHERE inbound_id = 1;

DELETE FROM inventory_inbound_order
WHERE inbound_id = 1;

DELETE FROM purchase_order_item
WHERE order_id = 1;

DELETE FROM purchase_order
WHERE order_id = 1;

DELETE poi
FROM purchase_order_item AS poi
INNER JOIN purchase_order AS po ON po.order_id = poi.order_id
WHERE po.order_no LIKE '%1788774497671%'
  AND po.plan_status = 1;

DELETE FROM purchase_order
WHERE order_no LIKE '%1788774497671%'
  AND plan_status = 1;

COMMIT;
