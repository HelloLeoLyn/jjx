-- =====================================================
-- 清理产品脏数据脚本（专用，2026-08-05 新增）
-- 只清产品域：产品档案 + BOM + 工艺路线 + 菲林 + 产品配置
-- 不清业务单据（订单/报价/采购/生产/库存 → 用 00_clean_test_data.sql）
--
-- 用途：产品档案（含 BOM/路线/菲林）数据造脏了时单独重来，
--       不影响业务单据和客户/物料/标准工序等基础档案。
--
-- ⚠️ 注意：product 删除后，引用 product_id 的业务数据
--   （报价明细/订单明细/样品单等）会成为孤儿数据；
--   如需连业务单据一起清，先跑 00_clean_test_data.sql 再跑本脚本。
-- =====================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 1. 工程域（产品关联档案） ====================
-- BOM 明细/主表
TRUNCATE engineering_bom_item;
TRUNCATE engineering_bom;

-- 工艺路线明细/主表
TRUNCATE engineering_routing_item;
TRUNCATE engineering_routing;

-- 菲林图纸（产品资料转移产物）
TRUNCATE engineering_film;

-- ==================== 2. 产品域 ====================
-- 产品实例/配置
TRUNCATE product_instance;
TRUNCATE product_config_option;
TRUNCATE product_config_model;

-- 产品主表/分类
TRUNCATE product;
TRUNCATE product_category;

SET FOREIGN_KEY_CHECKS = 1;
