-- 260_sample_requisition_permissions_and_qr065.sql
-- dev-20261010-028（2026-10-10）：样品需求单 QR-065 权限按钮 + 恢复启用并绑样品单
-- 幂等：按 perms / record_no 判在，重复执行为 0 影响。
-- 回滚：
--   DELETE FROM sys_role_menu WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE perms IN
--     ('sales:sample:reqsign:sales','sales:sample:reqsign:approve','sales:sample:reqsign:dept','engineering:sample:defect:record'));
--   DELETE FROM sys_menu WHERE perms IN (同上);
--   UPDATE quality_template_registry SET status=2, biz_type=NULL, print_component=NULL, print_mode=NULL, category='blank' WHERE record_no='JJX-QR-065';

-- ① 4 个按钮权限（sys_menu, menu_type=F）
INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,sort,create_by,create_time,remark)
SELECT '需求单-业务签核',229,20,NULL,NULL,'1','0','F','0','0','sales:sample:reqsign:sales',NULL,'0,13,229','1',0,'admin',NOW(),'样品单-需求单业务签核位'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='sales:sample:reqsign:sales');

INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,sort,create_by,create_time,remark)
SELECT '需求单-核准',229,21,NULL,NULL,'1','0','F','0','0','sales:sample:reqsign:approve',NULL,'0,13,229','1',0,'admin',NOW(),'样品单-需求单核准位'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='sales:sample:reqsign:approve');

INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,sort,create_by,create_time,remark)
SELECT '需求单-部门主管',229,22,NULL,NULL,'1','0','F','0','0','sales:sample:reqsign:dept',NULL,'0,13,229','1',0,'admin',NOW(),'样品单-需求单部门主管位'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='sales:sample:reqsign:dept');

INSERT INTO sys_menu
  (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,requires_auth,sort,create_by,create_time,remark)
SELECT '打样不良记录',239,2,NULL,NULL,'1','0','F','0','0','engineering:sample:defect:record',NULL,'0,90,239','1',0,'admin',NOW(),'打样平台-样品单级不良原因及改善记录'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='engineering:sample:defect:record');

-- ② 授权默认：签核 3 位 → 超级管理员(1)+SALES全权限(10)；不良记录 → 超级管理员(1)+ENGINEERING全权限(16)
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM sys_menu m
JOIN (SELECT 1 AS role_id UNION ALL SELECT 10) r
WHERE m.perms IN ('sales:sample:reqsign:sales','sales:sample:reqsign:approve','sales:sample:reqsign:dept');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM sys_menu m
JOIN (SELECT 1 AS role_id UNION ALL SELECT 16) r
WHERE m.perms = 'engineering:sample:defect:record';

-- ③ 恢复 QR-065 生效并绑定样品单（原 2026-10-09 dev-20261009-022 停用解绑）
UPDATE quality_template_registry
SET status = 1,
    category = 'data',
    biz_type = 'sales_sample_order',
    print_component = 'views/sales/sample-order/print.vue',
    print_mode = 'dual',
    qr_enabled = 1,
    biz_module = '销售管理-样品单',
    remark = '2026-10-10 dev-20261010-028：恢复生效并绑样品单（原借壳询价 dev-20260901-057，2026-10-09 dev-20261009-022 停用解绑）'
WHERE record_no = 'JJX-QR-065';
