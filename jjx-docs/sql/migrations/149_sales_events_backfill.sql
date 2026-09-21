-- ============================================================================
-- 149_sales_events_backfill.sql
-- 任务码：dev-20260921-009
--
-- 目的：给销售模块「前端在用、但动作不发事件」的 10 处补事件配置行，
--       让这些动作能按配置通知到人（此前是完全静默、无通知无任务）。
--
-- 背景（实测盘点，2026-09-21）：全站 358 个写接口中 163 个无事件；销售模块 86 个写接口里
--   在用且无事件 10 处：收款单改/删、退货审核/驳回/退款、订单完成、报价改单、询价发送/接受/拒绝。
--   代码侧同任务已补手写 payload 发布（com.jjx.event.EventPublishSupport），
--   因 @Event 注解取不到业务单号（delete(Long id)、approve(Long returnId…)），
--   模板里的 {receiptNo}/{returnNo}/{inquiryNo} 等必须由手写 payload 提供，否则会原样显示。
--
-- 收件人口径：沿用同族既有事件（sales.invoice.updated/deleted → [20]；
--   order.* → [20]/[21]）。SALES 业务操作=20、SALES 审核员=21，可在事件配置页按需调整。
--
-- 幂等：INSERT ... SELECT ... WHERE NOT EXISTS（按 event_code 去重），可重复执行。
-- 前置：后端重新打包重启后事件才会发出；本迁移只是先把配置行就位，不影响现有流程。
--
-- 执行后自检：
--   SELECT event_code,event_name,event_type,target_role,is_enabled FROM sys_event_config
--    WHERE event_code IN ('sales.receipt.updated','sales.receipt.deleted','sales.return.approved',
--          'sales.return.rejected','sales.return.refunded','order.completed','quotation.modified',
--          'inquiry.sent','inquiry.accepted','inquiry.rejected') ORDER BY event_code;
-- ============================================================================

INSERT INTO sys_event_config
  (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
   target_role, title, content, exclude_trigger, create_time, update_time)
SELECT 'sales.receipt.updated', '收款单修改', 'sales', 'notification', 'biz', 'high', 1,
       '[20, 21]', '收款单【{receiptNo}】已修改', '① 收款单【{receiptNo}】（客户 {customerName}）收款金额被改为 {receiptAmount}（原 {oldReceiptAmount}）。② 收款金额直接影响订单收款状态与对账结果，改动需复核。③ 请到「销售管理 → 收款单管理」核对该单。', 0, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'sales.receipt.updated');

INSERT INTO sys_event_config
  (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
   target_role, title, content, exclude_trigger, create_time, update_time)
SELECT 'sales.receipt.deleted', '收款单删除', 'sales', 'notification', 'biz', 'high', 1,
       '[20, 21]', '收款单【{receiptNo}】已删除', '① 收款单【{receiptNo}】（客户 {customerName}，金额 {receiptAmount}）已被删除。② 删除后订单的已收/未收金额会重新计算，若属误删需及时恢复。③ 请到「销售管理 → 收款单管理」核对。', 0, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'sales.receipt.deleted');

INSERT INTO sys_event_config
  (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
   target_role, title, content, exclude_trigger, create_time, update_time)
SELECT 'sales.return.approved', '销售退货审核通过', 'sales', 'notification', 'biz', 'normal', 1,
       '[20, 21]', '退货单【{returnNo}】审核通过', '① 退货单【{returnNo}】（客户 {customerName}，退货金额 {totalAmount}）已审核通过。② 下一步等仓库收到退货、确认收货后才会退款。③ 请到「销售管理 → 退货管理」推进收货。', 0, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'sales.return.approved');

INSERT INTO sys_event_config
  (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
   target_role, title, content, exclude_trigger, create_time, update_time)
SELECT 'sales.return.rejected', '销售退货审核驳回', 'sales', 'notification', 'biz', 'normal', 1,
       '[20]', '退货单【{returnNo}】审核驳回', '① 退货单【{returnNo}】（客户 {customerName}）审核被驳回。② 驳回后仍停留在申请中状态，需修改后重新提交。③ 驳回原因：{approveRemark}；请到「销售管理 → 退货管理」查看。', 0, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'sales.return.rejected');

INSERT INTO sys_event_config
  (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
   target_role, title, content, exclude_trigger, create_time, update_time)
SELECT 'sales.return.refunded', '销售退货退款', 'sales', 'notification', 'biz', 'high', 1,
       '[20, 21]', '退货单【{returnNo}】已退款 {refundAmount}', '① 退货单【{returnNo}】（客户 {customerName}）已退款 {refundAmount}。② 订单已收金额已相应扣减、付款状态已重算。③ 请到「销售管理 → 退货管理」核对退款金额。', 0, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'sales.return.refunded');

INSERT INTO sys_event_config
  (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
   target_role, title, content, exclude_trigger, create_time, update_time)
SELECT 'order.completed', '订单完成', 'sales', 'notification', 'biz', 'normal', 1,
       '[20]', '销售订单【{orderNo}】已完成', '① 销售订单【{orderNo}】（客户 {customerName}）已标记完成。② 完成后不再有生产/发货动作，如有遗漏请及时处理。③ 请到「销售管理 → 销售订单」查看该单。', 0, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'order.completed');

INSERT INTO sys_event_config
  (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
   target_role, title, content, exclude_trigger, create_time, update_time)
SELECT 'quotation.modified', '报价单改单', 'sales', 'notification', 'biz', 'normal', 1,
       '[20]', '报价单【{quotationNo}】已回到改单状态', '① 报价单【{quotationNo}】（客户 {customerName}）已改单，状态回到「改单中」。② 改单后必须重新提交审核才能继续流转（发送/转订单）。③ 请到「销售管理 → 报价单管理」继续编辑。', 0, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'quotation.modified');

INSERT INTO sys_event_config
  (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
   target_role, title, content, exclude_trigger, create_time, update_time)
SELECT 'inquiry.sent', '询价单发送客户', 'sales', 'notification', 'biz', 'normal', 1,
       '[20]', '询价单【{inquiryNo}】已发送客户', '① 询价单【{inquiryNo}】（客户 {customerName}）已发送给客户。② 等客户回复后再登记接受/拒绝，接受后即可转报价。③ 请到「销售管理 → 询价单管理」跟踪。', 0, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'inquiry.sent');

INSERT INTO sys_event_config
  (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
   target_role, title, content, exclude_trigger, create_time, update_time)
SELECT 'inquiry.accepted', '询价客户已接受', 'sales', 'both', 'biz', 'high', 1,
       '[20]', '询价单【{inquiryNo}】客户已接受，请转报价', '① 客户已接受询价单【{inquiryNo}】（客户 {customerName}）。② 询价转报价是后续打样/生产的前提，不要漏做。③ 请到「销售管理 → 询价单管理」执行「转报价」。', 0, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'inquiry.accepted');

INSERT INTO sys_event_config
  (event_code, event_name, biz_module, event_type, kanban_module, priority, is_enabled,
   target_role, title, content, exclude_trigger, create_time, update_time)
SELECT 'inquiry.rejected', '询价客户已拒绝', 'sales', 'notification', 'biz', 'normal', 1,
       '[20]', '询价单【{inquiryNo}】客户已拒绝', '① 客户拒绝/未接受询价单【{inquiryNo}】（客户 {customerName}）。② 该询价不再向前流转，如需继续可重新沟通或另开询价。③ 请到「销售管理 → 询价单管理」查看。', 0, NOW(), NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_event_config WHERE event_code = 'inquiry.rejected');
