-- ============================================================
-- 72_auto_complete_mid_tasks.sql（一次性数据补偿，2026-09-09 dev-20260909-004）
-- 任务完成链简化上线：中间节点自动完成（原需负责人人工逐级点"完成"）。
-- 历史遗留：子任务已全完成但父节点仍 ACTIVE 的中间节点，批量补齐为 COMPLETED。
--
-- 补偿条件（与后端 completionBlockers 口径对齐的保守子集）：
--   非根(parent_task_id NOT NULL) + ACTIVE
--   + 无未完成直接子（直接子全 COMPLETED/CANCELLED）
--   + 自身无 PENDING 报工
--   + task_quantity <= 自身 APPROVED 合格量 + Σ已 COMPLETED 直接子任务量
-- 根任务一律不动（留给工序「完工」按钮收口）。
-- 幂等：status='ACTIVE' 条件，重复执行第二遍影响 0 行。
-- guard 备份：sql/backups/production_task_completechain_20260909-1730.sql
-- ============================================================

UPDATE production_task t
SET t.status = 'COMPLETED', t.version = t.version + 1,
    t.update_by = 'system-compensation-20260909', t.update_time = NOW()
WHERE t.parent_task_id IS NOT NULL AND t.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM production_task c
                  WHERE c.parent_task_id = t.task_id AND c.status NOT IN ('COMPLETED','CANCELLED'))
  AND NOT EXISTS (SELECT 1 FROM production_work_report w
                  WHERE w.task_id = t.task_id AND w.report_status = 'PENDING')
  AND t.task_quantity <= (SELECT COALESCE(SUM(w2.qualified_quantity),0)
                          FROM production_work_report w2
                          WHERE w2.task_id = t.task_id AND w2.report_status = 'APPROVED')
                       + (SELECT COALESCE(SUM(c2.task_quantity),0)
                          FROM production_task c2
                          WHERE c2.parent_task_id = t.task_id AND c2.status = 'COMPLETED');
