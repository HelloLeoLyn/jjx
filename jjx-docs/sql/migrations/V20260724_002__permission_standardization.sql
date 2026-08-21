-- ============================================================
-- Migration: V20260724_002__permission_standardization.sql
-- 全模块权限标准化 + sales:log 独立授权 + 工程菜单授权
-- Applied: 2026-07-24
-- ============================================================

-- ===============================
-- Part 1: sales:log 独立授权
-- ===============================
SET @mid := (SELECT MAX(menu_id) FROM sys_menu);

INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status, create_time)
VALUES (@mid + 1, 13, '操作日志', 'sales:log:view', 'C', 6, 0, NOW());

INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status, create_time)
VALUES (@mid + 2, @mid + 1, '导出日志', 'sales:log:export', 'F', 1, 0, NOW());

INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status, create_time)
VALUES (@mid + 3, @mid + 1, '删除日志', 'sales:log:delete', 'F', 2, 0, NOW());

-- ===============================
-- Part 2: 工程菜单授权给 admin
-- ===============================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id IN (90, 91, 92, 93);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 9, menu_id FROM sys_menu WHERE menu_id IN (90, 91, 92, 93);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE perms LIKE 'sales:log:%';

-- ===============================
-- Part 3: 批量补齐缺失权限
-- ===============================
SET @mid := (SELECT MAX(menu_id) FROM sys_menu);

-- Product 模块
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 6, '设置首页',      'product:index:edit',      'F', 1, 0),
(@mid := @mid + 1, 6, '删除产品',      'product:delete',          'F', 2, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 65, '编辑产品字段',  'product:product:edit',    'F', 1, 0),
(@mid := @mid + 1, 65, '产品废弃',     'product:product:obsolete','F', 2, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 9, '新增BOM',      'product:bom:add',         'F', 1, 0),
(@mid := @mid + 1, 9, '编辑BOM',      'product:bom:edit',        'F', 2, 0),
(@mid := @mid + 1, 9, '删除BOM',      'product:bom:delete',      'F', 3, 0),
(@mid := @mid + 1, 9, '审核BOM',      'product:bom:approve',     'F', 4, 0),
(@mid := @mid + 1, 9, '驳回BOM',      'product:bom:reject',      'F', 5, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 7, '提交审核',     'product:status:submit',   'F', 1, 0),
(@mid := @mid + 1, 7, '审核通过',     'product:status:approve',  'F', 2, 0),
(@mid := @mid + 1, 7, '审核驳回',     'product:status:reject',   'F', 3, 0),
(@mid := @mid + 1, 7, '发布产品',     'product:status:release',  'F', 4, 0);

-- Inventory 模块
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 20, '新增材料',      'inventory:material:add',    'F', 1, 0),
(@mid := @mid + 1, 20, '编辑材料',      'inventory:material:edit',   'F', 2, 0),
(@mid := @mid + 1, 20, '删除材料',      'inventory:material:delete', 'F', 3, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 21, '新增分类',      'inventory:category:add',    'F', 1, 0),
(@mid := @mid + 1, 21, '编辑分类',      'inventory:category:edit',   'F', 2, 0),
(@mid := @mid + 1, 21, '删除分类',      'inventory:category:remove', 'F', 3, 0),
(@mid := @mid + 1, 21, '分类列表',      'inventory:category:list',   'F', 4, 0),
(@mid := @mid + 1, 21, '分类查询',      'inventory:category:query',  'F', 5, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 24, '新增仓库',      'inventory:warehouse:add',    'F', 1, 0),
(@mid := @mid + 1, 24, '编辑仓库',      'inventory:warehouse:edit',   'F', 2, 0),
(@mid := @mid + 1, 24, '删除仓库',      'inventory:warehouse:delete', 'F', 3, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 25, '新增位置',      'inventory:storage-location:add',    'F', 1, 0),
(@mid := @mid + 1, 25, '编辑位置',      'inventory:storage-location:edit',   'F', 2, 0),
(@mid := @mid + 1, 25, '删除位置',      'inventory:storage-location:delete', 'F', 3, 0),
(@mid := @mid + 1, 25, '位置查看',      'inventory:storage-location:view',   'F', 4, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 26, '导入库存',      'inventory:stock:import',     'F', 1, 0),
(@mid := @mid + 1, 26, '库存流水',      'inventory:transaction:view', 'F', 2, 0),
(@mid := @mid + 1, 26, '库存报表',      'inventory:report:view',      'F', 3, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 27, '编辑预警',      'inventory:alert:edit',       'F', 1, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 28, '创建入库',      'inventory:inbound:add',     'F', 1, 0),
(@mid := @mid + 1, 28, '审核入库',      'inventory:inbound:approve', 'F', 2, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 33, '创建出库',      'inventory:outbound:add',     'F', 1, 0),
(@mid := @mid + 1, 33, '审核出库',      'inventory:outbound:approve', 'F', 2, 0),
(@mid := @mid + 1, 33, '编辑出库',      'inventory:outbound:edit',    'F', 3, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 34, '新增盘点',      'inventory:stocktake:add',    'F', 1, 0),
(@mid := @mid + 1, 34, '编辑盘点',      'inventory:stocktake:edit',   'F', 2, 0),
(@mid := @mid + 1, 34, '审核盘点',      'inventory:stocktake:approve','F', 3, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 35, '新增调拨',      'inventory:transfer:add',     'F', 1, 0),
(@mid := @mid + 1, 35, '编辑调拨',      'inventory:transfer:edit',    'F', 2, 0),
(@mid := @mid + 1, 35, '审核调拨',      'inventory:transfer:approve', 'F', 3, 0);

-- Production 模块
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 45, '新增工单',      'production:order:add',    'F', 1, 0),
(@mid := @mid + 1, 45, '编辑工单',      'production:order:edit',   'F', 2, 0),
(@mid := @mid + 1, 45, '删除工单',      'production:order:delete', 'F', 3, 0),
(@mid := @mid + 1, 45, '导出工单',      'production:order:export', 'F', 4, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 48, '执行查看',      'production:operation-execution:view',   'F', 1, 0),
(@mid := @mid + 1, 48, '新增执行',      'production:operation-execution:add',    'F', 2, 0),
(@mid := @mid + 1, 48, '编辑执行',      'production:operation-execution:edit',   'F', 3, 0),
(@mid := @mid + 1, 48, '删除执行',      'production:operation-execution:delete', 'F', 4, 0),
(@mid := @mid + 1, 48, '导出行执行',   'production:operation-execution:export', 'F', 5, 0),
(@mid := @mid + 1, 48, '导入执行',      'production:operation-execution:import', 'F', 6, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 51, '操作记录查看',  'production:operation-record:view',   'F', 1, 0),
(@mid := @mid + 1, 51, '新增操作记录',  'production:operation-record:add',    'F', 2, 0),
(@mid := @mid + 1, 51, '编辑操作记录',  'production:operation-record:edit',   'F', 3, 0),
(@mid := @mid + 1, 51, '删除操作记录',  'production:operation-record:delete', 'F', 4, 0),
(@mid := @mid + 1, 51, '导出操作记录',  'production:operation-record:export', 'F', 5, 0),
(@mid := @mid + 1, 51, '导入操作记录',  'production:operation-record:import', 'F', 6, 0);

-- Purchase 模块
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 37, '新增供应商',    'purchase:supplier:add',    'F', 1, 0),
(@mid := @mid + 1, 37, '编辑供应商',    'purchase:supplier:edit',   'F', 2, 0),
(@mid := @mid + 1, 37, '删除供应商',    'purchase:supplier:delete', 'F', 3, 0),
(@mid := @mid + 1, 37, '导出供应商',    'purchase:supplier:export', 'F', 4, 0),
(@mid := @mid + 1, 37, '导入供应商',    'purchase:supplier:import', 'F', 5, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 38, '新增采购单',    'purchase:order:add',       'F', 1, 0),
(@mid := @mid + 1, 38, '编辑采购单',    'purchase:order:edit',      'F', 2, 0),
(@mid := @mid + 1, 38, '审核采购单',    'purchase:order:approve',   'F', 3, 0),
(@mid := @mid + 1, 38, '导出采购单',    'purchase:order:export',    'F', 4, 0);

-- Purchase Invoice/Payment/Receipt (hidden parent menus)
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status, visible) VALUES
(@mid := @mid + 1, 36, '采购发票',      'purchase:invoice:view',    'C', 3, 0, 1);
SET @inv := @mid;
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, @inv, '新增发票',   'purchase:invoice:add',    'F', 1, 0),
(@mid := @mid + 1, @inv, '编辑发票',   'purchase:invoice:edit',   'F', 2, 0),
(@mid := @mid + 1, @inv, '删除发票',   'purchase:invoice:delete', 'F', 3, 0),
(@mid := @mid + 1, @inv, '导出发票',   'purchase:invoice:export', 'F', 4, 0),
(@mid := @mid + 1, @inv, '导入发票',   'purchase:invoice:import', 'F', 5, 0),
(@mid := @mid + 1, @inv, '发票查看',   'purchase:invoice:view',   'F', 6, 0);

INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status, visible) VALUES
(@mid := @mid + 1, 36, '采购付款',      'purchase:payment:view',    'C', 4, 0, 1);
SET @pay := @mid;
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, @pay, '新增付款',   'purchase:payment:add',    'F', 1, 0),
(@mid := @mid + 1, @pay, '编辑付款',   'purchase:payment:edit',   'F', 2, 0),
(@mid := @mid + 1, @pay, '删除付款',   'purchase:payment:delete', 'F', 3, 0),
(@mid := @mid + 1, @pay, '审核付款',   'purchase:payment:approve','F', 4, 0),
(@mid := @mid + 1, @pay, '导出付款',   'purchase:payment:export', 'F', 5, 0),
(@mid := @mid + 1, @pay, '导入付款',   'purchase:payment:import', 'F', 6, 0),
(@mid := @mid + 1, @pay, '付款查看',   'purchase:payment:view',   'F', 7, 0);

INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status, visible) VALUES
(@mid := @mid + 1, 36, '采购收货',      'purchase:receipt:view',    'C', 5, 0, 1);
SET @rec := @mid;
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, @rec, '新增收货',   'purchase:receipt:add',    'F', 1, 0),
(@mid := @mid + 1, @rec, '编辑收货',   'purchase:receipt:edit',   'F', 2, 0),
(@mid := @mid + 1, @rec, '删除收货',   'purchase:receipt:delete', 'F', 3, 0),
(@mid := @mid + 1, @rec, '导出收货',   'purchase:receipt:export', 'F', 4, 0),
(@mid := @mid + 1, @rec, '导入收货',   'purchase:receipt:import', 'F', 5, 0),
(@mid := @mid + 1, @rec, '收货查看',   'purchase:receipt:view',   'F', 6, 0);

-- System 模块
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 2, '编辑用户',      'system:user:edit',     'F', 2, 0),
(@mid := @mid + 1, 2, '删除用户',      'system:user:delete',   'F', 3, 0),
(@mid := @mid + 1, 2, '重置密码',      'system:user:resetPwd', 'F', 4, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 3, '新增角色',      'system:role:add',     'F', 2, 0),
(@mid := @mid + 1, 3, '编辑角色',      'system:role:edit',    'F', 3, 0),
(@mid := @mid + 1, 3, '删除角色',      'system:role:delete',  'F', 4, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 4, '新增菜单',      'system:menu:add',     'F', 2, 0),
(@mid := @mid + 1, 4, '编辑菜单',      'system:menu:edit',    'F', 3, 0),
(@mid := @mid + 1, 4, '删除菜单',      'system:menu:delete',  'F', 4, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 5, '新增部门',      'system:dept:add',     'F', 2, 0),
(@mid := @mid + 1, 5, '编辑部门',      'system:dept:edit',    'F', 3, 0),
(@mid := @mid + 1, 5, '删除部门',      'system:dept:delete',  'F', 4, 0);
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 61, '新增字典',      'system:dict:add',     'F', 2, 0),
(@mid := @mid + 1, 61, '编辑字典',      'system:dict:edit',    'F', 3, 0),
(@mid := @mid + 1, 61, '删除字典',      'system:dict:delete',  'F', 4, 0),
(@mid := @mid + 1, 61, '字典列表',      'system:dict:list',    'F', 5, 0),
(@mid := @mid + 1, 61, '字典查询',      'system:dict:query',   'F', 6, 0);

-- Sales 补充
INSERT INTO sys_menu (menu_id, parent_id, menu_name, perms, menu_type, order_num, status) VALUES
(@mid := @mid + 1, 17, '开始审核',      'sales:order:review',  'F', 1, 0);
