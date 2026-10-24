-- dev-20261024-004 阶段二：归类闭环与规则库（本轮仅准备及jjx_dryrun_308演练）。
-- risk: high；DDL隐式提交，不可用事务回滚。
-- 前置：ops.schema.version=307；305/306/307已记账；inventory_material为48列，
-- 当前1607条中1041条category_id非空、566条为空；两个新列和规则表尚不存在。
-- 正式应用须用户先手工备份（排除hr_employee），另行授权经db-migrate.sh应用；
-- 本脚本不自行修改sys_config版本账本、不重编历史物料编码、不修改属性或库存。
-- 回滚：先停止使用新接口并配套回退代码；导出应用后的规则及归类确认数据供核对，
-- 再从用户应用前备份按material_id恢复category_id/template_id及需要恢复的业务值，
-- 删除idx_material_classify、class_source、class_confirmed和规则表（人工审核后执行）。
-- 仅删除新增结构会丢失归类轨迹，不等于恢复应用后被人工修改的分类。

SELECT DATABASE() AS target_schema;
SELECT config_value AS schema_version FROM sys_config WHERE config_key='ops.schema.version';
SELECT COUNT(*) AS total, SUM(category_id IS NOT NULL) AS categorized,
       SUM(category_id IS NULL) AS unclassified FROM inventory_material;

ALTER TABLE inventory_material
  ADD COLUMN class_source VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NULL COMMENT 'RULE/MANUAL/IMPORT，为空表示未归类',
  ADD COLUMN class_confirmed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '人工确认标记',
  ADD INDEX idx_material_classify (class_source, class_confirmed);

CREATE TABLE inventory_material_classify_rule (
  id BIGINT NOT NULL AUTO_INCREMENT,
  keyword VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字面关键词，不支持正则',
  match_field VARCHAR(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ANY' COMMENT 'NAME/SPEC/ANY',
  category_code VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  priority INT NOT NULL DEFAULT 0 COMMENT '数值越大越优先，同优先级按id升序',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  create_by VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_classify_keyword_category (keyword, category_code),
  CONSTRAINT chk_classify_match_field CHECK (match_field IN ('NAME','SPEC','ANY')),
  CONSTRAINT chk_classify_enabled CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='物料关键词归类规则';

UPDATE inventory_material
   SET class_source=CASE WHEN category_id IS NOT NULL THEN 'RULE' ELSE NULL END,
       class_confirmed=0;

SELECT class_source, class_confirmed, COUNT(*) AS material_count
  FROM inventory_material GROUP BY class_source, class_confirmed;
