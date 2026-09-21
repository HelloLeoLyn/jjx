-- ============================================================================
-- 150_sales_return_received_event.sql
-- 任务码：dev-20260921-010
--
-- 目的：补「销售退货收货」事件配置行。
--   退货收货会联动生成退货入库单并把货加回库存（SalesReturnServiceImpl.receive），
--   货与库存都动了，此前没有任何通知（同批已补的还有退货审核/驳回/退款）。
--
-- 收件人口径：沿用同族（sales.return.* → SALES 业务操作 20 + SALES 审核员 21）。
-- 幂等：INSERT ... WHERE NOT EXISTS（按 event_code 去重）。
-- 前置：后端重新打包重启后事件才会发出（代码侧同任务已在 receive() 里发 sales.return.received）。
--
-- 自检：
--   SELECT event_code,event_name,event_type,target_role,is_enabled FROM sys_event_config
--    WHERE event_code = 'sales.return.received';
-- ============================================================================

INSERT INTO sys_event_config
  (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
   target_role, title, content, exclude_trigger, create_time, update_time)
SELECT 'sales.return.received', '销售退货收货', 'sales', 'notification', 'biz', 'normal', 1,
       '[20, 21]', '退货单【{returnNo}】已收货入库',
       '① 退货单【{returnNo}】（客户 {customerName}）已收货，退货数量 {totalQuantity}，系统已联动生成退货入库单并把货加回库存。② 下一步可对该退货单执行退款（钱还没退）。③ 请到「销售管理 → 退货管理」核对并推进退款。',
       0, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'sales.return.received');
