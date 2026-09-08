-- 采购付款审批/执行状态拆分及权限补齐（任务 1573 / 1244）
-- 执行前备份：jjx_erp_db_backup_20260908-0901_before-payment-workflow.sql

SET @schema_name = DATABASE();

SET @sql = IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema = @schema_name AND table_name = 'purchase_payment' AND column_name = 'approval_status'),
  'SELECT 1',
  'ALTER TABLE purchase_payment ADD COLUMN approval_status varchar(20) NOT NULL DEFAULT ''PENDING'' COMMENT ''审批状态：PENDING/APPROVED/REJECTED'' AFTER payment_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema = @schema_name AND table_name = 'purchase_payment' AND column_name = 'approver_name'),
  'SELECT 1',
  'ALTER TABLE purchase_payment ADD COLUMN approver_name varchar(64) DEFAULT NULL COMMENT ''审批人'' AFTER approval_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS (SELECT 1 FROM information_schema.columns
          WHERE table_schema = @schema_name AND table_name = 'purchase_payment' AND column_name = 'approval_comment'),
  'SELECT 1',
  'ALTER TABLE purchase_payment ADD COLUMN approval_comment varchar(500) DEFAULT NULL COMMENT ''审批意见'' AFTER approver_name'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 历史数据兼容：原状态 2 曾同时表示“审批通过/已付款”，保留为已付款并视为已审批。
UPDATE purchase_payment
SET approval_status = CASE WHEN payment_status = 2 THEN 'APPROVED' ELSE 'PENDING' END
WHERE approval_status IS NULL OR approval_status = '' OR approval_status = 'PENDING';

INSERT INTO sys_menu
  (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type,
   visible, status, perms, icon, ancestors, route_name, requires_auth, redirect, sort,
   create_by, create_time, update_by, update_time, remark)
SELECT permission_name, parent.menu_id, permissions.order_num, '', NULL, NULL, '1', '0', 'F', '0', '0', permissions.perms,
       NULL, CONCAT(parent.ancestors, ',', parent.menu_id), NULL, '1', NULL, permissions.order_num,
       'admin', NOW(), 'admin', NOW(), '采购付款操作权限（任务1573/1244）'
FROM (
  SELECT '新增付款' permission_name, 1 order_num, 'purchase:payment:add' perms
  UNION ALL SELECT '编辑付款', 2, 'purchase:payment:edit'
  UNION ALL SELECT '删除付款', 3, 'purchase:payment:delete'
  UNION ALL SELECT '审批付款', 4, 'purchase:payment:approve'
  UNION ALL SELECT '导出付款', 5, 'purchase:payment:export'
) permissions
JOIN sys_menu parent ON parent.perms = 'purchase:payment:view'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.perms = permissions.perms);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT role_menu.role_id, child.menu_id
FROM sys_role_menu role_menu
JOIN sys_menu parent ON parent.menu_id = role_menu.menu_id AND parent.perms = 'purchase:payment:view'
JOIN sys_menu child ON child.parent_id = parent.menu_id AND child.menu_type = 'F';
