-- DEV-20260827-019B：报工代报通道与权限
ALTER TABLE `production_work_report`
  ADD COLUMN `proxy_id` BIGINT NULL COMMENT '代操作人ID（空=本人报工）' AFTER `reporter_name`,
  ADD COLUMN `proxy_name` VARCHAR(64) NULL COMMENT '代操作人姓名快照' AFTER `proxy_id`;

INSERT INTO `sys_menu`
  (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`,
   `menu_type`, `visible`, `status`, `perms`, `icon`, `ancestors`, `route_name`, `requires_auth`,
   `redirect`, `sort`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '代报工', 43, 14, '', NULL, NULL, '1', '0', 'F', '0', '0',
       'production:work-report:proxy', '#', NULL, NULL, '1', NULL, 0,
       'admin', NOW(), 'admin', NOW(), 'DEV-20260827-019B：代报工权限'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_menu` WHERE `perms` = 'production:work-report:proxy'
);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT r.role_id, m.menu_id
FROM (SELECT 1 AS role_id UNION ALL SELECT 28 UNION ALL SELECT 29) r
JOIN `sys_menu` m ON m.`perms` = 'production:work-report:proxy'
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = r.role_id AND x.`menu_id` = m.`menu_id`
);
