-- dev-20260930-012
-- Retire legacy tables confirmed empty with no runtime/FK dependency on 2026-09-30.
-- engineering_bom_backup_20260809: 0 rows, no application references, no foreign keys.
-- sys_event_config_bak_20260814: 0 rows, no application references, no foreign keys.
-- User-approved schema retirement; full database backup must exist before execution.
DROP TABLE IF EXISTS engineering_bom_backup_20260809;
DROP TABLE IF EXISTS sys_event_config_bak_20260814;
