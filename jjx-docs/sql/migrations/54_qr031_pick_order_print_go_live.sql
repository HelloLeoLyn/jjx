-- 54_qr031_pick_order_print_go_live.sql
-- 领料单打印落地（任务1319，2026-09-03）
-- 核查：领料单=出库单(PICK- 前缀)，outbound/print.vue 已支持（type=production/pick=1 → 领料单标题+领料人签字栏）
-- 模板升级：QR-031 领料单 + QR-057 出库/出货表单 → data（同一打印页分流：领料记 31、普通出库记 57）
UPDATE quality_template_registry SET category = 'data'
WHERE record_no IN ('JJX-QR-031', 'JJX-QR-057') AND category = 'blank';
