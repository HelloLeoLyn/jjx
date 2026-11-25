-- 印刷资源重构 P02：新模型结构与约束（任务码 dev-20261125-008）
-- 依据：jjx-docs/history/printing-resource-v2-contract-dev-20261125-007.md（Owner 2026-11-26 批准 5 张表）
-- 执行：仅限指定隔离库，经 db-migrate.sh/手工备份验证；不作用于正式库。
-- 字符集：utf8mb4 / utf8mb4_unicode_ci；FK 删除策略 RESTRICT。

CREATE TABLE IF NOT EXISTS print_artwork (
  artwork_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '图案主档ID',
  artwork_no VARCHAR(64) NOT NULL COMMENT '图案编号（全域唯一）',
  name VARCHAR(200) NOT NULL COMMENT '图案名称',
  purpose VARCHAR(200) NULL COMMENT '用途',
  family VARCHAR(24) NOT NULL COMMENT '面板/上线/下线',
  set_code VARCHAR(64) NULL COMMENT '编组标签（仅分组，无整套版本）',
  sample_order_id BIGINT NULL COMMENT '来源打样单ID',
  default_version_id BIGINT NULL COMMENT '默认版本ID（仅工程建议）',
  lock_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  create_by VARCHAR(64) NULL COMMENT '创建人账号',
  create_time DATETIME(3) NULL COMMENT '创建时间',
  update_by VARCHAR(64) NULL COMMENT '更新人账号',
  update_time DATETIME(3) NULL COMMENT '更新时间',
  PRIMARY KEY (artwork_id),
  UNIQUE KEY uk_print_artwork_no (artwork_no),
  KEY idx_print_artwork_set_family (set_code, family),
  KEY idx_print_artwork_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='印刷图案主档';

CREATE TABLE IF NOT EXISTS print_artwork_version (
  version_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '图案版本ID',
  artwork_id BIGINT NOT NULL COMMENT '图案ID',
  version_no VARCHAR(32) NOT NULL COMMENT '版本号（图案内唯一）',
  status VARCHAR(24) NOT NULL COMMENT 'DRAFT/REVIEW/APPROVED/RELEASED/DISABLED',
  drawing_file_id BIGINT NULL COMMENT '图纸附件ID（sys_attachment_ref 钉住）',
  technical_spec TEXT NULL COMMENT '技术要求',
  designer_id VARCHAR(64) NULL COMMENT '设计人账号',
  approved_by VARCHAR(64) NULL COMMENT '批准人',
  approved_time DATETIME(3) NULL COMMENT '批准时间',
  released_by VARCHAR(64) NULL COMMENT '发布人',
  released_time DATETIME(3) NULL COMMENT '发布时间',
  lock_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  create_by VARCHAR(64) NULL COMMENT '创建人账号',
  create_time DATETIME(3) NULL COMMENT '创建时间',
  update_by VARCHAR(64) NULL COMMENT '更新人账号',
  update_time DATETIME(3) NULL COMMENT '更新时间',
  PRIMARY KEY (version_id),
  UNIQUE KEY uk_print_artwork_version_no (artwork_id, version_no),
  UNIQUE KEY uk_print_artwork_version_id (version_id, artwork_id),
  KEY idx_print_artwork_version_status (artwork_id, status),
  CONSTRAINT fk_print_artwork_version_artwork FOREIGN KEY (artwork_id) REFERENCES print_artwork (artwork_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='印刷图案版本';

ALTER TABLE print_artwork
  ADD CONSTRAINT fk_print_artwork_default_version FOREIGN KEY (default_version_id, artwork_id)
  REFERENCES print_artwork_version (version_id, artwork_id);

CREATE TABLE IF NOT EXISTS print_film_copy (
  film_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '实体底片ID',
  film_no VARCHAR(64) NOT NULL COMMENT '底片编号（实体唯一）',
  version_id BIGINT NOT NULL COMMENT '图案版本ID',
  location VARCHAR(200) NULL COMMENT '存放位置',
  status VARCHAR(24) NOT NULL COMMENT 'AVAILABLE/ARCHIVED/DESTROYED',
  material VARCHAR(200) NULL COMMENT '片基材料',
  thickness_mm DECIMAL(10,3) NOT NULL COMMENT '厚度(mm)',
  size VARCHAR(100) NULL COMMENT '尺寸',
  output_time DATETIME(3) NULL COMMENT '输出时间',
  destroy_time DATETIME(3) NULL COMMENT '销毁时间',
  lock_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  create_by VARCHAR(64) NULL COMMENT '创建人账号',
  create_time DATETIME(3) NULL COMMENT '创建时间',
  update_by VARCHAR(64) NULL COMMENT '更新人账号',
  update_time DATETIME(3) NULL COMMENT '更新时间',
  PRIMARY KEY (film_id),
  UNIQUE KEY uk_print_film_copy_no (film_no),
  UNIQUE KEY uk_print_film_copy_version (version_id, film_id),
  KEY idx_print_film_copy_status (version_id, status),
  CONSTRAINT fk_print_film_copy_version FOREIGN KEY (version_id) REFERENCES print_artwork_version (version_id),
  CONSTRAINT chk_print_film_copy_thickness CHECK (thickness_mm > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='印刷实体底片';

CREATE TABLE IF NOT EXISTS print_frame_stretch (
  stretch_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '绷网履历ID',
  frame_id BIGINT NOT NULL COMMENT '网框ID（engineering_screen_frame）',
  mesh INT NOT NULL COMMENT '目数',
  tension_n_cm DECIMAL(8,2) NOT NULL COMMENT '张力(N/cm)',
  material VARCHAR(200) NULL COMMENT '网纱材料',
  stretch_time DATETIME(3) NOT NULL COMMENT '绷网时间',
  qualified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否合格',
  end_time DATETIME(3) NULL COMMENT '结束时间（NULL=当前开放段）',
  open_frame_id BIGINT GENERATED ALWAYS AS (CASE WHEN end_time IS NULL THEN frame_id ELSE NULL END) STORED COMMENT '开放段唯一键',
  lock_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  create_by VARCHAR(64) NULL COMMENT '创建人账号',
  create_time DATETIME(3) NULL COMMENT '创建时间',
  update_by VARCHAR(64) NULL COMMENT '更新人账号',
  update_time DATETIME(3) NULL COMMENT '更新时间',
  PRIMARY KEY (stretch_id),
  UNIQUE KEY uk_print_frame_stretch_id (frame_id, stretch_id),
  UNIQUE KEY uk_print_frame_stretch_open (open_frame_id),
  KEY idx_print_frame_stretch_frame (frame_id, stretch_time),
  CONSTRAINT fk_print_frame_stretch_frame FOREIGN KEY (frame_id) REFERENCES engineering_screen_frame (frame_id),
  CONSTRAINT chk_print_frame_stretch_mesh CHECK (mesh > 0),
  CONSTRAINT chk_print_frame_stretch_tension CHECK (tension_n_cm > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网框绷网履历';

CREATE TABLE IF NOT EXISTS print_use_segment (
  segment_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用版分段ID',
  execution_id BIGINT NOT NULL COMMENT '生产执行ID',
  spec_version_id BIGINT NOT NULL COMMENT '规范版本ID',
  routing_item_id BIGINT NOT NULL COMMENT '路线行ID',
  version_id BIGINT NOT NULL COMMENT '图案版本ID',
  plate_id BIGINT NOT NULL COMMENT '网版ID（engineering_screen_plate）',
  start_time DATETIME(3) NOT NULL COMMENT '开始时间',
  end_time DATETIME(3) NULL COMMENT '结束时间（NULL=开放段）',
  operator_id VARCHAR(64) NULL COMMENT '操作人账号',
  operator_name VARCHAR(100) NULL COMMENT '操作人显示名',
  reason VARCHAR(200) NULL COMMENT '换版原因',
  request_id VARCHAR(64) NOT NULL COMMENT '幂等请求号',
  snapshot JSON NULL COMMENT '当时显示快照',
  open_execution_id BIGINT GENERATED ALWAYS AS (CASE WHEN end_time IS NULL THEN execution_id ELSE NULL END) STORED COMMENT '执行开放段唯一键',
  open_plate_id BIGINT GENERATED ALWAYS AS (CASE WHEN end_time IS NULL THEN plate_id ELSE NULL END) STORED COMMENT '网版开放段唯一键',
  lock_version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  create_by VARCHAR(64) NULL COMMENT '创建人账号',
  create_time DATETIME(3) NULL COMMENT '创建时间',
  update_by VARCHAR(64) NULL COMMENT '更新人账号',
  update_time DATETIME(3) NULL COMMENT '更新时间',
  PRIMARY KEY (segment_id),
  UNIQUE KEY uk_print_use_segment_request (request_id),
  UNIQUE KEY uk_print_use_segment_exec_open (open_execution_id),
  UNIQUE KEY uk_print_use_segment_plate_open (open_plate_id),
  KEY idx_print_use_segment_exec_time (execution_id, start_time),
  KEY idx_print_use_segment_version_end (version_id, end_time),
  CONSTRAINT fk_print_use_segment_version FOREIGN KEY (version_id) REFERENCES print_artwork_version (version_id),
  CONSTRAINT fk_print_use_segment_plate FOREIGN KEY (plate_id) REFERENCES engineering_screen_plate (plate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产实际用版分段';
