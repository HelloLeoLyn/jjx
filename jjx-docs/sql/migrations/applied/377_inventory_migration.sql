-- risk: low
-- dev-20261119-001：库存迁移专属权限与流水字典；无新表、无业务数据订正。
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by,create_time,update_by,remark)
SELECT '库存迁移导入',26,20,'#','F','0','0','inventory:migration:import',NULL,'Codex',NOW(),'Codex','dev-20261119-001'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='inventory:migration:import');
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by,create_time,update_by,remark)
SELECT '迁移成本维护',26,21,'#','F','0','0','inventory:migration:cost',NULL,'Codex',NOW(),'Codex','dev-20261119-001'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='inventory:migration:cost');
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT DISTINCT old.role_id,newmenu.menu_id FROM sys_role_menu old
JOIN sys_menu oldmenu ON oldmenu.menu_id=old.menu_id AND oldmenu.perms='inventory:stock:import'
JOIN sys_menu newmenu ON newmenu.perms='inventory:migration:import'
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu r WHERE r.role_id=old.role_id AND r.menu_id=newmenu.menu_id);
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='inventory:migration:cost'
WHERE r.role_key='admin' AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id=r.role_id AND x.menu_id=m.menu_id);
INSERT INTO sys_dict_item (dict_code,item_key,item_value,label,sort_order,is_active)
SELECT 'inventory_transaction_type','migration_cost','migration_cost','迁移成本调整',90,1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='inventory_transaction_type' AND item_key='migration_cost');
INSERT INTO sys_dict_item (dict_code,item_key,item_value,label,sort_order,is_active)
SELECT 'inventory_inbound_type','migration','migration','期初库存迁移',90,1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='inventory_inbound_type' AND item_key='migration');
