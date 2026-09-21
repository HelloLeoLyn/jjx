-- ============================================================================
-- 159_production_templates_use_bizno.sql
-- 任务码：dev-20260921-013（生产模块批）
--
-- 目的：生产域事件模板统一业务单号 {bizNo}（生产工单号）
--   production.started         生产工单【{bizId}】已开始      → 【{bizNo}】
--   production.completed       工单【{orderId}】已完成        → 【{bizNo}】
--   production.work-report.*   新报工待审批：{orderNo} 等 3 条 → {bizNo}
--
-- 前置（同任务代码侧，需重启后端生效）：
--   ProductionOrderStartTransactionService.startOrder 改手写 payload（publishXxx，bizNo=orderNo + salesOrderNo）；
--   WorkReportActionServiceImpl 三条报工事件 params 补 bizNo=#result.orderNo；
--   ProductionOrderServiceImpl 里 production.completed 的 Map.of payload 补 bizNo。
--
-- 幂等：REPLACE + LIKE 守卫。
-- ============================================================================

UPDATE sys_event_config
   SET title = REPLACE(REPLACE(REPLACE(title, '{bizId}', '{bizNo}'), '{orderId}', '{bizNo}'), '{orderNo}', '{bizNo}'),
       content = REPLACE(REPLACE(REPLACE(content, '{bizId}', '{bizNo}'), '{orderId}', '{bizNo}'), '{orderNo}', '{bizNo}')
 WHERE event_code LIKE 'production.%'
   AND (title LIKE '%{bizId}%' OR title LIKE '%{orderId}%' OR title LIKE '%{orderNo}%'
        OR content LIKE '%{bizId}%' OR content LIKE '%{orderId}%' OR content LIKE '%{orderNo}%');
