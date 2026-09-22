-- ============================================================================
-- ⚠ 撞号改号（2026-09-22，Hermes）：原为 167_，与同号文件 167_quarantine_dispose_permission_scope.sql 撞号；按「后建者改号」改为 191_，内容一字未动。
-- 167_inbound_event_template_source.sql
-- 任务码：dev-20260921-040（生产完工入库口径：事件 payload / 模板）
--
-- 背景（2026-09-21 实测 SO260921001 完工链路）：
--   · inventory.inbound.confirmed 的 content 写死「采购单 {sourceNo}，供应商 {supplierName}」，
--     生产完工入库没有供应商 → 渲染成空值并刷 7 条 WARN（信息缺失，通知内容不完整）。
--     代码侧已新增 payload 键 sourceDesc（采购单 PR…，供应商 X / 生产工单 WO-…）→ 模板改用它。
--   · inventory.inbound.created_from_production 的 title 是「生产工单【{bizNo}】已生成入库单」，
--     但按事件信封约定 bizNo=本事件业务单号=入库单号（FINISH-WO-…）→ 工单号槽位语义错位
--     （修复前实测渲染为「生产工单【】」）。改为同时打印 sourceNo(工单号) 与 bizNo(入库单号)。
--
-- 幂等：只改这两个事件、按 event_code 定位，可重复执行。
-- 备份：~/jjx-backups/sys_event_config_inbound_tpl_*.sql
-- ============================================================================

UPDATE sys_event_config
   SET content = '① 入库单【{bizNo}】（{sourceDesc}）已完成入库过账，操作人 {operatorName}。② 物料已可领用，生产可据此安排领料/开工；仓管无需再处理。③ 生产请到「生产管理 → 生产订单」安排领料；库存请到「库存管理 → 入库管理」查看；点「去处理」直达。'
 WHERE event_code = 'inventory.inbound.confirmed'
   AND content LIKE '%采购单 {sourceNo}%';

UPDATE sys_event_config
   SET title = '生产工单【{sourceNo}】已生成入库单【{bizNo}】'
 WHERE event_code = 'inventory.inbound.created_from_production'
   AND title NOT LIKE '%{sourceNo}%';
