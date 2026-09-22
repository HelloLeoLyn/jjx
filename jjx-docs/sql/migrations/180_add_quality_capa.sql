-- dev-20260921-043：CAPA 最小闭环（不良→根因→措施→验证→关闭）。
CREATE TABLE IF NOT EXISTS quality_capa (
  capa_id BIGINT NOT NULL AUTO_INCREMENT,
  capa_no VARCHAR(40) NOT NULL,
  ncr_id BIGINT NOT NULL,
  root_cause_category VARCHAR(100) NULL,
  root_cause TEXT NULL,
  action_plan TEXT NULL,
  owner_id BIGINT NULL,
  owner_name VARCHAR(64) NULL,
  due_date DATE NULL,
  verification_result TEXT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING_ANALYSIS' COMMENT 'PENDING_ANALYSIS/ACTION_IN_PROGRESS/PENDING_VERIFICATION/CLOSED',
  closed_time DATETIME NULL,
  create_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (capa_id), UNIQUE KEY uk_quality_capa_no (capa_no),
  KEY idx_quality_capa_ncr (ncr_id), KEY idx_quality_capa_status_due (status, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='纠正预防措施台账';

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,
  perms,icon,ancestors,create_by,create_time,update_by,update_time,remark)
SELECT 'CAPA台账',338,9,'/quality/capa','views/quality/capa/index.vue',0,0,'C',0,0,
  'quality:capa:view','DocumentChecked','0,338','Codex',NOW(),'Codex',NOW(),'纠正预防措施闭环'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/quality/capa');

INSERT INTO sys_role_menu (role_id,menu_id)
SELECT rm.role_id,capa.menu_id FROM sys_role_menu rm
JOIN sys_menu ncr ON ncr.menu_id=rm.menu_id AND ncr.path='/quality/ncr'
JOIN sys_menu capa ON capa.path='/quality/capa'
LEFT JOIN sys_role_menu x ON x.role_id=rm.role_id AND x.menu_id=capa.menu_id
WHERE x.role_id IS NULL;
