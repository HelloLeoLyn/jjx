-- 60_sys_menu_sample_transfer_perm.sql
-- 打样平台列表「资料转移」独立权限点（dev-20260905-009，任务1454）
-- 1) 打样平台(menu_id=239) 下新增 F 按钮节点「资料转移」 perms=engineering:sample:transfer
-- 2) 授权随父菜单 239 现有授权复制（角色管理里按需再调整）

INSERT INTO sys_menu (parent_id, menu_name, order_num, menu_type, visible, status, perms, icon, path, component, query, is_frame, is_cache, remark)
SELECT 239, '资料转移', 1, 'F', 0, 0, 'engineering:sample:transfer', NULL, NULL, NULL, NULL, 1, 0, '打样平台-资料转移(建档/转标准)'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'engineering:sample:transfer');

SET @transfer_menu_id = LAST_INSERT_ID();

-- 授权复制：与父菜单 打样平台(239) 当前授权的角色保持一致
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, @transfer_menu_id
FROM sys_role_menu rm
WHERE rm.menu_id = 239
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = @transfer_menu_id);
