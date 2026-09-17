-- ============================================================================
-- 132: 来料检验权限纳管与职责分离
-- 风险：包含 sys_role_menu 权限关系删除；执行前必须按规范完成数据库备份。
-- ============================================================================
USE `jjx_erp_db`;

-- 旧版来料检验仍是当前正式入口，将页面权限从库存查看切换为质量查看。
UPDATE sys_menu
SET perms = 'quality:lot:view',
    update_by = 'Codex',
    update_time = NOW(),
    remark = CONCAT(COALESCE(remark, ''), ' | 2026-09-17：来料检验权限纳管')
WHERE path = '/inventory/iqc'
  AND component = 'views/inventory/iqc/index.vue'
  AND perms <> 'quality:lot:view';

-- 不合格品处置台账改用质量不良台账查看权限。
UPDATE sys_menu
SET perms = 'quality:ncr:view',
    update_by = 'Codex',
    update_time = NOW(),
    remark = CONCAT(COALESCE(remark, ''), ' | 2026-09-17：不良台账权限纳管')
WHERE path = '/inventory/iqc-quarantine'
  AND component = 'views/inventory/iqc-quarantine/index.vue'
  AND perms <> 'quality:ncr:view';

-- 按钮权限挂在当前正式入口下，便于角色授权页面按功能分配。
INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
     visible, status, perms, ancestors, requires_auth, create_by, create_time, remark)
SELECT '检验录入', m.menu_id, 1, '', NULL, 0, 0, 'F',
       0, 0, 'quality:lot:inspect', CONCAT('0,338,', m.menu_id), 1,
       'Codex', NOW(), '来料检验：录入、提交、复检'
FROM sys_menu m
WHERE m.path = '/inventory/iqc'
  AND m.component = 'views/inventory/iqc/index.vue'
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT parent_id, perms FROM sys_menu) x
      WHERE x.parent_id = m.menu_id AND x.perms = 'quality:lot:inspect'
  );

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type,
     visible, status, perms, ancestors, requires_auth, create_by, create_time, remark)
SELECT '检验判定', m.menu_id, 2, '', NULL, 0, 0, 'F',
       0, 0, 'quality:lot:judge', CONCAT('0,338,', m.menu_id), 1,
       'Codex', NOW(), '来料检验：审核通过、驳回'
FROM sys_menu m
WHERE m.path = '/inventory/iqc'
  AND m.component = 'views/inventory/iqc/index.vue'
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT parent_id, perms FROM sys_menu) x
      WHERE x.parent_id = m.menu_id AND x.perms = 'quality:lot:judge'
  );

-- 来料检验员（33）只保留查看与检验录入，不具备审核、处置及方案配置权限。
DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE rm.role_id = 33
  AND m.perms IN ('quality:lot:judge', 'quality:ncr:view', 'quality:ncr:dispose', 'quality:plan:config');

-- 来料检验员（33）与品质主管（34）均可进入正式入口并进行检验录入。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON (
       (m.path = '/inventory/iqc' AND m.component = 'views/inventory/iqc/index.vue')
       OR (m.parent_id = (
              SELECT iqc.menu_id
              FROM sys_menu iqc
              WHERE iqc.path = '/inventory/iqc'
                AND iqc.component = 'views/inventory/iqc/index.vue'
              LIMIT 1
          ) AND m.perms = 'quality:lot:inspect')
     )
WHERE r.role_id IN (33, 34)
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT role_id, menu_id FROM sys_role_menu) existing
      WHERE existing.role_id = r.role_id AND existing.menu_id = m.menu_id
  );

-- 品质主管（34）负责审核，并保留不良台账查看与处置权限。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 34, m.menu_id
FROM sys_menu m
WHERE m.perms IN ('quality:lot:judge', 'quality:ncr:view', 'quality:ncr:dispose')
  AND NOT EXISTS (
      SELECT 1 FROM (SELECT role_id, menu_id FROM sys_role_menu) existing
      WHERE existing.role_id = 34 AND existing.menu_id = m.menu_id
  );

-- 核验：角色 33 不应出现 judge/dispose；角色 34 应具备 inspect/judge/dispose。
SELECT r.role_id, m.menu_name, m.perms, m.path
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE r.role_id IN (33, 34)
  AND (m.perms LIKE 'quality:%' OR m.path = '/inventory/iqc')
ORDER BY r.role_id, m.menu_id;
