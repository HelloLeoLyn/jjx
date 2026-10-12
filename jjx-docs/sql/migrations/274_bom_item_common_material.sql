-- 274_bom_item_common_material.sql
-- dev-20261012-039：BOM 材料「整单通用」标记（不挂工序的合法标记）。
-- 背景：BOM 材料允许 process_id 为空（如整单通用料/易撕贴），但下游按工序挂料时会静默丢弃。
--       增加显式标记后：未挂工序且未标通用 → 提交审核被拦（不静默、不丢数据）。
-- 幂等：仅当列不存在时新增；重复执行为空操作。

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'engineering_bom_item'
    AND COLUMN_NAME = 'is_common'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE engineering_bom_item ADD COLUMN is_common tinyint(1) NOT NULL DEFAULT 0 COMMENT ''整单通用材料(不挂工序)：0否 1是'' AFTER process_name',
  'DO 0');
PREPARE stmt_274 FROM @ddl;
EXECUTE stmt_274;
DEALLOCATE PREPARE stmt_274;
