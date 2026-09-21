-- ============================================================================
-- 156_inventory_templates_use_bizno_inbound_outbound.sql
-- 任务码：dev-20260921-013（库存模模块批 1/3：入库 + 出库）
--
-- 目的：入库/出库单据事件模板统一业务单号 {bizNo}
--   改前：入库单【{bizId}】…、出库单【{bizId}】…（自增主键）；入库确认/采购生成入库单两条用 {inboundNo}。
--   改后：统一 {bizNo}（入库单号 / 出库单号）。
--
-- 前置（同任务代码侧，需重启后端生效）：
--   InventoryInboundServiceImpl：created/cancelled/approved/rejected/created_from_production 5 个动作改手写 payload
--     （新增 publishInboundEvent，bizNo=inboundNo），并为既有手写 payload（inbound.confirmed、
--      inbound.created_from_purchase、purchase.received 里的 inboundNo）自动补 bizNo；
--   InventoryOutboundServiceImpl：created/confirmed/cancelled/submitted/approved/rejected/
--     created_from_production/created_from_sales 8 个动作改手写 payload（publishOutboundEvent，bizNo=outboundNo）。
--
-- 未动：inventory.inbound.submitted（配置在、代码无发射点，属 dev-20260921-018 死配置）；
--       调拨/盘点/物料/仓库/库位族在后续子批处理（本迁移不含，避免出现空值）。
--
-- 幂等：REPLACE + LIKE 守卫。
--
-- 自检：
--   SELECT event_code,title FROM sys_event_config
--    WHERE event_code LIKE 'inventory.inbound%' OR event_code LIKE 'inventory.outbound%' ORDER BY event_code;
-- ============================================================================

UPDATE sys_event_config
   SET title = REPLACE(REPLACE(title, '{bizId}', '{bizNo}'), '{inboundNo}', '{bizNo}'),
       content = REPLACE(REPLACE(content, '{bizId}', '{bizNo}'), '{inboundNo}', '{bizNo}')
 WHERE event_code IN ('inventory.inbound.created', 'inventory.inbound.approved',
                      'inventory.inbound.rejected', 'inventory.inbound.cancelled',
                      'inventory.inbound.created_from_production',
                      'inventory.inbound.created_from_purchase', 'inventory.inbound.confirmed')
   AND (title LIKE '%{bizId}%' OR title LIKE '%{inboundNo}%'
        OR content LIKE '%{bizId}%' OR content LIKE '%{inboundNo}%');

UPDATE sys_event_config
   SET title = REPLACE(title, '{bizId}', '{bizNo}'),
       content = REPLACE(content, '{bizId}', '{bizNo}')
 WHERE event_code IN ('inventory.outbound.created', 'inventory.outbound.submitted',
                      'inventory.outbound.approved', 'inventory.outbound.rejected',
                      'inventory.outbound.cancelled', 'inventory.outbound.confirmed',
                      'inventory.outbound.created_from_production',
                      'inventory.outbound.created_from_sales')
   AND (title LIKE '%{bizId}%' OR content LIKE '%{bizId}%');
