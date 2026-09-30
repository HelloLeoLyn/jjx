-- dev-20260930-014
-- 「工单补料」权限点归属修正：从「库存管理 → 出库作业」(menu 33)
-- 迁移到「生产管理 → 生产订单」(menu 45)，与入口位置（生产订单行操作「申请补料（超耗/报废补产）」）对齐。
--
-- 背景：按钮入口在生产订单页，权限点却挂在出库作业树下 → 建角色时只勾「生产订单」的角色
--       在权限树里找不到该按钮，PRODUCTION 相关角色实际都拿不到 inventory:outbound:supplement。
--
-- 幂等/安全：不改 menu_id(400) 与 perms(inventory:outbound:supplement)；
--           sys_role_menu 绑定的是 menu_id，故已授权角色（超级管理员 / INVENTORY 全权限 / QUALITY 品质主管）不受影响。
--           重复执行影响 1 行（原地更新，无副作用）。

UPDATE sys_menu
SET parent_id = 45
WHERE menu_id = 400
  AND perms = 'inventory:outbound:supplement'
  AND EXISTS (SELECT 1 FROM (SELECT menu_id FROM sys_menu WHERE menu_id = 45) AS target_parent);

-- 核验：parent_id 应为 45（生产订单）
-- SELECT menu_id, parent_id, menu_name, perms FROM sys_menu WHERE menu_id = 400;
