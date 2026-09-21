-- ============================================================================
-- 154_sales_customer_sample_templates_bizno.sql
-- 任务码：dev-20260921-013（收尾：客户 5 条 + sample.transferred）
--
-- 目的：把最后 6 条还带 {bizId}（内部编号）的销售模板统一成 {bizNo}。
--
-- 前置（同任务代码侧，需重启后端生效）：
--   CustomerServiceImpl 的 5 个客户动作改手写 payload，bizNo = 客户编码（缺编码回退客户名称）；
--   批量动作（批量删除/批量审核）一次事件带汇总单号（「A001 等 3 个」），不逐个刷通知；
--   SampleOrderServiceImpl.transferMaterials 改手写 payload（bizNo = 样品单号）。
--
-- 幂等：REPLACE + LIKE 守卫。
--
-- 自检：
--   SELECT event_code, title FROM sys_event_config
--    WHERE event_code LIKE 'sales.customer%' OR event_code = 'sample.transferred';
-- ============================================================================

UPDATE sys_event_config
   SET title = REPLACE(title, '{bizId}', '{bizNo}')
 WHERE (event_code LIKE 'sales.customer.%' OR event_code = 'sample.transferred')
   AND title LIKE '%{bizId}%';
