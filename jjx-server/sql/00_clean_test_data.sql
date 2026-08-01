-- =====================================================
-- 清理测试数据脚本（v2）
-- 只清理数据，不删除表结构
-- 按业务模块顺序清理，先清子表再清主表
--
-- v2 变更（2026-07-31）：
--   1. 保留 sales_customer（客户信息）
--   2. sys_task 改为条件删除：只清 office/emergency 演示任务，
--      保留 kanban_module='dev' 的开发任务（175 条）
--   3. 移除死表 kanban_task 的 TRUNCATE（表已废弃）
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

TRUNCATE sales_order;

TRUNCATE sales_inquiry;
-- sales_customer 保留（客户信息）

-- ==================== 2. 产品模块 ====================
TRUNCATE product_instance;

TRUNCATE product_config_option;

TRUNCATE product_config_model;

TRUNCATE product;

TRUNCATE product_category;

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

TRUNCATE inventory_material;

TRUNCATE inventory_material_category;

TRUNCATE inventory_warehouse;

-- ==================== 6. 工程模块 ====================
TRUNCATE engineering_bom_item;

TRUNCATE engineering_routing_item;
-- engineering_design_task 已迁移为 sys_task，不再存在
TRUNCATE engineering_film;

TRUNCATE engineering_routing;

TRUNCATE engineering_standard_process;

TRUNCATE engineering_bom;

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
