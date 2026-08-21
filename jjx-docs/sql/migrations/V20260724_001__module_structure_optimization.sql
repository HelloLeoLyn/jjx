-- ============================================================
-- Migration: V20260724_001__module_structure_optimization.sql
-- 模块结构优化：删重、隐藏、排序、改名
-- Applied: 2026-07-24
-- ============================================================

-- 1. 删除重复菜单：质量检验(59) - 与质量管理(50) 同名权限同组件
DELETE FROM sys_role_menu WHERE menu_id = 59;
DELETE FROM sys_menu WHERE menu_id = 59;

-- 2. 删除重复菜单：报价审核(89) - 与报价审核(88) 完全重复
DELETE FROM sys_role_menu WHERE menu_id = 89;
DELETE FROM sys_menu WHERE menu_id = 89;

-- 3. 隐藏编辑页（visible=1 为隐藏，0 为显示）
UPDATE sys_menu SET visible = 1 WHERE menu_id = 60; -- 工艺编辑
UPDATE sys_menu SET visible = 1 WHERE menu_id = 72; -- 编辑标准工序

-- 4. 质量管理 → 质量检验（工厂用语更准确）
UPDATE sys_menu SET menu_name = '质量检验' WHERE menu_id = 50;

-- 5. 生产菜单统一排序
UPDATE sys_menu SET order_num = 0 WHERE menu_id = 44;  -- 生产看板
UPDATE sys_menu SET order_num = 1 WHERE menu_id = 45;  -- 生产订单
UPDATE sys_menu SET order_num = 2 WHERE menu_id = 48;  -- 生产执行
UPDATE sys_menu SET order_num = 3 WHERE menu_id = 51;  -- 生产操作
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 50;  -- 质量检验
UPDATE sys_menu SET order_num = 5 WHERE menu_id = 73;  -- 车间看板
UPDATE sys_menu SET order_num = 6 WHERE menu_id = 52;  -- 生产追溯
UPDATE sys_menu SET order_num = 7 WHERE menu_id = 49;  -- 设备管理
UPDATE sys_menu SET order_num = 8 WHERE menu_id = 77;  -- 生产报表
UPDATE sys_menu SET order_num = 9 WHERE menu_id = 76;  -- 成本核算
