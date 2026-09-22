-- ============================================================================
-- 176: 事件配置遗留收口（dev-20260921-018）
-- 说明：保留死配置行但明确禁用；为现有代码发射点补齐幂等配置。
-- 本脚本只生成，不在当前批次直接执行。
-- ============================================================================
USE `jjx_erp_db`;

UPDATE `sys_event_config`
SET `is_enabled` = 0, `update_time` = NOW()
WHERE `event_code` IN ('order.sent_to_customer', 'inventory.inbound.submitted')
  AND `is_enabled` <> 0;

INSERT INTO `sys_event_config`
    (`event_code`, `event_name`, `biz_module`, `event_type`, `kanban_module`, `priority`,
     `is_enabled`, `target_role`, `title`, `content`, `exclude_trigger`)
SELECT 'order.delivering', '销售订单发货', 'sales', 'notification', 'biz', 'normal',
       1, JSON_ARRAY(20, 21), '订单【{bizNo}】已发货，发货单【{deliveryNo}】',
       '订单【{bizNo}】本次发货 {deliverQuantity}，请跟进物流与客户签收。', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_event_config` WHERE `event_code` = 'order.delivering');

INSERT INTO `sys_event_config`
    (`event_code`, `event_name`, `biz_module`, `event_type`, `kanban_module`, `priority`,
     `is_enabled`, `target_role`, `title`, `content`, `exclude_trigger`)
SELECT 'purchase.arrived', '采购到货联动', 'purchase', 'notification', 'biz', 'normal',
       1, JSON_ARRAY(26, 23, 33), '采购单【{sourceNo}】已到货',
       '采购到货已生成入库单，请安排来料检验与入库。', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_event_config` WHERE `event_code` = 'purchase.arrived');

INSERT INTO `sys_event_config`
    (`event_code`, `event_name`, `biz_module`, `event_type`, `kanban_module`, `priority`,
     `is_enabled`, `target_role`, `title`, `content`, `exclude_trigger`)
SELECT 'quality.iqc.item.approved', 'IQC 明细审核通过', 'quality', 'notification', 'biz', 'normal',
       1, NULL, '入库单【{bizNo}】物料【{materialCode}】审核通过',
       '物料 {materialName}（{materialCode}）的来料检验明细已审核通过。', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_event_config` WHERE `event_code` = 'quality.iqc.item.approved');

INSERT INTO `sys_event_config`
    (`event_code`, `event_name`, `biz_module`, `event_type`, `kanban_module`, `priority`,
     `is_enabled`, `target_role`, `title`, `content`, `exclude_trigger`)
SELECT 'quality.iqc.item.rejected', 'IQC 明细审核驳回', 'quality', 'notification', 'biz', 'high',
       1, NULL, '入库单【{bizNo}】物料【{materialCode}】审核驳回',
       '物料 {materialName}（{materialCode}）的检验明细被驳回，请修改后重新提交。', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_event_config` WHERE `event_code` = 'quality.iqc.item.rejected');

INSERT INTO `sys_event_config`
    (`event_code`, `event_name`, `biz_module`, `event_type`, `kanban_module`, `priority`,
     `is_enabled`, `target_role`, `title`, `content`, `exclude_trigger`)
SELECT 'quality.iqc.reinspection.created', 'IQC 复检创建', 'quality', 'notification', 'biz', 'high',
       1, NULL, '入库单【{bizNo}】物料【{materialCode}】已发起复检',
       '物料 {materialName}（{materialCode}）已生成新的待检记录，请重新检验并提交。', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_event_config` WHERE `event_code` = 'quality.iqc.reinspection.created');
