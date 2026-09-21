-- ============================================================================
-- 151_quotation_templates_use_bizno.sql
-- 任务码：dev-20260921-013（阶段 1-c，销售模块首批）
--
-- 目的：报价类事件的模板里，业务单号由内部编号 {bizId} 改为业务单号 {bizNo}。
--   改前：报价单【{bizId}】已发送给客户 / 审核结果 / 已转为订单 / 客户已确认 / 客户已拒绝 / 已提交审核
--         （{bizId} 是自增主键，用户看到「报价单【2】已发送给客户」）
--   改后：报价单【{bizNo}】…（如「报价单【QT2609210002】已发送给客户」）
--
-- 前置（同任务代码侧已完成，需重启后端生效）：
--   QuotationServiceImpl 的 6 个动作改用 EventPublishSupport 手写 payload，
--   payload 里带 bizNo=报价单号 + customerName + quotationStatus + finalAmount
--   （渲染器 dev-20260921-012 起支持 {键|备选}，取不到键会渲染成空串并打 WARN，不再原样吐花括号）。
--
-- 幂等：REPLACE + WHERE LIKE '%{bizId}%' 守卫，可重复执行。
--
-- 自检：
--   SELECT event_code, title FROM sys_event_config
--    WHERE event_code LIKE 'quotation.%' ORDER BY event_code;
--   （应全部为 {bizNo}，无 {bizId}）
-- ============================================================================

UPDATE sys_event_config
   SET title = REPLACE(title, '{bizId}', '{bizNo}')
 WHERE event_code LIKE 'quotation.%'
   AND title LIKE '%{bizId}%';
