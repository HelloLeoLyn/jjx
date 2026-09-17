-- ============================================================================
-- 131: 历史档案记录独立识别时间 —— dev-20260917-022
-- 识别时间不能复用 update_time，后者还会被人工保存草稿更新。
-- ============================================================================
USE `jjx_erp_db`;

ALTER TABLE engineering_archive_import
    ADD COLUMN recognize_time datetime NULL COMMENT '最近一次 OCR 识别完成时间' AFTER recognize_status;

SELECT '131 历史档案识别时间字段创建完成' AS check_point;
