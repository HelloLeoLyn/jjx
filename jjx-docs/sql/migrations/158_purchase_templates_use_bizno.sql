-- ============================================================================
-- 158_purchase_templates_use_bizno.sql
-- 任务码：dev-20260921-013（采购模块批）
--
-- 目的：采购域事件模板统一业务单号 {bizNo}
--   采购单 → purchase_order.order_no；供应商 → supplier_code；付款 → payment_no；单据 → document_no；
--   物料询价 → material_code（该表没有单号字段）；purchase.received（采购到货）原用 {orderNo} 也统一。
--
-- 前置（同任务代码侧，需重启后端生效）：
--   MaterialInquiry 3（created/updated/deleted）、PurchaseOrder 4（submitted/approved/item_received/payment_updated）、
--   PurchaseSupplier 4（created/updated/deleted/status_updated）、PurchasePayment 4（created/deleted/approved/confirmed）、
--   PurchaseDocument 3（created/deleted/verified）改手写 payload（publishXxxEvent，带 bizNo）。
--
-- 未动：purchase.material_inquiry.status_updated（批量改状态、无单对象，发射点仍是 @Event）。
--
-- 幂等：REPLACE + LIKE 守卫。
-- ============================================================================

UPDATE sys_event_config
   SET title = REPLACE(REPLACE(title, '{bizId}', '{bizNo}'), '{orderNo}', '{bizNo}'),
       content = REPLACE(REPLACE(content, '{bizId}', '{bizNo}'), '{orderNo}', '{bizNo}')
 WHERE event_code LIKE 'purchase.%'
   AND (title LIKE '%{bizId}%' OR title LIKE '%{orderNo}%'
        OR content LIKE '%{bizId}%' OR content LIKE '%{orderNo}%');

-- 自检：
--   SELECT event_code,title FROM sys_event_config WHERE event_code LIKE 'purchase.%' ORDER BY event_code;
