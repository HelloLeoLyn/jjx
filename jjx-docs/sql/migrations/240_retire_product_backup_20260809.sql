-- dev-20260930-010
-- product_backup_20260809 是历史产品快照表，当前为空且无业务代码引用。
-- 退役该遗留表，避免清理覆盖率门禁将其误判为未归属业务表。
DROP TABLE IF EXISTS product_backup_20260809;
