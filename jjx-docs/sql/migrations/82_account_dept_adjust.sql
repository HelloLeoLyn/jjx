-- dev-20260910-013：部门组织架构重建（2026-09-10 18:48 完成）后的账号归属收尾。
-- 背景：新架构 16 个部门已在 sys_dept 重建，用户与 HR 员工的 dept_id 已重映射；
--       本次仅调整两个业务账号的部门，使其与新架构职责一致。
-- 安全性：仅 UPDATE 指定 user_name 的 dept_id，无 DROP/TRUNCATE/DELETE；重复执行为幂等（带目标值守卫）。
-- 执行前备份：由 scripts/db-migrate.sh 自动完成。

-- 质量岗位账号 → 品管部(10)
UPDATE sys_user SET dept_id = 10
 WHERE user_name IN ('quality_manager', 'quality_inspector')
   AND dept_id <> 10;

-- 管理员与主账号 → 总经理办公室(2)
UPDATE sys_user SET dept_id = 2
 WHERE user_name IN ('admin', 'gudy')
   AND dept_id <> 2;
