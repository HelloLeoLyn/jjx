-- Task 1517 / dev-20260905-036 页面 E2E 操作说明与结果核验
-- 先执行 task1517_e2e_verification_data.sql，本脚本可在每个操作阶段重复执行。
-- 默认核验最新一条测试工单；要查指定工单时，将下面子查询改成 order_no = 'E2E1517-...'。

SET @order_id = (
    SELECT order_id
    FROM production_order
    WHERE remark = 'TASK1517_E2E_FIXTURE'
    ORDER BY order_id DESC
    LIMIT 1
);

-- 页面操作：
-- 1. 生产管理 -> 工序执行，用下方 order_no 搜索，对第 3 道「面板冲孔」点击「完成工序」。
-- 2. 重新执行本脚本：工序状态应全部为 4，completed_quantity=300，finished_quantity=100，且出现 pending FQC。
-- 3. 生产管理 -> 质量管理，用 order_no 搜索 FQC；点击「判定」，填检验数量100、合格100、不合格0，判定「合格」。
-- 4. 生产管理 -> 生产工单，搜索 order_no，点击「完成」。
-- 5. 重新执行本脚本：工单状态应为 8，FINISH-* 入库单总数量及明细数量都应为100。

SELECT
    CASE WHEN @order_id IS NULL THEN '失败：未找到验证工单' ELSE '已找到验证工单' END AS fixture_check,
    @order_id AS order_id,
    (SELECT order_no FROM production_order WHERE order_id = @order_id) AS order_no;

SELECT
    order_id,
    order_no,
    order_status,
    planned_quantity,
    completed_quantity,
    finished_quantity,
    remaining_quantity,
    CASE
        WHEN order_status = 8 AND finished_quantity = 100 THEN 'PASS：工单已完成'
        WHEN completed_quantity = 300 AND finished_quantity = 100 THEN 'PASS：工序数量已回写，继续 FQC/完成工单'
        ELSE 'WAIT/FAIL：请核对当前操作阶段'
    END AS check_result
FROM production_order
WHERE order_id = @order_id;

SELECT
    execution_id,
    process_order,
    process_name,
    execution_status,
    qualified_quantity,
    CASE WHEN execution_status = 4 THEN 'PASS' ELSE 'WAIT' END AS check_result
FROM production_operation_execution
WHERE order_id = @order_id
ORDER BY process_order;

SELECT
    inspection_id,
    inspection_no,
    inspection_type,
    result,
    total_qty,
    pass_qty,
    fail_qty,
    CASE
        WHEN result = 'pass' AND pass_qty = 100 THEN 'PASS'
        WHEN result = 'pending' THEN 'WAIT：请在质量管理完成 FQC 判定'
        ELSE 'WAIT/FAIL'
    END AS check_result
FROM production_quality_inspection
WHERE order_id = @order_id AND inspection_type = 'FQC'
ORDER BY inspection_id DESC;

SELECT
    io.inbound_id,
    io.inbound_no,
    io.inbound_type,
    io.source_no,
    io.total_quantity,
    io.order_status,
    ii.item_id,
    ii.material_code,
    ii.quantity,
    CASE
        WHEN io.total_quantity = 100 AND ii.quantity = 100 THEN 'PASS'
        ELSE 'FAIL：完工入库数量不是100'
    END AS check_result
FROM inventory_inbound_order io
LEFT JOIN inventory_inbound_item ii ON ii.inbound_id = io.inbound_id
WHERE io.source_id = @order_id
  AND io.inbound_type = 'PRODUCTION_FINISH'
ORDER BY io.inbound_id DESC, ii.item_id;
