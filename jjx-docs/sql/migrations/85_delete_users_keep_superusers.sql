-- dev-20260910-013：账号清理——只保留超级用户（持 role_key='admin' 的账号）
-- 用户确认（2026-09-10）："删了所有用户，只保留admin和gudy"
-- 当前超级用户：user_id=1 admin(系统管理员)、user_id=134 gudy(林仪增)
--
-- 破坏性语句说明（DELETE）：
--   sys_user          删除除超级用户外的全部账号（40 行）
--   sys_user_role     删除这些账号的角色关联（52 条），避免悬空
--   sys_notification  删除发给这些账号的站内通知，避免悬空收件人
--   另：sys_dept.leader_user_id 指向被删账号的置 NULL（8 个部门）
-- 顺序：先清依赖，再删主体。无外键指向 sys_user，删除不会被阻拦。
-- 安全闸：找不到超级用户时（@keep 为空）**一条都不删**，避免误清全部账号。
-- 执行前备份：scripts/db-migrate.sh 自动完成。

SET @keep := (SELECT GROUP_CONCAT(DISTINCT ur.user_id)
                FROM sys_user_role ur JOIN sys_role r ON r.role_id = ur.role_id
               WHERE r.role_key = 'admin');

DELETE FROM sys_user_role
 WHERE @keep IS NOT NULL AND @keep <> ''
   AND NOT FIND_IN_SET(user_id, @keep);

DELETE FROM sys_notification
 WHERE receiver_id IS NOT NULL
   AND @keep IS NOT NULL AND @keep <> ''
   AND NOT FIND_IN_SET(receiver_id, @keep);

UPDATE sys_dept SET leader_user_id = NULL
 WHERE leader_user_id IS NOT NULL
   AND @keep IS NOT NULL AND @keep <> ''
   AND NOT FIND_IN_SET(leader_user_id, @keep);

DELETE FROM sys_user
 WHERE @keep IS NOT NULL AND @keep <> ''
   AND NOT FIND_IN_SET(user_id, @keep);
