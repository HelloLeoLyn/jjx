-- ============================================================================
-- 139_event_template_bizno.sql
-- 任务码：dev-20260918-010
--        （编号原为 137，与并发会话的 137_iqc_batch_identity_propagation.sql 撞号，
--          按公约「后建者改号」改为 139，并同步 sys_config.ops.schema.applied；内容未变。）
--
-- 目的：配合代码改动（把业务单号塞进事件 payload），把这三条事件的模板从
--       「内部编号」升级为「业务单号」，并对齐 138 号迁移定的通用模板。
--
-- 代码侧改动（本次同任务）：
--   purchase.received                        -> 手写 payload，新增 orderNo / supplierName / receiveCount
--   inventory.inbound.created_from_purchase  -> 手写 payload，新增 inboundNo / purchaseOrderNo / supplierName；
--                                               bizId 由 purchaseOrderId 改为 inboundId（让「去处理」落到入库单）
--   inventory.inbound.confirmed              -> 手写 payload，新增 inboundNo / sourceNo / supplierName / operatorName
--
-- ⚠ 前置：代码要重新打包 + 重启后端才生效。重启前若又触发收货/入库，
--         通知里会看到 {orderNo} / {inboundNo} 这类占位符原样显示（老 jar 的 payload 没这些字段）。
--
-- 幂等：UPDATE 直接写目标值，可重复执行。
-- ============================================================================

-- 1) 采购到货（通知）—— 收件人 [26, 23, 33]
UPDATE sys_event_config
   SET title = '采购单 {orderNo} 已到货，请安排来料检验与入库',
       content = '① 采购单 {orderNo}（供应商 {supplierName}）已完成收货。② 未完成来料检验就不能入库过账，会卡住后续生产领料。③ 请到「库存管理 → 入库管理」找到该单并提交来料检验；点本通知「去处理」可直达采购单。'
 WHERE event_code = 'purchase.received';

-- 2) 采购生成入库单（通知 + 待办）—— 收件人 [23, 33]
UPDATE sys_event_config
   SET title = '入库单【{inboundNo}】已生成，请收货并提交来料检验',
       content = '① 采购单 {sourceNo} 已生成入库单【{inboundNo}】（供应商 {supplierName}）。② 入库单必须先完成来料检验才能过账，未过账的物料不可领用。③ 请到「库存管理 → 入库管理」核对该单并提交检验；点「去处理」直达入库单。'
 WHERE event_code = 'inventory.inbound.created_from_purchase';

-- 3) 入库单确认入库（通知 + 待办）—— 收件人 [23, 28, 29, 30]
UPDATE sys_event_config
   SET title = '入库单【{inboundNo}】已入库过账，库存已可用',
       content = '① 入库单【{inboundNo}】（采购单 {sourceNo}，供应商 {supplierName}）已完成入库过账，操作人 {operatorName}。② 物料已可领用，生产可据此安排领料/开工；仓管无需再处理。③ 生产请到「生产管理 → 生产订单」安排领料；库存请到「库存管理 → 入库管理」查看；点「去处理」直达。'
 WHERE event_code = 'inventory.inbound.confirmed';

-- ============================================================================
-- 执行后自检：
--   SELECT event_code, title FROM sys_event_config
--    WHERE event_code IN ('purchase.received','inventory.inbound.created_from_purchase',
--                         'inventory.inbound.confirmed') ORDER BY event_code;
-- ============================================================================
