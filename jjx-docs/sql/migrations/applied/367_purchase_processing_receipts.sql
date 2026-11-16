-- risk: medium
-- dev-20261116-005：采购母卷加工要求/含税报价、实际切片余卷收货来源。
-- 用户已确认本次变更前手工备份；只加现有表从属快照字段，不新增表、不回填历史或改库存。
-- processing_plan为采购行原始方案，purchase_processing_json为入库明细原始收货事实；不是库存余额。
-- source_purchase_item_id -> purchase_order_item.item_id；历史NULL不猜来源。
-- amount始终未税净额；price_tax_inclusive只决定输入单价口径，报价行金额/税额由原数量价格重算，不另存。
-- 收货快照settledArea可由实际片/卷尺寸数量+明确损耗及末行尾差重算；netCost为母卷净额按累计核销面积分摊的凭证快照，最后吸收分币尾差。
-- 回滚：旧代码不支持加工收货，已产生加工凭证时禁止直接降级；回滚部署前核查相关凭证并按红冲流程处理，不删除原始记录。

SET @purchase_processing_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order_item' AND column_name='processing_plan'),'SELECT 1','ALTER TABLE purchase_order_item ADD COLUMN processing_plan json NULL COMMENT ''本采购行母卷分切切片余卷方案快照''');
PREPARE purchase_processing_stmt FROM @purchase_processing_ddl;
EXECUTE purchase_processing_stmt;
DEALLOCATE PREPARE purchase_processing_stmt;

SET @purchase_processing_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order_item' AND column_name='price_tax_inclusive'),'SELECT 1','ALTER TABLE purchase_order_item ADD COLUMN price_tax_inclusive tinyint(1) NULL DEFAULT 0 COMMENT ''输入单价含税；amount仍为未税净额''');
PREPARE purchase_processing_stmt FROM @purchase_processing_ddl;
EXECUTE purchase_processing_stmt;
DEALLOCATE PREPARE purchase_processing_stmt;

SET @purchase_processing_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_inbound_item' AND column_name='source_purchase_item_id'),'SELECT 1','ALTER TABLE inventory_inbound_item ADD COLUMN source_purchase_item_id bigint NULL COMMENT ''实际交付所属母卷或普通采购行ID''');
PREPARE purchase_processing_stmt FROM @purchase_processing_ddl;
EXECUTE purchase_processing_stmt;
DEALLOCATE PREPARE purchase_processing_stmt;

SET @purchase_processing_ddl=IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_inbound_item' AND column_name='purchase_processing_json'),'SELECT 1','ALTER TABLE inventory_inbound_item ADD COLUMN purchase_processing_json json NULL COMMENT ''实际加工交付原始数量尺寸损耗及母卷分摊快照''');
PREPARE purchase_processing_stmt FROM @purchase_processing_ddl;
EXECUTE purchase_processing_stmt;
DEALLOCATE PREPARE purchase_processing_stmt;

SET @purchase_processing_ddl=IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='inventory_inbound_item' AND index_name='idx_inbound_source_purchase'),'SELECT 1','ALTER TABLE inventory_inbound_item ADD INDEX idx_inbound_source_purchase(source_purchase_item_id)');
PREPARE purchase_processing_stmt FROM @purchase_processing_ddl;
EXECUTE purchase_processing_stmt;
DEALLOCATE PREPARE purchase_processing_stmt;
