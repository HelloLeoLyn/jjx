-- DEV-20260827-018：生产报工业务编号
-- 历史报工允许保持 NULL；MySQL 唯一索引允许存在多条 NULL。
ALTER TABLE `production_work_report`
  ADD COLUMN `report_no` VARCHAR(50) NULL COMMENT '报工单号（业务编号，唯一）' AFTER `report_id`,
  ADD UNIQUE KEY `uk_work_report_no` (`report_no`);
