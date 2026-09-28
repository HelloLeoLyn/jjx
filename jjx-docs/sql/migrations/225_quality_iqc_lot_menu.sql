-- risk: high
-- task: dev-20260928-037
-- 复用检验批工作台，增加 IQC 来料检验批独立入口；不新增页面、不改业务数据。
INSERT INTO sys_menu (
  parent_id, menu_name, path, component, query, is_frame, is_cache, menu_type,
  visible, status, perms, icon, ancestors, route_name, requires_auth, redirect,
  sort, create_by, update_by, remark
)
SELECT 338, '来料检验批', '/quality/lot/iqc', 'views/quality/lot/iqc-lot.vue', NULL,
       '0', '0', 'C', '0', '0', 'quality:lot:view', 'Checked', '0,338',
       'QualityIqcLot', '1', NULL, 5, 'codex', 'codex',
       'dev-20260928-037：复用统一检验批工作台的 IQC 入口'
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE path = '/quality/lot/iqc'
);
