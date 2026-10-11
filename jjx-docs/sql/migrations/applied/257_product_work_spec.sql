-- dev-20261010-010 产品作业规范工程录入；不新增表，不存BOM/工艺路线副本。
-- 部署前用户手工备份（排除hr_employee），通过 scripts/db-migrate.sh 执行。
-- JSON真源：凹凸参数、加工要求、独立刀模位置、结构图附件ID、工程变更记录、发行及确认元数据。
-- 普通产品spec_json仍维护原规格数组，避免共用字段造成互相覆盖。
SET @work_spec_ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'product' AND column_name = 'work_spec_json'),
  'SELECT 1',
  'ALTER TABLE product ADD COLUMN work_spec_json JSON NULL COMMENT ''工程录入作业规范附加内容（引用BOM/路线/结构图）'''
);
PREPARE work_spec_statement FROM @work_spec_ddl;
EXECUTE work_spec_statement;
DEALLOCATE PREPARE work_spec_statement;
