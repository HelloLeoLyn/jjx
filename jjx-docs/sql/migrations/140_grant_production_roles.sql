-- ============================================================================
-- 140_grant_production_roles.sql
-- 任务码：dev-20260918-012
--
-- 目的：给生产中心（部门 12 及其四个车间）当前没挂角色的 25 个账号补岗位角色。
--       角色沿用系统既有的三级派工模型，不自创：
--         32 production:worker          操作工（出库作业/工序执行/派工管理 3 个页面）
--         31 production:dispatch_leader 班组长（本班组接单 + 定执行人）
--         30 production:dispatch_mgr    车间主任（派工到班组）
--
-- 分配规则（用户 2026-09-18 确认「按建议来」）：
--   1) 一线员工 → 32；共 24 人。
--   2) 每个车间 1 名班组长 → 31（同时保留 32，班组长也要干活）：
--        冲型车间 曾素凤(156) / 加工车间 傅金枝(169) / 印刷车间 林宋锋(162)
--        选人规则 = 该车间里「建号最早」的员工；如与实际班组长不符，在角色管理里改一个人即可。
--   3) 车间负责人但没角色的两人 → 30（与已配好的温丽云/林浩 口径一致）：
--        冲型车间负责人 李浩东(182)、刀模中心负责人 曹建松(161)；曹建松同时是唯一员工，再给 32。
--   4) 叶泽清(140，生产中心负责人) 保持 28 全权限不动；不给一线员工 28。
--
-- 依据：
--   - 无角色 = 登录后看不到菜单（菜单/按钮由角色授权决定）。
--   - 派工候选人靠「部门树」而非角色（ProductionTaskMapper.selectAssigneeTreeUsers 用
--     sys_dept.leader_user_id 递归），角色 key 只用于显示身份（主任/班组长/操作工）。
--   - 生效需重新登录（权限快照在登录会话里）。
--
-- 幂等：INSERT ... WHERE NOT EXISTS，可重复执行。
-- 回滚：DELETE FROM sys_user_role WHERE (user_id, role_id) IN (本次写入的 29 条)。
-- ============================================================================

-- 1) 一线员工 → 32 操作工（24 人）
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, 32
  FROM sys_user u
 WHERE u.del_flag = '0'
   AND u.user_id IN (
       156, 166, 167, 168, 183, 184,        -- 冲型车间：曾素凤 林美静 李雪梅 梁均沛 郑海红 曾光华
       169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181,  -- 加工车间 13 人
       162, 163, 164, 165,                  -- 印刷车间：林宋锋 林美诗 蔡万烨 黄正亮
       161                                  -- 刀模中心：曹建松
   )
   AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.user_id AND ur.role_id = 32);

-- 2) 班组长 → 31（3 人，同时保留上面给的 32）
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, 31
  FROM sys_user u
 WHERE u.del_flag = '0'
   AND u.user_id IN (156, 169, 162)          -- 曾素凤(冲型) 傅金枝(加工) 林宋锋(印刷)
   AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.user_id AND ur.role_id = 31);

-- 3) 车间负责人 → 30 派工主管（2 人）
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.user_id, 30
  FROM sys_user u
 WHERE u.del_flag = '0'
   AND u.user_id IN (182, 161)               -- 李浩东(冲型车间负责人) 曹建松(刀模中心负责人)
   AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.user_id AND ur.role_id = 30);

-- ============================================================================
-- 执行后自检（生产中心应 0 人无角色；31 应为 3 人；30 应为 4 人）：
--   SELECT d.dept_name, COUNT(*) 人数, SUM(CASE WHEN ur.user_id IS NULL THEN 1 ELSE 0 END) 无角色
--     FROM sys_user u JOIN sys_dept d ON d.dept_id=u.dept_id
--     LEFT JOIN sys_user_role ur ON ur.user_id=u.user_id
--    WHERE u.del_flag='0' AND u.dept_id IN (12,13,14,15,16) GROUP BY d.dept_name;
--   SELECT role_id, COUNT(*) FROM sys_user_role WHERE role_id IN (28,30,31,32) GROUP BY role_id;
-- ============================================================================
