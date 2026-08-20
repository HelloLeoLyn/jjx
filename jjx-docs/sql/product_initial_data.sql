
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product` (
  `product_id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品ID',
  `product_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品编码',
  `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品名称',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `customer_id` bigint DEFAULT NULL COMMENT '客户ID（sales_customer.customer_id）',
  `customer_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户名称（冗余）',
  `product_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'standard' COMMENT '类型：standard标准/custom定制',
  `spec_json` json DEFAULT NULL COMMENT '规格参数',
  `base_price` decimal(12,2) DEFAULT NULL COMMENT '基础售价',
  `cost_price` decimal(12,2) DEFAULT NULL COMMENT '标准成本',
  `min_order_qty` int DEFAULT '1' COMMENT '最小起订量',
  `lead_time` int DEFAULT '15' COMMENT '标准交期(天)',
  `product_status` bigint NOT NULL DEFAULT '1' COMMENT '产品状态: 1开发中,2待审核,3审核中,4已通过,5已驳回,6已发布,7停产,8取消',
  `from_source` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源标记（inquiry询价建档/quotation报价建档，草稿清理用）',
  `current_bom_id` bigint DEFAULT NULL COMMENT '当前BOM ID',
  `current_route_id` bigint DEFAULT NULL COMMENT '当前工艺路线ID',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位',
  `approve_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '审核批注',
  `current_bom_version` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'V1.0' COMMENT '当前BOM版本号',
  `current_routing_version` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'V1.0' COMMENT '当前Routing版本号',
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `uk_product_code` (`product_code`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_product_status` (`product_status`),
  KEY `idx_product_name` (`product_name`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品主表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product` WRITE;
/*!40000 ALTER TABLE `product` DISABLE KEYS */;
INSERT INTO `product` VALUES (1,'JST001POOO','JST001POOO',1,1,'江苏盛泰科技有限公司','1','{\"unit\": \"mm\", \"width\": 0, \"height\": 0, \"length\": 0, \"ipGrade\": \"\", \"keyCount\": 0, \"hasBacklight\": false}',NULL,NULL,1000,15,6,NULL,1,1,'admin','2026-08-20 11:01:57','admin','2026-08-20 11:02:11','','PCS','','V1.0','V1.0');
/*!40000 ALTER TABLE `product` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `product_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_category` (
  `category_id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `category_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编码',
  `category_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID',
  `category_level` int DEFAULT '1' COMMENT '层级（1/2/3）',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_category_code` (`category_code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product_category` WRITE;
/*!40000 ALTER TABLE `product_category` DISABLE KEYS */;
INSERT INTO `product_category` VALUES (1,'JST','JST',0,1,0,'0','admin','2026-08-20 11:01:00','admin','2026-08-20 11:01:00','JST'),(2,'JTT','JTT',0,1,0,'0','admin','2026-08-20 11:01:10','admin','2026-08-20 11:01:10','JTT'),(3,'AD','AD',0,1,0,'0','admin','2026-08-20 11:01:15','admin','2026-08-20 11:01:15','AD');
/*!40000 ALTER TABLE `product_category` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `product_config_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_config_model` (
  `model_id` bigint NOT NULL AUTO_INCREMENT COMMENT '模型ID',
  `model_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型编码',
  `model_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `product_id` bigint NOT NULL COMMENT '产品ID',
  `is_default` tinyint(1) DEFAULT '0' COMMENT '是否默认模型',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 1激活/0未激活',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`model_id`),
  UNIQUE KEY `uk_model_code` (`model_code`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品配置模型表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product_config_model` WRITE;
/*!40000 ALTER TABLE `product_config_model` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_config_model` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `product_config_option`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_config_option` (
  `option_id` bigint NOT NULL AUTO_INCREMENT COMMENT '选项ID',
  `model_id` bigint NOT NULL COMMENT '模型ID',
  `option_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项编码',
  `option_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项名称',
  `option_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '类型：material/color/circuit/size',
  `value_json` json NOT NULL COMMENT '选项值及价格影响',
  `depends_on` json DEFAULT NULL COMMENT '依赖选项',
  `excludes` json DEFAULT NULL COMMENT '互斥选项',
  `is_required` tinyint(1) DEFAULT '0' COMMENT '是否必选',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`option_id`),
  KEY `idx_model_id` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品配置选项表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product_config_option` WRITE;
/*!40000 ALTER TABLE `product_config_option` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_config_option` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `product_instance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_instance` (
  `instance_id` bigint NOT NULL AUTO_INCREMENT COMMENT '实例ID',
  `instance_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '实例编码',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_item_id` bigint DEFAULT NULL COMMENT '订单明细ID',
  `product_id` bigint NOT NULL COMMENT '产品ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户名称',
  `config_snapshot` json DEFAULT NULL COMMENT '配置快照',
  `bom_snapshot` json DEFAULT NULL COMMENT 'BOM快照（生产时使用的BOM）',
  `quantity` int DEFAULT '1' COMMENT '数量',
  `lifecycle_status` tinyint NOT NULL DEFAULT '1' COMMENT '生命周期: 1设计/2客户确认/3备料/4生产/5质检/6发货/7完成/8暂停/9返工',
  `design_task_id` bigint DEFAULT NULL COMMENT '设计任务ID',
  `work_order_id` bigint DEFAULT NULL COMMENT '生产工单ID',
  `bom_id` bigint DEFAULT NULL COMMENT '实际使用的BOM ID',
  `route_id` bigint DEFAULT NULL COMMENT '实际使用的工艺路线ID',
  `film_ids` json DEFAULT NULL COMMENT '使用的菲林ID列表',
  `order_date` date DEFAULT NULL COMMENT '订单日期',
  `delivery_date` date DEFAULT NULL COMMENT '要求交货日期',
  `actual_delivery_date` date DEFAULT NULL COMMENT '实际交货日期',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `instance_status` tinyint DEFAULT '1' COMMENT '实例状态: 0草稿/1已创建/2已计划/3生产中/4已暂停/5已完成/6已发货/7已入库/8在库/9已交付/10已安装/11使用中/12维护中/13已退役/14已退回/15翻新/16已报废/17已取消',
  PRIMARY KEY (`instance_id`),
  UNIQUE KEY `uk_instance_code` (`instance_code`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_lifecycle_status` (`lifecycle_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品实例表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product_instance` WRITE;
/*!40000 ALTER TABLE `product_instance` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_instance` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `engineering_base`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_base` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) DEFAULT NULL,
  `name` varchar(200) DEFAULT NULL,
  `status` int DEFAULT '0',
  `remark` varchar(500) DEFAULT NULL,
  `create_by` varchar(50) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工程管理基础表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `engineering_base` WRITE;
/*!40000 ALTER TABLE `engineering_base` DISABLE KEYS */;
/*!40000 ALTER TABLE `engineering_base` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `engineering_bom`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_bom` (
  `bom_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'BOM ID',
  `bom_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BOM编码',
  `product_id` bigint NOT NULL COMMENT '产品ID',
  `bom_version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号（v1.0, v1.1）',
  `bom_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'manufacturing' COMMENT '类型：engineering工程/manufacturing制造',
  `is_current` tinyint(1) DEFAULT '1' COMMENT '是否当前版本',
  `effective_date` date DEFAULT NULL COMMENT '生效日期',
  `expiry_date` date DEFAULT NULL COMMENT '失效日期',
  `approve_status` bigint NOT NULL DEFAULT '1' COMMENT '审核状态: 1草稿,2待审批,3已批准,4已驳回',
  `approve_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批准人',
  `approve_time` datetime DEFAULT NULL COMMENT '批准时间',
  `approve_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批准备注',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `bom_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'bom名称',
  `version` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'V1.0' COMMENT '版本号',
  `source_sample_id` bigint DEFAULT NULL COMMENT '来源打样单ID',
  `parent_bom_id` bigint DEFAULT NULL COMMENT '父版本BOM ID',
  PRIMARY KEY (`bom_id`),
  UNIQUE KEY `uk_bom_code_version` (`bom_code`,`bom_version`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_is_current` (`is_current`),
  KEY `idx_approve_status` (`approve_status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BOM主表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `engineering_bom` WRITE;
/*!40000 ALTER TABLE `engineering_bom` DISABLE KEYS */;
INSERT INTO `engineering_bom` VALUES (1,'JST001POOO-BOM',1,'V1.0','manufacturing',1,'2026-08-20',NULL,3,NULL,NULL,'审核通过','admin','2026-08-20 11:03:12','admin','2026-08-20 11:03:23','JST001POOO-BOM','JST001POOO-BOM','V1.0',NULL,NULL);
/*!40000 ALTER TABLE `engineering_bom` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `engineering_bom_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_bom_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `bom_id` bigint NOT NULL COMMENT 'BOM ID',
  `parent_material_id` bigint DEFAULT NULL COMMENT '父节点明细ID（指向本表item_id，NULL=根节点）',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID（可空，工程后续匹配）',
  `material_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物料编码（可空）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称',
  `quantity` decimal(12,4) NOT NULL COMMENT '用量（每个成品消耗的物料数量）',
  `applied_qty` decimal(14,4) DEFAULT NULL COMMENT '应用料（含损耗）= 用量×(1+损耗率/100)',
  `actual_issue_qty` decimal(14,4) DEFAULT NULL COMMENT '实际投料（按最低投料向上取整）',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PCS' COMMENT '单位',
  `loss_rate` decimal(5,2) DEFAULT '0.00' COMMENT '损耗率(%)',
  `module_qty` decimal(12,4) DEFAULT NULL COMMENT '模数：每份材料可产出产品数量',
  `base_qty` decimal(12,4) DEFAULT NULL COMMENT '基数：每个产品所需材料份数',
  `min_issue_qty` decimal(12,4) DEFAULT NULL COMMENT '最低投料量',
  `width_mm` decimal(10,2) DEFAULT NULL COMMENT '规格-宽度(mm)',
  `length_mm` decimal(10,2) DEFAULT NULL COMMENT '规格-长度(mm)',
  `layer` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '层（overlay/upper_circuit/...）',
  `position_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '位号',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'buy' COMMENT '来源：buy/make',
  `substitute_json` json DEFAULT NULL COMMENT '替代物料列表',
  `item_order` int DEFAULT '0' COMMENT '排序',
  `specification` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号（原始串）',
  `remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_by` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`item_id`),
  KEY `idx_bom_id` (`bom_id`),
  KEY `idx_material_id` (`material_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BOM明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `engineering_bom_item` WRITE;
/*!40000 ALTER TABLE `engineering_bom_item` DISABLE KEYS */;
INSERT INTO `engineering_bom_item` VALUES (1,1,NULL,1111,'MTR202608131112','2812HL-14C-菘翊',0.6667,0.6667,0.6667,'PCS',0.00,3.0000,2.0000,1.0000,NULL,NULL,NULL,NULL,'buy',NULL,1,'','','2026-08-20 11:03:12','2026-08-20 11:03:12','admin','admin'),(2,1,NULL,977,'MTR202608130978','HJ-12228FDN',0.5000,0.5000,0.5000,'PCS',0.00,2.0000,1.0000,1.0000,NULL,NULL,NULL,NULL,'buy',NULL,2,'280g带点镀镍','','2026-08-20 11:03:12','2026-08-20 11:03:12','admin','admin'),(3,1,NULL,332,'MTR202608130333','PET350#（成浩林）',1.0000,1.0000,1.0000,'PCS',0.00,1.0000,1.0000,1.0000,NULL,NULL,NULL,NULL,'buy',NULL,3,'140','','2026-08-20 11:03:12','2026-08-20 11:03:12','admin','admin');
/*!40000 ALTER TABLE `engineering_bom_item` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `engineering_film`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_film` (
  `film_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菲林ID',
  `film_code` varchar(50) NOT NULL COMMENT '菲林编码',
  `film_name` varchar(200) NOT NULL COMMENT '菲林名称',
  `film_type` varchar(30) NOT NULL COMMENT '菲林类型：OVERLAY/UPPER_CIRCUIT/SPACER/LOWER_CIRCUIT/BACK_ADHESIVE',
  `product_id` bigint NOT NULL COMMENT '关联产品ID',
  `product_code` varchar(50) NOT NULL COMMENT '产品编码',
  `product_name` varchar(200) NOT NULL COMMENT '产品名称',
  `version` varchar(20) NOT NULL DEFAULT 'v1.0' COMMENT '版本号',
  `is_current` tinyint DEFAULT '1' COMMENT '是否当前版本：0-否,1-是',
  `parent_film_id` bigint DEFAULT NULL COMMENT '父菲林ID',
  `film_size` varchar(100) DEFAULT NULL COMMENT '菲林尺寸',
  `film_thickness` decimal(10,3) DEFAULT NULL COMMENT '菲林厚度(mm)',
  `film_material` varchar(100) DEFAULT NULL COMMENT '菲林材料',
  `color` varchar(50) DEFAULT NULL COMMENT '颜色',
  `file_id` bigint DEFAULT NULL COMMENT '文件ID',
  `file_path` varchar(500) DEFAULT NULL COMMENT '文件路径',
  `file_name` varchar(200) DEFAULT NULL COMMENT '文件名',
  `technical_spec` text COMMENT '技术规格',
  `design_notes` text COMMENT '设计说明',
  `process_id` bigint DEFAULT NULL COMMENT '关联工序ID',
  `process_code` varchar(50) DEFAULT NULL COMMENT '关联工序编码',
  `approve_status` tinyint NOT NULL DEFAULT '1' COMMENT '审核状态: 1草稿,2待审批,3已批准,4已驳回',
  `designer_id` bigint DEFAULT NULL COMMENT '设计人员ID',
  `designer_name` varchar(100) DEFAULT NULL COMMENT '设计人员姓名',
  `design_time` datetime DEFAULT NULL COMMENT '设计完成时间',
  `is_released` tinyint NOT NULL DEFAULT '0' COMMENT '是否下发生产：0-否,1-是',
  `release_time` datetime DEFAULT NULL COMMENT '下发生产时间',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `deleted` tinyint DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`film_id`),
  UNIQUE KEY `uk_film_code_version` (`film_code`,`version`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_film_type` (`film_type`),
  KEY `idx_approve_status` (`approve_status`),
  KEY `idx_is_current` (`is_current`),
  CONSTRAINT `chk_film_type` CHECK ((`film_type` in (_utf8mb4'OVERLAY',_utf8mb4'UPPER_CIRCUIT',_utf8mb4'SPACER',_utf8mb4'LOWER_CIRCUIT',_utf8mb4'BACK_ADHESIVE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品菲林表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `engineering_film` WRITE;
/*!40000 ALTER TABLE `engineering_film` DISABLE KEYS */;
/*!40000 ALTER TABLE `engineering_film` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `engineering_routing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_routing` (
  `routing_id` bigint NOT NULL AUTO_INCREMENT COMMENT '路线ID',
  `routing_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '路线编码',
  `routing_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '路线名称',
  `product_id` bigint NOT NULL COMMENT '产品ID',
  `product_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品编码',
  `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品名称',
  `routing_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工艺路线类型',
  `routing_version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号',
  `is_current` tinyint(1) DEFAULT '1' COMMENT '是否当前版本：0否 1是',
  `approve_status` tinyint NOT NULL DEFAULT '1' COMMENT '审核状态: 1草稿,2待审批,3已批准,4已驳回',
  `total_labor_hours` decimal(10,2) DEFAULT '0.00' COMMENT '总人工工时',
  `total_machine_hours` decimal(10,2) DEFAULT '0.00' COMMENT '总机器工时',
  `process_count` int DEFAULT '0' COMMENT '工序数量',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '路线说明',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `version` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'V1.0' COMMENT '版本号',
  `source_sample_id` bigint DEFAULT NULL COMMENT '来源打样单ID',
  `parent_routing_id` bigint DEFAULT NULL COMMENT '父版本Routing ID',
  PRIMARY KEY (`routing_id`),
  UNIQUE KEY `uk_routing_code_version` (`routing_code`,`routing_version`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_is_current` (`is_current`),
  KEY `idx_approve_status` (`approve_status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_routing_product_status` (`product_id`,`is_current`,`approve_status`),
  KEY `idx_routing_code_name` (`routing_code`,`routing_name`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品工艺路线表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `engineering_routing` WRITE;
/*!40000 ALTER TABLE `engineering_routing` DISABLE KEYS */;
INSERT INTO `engineering_routing` VALUES (1,'JST001POOO-ROUTING','JST001POOO工艺路线',1,'JST001POOO','JST001POOO',NULL,'V1.0',1,3,0.00,0.00,3,'','admin','2026-08-20 11:04:38','admin','2026-08-20 11:04:38','','V1.0',NULL,NULL);
/*!40000 ALTER TABLE `engineering_routing` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `engineering_routing_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_routing_item` (
  `detail_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `routing_id` bigint NOT NULL COMMENT '路线ID',
  `process_id` bigint DEFAULT NULL COMMENT '标准工序ID（可空，匹配不到时由工程后续完善）',
  `process_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序名称（印刷等自定义工序冗余，标准工序可空走关联）',
  `major_category` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ASSEMBLY' COMMENT '大类：ASSEMBLY冲型组装/PRINT印刷',
  `process_order` int NOT NULL COMMENT '工序顺序',
  `custom_labor_hours` decimal(10,2) DEFAULT NULL COMMENT '定制人工工时',
  `custom_machine_hours` decimal(10,2) DEFAULT NULL COMMENT '定制机器工时',
  `standard_wage` decimal(10,2) DEFAULT NULL COMMENT '标准工价(元/工时,059人工成本核算)',
  `custom_process_params` json DEFAULT NULL COMMENT '定制工艺参数（JSON格式）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `group_id` bigint DEFAULT NULL COMMENT '组合ID（同一组合的工序共享此ID，NULL表示独立工序）',
  `group_order` int DEFAULT NULL COMMENT '组合顺序（第几组）',
  `group_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组合名称',
  `process_category` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MAIN' COMMENT '工序类别: PREPARATION准备/MAIN主要/FINISHING后处理/QUALITY质量',
  `index_number` int DEFAULT NULL COMMENT '下标数字（带下标工序的下标值，如4=④）',
  `precondition` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前置依赖标识（如 PANEL_4=面板④）',
  `precondition_display` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前置依赖显示名（如：面板④ 面板冲型）',
  `is_optional` tinyint(1) NOT NULL DEFAULT '0' COMMENT '可选工序：0-必做,1-可选',
  PRIMARY KEY (`detail_id`),
  UNIQUE KEY `uk_routing_process_order` (`routing_id`,`process_order`),
  KEY `idx_routing_id` (`routing_id`),
  KEY `idx_process_id` (`process_id`),
  KEY `idx_process_order` (`process_order`),
  KEY `idx_detail_routing_order` (`routing_id`,`process_order`),
  KEY `idx_group_id` (`group_id`),
  CONSTRAINT `fk_routing_detail_process` FOREIGN KEY (`process_id`) REFERENCES `engineering_standard_process` (`process_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_routing_detail_routing` FOREIGN KEY (`routing_id`) REFERENCES `engineering_routing` (`routing_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品路线明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `engineering_routing_item` WRITE;
/*!40000 ALTER TABLE `engineering_routing_item` DISABLE KEYS */;
INSERT INTO `engineering_routing_item` VALUES (1,1,24,NULL,'ASSEMBLY',1,0.00,0.00,NULL,NULL,'面板冲孔作业','2026-08-20 11:04:38','2026-08-20 11:04:38',1787195078146543,1,'组合1','PANEL',NULL,NULL,NULL,0),(2,1,25,NULL,'ASSEMBLY',2,0.00,0.00,NULL,NULL,'面板冲形作业','2026-08-20 11:04:38','2026-08-20 11:04:38',1787195078145738,2,'组合2','PANEL',NULL,NULL,NULL,0),(3,1,NULL,NULL,'PRINT',3,0.00,0.00,NULL,'{\"inkNo\": \"32\", \"colorNo\": \"ZHENGYIN32\", \"screenNo\": \"A032\", \"printName\": \"\"}',NULL,'2026-08-20 11:04:38','2026-08-20 11:04:38',NULL,NULL,NULL,'PANEL',NULL,NULL,NULL,0);
/*!40000 ALTER TABLE `engineering_routing_item` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `engineering_standard_process`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_standard_process` (
  `process_id` bigint NOT NULL AUTO_INCREMENT COMMENT '工序ID',
  `process_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序编码',
  `process_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序名称',
  `process_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序类型：PRINTING印刷/CUTTING模切/LAMINATING贴合/TESTING测试/PACKAGING包装',
  `process_category` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序类别：PREPARATION准备/MAIN主要/FINISHING后处理/QUALITY质量',
  `standard_labor_hours` decimal(10,2) DEFAULT '0.00' COMMENT '标准人工工时(小时)',
  `standard_machine_hours` decimal(10,2) DEFAULT '0.00' COMMENT '标准机器工时(小时)',
  `process_param_template` json DEFAULT NULL COMMENT '工艺参数模板（JSON格式）',
  `skill_requirement` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '技能要求',
  `equipment_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备类型',
  `quality_standard` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '质量标准',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序说明',
  `is_enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用：0否 1是',
  `display_order` int DEFAULT '0' COMMENT '显示顺序',
  `icon` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '图标',
  `has_index` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否带下标：0-不带,1-带',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`process_id`),
  UNIQUE KEY `uk_process_code` (`process_code`),
  KEY `idx_process_type` (`process_type`),
  KEY `idx_is_enabled` (`is_enabled`),
  KEY `idx_display_order` (`display_order`),
  KEY `idx_standard_process_type_category` (`process_type`,`process_category`,`is_enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=68 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品标准工序表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `engineering_standard_process` WRITE;
/*!40000 ALTER TABLE `engineering_standard_process` DISABLE KEYS */;
INSERT INTO `engineering_standard_process` VALUES (17,'SP-101','面板','PANEL','PANEL',0.00,0.00,NULL,'','','','面板基材',1,0,'面板',0,'admin','2026-08-08 17:15:25','admin','2026-08-08 23:24:47'),(19,'SP-103','面板隔片','SPACER','PANEL',0.00,0.00,NULL,NULL,NULL,NULL,'面板隔片作业',1,3,'面板隔片',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(20,'SP-104','面板背胶','FILM_APPLY','PANEL',0.00,0.00,NULL,NULL,NULL,NULL,'面板背胶贴合',1,4,'面板背胶',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(21,'SP-105','面板裁切','CUTTING','PANEL',0.00,0.00,NULL,'','','','面板裁剪作业',1,0,'面板裁切',0,'admin','2026-08-08 17:15:25','admin','2026-08-08 22:30:45'),(22,'SP-106','面板保护膜','PROTECTIVE_FILM','PANEL',0.00,0.00,NULL,NULL,NULL,NULL,'面板保护膜作业',1,6,'面板保护膜',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(23,'SP-107','面板凹凸','PUNCH_SHAPE','PANEL',0.00,0.00,NULL,NULL,NULL,NULL,'面板凹凸成型',1,7,'面板凹凸',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(24,'SP-108','面板冲孔','PUNCH_HOLE','PANEL',0.00,0.00,NULL,NULL,NULL,NULL,'面板冲孔作业',1,8,'面板冲孔',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(25,'SP-109','面板冲形','PUNCH_SHAPE','PANEL',0.00,0.00,NULL,NULL,NULL,NULL,'面板冲形作业',1,9,'面板冲形',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(26,'SP-110','垫片','GASKET','PANEL',0.00,0.00,NULL,NULL,NULL,NULL,'面板垫片作业',1,10,'垫片',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(27,'SP-111','面板冲第一刀','PUNCH_SHAPE','PANEL',0.00,0.00,NULL,NULL,NULL,NULL,'面板冲切第一刀',1,11,'面板冲第一刀',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(28,'SP-112','面板冲第二刀','PUNCH_SHAPE','PANEL',0.00,0.00,NULL,NULL,NULL,NULL,'面板冲切第二刀',1,12,'面板冲第二刀',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(29,'SP-201','上线','UP_LINE','UP_LINE',0.00,0.00,NULL,'','','','上线基材',1,0,'上线',0,'admin','2026-08-08 17:15:25','admin','2026-08-08 23:28:02'),(30,'SP-202','上线隔片','SPACER','UP_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'上线隔片作业',1,21,'上线隔片',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(31,'SP-203','上线裁切','CUTTING','UP_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'上线裁切作业',1,22,'上线裁切',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(32,'SP-204','上线加强片','SPACER','UP_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'上线加强片作业',1,23,'上线加强片',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(33,'SP-205','上线保护膜','PROTECTIVE_FILM','UP_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'上线保护膜作业',1,24,'上线保护膜',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(34,'SP-206','上线凹凸','PUNCH_SHAPE','UP_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'上线凹凸成型',1,25,'上线凹凸',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(35,'SP-207','上线冲孔','PUNCH_HOLE','UP_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'上线冲孔作业',1,26,'上线冲孔',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(36,'SP-208','上线冲第一刀','PUNCH_SHAPE','UP_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'上线冲切第一刀',1,27,'上线冲第一刀',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(37,'SP-209','上线冲型','PUNCH_SHAPE','UP_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'上线冲型作业',1,28,'上线冲型',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(38,'SP-301','下线','DOWN_LINE','DOWN_LINE',0.00,0.00,NULL,'','','','下线基材',1,0,'下线',0,'admin','2026-08-08 17:15:25','admin','2026-08-08 23:30:56'),(39,'SP-302','下线隔片','SPACER','DOWN_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'下线隔片作业',1,31,'下线隔片',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(40,'SP-303','下线裁切','CUTTING','DOWN_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'下线裁切作业',1,32,'下线裁切',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(41,'SP-304','下线加强片','SPACER','DOWN_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'下线加强片作业',1,33,'下线加强片',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(42,'SP-305','下线保护膜','PROTECTIVE_FILM','DOWN_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'下线保护膜作业',1,34,'下线保护膜',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(43,'SP-306','下线背胶','FILM_APPLY','DOWN_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'下线背胶贴合',1,35,'下线背胶',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(44,'SP-307','下线冲孔','PUNCH_HOLE','DOWN_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'下线冲孔作业',1,36,'下线冲孔',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(45,'SP-308','下线第一刀','PUNCH_SHAPE','DOWN_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'下线冲切第一刀',1,37,'下线第一刀',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(46,'SP-309','下线冲型','PUNCH_SHAPE','DOWN_LINE',0.00,0.00,NULL,'','','','下线冲形作业',1,0,'下线冲型',0,'admin','2026-08-08 17:15:25','admin','2026-08-08 23:30:46'),(47,'SP-310','下线连接器','CONNECTOR','DOWN_LINE',0.00,0.00,NULL,NULL,NULL,NULL,'下线连接器装配',1,39,'下线连接器',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(48,'SP-401','弹片','GASKET','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'弹片作业',1,40,'弹片',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(49,'SP-402','弹片上贴黑豆','FILM_APPLY','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'弹片贴黑豆作业',1,41,'弹片上贴黑豆',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(50,'SP-501','LED','CONNECTOR','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'LED元件装配',1,50,'LED',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(51,'SP-502','打公PIN','CONNECTOR','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'打公PIN作业',1,51,'打公PIN',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(52,'SP-503','打母PIN','CONNECTOR','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'打母PIN作业',1,52,'打母PIN',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(53,'SP-504','撕水性保护膜','FILM_REMOVE','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'撕水性保护膜作业',1,53,'撕水性保护膜',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(54,'SP-505','清洁','CLEANING','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'清洁作业',1,54,'清洁',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(55,'SP-506','线路测阻值','RESISTOR','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'线路测阻值作业',1,55,'线路测阻值',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(56,'SP-507','贴RUBBER胶','FILM_APPLY','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'贴RUBBER胶作业',1,56,'贴RUBBER胶',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(57,'SP-508','贴周期码','FILM_APPLY','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'贴周期码作业',1,57,'贴周期码',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(58,'SP-509','贴离形纸','FILM_APPLY','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'贴离形纸作业',1,58,'贴离形纸',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(59,'SP-510','长方形','PUNCH_SHAPE','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'长方形冲切',1,59,'长方形',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(60,'SP-511','凸台','PUNCH_SHAPE','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'凸台成型',1,60,'凸台',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(61,'SP-512','连接器','CONNECTOR','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'连接器装配',1,61,'连接器',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(62,'SP-513','连接器与适配器','CONNECTOR','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'连接器与适配器装配',1,62,'连接器与适配器',0,'admin','2026-08-08 17:15:25',NULL,'2026-08-08 17:15:25'),(64,'SP-514','品检','OTHER','OTHER',0.00,0.00,NULL,'','','','品检作业（全检/抽检）',1,0,'QC',0,'admin','2026-08-08 22:01:20','admin','2026-08-10 09:45:02'),(65,'SP-515','OHM','RESISTOR','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'电阻/欧姆检测',1,71,'OHM',0,'admin','2026-08-08 22:06:34',NULL,'2026-08-08 22:06:34'),(66,'SP-517','包装','OTHER','OTHER',0.00,0.00,NULL,'','','','包装',1,0,'包装',0,'admin','2026-08-08 23:50:00','admin','2026-08-08 23:50:00'),(67,'SP-311','下线跳','DOWN_LINE','DOWN_LINE',0.00,0.00,NULL,'','','','下标工序',1,0,'下线',1,'admin','2026-08-09 00:16:59','admin','2026-08-10 10:27:54');
/*!40000 ALTER TABLE `engineering_standard_process` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

