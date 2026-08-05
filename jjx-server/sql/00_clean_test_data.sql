-- =====================================================
-- 清理测试数据脚本（v3）
-- 只清理数据，不删除表结构
-- 按业务模块顺序清理，先清子表再清主表
--
-- v2 变更（2026-07-31）：
--   1. 保留 sales_customer（客户信息）
--   2. sys_task 改为条件删除：只清 office/emergency 演示任务，
--      保留 kanban_module='dev' 的开发任务（175 条）
--   3. 移除死表 kanban_task 的 TRUNCATE（表已废弃）
--
-- v3 变更（2026-08-05）：
--   1. 产品域不再清理：product / product_category / product_instance 等保留
--   2. 工程域不再清理：engineering_bom(_item) / engineering_routing(_item) / engineering_film 保留
--      标准工序（engineering_standard_process）继续保留
--   3. 如需单独清理产品脏数据（产品/BOM/路线/菲林），跑专用脚本：01_clean_product_test_data.sql
-- =====================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 1. 销售模块（保留 sales_customer） ====================
TRUNCATE sales_order_product;

TRUNCATE sales_order_review;

TRUNCATE sales_delivery;

TRUNCATE sales_invoice;

TRUNCATE sales_receipt;

TRUNCATE sales_return;

TRUNCATE sales_performance;

TRUNCATE sales_contract;

TRUNCATE sales_quotation;

TRUNCATE sales_quotation_item;

TRUNCATE sales_quotation_flow;

TRUNCATE sales_order;

TRUNCATE sales_inquiry;
-- sales_customer 保留（客户信息）

-- ==================== 2. 产品模块（v3 起整体保留，不清） ====================
-- 产品档案：product / product_category / product_instance / product_config_* 保留（v3）
-- 产品脏数据用专用脚本：01_clean_product_test_data.sql

-- ==================== 3. 采购模块 ====================
TRUNCATE purchase_material_inquiry;

TRUNCATE purchase_payment;

TRUNCATE purchase_document;

TRUNCATE purchase_order_item;

TRUNCATE purchase_order;

TRUNCATE purchase_supplier;

-- ==================== 4. 生产模块 ====================
TRUNCATE production_quality_inspection_item;

TRUNCATE production_quality_inspection;

TRUNCATE production_operation_record;

TRUNCATE production_operation_execution;

TRUNCATE production_trace_log;

TRUNCATE production_order;

TRUNCATE production_equipment;

-- ==================== 4.5 样品单子表（sales_order 已清，子表独立） ====================
TRUNCATE sales_sample_bom;

TRUNCATE sales_sample_process;

TRUNCATE sales_sample_round;

TRUNCATE sales_sample_transfer;

-- ==================== 5. 库存模块 ====================
TRUNCATE inventory_alert_log;

TRUNCATE inventory_transaction;

TRUNCATE inventory_transfer_item;

TRUNCATE inventory_transfer_order;

TRUNCATE inventory_outbound_item;

TRUNCATE inventory_outbound_order;

TRUNCATE inventory_inbound_item;

TRUNCATE inventory_inbound_order;

TRUNCATE inventory_stocktake_item;

TRUNCATE inventory_stocktake_order;

TRUNCATE inventory_stock_item;

TRUNCATE inventory_stock;

TRUNCATE inventory_storage_location;
-- 基础档案保留：inventory_material（物料）/ inventory_material_category（材料分类）/ inventory_warehouse（仓库）不清（2026-08-03 真实物料数据）

-- ==================== 6. 工程模块（v3 起保留产品关联档案，不清） ====================
-- engineering_bom(_item) / engineering_routing(_item) / engineering_film 保留（v3）
-- 工程脏数据用专用脚本：01_clean_product_test_data.sql
-- 基础档案保留：engineering_standard_process（标准工序）不清
TRUNCATE engineering_base;

-- ==================== 7. 门户相关 ====================
TRUNCATE portal_product_display;

TRUNCATE portal_page_content;

TRUNCATE portal_language_config;


-- ==================== 8. 任务表（保留开发任务） ====================
-- 只清 office/emergency 演示任务，保留 kanban_module='dev' 开发任务
DELETE FROM sys_task WHERE kanban_module IS NULL OR kanban_module != 'dev';

-- ==================== 9. 系统模块（可清理部分） ====================
TRUNCATE sys_attachment;

TRUNCATE sys_notification;



TRUNCATE sys_oper_log;

TRUNCATE sys_login_log;

TRUNCATE sys_error_log;

SET FOREIGN_KEY_CHECKS = 1;
