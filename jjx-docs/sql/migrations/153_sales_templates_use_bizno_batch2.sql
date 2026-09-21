-- ============================================================================
-- 153_sales_templates_use_bizno_batch2.sql
-- 任务码：dev-20260921-013（阶段 1-c 第二批：订单族 + 销售其余家族）
--
-- 目的：把销售模块余下事件的模板统一成业务单号 {bizNo}
--       ① 标题里原本没有单号的（订单族 8 条）补上【{bizNo}】；
--       ② 标题/正文里用 {bizId}（内部编号）或各家族专名（{orderNo}/{inquiryNo}/{receiptNo}/{returnNo}）
--          的，统一改写成 {bizNo}（payload 里这些键同值，改后显示不变、写法统一）。
--
-- 前置（同任务代码侧已完成，需重启后端生效）：
--   订单族 9 个动作改手写 payload（OrderStatusServiceImpl.publishOrderEvent，带 orderNo）；
--   order.production_started 改手写 payload（OrderServiceImpl.createInstances）；
--   样品 confirmed/restarted/cancelled 与全部带 orderNo 的 @Event params 补 bizNo（9 处）；
--   发票 update 加 params bizNo=#invoice.invoiceNo、delete 改手写；发货签收 receive 改手写（带 deliveryNo）。
--
-- 不改：sales.customer.*（payload 还没有客户标识键，第三批处理）、sample.transferred（返回值非订单对象，
--       暂留 {bizId}）、order.delivering（该事件在配置表里本来就没有配置行，属未配置事件）。
--
-- 幂等：显式 SET 以「原标题」为守卫；批量 REPLACE 以 LIKE '%{旧键}%' 为守卫，可重复执行。
--
-- 自检：
--   SELECT event_code, title FROM sys_event_config
--    WHERE event_code LIKE 'order.%' OR event_code LIKE 'sample.%' OR event_code LIKE 'sales.%'
--       OR event_code LIKE 'inquiry.%' ORDER BY event_code;
--   （销售家族应无 {bizId}/{orderNo}/{inquiryNo}/{receiptNo}/{returnNo}；sample.transferred 与 sales.customer.* 除外）
-- ============================================================================

-- ① 订单族：标题补业务单号（原标题为守卫）
UPDATE sys_event_config SET title = '销售订单【{bizNo}】已提交审核' WHERE event_code='order.submitted'      AND title='销售订单已提交审核';
UPDATE sys_event_config SET title = '销售订单【{bizNo}】审核通过'   WHERE event_code='order.approved'       AND title='销售订单审核通过';
UPDATE sys_event_config SET title = '销售订单【{bizNo}】审核驳回'   WHERE event_code='order.rejected'       AND title='销售订单审核驳回';
UPDATE sys_event_config SET title = '销售订单【{bizNo}】已重新提交' WHERE event_code='order.resubmitted'    AND title='销售订单已重新提交';
UPDATE sys_event_config SET title = '销售订单【{bizNo}】已取消'     WHERE event_code='order.cancelled'      AND title='销售订单已取消';
UPDATE sys_event_config SET title = '销售订单【{bizNo}】已确认'     WHERE event_code='order.confirmed'      AND title='销售订单已确认';
UPDATE sys_event_config SET title = '销售订单【{bizNo}】已完成'     WHERE event_code='order.completed'      AND title='销售订单【{orderNo}】已完成';
UPDATE sys_event_config
   SET title   = '订单【{bizNo}】已提交生产',
       content = '订单【{bizNo}】已提交生产，请安排排产'
 WHERE event_code='order.production_started' AND title='订单提交生产';

-- ② 销售家族：单号类占位符统一为 {bizNo}（标题 + 正文）
UPDATE sys_event_config
   SET title = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(title,
                 '{bizId}', '{bizNo}'), '{orderNo}', '{bizNo}'), '{inquiryNo}', '{bizNo}'),
                 '{receiptNo}', '{bizNo}'), '{returnNo}', '{bizNo}'),
       content = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(content,
                 '{bizId}', '{bizNo}'), '{orderNo}', '{bizNo}'), '{inquiryNo}', '{bizNo}'),
                 '{receiptNo}', '{bizNo}'), '{returnNo}', '{bizNo}')
 WHERE event_code IN (
        'order.review_started',
        'inquiry.converted', 'inquiry.sent', 'inquiry.accepted', 'inquiry.rejected',
        'sales.invoice.updated', 'sales.invoice.deleted', 'sales.delivery.received',
        'sales.receipt.updated', 'sales.receipt.deleted',
        'sales.return.approved', 'sales.return.rejected', 'sales.return.refunded', 'sales.return.received',
        'sample.created', 'sample.submitted', 'sample.started', 'sample.ready', 'sample.accepted',
        'sample.rejected_by_engineering', 'sample.transfer.remind',
        'sample.confirmed', 'sample.restarted', 'sample.cancelled')
   AND (title LIKE '%{bizId}%' OR title LIKE '%{orderNo}%' OR title LIKE '%{inquiryNo}%'
        OR title LIKE '%{receiptNo}%' OR title LIKE '%{returnNo}%'
        OR content LIKE '%{bizId}%' OR content LIKE '%{orderNo}%' OR content LIKE '%{inquiryNo}%'
        OR content LIKE '%{receiptNo}%' OR content LIKE '%{returnNo}%');
