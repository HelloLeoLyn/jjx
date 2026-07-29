-- =====================================================
-- 清理测试数据脚本
-- 只清理数据，不删除表结构
-- 按业务模块顺序清理，先清子表再清主表
-- =====================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 1. 销售模块 ====================
TRUNCATE sales_order_product;
TRUNCATE sales_order_review;
TRUNCATE sales_log;
TRUNCATE sales_delivery;
TRUNCATE sales_invoice;
TRUNCATE sales_receipt;
TRUNCATE sales_return;
TRUNCATE sales_performance;
TRUNCATE sales_contract;
TRUNCATE sales_quotation;
TRUNCATE sales_order;
TRUNCATE sales_customer;

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
TRUNCATE engineering_design_task;
TRUNCATE engineering_film;
TRUNCATE engineering_routing;
TRUNCATE engineering_standard_process;
TRUNCATE engineering_bom;
TRUNCATE engineering_base;

-- ==================== 7. 门户相关 ====================
TRUNCATE portal_product_display;
TRUNCATE portal_page_content;
TRUNCATE portal_language_config;
TRUNCATE portal_inquiry;

-- ==================== 8. 看板 ====================
TRUNCATE kanban_task;

-- ==================== 9. 系统模块（可清理部分） ====================
TRUNCATE sys_attachment;
TRUNCATE sys_task;
TRUNCATE sys_notification;
TRUNCATE sys_event_notification;
TRUNCATE sys_event_kanban;
TRUNCATE sys_oper_log;
TRUNCATE sys_login_log;
TRUNCATE sys_error_log;

SET FOREIGN_KEY_CHECKS = 1;
