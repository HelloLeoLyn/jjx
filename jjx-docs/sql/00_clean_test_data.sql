-- =====================================================
-- 清理测试数据脚本（v9）
-- 只清理数据，不删除表结构
-- 按业务模块顺序清理，先清子表再清主表
--
-- v9 变更（2026-08-25）：
--   1. 适配统一生产任务模型，新增 production_work_report、
--      production_task_event、production_task 清理（先引用表，后任务主表）
--   2. 旧派工表 production_dispatch / production_dispatch_log 已下线并移除
--   3. 补充库存业务表 order_material_reserve / product_stock 清理
--
-- v5 变更（2026-08-10）：
--   1. 标准工序（engineering_standard_process）保留不清——基础档案，供打样/工艺路线复用
--   2. 其余同 v4：基础档案（客户/物料/产品/BOM/路线）全清
--
-- v7 变更（2026-08-13）：
--   1. 基础档案保留范围扩大：sales_customer（客户）、inventory_material（物料）+
--      inventory_material_category（物料分类）、purchase_supplier（供应商）不再清理
--   2. 其余同 v6
--
-- v8 变更（2026-08-18）：
--   1. inventory_warehouse（仓库）改为保留——基础档案（与物料/客户/供应商同级），
--      此前清理后发货/领料创建出库单时查不到默认仓库，warehouse_id NOT NULL 报 SQL 裸错
--   2. 其余同 v7
--
-- v6 变更（2026-08-12）：
--   1. 新增派工模块清理：production_dispatch_log / production_dispatch（先子后主）
--   2. 工装模具档案 production_tooling 保留不清（用户明确要求）
--
-- v4 变更（2026-08-06）：
--   1. 基础档案也全清：sales_customer（客户）、inventory_material（物料）+
--      分类 + inventory_warehouse（仓库）、product（产品）、
--      engineering_bom(_item)/routing(_item)/standard_process（BOM/路线/标准工序）
--   2. 保留范围收窄为三类：
--      a) 系统权限模块：sys_user / sys_role / sys_menu / sys_role_menu /
--         sys_user_role / sys_dept
--      b) 系统配置：sys_config / sys_dict / sys_dict_item / sys_event_config
--      c) 任务模块：sys_task（保留 kanban_module='dev' 开发任务）
--   3. 其他所有业务数据（销售/采购/生产/库存/工程/门户/日志/通知/附件）全清
--
-- v3 变更（2026-08-05）：
--   1. 产品域不再清理：product / product_category / product_instance 等保留
--   2. 工程域不再清理：engineering_bom(_item) / engineering_routing(_item) / engineering_film 保留
--      标准工序（engineering_standard_process）继续保留
--   3. 如需单独清理产品脏数据（产品/BOM/路线/菲林），跑专用脚本：01_clean_product_test_data.sql
--
-- v2 变更（2026-07-31）：
--   1. 保留 sales_customer（客户信息）
--   2. sys_task 改为条件删除：只清 office/emergency 演示任务，
--      保留 kanban_module='dev' 的开发任务（175 条）
--   3. 移除死表 kanban_task 的 TRUNCATE（表已废弃）
-- =====================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 1. 销售模块（v4 起 sales_customer 也清） ====================
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

TRUNCATE sales_order_stock_reserve;

TRUNCATE sales_inquiry;

-- v7：客户保留不清（基础档案）
-- TRUNCATE sales_customer;

-- 样品单子表（sales_order 已清，子表独立）
TRUNCATE sales_sample_bom;

TRUNCATE sales_sample_process;

TRUNCATE sales_sample_round;

TRUNCATE sales_sample_transfer;

-- ==================== 2. 产品模块（v4 起全清） ====================
TRUNCATE product_instance;

TRUNCATE product_config_option;

TRUNCATE product_config_model;

TRUNCATE product_category;

TRUNCATE product;

-- 08-09 版本化改造前的数据备份表（无代码引用，历史脏数据）
TRUNCATE product_backup_20260809;

-- ==================== 3. 工程模块（v5 起标准工序保留，其余清） ====================
TRUNCATE engineering_routing_item;

TRUNCATE engineering_routing;

TRUNCATE engineering_bom_item;

TRUNCATE engineering_bom;

-- v5：标准工序保留不清（基础档案）
-- TRUNCATE engineering_standard_process;

-- 08-09 版本化改造前的数据备份表（无代码引用，历史脏数据）
TRUNCATE engineering_bom_backup_20260809;

-- TRUNCATE engineering_routing_backup_20260809; -- 表已不存在（2026-08-13 清理时发现）

TRUNCATE engineering_film;

TRUNCATE engineering_base;

-- ==================== 4. 采购模块 ====================
TRUNCATE purchase_material_inquiry;

TRUNCATE purchase_payment;

TRUNCATE purchase_document;

TRUNCATE purchase_order_item;

TRUNCATE purchase_order;

-- v7：供应商保留不清（基础档案）
-- TRUNCATE purchase_supplier;

-- ==================== 5. 生产模块 ====================
TRUNCATE production_quality_inspection_item;

TRUNCATE production_quality_inspection;

-- 报工和任务流水均引用统一生产任务，必须先于 production_task 清理
TRUNCATE production_work_report;

TRUNCATE production_task_event;

TRUNCATE production_task;

TRUNCATE production_operation_record;

TRUNCATE production_operation_execution;

TRUNCATE production_trace_log;

TRUNCATE production_order;

TRUNCATE production_equipment;

-- 工装模具档案 production_tooling 保留不清

-- ==================== 6. 库存模块（v4 起物料/仓库也清） ====================
TRUNCATE inventory_alert_log;

TRUNCATE order_material_reserve;

TRUNCATE product_stock;

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

-- v8：仓库保留不清（基础档案，出库单创建依赖默认仓库）
-- TRUNCATE inventory_warehouse;

-- v7：物料及分类保留不清（基础档案）
-- TRUNCATE inventory_material_category;
-- TRUNCATE inventory_material;

-- v8：仓库保留不清（基础档案，出库单创建依赖默认仓库）
-- TRUNCATE inventory_warehouse;

-- ==================== 7. 门户相关 ====================
TRUNCATE portal_product_display;

TRUNCATE portal_page_content;

TRUNCATE portal_language_config;

-- ==================== 8. 任务模块（保留开发任务） ====================
-- 只清 office/emergency 演示任务，保留 kanban_module='dev' 开发任务
DELETE FROM sys_task WHERE kanban_module IS NULL OR kanban_module != 'dev';

-- ==================== 9. 系统模块（可清理部分：日志/通知/附件） ====================
TRUNCATE sys_attachment;

TRUNCATE sys_notification;

TRUNCATE sys_oper_log;

TRUNCATE sys_login_log;

TRUNCATE sys_error_log;

-- ==================== 10. 系统权限模块 + 系统配置（全部保留，不动） ====================
-- 权限：sys_user / sys_role / sys_menu / sys_role_menu / sys_user_role / sys_dept
-- 配置：sys_config / sys_dict / sys_dict_item / sys_event_config
-- 以上保留

SET FOREIGN_KEY_CHECKS = 1;
