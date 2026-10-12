-- 公共附件 V1-A 历史回填：仅由人工确认后执行。先执行并核查271；不自动执行。
-- risk: high
-- 在维护窗口暂停业务写入后，由人工备份（排除hr_employee），再使用db-migrate.sh执行。
-- 只INSERT引用，不改sys_attachment原始行，不猜产品/分类，不修改规范/质量硬引用。
-- 来源权限与AttachmentSourcePolicy一致；未知来源、空/零ID、缺失业务、已删附件全部跳过。
START TRANSACTION;
SELECT id FROM sys_attachment ORDER BY id FOR UPDATE;
CREATE TEMPORARY TABLE tmp_attachment_before AS
 SELECT id,biz_type,biz_id,file_path,sha256 FROM sys_attachment;
CREATE TEMPORARY TABLE tmp_attachment_hard_before AS
 SELECT CONCAT('spec:',id) AS ref_key,source_id AS attachment_id FROM product_work_spec_item WHERE resource_type='DRAWING'
 UNION ALL SELECT CONCAT('quality:',id),file_id FROM quality_template_registry
 UNION ALL SELECT CONCAT('film:',film_id),file_id FROM engineering_film;
CREATE TEMPORARY TABLE tmp_attachment_sources AS
 SELECT CAST('product' AS CHAR(50) CHARACTER SET ascii) COLLATE ascii_bin AS source_type,product_id AS source_id,product_code AS source_no FROM product
 UNION ALL SELECT 'inquiry',inquiry_id,inquiry_no FROM sales_inquiry WHERE deleted=0
 UNION ALL SELECT 'quotation',quotation_id,quotation_no FROM sales_quotation WHERE deleted=0
 UNION ALL SELECT 'sales_order',order_id,order_no FROM sales_order o WHERE deleted=0 AND NOT EXISTS(SELECT 1 FROM sales_sample_order s WHERE s.order_id=o.order_id AND s.deleted=0)
 UNION ALL SELECT 'sample',o.order_id,o.order_no FROM sales_sample_order s JOIN sales_order o ON o.order_id=s.order_id WHERE s.deleted=0 AND o.deleted=0
 UNION ALL SELECT 'purchase_order',order_id,order_no FROM purchase_order
 UNION ALL SELECT 'production_order',order_id,order_no FROM production_order
 UNION ALL SELECT 'material',material_id,material_code FROM inventory_material;
CREATE TEMPORARY TABLE tmp_attachment_candidates AS
 SELECT a.id,a.biz_id AS source_id,
 CAST(CASE a.biz_type WHEN 'order' THEN 'sales_order' WHEN 'sample_order' THEN 'sample' WHEN 'purchase' THEN 'purchase_order' WHEN 'production' THEN 'production_order' ELSE a.biz_type END AS CHAR(50) CHARACTER SET ascii) COLLATE ascii_bin AS source_type,
 a.deleted,a.file_path,a.file_name FROM sys_attachment a;
CREATE TEMPORARY TABLE tmp_attachment_backfill_plan AS
 SELECT a.id,a.source_type,a.source_id,s.source_no,
 CASE WHEN a.deleted<>0 THEN 'DELETED_ATTACHMENT'
      WHEN a.file_path IS NULL OR TRIM(a.file_path)='' OR a.file_name IS NULL OR TRIM(a.file_name)='' THEN 'MISSING_FILE_METADATA'
      WHEN a.source_id IS NULL OR a.source_id<=0 THEN 'INVALID_SOURCE_ID'
      WHEN a.source_type IS NULL OR a.source_type NOT IN ('product','inquiry','quotation','sales_order','sample','purchase_order','production_order','material') THEN 'UNKNOWN_SOURCE'
      WHEN s.source_id IS NULL THEN 'SOURCE_MISSING_OR_DELETED'
      WHEN EXISTS(SELECT 1 FROM sys_attachment_ref r WHERE r.attachment_id=a.id AND r.source_type=a.source_type AND r.source_id=a.source_id AND r.product_id IS NULL) THEN 'ALREADY_RECORDED'
      ELSE 'INSERTABLE' END AS reason
 FROM tmp_attachment_candidates a LEFT JOIN tmp_attachment_sources s ON s.source_type=a.source_type AND s.source_id=a.source_id;
SELECT reason,COUNT(*) AS count FROM tmp_attachment_backfill_plan GROUP BY reason ORDER BY reason;
SELECT id,source_type,source_id,reason FROM tmp_attachment_backfill_plan WHERE reason NOT IN ('INSERTABLE','ALREADY_RECORDED') ORDER BY id;
INSERT INTO sys_attachment_ref(attachment_id,source_type,source_id,source_no,product_id,document_type,sort_order,deleted,create_by,create_time)
 SELECT id,source_type,source_id,source_no,NULL,NULL,0,0,'migration-272',NOW()
 FROM tmp_attachment_backfill_plan WHERE reason='INSERTABLE';
SELECT ROW_COUNT() AS inserted_references;
-- 对账：以下所有 *_mismatch 必须为0；归档迁移前人工核验完整输出。
SELECT COUNT(*) AS attachment_identity_mismatch FROM tmp_attachment_before b LEFT JOIN sys_attachment a ON a.id=b.id
 WHERE a.id IS NULL OR NOT(a.file_path<=>b.file_path) OR NOT(a.sha256<=>b.sha256) OR NOT(a.biz_type<=>b.biz_type) OR NOT(a.biz_id<=>b.biz_id);
SELECT COUNT(*) AS reference_mismatch FROM tmp_attachment_backfill_plan p
 WHERE reason IN ('INSERTABLE','ALREADY_RECORDED') AND NOT EXISTS(SELECT 1 FROM sys_attachment_ref r WHERE r.attachment_id=p.id AND r.source_type=p.source_type AND r.source_id=p.source_id AND r.product_id IS NULL);
CREATE TEMPORARY TABLE tmp_attachment_hard_after AS
 SELECT CONCAT('spec:',id) AS ref_key,source_id AS attachment_id FROM product_work_spec_item WHERE resource_type='DRAWING'
 UNION ALL SELECT CONCAT('quality:',id),file_id FROM quality_template_registry
 UNION ALL SELECT CONCAT('film:',film_id),file_id FROM engineering_film;
SELECT COUNT(*) AS hard_reference_mismatch FROM tmp_attachment_hard_before b LEFT JOIN tmp_attachment_hard_after a ON a.ref_key=b.ref_key WHERE a.ref_key IS NULL OR NOT(a.attachment_id<=>b.attachment_id);
SELECT attachment_id,source_type,source_id,product_id,COUNT(*) AS duplicate_active FROM sys_attachment_ref WHERE deleted=0 GROUP BY attachment_id,source_type,source_id,product_id HAVING COUNT(*)>1;
COMMIT;
DROP TEMPORARY TABLE tmp_attachment_hard_after;
DROP TEMPORARY TABLE tmp_attachment_hard_before;
DROP TEMPORARY TABLE tmp_attachment_before;
DROP TEMPORARY TABLE tmp_attachment_sources;
DROP TEMPORARY TABLE tmp_attachment_candidates;
DROP TEMPORARY TABLE tmp_attachment_backfill_plan;
