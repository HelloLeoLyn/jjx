-- ============================================================================
-- 199_rename_quality_menus.sql
-- 任务：dev-20260922-014（用户 2026-09-22 17:04「那就改个容易区分或者针对性的名字」）
--
-- 起因：质量管理下两个页名字都带「不良/不合格」，容易混：
--   333「不合格品处置」= 材料/来料侧（隔离台账 inventory_iqc_quarantine + 处置单，接口 inbound/iqc）
--   378「不良台账」    = 产品侧（quality_ncr + quality_ncr_action，lot_type=FQC/OQC）
-- 改名（只改显示名，不动 path/component/perms，避免影响路由与授权）：
--   333 → 来料不合格处置
--   378 → 产品不良台账
-- 幂等：UPDATE 按 menu_id + 旧名条件，可重复执行。
-- 前置 guard 备份：~/jjx-backups/sys_menu_quality_rename_before_20260922-1705.sql
--   （md5 2913a5230472e51139583fd50606b2c3）
-- ============================================================================
USE `jjx_erp_db`;

UPDATE sys_menu SET menu_name = '来料不合格处置', update_time = NOW()
 WHERE menu_id = 333 AND menu_name = '不合格品处置';

UPDATE sys_menu SET menu_name = '产品不良台账', update_time = NOW()
 WHERE menu_id = 378 AND menu_name = '不良台账';

-- ── 核验 ──────────────────────────────────────────────────────────────────
SELECT menu_id, menu_name, path, component, perms FROM sys_menu WHERE menu_id IN (333, 378);
