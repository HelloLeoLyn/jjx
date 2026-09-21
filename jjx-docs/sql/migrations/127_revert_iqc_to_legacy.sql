-- ============================================================================
-- 127: 来料侧回退到原方案（不动旧流程/旧台账）——dev-20260917-005 回退 / 用户 2026-09-17 定
-- 定案：来料 = 抽检判定整批，用旧方案（页面/流程/隔离处置台账均不动）；
--       成品 = 保留检验批新模型（分批、复检=更正、差额入库、不良台账、报告）。
-- 本迁移只做三件事：
--   ① 隐藏「来料检验（检验批）」菜单(376)及其按钮(380/383)——来料不再有第二套入口；
--   ② QR-037 进料检验报告 的打印组件退回旧页面（不再走检验批取数）；
--   ③ 清理来料侧可能写入的新模型数据（当前为 0 行，幂等清理）。
-- 代码侧：InventoryInboundServiceImpl 的来料→检验批同步调用与依赖已移除（提交检验只写原表）。
-- ============================================================================
USE `jjx_erp_db`;

-- ① 隐藏来料检验（检验批）入口与其按钮
UPDATE sys_menu SET visible = 1, update_time = NOW() WHERE menu_id = 376;
UPDATE sys_menu SET visible = 1, update_time = NOW() WHERE menu_id IN (380, 383);

-- ② 进料检验报告退回旧打印页（成品 QR-039 仍走检验批新页）
UPDATE quality_template_registry
SET print_component = 'views/production/quality-print/iqc-report.vue',
    remark = CONCAT(COALESCE(remark, ''), ' | 2026-09-17 回退：来料按原方案，QR-037 走旧打印页'),
    update_time = NOW()
WHERE record_no = 'JJX-QR-037';

-- ③ 清理来料侧新模型数据（幂等；成品侧数据保留）
DELETE FROM quality_lot_item WHERE lot_id IN (SELECT lot_id FROM (SELECT lot_id FROM quality_lot WHERE lot_type = 'IQC') x);
DELETE FROM quality_ncr WHERE lot_type = 'IQC';
DELETE FROM quality_lot WHERE lot_type = 'IQC';

-- ④ 核验
SELECT '核验：来料入口应隐藏 / QR-037 应指旧页 / IQC 批应 0 行' AS check_point;
SELECT (SELECT visible FROM sys_menu WHERE menu_id = 376)                AS iqc_lot_menu_visible,
       (SELECT print_component FROM quality_template_registry WHERE record_no = 'JJX-QR-037') AS qr037_component,
       (SELECT COUNT(*) FROM quality_lot WHERE lot_type = 'IQC')          AS iqc_lots,
       (SELECT COUNT(*) FROM quality_lot)                                AS total_lots;
