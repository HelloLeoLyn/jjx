-- dev-20261117-002：独立加工处置事实，不创建虚假库存身份/空入库单。
-- 新表准入及兼容：jjx-docs/history/purchase-processing-disposition-dev-20261117-002.md
-- 数量/面积/成本真源快照仅存receipt_json；余额由事实聚合，不另存汇总。
CREATE TABLE IF NOT EXISTS purchase_processing_disposition (
    disposition_id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL COMMENT '来源采购订单',
    item_id BIGINT NOT NULL COMMENT '来源采购行',
    receipt_request_key VARCHAR(64) NOT NULL COMMENT '本次确认草稿键',
    request_marker VARCHAR(200) NOT NULL COMMENT '确认内容摘要；拒绝同键变更',
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(100) NOT NULL,
    inbound_id BIGINT NULL COMMENT '混合交货关联入库；仅报废为空',
    receipt_json JSON NOT NULL COMMENT '仅本次独立报废事实；正常交货不复制',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (disposition_id),
    UNIQUE KEY uk_processing_disposition_request (order_id,operator_id,receipt_request_key,item_id),
    KEY idx_processing_disposition_item (item_id,disposition_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='采购加工独立处置事实（只新增）';
