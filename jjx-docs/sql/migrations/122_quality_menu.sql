-- ============================================================================
-- 122: 质量管理菜单（检验批模型入口）——质量重构批3 / dev-20260917-009/010/011/012
-- 口径（2026-09-17 用户定 · 方案B）：来料与成品两条完全独立；菜单挂在既有"质量管理"(338) 下；
--   新页面权限统一 quality:*（旧菜单 inventory:inbound:view / production:quality:view 暂留，
--   待前端流程切换完成后再隐藏，见 011）。
-- 幂等：按 perms + menu_name 判存，重复执行不重复插入。
-- ============================================================================
USE `jjx_erp_db`;

-- ① 页面菜单
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, ancestors, requires_auth, create_by, create_time, remark)
SELECT '来料检验（检验批）', 338, 5, '/quality/lot/iqc', 'views/quality/lot/iqc-lot.vue', 0, 0, 'C', 0, 0, 'quality:lot:view', '0,338', 1, 'Hermes', NOW(), '质量重构：来料检验批（可分批、余量、不良台账）'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT perms, menu_name, parent_id FROM sys_menu) t WHERE t.perms = 'quality:lot:view' AND t.menu_name = '来料检验（检验批）');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, ancestors, requires_auth, create_by, create_time, remark)
SELECT '成品检验（检验批）', 338, 6, '/quality/lot/fqc', 'views/quality/lot/fqc-lot.vue', 0, 0, 'C', 0, 0, 'quality:lot:view', '0,338', 1, 'Hermes', NOW(), '质量重构：成品检验批（报工审批建批、分批、复检=更正）'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT perms, menu_name, parent_id FROM sys_menu) t WHERE t.perms = 'quality:lot:view' AND t.menu_name = '成品检验（检验批）');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, ancestors, requires_auth, create_by, create_time, remark)
SELECT '不良台账', 338, 7, '/quality/ncr', 'views/quality/ncr/index.vue', 0, 0, 'C', 0, 0, 'quality:ncr:view', '0,338', 1, 'Hermes', NOW(), '质量重构：不良台账（返工/让步接收/报废）'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT perms, menu_name, parent_id FROM sys_menu) t WHERE t.perms = 'quality:ncr:view' AND t.menu_name = '不良台账');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, ancestors, requires_auth, create_by, create_time, remark)
SELECT '抽样方案', 338, 8, '/quality/sampling-plan', 'views/quality/sampling-plan/index.vue', 0, 0, 'C', 0, 0, 'quality:plan:config', '0,338', 1, 'Hermes', NOW(), '质量重构：AQL 抽样方案（来料用）'
WHERE NOT EXISTS (SELECT 1 FROM (SELECT perms, menu_name, parent_id FROM sys_menu) t WHERE t.perms = 'quality:plan:config' AND t.menu_name = '抽样方案');

-- ② 按钮权限（挂到两个检验批页面 + 不良台账）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, ancestors, requires_auth, create_by, create_time)
SELECT '检验录入', m.menu_id, 1, '', NULL, 0, 0, 'F', 0, 0, 'quality:lot:inspect', CONCAT('0,338,', m.menu_id), 1, 'Hermes', NOW()
FROM sys_menu m WHERE m.menu_name IN ('来料检验（检验批）', '成品检验（检验批）') AND m.perms = 'quality:lot:view'
  AND NOT EXISTS (SELECT 1 FROM (SELECT perms, parent_id FROM sys_menu) t WHERE t.perms = 'quality:lot:inspect' AND t.parent_id = m.menu_id);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, ancestors, requires_auth, create_by, create_time)
SELECT '检验判定', m.menu_id, 2, '', NULL, 0, 0, 'F', 0, 0, 'quality:lot:judge', CONCAT('0,338,', m.menu_id), 1, 'Hermes', NOW()
FROM sys_menu m WHERE m.menu_name IN ('来料检验（检验批）', '成品检验（检验批）') AND m.perms = 'quality:lot:view'
  AND NOT EXISTS (SELECT 1 FROM (SELECT perms, parent_id FROM sys_menu) t WHERE t.perms = 'quality:lot:judge' AND t.parent_id = m.menu_id);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, ancestors, requires_auth, create_by, create_time)
SELECT '不良处置', m.menu_id, 1, '', NULL, 0, 0, 'F', 0, 0, 'quality:ncr:dispose', CONCAT('0,338,', m.menu_id), 1, 'Hermes', NOW()
FROM sys_menu m WHERE m.menu_name = '不良台账' AND m.perms = 'quality:ncr:view'
  AND NOT EXISTS (SELECT 1 FROM (SELECT perms, parent_id FROM sys_menu) t WHERE t.perms = 'quality:ncr:dispose' AND t.parent_id = m.menu_id);

-- ③ 授权：超级管理员(1)、生产全权限(28)、来料检验员(33)、品质主管(34)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms IN ('quality:lot:view', 'quality:lot:inspect', 'quality:lot:judge', 'quality:ncr:view', 'quality:ncr:dispose', 'quality:plan:config')
WHERE r.role_id IN (1, 28, 33, 34)
  AND NOT EXISTS (SELECT 1 FROM (SELECT role_id, menu_id FROM sys_role_menu) rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id);

SELECT '122 质量管理菜单就绪' AS check_point;
SELECT menu_id, menu_name, parent_id, menu_type, perms, path FROM sys_menu WHERE ancestors LIKE '%338%' AND (perms LIKE 'quality:%' OR menu_name IN ('来料检验（检验批）','成品检验（检验批）','不良台账','抽样方案')) ORDER BY menu_id;
