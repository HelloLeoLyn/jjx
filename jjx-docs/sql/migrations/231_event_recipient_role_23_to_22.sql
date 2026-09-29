-- 231_event_recipient_role_23_to_22.sql
-- 备份人: dahuang   任务码: dev-20260929-009
-- 原因: 库存/采购域事件的通知收件角色写的是「INVENTORY 业务操作」(role_id=23)，但该角色无人绑定，
--       而真正的仓库账号绑的是「INVENTORY 全权限」(role_id=22) → 仓管收不到任何库存/出入库通知。
-- 处理: 把这些事件 target_role 中的 23 替换为 22（保持数组原有顺序，以不影响 task.assign_role 取首元素）。
-- 影响: 仅改 sys_event_config.target_role（改「谁收通知 / 待办落哪个角色」），不动任何业务/库存数据。
-- 范围: sys_event_config 中 target_role 含 23 的全部事件（当前 45 条）。
-- 幂等: 二次执行时已无含 23 的行，UPDATE 不命中。
-- risk: high （批量 UPDATE 数据订正 → 全库快照）
-- 执行: bash scripts/db-migrate.sh 231_event_recipient_role_23_to_22.sql --yes --task dev-20260929-009

UPDATE sys_event_config c
JOIN (
    SELECT c2.id AS id,
           CONCAT('[', GROUP_CONCAT(CASE WHEN x.elem = 23 THEN 22 ELSE x.elem END ORDER BY x.ord SEPARATOR ','), ']') AS new_role
    FROM sys_event_config c2
    JOIN JSON_TABLE(c2.target_role, '$[*]' COLUMNS (elem INT PATH '$', ord FOR ORDINALITY)) x
    WHERE JSON_CONTAINS(c2.target_role, '23')
    GROUP BY c2.id
) t ON t.id = c.id
SET c.target_role = t.new_role;
