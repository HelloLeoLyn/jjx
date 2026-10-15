-- dev-20261015-017：独立打样BOM材料的项目及用量参数；不新增表、不修改历史行。
-- 执行前：用户手工完成全库备份（排除hr_employee）；按目标库ops.schema.applied核查未应用。
ALTER TABLE sales_sample_bom
  ADD COLUMN material_id BIGINT NULL COMMENT '物料档案ID',
  ADD COLUMN material_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '物料编码',
  ADD COLUMN project_ids JSON NULL COMMENT '项目标准工序ID列表，可空或组合',
  ADD COLUMN module_qty DECIMAL(12,0) NULL COMMENT '模数，与标准BOM一致，至少1',
  ADD COLUMN base_qty DECIMAL(12,0) NULL COMMENT '基数，与标准BOM一致，至少1';
