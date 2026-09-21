-- ============================================================================
-- 160_quality_iqc_templates_use_bizno.sql
-- 任务码：dev-20260921-013（质量模块批）
--
-- 目的：质量域 IQC 三条事件模板统一业务单号 {bizNo}
--   quality.iqc.submitted / approved / quarantine.created 原用 {inboundNo}，
--   统一为 {bizNo}（payload 里两键同值 = 入库单号，由 iqcPayload 提供）。
--
-- 前置：iqcPayload 已补 bizNo（dev-20260921-013 库存批 1/3 的同批改动）。
-- 说明：quality.iqc.item.approved / item.rejected / reinspection.created 三条是「代码发但未配置」，
--       属 dev-20260921-018 的死配置/未配置清单，本批不动。
--
-- 幂等：REPLACE + LIKE 守卫。
-- ============================================================================

UPDATE sys_event_config
   SET title = REPLACE(title, '{inboundNo}', '{bizNo}'),
       content = REPLACE(content, '{inboundNo}', '{bizNo}')
 WHERE event_code LIKE 'quality.iqc.%' AND (title LIKE '%{inboundNo}%' OR content LIKE '%{inboundNo}%');
