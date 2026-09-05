-- 62_event_close_source_events.sql
-- 事件办结自动销任务（dev-20260905-016，任务1467 阶段一：审核闭环）
-- sys_event_config 增加 close_source_events（逗号分隔）：本事件触发时，关闭 source_event 命中列表、
-- 同一 biz_id、且仍为待办/进行中的 office 任务（LocalEventPublisher 消费）

ALTER TABLE sys_event_config
  ADD COLUMN close_source_events varchar(200) NULL COMMENT '触发时关闭的source_event(逗号分隔,同biz_id的office待办任务)' AFTER exclude_trigger;

-- 报价/销售订单
UPDATE sys_event_config SET close_source_events='quotation.submitted' WHERE event_code='quotation.reviewed';
UPDATE sys_event_config SET close_source_events='order.submitted,order.review_started' WHERE event_code='order.approved';
UPDATE sys_event_config SET close_source_events='order.submitted,order.review_started' WHERE event_code='order.rejected';
UPDATE sys_event_config SET close_source_events='order.submitted' WHERE event_code='order.cancelled';
-- 产品/BOM/工艺路线/菲林
UPDATE sys_event_config SET close_source_events='bom.submitted' WHERE event_code='bom.approved';
UPDATE sys_event_config SET close_source_events='product.submitted' WHERE event_code='product.approved';
UPDATE sys_event_config SET close_source_events='product.routing.submitted' WHERE event_code IN ('product.routing.approved','product.routing.rejected');
UPDATE sys_event_config SET close_source_events='product.film.submitted' WHERE event_code IN ('product.film.approved','product.film.rejected');
-- 样品单
UPDATE sys_event_config SET close_source_events='sample.submitted' WHERE event_code IN ('sample.approved','sample.rejected');
UPDATE sys_event_config SET close_source_events='sample.ready' WHERE event_code IN ('sample.sent','sample.confirmed','sample.rejected_by_customer');
-- 采购
UPDATE sys_event_config SET close_source_events='purchase.submitted' WHERE event_code='purchase.approved';
-- 入库/出库/调拨/盘点
UPDATE sys_event_config SET close_source_events='inventory.inbound.submitted' WHERE event_code IN ('inventory.inbound.approved','inventory.inbound.rejected','inventory.inbound.cancelled');
UPDATE sys_event_config SET close_source_events='inventory.outbound.submitted' WHERE event_code IN ('inventory.outbound.approved','inventory.outbound.rejected','inventory.outbound.cancelled');
UPDATE sys_event_config SET close_source_events='inventory.transfer.submitted' WHERE event_code IN ('inventory.transfer.approved','inventory.transfer.rejected','inventory.transfer.cancelled');
UPDATE sys_event_config SET close_source_events='inventory.stocktake.submitted' WHERE event_code='inventory.stocktake.approved';
-- 报工
UPDATE sys_event_config SET close_source_events='production.work-report.submitted' WHERE event_code IN ('production.work-report.approved','production.work-report.rejected');
