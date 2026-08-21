-- =====================================================
-- V20260821_004：生产管理菜单清理（TT-UI-02）
-- 定位：生产管理(menu_id=43) 菜单树收敛为唯一 6 项：
--   45 生产订单 / 261 派工管理 / 48 工序执行 / 264 质检管理 / 52 生产追溯 / 49 设备管理
-- 背景：
--   旧 Dispatch 模块与 Task Tree 阶段多次建菜单，父菜单（51 操作记录 / 253 工装模具）已删除，
--   但其 F 按钮与 sys_role_menu 关系残留为孤儿行（历史菜单脏数据），角色权限树不干净。
--   本 migration 删除这些孤儿按钮及其角色关系；生产订单/派工管理/工序执行/质检管理/生产追溯/设备管理 六项保留。
-- 幂等：重复执行结果一致（按 perms 模式删除，不影响 6 项菜单）。
-- =====================================================

-- ---------- 1) 删除生产管理历史孤儿按钮的角色关系 ----------
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.perms LIKE 'production:operation-record:%'
   OR m.perms LIKE 'production:tooling:%';

-- ---------- 2) 删除孤儿按钮菜单（父菜单 51/253 已不存在） ----------
DELETE FROM sys_menu
WHERE perms LIKE 'production:operation-record:%'
   OR perms LIKE 'production:tooling:%';

-- ---------- 3) 生产管理 6 项菜单顺序归一（对齐卡片枚举顺序） ----------
UPDATE sys_menu SET order_num = 1 WHERE menu_id = 45; -- 生产订单
UPDATE sys_menu SET order_num = 2 WHERE menu_id = 261; -- 派工管理
UPDATE sys_menu SET order_num = 3 WHERE menu_id = 48; -- 工序执行
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 264; -- 质检管理
UPDATE sys_menu SET order_num = 5 WHERE menu_id = 52; -- 生产追溯
UPDATE sys_menu SET order_num = 6 WHERE menu_id = 49; -- 设备管理

-- ---------- 4) 角色授权矩阵一致性（权限树可渲染、按钮矩阵对齐业务语义） ----------
-- 4.1 生产管理员(28)：去掉 task:admin（task:admin 不下放普通生产角色；保留 view+dispatch+assign+recall+return）
DELETE FROM sys_role_menu WHERE role_id = 28 AND menu_id = 291;

-- 4.2 车间主任(30)/班组长(31)：补齐父菜单 43（生产管理），使其角色权限树可渲染：
--     生产管理(43) → 派工管理(261) → 分配任务/收回任务/退回任务
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (30, 43), (31, 43);
