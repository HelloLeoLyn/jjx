-- 公共附件 V1-A：仅供人工审查执行；本任务未执行生产迁移。
-- risk: high
-- 前置：人工全库备份（排除hr_employee），先核查269/270依赖及目标库结构。
-- 不改历史ID/path/hash/biz_type/biz_id，不物理去重；旧文件默认LEGACY，禁止自动清理。
-- MySQL 8；DDL非事务，失败后人工核查再使用db-migrate.sh --retry。
-- Fail closed on a pre-existing incompatible schema; never silently adopt a similarly named table.
DROP PROCEDURE IF EXISTS jjx_attachment_v1_preflight;
DELIMITER $$
CREATE PROCEDURE jjx_attachment_v1_preflight()
BEGIN
 IF EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_attachment' AND
   ((COLUMN_NAME='upload_state' AND (COLUMN_TYPE<>'varchar(16)' OR IS_NULLABLE<>'NO' OR COALESCE(COLUMN_DEFAULT,'')<>'LEGACY'))
    OR (COLUMN_NAME='expires_at' AND (DATA_TYPE<>'datetime' OR IS_NULLABLE<>'YES'))
    OR (COLUMN_NAME='upload_key' AND (COLUMN_TYPE<>'char(64)' OR IS_NULLABLE<>'YES'))
    OR (COLUMN_NAME='upload_owner' AND (COLUMN_TYPE<>'varchar(64)' OR IS_NULLABLE<>'YES')))) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V1-A attachment lifecycle column conflict; manual review required';
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_attachment' AND INDEX_NAME='uk_attachment_upload_key')
    AND COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_attachment' AND INDEX_NAME='uk_attachment_upload_key' AND NON_UNIQUE=0),'') <> 'upload_key' THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V1-A upload retry key index conflict; manual review required';
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_attachment_ref') THEN
   IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_attachment_ref' AND
       ((COLUMN_NAME IN ('id','attachment_id','source_id') AND COLUMN_TYPE='bigint' AND IS_NULLABLE='NO')
       OR (COLUMN_NAME='product_id' AND COLUMN_TYPE='bigint' AND IS_NULLABLE='YES')
       OR (COLUMN_NAME='source_type' AND COLUMN_TYPE='varchar(50)' AND COLLATION_NAME='ascii_bin')
       OR (COLUMN_NAME IN ('source_no','document_type') AND COLUMN_TYPE='varchar(100)')
       OR (COLUMN_NAME='display_name' AND COLUMN_TYPE='varchar(255)')
       OR (COLUMN_NAME='remark' AND COLUMN_TYPE='varchar(500)')
       OR (COLUMN_NAME='sort_order' AND COLUMN_TYPE='int')
       OR (COLUMN_NAME='deleted' AND COLUMN_TYPE='tinyint(1)')
       OR (COLUMN_NAME IN ('create_by','update_by','deleted_by') AND COLUMN_TYPE='varchar(64)')
       OR (COLUMN_NAME IN ('create_time','update_time','deleted_at') AND DATA_TYPE='datetime')
       OR (COLUMN_NAME='active_product_id' AND COLUMN_TYPE='bigint' AND EXTRA LIKE '%STORED GENERATED%' AND LOWER(GENERATION_EXPRESSION) LIKE '%ifnull%product_id%0%')
       OR (COLUMN_NAME='active_marker' AND DATA_TYPE='tinyint' AND EXTRA LIKE '%STORED GENERATED%' AND LOWER(GENERATION_EXPRESSION) LIKE '%if%deleted%0%1%null%'))) <> 19 THEN
     SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V1-A reference column conflict; manual review required';
   END IF;
   IF COALESCE((SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_attachment_ref' AND INDEX_NAME='uk_attachment_ref_active' AND NON_UNIQUE=0),'') <> 'attachment_id,source_type,source_id,active_product_id,active_marker' THEN
     SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V1-A reference uniqueness conflict; manual review required';
   END IF;
   IF (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_attachment_ref' AND
      ((COLUMN_NAME='attachment_id' AND REFERENCED_TABLE_NAME='sys_attachment' AND REFERENCED_COLUMN_NAME='id') OR (COLUMN_NAME='product_id' AND REFERENCED_TABLE_NAME='product' AND REFERENCED_COLUMN_NAME='product_id'))) <> 2 THEN
     SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V1-A reference FK conflict; manual review required';
   END IF;
 END IF;
END$$
DELIMITER ;
CALL jjx_attachment_v1_preflight();
DROP PROCEDURE jjx_attachment_v1_preflight;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_attachment' AND COLUMN_NAME='upload_state');
SET @sql := IF(@exists=0, 'ALTER TABLE sys_attachment ADD COLUMN upload_state varchar(16) NOT NULL DEFAULT ''LEGACY'' COMMENT ''LEGACY/TEMP/BOUND/ORPHAN''', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_attachment' AND COLUMN_NAME='expires_at');
SET @sql := IF(@exists=0, 'ALTER TABLE sys_attachment ADD COLUMN expires_at datetime NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_attachment' AND COLUMN_NAME='upload_key');
SET @sql := IF(@exists=0, 'ALTER TABLE sys_attachment ADD COLUMN upload_key char(64) CHARACTER SET ascii COLLATE ascii_bin NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_attachment' AND COLUMN_NAME='upload_owner');
SET @sql := IF(@exists=0, 'ALTER TABLE sys_attachment ADD COLUMN upload_owner varchar(64) NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_attachment' AND INDEX_NAME='uk_attachment_upload_key');
SET @sql := IF(@exists=0, 'ALTER TABLE sys_attachment ADD UNIQUE KEY uk_attachment_upload_key (upload_key)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE TABLE IF NOT EXISTS sys_attachment_ref (
 id bigint NOT NULL AUTO_INCREMENT,
 attachment_id bigint NOT NULL,
 source_type varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_id bigint NOT NULL,
 source_no varchar(100) NULL,
 product_id bigint NULL,
 document_type varchar(100) NULL,
 display_name varchar(255) NULL,
 remark varchar(500) NULL,
 sort_order int NOT NULL DEFAULT 0,
 deleted tinyint(1) NOT NULL DEFAULT 0,
 create_by varchar(64) NULL,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_by varchar(64) NULL,
 update_time datetime NULL,
 deleted_by varchar(64) NULL,
 deleted_at datetime NULL,
 active_product_id bigint GENERATED ALWAYS AS (IFNULL(product_id,0)) STORED,
 active_marker tinyint GENERATED ALWAYS AS (IF(deleted=0,1,NULL)) STORED,
 PRIMARY KEY (id),
 UNIQUE KEY uk_attachment_ref_active (attachment_id,source_type,source_id,active_product_id,active_marker),
 KEY idx_attachment_ref_source (source_type,source_id,deleted,sort_order),
 KEY idx_attachment_ref_product (product_id,deleted),
 CONSTRAINT fk_attachment_ref_file FOREIGN KEY (attachment_id) REFERENCES sys_attachment(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_attachment_ref_product FOREIGN KEY (product_id) REFERENCES product(product_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT chk_attachment_ref_ids CHECK (source_id>0 AND (product_id IS NULL OR product_id>0)),
 CONSTRAINT chk_attachment_ref_deleted CHECK (deleted IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共附件业务引用，解绑保留审计';
-- V1-A最后防线：旧SQL/旧清理程序也不得绕过服务直接物理删记录。
-- 注意：CREATE TRIGGER 不在 PREPARE 支持列表内（ERROR 1295），必须直接执行；
--       trigger body 只有一条 SIGNAL，不含分号，无需 DELIMITER 包裹。
DROP TRIGGER IF EXISTS trg_attachment_v1_no_delete;
CREATE TRIGGER trg_attachment_v1_no_delete BEFORE DELETE ON sys_attachment
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V1-A: physical attachment deletion disabled; preview only';

-- 使用现有字典管理权限维护；二级ext_data约定：{"parent":"general","sources":["product",...]}。
INSERT INTO sys_dict(dict_code,dict_name,dict_group,remark,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_category','公共附件一级分类','system','公共附件分类；停用保留历史可读',90,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict WHERE dict_code='attachment_category');
INSERT INTO sys_dict(dict_code,dict_name,dict_group,remark,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','公共附件二级类型','system','ext_data.parent指向一级item_key；sources为允许来源数组',91,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict WHERE dict_code='attachment_document_type');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_category','general','general','通用资料',0,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_category' AND item_key='general');
INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,ext_data,sort_order,is_active,deleted,tenant_id)
SELECT 'attachment_document_type','general_document','general_document','一般资料',
JSON_OBJECT('parent','general','sources',JSON_ARRAY('product','inquiry','quotation','sales_order','sample','purchase_order','production_order','material')),0,1,0,1
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key='general_document');
-- 执行后人工核对SHOW CREATE TABLE及SHOW CREATE TRIGGER；不把同名但不兼容结构视为成功。
SHOW CREATE TABLE sys_attachment_ref;
SHOW CREATE TRIGGER trg_attachment_v1_no_delete;
