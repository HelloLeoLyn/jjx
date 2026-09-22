-- ============================================================================
-- ⚠ 撞号改号（2026-09-22，Hermes）：原为 129_，与同号文件 129_rename_and_remove_legacy_menus.sql 撞号；按「后建者改号」改为 184_，内容一字未动。
-- 129: 复合子工序样本并入统一图标样本表并去重 —— dev-20260917-009
-- 子工序本身也是标准工序图标，不再维护第二张映射表。
-- ============================================================================
USE `jjx_erp_db`;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='engineering_process_icon_sample' AND column_name='parent_sample_id')=0,
  'ALTER TABLE engineering_process_icon_sample ADD COLUMN parent_sample_id BIGINT NULL COMMENT ''复合父样本ID，普通样本为空''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='engineering_process_icon_sample' AND column_name='component_order')=0,
  'ALTER TABLE engineering_process_icon_sample ADD COLUMN component_order INT NULL COMMENT ''复合子项顺序''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='engineering_process_icon_sample' AND column_name='work_instruction')=0,
  'ALTER TABLE engineering_process_icon_sample ADD COLUMN work_instruction VARCHAR(500) NULL COMMENT ''子工序作业说明/下标''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

INSERT INTO engineering_process_icon_sample
  (process_id,archive_id,workflow_type,step_no,original_path,normalized_path,perceptual_hash,match_score,confirm_status,usage_count,use_as_system_icon,create_by,update_by,parent_sample_id,component_order,work_instruction)
SELECT c.process_id,p.archive_id,p.workflow_type,p.step_no,
       CONCAT(p.original_path,'#component-',c.component_order),
       CONCAT(p.normalized_path,'#component-',c.component_order),p.perceptual_hash,1,1,0,1,c.create_by,c.update_by,
       c.sample_id,c.component_order,c.work_instruction
FROM engineering_process_icon_component c
JOIN engineering_process_icon_sample p ON p.sample_id=c.sample_id
WHERE NOT EXISTS (
  SELECT 1 FROM engineering_process_icon_sample x
  WHERE x.parent_sample_id=c.sample_id AND x.component_order=c.component_order
);

CREATE INDEX idx_icon_parent_component ON engineering_process_icon_sample(parent_sample_id,component_order);
CREATE INDEX idx_icon_workflow_hash_process ON engineering_process_icon_sample(workflow_type,perceptual_hash,process_id,confirm_status);

DROP TABLE IF EXISTS engineering_process_icon_component;

-- 同流程段、同哈希、同标准工序的未确认重复样本只保留最新一条；已确认样本不删除。
DELETE s FROM engineering_process_icon_sample s
JOIN engineering_process_icon_sample keep ON keep.workflow_type=s.workflow_type
 AND keep.perceptual_hash=s.perceptual_hash
 AND ((keep.process_id=s.process_id) OR (keep.process_id IS NULL AND s.process_id IS NULL))
 AND keep.confirm_status=s.confirm_status
 AND keep.sample_id>s.sample_id
WHERE s.confirm_status=0 AND s.parent_sample_id IS NULL;

SELECT '129 复合子工序样本合并与去重完成' AS check_point;
