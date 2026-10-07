-- dev-20261007-004
-- risk: low
-- BOM 明细加「项目」列（来源：界面手建卡 task_id=2622「bom修改/新增优化」第③条）。
--   取值复用现成字典 process_category（PANEL 面板 / UP_LINE 上线 / DOWN_LINE 下线 / OTHER 其他，
--   与打样「项目结构」同口径，零新建主数据）。
-- 影响：仅新增一列（可空，存量 3 行自动为 NULL）；不更新、不删除任何业务行。
-- 依赖：后端 EngineeringBomItem 实体/DTO + 前端 BomItemEditor 列必须与本列同批上线
--   （MyBatis-Plus selectList 会带出该列，列不存在则 BOM 明细查询报 Unknown column）。
-- 幂等：列已存在则跳过。

SET @has_col = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'engineering_bom_item'
     AND column_name = 'project_category'
);
SET @add_col = IF(
  @has_col = 0,
  'ALTER TABLE engineering_bom_item ADD COLUMN project_category VARCHAR(20) NULL COMMENT ''项目结构（process_category 字典：PANEL/UP_LINE/DOWN_LINE/OTHER）'' AFTER specification',
  'SELECT 1'
);
PREPARE add_project_category FROM @add_col;
EXECUTE add_project_category;
DEALLOCATE PREPARE add_project_category;

-- 收尾自检（应返回 1）
SELECT COUNT(*) AS has_project_category FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name = 'engineering_bom_item'
   AND column_name = 'project_category';
