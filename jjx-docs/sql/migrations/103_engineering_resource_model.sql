-- dev-20260912-013
-- 全新工程工艺资源模型：不迁移、不兼容 production_tooling / jjx_screen_master 历史数据。
-- 执行记录：2026-09-12 22:13 Codex；备份 md5=2eff779f55680c637301afc5bde68ba2；ops.schema.version=103。

CREATE TABLE engineering_screen_frame (
  frame_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '网框实体ID',
  frame_no VARCHAR(50) NOT NULL COMMENT '网框编号',
  frame_type VARCHAR(30) NULL COMMENT '网框型号',
  mesh VARCHAR(50) NULL COMMENT '目数',
  location VARCHAR(200) NULL COMMENT '存放位置',
  status VARCHAR(20) NOT NULL DEFAULT 'EMPTY' COMMENT 'EMPTY空框/PLATED已制版/MAINTENANCE维护中/SCRAPPED报废',
  remark VARCHAR(500) NULL,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  create_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (frame_id),
  UNIQUE KEY uk_screen_frame_no (frame_no),
  KEY idx_screen_frame_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工程-网框实体';

CREATE TABLE engineering_screen_plate (
  plate_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '制版履历ID',
  frame_id BIGINT NOT NULL COMMENT '网框实体ID',
  plate_no VARCHAR(80) NOT NULL COMMENT '版面编号',
  film_id BIGINT NULL COMMENT '来源菲林ID',
  content VARCHAR(1000) NULL COMMENT '版面内容',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE有效/WASHED已洗版/VOID作废',
  plated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '制版时间',
  washed_time DATETIME NULL COMMENT '洗版时间',
  end_reason VARCHAR(500) NULL COMMENT '结束原因',
  remark VARCHAR(500) NULL,
  create_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (plate_id),
  UNIQUE KEY uk_screen_plate_no (plate_no),
  KEY idx_screen_plate_frame_status (frame_id, status),
  KEY idx_screen_plate_film (film_id),
  CONSTRAINT fk_screen_plate_frame FOREIGN KEY (frame_id) REFERENCES engineering_screen_frame(frame_id),
  CONSTRAINT fk_screen_plate_film FOREIGN KEY (film_id) REFERENCES engineering_film(film_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工程-网版制版履历';

CREATE TABLE engineering_die (
  die_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '刀模实体ID',
  die_no VARCHAR(50) NOT NULL COMMENT '刀模编号',
  die_name VARCHAR(200) NOT NULL COMMENT '刀模名称',
  purpose VARCHAR(200) NULL COMMENT '用途',
  specification VARCHAR(500) NULL COMMENT '规格参数',
  version VARCHAR(30) NULL COMMENT '版本',
  location VARCHAR(200) NULL COMMENT '存放位置',
  status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE可用/MAINTENANCE维护中/STOPPED停用/REPLACED已重做/SCRAPPED报废',
  predecessor_id BIGINT NULL COMMENT '重做来源刀模ID',
  remark VARCHAR(500) NULL,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  create_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (die_id),
  UNIQUE KEY uk_engineering_die_no (die_no),
  KEY idx_engineering_die_status (status),
  KEY idx_engineering_die_predecessor (predecessor_id),
  CONSTRAINT fk_engineering_die_predecessor FOREIGN KEY (predecessor_id) REFERENCES engineering_die(die_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工程-刀模实体';

CREATE TABLE engineering_resource_product_rel (
  rel_id BIGINT NOT NULL AUTO_INCREMENT,
  resource_type VARCHAR(20) NOT NULL COMMENT 'FILM/SCREEN_PLATE/DIE',
  resource_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  purpose VARCHAR(200) NULL COMMENT '在该产品上的用途',
  is_active TINYINT NOT NULL DEFAULT 1,
  create_by VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (rel_id),
  UNIQUE KEY uk_resource_product (resource_type, resource_id, product_id),
  KEY idx_resource_product_product (product_id, resource_type),
  CONSTRAINT fk_resource_product_product FOREIGN KEY (product_id) REFERENCES product(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工程资源-产品多对多关系';

CREATE TABLE engineering_resource_maintenance (
  maintenance_id BIGINT NOT NULL AUTO_INCREMENT,
  resource_type VARCHAR(20) NOT NULL COMMENT 'SCREEN_FRAME/SCREEN_PLATE/DIE',
  resource_id BIGINT NOT NULL,
  action_type VARCHAR(20) NOT NULL COMMENT 'PLATE/WASH/REPAIR/REMAKE/STOP/ENABLE/SCRAP',
  before_status VARCHAR(20) NULL,
  after_status VARCHAR(20) NULL,
  successor_type VARCHAR(20) NULL COMMENT '重做后的资源类型',
  successor_id BIGINT NULL COMMENT '重做后的资源ID',
  description VARCHAR(500) NULL,
  operator VARCHAR(64) NULL,
  operate_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (maintenance_id),
  KEY idx_resource_maintenance_resource (resource_type, resource_id, operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工程资源维护履历';

-- 新结构从零启用；菲林仅把现有主产品关系同步为第一条关联，避免已在用菲林失联。
INSERT INTO engineering_resource_product_rel (resource_type, resource_id, product_id, purpose, create_by)
SELECT 'FILM', film_id, product_id, film_name, 'migration-103'
FROM engineering_film
WHERE deleted = 0
ON DUPLICATE KEY UPDATE is_active = 1;

INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,sort,create_by,create_time,update_by,remark)
SELECT '工艺资源',90,62,'resource','views/engineering/resource/index.vue','C','0','0','engineering:resource:view','Tools','0,90','EngineeringResource','1',62,'Codex',NOW(),'Codex','菲林、网框/制版、刀模及维护履历'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='engineering:resource:view');

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,ancestors,create_by,create_time,update_by)
SELECT x.menu_name,m.menu_id,x.order_num,'','F','0','0',x.perms,m.ancestors,'Codex',NOW(),'Codex'
FROM sys_menu m
JOIN (
  SELECT '工艺资源编辑' menu_name,1 order_num,'engineering:resource:edit' perms
  UNION ALL SELECT '工艺资源维护',2,'engineering:resource:maintain'
  UNION ALL SELECT '工艺资源删除',3,'engineering:resource:delete'
) x
WHERE m.perms='engineering:resource:view'
  AND NOT EXISTS (SELECT 1 FROM sys_menu e WHERE e.perms=x.perms);

INSERT INTO sys_role_menu (role_id,menu_id)
SELECT DISTINCT rm.role_id,m.menu_id
FROM sys_role_menu rm
JOIN sys_menu m ON m.perms IN ('engineering:resource:view','engineering:resource:edit','engineering:resource:maintain','engineering:resource:delete')
WHERE rm.menu_id=90
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id=rm.role_id AND e.menu_id=m.menu_id);

INSERT INTO sys_task (task_code,task_type,kanban_module,title,status,priority,create_by,create_time,description)
SELECT 'dev-20260912-013','DEV','dev','工程工艺资源新模型完整实施',0,'P2','Codex',NOW(),
       '全新结构：菲林/网版制版/刀模多产品关联，网框实体复用，洗版/维修/重做/停用/报废维护履历；不迁移旧两表数据，不做寿命和生产报工。'
WHERE NOT EXISTS (SELECT 1 FROM sys_task WHERE task_code='dev-20260912-013');
