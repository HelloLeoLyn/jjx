-- ============================================================
-- Migration: V20260810_004__sample_process_index.sql
-- 打样工作台下标工序（DEV-777，仿工艺路线 RouteItemIconEditor）
-- sales_sample_process 加 has_index / index_number
-- 幂等：可重复执行
-- ============================================================

-- 是否带下标（冗余，落库时按 std_process_id 关联标准工序 has_index 填充）
ALTER TABLE sales_sample_process
    ADD COLUMN has_index TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否带下标：0-不带,1-带（关联标准工序）' AFTER std_process_id;

-- 下标数字（带下标工序的下标值，如4=④）
ALTER TABLE sales_sample_process
    ADD COLUMN index_number INT NULL COMMENT '下标数字（带下标工序的下标值）' AFTER has_index;
