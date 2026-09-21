-- ============================================================================
-- 147_iqc_rework_accept_repair_po202609210001_3.sql
-- 任务码：dev-20260921-004
--
-- 目的：修回 PO202609210001-3（inventory_inbound_order.inbound_id=4，批次2）
--       来料检验→入库过程中的数量错误：95 件良品被当成隔离、库存少入 95。
--
-- 背景（实测证据，2026-09-21）：
--   11:43:16 提交检验 → item6(RM001585,收货100) 判 FAIL 100/95/5、处置 PARTIAL_ACCEPT
--   11:43:23 全部审核通过 → 隔离 5 件（正确）+ IQC_QUARANTINE +5 流水
--   11:43:34 处置 REWORK 5 件 → 返工单 IQW20260921114334147270 → 11:44:01 复检子单 inspection 8(5 件)
--            reinspectItem/返工完成把 item6 原地改写成子批次：batch_no=RW-IQW…、sampled=5、inspection_id=8，
--            但 quantity 仍 100
--   11:44:27 整单二次「提交入库审批」→ saveInspection 对子单落库 → item6.accepted_quantity=5
--   11:44:44 子单审核通过 → createIqcQuarantine 用 quantity-accepted = 100-5 = 95 误建隔离台账
--   11:45:03 确认入库 → addStock 按 accepted 过账 → RM001585 只入账 5（实物 100 可用）
--   对账：RM001585 汇总 505（=300+200+5），少 95；那 95 挂在 quarantine_id=2（PENDING）里
--
-- 收口口径（镜像系统自身链路，不发明新形态）：
--   误隔离的 95 件实为原批一次检验合格品 → 按 handleQuarantine 的 RELEASE 口径释放回良品：
--     隔离行 RELEASED/remaining=0 + RELEASE 处置单 + IQC_RELEASE 流水 + 库存加回 + 批次账目修正
--   明细行允收量改回真实值 100（95 一次合格 + 5 返工复检合格）
--
-- 幂等：所有语句都带「改前值」守卫（accepted=5 / status=PENDING / quantity=5 / quantity=100 …），
--       重复执行不产生第二次影响；流水与处置单用 NOT EXISTS 去重。
-- ============================================================================

-- 1) 明细行：允收/合格/已过账改回真实值；清掉被前端提示语污染的不合格原因
UPDATE inventory_inbound_item
   SET qualified_quantity = 100,
       accepted_quantity = 100,
       posted_quantity = 100,
       reject_reason = NULL
 WHERE item_id = 6 AND accepted_quantity = 5;

-- 2) 误建的隔离台账：按「已释放」收口
UPDATE inventory_iqc_quarantine
   SET remaining_quantity = 0,
       status = 'RELEASED',
       update_time = NOW()
 WHERE quarantine_id = 2 AND status = 'PENDING';

-- 2b) 处置单（形状对齐 handleQuarantine 的 RELEASE 分支）
INSERT INTO inventory_iqc_disposition_order
  (disposition_no, quarantine_id, inbound_id, inbound_item_id, inspection_id, lot_id, action, quantity,
   material_code, material_name, batch_no, iqc_batch_id, remark, status, operator_id, operator_name,
   create_time, update_time)
SELECT 'IQD20260921RERELEASE095', q.quarantine_id, q.inbound_id, q.inbound_item_id, q.inspection_id, q.lot_id,
       'RELEASE', 95, q.material_code, q.material_name, q.batch_no, q.iqc_batch_id,
       '数据收口 dev-20260921-004：该 95 件为原批一次检验合格品，因复检覆盖 accepted 被误算成隔离，按释放回良品处理',
       'COMPLETED', 148, '刘三元', NOW(), NOW()
  FROM inventory_iqc_quarantine q
 WHERE q.quarantine_id = 2
   AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM inventory_iqc_disposition_order) d
                    WHERE d.quarantine_id = 2 AND d.action = 'RELEASE');

-- 3) 批次库存：RW 批次 5 → 100（镜像 addReleasedQuarantineStock：同物料+同批次行加数量）
UPDATE inventory_stock_item
   SET quantity = quantity + 95,
       last_inbound_time = NOW(),
       update_time = NOW()
 WHERE item_id = 6
   AND batch_no = 'RW-IQW20260921114334147270'
   AND quantity = 5;

-- 3b) 库存汇总重算（= 该库存物品所有启用批次之和）
UPDATE inventory_stock s
   SET s.total_quantity = (SELECT COALESCE(SUM(i.quantity), 0)
                             FROM inventory_stock_item i
                            WHERE i.inventory_item_id = s.inventory_item_id
                              AND i.status = 1),
       s.last_update_time = NOW()
 WHERE s.inventory_item_id = 2060;

-- 4) 流水：补「隔离释放」与「入库补记」两条（append-only，不改历史流水）
INSERT INTO inventory_transaction
  (inventory_item_id, material_id, material_code, material_name, warehouse_id, batch_no, iqc_batch_id,
   transaction_type, source_type, source_id, quantity, before_quantity, after_quantity, unit_cost, amount,
   transaction_time, operator_id, operator_name, remark, create_time)
SELECT 2060, 1585, 'RM001585', 'DG68 样品 (新亚洲)', 1, 'RW-IQW20260921114334147270', 12,
       'IQC_RELEASE', 'INBOUND_IQC', 4, 95, 95, 0, 5.0000, 475.00,
       NOW(), 148, '刘三元', '数据收口 dev-20260921-004：误隔离的 95 件释放回良品', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT * FROM inventory_transaction) t
                    WHERE t.source_id = 4 AND t.transaction_type = 'IQC_RELEASE' AND t.quantity = 95);

INSERT INTO inventory_transaction
  (inventory_item_id, material_id, material_code, material_name, warehouse_id, batch_no, iqc_batch_id,
   transaction_type, source_type, source_no, quantity, before_quantity, after_quantity, unit_cost, amount,
   transaction_time, operator_id, operator_name, remark, create_time)
SELECT 2060, 1585, 'RM001585', 'DG68 样品 (新亚洲)', 1, 'RW-IQW20260921114334147270', 12,
       'INBOUND', 'PURCHASE', 'PO202609210001-3', 95, 505, 600, 5.0000, 475.00,
       NOW(), 148, '刘三元', '数据收口 dev-20260921-004：确认入库少记的 95 件补记（复检允收修正）', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT * FROM inventory_transaction) t
                    WHERE t.source_no = 'PO202609210001-3' AND t.transaction_type = 'INBOUND' AND t.quantity = 95);

-- 5) IQC 批次账目：子批次身份与数量修正（复检 5 件），原批允收 95
UPDATE inventory_iqc_batch
   SET parent_batch_id = 10,
       parent_batch_no = 'PO202609210001-3-1',
       root_batch_no = 'PO202609210001-3-1',
       batch_type = 'REWORK',
       quantity = 5,
       processed_quantity = 5,
       accepted_quantity = 5,
       rejected_quantity = 0,
       remaining_quantity = 0,
       status = 'QUALIFIED',
       update_time = NOW()
 WHERE batch_id = 12 AND quantity = 100;

UPDATE inventory_iqc_batch
   SET accepted_quantity = 95,
       remaining_quantity = 0,
       update_time = NOW()
 WHERE batch_id = 10 AND accepted_quantity = 0;

-- 6) 质量批次：原批真实检验结果（检 100、净合格 100；5 件不良与返工事实在 inspection 6 / 8 与返工单上）
UPDATE quality_lot
   SET inspected_quantity = 100,
       pass_quantity = 100,
       fail_quantity = 0,
       update_time = NOW()
 WHERE lot_id = 6 AND inspected_quantity = 5;

-- 7) 清除「实测记录可留空」提示语污染（前端默认值写进不合格描述）
UPDATE production_quality_inspection
   SET defect_desc = NULL,
       update_time = NOW()
 WHERE inspection_id IN (6, 8) AND defect_desc = '实测记录可留空';

-- ============================================================================
-- 执行后自检：
--   SELECT item_id,quantity,qualified_quantity,accepted_quantity,posted_quantity,reject_reason
--     FROM inventory_inbound_item WHERE item_id = 6;
--   SELECT quarantine_id,quantity,remaining_quantity,status FROM inventory_iqc_quarantine WHERE quarantine_id = 2;
--   SELECT item_id,batch_no,quantity FROM inventory_stock_item WHERE item_id = 6;
--   SELECT inventory_item_id,total_quantity,total_reserved,available_quantity FROM inventory_stock WHERE inventory_item_id = 2060;
--   SELECT transaction_id,transaction_type,batch_no,quantity,before_quantity,after_quantity FROM inventory_transaction WHERE transaction_id >= 8;
--   SELECT batch_id,parent_batch_id,batch_type,quantity,accepted_quantity,remaining_quantity,status
--     FROM inventory_iqc_batch WHERE batch_id IN (10,12);
--   SELECT lot_id,inspected_quantity,pass_quantity,fail_quantity FROM quality_lot WHERE lot_id = 6;
-- ============================================================================
