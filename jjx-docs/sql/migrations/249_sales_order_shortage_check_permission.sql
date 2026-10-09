-- dev-20261009-009：销售订单齐套检查独立权限。
-- 仅幂等新增菜单权限配置，无DDL，无业务数据更新，无自动角色授权。
-- 用户执行后在角色管理中授权“销售订单 / 齐套检查”，重新登录刷新权限。
START TRANSACTION;
SET @sales_shortage_parent = (
    SELECT menu_id FROM sys_menu
    WHERE menu_type = 'C' AND perms = 'sales:order:view' LIMIT 1
);
SET @sales_shortage_ancestors = (
    SELECT CONCAT(ancestors, ',', menu_id) FROM sys_menu
    WHERE menu_id = @sales_shortage_parent
);
INSERT INTO sys_menu
    (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,ancestors,requires_auth,create_by,create_time,remark)
SELECT '齐套检查',@sales_shortage_parent,20,'','F','0','0',
       'sales:inventory:alert:check',NULL,@sales_shortage_ancestors,'1','codex',NOW(),
       'dev-20261009-009：销售订单齐套预览及该订单缺料预警生成，不授权其他库存预警编辑操作'
WHERE @sales_shortage_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'sales:inventory:alert:check');
COMMIT;
SELECT menu_id,parent_id,menu_name,perms FROM sys_menu WHERE perms = 'sales:inventory:alert:check';
