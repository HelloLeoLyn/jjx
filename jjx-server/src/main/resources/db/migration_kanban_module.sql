-- =====================================================
-- 看板模块接入：事件→任务路由配置
-- sys_event_config 加 kanban_module / priority
-- sys_task 加 kanban_module
-- =====================================================

-- 事件配置：看板模块（默认 office）+ 优先级（默认 normal）
ALTER TABLE sys_event_config
    ADD COLUMN kanban_module VARCHAR(20) NOT NULL DEFAULT 'office' COMMENT '看板模块: office/emergency/production/dev' AFTER event_type,
    ADD COLUMN priority VARCHAR(10) NOT NULL DEFAULT 'normal' COMMENT '任务优先级: urgent/high/normal/low' AFTER kanban_module;

-- 任务表：看板模块
ALTER TABLE sys_task
    ADD COLUMN kanban_module VARCHAR(20) DEFAULT 'office' COMMENT '看板模块: office/emergency/production/dev' AFTER task_type;

-- =====================================================
-- 初始配置建议（用户确认过）：
--   order.review_started = office/high
--   purchase.submitted   = office/high
--   stock.low            = emergency/urgent
--   stock.over           = emergency/urgent
--   其余审批类           = office/normal
-- =====================================================
UPDATE sys_event_config SET kanban_module = 'office', priority = 'high'   WHERE event_code IN ('order.review_started', 'purchase.submitted');
UPDATE sys_event_config SET kanban_module = 'emergency', priority = 'urgent' WHERE event_code IN ('stock.low', 'stock.over');
-- 其余保持默认 office/normal
