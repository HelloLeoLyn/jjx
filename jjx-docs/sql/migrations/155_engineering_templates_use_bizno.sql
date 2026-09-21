-- ============================================================================
-- 155_engineering_templates_use_bizno.sql
-- 任务码：dev-20260921-013（工程管理模块批）
--
-- 目的：工程域事件模板统一业务单号 {bizNo}
--   改前：菲林【{bizId}】…、工艺路线【{bizId}】…（{bizId} 是自增主键）；
--         BOM已提交审核 / BOM审核通过（标题里根本没有单号）。
--   改后：菲林【{bizNo}】…、工艺路线【{bizNo}】…、BOM【{bizNo}】…
--
-- 前置（同任务代码侧，需重启后端生效）：
--   EngineeringFilmServiceImpl 5 个动作、EngineeringRoutingServiceImpl 4 个动作、
--   EngineeringBomServiceImpl 2 个动作（submitApprove/approve）改手写 payload，
--   bizNo 分别取 filmCode / routingCode / bomCode。
--
-- 幂等：REPLACE + LIKE 守卫；BOM 两条以原标题为守卫。
--
-- 自检：
--   SELECT event_code, title FROM sys_event_config
--    WHERE event_code LIKE 'bom.%' OR event_code LIKE 'product.film%' OR event_code LIKE 'product.routing%';
-- ============================================================================

-- BOM：标题补业务单号
UPDATE sys_event_config SET title = 'BOM【{bizNo}】已提交审核'
 WHERE event_code = 'bom.submitted' AND title = 'BOM已提交审核';

UPDATE sys_event_config SET title = 'BOM【{bizNo}】审核通过'
 WHERE event_code = 'bom.approved' AND title = 'BOM审核通过';

-- 菲林 / 工艺路线：{bizId} → {bizNo}
UPDATE sys_event_config
   SET title = REPLACE(title, '{bizId}', '{bizNo}'),
       content = REPLACE(content, '{bizId}', '{bizNo}')
 WHERE (event_code LIKE 'product.film.%' OR event_code LIKE 'product.routing.%')
   AND (title LIKE '%{bizId}%' OR content LIKE '%{bizId}%');
