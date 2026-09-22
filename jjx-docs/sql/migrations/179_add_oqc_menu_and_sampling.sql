-- dev-20260921-042：增加 OQC 出货检验入口与默认 AQL 抽样方案。
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache,
  menu_type, visible, status, perms, ancestors, create_by, create_time, update_by, update_time, remark)
SELECT '出货检验', 338, 7, '/quality/lot/oqc', 'views/quality/lot/oqc-lot.vue', 0, 0,
  'C', 0, 0, 'quality:lot:view', '0,338', 'Codex', NOW(), 'Codex', NOW(), 'OQC 出货检验；发货单自动建批，签收前须合格'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/quality/lot/oqc');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, oqc.menu_id
FROM sys_role_menu rm
JOIN sys_menu fqc ON fqc.menu_id=rm.menu_id AND fqc.path='/quality/lot/fqc'
JOIN sys_menu oqc ON oqc.path='/quality/lot/oqc'
LEFT JOIN sys_role_menu existing ON existing.role_id=rm.role_id AND existing.menu_id=oqc.menu_id
WHERE existing.role_id IS NULL;

INSERT INTO quality_sampling_plan
  (plan_name, lot_type, aql_value, inspection_level, lot_min, lot_max, sample_quantity,
   accept_number, reject_number, is_enabled, remark, create_by, create_time, update_by, update_time, del_flag)
SELECT CONCAT('OQC-', lot_min, '-', lot_max), 'OQC', aql_value, inspection_level, lot_min, lot_max,
  sample_quantity, accept_number, reject_number, is_enabled, '复用 IQC AQL 区间作为 OQC 初始方案',
  'Codex', NOW(), 'Codex', NOW(), 0
FROM quality_sampling_plan source
WHERE source.lot_type='IQC' AND source.del_flag=0
  AND NOT EXISTS (SELECT 1 FROM quality_sampling_plan target
    WHERE target.lot_type='OQC' AND target.lot_min=source.lot_min
      AND target.lot_max=source.lot_max AND target.del_flag=0);
