-- ============================================================
-- Migration: V20260810_001__routing_item_index_dependency_optional.sql
-- 工艺路线补充功能-批次1（下标工序/跨组依赖/可选工序标记）
-- 1) engineering_standard_process 加 has_index（标准工序是否带下标）
-- 2) engineering_routing_item 加 index_number/precondition/precondition_display/is_optional
-- 幂等：IF NOT EXISTS 判断，可重复执行
-- ============================================================

-- ------------------------------------------------------------
-- 1. 标准工序：是否带下标（0=不带，1=带；带下标的工序在路线里需要输入下标数字）
-- ------------------------------------------------------------
ALTER TABLE engineering_standard_process
    ADD COLUMN has_index TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否带下标：0-不带,1-带' AFTER icon;

-- ------------------------------------------------------------
-- 2. 工艺路线明细：下标数字 / 前置依赖 / 可选标记
-- ------------------------------------------------------------
-- 下标数字：带下标的工序在路线中的下标（如 4 显示为 ④）
ALTER TABLE engineering_routing_item
    ADD COLUMN index_number INT NULL COMMENT '下标数字（带下标工序的下标值，如4=④）' AFTER process_category;

-- 前置依赖标识（如 PANEL_4：类别_下标）
ALTER TABLE engineering_routing_item
    ADD COLUMN precondition VARCHAR(100) NULL COMMENT '前置依赖标识（如 PANEL_4=面板④）' AFTER index_number;

-- 前置依赖显示名（如"面板④ 面板冲型"）
ALTER TABLE engineering_routing_item
    ADD COLUMN precondition_display VARCHAR(200) NULL COMMENT '前置依赖显示名（如：面板④ 面板冲型）' AFTER precondition;

-- 可选工序标记（0=必做,1=可选）
ALTER TABLE engineering_routing_item
    ADD COLUMN is_optional TINYINT(1) NOT NULL DEFAULT 0 COMMENT '可选工序：0-必做,1-可选' AFTER precondition_display;
