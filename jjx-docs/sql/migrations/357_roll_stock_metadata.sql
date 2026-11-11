-- dev-20261110-013：卷材逐卷收发退，扩展既有四表，不建新表。
-- 正式编号357：主会话已协调（2026-11-11），写前核对目标账本/两目录最大356。risk: high（唯一索引变更）
-- 执行前必须取得用户目标库人工全库备份证据；不清理旧身份，不回填历史流水。
-- 面积㎡是唯一余额，余长=quantity*1000/roll_width_mm，不落余长余额。
-- 回退：本代码/结构配套；有卷材流水后不可删列，须先停用逐卷入口并按凭证红冲。

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_stock_item' AND COLUMN_NAME='roll_no');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_stock_item ADD COLUMN roll_no varchar(64) NULL COMMENT ''物理卷号，独立于检验批次''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_stock_item' AND COLUMN_NAME='roll_width_mm');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_stock_item ADD COLUMN roll_width_mm decimal(18,4) NULL COMMENT ''原始卷宽mm''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_stock_item' AND COLUMN_NAME='original_roll_length_m');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_stock_item ADD COLUMN original_roll_length_m decimal(18,4) NULL COMMENT ''首次实收原始卷长m，退回不改写''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_stock_item' AND COLUMN_NAME='source_inbound_item_id');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_stock_item ADD COLUMN source_inbound_item_id bigint NULL COMMENT ''原始入库明细，跨库位保持''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_stock_item' AND COLUMN_NAME='roll_scope');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_stock_item ADD COLUMN roll_scope varchar(64) GENERATED ALWAYS AS (COALESCE(roll_no, '''')) STORED COMMENT ''同批多卷唯一索引辅助''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_stock_item' AND COLUMN_NAME='active_roll_key');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_stock_item ADD COLUMN active_roll_key varchar(64) GENERATED ALWAYS AS (IF(roll_no IS NOT NULL AND quantity > 0, roll_no, NULL)) STORED COMMENT ''同物理卷至多一处正余额''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_transaction' AND COLUMN_NAME='stock_item_id');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_transaction ADD COLUMN stock_item_id bigint NULL COMMENT ''实际变动库存行，历史流水不补写''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_transaction' AND COLUMN_NAME='source_item_id');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_transaction ADD COLUMN source_item_id bigint NULL COMMENT ''当前入出库明细ID''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_transaction' AND COLUMN_NAME='roll_no');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_transaction ADD COLUMN roll_no varchar(64) NULL COMMENT ''物理卷号''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_item' AND COLUMN_NAME='stock_item_id');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_inbound_item ADD COLUMN stock_item_id bigint NULL', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_item' AND COLUMN_NAME='roll_no');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_inbound_item ADD COLUMN roll_no varchar(64) NULL', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_item' AND COLUMN_NAME='roll_width_mm');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_inbound_item ADD COLUMN roll_width_mm decimal(18,4) NULL', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_item' AND COLUMN_NAME='roll_length_m');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_inbound_item ADD COLUMN roll_length_m decimal(18,4) NULL COMMENT ''本次实收卷长m，红冲为负''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_item' AND COLUMN_NAME='source_outbound_item_id');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_inbound_item ADD COLUMN source_outbound_item_id bigint NULL COMMENT ''余卷退料原发料明细''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_item' AND COLUMN_NAME='reversal_of_item_id');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_inbound_item ADD COLUMN reversal_of_item_id bigint NULL COMMENT ''红冲对应的原入库明细''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_outbound_item' AND COLUMN_NAME='stock_item_id');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_outbound_item ADD COLUMN stock_item_id bigint NULL', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_outbound_item' AND COLUMN_NAME='roll_no');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_outbound_item ADD COLUMN roll_no varchar(64) NULL', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_outbound_item' AND COLUMN_NAME='reversal_of_item_id');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_outbound_item ADD COLUMN reversal_of_item_id bigint NULL COMMENT ''红冲对应的原出库明细''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_stock_item' AND INDEX_NAME='idx_stock_roll');
SET @ddl = IF(@i=0,'ALTER TABLE inventory_stock_item ADD KEY idx_stock_roll (roll_no)','SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_stock_item' AND INDEX_NAME='uk_active_roll');
SET @ddl = IF(@i=0,'ALTER TABLE inventory_stock_item ADD UNIQUE KEY uk_active_roll (active_roll_key)','SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_transaction' AND INDEX_NAME='idx_transaction_stock_roll');
SET @ddl = IF(@i=0,'ALTER TABLE inventory_transaction ADD KEY idx_transaction_stock_roll (stock_item_id,roll_no)','SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_transaction' AND INDEX_NAME='idx_transaction_roll');
SET @ddl = IF(@i=0,'ALTER TABLE inventory_transaction ADD KEY idx_transaction_roll (roll_no,transaction_id)','SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_item' AND INDEX_NAME='idx_inbound_roll_return');
SET @ddl = IF(@i=0,'ALTER TABLE inventory_inbound_item ADD KEY idx_inbound_roll_return (source_outbound_item_id)','SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_item' AND INDEX_NAME='idx_inbound_roll_reversal');
SET @ddl = IF(@i=0,'ALTER TABLE inventory_inbound_item ADD KEY idx_inbound_roll_reversal (reversal_of_item_id)','SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_outbound_item' AND INDEX_NAME='idx_outbound_roll_reversal');
SET @ddl = IF(@i=0,'ALTER TABLE inventory_outbound_item ADD KEY idx_outbound_roll_reversal (reversal_of_item_id)','SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 唯一索引原子替换；非卷材仍按原位置+批次，逐卷增加独立roll_scope。
SET @new_i=(SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_stock_item' AND INDEX_NAME='uk_item_position_batch_roll');
SET @old_i=(SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_stock_item' AND INDEX_NAME='uk_item_warehouse_location_batch');
SET @ddl=IF(@new_i>0,'SELECT 1',IF(@old_i>0,
 'ALTER TABLE inventory_stock_item DROP INDEX uk_item_warehouse_location_batch, ADD UNIQUE KEY uk_item_position_batch_roll (inventory_item_id,warehouse_id,location_id,batch_no,roll_scope)',
 'ALTER TABLE inventory_stock_item ADD UNIQUE KEY uk_item_position_batch_roll (inventory_item_id,warehouse_id,location_id,batch_no,roll_scope)'));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 原采购收货明细保留原quantity/IQC来源；允许入库的物理卷计划只保存原始尺寸。
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_inbound_item' AND COLUMN_NAME='roll_plan_json');
SET @ddl = IF(@c=0, 'ALTER TABLE inventory_inbound_item ADD COLUMN roll_plan_json json NULL COMMENT ''原采购明细多卷计划，过账后冻结''', 'SELECT 1');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
