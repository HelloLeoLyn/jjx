-- 267_remove_duplicate_product_spec_entry.sql
-- dev-20261011-017（2026-10-11）：去掉「产品作业规范」重复入口，只保留菜单 407
-- risk: low（行级菜单/授权订正，幂等；仅动 sys_menu 404/405/406 与 sys_role_menu 对应授权，不涉及业务表）
--
-- 背景：产品作业规范页 views/engineering/product-spec/index.vue 原有 3 个入口：
--   ① 菜单 407「产品作业规范」（产品管理 menu 6 下，C，perms=product:spec:view）——保留
--   ② 菜单 404「生产作业规范」（工程管理 menu 90 下，C，perms=engineering:spec:view）——本次删除
--   ③ 产品列表行内动作「作业规范」——前端已删（本任务代码改动）
-- 用户决定：保留 ①，去掉 ② 与 ③。
--
-- 影响面（执行前实测）：404 → 授权 role 1,16；405/406 → 授权 role 1,16；
--   407 → 授权 role 1,12,14,15。
--   仅 404 未持 407 的角色 = role_id 16（ENGINEERING 全权限）→ 本迁移补授 407。
--
-- 执行后验收：sys_menu 只剩 407 一个「作业规范」C 菜单；405/406 父级=407（无孤儿）；
--   角色 16 持有 407；产品列表页不再出现「作业规范」行内动作。
--
-- 回滚（手工，仅当需要恢复工程侧旧入口时执行）：
--   -- 1) 还原 405/406 父级与祖先链
--   UPDATE sys_menu SET parent_id=404, ancestors='0,90,404' WHERE menu_id IN (405,406);
--   -- 2) 重建菜单 404「生产作业规范」（字段取自删除前实测值）
--   INSERT INTO sys_menu (menu_id,menu_name,parent_id,order_num,path,component,query,is_frame,is_cache,
--       menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,redirect,sort,
--       create_by,create_time,update_by,update_time,remark)
--   VALUES (404,'生产作业规范',90,10,'spec','views/engineering/product-spec/index.vue',NULL,'1','0',
--       'C','0','0','engineering:spec:view','Notebook','0,90',NULL,'1',NULL,10,
--       'admin',NOW(),'admin',NOW(),'');
--   -- 3) 还原 404 授权（原来的持有角色）
--   INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1,404),(16,404);
--   -- 4) 回收本迁移补授的 407 授权（恢复原状）
--   DELETE FROM sys_role_menu WHERE menu_id=407 AND role_id=16;

-- 1) 补授 407：把「原来只有 404、没有 407」的角色授上 407（必须在删除 404 授权之前执行）
--    实测本步补授 role_id=16（ENGINEERING 全权限）。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, 407
FROM sys_role_menu rm
WHERE rm.menu_id = 404
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu e WHERE e.role_id = rm.role_id AND e.menu_id = 407
  );

-- 2) 405/406 重挂到 407（同步 ancestors = '0,6,407'；407 的 ancestors='0,6'），不留孤儿菜单
UPDATE sys_menu
SET parent_id = 407,
    ancestors = '0,6,407',
    update_by = 'hermes',
    update_time = NOW()
WHERE menu_id IN (405, 406);

-- 3) 删除菜单 404「生产作业规范」及其授权
DELETE FROM sys_role_menu WHERE menu_id = 404;
DELETE FROM sys_menu WHERE menu_id = 404;
