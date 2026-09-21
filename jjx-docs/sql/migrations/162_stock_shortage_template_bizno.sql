-- ============================================================================
-- 162_stock_shortage_template_bizno.sql
-- 任务码：dev-20260921-013（收尾：缺料预警）
--
-- 目的：stock.shortage 模板 订单【{orderNo}】缺料{shortageCount}种物料 → 统一 {bizNo}
-- 前置：InventoryAlertServiceImpl 两处 stock.shortage payload 补 bizNo
--       （按订单触发时=orderNo；全局巡检时=「全部订单」）。幂等：REPLACE + LIKE 守卫。
-- ============================================================================
UPDATE sys_event_config
   SET title = REPLACE(title, '{orderNo}', '{bizNo}'),
       content = REPLACE(content, '{orderNo}', '{bizNo}')
 WHERE event_code = 'stock.shortage' AND (title LIKE '%{orderNo}%' OR content LIKE '%{orderNo}%');
