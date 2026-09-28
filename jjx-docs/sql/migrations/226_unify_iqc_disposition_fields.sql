-- risk: high
-- task: dev-20260928-021
-- 统一 IQC 处置事实表：扩展既有父表承载退货/返工/报废元数据；旧类型表保留为历史兼容读取，不再作为新写模型。

ALTER TABLE inventory_iqc_disposition_order
    ADD COLUMN material_id BIGINT NULL COMMENT '物料主键' AFTER lot_id,
    ADD COLUMN supplier_id BIGINT NULL COMMENT '供应商主键' AFTER material_id,
    ADD COLUMN supplier_name VARCHAR(128) NULL COMMENT '供应商名称' AFTER supplier_id,
    ADD COLUMN action_no VARCHAR(64) NULL COMMENT '处置动作业务编号' AFTER supplier_name,
    ADD COLUMN child_batch_id BIGINT NULL COMMENT '返工复检子批次主键' AFTER action_no,
    ADD COLUMN child_batch_no VARCHAR(128) NULL COMMENT '返工复检子批次号' AFTER child_batch_id,
    ADD COLUMN reason VARCHAR(500) NULL COMMENT '处置原因/说明' AFTER child_batch_no,
    ADD COLUMN applicant_id BIGINT NULL COMMENT '报废申请人' AFTER reason,
    ADD COLUMN applicant_name VARCHAR(64) NULL COMMENT '报废申请人名称' AFTER applicant_id,
    ADD COLUMN approver_id BIGINT NULL COMMENT '审批人' AFTER applicant_name,
    ADD COLUMN approver_name VARCHAR(64) NULL COMMENT '审批人名称' AFTER approver_id;

UPDATE inventory_iqc_disposition_order d
JOIN inventory_iqc_return_order r ON r.disposition_id = d.disposition_id
SET d.material_id = COALESCE(d.material_id, r.material_id),
    d.supplier_id = COALESCE(d.supplier_id, r.supplier_id),
    d.supplier_name = COALESCE(d.supplier_name, r.supplier_name),
    d.action_no = COALESCE(d.action_no, r.return_no),
    d.reason = COALESCE(d.reason, r.reason),
    d.status = COALESCE(r.status, d.status),
    d.operator_id = COALESCE(d.operator_id, r.operator_id),
    d.operator_name = COALESCE(d.operator_name, r.operator_name);

UPDATE inventory_iqc_disposition_order d
JOIN inventory_iqc_rework_order r ON r.disposition_id = d.disposition_id
SET d.material_id = COALESCE(d.material_id, r.material_id),
    d.supplier_id = COALESCE(d.supplier_id, r.supplier_id),
    d.supplier_name = COALESCE(d.supplier_name, r.supplier_name),
    d.action_no = COALESCE(d.action_no, r.rework_no),
    d.child_batch_id = COALESCE(d.child_batch_id, r.child_batch_id),
    d.child_batch_no = COALESCE(d.child_batch_no, r.child_batch_no),
    d.reason = COALESCE(d.reason, r.reason),
    d.status = COALESCE(r.status, d.status),
    d.operator_id = COALESCE(d.operator_id, r.operator_id),
    d.operator_name = COALESCE(d.operator_name, r.operator_name);

UPDATE inventory_iqc_disposition_order d
JOIN inventory_iqc_scrap_order s ON s.disposition_id = d.disposition_id
SET d.material_id = COALESCE(d.material_id, s.material_id),
    d.action_no = COALESCE(d.action_no, s.scrap_no),
    d.reason = COALESCE(d.reason, s.reason),
    d.status = COALESCE(s.status, d.status),
    d.applicant_id = COALESCE(d.applicant_id, s.applicant_id),
    d.applicant_name = COALESCE(d.applicant_name, s.applicant_name),
    d.approver_id = COALESCE(d.approver_id, s.approver_id),
    d.approver_name = COALESCE(d.approver_name, s.approver_name);

-- 兼容极早期只有类型表、没有父处置单的历史行：补建统一事实行；不删除、不覆盖原表。
INSERT INTO inventory_iqc_disposition_order
    (disposition_no, quarantine_id, inbound_id, inbound_item_id, inspection_id, lot_id,
     material_id, action_no, action, quantity, material_code, material_name, batch_no,
     iqc_batch_id, reason, status, operator_id, operator_name, applicant_id, applicant_name,
     approver_id, approver_name, create_time, update_time)
SELECT r.return_no, r.quarantine_id, r.inbound_id, r.inbound_item_id, r.inspection_id, r.lot_id,
       r.material_id, r.return_no, 'RETURN', r.quantity, r.material_code, r.material_name, r.batch_no,
       r.iqc_batch_id, r.reason, COALESCE(r.status, 'CREATED'), r.operator_id, r.operator_name,
       NULL, NULL, NULL, NULL, r.create_time, r.update_time
FROM inventory_iqc_return_order r
WHERE r.disposition_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM inventory_iqc_disposition_order d WHERE d.disposition_no = r.return_no);

INSERT INTO inventory_iqc_disposition_order
    (disposition_no, quarantine_id, inbound_id, inbound_item_id, inspection_id, lot_id,
     material_id, action_no, action, quantity, material_code, material_name, batch_no,
     iqc_batch_id, child_batch_id, child_batch_no, reason, status, operator_id, operator_name,
     create_time, update_time)
SELECT r.rework_no, r.quarantine_id, r.inbound_id, r.inbound_item_id, r.inspection_id, r.lot_id,
       r.material_id, r.rework_no, 'REWORK', r.quantity, r.material_code, r.material_name, r.batch_no,
       r.iqc_batch_id, r.child_batch_id, r.child_batch_no, r.reason, COALESCE(r.status, 'CREATED'),
       r.operator_id, r.operator_name, r.create_time, r.update_time
FROM inventory_iqc_rework_order r
WHERE r.disposition_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM inventory_iqc_disposition_order d WHERE d.disposition_no = r.rework_no);

INSERT INTO inventory_iqc_disposition_order
    (disposition_no, quarantine_id, inbound_id, inbound_item_id, inspection_id, lot_id,
     material_id, action_no, action, quantity, material_code, material_name, batch_no,
     iqc_batch_id, reason, status, applicant_id, applicant_name, approver_id, approver_name,
     create_time, update_time)
SELECT s.scrap_no, s.quarantine_id, s.inbound_id, s.inbound_item_id, s.inspection_id, s.lot_id,
       s.material_id, s.scrap_no, 'SCRAP', s.quantity, s.material_code, s.material_name, s.batch_no,
       s.iqc_batch_id, s.reason, COALESCE(s.status, 'PENDING_APPROVAL'), s.applicant_id, s.applicant_name,
       s.approver_id, s.approver_name, s.create_time, s.update_time
FROM inventory_iqc_scrap_order s
WHERE s.disposition_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM inventory_iqc_disposition_order d WHERE d.disposition_no = s.scrap_no);

UPDATE inventory_iqc_disposition_order
SET action_no = COALESCE(action_no, disposition_no),
    reason = COALESCE(reason, remark)
WHERE action_no IS NULL OR reason IS NULL;

CREATE INDEX idx_iqc_disposition_action_status
    ON inventory_iqc_disposition_order (action, status, inbound_id);
