-- 工程管理：历史档案智能录入（dev-20260911-004）
-- 本地 PaddleOCR + 工序图标样本映射；导入结果只生成现有业务草稿。

CREATE TABLE IF NOT EXISTS engineering_archive_import (
  archive_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '历史档案ID',
  file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_path VARCHAR(500) NOT NULL COMMENT '原图相对路径',
  file_hash CHAR(64) NOT NULL COMMENT 'SHA-256，防止重复上传',
  recognize_status TINYINT NOT NULL DEFAULT 0 COMMENT '0待识别 1识别中 2待确认 3已生成草稿 4识别失败',
  recognize_message VARCHAR(500) DEFAULT NULL COMMENT '识别提示或失败原因',
  product_name VARCHAR(200) DEFAULT NULL COMMENT '识别产品名称',
  product_code VARCHAR(50) DEFAULT NULL COMMENT '识别产品编号',
  extracted_json JSON DEFAULT NULL COMMENT '可编辑识别结果',
  product_id BIGINT DEFAULT NULL COMMENT '生成的草稿产品ID',
  bom_id BIGINT DEFAULT NULL COMMENT '生成的草稿BOM ID',
  routing_id BIGINT DEFAULT NULL COMMENT '生成的草稿工艺路线ID',
  create_by VARCHAR(50) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(50) NOT NULL DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (archive_id),
  UNIQUE KEY uk_archive_file_hash (file_hash),
  KEY idx_archive_status_time (recognize_status, create_time),
  KEY idx_archive_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工程历史档案识别记录';

CREATE TABLE IF NOT EXISTS engineering_process_icon_sample (
  sample_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '工序图标样本ID',
  process_id BIGINT DEFAULT NULL COMMENT '标准工序ID，未确认时可空',
  archive_id BIGINT DEFAULT NULL COMMENT '来源历史档案ID',
  workflow_type VARCHAR(20) NOT NULL COMMENT 'PANEL/UP_LINE/DOWN_LINE',
  step_no INT DEFAULT NULL COMMENT '来源区域内序号',
  original_path VARCHAR(500) NOT NULL COMMENT '原始裁剪图路径',
  normalized_path VARCHAR(500) NOT NULL COMMENT '标准化识别图路径',
  perceptual_hash CHAR(16) NOT NULL COMMENT '64位感知哈希',
  match_score DECIMAL(6,5) DEFAULT NULL COMMENT '自动匹配相似度',
  confirm_status TINYINT NOT NULL DEFAULT 0 COMMENT '0待确认 1已确认 2已忽略',
  usage_count INT NOT NULL DEFAULT 0 COMMENT '命中次数',
  use_as_system_icon TINYINT NOT NULL DEFAULT 0 COMMENT '是否延用为系统展示图标',
  create_by VARCHAR(50) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(50) NOT NULL DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (sample_id),
  KEY idx_icon_process_confirm (process_id, confirm_status),
  KEY idx_icon_hash (perceptual_hash),
  KEY idx_icon_archive (archive_id),
  CONSTRAINT fk_icon_sample_process FOREIGN KEY (process_id)
    REFERENCES engineering_standard_process(process_id) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_icon_sample_archive FOREIGN KEY (archive_id)
    REFERENCES engineering_archive_import(archive_id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历史档案工序图标识别样本';

INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,create_by,create_time,remark)
SELECT '历史档案录入',90,62,'archive-import','views/engineering/archive-import/index.vue','1','0','C','0','0',
       'engineering:archive:view','document','0,90','EngineeringArchiveImport','1','Codex',NOW(),'dev-20260911-004'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='engineering:archive:view');

SET @archive_menu_id := (SELECT menu_id FROM sys_menu WHERE perms='engineering:archive:view' ORDER BY menu_id LIMIT 1);

INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,create_by,create_time,remark)
SELECT '上传识别',@archive_menu_id,1,'',NULL,'1','0','F','0','0','engineering:archive:import','#',
       CONCAT('0,90,',@archive_menu_id),'1','Codex',NOW(),'dev-20260911-004'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='engineering:archive:import');

INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,create_by,create_time,remark)
SELECT '确认生成草稿',@archive_menu_id,2,'',NULL,'1','0','F','0','0','engineering:archive:generate','#',
       CONCAT('0,90,',@archive_menu_id),'1','Codex',NOW(),'dev-20260911-004'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='engineering:archive:generate');

INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,create_by,create_time,remark)
SELECT '工序图标确认',@archive_menu_id,3,'',NULL,'1','0','F','0','0','engineering:archive:icon-map','#',
       CONCAT('0,90,',@archive_menu_id),'1','Codex',NOW(),'dev-20260911-004'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='engineering:archive:icon-map');

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT parent_auth.role_id,new_menu.menu_id
FROM sys_role_menu parent_auth
JOIN sys_menu new_menu ON new_menu.perms IN
  ('engineering:archive:view','engineering:archive:import','engineering:archive:generate','engineering:archive:icon-map')
WHERE parent_auth.menu_id=90
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu existing
    WHERE existing.role_id=parent_auth.role_id AND existing.menu_id=new_menu.menu_id
  );
