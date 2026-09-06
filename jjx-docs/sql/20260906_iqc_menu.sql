-- IQC 隔离台账菜单：挂在库存管理下，用户从左侧菜单进入。
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,route_name,create_by,create_time,remark)
SELECT 'IQC隔离台账',18,260,'iqc-quarantine','views/inventory/iqc-quarantine/index.vue',0,0,'C','0','0','inventory:inbound:view','Warning','InventoryIqcQuarantine','admin',NOW(),'IQC隔离与处置单台账'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='iqc-quarantine' AND parent_id=18);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r CROSS JOIN sys_menu m
WHERE m.path = 'iqc-quarantine' AND r.role_id IN (1,22,23,24,33,34)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id=r.role_id AND x.menu_id=m.menu_id);

-- ͳһIQC���
UPDATE sys_menu SET menu_name='IQC���ϼ��', path='iqc', component='views/inventory/iqc/index.vue' WHERE menu_id=330;
