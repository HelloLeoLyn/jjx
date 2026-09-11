-- dev-20260911-008：清理已删除“材料分类”菜单的按钮权限子节点
-- 破坏性说明：113-117 原 parent_id=21；父菜单已按用户要求删除，这些节点已成为孤儿且仅服务于材料分类。

DELETE FROM sys_role_menu
WHERE menu_id IN (113, 114, 115, 116, 117);

DELETE FROM sys_menu
WHERE parent_id = 21
  AND menu_id IN (113, 114, 115, 116, 117)
  AND perms LIKE 'inventory:category:%';
