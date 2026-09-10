-- dev-20260909-010：建立独立质量管理域，库存菜单按实物作业扁平化。
-- 现有页面使用绝对 path，保持历史 URL、书签及代码跳转不变。

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache,
    menu_type, visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort,
    create_by, create_time, update_by, update_time, remark)
SELECT '质量管理', 0, 260, '/quality', 'layout/index.vue', NULL, '1', '0', 'M', '0', '0', NULL,
    'CircleCheck', '0', 'QualityManagement', '1', NULL, 260, 'Codex', NOW(), 'Codex', NOW(), 'dev-20260909-010'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = '/quality');

SET @quality_menu_id := (SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = '/quality' LIMIT 1);

UPDATE sys_menu SET parent_id=@quality_menu_id,menu_name='来料检验',order_num=1,path='/inventory/iqc',ancestors=CONCAT('0,',@quality_menu_id),update_by='Codex',update_time=NOW(),remark='dev-20260909-010' WHERE menu_id=330;
UPDATE sys_menu SET parent_id=@quality_menu_id,menu_name='生产质检',order_num=2,path='/production/quality',ancestors=CONCAT('0,',@quality_menu_id),update_by='Codex',update_time=NOW(),remark='dev-20260909-010' WHERE menu_id=264;
UPDATE sys_menu SET parent_id=@quality_menu_id,menu_name='不合格品处置',order_num=3,path='/inventory/iqc-quarantine',ancestors=CONCAT('0,',@quality_menu_id),update_by='Codex',update_time=NOW(),remark='dev-20260909-010' WHERE menu_id=333;
UPDATE sys_menu SET parent_id=@quality_menu_id,menu_name='质检报告',order_num=4,path='/production/quality/report',visible='0',ancestors=CONCAT('0,',@quality_menu_id),update_by='Codex',update_time=NOW(),remark='dev-20260909-010' WHERE menu_id=265;
UPDATE sys_menu SET ancestors=CONCAT('0,',@quality_menu_id,',264'),update_by='Codex',update_time=NOW() WHERE parent_id=264;

INSERT INTO sys_role_menu (role_id,menu_id)
SELECT DISTINCT child.role_id,@quality_menu_id FROM sys_role_menu child
WHERE child.menu_id IN (330,264,333,265)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id=child.role_id AND existing.menu_id=@quality_menu_id);

UPDATE sys_menu SET parent_id=18,menu_name='物料档案',order_num=1,path='material',ancestors='0,18',update_by='Codex',update_time=NOW(),remark='dev-20260909-010' WHERE menu_id=19;
UPDATE sys_menu SET parent_id=18,menu_name='仓库与库位',order_num=2,path='warehouse',ancestors='0,18',update_by='Codex',update_time=NOW(),remark='dev-20260909-010' WHERE menu_id=23;
UPDATE sys_menu SET parent_id=18,menu_name='库存台账',order_num=3,path='stock',ancestors='0,18',update_by='Codex',update_time=NOW(),remark='dev-20260909-010' WHERE menu_id=26;
UPDATE sys_menu SET parent_id=18,menu_name='入库作业',order_num=4,path='inbound',ancestors='0,18',update_by='Codex',update_time=NOW(),remark='dev-20260909-010' WHERE menu_id=28;
UPDATE sys_menu SET parent_id=18,menu_name='出库作业',order_num=5,path='outbound',ancestors='0,18',update_by='Codex',update_time=NOW(),remark='dev-20260909-010' WHERE menu_id=33;
UPDATE sys_menu SET parent_id=18,menu_name='盘点作业',order_num=6,path='stocktake',ancestors='0,18',update_by='Codex',update_time=NOW(),remark='dev-20260909-010' WHERE menu_id=34;
UPDATE sys_menu SET parent_id=18,menu_name='调拨作业',order_num=7,path='transfer',ancestors='0,18',update_by='Codex',update_time=NOW(),remark='dev-20260909-010' WHERE menu_id=35;
UPDATE sys_menu SET parent_id=18,menu_name='库存预警',order_num=8,path='alert',ancestors='0,18',update_by='Codex',update_time=NOW(),remark='dev-20260909-010' WHERE menu_id=27;

UPDATE sys_menu SET ancestors='0,18,19',update_by='Codex',update_time=NOW() WHERE parent_id=19;
UPDATE sys_menu SET ancestors=CONCAT('0,18,19,',parent_id),update_by='Codex',update_time=NOW() WHERE parent_id IN (21,22,240);
UPDATE sys_menu SET ancestors='0,18,23',update_by='Codex',update_time=NOW() WHERE parent_id=23;
UPDATE sys_menu SET ancestors=CONCAT('0,18,23,',parent_id),update_by='Codex',update_time=NOW() WHERE parent_id IN (24,25);
UPDATE sys_menu SET ancestors=CONCAT('0,18,',parent_id),update_by='Codex',update_time=NOW() WHERE parent_id IN (26,27,28,33,34,35);

-- 77号迁移建立的临时分组已无子菜单，先清授权再删目录。
DELETE FROM sys_role_menu WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE parent_id=18 AND path IN ('base-data','stock-control','warehouse-operations','io'));
DELETE parent_menu FROM sys_menu parent_menu
LEFT JOIN sys_menu child ON child.parent_id=parent_menu.menu_id
WHERE parent_menu.parent_id=18
  AND parent_menu.path IN ('base-data','stock-control','warehouse-operations','io')
  AND child.menu_id IS NULL;
