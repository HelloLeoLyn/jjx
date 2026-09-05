-- 63_event_close_source_events_phase2.sql
-- 事件办结自动销任务·阶段二配对补全（非审核流程，dev-20260905-018/任务对应1472二期）
-- 规则：本事件触发时，关闭 source_event 命中、同 biz_id 的 office 待办任务

-- 样品单：请安排打样 → 工程接单/开始打样
UPDATE sys_event_config SET close_source_events='sample.created' WHERE event_code IN ('sample.accepted','sample.started');

-- 样品单：资料转移提醒(DEV-1228 17/18) 与 客户确认后建档提醒(16) → 资料转移完成
UPDATE sys_event_config SET close_source_events='sample.transfer.remind,sample.confirmed' WHERE event_code='sample.transferred';

-- 库存预警类：订单缺料/低库存/超上限 处理完成 → 预警处理事件
UPDATE sys_event_config SET close_source_events='stock.shortage,stock.low,stock.over' WHERE event_code='inventory.alert.processed';
