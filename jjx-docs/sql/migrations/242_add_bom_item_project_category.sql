-- dev-20261007-004
-- risk: low
-- BOM 明细加「项目」列（来源：界面手建卡 task_id=2622「bom修改/新增优化」第③条「项目是标准工序」）。
--   取值 = 标准工序（engineering_standard_process）；引用方式照 engineering_routing_item：
--   process_id（引用 engineering_standard_process.process_id）+ process_name（冗余快照，便于查询/展示）。
-- 影响：仅新增两列（可空，存量 3 行为 NULL）；不更新、不删除任何业务行。
-- 依赖：后端 EngineeringBomItem/DTO + 前端 BomItemEditor「项目」列必须与本列同批上线
--   （MyBatis-Plus selectList 会带出这两列，列不存在则 BOM 明细查询报 Unknown column）。
-- 幂等：列已存在则跳过。

SET @has_pid = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'engineering_bom_item'
     AND column_name = 'process_id'
);
SET @add_pid = IF(
  @has_pid = 0,
  'ALTER TABLE engineering_bom_item ADD COLUMN process_id BIGINT NULL COMMENT ''项目（标准工序）引用 engineering_standard_process.process_id'' AFTER specification',
  'SELECT 1'
);
PREPARE add_process_id FROM @add_pid;
EXECUTE add_process_id;
DEALLOCATE PREPARE add_process_id;

SET @has_pname = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'engineering_bom_item'
     AND column_name = 'process_name'
);
SET @add_pname = IF(
  @has_pname = 0,
  'ALTER TABLE engineering_bom_item ADD COLUMN process_name VARCHAR(200) NULL COMMENT ''项目（标准工序）名称冗余'' AFTER process_id',
  'SELECT 1'
);
PREPARE add_process_name FROM @add_pname;
EXECUTE add_process_name;
DEALLOCATE PREPARE add_process_name;

-- 收尾自检（应返回 2）
SELECT COUNT(*) AS added_cols FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name = 'engineering_bom_item'
   AND column_name IN ('process_id','process_name');
