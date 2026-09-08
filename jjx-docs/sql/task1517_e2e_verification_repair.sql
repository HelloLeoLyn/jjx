-- Task 1517 已生成验证数据的补数脚本
-- 修复「还有100件任务未分配或未完成」：Task 页面投影需要 APPROVED 报工事实。
-- 只处理最新一张 TASK1517_E2E_FIXTURE 工单，已有报工时不重复插入。

SET @order_id = (SELECT order_id FROM production_order
                 WHERE remark = 'TASK1517_E2E_FIXTURE' ORDER BY order_id DESC LIMIT 1);
SET @order_no = (SELECT order_no FROM production_order WHERE order_id = @order_id);
SET @assignee_id = 1;
SET @reporter_name = COALESCE((SELECT NULLIF(nick_name, '') FROM sys_user WHERE user_id = @assignee_id),
                              (SELECT user_name FROM sys_user WHERE user_id = @assignee_id));

INSERT INTO production_work_report
    (report_no, order_id, order_no, execution_id, task_id, reporter_id, reporter_name,
     qualified_quantity, defective_quantity, labor_hours, machine_hours, report_time,
     remark, report_status, reviewer_id, reviewer_name, review_time,
     create_by, create_time, update_by, update_time)
SELECT CONCAT('WR-E2E1517-FIX-', t.task_id), @order_id, @order_no, t.execution_id, t.task_id,
       @assignee_id, @reporter_name, 100, 0, 0, 0, NOW(), 'TASK1517_E2E_FIXTURE_REPAIR',
       'APPROVED', @assignee_id, @reporter_name, NOW(), 'admin', NOW(), 'admin', NOW()
FROM production_task t
WHERE t.execution_id IN (
    SELECT execution_id FROM production_operation_execution WHERE order_id = @order_id
)
AND NOT EXISTS (
    SELECT 1 FROM production_work_report wr
    WHERE wr.task_id = t.task_id AND wr.report_status IN ('PENDING', 'APPROVED')
);

SELECT po.order_no, e.process_order, e.process_name, t.task_id,
       t.task_quantity, COALESCE(SUM(wr.qualified_quantity + wr.defective_quantity), 0) AS approved_quantity,
       CASE WHEN COALESCE(SUM(wr.qualified_quantity + wr.defective_quantity), 0) = t.task_quantity
            THEN 'PASS' ELSE 'FAIL' END AS check_result
FROM production_order po
JOIN production_operation_execution e ON e.order_id = po.order_id
JOIN production_task t ON t.execution_id = e.execution_id AND t.parent_task_id IS NULL
LEFT JOIN production_work_report wr ON wr.task_id = t.task_id AND wr.report_status = 'APPROVED'
WHERE po.order_id = @order_id
GROUP BY po.order_no, e.process_order, e.process_name, t.task_id, t.task_quantity
ORDER BY e.process_order;
