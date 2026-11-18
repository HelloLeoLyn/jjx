-- 任务码：dev-20261118-007
-- 主Agent统一管理本次schema；快照冻结实际换算依据，不新增第二库存数量余额。
-- 历史JSON为空保持原行为；存量流水只增列，不UPDATE/DELETE任何流水行。
ALTER TABLE purchase_order_item ADD COLUMN measurement_snapshot JSON NULL COMMENT '本行双单位策略及实际换算快照';
ALTER TABLE inventory_inbound_item ADD COLUMN measurement_snapshot JSON NULL COMMENT '本次实收/余卷实际参数及换算快照';
ALTER TABLE inventory_outbound_item ADD COLUMN measurement_snapshot JSON NULL COMMENT '本次实发双数量及冻结依据';
ALTER TABLE inventory_transaction ADD COLUMN measurement_snapshot JSON NULL COMMENT '本笔收发双单位冻结依据';
ALTER TABLE inventory_stock_item ADD COLUMN measurement_snapshot JSON NULL COMMENT '本批实际换算参数缓存，数量仍从流水派生';
ALTER TABLE order_material_reserve
  ADD COLUMN stock_item_id BIGINT NULL COMMENT '明确占用的库存批次/卷记录，历史空值兼容',
  ADD COLUMN roll_no VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '预留卷号',
  ADD COLUMN demand_quantity DECIMAL(18,4) NULL COMMENT '本分配满足的需求数量，与整卷实际占用区分',
  ADD COLUMN consumed_quantity DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已由本订单领料消费的原分配数量',
  ADD INDEX idx_material_reserve_stock_status (stock_item_id,status),
  ADD INDEX idx_material_reserve_order_status (order_id,status);
SELECT table_name,column_name FROM information_schema.columns
WHERE table_schema=DATABASE() AND column_name IN ('measurement_snapshot','stock_item_id','roll_no','demand_quantity','consumed_quantity')
AND table_name IN ('purchase_order_item','inventory_inbound_item','inventory_outbound_item','inventory_transaction','inventory_stock_item','order_material_reserve');
