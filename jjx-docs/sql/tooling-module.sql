-- ============================================================
-- 工装模具档案模块（production_tooling）初始化脚本
-- 方案：jjx-docs/specs/tooling-mold-spec.md v1.1
-- 日期：2026-08-12
-- 说明：建表 + 编号规则配置 + 菜单/权限（生产管理下）
-- ============================================================

-- 1. 建表：工装模具档案
CREATE TABLE IF NOT EXISTS production_tooling (
  tooling_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  tooling_no       VARCHAR(50)  NOT NULL COMMENT '工装编号（网框编号/刀模编号，唯一）',
  tooling_name     VARCHAR(200) NOT NULL COMMENT '名称（如：3#丝印网框、主面板模切刀模）',
  tooling_type     VARCHAR(20)  NOT NULL COMMENT '类型：SCREEN=网框 DIE=刀模（静态枚举）',
  spec             VARCHAR(512) NULL COMMENT '参数（如：材质：xxx\n尺寸：xxx，长度512）',

  -- 网框专属字段（tooling_type=SCREEN 时使用）
  mesh_count       INT          NULL COMMENT '目数（如 200、300）',
  frame_size       VARCHAR(100) NULL COMMENT '框尺寸（如 400×500mm）',
  mesh_material    VARCHAR(50)  NULL COMMENT '网布材质（聚酯/尼龙/不锈钢）',
  tension          DECIMAL(6,2) NULL COMMENT '张紧力(N/cm)',

  -- 刀模专属字段（tooling_type=DIE 时使用）
  blade_height     DECIMAL(8,2) NULL COMMENT '刀高(mm)',
  die_material     VARCHAR(50)  NULL COMMENT '刀模材质（钢刀/蚀刻/激光）',
  die_size         VARCHAR(100) NULL COMMENT '模切尺寸（如 120×80mm）',
  life_limit       INT          NULL COMMENT '设计冲切寿命上限(次)',
  current_count    INT          NULL DEFAULT 0 COMMENT '已冲切次数（手工维护+报工累加）',

  -- 公共管理字段
  status           TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=在库 1=使用中 2=清洗/保养中 3=维修中 4=报废（静态枚举）',
  location         VARCHAR(200) NULL COMMENT '存放位置（货架/柜号）',
  department       VARCHAR(100) NULL COMMENT '使用部门',
  responsible      VARCHAR(64)  NULL COMMENT '责任人',
  customer         VARCHAR(100) NULL COMMENT '客户（定制工装所属客户）',
  enable_date      DATE         NULL COMMENT '启用日期',
  last_use_time    DATETIME     NULL COMMENT '最后使用时间',
  use_count        INT          NULL DEFAULT 0 COMMENT '累计使用次数',
  remark           VARCHAR(500) NULL COMMENT '备注',

  -- 审计字段（与全项目一致）
  del_flag         CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志 0正常 1删除',
  create_by        VARCHAR(64)  NULL COMMENT '创建人',
  create_time      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by        VARCHAR(64)  NULL COMMENT '更新人',
  update_time      DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (tooling_id),
  UNIQUE KEY uk_tooling_no (tooling_no),
  KEY idx_type_status (tooling_type, status),
  KEY idx_name (tooling_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工装模具档案';

-- 2. 编号规则配置（sys_config）
-- 占位符：{prefix}=类型前缀(SCREEN→WK, DIE→DM)，{date}=日期yyMMdd，{seq:N}=N位流水号
INSERT INTO sys_config (config_key, config_value, config_name, config_group, remark, sort_order, is_active, create_time)
VALUES ('tooling_no_rule', '{prefix}{date}{seq:3}', '工装模具编号规则', 'production',
        '占位符：{prefix}类型前缀(网框WK/刀模DM)、{date}日期yyMMdd、{seq:N}N位流水号', 0, 1, NOW())
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), remark = VALUES(remark);

-- 3. 菜单：工装模具档案（父菜单 生产管理=43，order_num=10 排在设备管理后）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '工装模具档案', 43, 10, 'tooling', 'views/production/tooling/index.vue', NULL, '1', '0', 'C', '0', '0', 'production:tooling:view', 'Tools', '0,43', 'ProductionTooling', '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), '网框/刀模工装档案'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:tooling:view');

-- 4. 按钮权限（挂在工装模具档案菜单下）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '工装模具查询', menu_id, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:tooling:query', NULL, ancestors, NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM sys_menu WHERE perms = 'production:tooling:view' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:tooling:query');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '工装模具新增', menu_id, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:tooling:add', NULL, ancestors, NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM sys_menu WHERE perms = 'production:tooling:view' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:tooling:add');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '工装模具修改', menu_id, 3, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:tooling:edit', NULL, ancestors, NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM sys_menu WHERE perms = 'production:tooling:view' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:tooling:edit');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '工装模具状态变更', menu_id, 4, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:tooling:changeStatus', NULL, ancestors, NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM sys_menu WHERE perms = 'production:tooling:view' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:tooling:changeStatus');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '工装模具删除', menu_id, 5, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:tooling:remove', NULL, ancestors, NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM sys_menu WHERE perms = 'production:tooling:view' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:tooling:remove');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '工装模具导入', menu_id, 6, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:tooling:import', NULL, ancestors, NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM sys_menu WHERE perms = 'production:tooling:view' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:tooling:import');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '工装模具导出', menu_id, 7, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:tooling:export', NULL, ancestors, NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM sys_menu WHERE perms = 'production:tooling:view' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:tooling:export');

-- 5. 授权超级管理员（role_id=1）全部工装模具权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu
WHERE perms IN ('production:tooling:view','production:tooling:query','production:tooling:add','production:tooling:edit','production:tooling:changeStatus','production:tooling:remove','production:tooling:import','production:tooling:export')
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = sys_menu.menu_id);
