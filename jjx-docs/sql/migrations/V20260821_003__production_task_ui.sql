-- =====================================================
-- V20260821_003：Task Tree 派工管理权限体系（清理旧 Dispatch/Assignment 权限）
-- 定位：新版「派工管理」页面，业务模型 = Execution + TaskNode → WorkReport（无旧 Dispatch 语义）
-- 最终权限树（唯一派工管理菜单 = menu_id 261）：
--   派工管理 261  production:task:view
--   ├─ 初始分配   production:task:dispatch
--   ├─ 分配任务   production:task:assign
--   ├─ 收回任务   production:task:recall
--   ├─ 退回任务   production:task:return
--   └─ 任务管理   production:task:admin
-- 关键决策：
--   1. 复用 menu_id=261，perms=production:task:view（消除旧 production:dispatch:list 语义）
--   2. 删除所有旧 Dispatch/Assignment 权限（list/assign/delegate/reassign/return、assignment:add）
--   3. 删除早期按动态 id 创建的重复 task 菜单（如有），统一收敛到 261
--   4. 报工权限挂工序执行(48)：production:work-report:add / cancel（幂等）
--   5. 角色授权按业务语义（RBAC 按钮权限 ≠ 可操作任意 TaskNode；Service 仍校验节点本人/父持有人）：
--      1  admin               全部（含 task:admin）
--      28 PRODUCTION 全权限   view + dispatch + assign + recall + return（生产管理员 prod_manager）
--      30 派工主管            view + assign + recall + return（车间主任）
--      31 班组长              view + assign + recall + return（班组长）
--      32 操作工              view（报工由 work-report 权限体系负责）
--      29 PRODUCTION 业务操作 不授予 task 权限（车间主任/班组长经 30/31 获得）
--   6. task:admin 不下放任何普通生产角色
-- 幂等：重复执行结果一致（临时表收集待删菜单 → 删关系/菜单 → 重建/授权）
-- =====================================================

-- ---------- 0) 删除旧 Dispatch/Assignment 权限（菜单 + 角色关系） ----------
DROP TEMPORARY TABLE IF EXISTS tmp_task_del_menu;
CREATE TEMPORARY TABLE tmp_task_del_menu (menu_id BIGINT PRIMARY KEY);

-- 旧 Dispatch/Assignment 权限（production:dispatch:list/assign/delegate/reassign/return、production:assignment:add 等）
INSERT IGNORE INTO tmp_task_del_menu
SELECT menu_id FROM sys_menu WHERE perms LIKE 'production:dispatch:%' OR perms LIKE 'production:assignment:%';

-- 早期动态 id 创建的 task 权限菜单（非 261 的 task:view 菜单）及其子按钮，收敛到 261
INSERT IGNORE INTO tmp_task_del_menu
SELECT menu_id FROM sys_menu WHERE perms = 'production:task:view' AND menu_id <> 261;
INSERT IGNORE INTO tmp_task_del_menu
SELECT menu_id FROM sys_menu WHERE parent_id IN (
    SELECT menu_id FROM sys_menu WHERE perms = 'production:task:view' AND menu_id <> 261
);

-- 先删角色关系，再删菜单（避免外键/残留）
DELETE rm FROM sys_role_menu rm JOIN tmp_task_del_menu t ON t.menu_id = rm.menu_id;
DELETE m FROM sys_menu m JOIN tmp_task_del_menu t ON t.menu_id = m.menu_id;
DROP TEMPORARY TABLE tmp_task_del_menu;

-- ---------- 1) 派工管理菜单：复用 261 ----------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT 261, '派工管理', 43, 11, '/production/dispatch', 'production/dispatch/index', NULL, '1', '0', 'C', '0', '0', 'production:task:view', 's-operation', '0,43', 'ProductionDispatch', '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), '任务模型：Execution → TaskNode → WorkReport（Task Tree）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 261);

UPDATE sys_menu
SET menu_name = '派工管理', parent_id = 43, order_num = 11, path = '/production/dispatch',
    component = 'production/dispatch/index', menu_type = 'C', visible = '0', status = '0',
    perms = 'production:task:view', icon = 's-operation', ancestors = '0,43',
    route_name = 'ProductionDispatch', update_time = NOW(),
    remark = '任务模型：Execution → TaskNode → WorkReport（Task Tree）'
WHERE menu_id = 261;

-- ---------- 2) 5 个 Task Tree 按钮（parent_id=261，幂等） ----------
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '初始分配', 261, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:task:dispatch', NULL, '0,43,261', NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:task:dispatch');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '分配任务', 261, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:task:assign', NULL, '0,43,261', NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:task:assign');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '收回任务', 261, 3, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:task:recall', NULL, '0,43,261', NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:task:recall');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '退回任务', 261, 4, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:task:return', NULL, '0,43,261', NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:task:return');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '任务管理', 261, 5, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:task:admin', NULL, '0,43,261', NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:task:admin');

-- ---------- 3) 报工按钮（工序执行 48 下，幂等） ----------
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '提交报工', 48, 1, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:work-report:add', NULL, '0,43,48', NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:work-report:add');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort, create_by, create_time, update_by, update_time, remark)
SELECT '撤销报工', 48, 2, '', NULL, NULL, '1', '0', 'F', '0', '0', 'production:work-report:cancel', NULL, '0,43,48', NULL, '1', NULL, 0, 'admin', NOW(), 'admin', NOW(), NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'production:work-report:cancel');

-- ---------- 4) 角色授权矩阵（先清 261 子树旧关系，再按矩阵重建；幂等） ----------
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.menu_id = 261 OR m.parent_id = 261;

-- admin(1)：全部（含 task:admin）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id = 261 OR parent_id = 261;

-- 28 production:all（生产管理员 prod_manager）：view + dispatch + assign + recall + return
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 28, menu_id FROM sys_menu
WHERE (menu_id = 261 OR parent_id = 261)
  AND perms IN ('production:task:view','production:task:dispatch','production:task:assign','production:task:recall','production:task:return');

-- 30 派工主管（车间主任）：view + assign + recall + return
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 30, menu_id FROM sys_menu
WHERE (menu_id = 261 OR parent_id = 261)
  AND perms IN ('production:task:view','production:task:assign','production:task:recall','production:task:return');

-- 31 班组长：view + assign + recall + return
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 31, menu_id FROM sys_menu
WHERE (menu_id = 261 OR parent_id = 261)
  AND perms IN ('production:task:view','production:task:assign','production:task:recall','production:task:return');

-- 32 操作工：view（报工由 work-report 权限体系负责）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 32, menu_id FROM sys_menu
WHERE (menu_id = 261 OR parent_id = 261) AND perms = 'production:task:view';

-- 报工按钮：1/28/29/32（工序执行 48 持有者；幂等）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM sys_menu m
JOIN (SELECT 1 AS role_id UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 32) r
WHERE m.perms IN ('production:work-report:add','production:work-report:cancel');
