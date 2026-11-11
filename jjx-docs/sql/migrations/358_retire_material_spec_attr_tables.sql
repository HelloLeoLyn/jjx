-- 358 退役遗留表 inventory_material_spec_attr_def / inventory_material_spec_value
-- 任务码 dev-20261111-011（OpenClaw/dahuang）；用户 2026-11-11 选定方案 A（退役）
-- 背景：两表自 dev-20261024-007 起废弃，属性真源改为 inventory_material.attribute_json；
--       全仓 java/xml/vue/ts 零引用；pre-push 表数基线将其视为“未批准新表”而拦截推送。
-- 归档：jjx-docs/sql/backups/inventory_material_spec_attr_def_retire_20261111-1724.sql
--       sha256 b14bbcf754e1c6fd29a6ec5e1b6cc4d896c5a584b8d50da11eacaa5885455b6d
--       导出时行数：attr_def 27 / spec_value 0
-- 风险：high（删表不可逆）
-- 回退：用上面的归档 SQL 恢复两表，再另行决定基线口径（不可只回退一半）
-- 前置：当天手工全库备份；执行方式 scripts/db-migrate.sh 358_... --yes --task dev-20261111-011 --backup <备份.sql>
-- 执行后：bash scripts/check-model-baseline.sh 应退出 0，无需写 approvedNewTables

SET @def_exists = (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_material_spec_attr_def');
SET @val_exists = (SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_material_spec_value');

SET @count_def = IF(@def_exists = 1, 'SELECT COUNT(*) INTO @def_rows FROM inventory_material_spec_attr_def', 'SET @def_rows = 0');
PREPARE c1 FROM @count_def; EXECUTE c1; DEALLOCATE PREPARE c1;

SET @count_val = IF(@val_exists = 1, 'SELECT COUNT(*) INTO @val_rows FROM inventory_material_spec_value', 'SET @val_rows = 0');
PREPARE c2 FROM @count_val; EXECUTE c2; DEALLOCATE PREPARE c2;

-- 行数与归档不一致即中止（故意引用不存在的表制造错误），避免删掉归档之外的数据
SET @guard = IF(@def_exists = 0 OR (@def_rows = 27 AND @val_rows = 0), 'SELECT 1',
  'SELECT * FROM `__358_abort_rowcount_mismatch_review_archive__`');
PREPARE g FROM @guard; EXECUTE g; DEALLOCATE PREPARE g;

DROP TABLE IF EXISTS inventory_material_spec_value;
DROP TABLE IF EXISTS inventory_material_spec_attr_def;
