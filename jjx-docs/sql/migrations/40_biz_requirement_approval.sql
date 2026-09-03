-- 40_biz_requirement_approval.sql
-- 业务需求单：四部门会签（任务 1248 P1-②）
-- 会签替代单一审核：评审中(2)由工程/制造/采购仓库/品管四部门表态，全部同意自动生效(3)，任一不同意驳回(6)

-- 1. 会签按钮权限点（挂在需求管理 318 下）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '需求会签', 318, 6, '', NULL, 1, 1, 'F', '0', '0', 'biz:requirement:approve', '#', 'admin', NOW(), '四部门会签'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'biz:requirement:approve');

-- 2. 需求管理入口分给会签四部门角色（工程17/制造28/采购26/仓库23/品管29）
-- 目录317 + 页面318 + 会签按钮322 一并绑定
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r JOIN sys_menu m ON m.menu_id IN (317, 318, (SELECT menu_id FROM sys_menu WHERE perms = 'biz:requirement:approve'))
WHERE r.role_key IN ('engineering:ops', 'production:all', 'production:ops', 'purchase:ops', 'inventory:ops');
