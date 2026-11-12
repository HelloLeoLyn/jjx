-- 361 BOM 材料来源方式（dev-20261112-008）
-- 用户 2026-11-12 裁决：新增独立列 consumption_mode，source_type(buy/make) 保持不变，领料判断零改动。
-- 新增：consumption_mode(roll_cut/sheet_nest/direct/continuous)、pitch_mm、pcs_per_pitch。
-- split 保留为历史文本，仅用于回填 pitch_mm，不删除、不改写。
-- 风险：medium（加列 + 一次性回填）；回退：停用前端入口并保留列，确认无引用后再删列。
-- 前置：当天手工全库备份（jjx-docs/sql/backups/jjx_erp_db_backup_20261112-0857.sql）。

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='engineering_bom_item' AND COLUMN_NAME='consumption_mode');
SET @ddl = IF(@c = 0,
  'ALTER TABLE engineering_bom_item ADD COLUMN consumption_mode varchar(20) NULL COMMENT ''材料来源方式：roll_cut/sheet_nest/direct/continuous'' AFTER source_type',
  'SELECT 1');
PREPARE s1 FROM @ddl; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='engineering_bom_item' AND COLUMN_NAME='pitch_mm');
SET @ddl = IF(@c = 0,
  'ALTER TABLE engineering_bom_item ADD COLUMN pitch_mm decimal(10,2) NULL COMMENT ''卷材跳距(mm)，仅 roll_cut 使用'' AFTER consumption_mode',
  'SELECT 1');
PREPARE s2 FROM @ddl; EXECUTE s2; DEALLOCATE PREPARE s2;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='engineering_bom_item' AND COLUMN_NAME='pcs_per_pitch');
SET @ddl = IF(@c = 0,
  'ALTER TABLE engineering_bom_item ADD COLUMN pcs_per_pitch int NOT NULL DEFAULT 1 COMMENT ''每跳产出片数（宽度方向排数），仅 roll_cut 使用'' AFTER pitch_mm',
  'SELECT 1');
PREPARE s3 FROM @ddl; EXECUTE s3; DEALLOCATE PREPARE s3;

-- 1) 跳距回填：只解析形如 80mm / 80 的历史 split 文本，解析不了保持 NULL
UPDATE engineering_bom_item
   SET pitch_mm = CAST(REPLACE(LOWER(TRIM(split)), 'mm', '') AS DECIMAL(10,2))
 WHERE pitch_mm IS NULL
   AND split IS NOT NULL
   AND TRIM(split) <> ''
   AND LOWER(TRIM(split)) REGEXP '^[0-9]+(\\.[0-9]+)?mm?$';

-- 2) 来源方式推断（只回填空值，不动 quantity/base/module）
UPDATE engineering_bom_item bi
  LEFT JOIN inventory_material m ON m.material_id = bi.material_id
   SET bi.consumption_mode = CASE
       WHEN bi.split IS NOT NULL AND TRIM(bi.split) <> '' THEN 'roll_cut'
       WHEN UPPER(IFNULL(m.unit, '')) IN ('M', 'M2') AND IFNULL(m.width_mm, 0) > 0 THEN 'roll_cut'
       WHEN IFNULL(m.width_mm, 0) > 0 AND IFNULL(m.length_mm, 0) > 0 THEN 'sheet_nest'
       ELSE 'direct'
     END
 WHERE bi.consumption_mode IS NULL;
