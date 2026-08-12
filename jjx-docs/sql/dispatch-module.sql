-- ============================================================
-- 生产派工模块（production_dispatch）初始化脚本
-- 方案：jjx-docs/specs/dispatch-spec.md v1.0
-- 日期：2026-08-12
-- 说明：工单加责任字段 + 派工单表 + 派工流水表 + 菜单/权限
-- ============================================================

-- 1. 生产工单增加工单级责任字段
ALTER TABLE production_order
  ADD COLUMN dispatch_team_id     BIGINT       NULL COMMENT '负责班组(部门ID)' AFTER priority,
  ADD COLUMN dispatch_team_name   VARCHAR(100) NULL COMMENT '负责班组名称' AFTER dispatch_team_id,
  ADD COLUMN dispatch_leader_id   BIGINT       NULL COMMENT '工单负责人(用户ID)' AFTER dispatch_team_name,
  ADD COLUMN dispatch_leader_name VARCHAR(64)  NULL COMMENT '工单负责人姓名' AFTER dispatch_leader_id;

-- 2. 工序派工单表
CREATE TABLE IF NOT EXISTS production_dispatch (
  dispatch_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '派工单ID',
  order_id           BIGINT       NOT NULL COMMENT '生产订单ID',
  order_no           VARCHAR(50)  NULL COMMENT '工单编号(冗余)',
  execution_id       BIGINT       NOT NULL COMMENT '工序执行记录ID(production_operation_execution)',
  process_name       VARCHAR(200) NULL COMMENT '工序名称(冗余)',
  process_order      INT          NULL COMMENT '工序顺序(冗余)',

  team_id            BIGINT       NULL COMMENT '责任班组(部门ID)',
  team_name          VARCHAR(100) NULL COMMENT '责任班组名称',
  equipment_id       BIGINT       NULL COMMENT '设备ID(空=不限)',
  equipment_name     VARCHAR(200) NULL COMMENT '设备名称',
  operators          VARCHAR(500) NULL COMMENT '执行人(JSON数组 [{userId,userName}])',

  assigned_by        BIGINT       NULL COMMENT '派工主管(用户ID)',
  assigned_by_name   VARCHAR(64)  NULL COMMENT '派工主管姓名',
  assign_time        DATETIME     NULL COMMENT '最近指派时间',
  status             TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0待派工 1已派工 2执行中 3已完成 4已退回（静态枚举）',
  reject_reason      VARCHAR(500) NULL COMMENT '退回原因(退回时必填)',
  re_dispatch_count  INT          NOT NULL DEFAULT 0 COMMENT '改派次数',
  remark             VARCHAR(500) NULL COMMENT '备注',

  del_flag           CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志 0正常 1删除',
  create_by          VARCHAR(64)  NULL COMMENT '创建人',
  create_time        DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          VARCHAR(64)  NULL COMMENT '更新人',
  update_time        DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (dispatch_id),
  UNIQUE KEY uk_execution (execution_id),
  KEY idx_order (order_id),
  KEY idx_team_status (team_id, status),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工序派工单';

-- 3. 派工操作流水表
CREATE TABLE IF NOT EXISTS production_dispatch_log (
  log_id        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  dispatch_id   BIGINT        NOT NULL COMMENT '派工单ID',
  order_id      BIGINT        NULL COMMENT '工单ID(冗余)',
  action        VARCHAR(20)   NOT NULL COMMENT '操作：ASSIGN指派/REASSIGN改派/REJECT退回/START开始/COMPLETE完成',
  content       VARCHAR(1000) NULL COMMENT '变更内容（如：由生产一组改派给生产二组，设备由3#印刷机改为5#印刷机）',
  operator_id   BIGINT        NULL COMMENT '操作人ID',
  operator_name VARCHAR(64)   NULL COMMENT '操作人姓名',
  create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

  PRIMARY KEY (log_id),
  KEY idx_dispatch (dispatch_id),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派工操作流水';

-- 4. 菜单：派工管理（父菜单 生产管理=43，order_num=11）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '派工管理', 43, 11, 'dispatch', 'views/production/dispatch/index.vue', NULL, '1', '0', 'C', '0', '0', 'production:dispatch:list', 'UserFilled', '0,43', 'ProductionDispatch', '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), '工序派工：班组/设备/执行人逐级指派'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:dispatch:list');

-- 5. 按钮权限
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '派工指派', menu_id, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:dispatch:assign', NULL, ancestors, NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM sys_menu WHERE perms = 'production:dispatch:list' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:dispatch:assign');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '派工开始/完成', menu_id, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:dispatch:start', NULL, ancestors, NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM sys_menu WHERE perms = 'production:dispatch:list' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:dispatch:start');

-- 6. 授权超级管理员（role_id=1）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu
WHERE perms IN ('production:dispatch:list','production:dispatch:assign','production:dispatch:start')
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = sys_menu.menu_id);
