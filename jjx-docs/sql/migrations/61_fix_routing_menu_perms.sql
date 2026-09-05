-- 61_fix_routing_menu_perms.sql
-- 工艺路线权限修复（任务1458/dev-20260905-013 前后端配套，本文件只管数据）
-- 1) 新增工艺(211)/编辑工艺(212) 按钮节点父级从 90(工程管理目录) 改挂到 10(工艺路线页)
-- 2) 工程·审核员(role 18) 补授 审核工艺(267)/驳回工艺(268)

UPDATE sys_menu SET parent_id = 10, update_time = NOW()
WHERE menu_id IN (211, 212) AND parent_id = 90;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 18, m.menu_id FROM sys_menu m
WHERE m.menu_id IN (267, 268)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = 18 AND x.menu_id = m.menu_id);
