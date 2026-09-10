-- ============================================================================
-- 迁移: 88_add_sales_button_perms.sql
-- 执行人: hermes-agent
-- 任务码: dev-20260910-014（销售模块权限粒度整改）
-- 原因:
--   1) 分离页面查看权限：收款单管理(295)、销售发票(296) 的 perms 原为 sales:order:view，
--      与销售订单(16) 同码 → 授予「销售订单查看」即同时放开收款/发票，无法单独收敛。
--      本次改为各自独立：sales:receipt:view / sales:invoice:view。
--   2) 补齐按钮级权限：收款单/发票/发货单/报表/对账/跟踪 页面按钮原先无 v-hasPermi，
--      仅持有页面查看权限即可执行写/输出动作。本次新增 6 个 F 型按钮权限点。
--   授权策略（保持行为不变）：每个新按钮权限授予「已持有其父菜单」的同一批角色，
--      即当前有页面访问权的角色都能继续用，管理员后续可再细分。父菜单现授权角色 = 1/10/20/21。
-- 类型: 幂等（INSERT..WHERE NOT EXISTS / UPDATE 定值），无破坏性语句。
-- 影响面: sys_menu（2 行 perms 变更 + 6 行新增）、sys_role_menu（新增按钮授权）。
-- 备份: scripts/db-migrate.sh 执行前自动全库备份（before-88）。
-- ============================================================================

-- ---- 1. 分离页面查看权限 ------------------------------------------------
UPDATE sys_menu SET perms='sales:receipt:view', update_by='hermes-agent' WHERE menu_id=295;
UPDATE sys_menu SET perms='sales:invoice:view', update_by='hermes-agent' WHERE menu_id=296;

-- ---- 2. 新增按钮级权限（F 型）+ 授权给父菜单角色 -------------------------

-- 2.1 收款单 - 新增收款
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,redirect,sort,create_by,create_time,update_by,update_time,remark)
SELECT '收款单-新增',295,1,'',NULL,NULL,'1','0','F','0','0','sales:receipt:add',NULL,'0,13,295',NULL,'1',NULL,1,'hermes-agent',NOW(),'hermes-agent',NOW(),'dev-20260910-014 按钮级权限'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='sales:receipt:add');
SET @mid := (SELECT menu_id FROM sys_menu WHERE perms='sales:receipt:add' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT rm.role_id,@mid FROM sys_role_menu rm
WHERE rm.menu_id=295 AND @mid IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id=rm.role_id AND e.menu_id=@mid);

-- 2.2 销售发票 - 打印
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,redirect,sort,create_by,create_time,update_by,update_time,remark)
SELECT '销售发票-打印',296,1,'',NULL,NULL,'1','0','F','0','0','sales:invoice:print',NULL,'0,13,296',NULL,'1',NULL,1,'hermes-agent',NOW(),'hermes-agent',NOW(),'dev-20260910-014 按钮级权限'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='sales:invoice:print');
SET @mid := (SELECT menu_id FROM sys_menu WHERE perms='sales:invoice:print' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT rm.role_id,@mid FROM sys_role_menu rm
WHERE rm.menu_id=296 AND @mid IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id=rm.role_id AND e.menu_id=@mid);

-- 2.3 发货管理 - 签收
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,redirect,sort,create_by,create_time,update_by,update_time,remark)
SELECT '发货单-签收',218,1,'',NULL,NULL,'1','0','F','0','0','sales:delivery:receive',NULL,'0,13,218',NULL,'1',NULL,1,'hermes-agent',NOW(),'hermes-agent',NOW(),'dev-20260910-014 按钮级权限'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='sales:delivery:receive');
SET @mid := (SELECT menu_id FROM sys_menu WHERE perms='sales:delivery:receive' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT rm.role_id,@mid FROM sys_role_menu rm
WHERE rm.menu_id=218 AND @mid IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id=rm.role_id AND e.menu_id=@mid);

-- 2.4 销售报表 - 导出
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,redirect,sort,create_by,create_time,update_by,update_time,remark)
SELECT '销售报表-导出',215,1,'',NULL,NULL,'1','0','F','0','0','sales:report:export',NULL,'0,13,215',NULL,'1',NULL,1,'hermes-agent',NOW(),'hermes-agent',NOW(),'dev-20260910-014 按钮级权限'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='sales:report:export');
SET @mid := (SELECT menu_id FROM sys_menu WHERE perms='sales:report:export' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT rm.role_id,@mid FROM sys_role_menu rm
WHERE rm.menu_id=215 AND @mid IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id=rm.role_id AND e.menu_id=@mid);

-- 2.5 业务对账 - 打印
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,redirect,sort,create_by,create_time,update_by,update_time,remark)
SELECT '业务对账-打印',326,1,'',NULL,NULL,'1','0','F','0','0','sales:reconcile:print',NULL,'0,13,326',NULL,'1',NULL,1,'hermes-agent',NOW(),'hermes-agent',NOW(),'dev-20260910-014 按钮级权限'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='sales:reconcile:print');
SET @mid := (SELECT menu_id FROM sys_menu WHERE perms='sales:reconcile:print' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT rm.role_id,@mid FROM sys_role_menu rm
WHERE rm.menu_id=326 AND @mid IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id=rm.role_id AND e.menu_id=@mid);

-- 2.6 订单跟踪 - 导出
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,redirect,sort,create_by,create_time,update_by,update_time,remark)
SELECT '订单跟踪-导出',17,1,'',NULL,NULL,'1','0','F','0','0','sales:tracking:export',NULL,'0,13,17',NULL,'1',NULL,1,'hermes-agent',NOW(),'hermes-agent',NOW(),'dev-20260910-014 按钮级权限'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='sales:tracking:export');
SET @mid := (SELECT menu_id FROM sys_menu WHERE perms='sales:tracking:export' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu (role_id,menu_id)
SELECT rm.role_id,@mid FROM sys_role_menu rm
WHERE rm.menu_id=17 AND @mid IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id=rm.role_id AND e.menu_id=@mid);

-- 执行后验证（手工）:
--   SELECT menu_id,menu_name,parent_id,perms FROM sys_menu WHERE perms LIKE 'sales:%' AND menu_type='F' ORDER BY menu_id;
--   SELECT m.perms, GROUP_CONCAT(r.role_name ORDER BY r.role_id) FROM sys_menu m JOIN sys_role_menu rm ON rm.menu_id=m.menu_id JOIN sys_role r ON r.role_id=rm.role_id WHERE m.perms IN ('sales:receipt:add','sales:invoice:print','sales:delivery:receive','sales:report:export','sales:reconcile:print','sales:tracking:export') GROUP BY m.perms;
