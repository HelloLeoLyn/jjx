-- =====================================================
-- 清理测试数据脚本
-- 只清理数据，不删除表结构
-- 按业务模块顺序清理，先清子表再清主表
-- =====================================================

-- 要保留的系统数据表（保留全部数据）：
-- sys_user, sys_user_role, sys_role, sys_role_menu, sys_menu
-- sys_dept, sys_dict, sys_dict_item, sys_config
-- sys_notification_template, sys_event_config

-- ==================== 1. 销售模块 ====================
TRUNCATE sales_order_status_log;
TRUNCATE sales_order_review;
TRUNCATE sales_log;
TRUNCATE sales_delivery;
TRUNCATE sales_invoice;
TRUNCATE sales_receipt;
TRUNCATE sales_quotation_item;
TRUNCATE sales_quotation;
TRUNCATE sales_order_item;
TRUNCATE sales_order;
TRUNCATE sales_customer;

-- ==================== 2. 产品模块 ====================
TRUNCATE product_instance;
TRUNCATE product_bom_item;
TRUNCATE product_bom;
TRUNCATE product_routing_item;
TRUNCATE product_routing;
TRUNCATE product_config;
TRUNCATE product_film;
TRUNCATE product_standard_process;
TRUNCATE product_product;
TRUNCATE product_category;

-- ==================== 3. 采购模块 ====================
TRUNCATE purchase_invoice;
TRUNCATE purchase_invoice_file;
TRUNCATE purchase_payment;
TRUNCATE purchase_receipt;
TRUNCATE purchase_order_item;
TRUNCATE purchase_order;
TRUNCATE purchase_supplier;

-- ==================== 4. 生产模块 ====================
TRUNCATE production_quality_inspection;
TRUNCATE production_operation_record;
TRUNCATE production_operation_execution;
TRUNCATE production_order;
TRUNCATE production_cost;
TRUNCATE production_equipment;

-- ==================== 5. 库存模块 ====================
TRUNCATE inventory_alert;
TRUNCATE inventory_transaction;
TRUNCATE inventory_transfer;
TRUNCATE inventory_outbound;
TRUNCATE inventory_inbound;
TRUNCATE inventory_stocktake;
TRUNCATE inventory_stock_item;
TRUNCATE inventory_stock;
TRUNCATE inventory_storage_location;
TRUNCATE inventory_material;
TRUNCATE inventory_material_category;
TRUNCATE inventory_warehouse;

-- ==================== 6. 工程模块 ====================
TRUNCATE engineering_config;
TRUNCATE engineering_film;
TRUNCATE engineering_routing;
TRUNCATE engineering_standard_process;
TRUNCATE engineering_bom;
TRUNCATE engineering;

-- ==================== 7. 看板/通知/日志（系统模块可清理部分） ====================
TRUNCATE kanban_task;
TRUNCATE sys_attachment;
TRUNCATE sys_task;
TRUNCATE sys_notification;
TRUNCATE sys_event_notification;
TRUNCATE sys_event_kanban;
TRUNCATE sys_oper_log;
TRUNCATE sys_login_log;
TRUNCATE sys_error_log;
