-- dev-20261125-004：幂等新增混批审批权限；不自动授予现有普通出库审批角色。
-- 配置新增免备份，无DDL。由角色管理授权审批人，超级管理员沿用既有权限机制。
INSERT INTO sys_menu(menu_name,parent_id,order_num,menu_type,visible,status,perms,ancestors,create_by,create_time,update_by,remark)
SELECT '生产领料混批审批', parent.menu_id, 7, 'F', '0', '0',
       'outbound:pick:mixed-batch:approve', CONCAT(parent.ancestors, ',', parent.menu_id),
       'Codex', NOW(), 'Codex', 'dev-20261125-004：跨供应商混批审批，绑定分配内容，未知供应商禁止通过'
FROM sys_menu parent
WHERE parent.perms='inventory:outbound:view' AND parent.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='outbound:pick:mixed-batch:approve');
