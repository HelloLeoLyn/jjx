-- ============================================================================
-- 152_quotation_modified_use_bizno.sql
-- 任务码：dev-20260921-013
--
-- 目的：统一业务单号占位符口径——quotation.modified 原用 {quotationNo}，
--       与 151 改后的其余报价事件（{bizNo}）不一致；统一为 {bizNo}。
--       （payload 里两个键都有，改哪个都能渲染，这里只为口径一致，避免以后新增事件时纠结用哪个。）
--
-- 幂等：REPLACE + WHERE LIKE 守卫。
-- ============================================================================

UPDATE sys_event_config
   SET title = REPLACE(title, '{quotationNo}', '{bizNo}')
 WHERE event_code = 'quotation.modified'
   AND title LIKE '%{quotationNo}%';
