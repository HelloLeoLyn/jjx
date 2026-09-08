-- Task 1517 / dev-20260905-036 E2E 验证数据
-- 用途：执行后在页面完成最后一道工序，验证工单完成数量回写及完工入库数量。
-- 范围：只新增带 E2E1517 标识的数据，不修改、不删除现有业务数据。
-- 执行前：按 jjx-docs/standards/CONVENTIONS.md 备份当前数据库。
-- 执行后：记住末尾输出的 E2E1517-* 工单号，再按 task1517_e2e_verification_check.sql 操作和核验。

SET @product_id = (SELECT product_id FROM product ORDER BY product_id LIMIT 1);
SET @bom_id = (SELECT bom_id FROM engineering_bom
               WHERE product_id = @product_id AND approve_status = 3
               ORDER BY is_current DESC, bom_id DESC LIMIT 1);
SET @routing_id = (SELECT routing_id FROM engineering_routing
                   WHERE product_id = @product_id AND approve_status = 3
                   ORDER BY is_current DESC, routing_id DESC LIMIT 1);
SET @process_1 = (SELECT process_id FROM engineering_standard_process WHERE process_id = 17 LIMIT 1);
SET @process_2 = (SELECT process_id FROM engineering_standard_process WHERE process_id = 29 LIMIT 1);
SET @process_3 = (SELECT process_id FROM engineering_standard_process WHERE process_id = 24 LIMIT 1);
SET @assignee_id = (SELECT user_id FROM sys_user WHERE user_id = 1 LIMIT 1);
SET @warehouse_id = (SELECT warehouse_id FROM inventory_warehouse WHERE status = 1 ORDER BY warehouse_id LIMIT 1);

DELIMITER //
DROP PROCEDURE IF EXISTS assert_task1517_fixture_ready//
CREATE PROCEDURE assert_task1517_fixture_ready()
BEGIN
    IF DATABASE() IS NULL OR DATABASE() <> 'jjx_erp_db' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '只能在 jjx_erp_db 执行';
    END IF;
    IF @product_id IS NULL OR @bom_id IS NULL OR @routing_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '缺少已批准的产品/BOM/工艺路线，无法生成任务1517验证数据';
    END IF;
    IF @process_1 IS NULL OR @process_2 IS NULL OR @process_3 IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '缺少验证所需标准工序17/29/24';
    END IF;
    IF @assignee_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '缺少ID=1的验证用户';
    END IF;
    IF @warehouse_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '缺少启用中的仓库，完工入库将无法生成';
    END IF;
END//
CALL assert_task1517_fixture_ready()//
DROP PROCEDURE assert_task1517_fixture_ready//
DELIMITER ;

SET @fixture_suffix = DATE_FORMAT(NOW(6), '%Y%m%d%H%i%s%f');
SET @order_no = CONCAT('E2E1517-', @fixture_suffix);
SET @trace_id = CONCAT('TRACE-E2E1517-', @fixture_suffix);
SET @product_code = (SELECT product_code FROM product WHERE product_id = @product_id);
SET @product_name = (SELECT product_name FROM product WHERE product_id = @product_id);
SET @product_unit = COALESCE((SELECT unit FROM product WHERE product_id = @product_id), 'PCS');
SET @bom_code = (SELECT bom_code FROM engineering_bom WHERE bom_id = @bom_id);
SET @routing_code = (SELECT routing_code FROM engineering_routing WHERE routing_id = @routing_id);
SET @reporter_name = COALESCE((SELECT NULLIF(nick_name, '') FROM sys_user WHERE user_id = @assignee_id),
                              (SELECT user_name FROM sys_user WHERE user_id = @assignee_id));

INSERT INTO production_order
    (trace_id, order_no, order_type, product_id, product_code, product_name, product_unit,
     bom_id, bom_code, routing_id, routing_code, planned_quantity, completed_quantity,
     finished_quantity, remaining_quantity, plan_start_date, plan_end_date, order_status,
     approval_status, priority, create_by, create_time, update_by, update_time, remark)
VALUES
    (@trace_id, @order_no, 'WORK_ORDER', @product_id, @product_code, @product_name, @product_unit,
     @bom_id, @bom_code, @routing_id, @routing_code, 100, 0, 0, 100,
     CURDATE(), DATE_ADD(CURDATE(), INTERVAL 7 DAY), 6, 2, 'MEDIUM',
     'admin', NOW(), 'admin', NOW(), 'TASK1517_E2E_FIXTURE');
SET @order_id = LAST_INSERT_ID();

-- 前两道工序已完成，每道合格100；最后一道执行中，等待页面点击“完成工序”。
INSERT INTO production_operation_execution
    (order_id, process_id, process_name, major_category, process_order,
     actual_start_time, actual_end_time, input_quantity, output_quantity,
     qualified_quantity, defective_quantity, execution_status, create_time, update_time)
VALUES
    (@order_id, @process_1, '面板', 'ASSEMBLY', 1,
     DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR), 100, 100, 100, 0, 4, NOW(), NOW());
SET @execution_1 = LAST_INSERT_ID();

INSERT INTO production_operation_execution
    (order_id, process_id, process_name, major_category, process_order,
     actual_start_time, actual_end_time, input_quantity, output_quantity,
     qualified_quantity, defective_quantity, execution_status, create_time, update_time)
VALUES
    (@order_id, @process_2, '上线', 'ASSEMBLY', 2,
     DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 1 HOUR), 100, 100, 100, 0, 4, NOW(), NOW());
SET @execution_2 = LAST_INSERT_ID();

INSERT INTO production_operation_execution
    (order_id, process_id, process_name, major_category, process_order,
     actual_start_time, input_quantity, output_quantity, qualified_quantity,
     defective_quantity, execution_status, create_time, update_time)
VALUES
    (@order_id, @process_3, '面板冲孔', 'ASSEMBLY', 3,
     DATE_SUB(NOW(), INTERVAL 30 MINUTE), 100, 100, 100, 0, 2, NOW(), NOW());
SET @execution_3 = LAST_INSERT_ID();

-- completeExecution 要求每个工序第一层任务已经完成。
INSERT INTO production_task
    (task_no, execution_id, parent_task_id, assignee_id, task_quantity,
     status, version, create_by, create_time, update_by, update_time)
VALUES
    (CONCAT(@order_no, '-P1-T1'), @execution_1, NULL, @assignee_id, 100, 'COMPLETED', 0, 'admin', NOW(), 'admin', NOW()),
    (CONCAT(@order_no, '-P2-T1'), @execution_2, NULL, @assignee_id, 100, 'COMPLETED', 0, 'admin', NOW(), 'admin', NOW()),
    (CONCAT(@order_no, '-P3-T1'), @execution_3, NULL, @assignee_id, 100, 'COMPLETED', 0, 'admin', NOW(), 'admin', NOW());

-- 任务详情的完成数量以已审批报工事实统计，仅把 Task 状态写成 COMPLETED 不足以通过前端完工预检。
INSERT INTO production_work_report
    (report_no, order_id, order_no, execution_id, task_id, reporter_id, reporter_name,
     qualified_quantity, defective_quantity, labor_hours, machine_hours, report_time,
     remark, report_status, reviewer_id, reviewer_name, review_time,
     create_by, create_time, update_by, update_time)
SELECT CONCAT('WR-E2E1517-', @fixture_suffix, '-1'), @order_id, @order_no, @execution_1, task_id,
       @assignee_id, @reporter_name, 100, 0, 0, 0, NOW(), 'TASK1517_E2E_FIXTURE',
       'APPROVED', @assignee_id, @reporter_name, NOW(), 'admin', NOW(), 'admin', NOW()
FROM production_task WHERE task_no = CONCAT(@order_no, '-P1-T1');

INSERT INTO production_work_report
    (report_no, order_id, order_no, execution_id, task_id, reporter_id, reporter_name,
     qualified_quantity, defective_quantity, labor_hours, machine_hours, report_time,
     remark, report_status, reviewer_id, reviewer_name, review_time,
     create_by, create_time, update_by, update_time)
SELECT CONCAT('WR-E2E1517-', @fixture_suffix, '-2'), @order_id, @order_no, @execution_2, task_id,
       @assignee_id, @reporter_name, 100, 0, 0, 0, NOW(), 'TASK1517_E2E_FIXTURE',
       'APPROVED', @assignee_id, @reporter_name, NOW(), 'admin', NOW(), 'admin', NOW()
FROM production_task WHERE task_no = CONCAT(@order_no, '-P2-T1');

INSERT INTO production_work_report
    (report_no, order_id, order_no, execution_id, task_id, reporter_id, reporter_name,
     qualified_quantity, defective_quantity, labor_hours, machine_hours, report_time,
     remark, report_status, reviewer_id, reviewer_name, review_time,
     create_by, create_time, update_by, update_time)
SELECT CONCAT('WR-E2E1517-', @fixture_suffix, '-3'), @order_id, @order_no, @execution_3, task_id,
       @assignee_id, @reporter_name, 100, 0, 0, 0, NOW(), 'TASK1517_E2E_FIXTURE',
       'APPROVED', @assignee_id, @reporter_name, NOW(), 'admin', NOW(), 'admin', NOW()
FROM production_task WHERE task_no = CONCAT(@order_no, '-P3-T1');

-- 执行完成后的预期：completed_quantity=300，finished_quantity=100，且自动生成一张待检FQC。
SELECT
    @order_id AS order_id,
    @order_no AS order_no,
    @execution_3 AS execution_id_to_complete,
    '生产管理 -> 工序执行 -> 搜索工单号 -> 对第3道工序点击完成工序' AS next_operation;

SELECT order_id, order_no, order_status, planned_quantity, completed_quantity,
       finished_quantity, remaining_quantity, remark
FROM production_order
WHERE order_id = @order_id;

SELECT execution_id, process_order, process_name, execution_status, qualified_quantity
FROM production_operation_execution
WHERE order_id = @order_id
ORDER BY process_order;
