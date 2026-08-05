-- MySQL dump 10.13  Distrib 8.4.10, for Linux (x86_64)
--
-- Host: localhost    Database: jjx_erp_db
-- ------------------------------------------------------
-- Server version	8.4.10-0ubuntu0.26.04.1

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

--
-- Table structure for table `engineering_base`
--

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

--
-- Dumping data for table `engineering_base`
--

LOCK TABLES `engineering_base` WRITE;
/*!40000 ALTER TABLE `engineering_base` DISABLE KEYS */;
/*!40000 ALTER TABLE `engineering_base` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `engineering_bom`
--

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
  `approve_status` bigint NOT NULL DEFAULT '1' COMMENT '审核状态',
  `approve_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批准人',
  `approve_time` datetime DEFAULT NULL COMMENT '批准时间',
  `approve_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批准备注',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `bom_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'bom名称',
  PRIMARY KEY (`bom_id`),
  UNIQUE KEY `uk_bom_code_version` (`bom_code`,`bom_version`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_is_current` (`is_current`),
  KEY `idx_approve_status` (`approve_status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BOM主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `engineering_bom`
--

LOCK TABLES `engineering_bom` WRITE;
/*!40000 ALTER TABLE `engineering_bom` DISABLE KEYS */;
INSERT INTO `engineering_bom` VALUES (1,'BOM-QT2608040001-SAMPLE',1,'V1','manufacturing',1,'2026-08-04',NULL,3,NULL,NULL,'审核通过','xiaoshou0','2026-08-04 16:07:19','gongcheng0','2026-08-04 17:13:29','由样品单[SP2608040001]资料转移生成，请工程确认后批准','BOM-QT2608040001-SAMPLE');
/*!40000 ALTER TABLE `engineering_bom` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `engineering_bom_item`
--

DROP TABLE IF EXISTS `engineering_bom_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_bom_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `bom_id` bigint NOT NULL COMMENT 'BOM ID',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID（可空，工程后续匹配）',
  `material_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物料编码（可空）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称',
  `quantity` decimal(12,4) NOT NULL COMMENT '用量（每个成品消耗的物料数量）',
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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BOM明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `engineering_bom_item`
--

LOCK TABLES `engineering_bom_item` WRITE;
/*!40000 ALTER TABLE `engineering_bom_item` DISABLE KEYS */;
INSERT INTO `engineering_bom_item` VALUES (1,1,1,'MAT-001','PET薄膜 0.125mm 透明',1.0000,'M',0.00,NULL,NULL,NULL,NULL,NULL,'印刷',NULL,'buy',NULL,1,'0.125mm×1200mm卷',NULL,'2026-08-04 16:47:36','2026-08-04 16:47:36','gongcheng0','gongcheng0'),(2,1,4,'MAT-004','导电银浆 BY-6000',2.0000,'KG',0.00,NULL,NULL,NULL,NULL,NULL,'冲切',NULL,'buy',NULL,2,'1kg/罐',NULL,'2026-08-04 16:47:36','2026-08-04 16:47:36','gongcheng0','gongcheng0');
/*!40000 ALTER TABLE `engineering_bom_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `engineering_film`
--

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
  `approve_status` tinyint NOT NULL DEFAULT '1' COMMENT '审核状态：1-草稿,2-待审核,3-已通过,4-已驳回',
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

--
-- Dumping data for table `engineering_film`
--

LOCK TABLES `engineering_film` WRITE;
/*!40000 ALTER TABLE `engineering_film` DISABLE KEYS */;
/*!40000 ALTER TABLE `engineering_film` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `engineering_routing`
--

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
  `approve_status` tinyint NOT NULL DEFAULT '1' COMMENT '审核状态',
  `total_labor_hours` decimal(10,2) DEFAULT '0.00' COMMENT '总人工工时',
  `total_machine_hours` decimal(10,2) DEFAULT '0.00' COMMENT '总机器工时',
  `process_count` int DEFAULT '0' COMMENT '工序数量',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '路线说明',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
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

--
-- Dumping data for table `engineering_routing`
--

LOCK TABLES `engineering_routing` WRITE;
/*!40000 ALTER TABLE `engineering_routing` DISABLE KEYS */;
INSERT INTO `engineering_routing` VALUES (1,'RTE-QT2608040001-SAMPLE','QT2608040001（打样传承工艺路线）',1,'QT2608040001','QT2608040001',NULL,'V1',1,3,3.05,2.55,5,NULL,'xiaoshou0','2026-08-04 16:07:20','xiaoshou0','2026-08-04 17:08:07',NULL);
/*!40000 ALTER TABLE `engineering_routing` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `engineering_routing_item`
--

DROP TABLE IF EXISTS `engineering_routing_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_routing_item` (
  `detail_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `routing_id` bigint NOT NULL COMMENT '路线ID',
  `process_id` bigint DEFAULT NULL COMMENT '标准工序ID（可空，匹配不到时由工程后续完善）',
  `process_order` int NOT NULL COMMENT '工序顺序',
  `custom_labor_hours` decimal(10,2) DEFAULT NULL COMMENT '定制人工工时',
  `custom_machine_hours` decimal(10,2) DEFAULT NULL COMMENT '定制机器工时',
  `custom_process_params` json DEFAULT NULL COMMENT '定制工艺参数（JSON格式）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `group_id` bigint DEFAULT NULL COMMENT '组合ID（同一组合的工序共享此ID，NULL表示独立工序）',
  `group_order` int DEFAULT NULL COMMENT '组合顺序（第几组）',
  `group_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组合名称',
  `process_category` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MAIN' COMMENT '工序类别: PREPARATION准备/MAIN主要/FINISHING后处理/QUALITY质量',
  PRIMARY KEY (`detail_id`),
  UNIQUE KEY `uk_routing_process_order` (`routing_id`,`process_order`),
  KEY `idx_routing_id` (`routing_id`),
  KEY `idx_process_id` (`process_id`),
  KEY `idx_process_order` (`process_order`),
  KEY `idx_detail_routing_order` (`routing_id`,`process_order`),
  KEY `idx_group_id` (`group_id`),
  CONSTRAINT `fk_routing_detail_process` FOREIGN KEY (`process_id`) REFERENCES `engineering_standard_process` (`process_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_routing_detail_routing` FOREIGN KEY (`routing_id`) REFERENCES `engineering_routing` (`routing_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品路线明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `engineering_routing_item`
--

LOCK TABLES `engineering_routing_item` WRITE;
/*!40000 ALTER TABLE `engineering_routing_item` DISABLE KEYS */;
INSERT INTO `engineering_routing_item` VALUES (4,1,NULL,1,0.55,0.55,NULL,'打样传承: 印刷','2026-08-04 17:08:07','2026-08-04 17:08:07',1785834486522160,1,'组合1','MAIN'),(5,1,1,2,0.50,0.00,NULL,'原材料（PET/PC薄膜、银浆、胶材等）进厂检验','2026-08-04 17:08:07','2026-08-04 17:08:07',1785834486522160,1,'组合1','PREPARATION'),(6,1,NULL,3,0.00,0.00,NULL,'打样传承: 冲切','2026-08-04 17:08:07','2026-08-04 17:08:07',1785834486522413,2,'组合2','MAIN'),(7,1,3,4,0.50,0.50,NULL,'基材表面清洁去油污','2026-08-04 17:08:07','2026-08-04 17:08:07',1785834486522413,2,'组合2','PREPARATION'),(8,1,4,5,1.50,1.50,NULL,'按线路图制作印刷丝网','2026-08-04 17:08:07','2026-08-04 17:08:07',1785834486522413,2,'组合2','PREPARATION');
/*!40000 ALTER TABLE `engineering_routing_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `engineering_standard_process`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品标准工序表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `engineering_standard_process`
--

LOCK TABLES `engineering_standard_process` WRITE;
/*!40000 ALTER TABLE `engineering_standard_process` DISABLE KEYS */;
INSERT INTO `engineering_standard_process` VALUES (1,'SP-001','来料检验','PRINTING','PREPARATION',0.50,0.00,NULL,NULL,NULL,NULL,'原材料（PET/PC薄膜、银浆、胶材等）进厂检验',1,1,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(2,'SP-002','薄膜裁切','CUTTING','PREPARATION',0.80,0.80,NULL,NULL,NULL,NULL,'按产品尺寸裁切基材薄膜',1,2,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(3,'SP-003','材料清洗','PRINTING','PREPARATION',0.50,0.50,NULL,NULL,NULL,NULL,'基材表面清洁去油污',1,3,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(4,'SP-004','丝网制作','PRINTING','PREPARATION',1.50,1.50,NULL,NULL,NULL,NULL,'按线路图制作印刷丝网',1,4,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(5,'SP-005','银浆线路印刷','PRINTING','MAIN',1.20,1.20,NULL,NULL,NULL,NULL,'导电银浆印刷线路（上线）',1,5,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(6,'SP-006','银浆烘干','PRINTING','MAIN',0.60,0.60,NULL,NULL,NULL,NULL,'印刷后银浆烘干固化',1,6,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(7,'SP-007','绝缘油印刷','PRINTING','MAIN',1.00,1.00,NULL,NULL,NULL,NULL,'绝缘保护层印刷（下线）',1,7,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(8,'SP-008','碳浆印刷','PRINTING','MAIN',1.00,1.00,NULL,NULL,NULL,NULL,'碳浆触点印刷（下线）',1,8,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(9,'SP-009','UV固化','PRINTING','MAIN',0.50,0.50,NULL,NULL,NULL,NULL,'UV油墨固化',1,9,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(10,'SP-010','银浆检验','TESTING','QUALITY',0.40,0.00,NULL,NULL,NULL,NULL,'印刷线路银浆质量检验',1,10,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(11,'SP-011','隔离层贴合','LAMINATING','MAIN',1.10,1.10,NULL,NULL,NULL,NULL,'隔离层（spacer）贴合',1,11,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(12,'SP-012','双面胶贴合','LAMINATING','MAIN',1.10,1.10,NULL,NULL,NULL,NULL,'背胶层贴合',1,12,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(13,'SP-013','定位模切','CUTTING','MAIN',1.00,1.00,NULL,NULL,NULL,NULL,'按外形模具定位模切',1,13,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(14,'SP-014','冲切外形','CUTTING','MAIN',0.90,0.90,NULL,NULL,NULL,NULL,'冲切产品外形轮廓',1,14,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(15,'SP-015','面板印刷','MAIN_PAD','MAIN',1.30,1.30,NULL,NULL,NULL,NULL,'面板文字/图案印刷',1,15,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(16,'SP-016','按键装配','LAMINATING','FINISHING',1.50,1.00,NULL,NULL,NULL,NULL,'金属弹片/按键装配',1,16,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(17,'SP-017','引线焊接','UP_LINE','FINISHING',1.20,0.80,NULL,NULL,NULL,NULL,'连接引线焊接/压接',1,17,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(18,'SP-018','功能测试','TESTING','QUALITY',1.00,1.00,NULL,NULL,NULL,NULL,'导通/绝缘/按键功能测试',1,18,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(19,'SP-019','外观检验','TESTING','QUALITY',0.60,0.00,NULL,NULL,NULL,NULL,'外观/尺寸/丝印质量检验',1,19,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45'),(20,'SP-020','包装入库','PACKAGING','FINISHING',0.50,0.20,NULL,NULL,NULL,NULL,'防静电包装、装箱、入库',1,20,'','admin','2026-08-04 16:09:45',NULL,'2026-08-04 16:09:45');
/*!40000 ALTER TABLE `engineering_standard_process` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_alert_log`
--

DROP TABLE IF EXISTS `inventory_alert_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_alert_log` (
  `alert_id` bigint NOT NULL AUTO_INCREMENT COMMENT '预警ID',
  `alert_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '预警类型：safe_stock安全库存/max_stock最高库存/expiry保质期/obsolete呆滞料',
  `order_no` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联订单号(订单缺料预警用)',
  `alert_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'warning' COMMENT '预警级别：info提示/warning警告/urgent紧急',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称',
  `current_stock` decimal(12,4) DEFAULT NULL COMMENT '当前库存',
  `safe_stock` decimal(12,4) DEFAULT NULL COMMENT '安全库存',
  `max_stock` decimal(12,4) DEFAULT NULL COMMENT '最高库存',
  `expiry_date` date DEFAULT NULL COMMENT '有效期至',
  `last_outbound_date` date DEFAULT NULL COMMENT '最后出库日期',
  `alert_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '预警消息',
  `alert_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预警时间',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '处理状态: 0新增/1已读/2已处理',
  `processed_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理人',
  `processed_time` datetime DEFAULT NULL COMMENT '处理时间',
  `process_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理备注',
  `suggestion` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理建议',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`alert_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_alert_type` (`alert_type`),
  KEY `idx_alert_time` (`alert_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存预警日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_alert_log`
--

LOCK TABLES `inventory_alert_log` WRITE;
/*!40000 ALTER TABLE `inventory_alert_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_alert_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_inbound_item`
--

DROP TABLE IF EXISTS `inventory_inbound_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_inbound_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `inbound_id` bigint NOT NULL COMMENT '入库单ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码（冗余）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称（冗余）',
  `specification` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PCS' COMMENT '单位',
  `quantity` decimal(12,4) NOT NULL COMMENT '入库数量',
  `unit_price` decimal(12,4) DEFAULT NULL COMMENT '单价',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '金额',
  `batch_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `production_date` date DEFAULT NULL COMMENT '生产日期',
  `expiry_date` date DEFAULT NULL COMMENT '有效期至',
  `location_id` bigint DEFAULT NULL COMMENT '实际存放库位',
  `qualified_quantity` decimal(12,4) DEFAULT NULL COMMENT '合格数量',
  `rejected_quantity` decimal(12,4) DEFAULT NULL COMMENT '不合格数量',
  `reject_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '不合格原因',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`item_id`),
  KEY `idx_inbound_id` (`inbound_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_location_id` (`location_id`),
  CONSTRAINT `fk_inbound_item_material` FOREIGN KEY (`material_id`) REFERENCES `inventory_material` (`material_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_inbound_item_order` FOREIGN KEY (`inbound_id`) REFERENCES `inventory_inbound_order` (`inbound_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入库单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_inbound_item`
--

LOCK TABLES `inventory_inbound_item` WRITE;
/*!40000 ALTER TABLE `inventory_inbound_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_inbound_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_inbound_order`
--

DROP TABLE IF EXISTS `inventory_inbound_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_inbound_order` (
  `inbound_id` bigint NOT NULL AUTO_INCREMENT COMMENT '入库单ID',
  `inbound_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入库单号，格式：IN+YYYYMMDD+流水号',
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '链路追踪ID',
  `inbound_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入库类型：purchase采购入库/production生产入库/return退货入库/transfer调拨入库/adjust盘盈入库',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源类型：purchase_order/work_order/sales_return',
  `source_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `source_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单号',
  `warehouse_id` bigint NOT NULL COMMENT '入库仓库ID',
  `location_id` bigint DEFAULT NULL COMMENT '建议库位ID',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商ID（采购入库时使用）',
  `supplier_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商名称',
  `inbound_date` date NOT NULL COMMENT '入库日期',
  `total_quantity` decimal(12,4) DEFAULT '0.0000' COMMENT '总数量',
  `total_amount` decimal(12,2) DEFAULT '0.00' COMMENT '总金额',
  `inspector_id` bigint DEFAULT NULL COMMENT '检验员ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检验员姓名',
  `inspection_result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检验结果：pass合格/fail不合格/partial部分合格',
  `inspection_time` datetime DEFAULT NULL COMMENT '检验时间',
  `inspection_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检验备注',
  `order_status` tinyint NOT NULL DEFAULT '0' COMMENT '单据状态: 0草稿/1待审批/2已批准/3已驳回/4处理中/5已确认/6已出库/7已入库/8已关闭/9已取消',
  `approve_status` tinyint NOT NULL DEFAULT '1' COMMENT '审批状态: 1待审批/2已批准/3已驳回',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `approver_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人姓名',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `approve_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批意见',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`inbound_id`),
  UNIQUE KEY `uk_inbound_no` (`inbound_no`),
  KEY `idx_source` (`source_type`,`source_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_inbound_date` (`inbound_date`),
  KEY `idx_order_status` (`order_status`),
  KEY `idx_approve_status` (`approve_status`),
  CONSTRAINT `fk_inbound_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `inventory_warehouse` (`warehouse_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入库单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_inbound_order`
--

LOCK TABLES `inventory_inbound_order` WRITE;
/*!40000 ALTER TABLE `inventory_inbound_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_inbound_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_material`
--

DROP TABLE IF EXISTS `inventory_material`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_material` (
  `material_id` bigint NOT NULL AUTO_INCREMENT COMMENT '物料ID',
  `product_id` bigint DEFAULT NULL COMMENT '关联产品ID(成品物料)',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称',
  `material_name_en` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '英文名称',
  `material_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'F' COMMENT '物料类型：R原材料/S半成品/F成品/A辅助材料',
  `process_group` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'M' COMMENT 'M面板,U上线,D下线,T弹片,S导光片,K扩散片,O其他',
  `category_id` bigint DEFAULT NULL COMMENT '物料分类ID',
  `specification` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号/技术参数',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PCS' COMMENT '基本计量单位',
  `unit_conv` decimal(10,4) DEFAULT NULL COMMENT '换算系数（辅助单位与基本单位的换算）',
  `unit_alt` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '辅助计量单位',
  `batch_control` tinyint(1) DEFAULT '0' COMMENT '是否启用批次管理：0否 1是',
  `shelf_life` int DEFAULT NULL COMMENT '保质期天数',
  `expiry_alert_days` int DEFAULT '30' COMMENT '保质期预警提前天数',
  `safe_stock` decimal(12,4) DEFAULT '0.0000' COMMENT '安全库存数量',
  `max_stock` decimal(12,4) DEFAULT '0.0000' COMMENT '最高库存数量',
  `reorder_point` decimal(12,4) DEFAULT '0.0000' COMMENT '再订货点',
  `standard_price` decimal(12,4) DEFAULT NULL COMMENT '标准采购单价',
  `lead_time` int DEFAULT NULL COMMENT '采购提前期(天)',
  `supplier_id` bigint DEFAULT NULL COMMENT '主要供应商ID',
  `supplier_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '主要供应商名称',
  `default_warehouse_id` bigint DEFAULT NULL COMMENT '默认仓库ID',
  `default_location_id` bigint DEFAULT NULL COMMENT '默认库位ID',
  `status` tinyint DEFAULT '1' COMMENT '物料状态：1启用/0停用/2废弃',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`material_id`),
  UNIQUE KEY `uk_material_code` (`material_code`),
  KEY `idx_material_type` (`material_type`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_material_name` (`material_name`),
  CONSTRAINT `fk_material_category` FOREIGN KEY (`category_id`) REFERENCES `inventory_material_category` (`category_id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_material`
--

LOCK TABLES `inventory_material` WRITE;
/*!40000 ALTER TABLE `inventory_material` DISABLE KEYS */;
INSERT INTO `inventory_material` VALUES (1,NULL,'MAT-001','PET薄膜 0.125mm 透明','PET Film 0.125mm Clear','R','P',15,'0.125mm×1200mm卷','M',1.0000,'m',1,365,30,200.0000,1000.0000,100.0000,18.5000,7,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','线路印刷基材，双面电晕处理'),(2,NULL,'MAT-002','PC薄膜 0.25mm 透明','PC Film 0.25mm Clear','R','O',16,'0.25mm×1000mm卷','M',1.0000,'m',1,365,30,150.0000,800.0000,80.0000,32.0000,7,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','高强度基材，耐温130℃'),(3,NULL,'MAT-003','PI薄膜 0.05mm','PI Film 0.05mm','R','O',17,'0.05mm×500mm卷','M',1.0000,'m',1,730,60,50.0000,300.0000,20.0000,85.0000,14,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','耐高温聚酰亚胺，用于FPC补强'),(4,NULL,'MAT-004','导电银浆 BY-6000','Silver Paste BY-6000','R','P',18,'1kg/罐','KG',1.0000,'kg',1,180,15,30.0000,150.0000,15.0000,680.0000,3,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','低温固化丝印银浆，方阻<0.05Ω/□'),(5,NULL,'MAT-005','压延铜箔 35μm','Rolled Copper Foil 35um','R','O',19,'35μm×300mm卷','M',1.0000,'m',1,365,30,100.0000,500.0000,50.0000,42.0000,7,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','线路导电层，蚀刻用'),(6,NULL,'MAT-006','碳浆导电油墨','Carbon Conductive Ink','R','P',20,'1kg/罐','KG',1.0000,'kg',1,180,15,20.0000,100.0000,10.0000,220.0000,3,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','低阻抗碳浆，用于跳线/桥接'),(7,NULL,'MAT-007','3M双面胶 0.1mm','3M Double Tape 0.1mm','A','O',21,'0.1mm×1200mm卷','M',1.0000,'m',0,365,30,300.0000,1500.0000,150.0000,25.0000,3,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','3M467MP，粘合层间'),(8,NULL,'MAT-008','压敏胶带 0.05mm','PSA Tape 0.05mm','A','O',22,'0.05mm×1000mm卷','M',1.0000,'m',0,365,30,200.0000,1000.0000,100.0000,15.0000,3,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','整面压敏胶，用于面板贴合'),(9,NULL,'MAT-009','ACF导电胶膜','ACF Film','A','O',23,'0.05mm×2mm×50m','RL',1.0000,'卷',1,180,15,10.0000,50.0000,5.0000,450.0000,7,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','各向异性导电胶，FPC绑定'),(10,NULL,'MAT-010','PET隔离膜 0.1mm','PET Spacer Film 0.1mm','R','O',24,'0.1mm×1200mm卷','M',1.0000,'m',1,365,30,150.0000,800.0000,80.0000,12.0000,7,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','上下线路隔离层'),(11,NULL,'MAT-011','间隔胶 0.2mm','Spacer Adhesive 0.2mm','A','O',25,'0.2mm×1000mm卷','M',1.0000,'m',0,365,30,120.0000,600.0000,60.0000,28.0000,3,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','按键行程间隔层'),(12,NULL,'MAT-012','PC面板 0.5mm 磨砂','PC Panel 0.5mm Matt','R','M',26,'0.5mm×600mm','PCS',1.0000,'片',1,365,30,500.0000,3000.0000,200.0000,6.5000,5,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','面板层，磨砂防刮'),(13,NULL,'MAT-013','PET面板 0.25mm 亮面','PET Panel 0.25mm Glossy','R','M',27,'0.25mm×600mm','PCS',1.0000,'片',1,365,30,400.0000,2500.0000,150.0000,4.8000,5,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','面板层，高透亮面'),(14,NULL,'MAT-014','FPC连接器 0.5mm间距','FPC Connector 0.5mm Pitch','A','O',28,'0.5mm×4P-30P','PCS',1.0000,'个',0,730,60,2000.0000,10000.0000,500.0000,0.8500,14,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','翻盖式FPC座'),(15,NULL,'MAT-015','镀金FPC排线','Gold-plated FPC Cable','S','O',28,'0.3mm间距×定制','PCS',1.0000,'条',1,365,30,1000.0000,5000.0000,300.0000,2.6000,10,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','按图纸定制，镀金处理'),(16,NULL,'MAT-016','不锈钢弹片 SUS304','SUS304 Dome','R','T',21,'4.5mm×0.05mm','PCS',1.0000,'个',0,730,60,5000.0000,30000.0000,1000.0000,0.0600,7,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','金属按键弹片'),(17,NULL,'MAT-017','LED导光板','LED Light Guide','S','L',27,'按图纸定制','PCS',1.0000,'片',1,365,30,500.0000,3000.0000,200.0000,3.2000,10,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','背光导光'),(18,NULL,'MAT-018','扩散膜','Diffuser Film','S','K',27,'0.1mm×按图纸','PCS',1.0000,'片',1,365,30,300.0000,2000.0000,100.0000,1.5000,7,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','背光匀光'),(19,NULL,'MAT-019','PE保护膜 0.05mm','PE Protective Film 0.05mm','A','O',29,'0.05mm×1200mm卷','M',1.0000,'m',0,180,15,300.0000,1500.0000,150.0000,3.8000,3,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','出货表面保护'),(20,NULL,'MAT-020','防静电包装袋','ESD Shielding Bag','A','O',30,'按规格定制','PCS',1.0000,'个',0,730,60,5000.0000,30000.0000,1000.0000,0.3500,3,NULL,NULL,NULL,NULL,1,'admin','2026-08-03 17:20:16','admin','2026-08-03 17:20:16','防静电屏蔽袋'),(21,1,'QT2608040001','QT2608040001',NULL,'F','M',NULL,NULL,'PCS',NULL,NULL,0,NULL,30,0.0000,0.0000,0.0000,NULL,NULL,NULL,NULL,NULL,NULL,1,'system','2026-08-04 17:13:51','system','2026-08-04 17:13:51',NULL);
/*!40000 ALTER TABLE `inventory_material` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_material_category`
--

DROP TABLE IF EXISTS `inventory_material_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_material_category` (
  `category_id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `category_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编码',
  `category_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID，0表示顶级分类',
  `category_level` int DEFAULT '1' COMMENT '层级：1一级/2二级/3三级',
  `category_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类路径，如：/1/2/3',
  `sort_order` int DEFAULT '0' COMMENT '排序序号',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态：0正常 1停用',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_category_code` (`category_code`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_material_category`
--

LOCK TABLES `inventory_material_category` WRITE;
/*!40000 ALTER TABLE `inventory_material_category` DISABLE KEYS */;
INSERT INTO `inventory_material_category` VALUES (1,'SUBSTRATE','基材',0,1,'/SUBSTRATE',1,'0','admin','2026-08-03 17:19:22','admin','2026-08-03 17:19:22','薄膜开关基材（PET/PC/PI薄膜）'),(2,'CONDUCTIVE','导电材料',0,1,'/CONDUCTIVE',2,'0','admin','2026-08-03 17:19:22','admin','2026-08-03 17:19:22','银浆/铜箔/导电油墨'),(3,'ADHESIVE','胶粘材料',0,1,'/ADHESIVE',3,'0','admin','2026-08-03 17:19:22','admin','2026-08-03 17:19:22','双面胶/压敏胶/导电胶'),(4,'SPACER','隔离材料',0,1,'/SPACER',4,'0','admin','2026-08-03 17:19:22','admin','2026-08-03 17:19:22','隔离膜/间隔胶'),(5,'PANEL','面板材料',0,1,'/PANEL',5,'0','admin','2026-08-03 17:19:22','admin','2026-08-03 17:19:22','PC/PET面板'),(6,'CONNECTOR','连接器',0,1,'/CONNECTOR',6,'0','admin','2026-08-03 17:19:22','admin','2026-08-03 17:19:22','FPC连接器/排线'),(7,'AUX','辅料包装',0,1,'/AUX',7,'0','admin','2026-08-03 17:19:22','admin','2026-08-03 17:19:22','保护膜/定位胶带/包装'),(15,'PET_FILM','PET薄膜',1,2,'/SUBSTRATE/PET_FILM',1,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','PET基膜'),(16,'PC_FILM','PC薄膜',1,2,'/SUBSTRATE/PC_FILM',2,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','PC基膜'),(17,'PI_FILM','PI薄膜',1,2,'/SUBSTRATE/PI_FILM',3,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','聚酰亚胺薄膜'),(18,'SILVER_PASTE','导电银浆',2,2,'/CONDUCTIVE/SILVER_PASTE',1,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','丝印导电银浆'),(19,'COPPER_FOIL','铜箔',2,2,'/CONDUCTIVE/COPPER_FOIL',2,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','压延/电解铜箔'),(20,'CONDUCTIVE_INK','导电油墨',2,2,'/CONDUCTIVE/CONDUCTIVE_INK',3,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','碳浆等导电油墨'),(21,'DOUBLE_TAPE','双面胶带',3,2,'/ADHESIVE/DOUBLE_TAPE',1,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','3M等双面胶'),(22,'PSA','压敏胶',3,2,'/ADHESIVE/PSA',2,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','压敏胶带/胶膜'),(23,'ACF','导电胶',3,2,'/ADHESIVE/ACF',3,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','ACF各向异性导电胶'),(24,'SPACER_FILM','隔离膜',4,2,'/SPACER/SPACER_FILM',1,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','线路隔离'),(25,'SPACER_ADHESIVE','间隔胶',4,2,'/SPACER/SPACER_ADHESIVE',2,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','层间隔胶'),(26,'PC_PANEL','PC面板',5,2,'/PANEL/PC_PANEL',1,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','PC面板材料'),(27,'PET_PANEL','PET面板',5,2,'/PANEL/PET_PANEL',2,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','PET面板材料'),(28,'FPC_CONN','FPC连接器',6,2,'/CONNECTOR/FPC_CONN',1,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','FPC连接器/排线'),(29,'PROTECT_FILM','保护膜',7,2,'/AUX/PROTECT_FILM',1,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','PE/PET保护膜'),(30,'PACKING','包装材料',7,2,'/AUX/PACKING',2,'0','admin','2026-08-03 17:19:51','admin','2026-08-03 17:19:51','包装袋/盒/箱');
/*!40000 ALTER TABLE `inventory_material_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_outbound_item`
--

DROP TABLE IF EXISTS `inventory_outbound_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_outbound_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `outbound_id` bigint NOT NULL COMMENT '出库单ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码（冗余）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称（冗余）',
  `specification` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PCS' COMMENT '单位',
  `quantity` decimal(12,4) NOT NULL COMMENT '出库数量',
  `unit_price` decimal(12,4) DEFAULT NULL COMMENT '出库单价',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '金额',
  `batch_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `location_id` bigint DEFAULT NULL COMMENT '出库库位',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`item_id`),
  KEY `idx_outbound_id` (`outbound_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_batch_no` (`batch_no`),
  CONSTRAINT `fk_outbound_item_material` FOREIGN KEY (`material_id`) REFERENCES `inventory_material` (`material_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_outbound_item_order` FOREIGN KEY (`outbound_id`) REFERENCES `inventory_outbound_order` (`outbound_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出库单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_outbound_item`
--

LOCK TABLES `inventory_outbound_item` WRITE;
/*!40000 ALTER TABLE `inventory_outbound_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_outbound_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_outbound_order`
--

DROP TABLE IF EXISTS `inventory_outbound_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_outbound_order` (
  `outbound_id` bigint NOT NULL AUTO_INCREMENT COMMENT '出库单ID',
  `outbound_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出库单号，格式：OUT+YYYYMMDD+流水号',
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '链路追踪ID',
  `outbound_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出库类型：production生产领料/sales销售出库/return退货出库/scrap报废出库/transfer调拨出库/adjust盘亏出库',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源类型：work_order/sales_order/purchase_return',
  `source_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `source_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单号',
  `warehouse_id` bigint NOT NULL COMMENT '出库仓库ID',
  `customer_id` bigint DEFAULT NULL COMMENT '客户ID（销售出库时使用）',
  `customer_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户名称',
  `outbound_date` date NOT NULL COMMENT '出库日期',
  `total_quantity` decimal(12,4) DEFAULT '0.0000' COMMENT '总数量',
  `total_amount` decimal(12,2) DEFAULT '0.00' COMMENT '总金额',
  `order_status` tinyint NOT NULL DEFAULT '0' COMMENT '单据状态: 0草稿/1待审批/2已批准/3已驳回/4处理中/5已确认/6已出库/7已入库/8已关闭/9已取消',
  `approve_status` tinyint NOT NULL DEFAULT '1' COMMENT '审批状态: 1待审批/2已批准/3已驳回',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `approver_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人姓名',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `approve_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批意见',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`outbound_id`),
  UNIQUE KEY `uk_outbound_no` (`outbound_no`),
  KEY `idx_source` (`source_type`,`source_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_outbound_date` (`outbound_date`),
  KEY `idx_order_status` (`order_status`),
  CONSTRAINT `fk_outbound_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `inventory_warehouse` (`warehouse_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出库单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_outbound_order`
--

LOCK TABLES `inventory_outbound_order` WRITE;
/*!40000 ALTER TABLE `inventory_outbound_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_outbound_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_stock`
--

DROP TABLE IF EXISTS `inventory_stock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_stock` (
  `stock_id` bigint NOT NULL AUTO_INCREMENT COMMENT '汇总记录ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码（冗余）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称（冗余）',
  `total_quantity` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '总库存数量',
  `total_reserved` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '总预留数量',
  `available_quantity` decimal(12,4) GENERATED ALWAYS AS ((`total_quantity` - `total_reserved`)) STORED COMMENT '可用数量',
  `earliest_expiry` date DEFAULT NULL COMMENT '当前最早有效期（来自最早批次的 expiry_date）',
  `location_id` bigint DEFAULT NULL COMMENT '当前最早批次所在的库位ID',
  `last_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`stock_id`),
  UNIQUE KEY `uk_material` (`material_id`),
  KEY `location_id` (`location_id`),
  CONSTRAINT `inventory_stock_ibfk_1` FOREIGN KEY (`material_id`) REFERENCES `inventory_material` (`material_id`),
  CONSTRAINT `inventory_stock_ibfk_2` FOREIGN KEY (`location_id`) REFERENCES `inventory_storage_location` (`location_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存汇总表（按物料汇总，动态反映最早批次的库位和有效期）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_stock`
--

LOCK TABLES `inventory_stock` WRITE;
/*!40000 ALTER TABLE `inventory_stock` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_stock` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_stock_item`
--

DROP TABLE IF EXISTS `inventory_stock_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_stock_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码（冗余）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称（冗余）',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `location_id` bigint DEFAULT NULL COMMENT '库位ID',
  `batch_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '批次号',
  `production_date` date DEFAULT NULL COMMENT '生产日期',
  `expiry_date` date DEFAULT NULL COMMENT '有效期至',
  `quantity` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '该批次数量',
  `reserved_quantity` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '该批次预留数量',
  `unit_cost` decimal(12,4) DEFAULT NULL COMMENT '该批次单位成本',
  `status` tinyint(1) DEFAULT '1' COMMENT '库存状态',
  `last_inbound_time` datetime DEFAULT NULL COMMENT '最后入库时间',
  `last_outbound_time` datetime DEFAULT NULL COMMENT '最后出库时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`item_id`),
  UNIQUE KEY `uk_material_warehouse_location_batch` (`material_id`,`warehouse_id`,`location_id`,`batch_no`),
  KEY `idx_material_expiry` (`material_id`,`expiry_date`,`status`),
  KEY `idx_material_status` (`material_id`,`status`),
  KEY `warehouse_id` (`warehouse_id`),
  KEY `location_id` (`location_id`),
  CONSTRAINT `inventory_stock_item_ibfk_1` FOREIGN KEY (`material_id`) REFERENCES `inventory_material` (`material_id`),
  CONSTRAINT `inventory_stock_item_ibfk_2` FOREIGN KEY (`warehouse_id`) REFERENCES `inventory_warehouse` (`warehouse_id`),
  CONSTRAINT `inventory_stock_item_ibfk_3` FOREIGN KEY (`location_id`) REFERENCES `inventory_storage_location` (`location_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存批次明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_stock_item`
--

LOCK TABLES `inventory_stock_item` WRITE;
/*!40000 ALTER TABLE `inventory_stock_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_stock_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_stocktake_item`
--

DROP TABLE IF EXISTS `inventory_stocktake_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_stocktake_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `stocktake_id` bigint NOT NULL COMMENT '盘点单ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码（冗余）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称（冗余）',
  `batch_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `location_id` bigint DEFAULT NULL COMMENT '库位ID',
  `system_quantity` decimal(12,4) NOT NULL COMMENT '系统账面数量',
  `actual_quantity` decimal(12,4) DEFAULT NULL COMMENT '实际盘点数量',
  `diff_quantity` decimal(12,4) GENERATED ALWAYS AS ((`actual_quantity` - `system_quantity`)) STORED COMMENT '差异数量',
  `unit_cost` decimal(12,4) DEFAULT NULL COMMENT '单位成本',
  `diff_amount` decimal(12,2) GENERATED ALWAYS AS ((`diff_quantity` * `unit_cost`)) STORED COMMENT '差异金额',
  `adjust_status` tinyint NOT NULL DEFAULT '0' COMMENT '调整状态: 0待处理/1已处理',
  `adjust_order_id` bigint DEFAULT NULL COMMENT '生成的调整单ID',
  `reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '差异原因',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`item_id`),
  KEY `idx_stocktake_id` (`stocktake_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_batch_no` (`batch_no`),
  CONSTRAINT `fk_stocktake_item_order` FOREIGN KEY (`stocktake_id`) REFERENCES `inventory_stocktake_order` (`stocktake_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_stocktake_item`
--

LOCK TABLES `inventory_stocktake_item` WRITE;
/*!40000 ALTER TABLE `inventory_stocktake_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_stocktake_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_stocktake_order`
--

DROP TABLE IF EXISTS `inventory_stocktake_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_stocktake_order` (
  `stocktake_id` bigint NOT NULL AUTO_INCREMENT COMMENT '盘点单ID',
  `stocktake_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '盘点单号，格式：ST+YYYYMMDD+流水号',
  `stocktake_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'full' COMMENT '盘点类型：full全盘/partial抽盘/cycle循环盘点',
  `warehouse_id` bigint NOT NULL COMMENT '盘点仓库ID',
  `location_ids` json DEFAULT NULL COMMENT '盘点库位范围，JSON数组',
  `material_ids` json DEFAULT NULL COMMENT '盘点物料范围，JSON数组',
  `plan_start_time` datetime DEFAULT NULL COMMENT '计划开始时间',
  `plan_end_time` datetime DEFAULT NULL COMMENT '计划结束时间',
  `actual_start_time` datetime DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_time` datetime DEFAULT NULL COMMENT '实际结束时间',
  `stocktaker_id` bigint DEFAULT NULL COMMENT '盘点人ID',
  `stocktaker_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '盘点人姓名',
  `supervisor_id` bigint DEFAULT NULL COMMENT '监盘人ID',
  `supervisor_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '监盘人姓名',
  `total_system_quantity` decimal(12,4) DEFAULT '0.0000' COMMENT '系统总数量',
  `total_actual_quantity` decimal(12,4) DEFAULT '0.0000' COMMENT '实盘总数量',
  `total_diff_quantity` decimal(12,4) DEFAULT '0.0000' COMMENT '总差异数量',
  `total_diff_amount` decimal(12,2) DEFAULT '0.00' COMMENT '总差异金额',
  `order_status` tinyint NOT NULL DEFAULT '0' COMMENT '单据状态: 0草稿/4处理中/5已确认/11已处理/8已关闭',
  `approve_status` tinyint NOT NULL DEFAULT '1' COMMENT '审批状态: 1待审批/2已批准/3已驳回',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `approver_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人姓名',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `approve_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批意见',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`stocktake_id`),
  UNIQUE KEY `uk_stocktake_no` (`stocktake_no`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_order_status` (`order_status`),
  CONSTRAINT `fk_stocktake_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `inventory_warehouse` (`warehouse_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盘点单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_stocktake_order`
--

LOCK TABLES `inventory_stocktake_order` WRITE;
/*!40000 ALTER TABLE `inventory_stocktake_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_stocktake_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_storage_location`
--

DROP TABLE IF EXISTS `inventory_storage_location`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_storage_location` (
  `location_id` bigint NOT NULL AUTO_INCREMENT COMMENT '库位ID',
  `warehouse_id` bigint NOT NULL COMMENT '所属仓库ID',
  `location_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库位编码',
  `location_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '库位名称',
  `location_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'normal' COMMENT '库位类型：normal普通/frozen冷冻/flammable易燃/valuable贵重',
  `capacity` decimal(12,2) DEFAULT NULL COMMENT '最大容量（按基本单位）',
  `used_capacity` decimal(12,2) DEFAULT '0.00' COMMENT '已使用容量',
  `width` decimal(10,2) DEFAULT NULL COMMENT '宽度(cm)',
  `height` decimal(10,2) DEFAULT NULL COMMENT '高度(cm)',
  `depth` decimal(10,2) DEFAULT NULL COMMENT '深度(cm)',
  `sort_order` int DEFAULT '0' COMMENT '排序序号',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态：0正常 1停用',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`location_id`),
  UNIQUE KEY `uk_location_code` (`location_code`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_location_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `inventory_warehouse` (`warehouse_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库位表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_storage_location`
--

LOCK TABLES `inventory_storage_location` WRITE;
/*!40000 ALTER TABLE `inventory_storage_location` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_storage_location` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_transaction`
--

DROP TABLE IF EXISTS `inventory_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_transaction` (
  `transaction_id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码（冗余）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称（冗余）',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `location_id` bigint DEFAULT NULL COMMENT '库位ID',
  `transaction_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '交易类型：inbound入库/outbound出库/transfer_in调拨入库/transfer_out调拨出库/adjust盘盈盘亏',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源类型：purchase_order/work_order/sales_order/stocktake',
  `source_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `source_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单号',
  `batch_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `quantity` decimal(12,4) NOT NULL COMMENT '变动数量（正数增加，负数减少）',
  `before_quantity` decimal(12,4) NOT NULL COMMENT '变动前数量',
  `after_quantity` decimal(12,4) NOT NULL COMMENT '变动后数量',
  `unit_cost` decimal(12,4) DEFAULT NULL COMMENT '单位成本',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '变动金额',
  `transaction_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '交易时间',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人姓名',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`transaction_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_source` (`source_type`,`source_id`),
  KEY `idx_transaction_time` (`transaction_time`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_transaction_type` (`transaction_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_transaction`
--

LOCK TABLES `inventory_transaction` WRITE;
/*!40000 ALTER TABLE `inventory_transaction` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_transaction` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_transfer_item`
--

DROP TABLE IF EXISTS `inventory_transfer_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_transfer_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `transfer_id` bigint NOT NULL COMMENT '调拨单ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码（冗余）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称（冗余）',
  `specification` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PCS' COMMENT '单位',
  `quantity` decimal(12,4) NOT NULL COMMENT '调拨数量',
  `unit_cost` decimal(12,4) DEFAULT NULL COMMENT '单位成本',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '金额',
  `batch_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `from_location_id` bigint DEFAULT NULL COMMENT '实际出库库位',
  `to_location_id` bigint DEFAULT NULL COMMENT '实际入库库位',
  `out_quantity` decimal(12,4) DEFAULT '0.0000' COMMENT '已出库数量',
  `in_quantity` decimal(12,4) DEFAULT '0.0000' COMMENT '已入库数量',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '明细状态: 0待调拨/1已完成',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`item_id`),
  KEY `idx_transfer_id` (`transfer_id`),
  KEY `idx_material_id` (`material_id`),
  CONSTRAINT `fk_transfer_item_order` FOREIGN KEY (`transfer_id`) REFERENCES `inventory_transfer_order` (`transfer_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调拨单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_transfer_item`
--

LOCK TABLES `inventory_transfer_item` WRITE;
/*!40000 ALTER TABLE `inventory_transfer_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_transfer_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_transfer_order`
--

DROP TABLE IF EXISTS `inventory_transfer_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_transfer_order` (
  `transfer_id` bigint NOT NULL AUTO_INCREMENT COMMENT '调拨单ID',
  `transfer_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '调拨单号，格式：TR+YYYYMMDD+流水号',
  `transfer_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'normal' COMMENT '调拨类型：normal普通调拨/urgent紧急调拨',
  `from_warehouse_id` bigint NOT NULL COMMENT '调出仓库ID',
  `from_location_id` bigint DEFAULT NULL COMMENT '调出库位ID',
  `to_warehouse_id` bigint NOT NULL COMMENT '调入仓库ID',
  `to_location_id` bigint DEFAULT NULL COMMENT '调入库位ID',
  `transfer_date` date NOT NULL COMMENT '调拨日期',
  `expected_date` date DEFAULT NULL COMMENT '预计到达日期',
  `actual_date` date DEFAULT NULL COMMENT '实际到达日期',
  `total_quantity` decimal(12,4) DEFAULT '0.0000' COMMENT '调拨总数量',
  `total_amount` decimal(12,2) DEFAULT '0.00' COMMENT '调拨总金额',
  `order_status` tinyint NOT NULL DEFAULT '0' COMMENT '单据状态: 0草稿/1待审批/2已批准/3已驳回/6已出库/10已完成/12调拨中/9已取消',
  `approve_status` tinyint NOT NULL DEFAULT '1' COMMENT '审批状态: 1待审批/2已批准/3已驳回',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `approver_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人姓名',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `approve_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批意见',
  `out_operator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '出库操作人',
  `out_time` datetime DEFAULT NULL COMMENT '出库时间',
  `in_operator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '入库操作人',
  `in_time` datetime DEFAULT NULL COMMENT '入库时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`transfer_id`),
  UNIQUE KEY `uk_transfer_no` (`transfer_no`),
  KEY `idx_from_warehouse` (`from_warehouse_id`),
  KEY `idx_to_warehouse` (`to_warehouse_id`),
  KEY `idx_order_status` (`order_status`),
  CONSTRAINT `fk_transfer_from_warehouse` FOREIGN KEY (`from_warehouse_id`) REFERENCES `inventory_warehouse` (`warehouse_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_transfer_to_warehouse` FOREIGN KEY (`to_warehouse_id`) REFERENCES `inventory_warehouse` (`warehouse_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调拨单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_transfer_order`
--

LOCK TABLES `inventory_transfer_order` WRITE;
/*!40000 ALTER TABLE `inventory_transfer_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_transfer_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `inventory_warehouse`
--

DROP TABLE IF EXISTS `inventory_warehouse`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_warehouse` (
  `warehouse_id` bigint NOT NULL AUTO_INCREMENT COMMENT '仓库ID',
  `warehouse_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓库名称',
  `warehouse_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'normal' COMMENT '仓库类型：normal普通仓库/quality质检仓库/finished成品仓库/scrap废品仓库',
  `location` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '仓库位置描述',
  `manager` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '仓库负责人',
  `contact_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `sort_order` int DEFAULT '0' COMMENT '排序序号',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态：0正常 1停用',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`warehouse_id`),
  UNIQUE KEY `uk_warehouse_code` (`warehouse_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓库表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_warehouse`
--

LOCK TABLES `inventory_warehouse` WRITE;
/*!40000 ALTER TABLE `inventory_warehouse` DISABLE KEYS */;
INSERT INTO `inventory_warehouse` VALUES (1,'WH-01','原料仓','material',NULL,NULL,NULL,0,'1',NULL,'2026-08-04 11:56:13',NULL,'2026-08-04 11:56:13',NULL);
/*!40000 ALTER TABLE `inventory_warehouse` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `portal_language_config`
--

DROP TABLE IF EXISTS `portal_language_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `portal_language_config` (
  `config_id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `language_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '语言代码',
  `language_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '语言名称',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `idx_language_code` (`language_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多语言配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `portal_language_config`
--

LOCK TABLES `portal_language_config` WRITE;
/*!40000 ALTER TABLE `portal_language_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `portal_language_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `portal_page_content`
--

DROP TABLE IF EXISTS `portal_page_content`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `portal_page_content` (
  `content_id` bigint NOT NULL AUTO_INCREMENT COMMENT '内容ID',
  `page_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '页面代码',
  `page_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '页面名称',
  `language_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '语言代码',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '内容',
  `meta_keywords` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '关键词',
  `meta_description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '描述',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`content_id`),
  UNIQUE KEY `idx_page_language` (`page_code`,`language_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面内容表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `portal_page_content`
--

LOCK TABLES `portal_page_content` WRITE;
/*!40000 ALTER TABLE `portal_page_content` DISABLE KEYS */;
/*!40000 ALTER TABLE `portal_page_content` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `portal_product_display`
--

DROP TABLE IF EXISTS `portal_product_display`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `portal_product_display` (
  `display_id` bigint NOT NULL AUTO_INCREMENT COMMENT '展示ID',
  `product_id` bigint NOT NULL COMMENT '产品ID',
  `language_code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '语言代码',
  `display_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '展示名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '描述',
  `features` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '特点',
  `applications` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '应用领域',
  `specifications` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '规格参数',
  `image_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '图片地址',
  `display_order` int DEFAULT '0' COMMENT '显示顺序',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`display_id`),
  UNIQUE KEY `idx_product_language` (`product_id`,`language_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品展示表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `portal_product_display`
--

LOCK TABLES `portal_product_display` WRITE;
/*!40000 ALTER TABLE `portal_product_display` DISABLE KEYS */;
/*!40000 ALTER TABLE `portal_product_display` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product`
--

DROP TABLE IF EXISTS `product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product` (
  `product_id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品ID',
  `product_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品编码',
  `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品名称',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `product_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'standard' COMMENT '类型：standard标准/custom定制',
  `spec_json` json DEFAULT NULL COMMENT '规格参数',
  `base_price` decimal(12,2) DEFAULT NULL COMMENT '基础售价',
  `cost_price` decimal(12,2) DEFAULT NULL COMMENT '标准成本',
  `min_order_qty` int DEFAULT '1' COMMENT '最小起订量',
  `lead_time` int DEFAULT '15' COMMENT '标准交期(天)',
  `product_status` bigint NOT NULL DEFAULT '1' COMMENT '状态',
  `current_bom_id` bigint DEFAULT NULL COMMENT '当前BOM ID',
  `current_route_id` bigint DEFAULT NULL COMMENT '当前工艺路线ID',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位',
  `approve_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '审核批注',
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `uk_product_code` (`product_code`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_product_status` (`product_status`),
  KEY `idx_product_name` (`product_name`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product`
--

LOCK TABLES `product` WRITE;
/*!40000 ALTER TABLE `product` DISABLE KEYS */;
INSERT INTO `product` VALUES (1,'QT2608040001','QT2608040001',NULL,'standard',NULL,NULL,NULL,1,15,6,1,1,'xiaoshou0','2026-08-04 16:07:19','xiaoshou0','2026-08-04 17:13:47',NULL,'PCS','');
/*!40000 ALTER TABLE `product` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_category`
--

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_category`
--

LOCK TABLES `product_category` WRITE;
/*!40000 ALTER TABLE `product_category` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_config_model`
--

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

--
-- Dumping data for table `product_config_model`
--

LOCK TABLES `product_config_model` WRITE;
/*!40000 ALTER TABLE `product_config_model` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_config_model` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_config_option`
--

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

--
-- Dumping data for table `product_config_option`
--

LOCK TABLES `product_config_option` WRITE;
/*!40000 ALTER TABLE `product_config_option` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_config_option` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_instance`
--

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

--
-- Dumping data for table `product_instance`
--

LOCK TABLES `product_instance` WRITE;
/*!40000 ALTER TABLE `product_instance` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_instance` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `production_equipment`
--

DROP TABLE IF EXISTS `production_equipment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_equipment` (
  `equipment_id` bigint NOT NULL AUTO_INCREMENT COMMENT '设备ID',
  `equipment_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设备编号',
  `equipment_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设备名称',
  `equipment_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备类型',
  `model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '型号规格',
  `department` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属部门',
  `location` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '安装位置',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '设备状态: 0待机/1运行中/2维护中/3故障中',
  `utilization` decimal(5,2) DEFAULT '0.00' COMMENT '利用率(%)',
  `last_maintenance` datetime DEFAULT NULL COMMENT '上次维护时间',
  `next_maintenance` datetime DEFAULT NULL COMMENT '下次维护时间',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '0',
  `create_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`equipment_id`),
  UNIQUE KEY `uk_equipment_no` (`equipment_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备管理';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_equipment`
--

LOCK TABLES `production_equipment` WRITE;
/*!40000 ALTER TABLE `production_equipment` DISABLE KEYS */;
/*!40000 ALTER TABLE `production_equipment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `production_operation_execution`
--

DROP TABLE IF EXISTS `production_operation_execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_operation_execution` (
  `execution_id` bigint NOT NULL AUTO_INCREMENT COMMENT '执行ID',
  `order_id` bigint NOT NULL COMMENT '生产订单ID',
  `process_id` bigint NOT NULL COMMENT '标准工序ID',
  `process_order` int NOT NULL COMMENT '工序顺序',
  `planned_start_time` datetime DEFAULT NULL COMMENT '计划开始时间',
  `planned_end_time` datetime DEFAULT NULL COMMENT '计划结束时间',
  `actual_start_time` datetime DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_time` datetime DEFAULT NULL COMMENT '实际结束时间',
  `actual_labor_hours` decimal(10,2) DEFAULT '0.00' COMMENT '实际人工工时',
  `actual_machine_hours` decimal(10,2) DEFAULT '0.00' COMMENT '实际机器工时',
  `equipment_id` bigint DEFAULT NULL COMMENT '使用设备ID',
  `equipment_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备编号',
  `equipment_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备名称',
  `operator_id` bigint DEFAULT NULL COMMENT '操作员ID',
  `operator_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作员姓名',
  `input_quantity` decimal(18,4) DEFAULT '0.0000' COMMENT '投入数量',
  `output_quantity` decimal(18,4) DEFAULT '0.0000' COMMENT '产出数量',
  `qualified_quantity` decimal(18,4) DEFAULT '0.0000' COMMENT '合格数量',
  `defective_quantity` decimal(18,4) DEFAULT '0.0000' COMMENT '不良数量',
  `defective_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '不良原因',
  `actual_process_params` json DEFAULT NULL COMMENT '实际工艺参数（JSON格式）',
  `quality_check_result` json DEFAULT NULL COMMENT '质量检查结果（JSON格式）',
  `execution_status` tinyint DEFAULT '0' COMMENT '执行状态: 0待执行/1准备中/2执行中/3已暂停/4已完成/5已跳过/6已取消/7已超期/8异常中/9待确认',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`execution_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_process_id` (`process_id`),
  KEY `idx_execution_status` (`execution_status`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_equipment_id` (`equipment_id`),
  KEY `idx_planned_time` (`planned_start_time`,`planned_end_time`),
  KEY `idx_actual_time` (`actual_start_time`,`actual_end_time`),
  KEY `idx_execution_order_status` (`order_id`,`execution_status`,`process_order`),
  KEY `idx_execution_operator_time` (`operator_id`,`actual_start_time`,`actual_end_time`),
  KEY `idx_execution_equipment_time` (`equipment_id`,`actual_start_time`,`actual_end_time`),
  CONSTRAINT `fk_execution_order` FOREIGN KEY (`order_id`) REFERENCES `production_order` (`order_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_execution_process` FOREIGN KEY (`process_id`) REFERENCES `engineering_standard_process` (`process_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产工序执行表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_operation_execution`
--

LOCK TABLES `production_operation_execution` WRITE;
/*!40000 ALTER TABLE `production_operation_execution` DISABLE KEYS */;
/*!40000 ALTER TABLE `production_operation_execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `production_operation_record`
--

DROP TABLE IF EXISTS `production_operation_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_operation_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `execution_id` bigint NOT NULL COMMENT '工序执行ID',
  `record_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录类型：START开始/PAUSE暂停/RESUME恢复/COMPLETE完成/QUALITY质量检查/ISSUE问题记录/PARAM参数调整/STATUS状态变更',
  `record_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  `operator_id` bigint DEFAULT NULL COMMENT '操作员ID',
  `operator_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作员姓名',
  `quantity` decimal(18,4) DEFAULT NULL COMMENT '数量',
  `parameters` json DEFAULT NULL COMMENT '参数（JSON格式）',
  `quality_data` json DEFAULT NULL COMMENT '质量数据（JSON格式）',
  `issue_description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '问题描述',
  `issue_solution` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '解决方案',
  `attachment_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '附件URL',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`record_id`),
  KEY `idx_execution_id` (`execution_id`),
  KEY `idx_record_type` (`record_type`),
  KEY `idx_record_time` (`record_time`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_record_execution_type` (`execution_id`,`record_type`,`record_time`),
  CONSTRAINT `fk_record_execution` FOREIGN KEY (`execution_id`) REFERENCES `production_operation_execution` (`execution_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产工序记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_operation_record`
--

LOCK TABLES `production_operation_record` WRITE;
/*!40000 ALTER TABLE `production_operation_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `production_operation_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `production_order`
--

DROP TABLE IF EXISTS `production_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_order` (
  `order_id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '链路追踪ID',
  `order_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单编号',
  `order_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单类型：PLAN生产计划/WORK_ORDER生产工单',
  `parent_order_id` bigint DEFAULT NULL COMMENT '父订单ID（计划生成工单时使用）',
  `sales_order_id` bigint DEFAULT NULL COMMENT '销售订单ID',
  `sales_order_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '销售订单编号',
  `product_id` bigint NOT NULL COMMENT '产品ID',
  `product_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品编码',
  `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品名称',
  `product_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产品规格',
  `product_unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PCS' COMMENT '产品单位',
  `bom_id` bigint DEFAULT NULL COMMENT '创建时使用的BOM ID',
  `bom_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建时使用的BOM编码',
  `routing_id` bigint DEFAULT NULL COMMENT '使用的工艺路线ID',
  `routing_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工艺路线编码',
  `planned_quantity` decimal(18,4) NOT NULL COMMENT '计划数量',
  `completed_quantity` decimal(18,4) DEFAULT '0.0000' COMMENT '已完成数量',
  `remaining_quantity` decimal(18,4) DEFAULT '0.0000' COMMENT '剩余数量',
  `plan_start_date` date NOT NULL COMMENT '计划开始日期',
  `plan_end_date` date NOT NULL COMMENT '计划结束日期',
  `actual_start_time` datetime DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_time` datetime DEFAULT NULL COMMENT '实际结束时间',
  `order_status` tinyint DEFAULT '0' COMMENT '订单状态: 0草稿/1待审核/2已审核/3已驳回/4已计划/5待开始/6进行中/7已暂停/8已完成/9已取消/10已关闭/11已超期',
  `approval_status` tinyint DEFAULT '0' COMMENT '审批状态: 0草稿/1待审批/2已批准/3已驳回/4已取消',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `approver_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人姓名',
  `approval_time` datetime DEFAULT NULL COMMENT '审批时间',
  `approval_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批备注',
  `priority` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '优先级：LOW低/MEDIUM中/HIGH高/URGENT紧急',
  `department_id` bigint DEFAULT NULL COMMENT '生产部门ID',
  `department_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生产部门名称',
  `material_cost` decimal(18,4) DEFAULT '0.0000' COMMENT '材料成本',
  `labor_cost` decimal(18,4) DEFAULT '0.0000' COMMENT '人工成本',
  `total_cost` decimal(18,4) DEFAULT '0.0000' COMMENT '总成本',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `material_status` tinyint DEFAULT '0' COMMENT '领料状态:0未领料/1待发料/2已领料',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_order_type` (`order_type`),
  KEY `idx_order_status` (`order_status`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_plan_date` (`plan_start_date`,`plan_end_date`),
  KEY `idx_priority` (`priority`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_parent_order` (`parent_order_id`),
  KEY `idx_sales_order` (`sales_order_id`),
  KEY `idx_production_order_type_status` (`order_type`,`order_status`,`plan_start_date`),
  KEY `idx_production_order_priority_date` (`priority`,`plan_start_date`,`plan_end_date`),
  KEY `idx_production_order_department` (`department_id`,`order_status`,`create_time`),
  CONSTRAINT `fk_order_parent` FOREIGN KEY (`parent_order_id`) REFERENCES `production_order` (`order_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产订单表（合并计划和工单）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_order`
--

LOCK TABLES `production_order` WRITE;
/*!40000 ALTER TABLE `production_order` DISABLE KEYS */;
INSERT INTO `production_order` VALUES (1,NULL,'WO2608040001','WORK_ORDER',NULL,2,'SO2608040001',1,'QT2608040001','QT2608040001','','PCS',NULL,NULL,1,NULL,2.0000,0.0000,0.0000,'2026-08-04','2026-09-03',NULL,NULL,0,0,NULL,NULL,NULL,NULL,'MEDIUM',NULL,NULL,0.0000,0.0000,0.0000,'admin','2026-08-04 17:43:29',NULL,'2026-08-04 17:43:28','由销售订单[SO2608040001]自动生成',0);
/*!40000 ALTER TABLE `production_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `production_quality_inspection`
--

DROP TABLE IF EXISTS `production_quality_inspection`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_quality_inspection` (
  `inspection_id` bigint NOT NULL AUTO_INCREMENT COMMENT '检验ID',
  `inspection_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验单号',
  `inspection_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验类型: IQC-来料检, IPQC-过程检, OQC-成品检',
  `order_id` bigint DEFAULT NULL COMMENT '关联工单ID',
  `material_id` bigint DEFAULT NULL COMMENT '关联物料ID',
  `product_id` bigint DEFAULT NULL COMMENT '关联产品ID',
  `inspector` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检验员',
  `inspect_time` datetime DEFAULT NULL COMMENT '检验时间',
  `result` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '结果: pending-待检, pass-合格, fail-不合格',
  `total_qty` int DEFAULT '0' COMMENT '检验总数',
  `pass_qty` int DEFAULT '0' COMMENT '合格数',
  `fail_qty` int DEFAULT '0' COMMENT '不合格数',
  `defect_desc` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '缺陷描述',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) COLLATE utf8mb4_unicode_ci DEFAULT '0',
  `create_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`inspection_id`),
  UNIQUE KEY `uk_inspection_no` (`inspection_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_type` (`inspection_type`),
  KEY `idx_result` (`result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质量检验单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_quality_inspection`
--

LOCK TABLES `production_quality_inspection` WRITE;
/*!40000 ALTER TABLE `production_quality_inspection` DISABLE KEYS */;
/*!40000 ALTER TABLE `production_quality_inspection` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `production_quality_inspection_item`
--

DROP TABLE IF EXISTS `production_quality_inspection_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_quality_inspection_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '项ID',
  `inspection_id` bigint NOT NULL COMMENT '关联检验单ID',
  `check_item` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验项目',
  `standard` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标准值',
  `actual_value` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实测值',
  `result` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '结果: pass-合格, fail-不合格',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`item_id`),
  KEY `idx_inspection_id` (`inspection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质量检验项明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_quality_inspection_item`
--

LOCK TABLES `production_quality_inspection_item` WRITE;
/*!40000 ALTER TABLE `production_quality_inspection_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `production_quality_inspection_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `production_trace_log`
--

DROP TABLE IF EXISTS `production_trace_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_trace_log` (
  `trace_id` bigint NOT NULL AUTO_INCREMENT COMMENT '追溯ID',
  `trace_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '追溯类型: MATERIAL-原料追溯, ORDER-工单追溯, PRODUCT-产品追溯',
  `trace_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '追溯编码（物料编码/工单号/产品编码）',
  `batch_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `order_id` bigint DEFAULT NULL COMMENT '关联工单ID',
  `product_id` bigint DEFAULT NULL COMMENT '关联产品ID',
  `material_id` bigint DEFAULT NULL COMMENT '关联物料ID',
  `operation` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作: inbound-入库, outbound-出库, start-开工, complete-完工, inspect-质检',
  `operator` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人',
  `operate_time` datetime DEFAULT NULL COMMENT '操作时间',
  `detail` json DEFAULT NULL COMMENT '操作详情（JSON）',
  `create_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`trace_id`),
  KEY `idx_trace_code` (`trace_code`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产追溯日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_trace_log`
--

LOCK TABLES `production_trace_log` WRITE;
/*!40000 ALTER TABLE `production_trace_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `production_trace_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `purchase_document`
--

DROP TABLE IF EXISTS `purchase_document`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_document` (
  `document_id` bigint NOT NULL AUTO_INCREMENT COMMENT '票据ID',
  `document_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '票据编号',
  `document_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '票据类型（invoice发票/receipt收据/contract合同/quotation报价单/delivery_note送货单/other其他）',
  `order_id` bigint NOT NULL COMMENT '关联的采购订单',
  `supplier_id` bigint NOT NULL COMMENT '供应商ID',
  `document_date` date NOT NULL COMMENT '票据日期',
  `document_amount` decimal(12,2) NOT NULL COMMENT '票据金额',
  `currency` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'CNY' COMMENT '币种',
  `document_status` tinyint NOT NULL DEFAULT '0' COMMENT '单据状态: 0待处理/1已核验/2已归档',
  `verification_date` date DEFAULT NULL COMMENT '核验日期',
  `file_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '文件名称',
  `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '文件URL',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`document_id`),
  UNIQUE KEY `uk_document_no` (`document_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_document_type` (`document_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购票据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `purchase_document`
--

LOCK TABLES `purchase_document` WRITE;
/*!40000 ALTER TABLE `purchase_document` DISABLE KEYS */;
/*!40000 ALTER TABLE `purchase_document` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `purchase_material_inquiry`
--

DROP TABLE IF EXISTS `purchase_material_inquiry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_material_inquiry` (
  `inquiry_id` bigint NOT NULL AUTO_INCREMENT COMMENT '询价ID',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码',
  `material_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称',
  `material_spec` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位',
  `inquiry_date` date NOT NULL COMMENT '询价日期',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商ID',
  `supplier_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商名称',
  `supplier_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商编码',
  `inquiry_price` decimal(15,2) DEFAULT NULL COMMENT '询价单价',
  `currency` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'CNY' COMMENT '币种',
  `quantity` decimal(15,2) DEFAULT NULL COMMENT '询价数量',
  `delivery_days` int DEFAULT NULL COMMENT '交货天数',
  `payment_terms` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '付款条件',
  `validity_days` int DEFAULT NULL COMMENT '报价有效期（天）',
  `inquiry_person` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '询价人',
  `inquiry_status` tinyint NOT NULL DEFAULT '0' COMMENT '询价状态: 0有效/1无效/2已过期/3已取消/4已完成',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`inquiry_id`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_inquiry_date` (`inquiry_date`),
  KEY `idx_inquiry_status` (`inquiry_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='材料询价表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `purchase_material_inquiry`
--

LOCK TABLES `purchase_material_inquiry` WRITE;
/*!40000 ALTER TABLE `purchase_material_inquiry` DISABLE KEYS */;
/*!40000 ALTER TABLE `purchase_material_inquiry` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `purchase_order`
--

DROP TABLE IF EXISTS `purchase_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_order` (
  `order_id` bigint NOT NULL AUTO_INCREMENT COMMENT '采购订单ID',
  `order_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '采购订单号',
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '链路追踪ID',
  `supplier_id` bigint NOT NULL COMMENT '供应商ID',
  `supplier_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商名称',
  `order_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'normal' COMMENT '订单类型（normal正常/urgent紧急）',
  `order_date` date NOT NULL COMMENT '订单日期',
  `expected_delivery_date` date NOT NULL COMMENT '期望交货日期',
  `actual_delivery_date` date DEFAULT NULL COMMENT '实际交货日期',
  `order_amount` decimal(12,2) DEFAULT '0.00' COMMENT '订单金额（不含税）',
  `order_tax` decimal(12,2) DEFAULT '0.00' COMMENT '订单税额',
  `order_total_amount` decimal(12,2) DEFAULT '0.00' COMMENT '订单含税总金额',
  `currency` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'CNY' COMMENT '币种',
  `approval_status` tinyint DEFAULT '1' COMMENT '审批状态（1草稿/2已取消/3待审批/4已批准/5已拒绝）',
  `receipt_status` tinyint NOT NULL DEFAULT '0' COMMENT '收货状态: 0待收货/1部分收货/2已收货',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `approver_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '审批人姓名',
  `approval_time` datetime DEFAULT NULL COMMENT '审批时间',
  `approval_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '审批意见',
  `payment_status` tinyint NOT NULL DEFAULT '0' COMMENT '付款状态: 0待付款/1部分付款/2已付款',
  `paid_amount` decimal(12,2) DEFAULT '0.00' COMMENT '已付款金额',
  `contract_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '合同编号',
  `delivery_method` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '交货方式',
  `delivery_address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '交货地址',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `urgent_flag` tinyint(1) DEFAULT '0' COMMENT '是否紧急（0否 1是）',
  `urgent_reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '紧急原因',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_order_date` (`order_date`),
  KEY `idx_receipt_status` (`receipt_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `purchase_order`
--

LOCK TABLES `purchase_order` WRITE;
/*!40000 ALTER TABLE `purchase_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `purchase_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `purchase_order_item`
--

DROP TABLE IF EXISTS `purchase_order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_order_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_id` bigint NOT NULL COMMENT '采购订单ID',
  `material_id` bigint NOT NULL COMMENT '物料ID',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称',
  `material_spec` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '物料规格',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PCS' COMMENT '单位',
  `quantity` decimal(12,4) NOT NULL COMMENT '订单数量',
  `unit_price` decimal(10,2) NOT NULL COMMENT '单价',
  `amount` decimal(12,2) NOT NULL COMMENT '金额',
  `received_quantity` decimal(12,4) DEFAULT '0.0000' COMMENT '已收货数量',
  `receipt_status` tinyint DEFAULT '0' COMMENT '收货状态（0 pending待收货/ 1 partially_received部分收货/2 completed已收货）',
  `inquiry_info` json DEFAULT NULL COMMENT '询价信息JSON',
  `inquiry_status` tinyint NOT NULL DEFAULT '0' COMMENT '询价状态: 0待询价/1已询价/2比价中/3已选中',
  `batch_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '批次号',
  `production_date` date DEFAULT NULL COMMENT '生产日期',
  `expiry_date` date DEFAULT NULL COMMENT '有效期至',
  `inspection_result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '检验结果（passed合格/failed不合格）',
  `inspection_remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '检验备注',
  `item_order` int DEFAULT '0' COMMENT '排序',
  PRIMARY KEY (`item_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_material_id` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购订单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `purchase_order_item`
--

LOCK TABLES `purchase_order_item` WRITE;
/*!40000 ALTER TABLE `purchase_order_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `purchase_order_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `purchase_payment`
--

DROP TABLE IF EXISTS `purchase_payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_payment` (
  `payment_id` bigint NOT NULL AUTO_INCREMENT COMMENT '付款ID',
  `payment_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '付款单号',
  `order_id` bigint NOT NULL COMMENT '采购订单ID',
  `document_id` bigint DEFAULT NULL COMMENT '票据ID',
  `payment_date` date NOT NULL COMMENT '付款日期',
  `payment_amount` decimal(12,2) NOT NULL COMMENT '付款金额',
  `payment_method` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '付款方式（bank银行转账/cash现金/check支票）',
  `bank_account` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '银行账户',
  `payment_status` tinyint NOT NULL DEFAULT '0' COMMENT '付款状态: 0待付款/1部分付款/2已付款',
  `approval_time` datetime DEFAULT NULL COMMENT '批准时间',
  `actual_payment_date` date DEFAULT NULL COMMENT '实际付款日期',
  `voucher_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '凭证编号',
  `voucher_file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '凭证文件URL',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`payment_id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购付款表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `purchase_payment`
--

LOCK TABLES `purchase_payment` WRITE;
/*!40000 ALTER TABLE `purchase_payment` DISABLE KEYS */;
/*!40000 ALTER TABLE `purchase_payment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `purchase_supplier`
--

DROP TABLE IF EXISTS `purchase_supplier`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_supplier` (
  `supplier_id` bigint NOT NULL AUTO_INCREMENT COMMENT '供应商ID',
  `supplier_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商编码',
  `supplier_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商名称',
  `supplier_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'M' COMMENT '供应商类型（M原材料/E设备/O其他）',
  `contact_person` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '联系人',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '电话',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '邮箱',
  `address` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '地址',
  `payment_terms` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'NET_30' COMMENT '付款条件',
  `bank_account` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '银行账户',
  `tax_number` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '税号',
  `evaluation_score` decimal(5,2) DEFAULT '0.00' COMMENT '评估总分',
  `quality_score` decimal(5,2) DEFAULT '0.00' COMMENT '质量评分',
  `delivery_score` decimal(5,2) DEFAULT '0.00' COMMENT '交期评分',
  `price_score` decimal(5,2) DEFAULT '0.00' COMMENT '价格评分',
  `last_evaluation_date` date DEFAULT NULL COMMENT '最后评估日期',
  `status` tinyint DEFAULT '1' COMMENT '状态（1正常 0停用 2删除）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '删除标志',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`supplier_id`),
  UNIQUE KEY `uk_supplier_code` (`supplier_code`),
  KEY `idx_supplier_name` (`supplier_name`),
  KEY `idx_supplier_type` (`supplier_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `purchase_supplier`
--

LOCK TABLES `purchase_supplier` WRITE;
/*!40000 ALTER TABLE `purchase_supplier` DISABLE KEYS */;
/*!40000 ALTER TABLE `purchase_supplier` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_contract`
--

DROP TABLE IF EXISTS `sales_contract`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_contract` (
  `contract_id` bigint NOT NULL AUTO_INCREMENT COMMENT '合同ID',
  `contract_no` varchar(50) NOT NULL COMMENT '合同编号',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(200) NOT NULL COMMENT '客户名称',
  `contract_name` varchar(200) NOT NULL COMMENT '合同名称',
  `contract_type` tinyint DEFAULT '1' COMMENT '合同类型 (1: 销售合同, 2: 框架协议, 3: 补充协议)',
  `sign_date` date NOT NULL COMMENT '签订日期',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `expiry_date` date DEFAULT NULL COMMENT '到期日期',
  `contract_amount` decimal(15,2) NOT NULL COMMENT '合同金额',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '币种',
  `payment_terms` text COMMENT '付款条款',
  `delivery_terms` text COMMENT '交货条款',
  `quality_terms` text COMMENT '质量条款',
  `warranty_terms` text COMMENT '保修条款',
  `other_terms` text COMMENT '其他条款',
  `contract_status` tinyint DEFAULT '1' COMMENT '合同状态 (1: 草稿, 2: 已签订, 3: 执行中, 4: 已完成, 5: 已终止, 6: 已过期)',
  `attachment_path` varchar(500) DEFAULT NULL COMMENT '附件路径',
  `remark` text COMMENT '备注',
  `signatory_id` bigint DEFAULT NULL COMMENT '签署人ID',
  `signatory_name` varchar(100) DEFAULT NULL COMMENT '签署人姓名',
  `approver_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `approver_name` varchar(100) DEFAULT NULL COMMENT '审核人姓名',
  `approve_time` datetime DEFAULT NULL COMMENT '审核时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 (0: 正常, 1: 删除)',
  PRIMARY KEY (`contract_id`),
  UNIQUE KEY `uk_contract_no` (`contract_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_sign_date` (`sign_date`),
  KEY `idx_contract_status` (`contract_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售合同表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_contract`
--

LOCK TABLES `sales_contract` WRITE;
/*!40000 ALTER TABLE `sales_contract` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_contract` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_customer`
--

DROP TABLE IF EXISTS `sales_customer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_customer` (
  `customer_id` bigint NOT NULL AUTO_INCREMENT COMMENT '客户ID',
  `customer_code` varchar(50) NOT NULL COMMENT '客户编码',
  `customer_name` varchar(200) NOT NULL COMMENT '客户名称',
  `customer_short_name` varchar(100) DEFAULT NULL COMMENT '客户简称',
  `customer_type` tinyint DEFAULT '1' COMMENT '客户类型 (1: 终端客户, 2: 代理商, 3: 经销商)',
  `customer_level` tinyint DEFAULT '2' COMMENT '客户等级 (1: A级, 2: B级, 3: C级)',
  `industry_category` varchar(100) DEFAULT NULL COMMENT '行业分类',
  `customer_source` tinyint DEFAULT '1' COMMENT '客户来源 (1: 展会, 2: 网络, 3: 转介绍, 4: 主动开发)',
  `country` varchar(50) DEFAULT NULL COMMENT '国家',
  `province` varchar(50) DEFAULT NULL COMMENT '省份',
  `city` varchar(50) DEFAULT NULL COMMENT '城市',
  `address` varchar(500) DEFAULT NULL COMMENT '详细地址',
  `postal_code` varchar(20) DEFAULT NULL COMMENT '邮政编码',
  `contact_person` varchar(100) DEFAULT NULL COMMENT '联系人姓名',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `contact_email` varchar(100) DEFAULT NULL COMMENT '联系邮箱',
  `fax` varchar(50) DEFAULT NULL COMMENT '传真',
  `website` varchar(200) DEFAULT NULL COMMENT '网址',
  `unified_social_credit_code` varchar(50) DEFAULT NULL COMMENT '统一社会信用代码',
  `taxpayer_id` varchar(50) DEFAULT NULL COMMENT '纳税人识别号',
  `bank_name` varchar(200) DEFAULT NULL COMMENT '开户银行',
  `bank_account` varchar(100) DEFAULT NULL COMMENT '银行账号',
  `payment_method` tinyint DEFAULT '1' COMMENT '付款方式 (1: 预付, 2: 货到付款, 3: 月结30天, 4: 月结60天)',
  `payment_terms` varchar(500) DEFAULT NULL COMMENT '付款条件',
  `credit_limit` decimal(15,2) DEFAULT '0.00' COMMENT '信用额度',
  `used_credit_limit` decimal(15,2) DEFAULT '0.00' COMMENT '已用信用额度',
  `customer_status` tinyint DEFAULT '1' COMMENT '客户状态 (1: 潜在客户, 2: 正式客户, 3: 暂停合作, 4: 终止合作)',
  `cooperation_start_date` datetime DEFAULT NULL COMMENT '合作开始日期',
  `cooperation_end_date` datetime DEFAULT NULL COMMENT '合作结束日期',
  `sales_manager_id` bigint DEFAULT NULL COMMENT '销售负责人ID',
  `sales_manager_name` varchar(100) DEFAULT NULL COMMENT '销售负责人姓名',
  `remark` text COMMENT '客户备注',
  `customer_score` tinyint DEFAULT '3' COMMENT '客户评分 (1-5分)',
  `annual_purchase_amount` decimal(15,2) DEFAULT '0.00' COMMENT '年采购额',
  `main_product_demand` varchar(500) DEFAULT NULL COMMENT '主要产品需求',
  `special_requirements` text COMMENT '特殊要求',
  `is_vip` tinyint(1) DEFAULT '0' COMMENT '是否VIP客户',
  `customer_tags` json DEFAULT NULL COMMENT '客户标签 (JSON格式存储多个标签)',
  `attachments` json DEFAULT NULL COMMENT '附件信息 (JSON格式存储附件信息)',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 (0: 正常, 1: 删除)',
  PRIMARY KEY (`customer_id`),
  UNIQUE KEY `uk_customer_code` (`customer_code`),
  KEY `idx_customer_name` (`customer_name`),
  KEY `idx_customer_status` (`customer_status`),
  KEY `idx_sales_manager_id` (`sales_manager_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售客户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_customer`
--

LOCK TABLES `sales_customer` WRITE;
/*!40000 ALTER TABLE `sales_customer` DISABLE KEYS */;
INSERT INTO `sales_customer` VALUES (1,'CUST-202607-JST','捷顺通电子科技有限公司','JST',1,2,NULL,1,'中国','广东省','深圳市','深圳市宝安区西乡街道航城工业区A栋',NULL,'王经理','13800138001','wang@jst-tech.com',NULL,NULL,NULL,NULL,NULL,NULL,1,NULL,500000.00,0.00,2,'2026-08-03 10:40:31',NULL,1,'张伟','长期合作客户，主要采购薄膜开关',3,0.00,NULL,NULL,0,NULL,NULL,NULL,'2026-07-30 19:17:28','xiaoshou0','2026-08-03 10:40:31',0),(2,'CUST-202607-JTT','金泰通电子有限公司','JTT',1,2,NULL,1,'中国','广东省','东莞市','东莞市长安镇乌沙社区兴发路168号',NULL,'李小姐','13900139002','li@jtt-electronic.com',NULL,NULL,NULL,NULL,NULL,NULL,1,NULL,300000.00,0.00,2,'2026-08-03 10:40:35',NULL,1,'张伟','重点客户，每月稳定订单',3,0.00,NULL,NULL,0,NULL,NULL,NULL,'2026-07-30 19:17:28','xiaoshou0','2026-08-03 10:40:35',0),(3,'CUST-202607-LEE','李记精密电子科技','Lee',1,3,NULL,2,'中国','江苏省','苏州市','苏州工业园区星湖街328号创意产业园',NULL,'陈工','13700137003','chen@lee-precision.com',NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,200000.00,0.00,2,'2026-08-03 10:40:38',NULL,1,'李强','技术型客户，对精度要求高',3,0.00,NULL,NULL,0,NULL,NULL,NULL,'2026-07-30 19:17:28','xiaoshou0','2026-08-03 10:40:38',0),(4,'CUST-202607-DLT','德力通电子实业有限公司','DLT',1,2,NULL,1,'中国','浙江省','杭州市','杭州市余杭区良渚街道勾运路58号',NULL,'赵总','13600136004','zhao@dlt-industry.com',NULL,NULL,NULL,NULL,NULL,NULL,1,NULL,400000.00,0.00,1,NULL,NULL,2,'王芳','新开发客户，订单增长较快',3,0.00,NULL,NULL,0,NULL,NULL,NULL,'2026-07-30 19:17:28',NULL,NULL,0),(5,'CUST-202607-HY','华谊智控科技有限公司','HY',1,1,NULL,1,'中国','上海市','上海市','上海市松江区新桥镇新格路258号',NULL,'周经理','13500135005','zhou@hy-zk.com',NULL,NULL,NULL,NULL,NULL,NULL,1,NULL,600000.00,0.00,1,NULL,NULL,1,'张伟','大客户，需定期维护',3,0.00,NULL,NULL,0,NULL,NULL,NULL,'2026-07-30 19:17:28',NULL,NULL,0),(6,'CUST-202607-LIT','立通达光电有限公司','LiT',1,2,NULL,2,'中国','广东省','深圳市','深圳市龙华区观澜街道环观南路71号',NULL,'吴工','13400134006','wu@lit-opto.com',NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,250000.00,0.00,1,NULL,NULL,2,'王芳','光电领域客户，对透光率有要求',3,0.00,NULL,NULL,0,NULL,NULL,NULL,'2026-07-30 19:17:28',NULL,NULL,0),(7,'CST260730001','测试客户','TEST',1,1,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,NULL,0.00,0.00,1,NULL,NULL,NULL,NULL,NULL,3,0.00,NULL,NULL,0,NULL,NULL,'admin','2026-07-30 22:49:31','xiaoshou0','2026-08-05 09:51:46',1),(8,'CST260730002','E2E客户','E2E',1,1,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,NULL,0.00,0.00,1,NULL,NULL,NULL,NULL,NULL,3,0.00,NULL,NULL,0,NULL,NULL,'admin','2026-07-30 22:50:52','admin','2026-07-30 22:50:52',0),(9,'CST260730003','测试客户','TEST',1,1,'',1,NULL,NULL,NULL,'{\"country\":\"CN\",\"province\":\"gd\",\"city\":\"十五\",\"district\":\"解决\",\"street\":\"详细\",\"zipCode\":\"512333\"}',NULL,'leo','13912345633','13912345632@139.com','',NULL,NULL,NULL,NULL,NULL,1,NULL,0.00,0.00,4,NULL,NULL,NULL,NULL,'',3,0.00,NULL,NULL,0,NULL,NULL,'admin','2026-07-30 23:28:12','xiaoshou0','2026-08-05 09:52:49',0),(10,'CST260730004','全量客户','QL',1,1,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,NULL,0.00,0.00,1,NULL,NULL,NULL,NULL,NULL,3,0.00,NULL,NULL,0,NULL,NULL,'admin','2026-07-30 23:33:31','admin','2026-07-30 23:33:31',0);
/*!40000 ALTER TABLE `sales_customer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_delivery`
--

DROP TABLE IF EXISTS `sales_delivery`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_delivery` (
  `delivery_id` bigint NOT NULL AUTO_INCREMENT COMMENT '发货单ID',
  `delivery_no` varchar(50) NOT NULL COMMENT '发货单号',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(200) NOT NULL COMMENT '客户名称',
  `delivery_date` date NOT NULL COMMENT '发货日期',
  `delivery_address` varchar(500) DEFAULT NULL COMMENT '发货地址',
  `contact_person` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `delivery_method` varchar(50) DEFAULT NULL COMMENT '发货方式',
  `tracking_no` varchar(100) DEFAULT NULL COMMENT '物流单号',
  `carrier` varchar(100) DEFAULT NULL COMMENT '承运商',
  `delivery_status` tinyint DEFAULT '1' COMMENT '发货状态 (1: 待发货, 2: 已发货, 3: 已签收, 4: 异常)',
  `total_quantity` int DEFAULT '0' COMMENT '总数量',
  `total_weight` decimal(10,2) DEFAULT '0.00' COMMENT '总重量',
  `total_volume` decimal(10,2) DEFAULT '0.00' COMMENT '总体积',
  `freight_amount` decimal(15,2) DEFAULT '0.00' COMMENT '运费金额',
  `insurance_amount` decimal(15,2) DEFAULT '0.00' COMMENT '保险费',
  `other_charges` decimal(15,2) DEFAULT '0.00' COMMENT '其他费用',
  `total_amount` decimal(15,2) DEFAULT '0.00' COMMENT '总金额',
  `remark` text COMMENT '备注',
  `delivery_person_id` bigint DEFAULT NULL COMMENT '发货人ID',
  `delivery_person_name` varchar(100) DEFAULT NULL COMMENT '发货人姓名',
  `receiver_name` varchar(100) DEFAULT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(50) DEFAULT NULL COMMENT '收货人电话',
  `receive_time` datetime DEFAULT NULL COMMENT '签收时间',
  `receive_remark` text COMMENT '签收备注',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 (0: 正常, 1: 删除)',
  PRIMARY KEY (`delivery_id`),
  UNIQUE KEY `uk_delivery_no` (`delivery_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_delivery_date` (`delivery_date`),
  KEY `idx_delivery_status` (`delivery_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售发货单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_delivery`
--

LOCK TABLES `sales_delivery` WRITE;
/*!40000 ALTER TABLE `sales_delivery` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_delivery` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_inquiry`
--

DROP TABLE IF EXISTS `sales_inquiry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_inquiry` (
  `inquiry_id` bigint NOT NULL AUTO_INCREMENT COMMENT '询价单ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪ID',
  `inquiry_no` varchar(50) NOT NULL COMMENT '询价单编号',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(200) NOT NULL COMMENT '客户名称',
  `contact_person` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `inquiry_date` date NOT NULL COMMENT '询价日期',
  `expected_quantity` int DEFAULT NULL COMMENT '预估数量',
  `product_description` text COMMENT '产品描述/规格要求',
  `product_id` bigint DEFAULT NULL COMMENT '关联产品ID(标准品)',
  `key_count` int DEFAULT NULL COMMENT '按键数量',
  `size_description` varchar(200) DEFAULT NULL COMMENT '尺寸要求',
  `material_requirements` text COMMENT '材料要求',
  `circuit_requirements` text COMMENT '线路要求',
  `connector_requirements` text COMMENT '连接器要求',
  `special_requirements` text COMMENT '特殊要求',
  `has_drawing` tinyint(1) DEFAULT '0' COMMENT '是否有图纸文件',
  `inquiry_status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0草稿/1待处理/2已发送/3已转报价/4已确认/5已拒绝',
  `inquiry_type` tinyint DEFAULT '1' COMMENT '询价类型: 1标准品 2样品',
  `converted_quotation_id` bigint DEFAULT NULL COMMENT '转报价单ID',
  `convert_time` datetime DEFAULT NULL COMMENT '转换时间',
  `remark` text COMMENT '备注',
  `sales_person_id` bigint DEFAULT NULL COMMENT '销售负责人ID',
  `sales_person_name` varchar(100) DEFAULT NULL COMMENT '销售负责人姓名',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 0正常 1删除',
  PRIMARY KEY (`inquiry_id`),
  UNIQUE KEY `inquiry_no` (`inquiry_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_inquiry_status` (`inquiry_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售询价单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_inquiry`
--

LOCK TABLES `sales_inquiry` WRITE;
/*!40000 ALTER TABLE `sales_inquiry` DISABLE KEYS */;
INSERT INTO `sales_inquiry` VALUES (1,'e32451d48af1460e','INQ2608040001',1,'捷顺通电子科技有限公司','王经理','13800138001','2026-08-04',1,'',NULL,NULL,'','','','','',0,3,2,1,'2026-08-04 15:22:31','',26,'xiaoshou0','xiaoshou0','2026-08-04 15:20:05','xiaoshou0','2026-08-04 15:20:05',0),(2,'cdb02bd1b7ab4dc2','INQ2608040002',4,'德力通电子实业有限公司','赵总','13600136004','2026-08-04',1,'',NULL,NULL,'','','','','',1,3,2,2,'2026-08-04 15:49:12','',26,'xiaoshou0','xiaoshou0','2026-08-04 15:48:39','xiaoshou0','2026-08-04 15:48:39',0),(3,'6240eb735b0a4585','INQ2608040003',3,'李记精密电子科技','陈工','13700137003','2026-08-04',1,'',NULL,NULL,'','','','','',0,3,2,3,'2026-08-04 15:53:42','',26,'xiaoshou0','xiaoshou0','2026-08-04 15:50:18','xiaoshou0','2026-08-04 15:50:18',0);
/*!40000 ALTER TABLE `sales_inquiry` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_invoice`
--

DROP TABLE IF EXISTS `sales_invoice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_invoice` (
  `invoice_id` bigint NOT NULL AUTO_INCREMENT COMMENT '发票ID',
  `invoice_no` varchar(50) NOT NULL COMMENT '发票号码',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(200) NOT NULL COMMENT '客户名称',
  `invoice_type` tinyint DEFAULT '1' COMMENT '发票类型 (1: 增值税普通发票, 2: 增值税专用发票, 3: 电子普通发票)',
  `invoice_date` date NOT NULL COMMENT '开票日期',
  `taxpayer_id` varchar(50) DEFAULT NULL COMMENT '纳税人识别号',
  `address` varchar(500) DEFAULT NULL COMMENT '地址',
  `phone` varchar(50) DEFAULT NULL COMMENT '电话',
  `bank_name` varchar(200) DEFAULT NULL COMMENT '开户银行',
  `bank_account` varchar(100) DEFAULT NULL COMMENT '银行账号',
  `invoice_amount` decimal(15,2) NOT NULL COMMENT '发票金额',
  `tax_amount` decimal(15,2) NOT NULL COMMENT '税额',
  `total_amount` decimal(15,2) NOT NULL COMMENT '价税合计',
  `invoice_status` tinyint DEFAULT '1' COMMENT '发票状态 (1: 待开票, 2: 已开票, 3: 已寄出, 4: 已收到, 5: 已作废)',
  `remark` text COMMENT '备注',
  `issue_by` bigint DEFAULT NULL COMMENT '开票人ID',
  `issue_name` varchar(100) DEFAULT NULL COMMENT '开票人姓名',
  `issue_time` datetime DEFAULT NULL COMMENT '开票时间',
  `send_time` datetime DEFAULT NULL COMMENT '寄出时间',
  `send_method` varchar(50) DEFAULT NULL COMMENT '寄送方式',
  `tracking_no` varchar(100) DEFAULT NULL COMMENT '快递单号',
  `receive_time` datetime DEFAULT NULL COMMENT '收到时间',
  `receive_by` bigint DEFAULT NULL COMMENT '签收人ID',
  `receive_name` varchar(100) DEFAULT NULL COMMENT '签收人姓名',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 (0: 正常, 1: 删除)',
  PRIMARY KEY (`invoice_id`),
  UNIQUE KEY `uk_invoice_no` (`invoice_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_invoice_date` (`invoice_date`),
  KEY `idx_invoice_status` (`invoice_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售发票表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_invoice`
--

LOCK TABLES `sales_invoice` WRITE;
/*!40000 ALTER TABLE `sales_invoice` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_invoice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_order`
--

DROP TABLE IF EXISTS `sales_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_order` (
  `order_id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪ID',
  `order_no` varchar(50) NOT NULL COMMENT '订单编号',
  `quotation_id` bigint DEFAULT NULL COMMENT '报价单ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(200) NOT NULL COMMENT '客户名称',
  `contact_person` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `order_date` date NOT NULL COMMENT '订单日期',
  `delivery_date` date DEFAULT NULL COMMENT '客户要求交货日期',
  `order_type` tinyint NOT NULL DEFAULT '1' COMMENT '订单类型: 1标准订单,2样品订单',
  `order_status` tinyint NOT NULL DEFAULT '1' COMMENT '订单状态: 1草稿,2已确认,3部分发货,4已发货,5部分完成,6已完成,7已取消',
  `prod_status` tinyint NOT NULL DEFAULT '1' COMMENT '生产状态: 1无生产,2部分生产中,3全部生产中,4生产完成',
  `is_urgent` tinyint NOT NULL DEFAULT '0' COMMENT '是否急单: 0否,1是',
  `urgent_reason` varchar(200) DEFAULT NULL COMMENT '加急原因',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '币种',
  `exchange_rate` decimal(10,4) DEFAULT '1.0000' COMMENT '汇率',
  `payment_terms` varchar(500) DEFAULT NULL COMMENT '付款条件',
  `delivery_terms` varchar(500) DEFAULT NULL COMMENT '交货条件',
  `delivery_address` varchar(500) DEFAULT NULL COMMENT '交货地址',
  `total_amount` decimal(15,2) DEFAULT '0.00' COMMENT '总金额',
  `tax_rate` decimal(5,2) DEFAULT '0.00' COMMENT '税率',
  `tax_amount` decimal(15,2) DEFAULT '0.00' COMMENT '税额',
  `total_amount_with_tax` decimal(15,2) DEFAULT '0.00' COMMENT '含税总金额',
  `discount_rate` decimal(5,2) DEFAULT '0.00' COMMENT '折扣率',
  `discount_amount` decimal(15,2) DEFAULT '0.00' COMMENT '折扣金额',
  `final_amount` decimal(15,2) DEFAULT '0.00' COMMENT '最终金额',
  `payment_status` tinyint NOT NULL DEFAULT '1' COMMENT '支付状态: 1未支付,2支付中,3已支付,4部分支付,5已退款',
  `paid_amount` decimal(15,2) DEFAULT '0.00' COMMENT '已付金额',
  `unpaid_amount` decimal(15,2) DEFAULT '0.00' COMMENT '未付金额',
  `total_quantity` int DEFAULT '0' COMMENT '总数量',
  `shipped_quantity` int DEFAULT '0' COMMENT '已发货数量',
  `produced_quantity` int DEFAULT '0' COMMENT '已生产数量',
  `sales_manager_id` bigint DEFAULT NULL COMMENT '销售负责人ID',
  `sales_manager_name` varchar(100) DEFAULT NULL COMMENT '销售负责人姓名',
  `remark` text COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志: 0正常,1删除',
  `sample_status` tinyint DEFAULT '0' COMMENT '样品单状态: 1=已创建,2=待审核,3=工程打样中,4=样品待送样,5=已送样待确认,6=样品确认,7=已转量产,8=已关闭',
  `sample_round` int DEFAULT '0' COMMENT '样品迭代轮次',
  `sample_qty` int DEFAULT NULL COMMENT '打样数量',
  `engineering_note` text COMMENT '工程备注',
  `engineering_acceptor` varchar(50) DEFAULT NULL COMMENT '工程接单人',
  `engineering_accept_time` datetime DEFAULT NULL COMMENT '工程接单时间',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '工程拒单原因',
  `current_process` varchar(50) DEFAULT NULL COMMENT '打样当前工序',
  `sample_cost` decimal(12,2) DEFAULT '0.00' COMMENT '打样成本',
  `sample_work_hours` decimal(8,2) DEFAULT '0.00' COMMENT '打样工时(小时)',
  `sample_tracking_no` varchar(100) DEFAULT NULL COMMENT '送样快递单号',
  `sample_send_date` datetime DEFAULT NULL COMMENT '送样日期',
  `sample_confirm_date` datetime DEFAULT NULL COMMENT '客户确认日期',
  `sample_client_name` varchar(100) DEFAULT NULL COMMENT '客户方确认人',
  `converted_order_id` bigint DEFAULT NULL COMMENT '转量产后的标准订单ID',
  `convert_order_time` datetime DEFAULT NULL COMMENT '转量产时间',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `order_no` (`order_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_order_date` (`order_date`),
  KEY `idx_order_status` (`order_status`),
  KEY `idx_prod_status` (`prod_status`),
  KEY `idx_urgent` (`is_urgent`,`order_status`),
  KEY `idx_payment_status` (`payment_status`),
  KEY `idx_sample_status` (`sample_status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售订单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_order`
--

LOCK TABLES `sales_order` WRITE;
/*!40000 ALTER TABLE `sales_order` DISABLE KEYS */;
INSERT INTO `sales_order` VALUES (1,'e32451d48af1460e','SP2608040001',1,1,'捷顺通电子科技有限公司','王经理','13800138001','2026-08-04','2026-09-03',2,10,1,0,NULL,'CNY',1.0000,NULL,NULL,NULL,660.00,0.00,0.00,0.00,0.00,0.00,660.00,1,0.00,0.00,10,0,0,26,'xiaoshou0','','xiaoshou0','2026-08-04 16:03:27','admin','2026-08-04 17:17:27',0,7,1,10,NULL,'工程','2026-08-04 16:05:33',NULL,'冲切',0.00,0.00,'','2026-08-04 16:07:11','2026-08-04 16:07:15','客户确认',2,'2026-08-04 17:16:37'),(2,NULL,'SO2608040001',1,1,'捷顺通电子科技有限公司','王经理','13800138001','2026-08-04','2026-09-03',1,7,3,0,NULL,'CNY',1.0000,NULL,NULL,NULL,660.00,0.00,0.00,0.00,0.00,0.00,660.00,1,0.00,0.00,10,0,0,26,'xiaoshou0','由样品单[SP2608040001]转量产生成\n【最后工序】冲切\n【材料成本】1378.50元\n【打样工时】0.55小时','admin','2026-08-04 17:16:37','admin','2026-08-04 17:43:29',0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,0.00,0.00,NULL,NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `sales_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_order_product`
--

DROP TABLE IF EXISTS `sales_order_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_order_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `quantity` int DEFAULT NULL COMMENT '产品数量',
  `amount` double NOT NULL COMMENT '产品金额',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `product_id` bigint DEFAULT NULL COMMENT '产品ID（样品单可为空）',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位',
  `unit_price` double DEFAULT NULL COMMENT '单价',
  `product_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产品编码',
  `product_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产品名称',
  `remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `specification` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '规格描述',
  `customer_material_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '客户物料号',
  `line_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '行备注',
  PRIMARY KEY (`id`),
  KEY `sales_order_product_product_code_index` (`product_code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单产品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_order_product`
--

LOCK TABLES `sales_order_product` WRITE;
/*!40000 ALTER TABLE `sales_order_product` DISABLE KEYS */;
INSERT INTO `sales_order_product` VALUES (1,2,660,1,1,'PCS',330,'QT2608040001','QT2608040001',NULL,'','',''),(2,2,660,2,1,'PCS',330,'QT2608040001','QT2608040001',NULL,'','','');
/*!40000 ALTER TABLE `sales_order_product` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_order_review`
--

DROP TABLE IF EXISTS `sales_order_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_order_review` (
  `review_id` bigint NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `review_type` tinyint DEFAULT '1' COMMENT '审核类型 (1: 订单审核, 2: 价格审核, 3: 技术审核)',
  `review_stage` tinyint DEFAULT '1' COMMENT '审核阶段',
  `reviewer_role` varchar(100) DEFAULT '' COMMENT '审核人角色',
  `review_status` tinyint DEFAULT '1' COMMENT '审核状态 (1: 待审核, 2: 审核通过, 3: 审核驳回)',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `reviewer_name` varchar(100) DEFAULT NULL COMMENT '审核人姓名',
  `review_result` tinyint DEFAULT NULL COMMENT '审核结果',
  `review_comment` text COMMENT '审核意见',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_remark` text COMMENT '审核备注',
  `next_reviewer_id` bigint DEFAULT NULL COMMENT '下一审核人ID',
  `next_reviewer_name` varchar(100) DEFAULT NULL COMMENT '下一审核人姓名',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 (0: 正常, 1: 删除)',
  PRIMARY KEY (`review_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_review_status` (`review_status`),
  KEY `idx_reviewer_id` (`reviewer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单审核记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_order_review`
--

LOCK TABLES `sales_order_review` WRITE;
/*!40000 ALTER TABLE `sales_order_review` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_order_review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_order_stock_reserve`
--

DROP TABLE IF EXISTS `sales_order_stock_reserve`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_order_stock_reserve` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '销售订单ID',
  `order_no` varchar(50) DEFAULT NULL COMMENT '订单号',
  `product_id` bigint DEFAULT NULL COMMENT '产品ID',
  `material_id` bigint NOT NULL COMMENT '成品物料ID',
  `material_code` varchar(50) DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(200) DEFAULT NULL COMMENT '物料名称',
  `reserve_quantity` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '预留数量',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0=有效 1=已释放',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_material` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售订单成品库存预留表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_order_stock_reserve`
--

LOCK TABLES `sales_order_stock_reserve` WRITE;
/*!40000 ALTER TABLE `sales_order_stock_reserve` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_order_stock_reserve` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_performance`
--

DROP TABLE IF EXISTS `sales_performance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_performance` (
  `performance_id` bigint NOT NULL AUTO_INCREMENT COMMENT '业绩ID',
  `sales_person_id` bigint NOT NULL COMMENT '销售员ID',
  `sales_person_name` varchar(100) NOT NULL COMMENT '销售员姓名',
  `stat_year` int NOT NULL COMMENT '统计年份',
  `stat_month` int NOT NULL COMMENT '统计月份',
  `new_customer_count` int DEFAULT '0' COMMENT '新增客户数',
  `quotation_count` int DEFAULT '0' COMMENT '报价单数',
  `quotation_amount` decimal(15,2) DEFAULT '0.00' COMMENT '报价金额',
  `order_count` int DEFAULT '0' COMMENT '订单数',
  `order_amount` decimal(15,2) DEFAULT '0.00' COMMENT '订单金额',
  `delivery_count` int DEFAULT '0' COMMENT '发货单数',
  `delivery_amount` decimal(15,2) DEFAULT '0.00' COMMENT '发货金额',
  `receipt_amount` decimal(15,2) DEFAULT '0.00' COMMENT '收款金额',
  `invoice_amount` decimal(15,2) DEFAULT '0.00' COMMENT '开票金额',
  `performance_score` decimal(5,2) DEFAULT '0.00' COMMENT '业绩评分',
  `remark` text COMMENT '备注',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 (0: 正常, 1: 删除)',
  PRIMARY KEY (`performance_id`),
  UNIQUE KEY `uk_sales_person_stat` (`sales_person_id`,`stat_year`,`stat_month`),
  KEY `idx_sales_person_id` (`sales_person_id`),
  KEY `idx_stat_year_month` (`stat_year`,`stat_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售业绩统计表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_performance`
--

LOCK TABLES `sales_performance` WRITE;
/*!40000 ALTER TABLE `sales_performance` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_performance` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_quotation`
--

DROP TABLE IF EXISTS `sales_quotation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_quotation` (
  `quotation_id` bigint NOT NULL AUTO_INCREMENT COMMENT '报价单ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪ID',
  `quotation_no` varchar(50) NOT NULL COMMENT '报价单编号',
  `quotation_type` tinyint DEFAULT '1' COMMENT '报价单类型: 1标准品 2样品',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(200) NOT NULL COMMENT '客户名称',
  `contact_person` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `quotation_date` date NOT NULL COMMENT '报价日期',
  `valid_until` date NOT NULL COMMENT '有效期至',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '币种',
  `exchange_rate` decimal(10,4) DEFAULT '1.0000' COMMENT '汇率',
  `quotation_status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0草稿/1已发送/2已确认/3已拒绝/4已过期',
  `subtotal_amount` decimal(15,2) DEFAULT '0.00' COMMENT '小计金额',
  `tax_rate` decimal(5,2) DEFAULT '0.00' COMMENT '税率',
  `tax_amount` decimal(15,2) DEFAULT '0.00' COMMENT '税额',
  `total_amount` decimal(15,2) DEFAULT '0.00' COMMENT '总金额',
  `discount_amount` decimal(15,2) DEFAULT '0.00' COMMENT '折扣金额',
  `final_amount` decimal(15,2) DEFAULT '0.00' COMMENT '最终金额',
  `remark` text COMMENT '备注',
  `sales_person_id` bigint DEFAULT NULL COMMENT '销售员ID',
  `sales_person_name` varchar(100) DEFAULT NULL COMMENT '销售员姓名',
  `approver_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `approver_name` varchar(100) DEFAULT NULL COMMENT '审核人姓名',
  `approve_time` datetime DEFAULT NULL COMMENT '审核时间',
  `approve_remark` text COMMENT '审核备注',
  `send_time` datetime DEFAULT NULL COMMENT '发送时间',
  `send_method` varchar(50) DEFAULT NULL COMMENT '发送方式',
  `send_remark` text COMMENT '发送备注',
  `converted_order_id` bigint DEFAULT NULL COMMENT '转为订单ID',
  `convert_time` datetime DEFAULT NULL COMMENT '转为订单时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 (0: 正常, 1: 删除)',
  PRIMARY KEY (`quotation_id`),
  UNIQUE KEY `uk_quotation_no` (`quotation_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_quotation_date` (`quotation_date`),
  KEY `idx_quotation_status` (`quotation_status`),
  KEY `idx_sales_person_id` (`sales_person_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售报价表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_quotation`
--

LOCK TABLES `sales_quotation` WRITE;
/*!40000 ALTER TABLE `sales_quotation` DISABLE KEYS */;
INSERT INTO `sales_quotation` VALUES (1,'e32451d48af1460e','QT2608040001',2,1,'捷顺通电子科技有限公司','王经理','13800138001','2026-08-04','2026-09-03','CNY',1.0000,9,660.00,0.00,0.00,660.00,0.00,660.00,'由询价单[INQ2608040001]自动创建',26,'xiaoshou0',1,'系统管理员','2026-08-04 16:03:07','','2026-08-04 16:03:20','email',NULL,2,'2026-08-04 17:16:37','xiaoshou0','2026-08-04 15:22:31','xiaoshou0','2026-08-04 16:03:27',0),(2,'cdb02bd1b7ab4dc2','QT2608040002',2,4,'德力通电子实业有限公司','赵总','13600136004','2026-08-04','2026-09-03','CNY',1.0000,0,0.00,0.00,0.00,0.00,0.00,0.00,'由询价单[INQ2608040002]自动创建',26,'xiaoshou0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'xiaoshou0','2026-08-04 15:49:12','xiaoshou0','2026-08-04 15:49:12',0),(3,'6240eb735b0a4585','QT2608040003',2,3,'李记精密电子科技','陈工','13700137003','2026-08-04','2026-09-03','CNY',1.0000,0,0.00,0.00,0.00,0.00,0.00,0.00,'由询价单[INQ2608040003]自动创建',26,'xiaoshou0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'xiaoshou0','2026-08-04 15:53:42','xiaoshou0','2026-08-04 15:53:42',0),(4,'ab4e9054a3614da0','COPY_QT2608040001',1,1,'捷顺通电子科技有限公司','王经理','13800138001','2026-08-04','2026-09-03','CNY',1.0000,0,660.00,0.00,0.00,660.00,0.00,660.00,'复制自报价单：QT2608040001\n由询价单[INQ2608040001]自动创建',26,'xiaoshou0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'xiaoshou0','2026-08-04 15:55:35','xiaoshou0','2026-08-04 16:01:12',0),(5,'736d131a524042d2','TEST-QT-412-02',1,1,'捷顺通电子科技有限公司',NULL,NULL,'2026-08-04','2026-09-04','CNY',1.0000,2,1000.00,0.00,0.00,1000.00,0.00,1000.00,NULL,NULL,NULL,1,'系统管理员','2026-08-04 21:56:29','ok','2026-08-04 21:56:29','email',NULL,NULL,NULL,'admin','2026-08-04 21:56:00','admin','2026-08-04 21:56:00',0),(6,'913f6b1791454c22','COPY_TEST-QT-412-02',1,1,'捷顺通电子科技有限公司',NULL,NULL,'2026-08-04','2026-09-04','CNY',1.0000,3,1000.00,0.00,0.00,1000.00,0.00,1000.00,'复制自报价单：TEST-QT-412-02\nnull',NULL,NULL,1,'系统管理员','2026-08-04 21:58:25',NULL,'2026-08-04 21:58:25','email',NULL,NULL,NULL,'admin','2026-08-04 21:57:33','admin','2026-08-04 21:58:20',0);
/*!40000 ALTER TABLE `sales_quotation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_quotation_flow`
--

DROP TABLE IF EXISTS `sales_quotation_flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_quotation_flow` (
  `flow_id` bigint NOT NULL AUTO_INCREMENT COMMENT '流转记录ID',
  `quotation_id` bigint NOT NULL COMMENT '报价单ID',
  `action_code` varchar(50) NOT NULL COMMENT '动作编码: SUBMIT_REVIEW/APPROVE/REJECT/SEND/CUSTOMER_CONFIRM/CUSTOMER_REJECT',
  `action_name` varchar(50) NOT NULL COMMENT '动作名称',
  `from_status` int DEFAULT NULL COMMENT '流转前状态',
  `to_status` int DEFAULT NULL COMMENT '流转后状态',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人姓名',
  `remark` varchar(1000) DEFAULT NULL COMMENT '流转说明/审核意见',
  `attachment_ids` varchar(500) DEFAULT NULL COMMENT '附件ID列表(JSON数组)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`flow_id`),
  KEY `idx_quotation` (`quotation_id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报价单状态流转记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_quotation_flow`
--

LOCK TABLES `sales_quotation_flow` WRITE;
/*!40000 ALTER TABLE `sales_quotation_flow` DISABLE KEYS */;
INSERT INTO `sales_quotation_flow` VALUES (1,1,'SUBMIT_REVIEW','提交审核',0,5,NULL,'系统',NULL,'5','2026-08-04 15:25:37'),(2,1,'APPROVE','审核通过',5,6,NULL,'系统','',NULL,'2026-08-04 15:36:03'),(3,1,'SEND','发送报价',6,1,NULL,'系统',NULL,NULL,'2026-08-04 15:36:07'),(4,1,'CUSTOMER_REJECT','客户拒绝报价',1,3,NULL,'系统',NULL,NULL,'2026-08-04 15:36:20'),(5,1,'STATUS_CHANGE','状态变更',3,0,NULL,'系统',NULL,NULL,'2026-08-04 16:03:01'),(6,1,'SUBMIT_REVIEW','提交审核',0,5,NULL,'系统',NULL,NULL,'2026-08-04 16:03:04'),(7,1,'APPROVE','审核通过',5,6,NULL,'系统','',NULL,'2026-08-04 16:03:07'),(8,1,'SEND','发送报价',6,1,NULL,'系统',NULL,NULL,'2026-08-04 16:03:20'),(9,1,'CUSTOMER_CONFIRM','客户确认报价',1,2,NULL,'系统',NULL,NULL,'2026-08-04 16:03:24'),(10,5,'SUBMIT_REVIEW','提交审核',0,5,NULL,'系统',NULL,NULL,'2026-08-04 21:56:06'),(11,5,'APPROVE','审核通过',5,6,NULL,'系统','ok',NULL,'2026-08-04 21:56:29'),(12,5,'SEND','发送报价',6,1,NULL,'系统',NULL,NULL,'2026-08-04 21:56:29'),(13,5,'CUSTOMER_CONFIRM','客户确认报价',1,2,NULL,'系统',NULL,NULL,'2026-08-04 21:56:35'),(14,6,'SUBMIT_REVIEW','提交审核',0,5,NULL,'系统',NULL,NULL,'2026-08-04 21:58:20'),(15,6,'APPROVE','审核通过',5,6,NULL,'系统',NULL,NULL,'2026-08-04 21:58:25'),(16,6,'SEND','发送报价',6,1,NULL,'系统',NULL,NULL,'2026-08-04 21:58:25'),(17,6,'CUSTOMER_REJECT','客户拒绝报价',1,3,NULL,'系统',NULL,NULL,'2026-08-04 21:58:25');
/*!40000 ALTER TABLE `sales_quotation_flow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_quotation_item`
--

DROP TABLE IF EXISTS `sales_quotation_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_quotation_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `quotation_id` bigint NOT NULL COMMENT '报价单ID',
  `product_id` bigint DEFAULT NULL COMMENT '产品ID',
  `product_code` varchar(50) DEFAULT NULL COMMENT '产品编码',
  `product_name` varchar(200) DEFAULT NULL COMMENT '产品名称',
  `key_count` int DEFAULT NULL COMMENT '按键数量',
  `width` decimal(10,2) DEFAULT NULL COMMENT '宽度mm',
  `height` decimal(10,2) DEFAULT NULL COMMENT '高度mm',
  `thickness` decimal(10,2) DEFAULT NULL COMMENT '厚度mm',
  `material_type` varchar(50) DEFAULT NULL COMMENT '材料类型',
  `color` varchar(50) DEFAULT NULL COMMENT '颜色',
  `circuit_type` varchar(50) DEFAULT NULL COMMENT '线路类型',
  `connector_type` varchar(50) DEFAULT NULL COMMENT '连接器类型',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `unit_price` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '单价',
  `unit` varchar(20) DEFAULT 'PCS' COMMENT '单位',
  `amount` decimal(12,2) DEFAULT '0.00' COMMENT '金额',
  `delivery_days` int DEFAULT NULL COMMENT '交期天数',
  `estimated_delivery_date` date DEFAULT NULL COMMENT '预计交期',
  `custom_requirements` varchar(500) DEFAULT NULL COMMENT '自定义要求',
  `logo_requirement` varchar(500) DEFAULT NULL COMMENT 'Logo要求',
  `certification_requirement` varchar(500) DEFAULT NULL COMMENT '认证要求',
  `item_order` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`item_id`),
  KEY `idx_quotation_id` (`quotation_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报价单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_quotation_item`
--

LOCK TABLES `sales_quotation_item` WRITE;
/*!40000 ALTER TABLE `sales_quotation_item` DISABLE KEYS */;
INSERT INTO `sales_quotation_item` VALUES (1,1,NULL,'QT2608040001','QT2608040001',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,330.00,'PCS',660.00,NULL,NULL,NULL,NULL,NULL,0,'2026-08-04 15:25:29','2026-08-04 15:25:29'),(2,5,1,'P001','薄膜开关测试品',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,10,100.00,'PCS',1000.00,NULL,NULL,NULL,NULL,NULL,0,'2026-08-04 21:56:00','2026-08-04 21:56:00'),(3,6,1,'P001','薄膜开关测试品',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,10,100.00,'PCS',1000.00,NULL,NULL,NULL,NULL,NULL,0,'2026-08-04 21:58:19','2026-08-04 21:58:19');
/*!40000 ALTER TABLE `sales_quotation_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_receipt`
--

DROP TABLE IF EXISTS `sales_receipt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_receipt` (
  `receipt_id` bigint NOT NULL AUTO_INCREMENT COMMENT '收款单ID',
  `receipt_no` varchar(50) NOT NULL COMMENT '收款单号',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(200) NOT NULL COMMENT '客户名称',
  `receipt_date` date NOT NULL COMMENT '收款日期',
  `receipt_type` tinyint DEFAULT '1' COMMENT '收款类型 (1: 预付款, 2: 进度款, 3: 尾款, 4: 质保金)',
  `payment_method` tinyint DEFAULT '1' COMMENT '付款方式 (1: 现金, 2: 银行转账, 3: 支票, 4: 支付宝, 5: 微信)',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '币种',
  `exchange_rate` decimal(10,4) DEFAULT '1.0000' COMMENT '汇率',
  `receipt_amount` decimal(15,2) NOT NULL COMMENT '收款金额',
  `actual_amount` decimal(15,2) NOT NULL COMMENT '实际金额',
  `bank_name` varchar(200) DEFAULT NULL COMMENT '银行名称',
  `bank_account` varchar(100) DEFAULT NULL COMMENT '银行账号',
  `check_no` varchar(100) DEFAULT NULL COMMENT '支票号',
  `payer_name` varchar(100) DEFAULT NULL COMMENT '付款人姓名',
  `payer_account` varchar(100) DEFAULT NULL COMMENT '付款人账号',
  `receipt_status` tinyint DEFAULT '1' COMMENT '收款状态 (1: 待确认, 2: 已确认, 3: 已到账, 4: 已退回)',
  `remark` text COMMENT '备注',
  `confirm_time` datetime DEFAULT NULL COMMENT '确认时间',
  `confirm_by` bigint DEFAULT NULL COMMENT '确认人ID',
  `confirm_name` varchar(100) DEFAULT NULL COMMENT '确认人姓名',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 (0: 正常, 1: 删除)',
  PRIMARY KEY (`receipt_id`),
  UNIQUE KEY `uk_receipt_no` (`receipt_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_receipt_date` (`receipt_date`),
  KEY `idx_receipt_status` (`receipt_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售收款单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_receipt`
--

LOCK TABLES `sales_receipt` WRITE;
/*!40000 ALTER TABLE `sales_receipt` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_receipt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_return`
--

DROP TABLE IF EXISTS `sales_return`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_return` (
  `return_id` bigint NOT NULL AUTO_INCREMENT COMMENT '退货单ID',
  `return_no` varchar(50) NOT NULL COMMENT '退货单号',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `delivery_id` bigint DEFAULT NULL COMMENT '发货单ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(200) NOT NULL COMMENT '客户名称',
  `return_date` date NOT NULL COMMENT '退货日期',
  `return_reason` varchar(500) NOT NULL COMMENT '退货原因',
  `return_type` tinyint DEFAULT '1' COMMENT '退货类型 (1: 质量问题, 2: 规格不符, 3: 数量错误, 4: 客户取消, 5: 其他)',
  `return_status` tinyint DEFAULT '1' COMMENT '退货状态 (1: 申请中, 2: 已审核, 3: 已收货, 4: 已退款, 5: 已完成, 6: 已取消)',
  `total_quantity` int DEFAULT '0' COMMENT '总数量',
  `total_amount` decimal(15,2) DEFAULT '0.00' COMMENT '总金额',
  `refund_amount` decimal(15,2) DEFAULT '0.00' COMMENT '退款金额',
  `remark` text COMMENT '备注',
  `approver_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `approver_name` varchar(100) DEFAULT NULL COMMENT '审核人姓名',
  `approve_time` datetime DEFAULT NULL COMMENT '审核时间',
  `approve_remark` text COMMENT '审核备注',
  `receive_time` datetime DEFAULT NULL COMMENT '收货时间',
  `receive_by` bigint DEFAULT NULL COMMENT '收货人ID',
  `receive_name` varchar(100) DEFAULT NULL COMMENT '收货人姓名',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `refund_by` bigint DEFAULT NULL COMMENT '退款人ID',
  `refund_name` varchar(100) DEFAULT NULL COMMENT '退款人姓名',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 (0: 正常, 1: 删除)',
  PRIMARY KEY (`return_id`),
  UNIQUE KEY `uk_return_no` (`return_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_return_date` (`return_date`),
  KEY `idx_return_status` (`return_status`),
  KEY `fk_return_delivery` (`delivery_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售退货单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_return`
--

LOCK TABLES `sales_return` WRITE;
/*!40000 ALTER TABLE `sales_return` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_return` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_sample_bom`
--

DROP TABLE IF EXISTS `sales_sample_bom`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_sample_bom` (
  `bom_id` bigint NOT NULL AUTO_INCREMENT COMMENT '打样BOM记录ID',
  `order_id` bigint NOT NULL COMMENT '样品单ID(sales_order.order_id)',
  `round_no` int DEFAULT '1' COMMENT '打样轮次',
  `layer_name` varchar(50) NOT NULL COMMENT '层结构(面板/线路/间隔/背胶/其他)',
  `material_name` varchar(200) NOT NULL COMMENT '物料名称',
  `specification` varchar(500) DEFAULT NULL COMMENT '规格',
  `quantity` decimal(12,4) NOT NULL DEFAULT '1.0000' COMMENT '用量',
  `unit` varchar(20) DEFAULT 'PCS' COMMENT '单位',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(50) DEFAULT NULL COMMENT '录入人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`bom_id`),
  KEY `idx_order` (`order_id`,`round_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打样BOM物料清单(结构化)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_sample_bom`
--

LOCK TABLES `sales_sample_bom` WRITE;
/*!40000 ALTER TABLE `sales_sample_bom` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_sample_bom` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_sample_process`
--

DROP TABLE IF EXISTS `sales_sample_process`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_sample_process` (
  `process_id` bigint NOT NULL AUTO_INCREMENT COMMENT '工序记录ID',
  `order_id` bigint NOT NULL COMMENT '样品单ID(sales_order.order_id)',
  `round_no` int DEFAULT '1' COMMENT '打样轮次',
  `process_name` varchar(100) NOT NULL COMMENT '工序名称',
  `materials` text COMMENT '该工序材料明细(JSON: [{name,spec,qty,unit}])',
  `process_note` text COMMENT '工艺说明(怎么做的)',
  `operator` varchar(50) DEFAULT NULL COMMENT '操作人',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration_minutes` int DEFAULT NULL COMMENT '耗时(分钟)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`process_id`),
  KEY `idx_order` (`order_id`,`round_no`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打样工序历史记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_sample_process`
--

LOCK TABLES `sales_sample_process` WRITE;
/*!40000 ALTER TABLE `sales_sample_process` DISABLE KEYS */;
INSERT INTO `sales_sample_process` VALUES (1,1,1,'印刷','[{\"name\":\"PET薄膜 0.125mm 透明\",\"spec\":\"0.125mm×1200mm卷\",\"qty\":1,\"unit\":\"M\",\"materialId\":1,\"materialCode\":\"MAT-001\"}]',NULL,'gongcheng0','2026-08-04 16:06:14',NULL,33,'工序进度更新','2026-08-04 16:06:13'),(2,1,1,'冲切','[{\"name\":\"导电银浆 BY-6000\",\"spec\":\"1kg/罐\",\"qty\":2,\"unit\":\"KG\",\"materialId\":4,\"materialCode\":\"MAT-004\"}]',NULL,'gongcheng0','2026-08-04 16:06:46',NULL,NULL,'工序进度更新','2026-08-04 16:06:45');
/*!40000 ALTER TABLE `sales_sample_process` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_sample_round`
--

DROP TABLE IF EXISTS `sales_sample_round`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_sample_round` (
  `round_id` bigint NOT NULL AUTO_INCREMENT COMMENT '轮次记录ID',
  `order_id` bigint NOT NULL COMMENT '样品单ID',
  `round_no` int NOT NULL COMMENT '轮次号',
  `engineering_note` text COMMENT '该轮工艺参数快照',
  `attachment_ids` varchar(500) DEFAULT NULL COMMENT '该轮图纸附件ID(JSON数组)',
  `bom_snapshot` text COMMENT '该轮BOM物料快照(JSON)',
  `process_snapshot` text COMMENT '该轮工序记录汇总(JSON)',
  `result` varchar(20) DEFAULT NULL COMMENT '该轮结果: pending/confirmed/rejected',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '该轮退回原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`round_id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='样品打样轮次快照';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_sample_round`
--

LOCK TABLES `sales_sample_round` WRITE;
/*!40000 ALTER TABLE `sales_sample_round` DISABLE KEYS */;
INSERT INTO `sales_sample_round` VALUES (1,1,1,NULL,'[1,2,3]','[{\"process\":\"印刷\",\"name\":\"PET薄膜 0.125mm 透明\",\"spec\":\"0.125mm×1200mm卷\",\"qty\":1,\"unit\":\"M\",\"materialId\":1,\"materialCode\":\"MAT-001\"},{\"process\":\"冲切\",\"name\":\"导电银浆 BY-6000\",\"spec\":\"1kg/罐\",\"qty\":2,\"unit\":\"KG\",\"materialId\":4,\"materialCode\":\"MAT-004\"}]',NULL,'confirmed',NULL,'2026-08-04 16:06:54');
/*!40000 ALTER TABLE `sales_sample_round` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_sample_transfer`
--

DROP TABLE IF EXISTS `sales_sample_transfer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_sample_transfer` (
  `transfer_id` bigint NOT NULL AUTO_INCREMENT COMMENT '转移记录ID',
  `order_id` bigint NOT NULL COMMENT '样品单ID',
  `order_no` varchar(50) DEFAULT NULL COMMENT '样品单号',
  `transfer_no` varchar(50) DEFAULT NULL COMMENT '转移单号',
  `product_id` bigint DEFAULT NULL COMMENT '建档产品ID',
  `bom_id` bigint DEFAULT NULL COMMENT '建档BOM ID',
  `routing_id` bigint DEFAULT NULL COMMENT '建档路线ID',
  `product_action` varchar(20) DEFAULT 'NONE' COMMENT '产品建档动作: NONE/CREATE/UPDATE',
  `bom_action` varchar(20) DEFAULT 'NONE' COMMENT 'BOM动作: NONE/CREATE/SKIP_NO_PROCESS',
  `routing_action` varchar(20) DEFAULT 'NONE' COMMENT '路线动作: NONE/CREATE/SKIP_NO_PROCESS',
  `status` varchar(20) DEFAULT 'SUCCESS' COMMENT '转移结果: SUCCESS/PARTIAL/FAILED',
  `detail` text COMMENT '转移明细说明',
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`transfer_id`),
  KEY `idx_transfer_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='样品单产品资料转移记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_sample_transfer`
--

LOCK TABLES `sales_sample_transfer` WRITE;
/*!40000 ALTER TABLE `sales_sample_transfer` DISABLE KEYS */;
INSERT INTO `sales_sample_transfer` VALUES (1,1,'SP2608040001','TF2608040002',1,1,1,'CREATE','CREATE','CREATE','SUCCESS','产品[QT2608040001]新建建档(待审核)\nBOM[BOM-QT2608040001-SAMPLE]生成草稿(2条明细)\n工艺路线[RTE-QT2608040001-SAMPLE]生成草稿(2道工序)','xiaoshou0','2026-08-04 16:07:19');
/*!40000 ALTER TABLE `sales_sample_transfer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_attachment`
--

DROP TABLE IF EXISTS `sys_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '附件ID',
  `biz_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型（如: sales_order, product等）',
  `biz_id` bigint NOT NULL COMMENT '业务记录ID',
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '链路追踪ID',
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
  `file_path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储路径',
  `file_size` bigint DEFAULT '0' COMMENT '文件大小（字节）',
  `file_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT 'MIME类型',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `create_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`,`biz_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用附件表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_attachment`
--

LOCK TABLES `sys_attachment` WRITE;
/*!40000 ALTER TABLE `sys_attachment` DISABLE KEYS */;
INSERT INTO `sys_attachment` VALUES (1,'inquiry',1,'e32451d48af1460e','温度检测.jpg','inquiry/2026-08-04/21d5ab69-c25b-4ad2-8d27-b16cc625231f.jpg',231093,'image/jpeg',0,'','xiaoshou0','2026-08-04 15:20:05','','2026-08-04 15:20:04',0),(2,'inquiry',1,'e32451d48af1460e','硬件检测.jpg','inquiry/2026-08-04/1ccdfaea-9c5a-46e1-83be-53adfc1f1ad4.jpg',215153,'image/jpeg',0,'','xiaoshou0','2026-08-04 15:20:05','','2026-08-04 15:20:04',0),(3,'inquiry',1,'e32451d48af1460e','硬盘信息.jpg','inquiry/2026-08-04/50b9f862-6d4d-40af-aa19-7bf6d8327923.jpg',170626,'image/jpeg',0,'','xiaoshou0','2026-08-04 15:20:05','','2026-08-04 15:20:04',0),(4,'quotation',1,NULL,'AIDA64检测.jpg','quotation/2026-08-04/3594efac-1528-44ee-98a4-84925dfbe4be.jpg',282409,'image/jpeg',0,'','xiaoshou0','2026-08-04 15:24:27','','2026-08-04 15:24:27',0),(5,'quotation',1,NULL,'AIDA64检测.jpg','quotation/2026-08-04/c2c85054-814e-437f-a814-0c8f7fe8624f.jpg',282409,'image/jpeg',0,'','xiaoshou0','2026-08-04 15:25:36','','2026-08-04 15:25:35',0),(6,'inquiry',2,'cdb02bd1b7ab4dc2','硬件检测.jpg','inquiry/2026-08-04/6b150316-4f58-42fb-a36f-3c10b3d46429.jpg',215153,'image/jpeg',0,'','xiaoshou0','2026-08-04 15:48:39','','2026-08-04 15:48:38',0),(7,'inquiry',2,'cdb02bd1b7ab4dc2','硬盘信息.jpg','inquiry/2026-08-04/31717ad6-c98f-46ba-9eca-27d1462cbf78.jpg',170626,'image/jpeg',0,'','xiaoshou0','2026-08-04 15:48:39','','2026-08-04 15:48:38',0),(8,'quotation',3,NULL,'硬件检测.jpg','quotation/2026-08-04/393fab75-cc87-4369-8d60-02b4bfb173c9.jpg',215153,'image/jpeg',0,'','xiaoshou0','2026-08-04 16:02:53','','2026-08-04 16:02:52',0);
/*!40000 ALTER TABLE `sys_attachment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `config_id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` varchar(500) NOT NULL COMMENT '配置值',
  `config_name` varchar(200) NOT NULL COMMENT '配置名称',
  `config_group` varchar(50) NOT NULL DEFAULT 'system' COMMENT '分组(system/business/email/sms)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `is_active` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_config`
--

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
INSERT INTO `sys_config` VALUES (1,'default_lead_time','15','默认交期天数','business',NULL,1,1,'2026-07-28 20:20:19','2026-07-28 20:20:19'),(2,'low_stock_threshold','10','低库存预警阈值','inventory',NULL,2,1,'2026-07-28 20:20:19','2026-07-28 20:20:19'),(3,'order_auto_close_days','30','订单自动关闭天数','sales',NULL,3,1,'2026-07-28 20:20:19','2026-07-28 20:20:19'),(4,'smtp_host','smtp.example.com','SMTP服务器','email',NULL,4,1,'2026-07-28 20:20:19','2026-07-28 20:20:19'),(5,'smtp_port','587','SMTP端口','email',NULL,5,1,'2026-07-28 20:20:19','2026-07-28 20:20:19'),(6,'sms_api_key','','短信API密钥','sms',NULL,6,1,'2026-07-28 20:20:19','2026-07-28 20:20:19');
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `dept_id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父部门ID',
  `dept_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门名称',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '删除标志',
  `create_by` bigint NOT NULL DEFAULT '1' COMMENT '更新者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint NOT NULL DEFAULT '1' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`dept_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dept`
--

LOCK TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` VALUES (1,0,'JJX公司',0,'张三','13800138001','zhangsan@jjx.com','0','0',1,'2026-03-18 15:57:47',1,'2026-03-18 15:57:47'),(2,1,'研发部门',1,'李四','13800138002','lisi@jjx.com','0','0',1,'2026-03-18 15:57:47',1,'2026-03-18 15:57:47'),(3,1,'市场部门',2,'王五','13800138003','wangwu@jjx.com','0','0',1,'2026-03-18 15:57:47',1,'2026-03-18 15:57:47'),(4,0,'办公室',0,'','','','0','0',1,'2026-03-18 15:57:47',1,'2026-03-18 15:57:47');
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict`
--

DROP TABLE IF EXISTS `sys_dict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict` (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典ID',
  `dict_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典编码(如: order_status)',
  `dict_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典名称(如: 订单状态)',
  `dict_group` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分组（sales/production/inventory等）',
  `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '备注',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `is_active` tinyint DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `uk_dict_code` (`dict_code`,`tenant_id`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=117 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict`
--

LOCK TABLES `sys_dict` WRITE;
/*!40000 ALTER TABLE `sys_dict` DISABLE KEYS */;
INSERT INTO `sys_dict` VALUES (1,'order_status','订单状态','sales',NULL,0,1,'2026-05-17 18:28:11','2026-07-28 20:15:34',0,1),(2,'user_type','用户类型','system',NULL,0,1,'2026-05-17 18:28:53','2026-07-28 20:15:34',0,1),(3,'user_sales','销售人员','system',NULL,0,1,'2026-06-09 22:50:59','2026-07-28 20:15:34',0,1),(4,'process_type','工序类型','production','工序类型',0,1,'2026-06-12 10:24:25','2026-07-28 20:15:34',0,1),(5,'process_category','工序类目','production','',0,1,'2026-06-12 16:53:46','2026-07-28 20:15:34',0,1),(62,'sales_quotation_status','报价单状态','sales','由枚举 QuotationStatus.java[QuotationStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(63,'sales_inquiry_status','询价单状态','sales','由枚举 InquiryStatus.java[InquiryStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(64,'sales_order_status','销售订单状态','sales','由枚举 OrderStatus.java[OrderStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(65,'sales_order_type','销售订单类型','sales','由枚举 OrderTypeEnum.java[OrderTypeEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(66,'sales_order_payment_status','销售订单付款状态','sales','由枚举 PaymentStatusEnum.java[PaymentStatusEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(67,'sales_order_prod_status','销售订单生产状态','sales','由枚举 ProdStatusEnum.java[ProdStatusEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(68,'sales_sample_order_status','样品单状态','sales','由枚举 SampleOrderStatusEnum.java[SampleOrderStatusEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(69,'production_order_status','生产工单状态','production','由枚举 OrderStatusEnum.java[OrderStatusEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(70,'production_order_type','生产工单类型','production','由枚举 OrderTypeEnum.java[OrderTypeEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(71,'production_execution_status','工序执行状态','production','由枚举 ExecutionStatusEnum.java[ExecutionStatusEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(72,'production_record_type','生产记录类型','production','由枚举 RecordTypeEnum.java[RecordTypeEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(73,'purchase_order_status','采购订单状态','purchase','由枚举 PurchaseOrderStatus.java[PurchaseOrderStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(74,'purchase_approval_status','采购审批状态','purchase','由枚举 ApprovalStatusEnum.java[ApprovalStatusEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(75,'purchase_order_type','采购订单类型','purchase','由枚举 OrderType.java[OrderType] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(76,'purchase_payment_status','采购付款状态','purchase','由枚举 PaymentStatus.java[PaymentStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(77,'purchase_receipt_status','采购收货状态','purchase','由枚举 ReceiptStatus.java[ReceiptStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(78,'purchase_inquiry_status','采购询价状态','purchase','由枚举 InquiryStatus.java[InquiryStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(79,'purchase_material_inquiry_status','物料询价状态','purchase','由枚举 MaterialInquiryStatus.java[MaterialInquiryStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(80,'purchase_document_status','采购单据状态','purchase','由枚举 DocumentStatus.java[DocumentStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(81,'purchase_document_type','采购单据类型','purchase','由枚举 DocumentType.java[DocumentType] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(82,'purchase_inspection_result','来料检验结果','purchase','由枚举 InspectionResult.java[InspectionResult] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(83,'purchase_payment_method','采购付款方式','purchase','由枚举 PaymentMethod.java[PaymentMethod] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(84,'purchase_supplier_type','供应商类型','purchase','由枚举 SupplierTypeEnum.java[SupplierTypeEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(85,'inventory_inbound_type','入库类型','inventory','由枚举 InboundTypeEnum.java[InboundTypeEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(86,'inventory_outbound_type','出库类型','inventory','由枚举 OutboundTypeEnum.java[OutboundTypeEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(87,'inventory_transaction_type','库存流水类型','inventory','由枚举 TransactionTypeEnum.java[TransactionTypeEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(88,'inventory_stock_status','库存状态','inventory','由枚举 StockStatusEnum.java[StockStatusEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(89,'inventory_stock_item_status','库存明细状态','inventory','由枚举 StockItemStatusEnum.java[StockItemStatusEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(90,'inventory_alert_type','库存预警类型','inventory','由枚举 AlertTypeEnum.java[AlertTypeEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(91,'inventory_alert_level','库存预警级别','inventory','由枚举 AlertLevelEnum.java[AlertLevelEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(92,'inventory_material_type','物料类型','inventory','由枚举 MaterialEnums.java[Type] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(93,'inventory_material_status','物料状态','inventory','由枚举 MaterialEnums.java[Status] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(94,'product_film_type','菲林类型','product','由枚举 FilmTypeEnum.java[FilmTypeEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(95,'product_process_category','工序类目','product','由枚举 ProcessCategoryEnum.java[ProcessCategoryEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(96,'product_process_type','工序类型','product','由枚举 ProcessTypeEnum.java[ProcessTypeEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(97,'product_type','产品类型','product','由枚举 ProductEnums.java[Type] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(98,'product_status','产品状态','product','由枚举 ProductEnums.java[Status] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(99,'product_bom_type','BOM类型','product','由枚举 ProductEnums.java[BomType] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(100,'product_bom_status','BOM状态','product','由枚举 ProductEnums.java[BomStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(101,'product_source_type','BOM来源类型','product','由枚举 ProductEnums.java[SourceType] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(102,'product_bom_layer','BOM层结构','product','由枚举 ProductEnums.java[BomLayer] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(103,'product_route_status','工艺路线状态','product','由枚举 ProductEnums.java[RouteStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(104,'product_step_type','工序步骤类型','product','由枚举 ProductEnums.java[StepType] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(105,'product_lifecycle_status','产品生命周期状态','product','由枚举 ProductEnums.java[LifecycleStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(106,'product_instance_status','产品实例状态','product','由枚举 ProductEnums.java[InstanceStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(107,'product_task_status','产品任务状态','product','由枚举 ProductEnums.java[TaskStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(108,'product_task_type','产品任务类型','product','由枚举 ProductEnums.java[TaskType] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(109,'product_film_status','菲林状态','product','由枚举 ProductEnums.java[FilmStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(110,'product_config_option_type','配置项类型','product','由枚举 ProductEnums.java[ConfigOptionType] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(111,'product_config_model_status','配置模型状态','product','由枚举 ProductEnums.java[ConfigModelStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(112,'product_category_status','产品分类状态','product','由枚举 ProductEnums.java[CategoryStatus] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(113,'common_approve_status','通用审批状态','common','由枚举 ApproveStatusEnum.java[ApproveStatusEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(114,'common_status','通用状态','common','由枚举 StatusEnum.java[StatusEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(115,'common_yes_no','是否','common','由枚举 YesNoEnum.java[YesNoEnum] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(116,'system_user_type','用户类型','system','由枚举 UserType.java[UserType] 自动导入',0,1,'2026-08-01 11:16:55','2026-08-01 11:16:55',0,1);
/*!40000 ALTER TABLE `sys_dict` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_item`
--

DROP TABLE IF EXISTS `sys_dict_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典项ID',
  `dict_code` varchar(50) NOT NULL COMMENT '字典编码(关联sys_dict)',
  `item_key` varchar(100) NOT NULL COMMENT '字典键(英文标识，如: draft, paid)',
  `item_value` varchar(50) NOT NULL COMMENT '字典值(实际存储值，如: 0, 1, 2 或 draft, paid)',
  `label` varchar(100) NOT NULL COMMENT '显示文本(前端展示用，如: 草稿, 已支付)',
  `remark` text COMMENT '备注',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `is_active` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `ext_data` json DEFAULT NULL COMMENT '扩展数据(如: {color:"red", icon:"star"})',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  PRIMARY KEY (`item_id`),
  UNIQUE KEY `uk_dict_item` (`dict_code`,`item_key`,`tenant_id`,`deleted`),
  KEY `idx_dict_active_sort` (`dict_code`,`is_active`,`sort_order`,`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=995 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据表(key-英文键, value-存储值, label-显示文本)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_item`
--

LOCK TABLES `sys_dict_item` WRITE;
/*!40000 ALTER TABLE `sys_dict_item` DISABLE KEYS */;
INSERT INTO `sys_dict_item` VALUES (1,'order_status','draft','0','草稿',NULL,1,1,NULL,'2026-05-17 16:53:32','2026-05-17 16:53:32',0,1),(2,'order_status','pending','1','待支付',NULL,2,1,NULL,'2026-05-17 16:53:32','2026-05-17 16:53:32',0,1),(3,'order_status','paid','2','已支付',NULL,3,1,NULL,'2026-05-17 16:53:32','2026-05-17 16:53:32',0,1),(4,'order_status','shipped','3','已发货',NULL,4,1,NULL,'2026-05-17 16:53:32','2026-05-17 16:53:32',0,1),(5,'order_status','completed','4','已完成',NULL,5,1,NULL,'2026-05-17 16:53:32','2026-05-17 16:53:32',0,1),(6,'order_status','cancelled','5','已取消',NULL,6,1,NULL,'2026-05-17 16:53:32','2026-05-17 16:53:32',0,1),(7,'user_type','normal','0','普通用户',NULL,1,1,'{\"color\": \"blue\"}','2026-05-17 16:53:32','2026-05-17 16:53:32',0,1),(8,'user_type','vip','1','VIP会员',NULL,2,1,'{\"color\": \"gold\"}','2026-05-17 16:53:32','2026-05-17 16:53:32',0,1),(9,'user_type','svip','2','超级VIP',NULL,3,1,'{\"color\": \"purple\"}','2026-05-17 16:53:32','2026-05-17 16:53:32',0,1),(10,'user_sales','user_sales_role','7','销售人员角色','',0,1,NULL,'2026-06-09 23:01:11','2026-06-09 23:01:11',0,1),(27,'dc','k','v','L',NULL,0,1,NULL,'2026-07-28 21:27:05','2026-07-28 21:27:05',0,1),(710,'sales_quotation_status','DRAFT','0','草稿','{\"enum\": \"DRAFT\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}',0,1,'{\"enum\": \"DRAFT\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(711,'sales_quotation_status','SENT','1','已发送','{\"enum\": \"SENT\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}',1,1,'{\"enum\": \"SENT\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(712,'sales_quotation_status','ACCEPTED','2','已确认','{\"enum\": \"ACCEPTED\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}',2,1,'{\"enum\": \"ACCEPTED\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(713,'sales_quotation_status','REJECTED','3','已拒绝','{\"enum\": \"REJECTED\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}',3,1,'{\"enum\": \"REJECTED\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(714,'sales_quotation_status','EXPIRED','4','已过期','{\"enum\": \"EXPIRED\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}',4,1,'{\"enum\": \"EXPIRED\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(715,'sales_quotation_status','PENDING_REVIEW','5','待审核','{\"enum\": \"PENDING_REVIEW\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}',5,1,'{\"enum\": \"PENDING_REVIEW\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(716,'sales_quotation_status','APPROVED','6','已审核','{\"enum\": \"APPROVED\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}',6,1,'{\"enum\": \"APPROVED\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(717,'sales_inquiry_status','DRAFT','0','草稿','{\"enum\": \"DRAFT\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}',0,1,'{\"enum\": \"DRAFT\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(718,'sales_inquiry_status','PENDING','1','待处理','{\"enum\": \"PENDING\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}',1,1,'{\"enum\": \"PENDING\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(719,'sales_inquiry_status','SENT','2','已发送','{\"enum\": \"SENT\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}',2,1,'{\"enum\": \"SENT\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(720,'sales_inquiry_status','CONVERTED','3','已转报价','{\"enum\": \"CONVERTED\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}',3,1,'{\"enum\": \"CONVERTED\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(721,'sales_inquiry_status','ACCEPTED','4','已确认','{\"enum\": \"ACCEPTED\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}',4,1,'{\"enum\": \"ACCEPTED\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(722,'sales_inquiry_status','REJECTED','5','已拒绝','{\"enum\": \"REJECTED\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}',5,1,'{\"enum\": \"REJECTED\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(723,'sales_inquiry_status','EXPIRED','6','已过期','{\"enum\": \"EXPIRED\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}',6,1,'{\"enum\": \"EXPIRED\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(724,'sales_order_status','DRAFT','1','草稿','{\"enum\": \"DRAFT\", \"origin\": \"OrderStatus.java.OrderStatus\"}',0,1,'{\"enum\": \"DRAFT\", \"origin\": \"OrderStatus.java.OrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(725,'sales_order_status','PENDING_REVIEW','2','待审核','{\"enum\": \"PENDING_REVIEW\", \"origin\": \"OrderStatus.java.OrderStatus\"}',1,1,'{\"enum\": \"PENDING_REVIEW\", \"origin\": \"OrderStatus.java.OrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(726,'sales_order_status','REVIEWING','3','审核中','{\"enum\": \"REVIEWING\", \"origin\": \"OrderStatus.java.OrderStatus\"}',2,1,'{\"enum\": \"REVIEWING\", \"origin\": \"OrderStatus.java.OrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(727,'sales_order_status','APPROVED','4','已审核','{\"enum\": \"APPROVED\", \"origin\": \"OrderStatus.java.OrderStatus\"}',3,1,'{\"enum\": \"APPROVED\", \"origin\": \"OrderStatus.java.OrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(728,'sales_order_status','REJECTED','5','已驳回','{\"enum\": \"REJECTED\", \"origin\": \"OrderStatus.java.OrderStatus\"}',4,1,'{\"enum\": \"REJECTED\", \"origin\": \"OrderStatus.java.OrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(729,'sales_order_status','PENDING_CUSTOMER_CONFIRM','6','待客户确认','{\"enum\": \"PENDING_CUSTOMER_CONFIRM\", \"origin\": \"OrderStatus.java.OrderStatus\"}',5,1,'{\"enum\": \"PENDING_CUSTOMER_CONFIRM\", \"origin\": \"OrderStatus.java.OrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(730,'sales_order_status','CONFIRMED','7','已确认','{\"enum\": \"CONFIRMED\", \"origin\": \"OrderStatus.java.OrderStatus\"}',6,1,'{\"enum\": \"CONFIRMED\", \"origin\": \"OrderStatus.java.OrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(731,'sales_order_status','IN_PRODUCTION','8','生产中','{\"enum\": \"IN_PRODUCTION\", \"origin\": \"OrderStatus.java.OrderStatus\"}',7,1,'{\"enum\": \"IN_PRODUCTION\", \"origin\": \"OrderStatus.java.OrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(732,'sales_order_status','SHIPPED','9','已发货','{\"enum\": \"SHIPPED\", \"origin\": \"OrderStatus.java.OrderStatus\"}',8,1,'{\"enum\": \"SHIPPED\", \"origin\": \"OrderStatus.java.OrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(733,'sales_order_status','COMPLETED','10','已完成','{\"enum\": \"COMPLETED\", \"origin\": \"OrderStatus.java.OrderStatus\"}',9,1,'{\"enum\": \"COMPLETED\", \"origin\": \"OrderStatus.java.OrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(734,'sales_order_status','CANCELLED','11','已取消','{\"enum\": \"CANCELLED\", \"origin\": \"OrderStatus.java.OrderStatus\"}',10,1,'{\"enum\": \"CANCELLED\", \"origin\": \"OrderStatus.java.OrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(735,'sales_order_status','EXPIRED','12','已过期','{\"enum\": \"EXPIRED\", \"origin\": \"OrderStatus.java.OrderStatus\"}',11,1,'{\"enum\": \"EXPIRED\", \"origin\": \"OrderStatus.java.OrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(736,'sales_order_type','STANDARD','1','标准订单','{\"enum\": \"STANDARD\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}',0,1,'{\"enum\": \"STANDARD\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(737,'sales_order_type','SAMPLE','2','样品订单','{\"enum\": \"SAMPLE\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}',1,1,'{\"enum\": \"SAMPLE\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(738,'sales_order_payment_status','UNPAID','1','未支付','{\"enum\": \"UNPAID\", \"origin\": \"PaymentStatusEnum.java.PaymentStatusEnum\"}',0,1,'{\"enum\": \"UNPAID\", \"origin\": \"PaymentStatusEnum.java.PaymentStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(739,'sales_order_payment_status','PAYING','2','支付中','{\"enum\": \"PAYING\", \"origin\": \"PaymentStatusEnum.java.PaymentStatusEnum\"}',1,1,'{\"enum\": \"PAYING\", \"origin\": \"PaymentStatusEnum.java.PaymentStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(740,'sales_order_payment_status','PAID','3','已支付','{\"enum\": \"PAID\", \"origin\": \"PaymentStatusEnum.java.PaymentStatusEnum\"}',2,1,'{\"enum\": \"PAID\", \"origin\": \"PaymentStatusEnum.java.PaymentStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(741,'sales_order_payment_status','PARTIAL_PAID','4','部分支付','{\"enum\": \"PARTIAL_PAID\", \"origin\": \"PaymentStatusEnum.java.PaymentStatusEnum\"}',3,1,'{\"enum\": \"PARTIAL_PAID\", \"origin\": \"PaymentStatusEnum.java.PaymentStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(742,'sales_order_payment_status','REFUNDED','5','已退款','{\"enum\": \"REFUNDED\", \"origin\": \"PaymentStatusEnum.java.PaymentStatusEnum\"}',4,1,'{\"enum\": \"REFUNDED\", \"origin\": \"PaymentStatusEnum.java.PaymentStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(743,'sales_order_prod_status','NONE','1','无生产','{\"enum\": \"NONE\", \"origin\": \"ProdStatusEnum.java.ProdStatusEnum\"}',0,1,'{\"enum\": \"NONE\", \"origin\": \"ProdStatusEnum.java.ProdStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(744,'sales_order_prod_status','PARTIAL_PRODUCING','2','部分生产中','{\"enum\": \"PARTIAL_PRODUCING\", \"origin\": \"ProdStatusEnum.java.ProdStatusEnum\"}',1,1,'{\"enum\": \"PARTIAL_PRODUCING\", \"origin\": \"ProdStatusEnum.java.ProdStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(745,'sales_order_prod_status','FULL_PRODUCING','3','全部生产中','{\"enum\": \"FULL_PRODUCING\", \"origin\": \"ProdStatusEnum.java.ProdStatusEnum\"}',2,1,'{\"enum\": \"FULL_PRODUCING\", \"origin\": \"ProdStatusEnum.java.ProdStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(746,'sales_order_prod_status','COMPLETED','4','生产完成','{\"enum\": \"COMPLETED\", \"origin\": \"ProdStatusEnum.java.ProdStatusEnum\"}',3,1,'{\"enum\": \"COMPLETED\", \"origin\": \"ProdStatusEnum.java.ProdStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(747,'sales_sample_order_status','CREATED','1','样品需求已创建','{\"enum\": \"CREATED\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}',0,1,'{\"enum\": \"CREATED\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(748,'sales_sample_order_status','PENDING_REVIEW','2','待审核','{\"enum\": \"PENDING_REVIEW\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}',1,1,'{\"enum\": \"PENDING_REVIEW\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(749,'sales_sample_order_status','ENGINEERING','3','工程打样中','{\"enum\": \"ENGINEERING\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}',2,1,'{\"enum\": \"ENGINEERING\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(750,'sales_sample_order_status','SAMPLE_READY','4','样品待送样','{\"enum\": \"SAMPLE_READY\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}',3,1,'{\"enum\": \"SAMPLE_READY\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(751,'sales_sample_order_status','SAMPLE_SENT','5','已送样待确认','{\"enum\": \"SAMPLE_SENT\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}',4,1,'{\"enum\": \"SAMPLE_SENT\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(752,'sales_sample_order_status','CONFIRMED','6','样品确认','{\"enum\": \"CONFIRMED\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}',5,1,'{\"enum\": \"CONFIRMED\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(753,'sales_sample_order_status','TRANSFERRED','7','已转量产','{\"enum\": \"TRANSFERRED\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}',6,1,'{\"enum\": \"TRANSFERRED\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(754,'sales_sample_order_status','CLOSED','8','已关闭','{\"enum\": \"CLOSED\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}',7,1,'{\"enum\": \"CLOSED\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(755,'sales_sample_order_status','REJECTED','9','客户退回','{\"enum\": \"REJECTED\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}',8,1,'{\"enum\": \"REJECTED\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(756,'sales_sample_order_status','CANCELLED','10','已取消','{\"enum\": \"CANCELLED\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}',9,1,'{\"enum\": \"CANCELLED\", \"origin\": \"SampleOrderStatusEnum.java.SampleOrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(757,'production_order_status','DRAFT','0','草稿','{\"enum\": \"DRAFT\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}',0,1,'{\"enum\": \"DRAFT\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(758,'production_order_status','PENDING_APPROVAL','1','待审核','{\"enum\": \"PENDING_APPROVAL\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}',1,1,'{\"enum\": \"PENDING_APPROVAL\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(759,'production_order_status','APPROVED','2','已审核','{\"enum\": \"APPROVED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}',2,1,'{\"enum\": \"APPROVED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(760,'production_order_status','REJECTED','3','已驳回','{\"enum\": \"REJECTED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}',3,1,'{\"enum\": \"REJECTED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(761,'production_order_status','PLANNED','4','已计划','{\"enum\": \"PLANNED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}',4,1,'{\"enum\": \"PLANNED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(762,'production_order_status','PENDING_START','5','待开始','{\"enum\": \"PENDING_START\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}',5,1,'{\"enum\": \"PENDING_START\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(763,'production_order_status','IN_PROGRESS','6','进行中','{\"enum\": \"IN_PROGRESS\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}',6,1,'{\"enum\": \"IN_PROGRESS\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(764,'production_order_status','PAUSED','7','已暂停','{\"enum\": \"PAUSED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}',7,1,'{\"enum\": \"PAUSED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(765,'production_order_status','COMPLETED','8','已完成','{\"enum\": \"COMPLETED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}',8,1,'{\"enum\": \"COMPLETED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(766,'production_order_status','CANCELLED','9','已取消','{\"enum\": \"CANCELLED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}',9,1,'{\"enum\": \"CANCELLED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(767,'production_order_status','CLOSED','10','已关闭','{\"enum\": \"CLOSED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}',10,1,'{\"enum\": \"CLOSED\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(768,'production_order_status','OVERDUE','11','已超期','{\"enum\": \"OVERDUE\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}',11,1,'{\"enum\": \"OVERDUE\", \"origin\": \"OrderStatusEnum.java.OrderStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(769,'production_order_type','PLAN','PLAN','生产计划','{\"enum\": \"PLAN\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}',0,1,'{\"enum\": \"PLAN\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(770,'production_order_type','ORDER','ORDER','生产工单','{\"enum\": \"ORDER\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}',1,1,'{\"enum\": \"ORDER\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(771,'production_order_type','TRIAL','TRIAL','试产订单','{\"enum\": \"TRIAL\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}',2,1,'{\"enum\": \"TRIAL\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(772,'production_order_type','REWORK','REWORK','返工订单','{\"enum\": \"REWORK\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}',3,1,'{\"enum\": \"REWORK\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(773,'production_order_type','SAMPLE','SAMPLE','样品订单','{\"enum\": \"SAMPLE\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}',4,1,'{\"enum\": \"SAMPLE\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(774,'production_order_type','REPAIR','REPAIR','维修订单','{\"enum\": \"REPAIR\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}',5,1,'{\"enum\": \"REPAIR\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(775,'production_order_type','SPARE','SPARE','备件订单','{\"enum\": \"SPARE\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}',6,1,'{\"enum\": \"SPARE\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(776,'production_order_type','URGENT','URGENT','紧急订单','{\"enum\": \"URGENT\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}',7,1,'{\"enum\": \"URGENT\", \"origin\": \"OrderTypeEnum.java.OrderTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(777,'production_execution_status','PENDING','0','待执行','{\"enum\": \"PENDING\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}',0,1,'{\"enum\": \"PENDING\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(778,'production_execution_status','PREPARING','1','准备中','{\"enum\": \"PREPARING\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}',1,1,'{\"enum\": \"PREPARING\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(779,'production_execution_status','EXECUTING','2','执行中','{\"enum\": \"EXECUTING\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}',2,1,'{\"enum\": \"EXECUTING\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(780,'production_execution_status','PAUSED','3','已暂停','{\"enum\": \"PAUSED\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}',3,1,'{\"enum\": \"PAUSED\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(781,'production_execution_status','COMPLETED','4','已完成','{\"enum\": \"COMPLETED\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}',4,1,'{\"enum\": \"COMPLETED\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(782,'production_execution_status','SKIPPED','5','已跳过','{\"enum\": \"SKIPPED\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}',5,1,'{\"enum\": \"SKIPPED\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(783,'production_execution_status','CANCELLED','6','已取消','{\"enum\": \"CANCELLED\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}',6,1,'{\"enum\": \"CANCELLED\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(784,'production_execution_status','OVERDUE','7','已超期','{\"enum\": \"OVERDUE\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}',7,1,'{\"enum\": \"OVERDUE\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(785,'production_execution_status','ABNORMAL','8','异常中','{\"enum\": \"ABNORMAL\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}',8,1,'{\"enum\": \"ABNORMAL\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(786,'production_execution_status','PENDING_CONFIRMATION','9','待确认','{\"enum\": \"PENDING_CONFIRMATION\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}',9,1,'{\"enum\": \"PENDING_CONFIRMATION\", \"origin\": \"ExecutionStatusEnum.java.ExecutionStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(787,'production_record_type','START','START','开始记录','{\"enum\": \"START\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',0,1,'{\"enum\": \"START\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(788,'production_record_type','PAUSE','PAUSE','暂停记录','{\"enum\": \"PAUSE\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',1,1,'{\"enum\": \"PAUSE\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(789,'production_record_type','RESUME','RESUME','恢复记录','{\"enum\": \"RESUME\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',2,1,'{\"enum\": \"RESUME\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(790,'production_record_type','COMPLETE','COMPLETE','完成记录','{\"enum\": \"COMPLETE\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',3,1,'{\"enum\": \"COMPLETE\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(791,'production_record_type','QUALITY','QUALITY','质量记录','{\"enum\": \"QUALITY\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',4,1,'{\"enum\": \"QUALITY\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(792,'production_record_type','ISSUE','ISSUE','问题记录','{\"enum\": \"ISSUE\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',5,1,'{\"enum\": \"ISSUE\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(793,'production_record_type','PARAMETER','PARAMETER','参数记录','{\"enum\": \"PARAMETER\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',6,1,'{\"enum\": \"PARAMETER\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(794,'production_record_type','STATUS','STATUS','状态记录','{\"enum\": \"STATUS\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',7,1,'{\"enum\": \"STATUS\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(795,'production_record_type','OPERATION','OPERATION','操作记录','{\"enum\": \"OPERATION\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',8,1,'{\"enum\": \"OPERATION\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(796,'production_record_type','DATA','DATA','数据记录','{\"enum\": \"DATA\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',9,1,'{\"enum\": \"DATA\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(797,'production_record_type','EQUIPMENT','EQUIPMENT','设备记录','{\"enum\": \"EQUIPMENT\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',10,1,'{\"enum\": \"EQUIPMENT\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(798,'production_record_type','MATERIAL','MATERIAL','物料记录','{\"enum\": \"MATERIAL\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',11,1,'{\"enum\": \"MATERIAL\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(799,'production_record_type','TIME','TIME','工时记录','{\"enum\": \"TIME\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',12,1,'{\"enum\": \"TIME\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(800,'production_record_type','ATTACHMENT','ATTACHMENT','附件记录','{\"enum\": \"ATTACHMENT\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',13,1,'{\"enum\": \"ATTACHMENT\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(801,'production_record_type','REMARK','REMARK','备注记录','{\"enum\": \"REMARK\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',14,1,'{\"enum\": \"REMARK\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(802,'production_record_type','SYSTEM','SYSTEM','系统记录','{\"enum\": \"SYSTEM\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}',15,1,'{\"enum\": \"SYSTEM\", \"origin\": \"RecordTypeEnum.java.RecordTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(803,'purchase_order_status','DRAFT','draft','草稿','{\"enum\": \"DRAFT\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}',0,1,'{\"enum\": \"DRAFT\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(804,'purchase_order_status','INQUIRY','inquiry','询价中','{\"enum\": \"INQUIRY\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}',1,1,'{\"enum\": \"INQUIRY\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(805,'purchase_order_status','COMPARING','comparing','比价中','{\"enum\": \"COMPARING\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}',2,1,'{\"enum\": \"COMPARING\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(806,'purchase_order_status','SUBMITTED','submitted','已提交','{\"enum\": \"SUBMITTED\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}',3,1,'{\"enum\": \"SUBMITTED\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(807,'purchase_order_status','APPROVED','approved','已批准','{\"enum\": \"APPROVED\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}',4,1,'{\"enum\": \"APPROVED\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(808,'purchase_order_status','IN_PROGRESS','in_progress','执行中','{\"enum\": \"IN_PROGRESS\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}',5,1,'{\"enum\": \"IN_PROGRESS\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(809,'purchase_order_status','COMPLETED','completed','已完成','{\"enum\": \"COMPLETED\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}',6,1,'{\"enum\": \"COMPLETED\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(810,'purchase_order_status','CLOSED','closed','已关闭','{\"enum\": \"CLOSED\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}',7,1,'{\"enum\": \"CLOSED\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(811,'purchase_order_status','CANCELLED','cancelled','已取消','{\"enum\": \"CANCELLED\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}',8,1,'{\"enum\": \"CANCELLED\", \"origin\": \"PurchaseOrderStatus.java.PurchaseOrderStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(812,'purchase_approval_status','DRAFT','1','草稿','{\"enum\": \"DRAFT\", \"origin\": \"ApprovalStatusEnum.java.ApprovalStatusEnum\"}',0,1,'{\"enum\": \"DRAFT\", \"origin\": \"ApprovalStatusEnum.java.ApprovalStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(813,'purchase_approval_status','CANCELLED','2','已取消','{\"enum\": \"CANCELLED\", \"origin\": \"ApprovalStatusEnum.java.ApprovalStatusEnum\"}',1,1,'{\"enum\": \"CANCELLED\", \"origin\": \"ApprovalStatusEnum.java.ApprovalStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(814,'purchase_approval_status','PENDING','3','待审批','{\"enum\": \"PENDING\", \"origin\": \"ApprovalStatusEnum.java.ApprovalStatusEnum\"}',2,1,'{\"enum\": \"PENDING\", \"origin\": \"ApprovalStatusEnum.java.ApprovalStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(815,'purchase_approval_status','APPROVED','4','已批准','{\"enum\": \"APPROVED\", \"origin\": \"ApprovalStatusEnum.java.ApprovalStatusEnum\"}',3,1,'{\"enum\": \"APPROVED\", \"origin\": \"ApprovalStatusEnum.java.ApprovalStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(816,'purchase_approval_status','REJECTED','5','已拒绝','{\"enum\": \"REJECTED\", \"origin\": \"ApprovalStatusEnum.java.ApprovalStatusEnum\"}',4,1,'{\"enum\": \"REJECTED\", \"origin\": \"ApprovalStatusEnum.java.ApprovalStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(817,'purchase_order_type','NORMAL','normal','正常','{\"enum\": \"NORMAL\", \"origin\": \"OrderType.java.OrderType\"}',0,1,'{\"enum\": \"NORMAL\", \"origin\": \"OrderType.java.OrderType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(818,'purchase_order_type','URGENT','urgent','紧急','{\"enum\": \"URGENT\", \"origin\": \"OrderType.java.OrderType\"}',1,1,'{\"enum\": \"URGENT\", \"origin\": \"OrderType.java.OrderType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(819,'purchase_payment_status','PENDING','pending','待付款','{\"enum\": \"PENDING\", \"origin\": \"PaymentStatus.java.PaymentStatus\"}',0,1,'{\"enum\": \"PENDING\", \"origin\": \"PaymentStatus.java.PaymentStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(820,'purchase_payment_status','PARTIALLY_PAID','partially_paid','部分付款','{\"enum\": \"PARTIALLY_PAID\", \"origin\": \"PaymentStatus.java.PaymentStatus\"}',1,1,'{\"enum\": \"PARTIALLY_PAID\", \"origin\": \"PaymentStatus.java.PaymentStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(821,'purchase_payment_status','PAID','paid','已付款','{\"enum\": \"PAID\", \"origin\": \"PaymentStatus.java.PaymentStatus\"}',2,1,'{\"enum\": \"PAID\", \"origin\": \"PaymentStatus.java.PaymentStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(822,'purchase_payment_status','COMPLETED','completed','已完成','{\"enum\": \"COMPLETED\", \"origin\": \"PaymentStatus.java.PaymentStatus\"}',3,1,'{\"enum\": \"COMPLETED\", \"origin\": \"PaymentStatus.java.PaymentStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(823,'purchase_receipt_status','PENDING','pending','待收货','{\"enum\": \"PENDING\", \"origin\": \"ReceiptStatus.java.ReceiptStatus\"}',0,1,'{\"enum\": \"PENDING\", \"origin\": \"ReceiptStatus.java.ReceiptStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(824,'purchase_receipt_status','PARTIALLY_RECEIVED','partially_received','部分收货','{\"enum\": \"PARTIALLY_RECEIVED\", \"origin\": \"ReceiptStatus.java.ReceiptStatus\"}',1,1,'{\"enum\": \"PARTIALLY_RECEIVED\", \"origin\": \"ReceiptStatus.java.ReceiptStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(825,'purchase_receipt_status','COMPLETED','completed','已收货','{\"enum\": \"COMPLETED\", \"origin\": \"ReceiptStatus.java.ReceiptStatus\"}',2,1,'{\"enum\": \"COMPLETED\", \"origin\": \"ReceiptStatus.java.ReceiptStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(826,'purchase_inquiry_status','PENDING','0','待询价','{\"enum\": \"PENDING\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}',0,1,'{\"enum\": \"PENDING\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(827,'purchase_inquiry_status','INQUIRED','1','已询价','{\"enum\": \"INQUIRED\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}',1,1,'{\"enum\": \"INQUIRED\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(828,'purchase_inquiry_status','COMPARING','2','比价中','{\"enum\": \"COMPARING\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}',2,1,'{\"enum\": \"COMPARING\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(829,'purchase_inquiry_status','SELECTED','3','已选中','{\"enum\": \"SELECTED\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}',3,1,'{\"enum\": \"SELECTED\", \"origin\": \"InquiryStatus.java.InquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(830,'purchase_material_inquiry_status','ACTIVE','0','有效','{\"enum\": \"ACTIVE\", \"origin\": \"MaterialInquiryStatus.java.MaterialInquiryStatus\"}',0,1,'{\"enum\": \"ACTIVE\", \"origin\": \"MaterialInquiryStatus.java.MaterialInquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(831,'purchase_material_inquiry_status','INACTIVE','1','无效','{\"enum\": \"INACTIVE\", \"origin\": \"MaterialInquiryStatus.java.MaterialInquiryStatus\"}',1,1,'{\"enum\": \"INACTIVE\", \"origin\": \"MaterialInquiryStatus.java.MaterialInquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(832,'purchase_material_inquiry_status','EXPIRED','2','已过期','{\"enum\": \"EXPIRED\", \"origin\": \"MaterialInquiryStatus.java.MaterialInquiryStatus\"}',2,1,'{\"enum\": \"EXPIRED\", \"origin\": \"MaterialInquiryStatus.java.MaterialInquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(833,'purchase_material_inquiry_status','CANCELLED','3','已取消','{\"enum\": \"CANCELLED\", \"origin\": \"MaterialInquiryStatus.java.MaterialInquiryStatus\"}',3,1,'{\"enum\": \"CANCELLED\", \"origin\": \"MaterialInquiryStatus.java.MaterialInquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(834,'purchase_material_inquiry_status','COMPLETED','4','已完成','{\"enum\": \"COMPLETED\", \"origin\": \"MaterialInquiryStatus.java.MaterialInquiryStatus\"}',4,1,'{\"enum\": \"COMPLETED\", \"origin\": \"MaterialInquiryStatus.java.MaterialInquiryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(835,'purchase_document_status','PENDING','0','待处理','{\"enum\": \"PENDING\", \"origin\": \"DocumentStatus.java.DocumentStatus\"}',0,1,'{\"enum\": \"PENDING\", \"origin\": \"DocumentStatus.java.DocumentStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(836,'purchase_document_status','VERIFIED','1','已核验','{\"enum\": \"VERIFIED\", \"origin\": \"DocumentStatus.java.DocumentStatus\"}',1,1,'{\"enum\": \"VERIFIED\", \"origin\": \"DocumentStatus.java.DocumentStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(837,'purchase_document_status','ARCHIVED','2','已归档','{\"enum\": \"ARCHIVED\", \"origin\": \"DocumentStatus.java.DocumentStatus\"}',2,1,'{\"enum\": \"ARCHIVED\", \"origin\": \"DocumentStatus.java.DocumentStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(838,'purchase_document_type','INVOICE','invoice','发票','{\"enum\": \"INVOICE\", \"origin\": \"DocumentType.java.DocumentType\"}',0,1,'{\"enum\": \"INVOICE\", \"origin\": \"DocumentType.java.DocumentType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(839,'purchase_document_type','RECEIPT','receipt','收据','{\"enum\": \"RECEIPT\", \"origin\": \"DocumentType.java.DocumentType\"}',1,1,'{\"enum\": \"RECEIPT\", \"origin\": \"DocumentType.java.DocumentType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(840,'purchase_document_type','CONTRACT','contract','合同','{\"enum\": \"CONTRACT\", \"origin\": \"DocumentType.java.DocumentType\"}',2,1,'{\"enum\": \"CONTRACT\", \"origin\": \"DocumentType.java.DocumentType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(841,'purchase_document_type','QUOTATION','quotation','报价单','{\"enum\": \"QUOTATION\", \"origin\": \"DocumentType.java.DocumentType\"}',3,1,'{\"enum\": \"QUOTATION\", \"origin\": \"DocumentType.java.DocumentType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(842,'purchase_document_type','DELIVERY_NOTE','delivery_note','送货单','{\"enum\": \"DELIVERY_NOTE\", \"origin\": \"DocumentType.java.DocumentType\"}',4,1,'{\"enum\": \"DELIVERY_NOTE\", \"origin\": \"DocumentType.java.DocumentType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(843,'purchase_document_type','OTHER','other','其他','{\"enum\": \"OTHER\", \"origin\": \"DocumentType.java.DocumentType\"}',5,1,'{\"enum\": \"OTHER\", \"origin\": \"DocumentType.java.DocumentType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(844,'purchase_inspection_result','PASSED','passed','合格','{\"enum\": \"PASSED\", \"origin\": \"InspectionResult.java.InspectionResult\"}',0,1,'{\"enum\": \"PASSED\", \"origin\": \"InspectionResult.java.InspectionResult\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(845,'purchase_inspection_result','FAILED','failed','不合格','{\"enum\": \"FAILED\", \"origin\": \"InspectionResult.java.InspectionResult\"}',1,1,'{\"enum\": \"FAILED\", \"origin\": \"InspectionResult.java.InspectionResult\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(846,'purchase_payment_method','BANK','bank','银行转账','{\"enum\": \"BANK\", \"origin\": \"PaymentMethod.java.PaymentMethod\"}',0,1,'{\"enum\": \"BANK\", \"origin\": \"PaymentMethod.java.PaymentMethod\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(847,'purchase_payment_method','CASH','cash','现金','{\"enum\": \"CASH\", \"origin\": \"PaymentMethod.java.PaymentMethod\"}',1,1,'{\"enum\": \"CASH\", \"origin\": \"PaymentMethod.java.PaymentMethod\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(848,'purchase_payment_method','CHECK','check','支票','{\"enum\": \"CHECK\", \"origin\": \"PaymentMethod.java.PaymentMethod\"}',2,1,'{\"enum\": \"CHECK\", \"origin\": \"PaymentMethod.java.PaymentMethod\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(849,'purchase_supplier_type','MATERIAL','M','原材料供应商','{\"enum\": \"MATERIAL\", \"origin\": \"SupplierTypeEnum.java.SupplierTypeEnum\"}',0,1,'{\"enum\": \"MATERIAL\", \"origin\": \"SupplierTypeEnum.java.SupplierTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(850,'purchase_supplier_type','EQUIPMENT','E','设备供应商','{\"enum\": \"EQUIPMENT\", \"origin\": \"SupplierTypeEnum.java.SupplierTypeEnum\"}',1,1,'{\"enum\": \"EQUIPMENT\", \"origin\": \"SupplierTypeEnum.java.SupplierTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(851,'purchase_supplier_type','OTHER','O','其他供应商','{\"enum\": \"OTHER\", \"origin\": \"SupplierTypeEnum.java.SupplierTypeEnum\"}',2,1,'{\"enum\": \"OTHER\", \"origin\": \"SupplierTypeEnum.java.SupplierTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(852,'inventory_inbound_type','PURCHASE','purchase','采购入库','{\"enum\": \"PURCHASE\", \"origin\": \"InboundTypeEnum.java.InboundTypeEnum\"}',0,1,'{\"enum\": \"PURCHASE\", \"origin\": \"InboundTypeEnum.java.InboundTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(853,'inventory_inbound_type','PRODUCTION','production','生产入库','{\"enum\": \"PRODUCTION\", \"origin\": \"InboundTypeEnum.java.InboundTypeEnum\"}',1,1,'{\"enum\": \"PRODUCTION\", \"origin\": \"InboundTypeEnum.java.InboundTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(854,'inventory_inbound_type','RETURN','return','退货入库','{\"enum\": \"RETURN\", \"origin\": \"InboundTypeEnum.java.InboundTypeEnum\"}',2,1,'{\"enum\": \"RETURN\", \"origin\": \"InboundTypeEnum.java.InboundTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(855,'inventory_inbound_type','TRANSFER','transfer','调拨入库','{\"enum\": \"TRANSFER\", \"origin\": \"InboundTypeEnum.java.InboundTypeEnum\"}',3,1,'{\"enum\": \"TRANSFER\", \"origin\": \"InboundTypeEnum.java.InboundTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(856,'inventory_inbound_type','ADJUST','adjust','盘盈入库','{\"enum\": \"ADJUST\", \"origin\": \"InboundTypeEnum.java.InboundTypeEnum\"}',4,1,'{\"enum\": \"ADJUST\", \"origin\": \"InboundTypeEnum.java.InboundTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(857,'inventory_outbound_type','PRODUCTION','production','生产领料','{\"enum\": \"PRODUCTION\", \"origin\": \"OutboundTypeEnum.java.OutboundTypeEnum\"}',0,1,'{\"enum\": \"PRODUCTION\", \"origin\": \"OutboundTypeEnum.java.OutboundTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(858,'inventory_outbound_type','SALES','sales','销售出库','{\"enum\": \"SALES\", \"origin\": \"OutboundTypeEnum.java.OutboundTypeEnum\"}',1,1,'{\"enum\": \"SALES\", \"origin\": \"OutboundTypeEnum.java.OutboundTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(859,'inventory_outbound_type','RETURN','return','退货出库','{\"enum\": \"RETURN\", \"origin\": \"OutboundTypeEnum.java.OutboundTypeEnum\"}',2,1,'{\"enum\": \"RETURN\", \"origin\": \"OutboundTypeEnum.java.OutboundTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(860,'inventory_outbound_type','SCRAP','scrap','报废出库','{\"enum\": \"SCRAP\", \"origin\": \"OutboundTypeEnum.java.OutboundTypeEnum\"}',3,1,'{\"enum\": \"SCRAP\", \"origin\": \"OutboundTypeEnum.java.OutboundTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(861,'inventory_outbound_type','TRANSFER','transfer','调拨出库','{\"enum\": \"TRANSFER\", \"origin\": \"OutboundTypeEnum.java.OutboundTypeEnum\"}',4,1,'{\"enum\": \"TRANSFER\", \"origin\": \"OutboundTypeEnum.java.OutboundTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(862,'inventory_outbound_type','ADJUST','adjust','盘亏出库','{\"enum\": \"ADJUST\", \"origin\": \"OutboundTypeEnum.java.OutboundTypeEnum\"}',5,1,'{\"enum\": \"ADJUST\", \"origin\": \"OutboundTypeEnum.java.OutboundTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(863,'inventory_transaction_type','INBOUND','inbound','入库','{\"enum\": \"INBOUND\", \"origin\": \"TransactionTypeEnum.java.TransactionTypeEnum\"}',0,1,'{\"enum\": \"INBOUND\", \"origin\": \"TransactionTypeEnum.java.TransactionTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(864,'inventory_transaction_type','OUTBOUND','outbound','出库','{\"enum\": \"OUTBOUND\", \"origin\": \"TransactionTypeEnum.java.TransactionTypeEnum\"}',1,1,'{\"enum\": \"OUTBOUND\", \"origin\": \"TransactionTypeEnum.java.TransactionTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(865,'inventory_transaction_type','TRANSFER_IN','transfer_in','调拨入库','{\"enum\": \"TRANSFER_IN\", \"origin\": \"TransactionTypeEnum.java.TransactionTypeEnum\"}',2,1,'{\"enum\": \"TRANSFER_IN\", \"origin\": \"TransactionTypeEnum.java.TransactionTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(866,'inventory_transaction_type','TRANSFER_OUT','transfer_out','调拨出库','{\"enum\": \"TRANSFER_OUT\", \"origin\": \"TransactionTypeEnum.java.TransactionTypeEnum\"}',3,1,'{\"enum\": \"TRANSFER_OUT\", \"origin\": \"TransactionTypeEnum.java.TransactionTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(867,'inventory_transaction_type','ADJUST','adjust','盘盈盘亏','{\"enum\": \"ADJUST\", \"origin\": \"TransactionTypeEnum.java.TransactionTypeEnum\"}',4,1,'{\"enum\": \"ADJUST\", \"origin\": \"TransactionTypeEnum.java.TransactionTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(868,'inventory_stock_status','ACTIVE','active','正常','{\"enum\": \"ACTIVE\", \"origin\": \"StockStatusEnum.java.StockStatusEnum\"}',0,1,'{\"enum\": \"ACTIVE\", \"origin\": \"StockStatusEnum.java.StockStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(869,'inventory_stock_status','FROZEN','frozen','冻结','{\"enum\": \"FROZEN\", \"origin\": \"StockStatusEnum.java.StockStatusEnum\"}',1,1,'{\"enum\": \"FROZEN\", \"origin\": \"StockStatusEnum.java.StockStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(870,'inventory_stock_status','EXPIRED','expired','过期','{\"enum\": \"EXPIRED\", \"origin\": \"StockStatusEnum.java.StockStatusEnum\"}',2,1,'{\"enum\": \"EXPIRED\", \"origin\": \"StockStatusEnum.java.StockStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(871,'inventory_stock_status','SCRAP','scrap','报废','{\"enum\": \"SCRAP\", \"origin\": \"StockStatusEnum.java.StockStatusEnum\"}',3,1,'{\"enum\": \"SCRAP\", \"origin\": \"StockStatusEnum.java.StockStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(872,'inventory_stock_item_status','INACTIVE','0','未生效','{\"enum\": \"INACTIVE\", \"origin\": \"StockItemStatusEnum.java.StockItemStatusEnum\"}',0,1,'{\"enum\": \"INACTIVE\", \"origin\": \"StockItemStatusEnum.java.StockItemStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(873,'inventory_stock_item_status','ACTIVE','1','生效','{\"enum\": \"ACTIVE\", \"origin\": \"StockItemStatusEnum.java.StockItemStatusEnum\"}',1,1,'{\"enum\": \"ACTIVE\", \"origin\": \"StockItemStatusEnum.java.StockItemStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(874,'inventory_alert_type','SAFE_STOCK','safe_stock','安全库存预警','{\"enum\": \"SAFE_STOCK\", \"origin\": \"AlertTypeEnum.java.AlertTypeEnum\"}',0,1,'{\"enum\": \"SAFE_STOCK\", \"origin\": \"AlertTypeEnum.java.AlertTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(875,'inventory_alert_type','MAX_STOCK','max_stock','最高库存预警','{\"enum\": \"MAX_STOCK\", \"origin\": \"AlertTypeEnum.java.AlertTypeEnum\"}',1,1,'{\"enum\": \"MAX_STOCK\", \"origin\": \"AlertTypeEnum.java.AlertTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(876,'inventory_alert_type','EXPIRY','expiry','保质期预警','{\"enum\": \"EXPIRY\", \"origin\": \"AlertTypeEnum.java.AlertTypeEnum\"}',2,1,'{\"enum\": \"EXPIRY\", \"origin\": \"AlertTypeEnum.java.AlertTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(877,'inventory_alert_type','OBSOLETE','obsolete','呆滞料预警','{\"enum\": \"OBSOLETE\", \"origin\": \"AlertTypeEnum.java.AlertTypeEnum\"}',3,1,'{\"enum\": \"OBSOLETE\", \"origin\": \"AlertTypeEnum.java.AlertTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(878,'inventory_alert_level','INFO','info','提示','{\"enum\": \"INFO\", \"origin\": \"AlertLevelEnum.java.AlertLevelEnum\"}',0,1,'{\"enum\": \"INFO\", \"origin\": \"AlertLevelEnum.java.AlertLevelEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(879,'inventory_alert_level','WARNING','warning','警告','{\"enum\": \"WARNING\", \"origin\": \"AlertLevelEnum.java.AlertLevelEnum\"}',1,1,'{\"enum\": \"WARNING\", \"origin\": \"AlertLevelEnum.java.AlertLevelEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(880,'inventory_alert_level','URGENT','urgent','紧急','{\"enum\": \"URGENT\", \"origin\": \"AlertLevelEnum.java.AlertLevelEnum\"}',2,1,'{\"enum\": \"URGENT\", \"origin\": \"AlertLevelEnum.java.AlertLevelEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(881,'inventory_material_type','RAW','R','原材料','{\"enum\": \"RAW\", \"origin\": \"MaterialEnums.java.Type\"}',0,1,'{\"enum\": \"RAW\", \"origin\": \"MaterialEnums.java.Type\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(882,'inventory_material_type','SEMI','S','半成品','{\"enum\": \"SEMI\", \"origin\": \"MaterialEnums.java.Type\"}',1,1,'{\"enum\": \"SEMI\", \"origin\": \"MaterialEnums.java.Type\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(883,'inventory_material_type','FINISHED','F','成品','{\"enum\": \"FINISHED\", \"origin\": \"MaterialEnums.java.Type\"}',2,1,'{\"enum\": \"FINISHED\", \"origin\": \"MaterialEnums.java.Type\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(884,'inventory_material_type','AUXILIARY','A','辅助材料','{\"enum\": \"AUXILIARY\", \"origin\": \"MaterialEnums.java.Type\"}',3,1,'{\"enum\": \"AUXILIARY\", \"origin\": \"MaterialEnums.java.Type\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(885,'inventory_material_status','ACTIVE','active','启用','{\"enum\": \"ACTIVE\", \"origin\": \"MaterialEnums.java.Status\"}',0,1,'{\"enum\": \"ACTIVE\", \"origin\": \"MaterialEnums.java.Status\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(886,'inventory_material_status','INACTIVE','inactive','停用','{\"enum\": \"INACTIVE\", \"origin\": \"MaterialEnums.java.Status\"}',1,1,'{\"enum\": \"INACTIVE\", \"origin\": \"MaterialEnums.java.Status\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(887,'inventory_material_status','OBSOLETE','obsolete','废弃','{\"enum\": \"OBSOLETE\", \"origin\": \"MaterialEnums.java.Status\"}',2,1,'{\"enum\": \"OBSOLETE\", \"origin\": \"MaterialEnums.java.Status\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(888,'product_film_type','OVERLAY','OVERLAY','面板菲林','{\"enum\": \"OVERLAY\", \"origin\": \"FilmTypeEnum.java.FilmTypeEnum\"}',0,1,'{\"enum\": \"OVERLAY\", \"origin\": \"FilmTypeEnum.java.FilmTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(889,'product_film_type','UPPER_CIRCUIT','UPPER_CIRCUIT','上层线路菲林','{\"enum\": \"UPPER_CIRCUIT\", \"origin\": \"FilmTypeEnum.java.FilmTypeEnum\"}',1,1,'{\"enum\": \"UPPER_CIRCUIT\", \"origin\": \"FilmTypeEnum.java.FilmTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(890,'product_film_type','SPACER','SPACER','间隔菲林','{\"enum\": \"SPACER\", \"origin\": \"FilmTypeEnum.java.FilmTypeEnum\"}',2,1,'{\"enum\": \"SPACER\", \"origin\": \"FilmTypeEnum.java.FilmTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(891,'product_film_type','LOWER_CIRCUIT','LOWER_CIRCUIT','下层线路菲林','{\"enum\": \"LOWER_CIRCUIT\", \"origin\": \"FilmTypeEnum.java.FilmTypeEnum\"}',3,1,'{\"enum\": \"LOWER_CIRCUIT\", \"origin\": \"FilmTypeEnum.java.FilmTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(892,'product_film_type','BACK_ADHESIVE','BACK_ADHESIVE','背胶菲林','{\"enum\": \"BACK_ADHESIVE\", \"origin\": \"FilmTypeEnum.java.FilmTypeEnum\"}',4,1,'{\"enum\": \"BACK_ADHESIVE\", \"origin\": \"FilmTypeEnum.java.FilmTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(893,'product_process_category','PREPARATION','PREPARATION','准备','{\"enum\": \"PREPARATION\", \"origin\": \"ProcessCategoryEnum.java.ProcessCategoryEnum\"}',0,1,'{\"enum\": \"PREPARATION\", \"origin\": \"ProcessCategoryEnum.java.ProcessCategoryEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(894,'product_process_category','MAIN','MAIN','主要','{\"enum\": \"MAIN\", \"origin\": \"ProcessCategoryEnum.java.ProcessCategoryEnum\"}',1,1,'{\"enum\": \"MAIN\", \"origin\": \"ProcessCategoryEnum.java.ProcessCategoryEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(895,'product_process_category','FINISHING','FINISHING','后处理','{\"enum\": \"FINISHING\", \"origin\": \"ProcessCategoryEnum.java.ProcessCategoryEnum\"}',2,1,'{\"enum\": \"FINISHING\", \"origin\": \"ProcessCategoryEnum.java.ProcessCategoryEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(896,'product_process_category','QUALITY','QUALITY','质量','{\"enum\": \"QUALITY\", \"origin\": \"ProcessCategoryEnum.java.ProcessCategoryEnum\"}',3,1,'{\"enum\": \"QUALITY\", \"origin\": \"ProcessCategoryEnum.java.ProcessCategoryEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(897,'product_process_type','MAIN_PAD','MAIN_PAD','面板','{\"enum\": \"MAIN_PAD\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}',0,1,'{\"enum\": \"MAIN_PAD\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(898,'product_process_type','UP_LINE','UP_LINE','上线','{\"enum\": \"UP_LINE\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}',1,1,'{\"enum\": \"UP_LINE\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(899,'product_process_type','DOWN_LINE','DOWN_LINE','下线','{\"enum\": \"DOWN_LINE\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}',2,1,'{\"enum\": \"DOWN_LINE\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(900,'product_process_type','PRINTING','PRINTING','印刷','{\"enum\": \"PRINTING\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}',3,1,'{\"enum\": \"PRINTING\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(901,'product_process_type','CUTTING','CUTTING','模切','{\"enum\": \"CUTTING\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}',4,1,'{\"enum\": \"CUTTING\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(902,'product_process_type','LAMINATING','LAMINATING','贴合','{\"enum\": \"LAMINATING\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}',5,1,'{\"enum\": \"LAMINATING\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(903,'product_process_type','TESTING','TESTING','测试','{\"enum\": \"TESTING\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}',6,1,'{\"enum\": \"TESTING\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(904,'product_process_type','PACKAGING','PACKAGING','包装','{\"enum\": \"PACKAGING\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}',7,1,'{\"enum\": \"PACKAGING\", \"origin\": \"ProcessTypeEnum.java.ProcessTypeEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(905,'product_type','STANDARD','1','标准产品','{\"enum\": \"STANDARD\", \"origin\": \"ProductEnums.java.Type\"}',0,1,'{\"enum\": \"STANDARD\", \"origin\": \"ProductEnums.java.Type\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(906,'product_type','CUSTOM','2','定制产品','{\"enum\": \"CUSTOM\", \"origin\": \"ProductEnums.java.Type\"}',1,1,'{\"enum\": \"CUSTOM\", \"origin\": \"ProductEnums.java.Type\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(907,'product_status','DEVELOPING','1','开发中','{\"enum\": \"DEVELOPING\", \"origin\": \"ProductEnums.java.Status\"}',0,1,'{\"enum\": \"DEVELOPING\", \"origin\": \"ProductEnums.java.Status\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(908,'product_status','PENDING','2','待审核','{\"enum\": \"PENDING\", \"origin\": \"ProductEnums.java.Status\"}',1,1,'{\"enum\": \"PENDING\", \"origin\": \"ProductEnums.java.Status\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(909,'product_status','REVIEWING','3','审核中','{\"enum\": \"REVIEWING\", \"origin\": \"ProductEnums.java.Status\"}',2,1,'{\"enum\": \"REVIEWING\", \"origin\": \"ProductEnums.java.Status\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(910,'product_status','APPROVED','4','已通过','{\"enum\": \"APPROVED\", \"origin\": \"ProductEnums.java.Status\"}',3,1,'{\"enum\": \"APPROVED\", \"origin\": \"ProductEnums.java.Status\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(911,'product_status','REJECTED','5','已驳回','{\"enum\": \"REJECTED\", \"origin\": \"ProductEnums.java.Status\"}',4,1,'{\"enum\": \"REJECTED\", \"origin\": \"ProductEnums.java.Status\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(912,'product_status','RELEASED','6','已发布','{\"enum\": \"RELEASED\", \"origin\": \"ProductEnums.java.Status\"}',5,1,'{\"enum\": \"RELEASED\", \"origin\": \"ProductEnums.java.Status\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(913,'product_status','OBSOLETE','7','停产','{\"enum\": \"OBSOLETE\", \"origin\": \"ProductEnums.java.Status\"}',6,1,'{\"enum\": \"OBSOLETE\", \"origin\": \"ProductEnums.java.Status\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(914,'product_status','CANCELLED','8','取消','{\"enum\": \"CANCELLED\", \"origin\": \"ProductEnums.java.Status\"}',7,1,'{\"enum\": \"CANCELLED\", \"origin\": \"ProductEnums.java.Status\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(915,'product_bom_type','ENGINEERING','1','工程BOM','{\"enum\": \"ENGINEERING\", \"origin\": \"ProductEnums.java.BomType\"}',0,1,'{\"enum\": \"ENGINEERING\", \"origin\": \"ProductEnums.java.BomType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(916,'product_bom_type','MANUFACTURING','2','制造BOM','{\"enum\": \"MANUFACTURING\", \"origin\": \"ProductEnums.java.BomType\"}',1,1,'{\"enum\": \"MANUFACTURING\", \"origin\": \"ProductEnums.java.BomType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(917,'product_bom_status','DRAFT','1','草稿','{\"enum\": \"DRAFT\", \"origin\": \"ProductEnums.java.BomStatus\"}',0,1,'{\"enum\": \"DRAFT\", \"origin\": \"ProductEnums.java.BomStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(918,'product_bom_status','REVIEWING','2','审核中','{\"enum\": \"REVIEWING\", \"origin\": \"ProductEnums.java.BomStatus\"}',1,1,'{\"enum\": \"REVIEWING\", \"origin\": \"ProductEnums.java.BomStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(919,'product_bom_status','APPROVED','3','已批准','{\"enum\": \"APPROVED\", \"origin\": \"ProductEnums.java.BomStatus\"}',2,1,'{\"enum\": \"APPROVED\", \"origin\": \"ProductEnums.java.BomStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(920,'product_bom_status','REJECT','4','已驳回','{\"enum\": \"REJECT\", \"origin\": \"ProductEnums.java.BomStatus\"}',3,1,'{\"enum\": \"REJECT\", \"origin\": \"ProductEnums.java.BomStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(921,'product_bom_status','OBSOLETE','5','已作废','{\"enum\": \"OBSOLETE\", \"origin\": \"ProductEnums.java.BomStatus\"}',4,1,'{\"enum\": \"OBSOLETE\", \"origin\": \"ProductEnums.java.BomStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(922,'product_source_type','BUY','1','外购','{\"enum\": \"BUY\", \"origin\": \"ProductEnums.java.SourceType\"}',0,1,'{\"enum\": \"BUY\", \"origin\": \"ProductEnums.java.SourceType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(923,'product_source_type','MAKE','2','自制','{\"enum\": \"MAKE\", \"origin\": \"ProductEnums.java.SourceType\"}',1,1,'{\"enum\": \"MAKE\", \"origin\": \"ProductEnums.java.SourceType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(924,'product_bom_layer','OVERLAY','1','面板层','{\"enum\": \"OVERLAY\", \"origin\": \"ProductEnums.java.BomLayer\"}',0,1,'{\"enum\": \"OVERLAY\", \"origin\": \"ProductEnums.java.BomLayer\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(925,'product_bom_layer','UPPER_CIRCUIT','2','上层线路','{\"enum\": \"UPPER_CIRCUIT\", \"origin\": \"ProductEnums.java.BomLayer\"}',1,1,'{\"enum\": \"UPPER_CIRCUIT\", \"origin\": \"ProductEnums.java.BomLayer\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(926,'product_bom_layer','SPACER','3','间隔层','{\"enum\": \"SPACER\", \"origin\": \"ProductEnums.java.BomLayer\"}',2,1,'{\"enum\": \"SPACER\", \"origin\": \"ProductEnums.java.BomLayer\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(927,'product_bom_layer','LOWER_CIRCUIT','4','下层线路','{\"enum\": \"LOWER_CIRCUIT\", \"origin\": \"ProductEnums.java.BomLayer\"}',3,1,'{\"enum\": \"LOWER_CIRCUIT\", \"origin\": \"ProductEnums.java.BomLayer\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(928,'product_bom_layer','BACK_ADHESIVE','5','背胶层','{\"enum\": \"BACK_ADHESIVE\", \"origin\": \"ProductEnums.java.BomLayer\"}',4,1,'{\"enum\": \"BACK_ADHESIVE\", \"origin\": \"ProductEnums.java.BomLayer\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(929,'product_route_status','DRAFT','1','草稿','{\"enum\": \"DRAFT\", \"origin\": \"ProductEnums.java.RouteStatus\"}',0,1,'{\"enum\": \"DRAFT\", \"origin\": \"ProductEnums.java.RouteStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(930,'product_route_status','REVIEWING','2','审核中','{\"enum\": \"REVIEWING\", \"origin\": \"ProductEnums.java.RouteStatus\"}',1,1,'{\"enum\": \"REVIEWING\", \"origin\": \"ProductEnums.java.RouteStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(931,'product_route_status','APPROVED','3','已批准','{\"enum\": \"APPROVED\", \"origin\": \"ProductEnums.java.RouteStatus\"}',2,1,'{\"enum\": \"APPROVED\", \"origin\": \"ProductEnums.java.RouteStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(932,'product_route_status','OBSOLETE','4','已作废','{\"enum\": \"OBSOLETE\", \"origin\": \"ProductEnums.java.RouteStatus\"}',3,1,'{\"enum\": \"OBSOLETE\", \"origin\": \"ProductEnums.java.RouteStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(933,'product_step_type','SCREEN_PRINTING','1','丝印','{\"enum\": \"SCREEN_PRINTING\", \"origin\": \"ProductEnums.java.StepType\"}',0,1,'{\"enum\": \"SCREEN_PRINTING\", \"origin\": \"ProductEnums.java.StepType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(934,'product_step_type','DIE_CUTTING','2','冲切','{\"enum\": \"DIE_CUTTING\", \"origin\": \"ProductEnums.java.StepType\"}',1,1,'{\"enum\": \"DIE_CUTTING\", \"origin\": \"ProductEnums.java.StepType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(935,'product_step_type','LAMINATION','3','贴合','{\"enum\": \"LAMINATION\", \"origin\": \"ProductEnums.java.StepType\"}',2,1,'{\"enum\": \"LAMINATION\", \"origin\": \"ProductEnums.java.StepType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(936,'product_step_type','TESTING','4','测试','{\"enum\": \"TESTING\", \"origin\": \"ProductEnums.java.StepType\"}',3,1,'{\"enum\": \"TESTING\", \"origin\": \"ProductEnums.java.StepType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(937,'product_step_type','PACKAGING','5','包装','{\"enum\": \"PACKAGING\", \"origin\": \"ProductEnums.java.StepType\"}',4,1,'{\"enum\": \"PACKAGING\", \"origin\": \"ProductEnums.java.StepType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(938,'product_lifecycle_status','DESIGN','1','设计阶段','{\"enum\": \"DESIGN\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}',0,1,'{\"enum\": \"DESIGN\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(939,'product_lifecycle_status','CUSTOMER_CONFIRM','2','客户确认','{\"enum\": \"CUSTOMER_CONFIRM\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}',1,1,'{\"enum\": \"CUSTOMER_CONFIRM\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(940,'product_lifecycle_status','MATERIAL_PREPARING','3','备料阶段','{\"enum\": \"MATERIAL_PREPARING\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}',2,1,'{\"enum\": \"MATERIAL_PREPARING\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(941,'product_lifecycle_status','PRODUCTION','4','生产阶段','{\"enum\": \"PRODUCTION\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}',3,1,'{\"enum\": \"PRODUCTION\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(942,'product_lifecycle_status','QC','5','质检阶段','{\"enum\": \"QC\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}',4,1,'{\"enum\": \"QC\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(943,'product_lifecycle_status','SHIPPED','6','发货阶段','{\"enum\": \"SHIPPED\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}',5,1,'{\"enum\": \"SHIPPED\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(944,'product_lifecycle_status','COMPLETED','7','完成阶段','{\"enum\": \"COMPLETED\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}',6,1,'{\"enum\": \"COMPLETED\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(945,'product_lifecycle_status','HOLD','8','暂停','{\"enum\": \"HOLD\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}',7,1,'{\"enum\": \"HOLD\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(946,'product_lifecycle_status','REWORK','9','返工','{\"enum\": \"REWORK\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}',8,1,'{\"enum\": \"REWORK\", \"origin\": \"ProductEnums.java.LifecycleStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(947,'product_instance_status','NORMAL','1','正常','{\"enum\": \"NORMAL\", \"origin\": \"ProductEnums.java.InstanceStatus\"}',0,1,'{\"enum\": \"NORMAL\", \"origin\": \"ProductEnums.java.InstanceStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(948,'product_instance_status','ABNORMAL','2','异常','{\"enum\": \"ABNORMAL\", \"origin\": \"ProductEnums.java.InstanceStatus\"}',1,1,'{\"enum\": \"ABNORMAL\", \"origin\": \"ProductEnums.java.InstanceStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(949,'product_instance_status','SUSPENDED','3','暂停','{\"enum\": \"SUSPENDED\", \"origin\": \"ProductEnums.java.InstanceStatus\"}',2,1,'{\"enum\": \"SUSPENDED\", \"origin\": \"ProductEnums.java.InstanceStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(950,'product_instance_status','COMPLETED','4','完成','{\"enum\": \"COMPLETED\", \"origin\": \"ProductEnums.java.InstanceStatus\"}',3,1,'{\"enum\": \"COMPLETED\", \"origin\": \"ProductEnums.java.InstanceStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(951,'product_task_status','PENDING','1','待处理','{\"enum\": \"PENDING\", \"origin\": \"ProductEnums.java.TaskStatus\"}',0,1,'{\"enum\": \"PENDING\", \"origin\": \"ProductEnums.java.TaskStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(952,'product_task_status','PROCESSING','2','处理中','{\"enum\": \"PROCESSING\", \"origin\": \"ProductEnums.java.TaskStatus\"}',1,1,'{\"enum\": \"PROCESSING\", \"origin\": \"ProductEnums.java.TaskStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(953,'product_task_status','DONE','3','已完成','{\"enum\": \"DONE\", \"origin\": \"ProductEnums.java.TaskStatus\"}',2,1,'{\"enum\": \"DONE\", \"origin\": \"ProductEnums.java.TaskStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(954,'product_task_status','REJECTED','4','已驳回','{\"enum\": \"REJECTED\", \"origin\": \"ProductEnums.java.TaskStatus\"}',3,1,'{\"enum\": \"REJECTED\", \"origin\": \"ProductEnums.java.TaskStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(955,'product_task_type','DESIGN','1','设计','{\"enum\": \"DESIGN\", \"origin\": \"ProductEnums.java.TaskType\"}',0,1,'{\"enum\": \"DESIGN\", \"origin\": \"ProductEnums.java.TaskType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(956,'product_task_type','REVIEW','2','审核','{\"enum\": \"REVIEW\", \"origin\": \"ProductEnums.java.TaskType\"}',1,1,'{\"enum\": \"REVIEW\", \"origin\": \"ProductEnums.java.TaskType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(957,'product_task_type','MODIFY','3','修改','{\"enum\": \"MODIFY\", \"origin\": \"ProductEnums.java.TaskType\"}',2,1,'{\"enum\": \"MODIFY\", \"origin\": \"ProductEnums.java.TaskType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(958,'product_film_status','DRAFT','1','草稿','{\"enum\": \"DRAFT\", \"origin\": \"ProductEnums.java.FilmStatus\"}',0,1,'{\"enum\": \"DRAFT\", \"origin\": \"ProductEnums.java.FilmStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(959,'product_film_status','REVIEWING','2','审核中','{\"enum\": \"REVIEWING\", \"origin\": \"ProductEnums.java.FilmStatus\"}',1,1,'{\"enum\": \"REVIEWING\", \"origin\": \"ProductEnums.java.FilmStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(960,'product_film_status','APPROVED','3','已批准','{\"enum\": \"APPROVED\", \"origin\": \"ProductEnums.java.FilmStatus\"}',2,1,'{\"enum\": \"APPROVED\", \"origin\": \"ProductEnums.java.FilmStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(961,'product_film_status','OBSOLETE','4','已作废','{\"enum\": \"OBSOLETE\", \"origin\": \"ProductEnums.java.FilmStatus\"}',3,1,'{\"enum\": \"OBSOLETE\", \"origin\": \"ProductEnums.java.FilmStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(962,'product_config_option_type','MATERIAL','1','材料','{\"enum\": \"MATERIAL\", \"origin\": \"ProductEnums.java.ConfigOptionType\"}',0,1,'{\"enum\": \"MATERIAL\", \"origin\": \"ProductEnums.java.ConfigOptionType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(963,'product_config_option_type','COLOR','2','颜色','{\"enum\": \"COLOR\", \"origin\": \"ProductEnums.java.ConfigOptionType\"}',1,1,'{\"enum\": \"COLOR\", \"origin\": \"ProductEnums.java.ConfigOptionType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(964,'product_config_option_type','CIRCUIT','3','电路','{\"enum\": \"CIRCUIT\", \"origin\": \"ProductEnums.java.ConfigOptionType\"}',2,1,'{\"enum\": \"CIRCUIT\", \"origin\": \"ProductEnums.java.ConfigOptionType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(965,'product_config_option_type','SIZE','4','尺寸','{\"enum\": \"SIZE\", \"origin\": \"ProductEnums.java.ConfigOptionType\"}',3,1,'{\"enum\": \"SIZE\", \"origin\": \"ProductEnums.java.ConfigOptionType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(966,'product_config_model_status','ACTIVE','1','激活','{\"enum\": \"ACTIVE\", \"origin\": \"ProductEnums.java.ConfigModelStatus\"}',0,1,'{\"enum\": \"ACTIVE\", \"origin\": \"ProductEnums.java.ConfigModelStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(967,'product_config_model_status','INACTIVE','0','未激活','{\"enum\": \"INACTIVE\", \"origin\": \"ProductEnums.java.ConfigModelStatus\"}',1,1,'{\"enum\": \"INACTIVE\", \"origin\": \"ProductEnums.java.ConfigModelStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(968,'product_category_status','NORMAL','0','正常','{\"enum\": \"NORMAL\", \"origin\": \"ProductEnums.java.CategoryStatus\"}',0,1,'{\"enum\": \"NORMAL\", \"origin\": \"ProductEnums.java.CategoryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(969,'product_category_status','DISABLE','1','停用','{\"enum\": \"DISABLE\", \"origin\": \"ProductEnums.java.CategoryStatus\"}',1,1,'{\"enum\": \"DISABLE\", \"origin\": \"ProductEnums.java.CategoryStatus\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(970,'common_approve_status','DRAFT','1','草稿','{\"enum\": \"DRAFT\", \"origin\": \"ApproveStatusEnum.java.ApproveStatusEnum\"}',0,1,'{\"enum\": \"DRAFT\", \"origin\": \"ApproveStatusEnum.java.ApproveStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(971,'common_approve_status','PENDING','2','待审批','{\"enum\": \"PENDING\", \"origin\": \"ApproveStatusEnum.java.ApproveStatusEnum\"}',1,1,'{\"enum\": \"PENDING\", \"origin\": \"ApproveStatusEnum.java.ApproveStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(972,'common_approve_status','APPROVED','3','已批准','{\"enum\": \"APPROVED\", \"origin\": \"ApproveStatusEnum.java.ApproveStatusEnum\"}',2,1,'{\"enum\": \"APPROVED\", \"origin\": \"ApproveStatusEnum.java.ApproveStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(973,'common_approve_status','REJECTED','4','已拒绝','{\"enum\": \"REJECTED\", \"origin\": \"ApproveStatusEnum.java.ApproveStatusEnum\"}',3,1,'{\"enum\": \"REJECTED\", \"origin\": \"ApproveStatusEnum.java.ApproveStatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(974,'common_status','NORMAL','1','正常','{\"enum\": \"NORMAL\", \"origin\": \"StatusEnum.java.StatusEnum\"}',0,1,'{\"enum\": \"NORMAL\", \"origin\": \"StatusEnum.java.StatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(975,'common_status','DISABLE','0','停用','{\"enum\": \"DISABLE\", \"origin\": \"StatusEnum.java.StatusEnum\"}',1,1,'{\"enum\": \"DISABLE\", \"origin\": \"StatusEnum.java.StatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(976,'common_status','DELETED','2','删除','{\"enum\": \"DELETED\", \"origin\": \"StatusEnum.java.StatusEnum\"}',2,1,'{\"enum\": \"DELETED\", \"origin\": \"StatusEnum.java.StatusEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(977,'common_yes_no','NO','0','否','{\"enum\": \"NO\", \"origin\": \"YesNoEnum.java.YesNoEnum\"}',0,1,'{\"enum\": \"NO\", \"origin\": \"YesNoEnum.java.YesNoEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(978,'common_yes_no','YES','1','是','{\"enum\": \"YES\", \"origin\": \"YesNoEnum.java.YesNoEnum\"}',1,1,'{\"enum\": \"YES\", \"origin\": \"YesNoEnum.java.YesNoEnum\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(979,'system_user_type','SYSTEM','00','系统用户','{\"enum\": \"SYSTEM\", \"origin\": \"UserType.java.UserType\"}',0,1,'{\"enum\": \"SYSTEM\", \"origin\": \"UserType.java.UserType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(980,'system_user_type','NORMAL','99','普通用户','{\"enum\": \"NORMAL\", \"origin\": \"UserType.java.UserType\"}',1,1,'{\"enum\": \"NORMAL\", \"origin\": \"UserType.java.UserType\"}','2026-08-01 11:16:55','2026-08-01 11:16:55',0,1),(981,'sales_quotation_status','MODIFYING','8','改单','{\"enum\": \"MODIFYING\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}',7,1,'{\"enum\": \"MODIFYING\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}','2026-08-01 11:33:39','2026-08-01 11:33:39',0,1),(982,'sales_quotation_status','COMPLETED','9','已完成','{\"enum\": \"COMPLETED\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}',8,1,'{\"enum\": \"COMPLETED\", \"origin\": \"QuotationStatus.java.QuotationStatus\"}','2026-08-01 11:33:39','2026-08-01 11:33:39',0,1),(983,'process_type','MAIN_PAD','MAIN_PAD','面板',NULL,1,1,NULL,'2026-08-04 16:22:26','2026-08-04 16:22:26',0,1),(984,'process_type','UP_LINE','UP_LINE','上线',NULL,2,1,NULL,'2026-08-04 16:22:26','2026-08-04 16:22:26',0,1),(985,'process_type','DOWN_LINE','DOWN_LINE','下线',NULL,3,1,NULL,'2026-08-04 16:22:26','2026-08-04 16:22:26',0,1),(986,'process_type','PRINTING','PRINTING','印刷',NULL,4,1,NULL,'2026-08-04 16:22:26','2026-08-04 16:22:26',0,1),(987,'process_type','CUTTING','CUTTING','模切',NULL,5,1,NULL,'2026-08-04 16:22:26','2026-08-04 16:22:26',0,1),(988,'process_type','LAMINATING','LAMINATING','贴合',NULL,6,1,NULL,'2026-08-04 16:22:26','2026-08-04 16:22:26',0,1),(989,'process_type','TESTING','TESTING','测试',NULL,7,1,NULL,'2026-08-04 16:22:26','2026-08-04 16:22:26',0,1),(990,'process_type','PACKAGING','PACKAGING','包装',NULL,8,1,NULL,'2026-08-04 16:22:26','2026-08-04 16:22:26',0,1),(991,'process_category','PREPARATION','PREPARATION','准备',NULL,1,1,NULL,'2026-08-04 16:22:26','2026-08-04 16:22:26',0,1),(992,'process_category','MAIN','MAIN','主要',NULL,2,1,NULL,'2026-08-04 16:22:26','2026-08-04 16:22:26',0,1),(993,'process_category','FINISHING','FINISHING','后处理',NULL,3,1,NULL,'2026-08-04 16:22:26','2026-08-04 16:22:26',0,1),(994,'process_category','QUALITY','QUALITY','质量',NULL,4,1,NULL,'2026-08-04 16:22:26','2026-08-04 16:22:26',0,1);
/*!40000 ALTER TABLE `sys_dict_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_error_log`
--

DROP TABLE IF EXISTS `sys_error_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_error_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `trace_id` varchar(50) DEFAULT NULL COMMENT '追踪ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '用户账号',
  `exception_name` varchar(200) DEFAULT NULL COMMENT '异常类名',
  `exception_msg` varchar(500) DEFAULT NULL COMMENT '异常消息',
  `request_url` varchar(200) DEFAULT NULL COMMENT '请求URL',
  `request_method` varchar(10) DEFAULT NULL COMMENT '请求方式',
  `request_params` varchar(1000) DEFAULT NULL COMMENT '请求参数',
  `client_ip` varchar(50) DEFAULT NULL COMMENT '客户端IP',
  `trigger_time` datetime DEFAULT NULL COMMENT '触发时间',
  `handle_status` tinyint DEFAULT '0' COMMENT '处理状态(0未处理 1已处理 2已忽略)',
  `handle_remark` varchar(500) DEFAULT NULL COMMENT '处理备注',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_by` varchar(50) DEFAULT NULL COMMENT '处理人',
  PRIMARY KEY (`id`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_exception_name` (`exception_name`),
  KEY `idx_trigger_time` (`trigger_time`)
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='错误日志表(精简版)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_error_log`
--

LOCK TABLES `sys_error_log` WRITE;
/*!40000 ALTER TABLE `sys_error_log` DISABLE KEYS */;
INSERT INTO `sys_error_log` VALUES (1,NULL,26,'xiaoshou0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/summary','GET',NULL,'127.0.0.1','2026-08-04 15:19:29',0,NULL,NULL,NULL),(2,'ea1d0ace62b94e72b30b905199996e52',26,'xiaoshou0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/low-stock','GET',NULL,'127.0.0.1','2026-08-04 15:19:29',0,NULL,NULL,NULL),(3,'f1790da8e6544d6db34eba1cafe9610a',26,'xiaoshou0','org.springframework.web.servlet.resource.NoResourceFoundException','No static resource sales/quotation/1/copy.','/sales/quotation/1/copy','POST',NULL,'127.0.0.1','2026-08-04 15:36:23',0,NULL,NULL,NULL),(4,'fe6adf0a64c84b21a7e5bb9fb2839520',26,'xiaoshou0','org.springframework.web.servlet.resource.NoResourceFoundException','No static resource sales/quotation/1/copy.','/sales/quotation/1/copy','POST',NULL,'127.0.0.1','2026-08-04 15:36:25',0,NULL,NULL,NULL),(5,'30db824186104a56b6ef88c444dfce7a',26,'xiaoshou0','org.springframework.web.servlet.resource.NoResourceFoundException','No static resource sales/quotation/1/copy.','/sales/quotation/1/copy','POST',NULL,'127.0.0.1','2026-08-04 15:36:48',0,NULL,NULL,NULL),(6,'19c45615b0944da3a7a9c72a9acc1083',26,'xiaoshou0','org.springframework.web.servlet.resource.NoResourceFoundException','No static resource sales/quotation/1/copy.','/sales/quotation/1/copy','POST',NULL,'127.0.0.1','2026-08-04 15:48:05',0,NULL,NULL,NULL),(7,'6d943668734c4ea6bc35b2f0815d3f6c',26,'xiaoshou0','org.springframework.web.servlet.resource.NoResourceFoundException','No static resource sales/quotation/1/copy.','/sales/quotation/1/copy','POST',NULL,'127.0.0.1','2026-08-04 15:54:03',0,NULL,NULL,NULL),(8,NULL,26,'xiaoshou0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/summary','GET',NULL,'127.0.0.1','2026-08-04 16:00:44',0,NULL,NULL,NULL),(9,NULL,26,'xiaoshou0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/low-stock','GET',NULL,'127.0.0.1','2026-08-04 16:00:44',0,NULL,NULL,NULL),(10,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/summary','GET',NULL,'127.0.0.1','2026-08-04 16:05:19',0,NULL,NULL,NULL),(11,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/low-stock','GET',NULL,'127.0.0.1','2026-08-04 16:05:19',0,NULL,NULL,NULL),(12,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/summary','GET',NULL,'127.0.0.1','2026-08-04 16:13:07',0,NULL,NULL,NULL),(13,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/low-stock','GET',NULL,'127.0.0.1','2026-08-04 16:13:07',0,NULL,NULL,NULL),(14,'19c1f6cfea1043938aff4b09d2a2321e',1,'admin','org.springframework.web.context.request.async.AsyncRequestNotUsableException','ServletOutputStream failed to write: java.io.IOException: Broken pipe','/kanban/board/dev/tasks','GET',NULL,'127.0.0.1','2026-08-04 16:19:16',0,NULL,NULL,NULL),(15,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/summary','GET',NULL,'127.0.0.1','2026-08-04 16:22:51',0,NULL,NULL,NULL),(16,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/low-stock','GET',NULL,'127.0.0.1','2026-08-04 16:22:51',0,NULL,NULL,NULL),(17,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/summary','GET',NULL,'127.0.0.1','2026-08-04 16:44:01',0,NULL,NULL,NULL),(18,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/low-stock','GET',NULL,'127.0.0.1','2026-08-04 16:44:01',0,NULL,NULL,NULL),(19,NULL,28,'gongcheng0','org.springframework.http.converter.HttpMessageNotReadableException','JSON parse error: Unrecognized field \"detailId\" (class com.jjx.product.domain.dto.EngineeringRoutingItemDTO), not marked as ignorable','/engineering/routings/1','PUT',NULL,'127.0.0.1','2026-08-04 16:46:37',0,NULL,NULL,NULL),(20,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/summary','GET',NULL,'127.0.0.1','2026-08-04 16:52:05',0,NULL,NULL,NULL),(21,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/low-stock','GET',NULL,'127.0.0.1','2026-08-04 16:52:05',0,NULL,NULL,NULL),(22,NULL,26,'xiaoshou0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/summary','GET',NULL,'127.0.0.1','2026-08-04 17:01:35',0,NULL,NULL,NULL),(23,NULL,26,'xiaoshou0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/low-stock','GET',NULL,'127.0.0.1','2026-08-04 17:01:35',0,NULL,NULL,NULL),(24,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/summary','GET',NULL,'127.0.0.1','2026-08-04 17:07:43',0,NULL,NULL,NULL),(25,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/low-stock','GET',NULL,'127.0.0.1','2026-08-04 17:07:43',0,NULL,NULL,NULL),(26,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/summary','GET',NULL,'127.0.0.1','2026-08-04 17:08:16',0,NULL,NULL,NULL),(27,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/low-stock','GET',NULL,'127.0.0.1','2026-08-04 17:08:16',0,NULL,NULL,NULL),(28,NULL,26,'xiaoshou0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/summary','GET',NULL,'127.0.0.1','2026-08-04 17:12:11',0,NULL,NULL,NULL),(29,NULL,26,'xiaoshou0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/low-stock','GET',NULL,'127.0.0.1','2026-08-04 17:12:11',0,NULL,NULL,NULL),(30,'42c4093e37df4fe3a164ccb9478bd63c',1,'admin','org.springframework.web.HttpRequestMethodNotSupportedException','Request method \'POST\' is not supported','/sales/orders/2/status/send-to-customer','POST',NULL,'127.0.0.1','2026-08-04 17:34:02',0,NULL,NULL,NULL),(31,'537760e43e004c4595d685a43ffe50c8',1,'admin','org.springframework.web.HttpRequestMethodNotSupportedException','Request method \'POST\' is not supported','/sales/orders/2/status/send-to-customer','POST',NULL,'127.0.0.1','2026-08-04 17:34:13',0,NULL,NULL,NULL),(32,'863e3cf56b1f44f696acf87b9e99cd92',1,'admin','org.springframework.web.HttpRequestMethodNotSupportedException','Request method \'POST\' is not supported','/sales/orders/2/status/send-to-customer','POST',NULL,'127.0.0.1','2026-08-04 17:35:20',0,NULL,NULL,NULL),(33,'7ef6672e02c448d2b062d2faef3d1112',1,'admin','org.springframework.web.context.request.async.AsyncRequestNotUsableException','ServletOutputStream failed to write: java.io.IOException: Broken pipe','/kanban/board/dev/tasks','GET',NULL,'127.0.0.1','2026-08-04 18:30:20',0,NULL,NULL,NULL),(34,'a70875eb46c84bc997312a922e179f2a',1,'admin','org.springframework.web.context.request.async.AsyncRequestNotUsableException','ServletOutputStream failed to write: java.io.IOException: Broken pipe','/kanban/board/dev/tasks','GET',NULL,'127.0.0.1','2026-08-04 18:30:34',0,NULL,NULL,NULL),(35,'0f5421afd56f4e48a33f22b8fb1a71db',1,'admin','org.springframework.web.bind.MissingServletRequestParameterException','Required request parameter \'approved\' for method parameter type Boolean is not present','/sales/quotation/review/3','PUT',NULL,'127.0.0.1','2026-08-04 21:54:50',0,NULL,NULL,NULL),(36,'3ae89ecdf6de48e59a2eb6d3bdca3d98',1,'admin','org.springframework.web.bind.MissingServletRequestParameterException','Required request parameter \'approved\' for method parameter type Boolean is not present','/sales/quotation/review/4','PUT',NULL,'127.0.0.1','2026-08-04 21:55:03',0,NULL,NULL,NULL),(37,NULL,1,'admin','org.springframework.http.converter.HttpMessageNotReadableException','JSON parse error: Cannot deserialize value of type `java.lang.Integer` from String \"standard\": not a valid `java.lang.Integer` value','/sales/quotation','POST',NULL,'127.0.0.1','2026-08-04 21:55:40',0,NULL,NULL,NULL),(38,'03a6fa89bd4d4875b50902f65c796470',1,'admin','org.springframework.web.bind.MissingServletRequestParameterException','Required request parameter \'approved\' for method parameter type Boolean is not present','/sales/quotation/review/5','PUT',NULL,'127.0.0.1','2026-08-04 21:56:20',0,NULL,NULL,NULL),(39,'1ac1f9e33b684dd0a916f542aaaa10f5',1,'admin','org.springframework.web.context.request.async.AsyncRequestNotUsableException','ServletOutputStream failed to write: java.io.IOException: Broken pipe','/kanban/board/dev/tasks','GET',NULL,'127.0.0.1','2026-08-05 09:45:20',0,NULL,NULL,NULL),(40,'a76488d6184a4c01bf9b3a936abdb34f',1,'admin','org.springframework.web.context.request.async.AsyncRequestNotUsableException','ServletOutputStream failed to write: java.io.IOException: Broken pipe','/kanban/board/dev/tasks','GET',NULL,'127.0.0.1','2026-08-05 09:47:43',0,NULL,NULL,NULL),(41,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/summary','GET',NULL,'127.0.0.1','2026-08-05 09:50:36',0,NULL,NULL,NULL),(42,NULL,28,'gongcheng0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/low-stock','GET',NULL,'127.0.0.1','2026-08-05 09:50:36',0,NULL,NULL,NULL),(43,NULL,26,'xiaoshou0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/summary','GET',NULL,'127.0.0.1','2026-08-05 09:50:53',0,NULL,NULL,NULL),(44,NULL,26,'xiaoshou0','cn.dev33.satoken.exception.NotPermissionException','无此权限：inventory:stock:view','/inventory/stock/low-stock','GET',NULL,'127.0.0.1','2026-08-05 09:50:53',0,NULL,NULL,NULL);
/*!40000 ALTER TABLE `sys_error_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_event_config`
--

DROP TABLE IF EXISTS `sys_event_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_event_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_code` varchar(50) NOT NULL,
  `event_name` varchar(100) NOT NULL,
  `biz_module` varchar(50) DEFAULT NULL,
  `event_type` varchar(20) NOT NULL COMMENT 'notification/task/both',
  `kanban_module` varchar(20) NOT NULL DEFAULT 'office' COMMENT '看板模块: office/emergency/production/dev',
  `priority` varchar(10) NOT NULL DEFAULT 'normal' COMMENT '任务优先级: urgent/high/normal/low',
  `is_enabled` tinyint(1) DEFAULT '1',
  `target_role` json DEFAULT NULL COMMENT '角色ID列表: [7, 8]',
  `title` varchar(500) DEFAULT NULL,
  `content` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `exclude_trigger` tinyint(1) DEFAULT '0' COMMENT '排除触发者',
  PRIMARY KEY (`id`),
  UNIQUE KEY `event_code` (`event_code`)
) ENGINE=InnoDB AUTO_INCREMENT=138 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_event_config`
--

LOCK TABLES `sys_event_config` WRITE;
/*!40000 ALTER TABLE `sys_event_config` DISABLE KEYS */;
INSERT INTO `sys_event_config` VALUES (1,'inquiry.converted','询价转报价','sales','both','office','normal',1,'[7]','询价单【{bizId}】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','2026-07-31 00:40:53','2026-07-31 00:40:53',0),(2,'quotation.submitted','报价单提交审核','sales','both','office','normal',1,'[8]','报价单【{bizId}】已提交审核','报价单已提交审核，请尽快处理。','2026-07-31 00:40:53','2026-07-31 01:43:49',1),(3,'quotation.reviewed','报价单审核','sales','notification','office','normal',1,'[7]','报价单【{bizId}】审核结果','报价单审核已完成，请查看结果。','2026-07-31 00:40:53','2026-07-31 18:04:23',0),(4,'quotation.sent','报价单发送给客户','sales','notification','office','normal',1,'[7]','报价单【{bizId}】已发送给客户','报价单已发送给客户，请关注客户反馈。','2026-07-31 00:40:53','2026-07-31 18:04:23',0),(5,'quotation.converted','报价单转订单','sales','notification','office','normal',1,'[7]','报价单【{bizId}】已转为订单','报价单已成功转为销售订单。','2026-07-31 00:40:53','2026-07-31 18:04:23',0),(6,'sample.submitted','样品单提交审核','sales','notification','office','normal',1,'[8]',NULL,NULL,'2026-07-31 00:40:53','2026-08-01 14:45:10',0),(7,'sample.approved','样品单审核通过','sales','both','office','normal',1,'[9]',NULL,NULL,'2026-07-31 00:40:53','2026-08-01 14:45:10',0),(8,'sample.rejected','样品单审核驳回','sales','notification','office','normal',1,'[9]',NULL,NULL,'2026-07-31 00:40:53','2026-08-01 14:45:10',0),(9,'sample.ready','样品制作完成','sales','both','office','normal',1,'[7, 9]','样品【{bizId}】已制作完成','样品已制作完成，请安排送样。','2026-07-31 00:40:53','2026-07-31 01:43:49',0),(10,'sample.sent','样品已送样','sales','notification','office','normal',1,'[7]',NULL,NULL,'2026-07-31 00:40:53','2026-08-01 14:45:10',0),(11,'sample.confirmed','样品客户确认OK','sales','notification','office','normal',1,'[7]',NULL,NULL,'2026-07-31 00:40:53','2026-08-01 14:45:10',0),(12,'sample.rejected_by_customer','样品客户退回','sales','notification','office','normal',1,'[9]',NULL,NULL,'2026-07-31 00:40:53','2026-08-01 14:45:10',0),(13,'sample.converted','样品转量产','sales','both','office','normal',1,'[7]',NULL,NULL,'2026-07-31 00:40:53','2026-08-01 14:45:16',0),(14,'order.review_started','订单开始审核','sales','both','office','high',1,'[8]','订单【{bizId}】开始审核','订单已进入审核流程，请尽快处理。','2026-07-31 00:40:53','2026-07-31 12:37:02',1),(15,'order.approved','订单审核通过','sales','notification','office','normal',1,'[7, 10, 1]',NULL,NULL,'2026-07-31 00:40:53','2026-08-01 11:10:29',0),(16,'order.rejected','订单审核驳回','sales','notification','office','normal',1,NULL,NULL,NULL,'2026-07-31 00:40:53','2026-07-31 00:40:53',0),(17,'order.resubmitted','订单重新提交','sales','notification','office','normal',1,NULL,NULL,NULL,'2026-07-31 00:40:53','2026-07-31 00:40:53',0),(18,'order.cancelled','订单取消','sales','notification','office','normal',1,NULL,NULL,NULL,'2026-07-31 00:40:53','2026-07-31 00:40:53',0),(19,'order.sent_to_customer','订单发送客户确认','sales','notification','office','normal',1,NULL,NULL,NULL,'2026-07-31 00:40:53','2026-07-31 00:40:53',0),(32,'product.submitted','产品提审','product','both','office','normal',1,'[9]','产品【{bizId}】已提交审核','产品已提交审核，请尽快处理。','2026-07-31 01:26:35','2026-07-31 01:26:35',1),(33,'product.approved','产品审核通过','product','notification','office','normal',1,'[9]','产品【{bizId}】审核通过','产品已审核通过。','2026-07-31 01:26:35','2026-07-31 01:26:35',0),(34,'bom.submitted','BOM提审','product','both','office','normal',1,'[9]','BOM【{bizId}】已提交审核','BOM已提交审核，请尽快处理。','2026-07-31 01:26:35','2026-07-31 01:26:35',1),(35,'bom.approved','BOM审核通过','product','notification','office','normal',1,'[9]','BOM【{bizId}】审核通过','BOM已审核通过。','2026-07-31 01:26:35','2026-07-31 01:26:35',0),(36,'purchase.submitted','采购单提审','purchase','both','office','high',1,'[8]','采购单【{bizId}】已提交审核','采购单已提交审核，请尽快处理。','2026-07-31 01:28:38','2026-07-31 12:37:02',1),(37,'purchase.received','采购到货','purchase','notification','office','normal',1,'[8, 9]','采购单【{bizId}】已到货','采购物料已到货，请安排检验或入库。','2026-07-31 01:28:38','2026-07-31 01:28:38',0),(38,'production.completed','工单完工','production','notification','office','normal',1,'[9]','工单【{orderId}】已完成','生产工单已完成，请安排质检。','2026-07-31 01:29:00','2026-07-31 01:29:00',0),(39,'order.submitted','订单提交','sales','notification','office','normal',1,'[8]','订单【{bizId}】已提交','销售订单已提交，请处理。','2026-07-31 01:29:00','2026-07-31 01:29:00',0),(40,'stock.low','库存不足预警','inventory','notification','emergency','urgent',1,'[7, 8]','物料库存低于安全库存','物料库存已低于安全库存，请及时安排补货。','2026-07-31 02:10:37','2026-07-31 12:37:02',0),(41,'stock.over','库存超上限预警','inventory','notification','emergency','urgent',1,'[7, 8]','物料库存超过最高库存','物料库存已超过最高库存设定，请检查。','2026-07-31 02:10:37','2026-07-31 12:37:02',0),(42,'quotation.confirmed','报价单客户确认','sales','notification','office','normal',1,'[7]','报价单【{bizId}】客户已确认','客户已确认报价单，请及时转为销售订单。','2026-08-01 11:04:34','2026-08-01 11:04:34',0),(43,'quotation.rejected','报价单客户拒绝','sales','notification','office','normal',1,'[7]','报价单【{bizId}】客户已拒绝','客户拒绝了该报价单，请查看拒绝原因并跟进。','2026-08-01 11:04:34','2026-08-01 11:04:34',0),(44,'sample.created','报价转样品单','sales','both','office','normal',1,'[9]','样品单【{bizId}】已创建，请安排打样','报价单已转为样品单，请工程部门安排打样工作。','2026-08-01 13:46:06','2026-08-01 13:46:06',0),(45,'inventory.inbound.created','入库单创建','inventory','both','office','normal',1,'[11]','入库单【{bizId}】已创建','有新入库单待处理，请及时安排入库。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(46,'inventory.inbound.submitted','入库单提交审核','inventory','both','office','normal',1,'[11]','入库单【{bizId}】已提交审核','入库单已提交审核，请审核。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(47,'inventory.inbound.approved','入库单审核通过','inventory','both','office','normal',1,'[11]','入库单【{bizId}】审核通过','入库单审核已通过，请执行入库。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(48,'inventory.inbound.rejected','入库单审核驳回','inventory','notification','office','normal',1,'[11]','入库单【{bizId}】审核驳回','入库单审核未通过，请查看原因。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(49,'inventory.inbound.confirmed','入库单确认入库','inventory','both','office','normal',1,'[11]','入库单【{bizId}】已入库','入库已完成，库存已更新。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(50,'inventory.inbound.cancelled','入库单取消','inventory','notification','office','normal',1,'[11]','入库单【{bizId}】已取消','入库单已取消。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(51,'inventory.inbound.created_from_purchase','采购生成入库单','inventory','both','office','normal',1,'[11]','采购单【{bizId}】已生成入库单','采购到货已生成入库单，请安排收货入库。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(52,'inventory.inbound.created_from_production','生产生成入库单','inventory','both','office','normal',1,'[11]','生产工单【{bizId}】已生成入库单','生产完工已生成入库单，请安排入库。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(53,'inventory.outbound.created','出库单创建','inventory','both','office','normal',1,'[11]','出库单【{bizId}】已创建','有新出库单待处理，请及时安排出库。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(54,'inventory.outbound.submitted','出库单提交审核','inventory','both','office','normal',1,'[11]','出库单【{bizId}】已提交审核','出库单已提交审核，请审核。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(55,'inventory.outbound.approved','出库单审核通过','inventory','both','office','normal',1,'[11]','出库单【{bizId}】审核通过','出库单审核已通过，请执行出库。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(56,'inventory.outbound.rejected','出库单审核驳回','inventory','notification','office','normal',1,'[11]','出库单【{bizId}】审核驳回','出库单审核未通过，请查看原因。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(57,'inventory.outbound.confirmed','出库单确认出库','inventory','both','office','normal',1,'[11]','出库单【{bizId}】已出库','出库已完成，库存已更新。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(58,'inventory.outbound.cancelled','出库单取消','inventory','notification','office','normal',1,'[11]','出库单【{bizId}】已取消','出库单已取消。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(59,'inventory.outbound.created_from_production','生产领料出库单','inventory','both','office','normal',1,'[11]','生产领料单【{bizId}】已创建','生产领料已生成出库单，请安排发料。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(60,'inventory.outbound.created_from_sales','销售出库单','inventory','both','office','normal',1,'[11]','销售出库单【{bizId}】已创建','销售发货已生成出库单，请安排出库。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(61,'inventory.transfer.created','调拨单创建','inventory','both','office','normal',1,'[11]','调拨单【{bizId}】已创建','有新调拨单待处理，请及时执行调拨。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(62,'inventory.transfer.submitted','调拨单提交审核','inventory','both','office','normal',1,'[11]','调拨单【{bizId}】已提交审核','调拨单已提交审核，请审核。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(63,'inventory.transfer.approved','调拨单审核通过','inventory','both','office','normal',1,'[11]','调拨单【{bizId}】审核通过','调拨单审核已通过，请执行调出。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(64,'inventory.transfer.rejected','调拨单审核驳回','inventory','notification','office','normal',1,'[11]','调拨单【{bizId}】审核驳回','调拨单审核未通过，请查看原因。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(65,'inventory.transfer.confirmed_out','调拨单确认调出','inventory','both','office','normal',1,'[11]','调拨单【{bizId}】已调出','调拨已出库，请对方仓库确认入库。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(66,'inventory.transfer.confirmed_in','调拨单确认调入','inventory','both','office','normal',1,'[11]','调拨单【{bizId}】已调入','调拨已入库，调拨完成。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(67,'inventory.transfer.cancelled','调拨单取消','inventory','notification','office','normal',1,'[11]','调拨单【{bizId}】已取消','调拨单已取消。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(68,'inventory.stocktake.created','盘点单创建','inventory','both','office','normal',1,'[11]','盘点单【{bizId}】已创建','有新盘点单，请安排盘点。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(69,'inventory.stocktake.started','盘点单开始','inventory','both','office','normal',1,'[11]','盘点单【{bizId}】已开始盘点','盘点已开始，请录入盘点数据。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(70,'inventory.stocktake.data_inputted','盘点数据录入','inventory','notification','office','normal',1,'[11]','盘点单【{bizId}】数据已录入','盘点数据已录入，请核算盈亏。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(71,'inventory.stocktake.result_confirmed','盘点结果确认','inventory','both','office','normal',1,'[11]','盘点单【{bizId}】结果已确认','盘点结果已确认，请处理盈亏。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(72,'inventory.stocktake.diff_processed','盘点盈亏处理','inventory','both','office','normal',1,'[11]','盘点单【{bizId}】盈亏已处理','盘点盈亏已处理，库存已调整。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(73,'inventory.stocktake.closed','盘点单关闭','inventory','notification','office','normal',1,'[11]','盘点单【{bizId}】已关闭','盘点已关闭。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(74,'inventory.stocktake.submitted','盘点单提交审核','inventory','both','office','normal',1,'[11]','盘点单【{bizId}】已提交审核','盘点单已提交审核，请审核。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(75,'inventory.stocktake.approved','盘点单审核通过','inventory','both','office','normal',1,'[11]','盘点单【{bizId}】审核通过','盘点单审核已通过。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(76,'inventory.material.created','物料新增','inventory','notification','office','normal',1,'[11]','物料【{bizId}】已新增','新物料已创建。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(77,'inventory.material.updated','物料修改','inventory','notification','office','normal',1,'[11]','物料【{bizId}】已修改','物料信息已更新。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(78,'inventory.material.deleted','物料删除','inventory','notification','office','normal',1,'[11]','物料【{bizId}】已删除','物料已删除。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(79,'inventory.material.status_updated','物料状态变更','inventory','notification','office','normal',1,'[11]','物料状态已变更','物料启用/停用状态已更新。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(80,'inventory.material_category.status_updated','物料分类状态变更','inventory','notification','office','normal',1,'[11]','物料分类状态已变更','物料分类状态已更新。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(81,'inventory.material_category.deleted','物料分类删除','inventory','notification','office','normal',1,'[11]','物料分类已删除','物料分类已删除。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(82,'inventory.storage_location.status_updated','库位状态变更','inventory','notification','office','normal',1,'[11]','库位状态已变更','库位状态已更新。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(83,'inventory.storage_location.deleted','库位删除','inventory','notification','office','normal',1,'[11]','库位已删除','库位已删除。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(84,'inventory.warehouse.status_updated','仓库状态变更','inventory','notification','office','normal',1,'[11]','仓库状态已变更','仓库状态已更新。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(85,'inventory.warehouse.deleted','仓库删除','inventory','notification','office','normal',1,'[11]','仓库已删除','仓库已删除。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(86,'inventory.alert.processed','库存预警处理','inventory','both','office','normal',1,'[11]','库存预警已处理','库存预警已处理完成。','2026-08-01 13:59:38','2026-08-01 13:59:38',0),(87,'product.category.created','产品分类新增','product','notification','office','normal',1,'[9]','产品分类已新增','新分类已创建。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(88,'product.category.updated','产品分类修改','product','notification','office','normal',1,'[9]','产品分类已修改','分类信息已更新。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(89,'product.category.deleted','产品分类删除','product','notification','office','normal',1,'[9]','产品分类已删除','分类已删除。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(90,'product.category.deleted_children','产品分类级联删除','product','notification','office','normal',1,'[9]','产品分类已级联删除','分类及子分类已删除。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(91,'product.film.submitted','菲林提交审核','product','both','office','normal',1,'[9]','菲林【{bizId}】已提交审核','菲林已提交审核，请审核。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(92,'product.film.approved','菲林审核通过','product','both','office','normal',1,'[9]','菲林【{bizId}】审核通过','菲林审核已通过。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(93,'product.film.rejected','菲林审核驳回','product','notification','office','normal',1,'[9]','菲林【{bizId}】审核驳回','菲林审核未通过，请查看原因。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(94,'product.film.released','菲林发布到生产','product','both','office','normal',1,'[9]','菲林【{bizId}】已发布','菲林已发布到生产。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(95,'product.film.deleted','菲林删除','product','notification','office','normal',1,'[9]','菲林【{bizId}】已删除','菲林已删除。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(96,'product.routing.submitted','工艺路线提交审核','product','both','office','normal',1,'[9]','工艺路线【{bizId}】已提交审核','工艺路线已提交审核，请审核。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(97,'product.routing.approved','工艺路线审核通过','product','both','office','normal',1,'[9]','工艺路线【{bizId}】审核通过','工艺路线审核已通过。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(98,'product.routing.rejected','工艺路线审核驳回','product','notification','office','normal',1,'[9]','工艺路线【{bizId}】审核驳回','工艺路线审核未通过，请查看原因。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(99,'product.routing.version_changed','工艺路线版本切换','product','notification','office','normal',1,'[9]','工艺路线【{bizId}】版本已切换','工艺路线当前版本已变更。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(100,'product.instance.created','产品实例创建','product','both','office','normal',1,'[9]','产品实例【{bizId}】已创建','新实例已创建，请跟进。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(101,'product.instance.batch_created','产品实例批量创建','product','both','office','normal',1,'[9]','产品实例批量创建完成','批量创建实例完成。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(102,'product.instance.status_updated','产品实例状态变更','product','notification','office','normal',1,'[9]','产品实例状态已变更','实例状态已更新。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(103,'product.instance.production_started','产品实例开始生产','product','both','office','normal',1,'[9]','产品实例【{bizId}】开始生产','实例已开始生产。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(104,'product.instance.production_completed','产品实例生产完成','product','both','office','normal',1,'[9]','产品实例【{bizId}】生产完成','实例生产已完成。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(105,'product.instance.delivered','产品实例已交付','product','both','office','normal',1,'[9]','产品实例【{bizId}】已交付','实例已交付。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(106,'product.standard_process.deleted','标准工序删除','product','notification','office','normal',1,'[9]','标准工序已删除','标准工序已删除。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(107,'product.standard_process.status_updated','标准工序启停','product','notification','office','normal',1,'[9]','标准工序启停状态已变更','标准工序启用/停用已更新。','2026-08-01 14:02:19','2026-08-01 14:02:19',0),(108,'purchase.approved','采购订单审批通过','purchase','both','office','normal',1,'[8]','采购单【{bizId}】审批通过','采购订单已批准，请执行采购。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(109,'purchase.payment_updated','采购付款更新','purchase','notification','office','normal',1,'[8]','采购单【{bizId}】付款已更新','采购订单付款信息已更新。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(110,'purchase.item_received','采购收货登记','purchase','both','office','normal',1,'[8]','采购单【{bizId}】已收货','采购收货已登记，请验收。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(111,'purchase.supplier.created','供应商新增','purchase','notification','office','normal',1,'[8]','供应商【{bizId}】已新增','新供应商已创建。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(112,'purchase.supplier.updated','供应商修改','purchase','notification','office','normal',1,'[8]','供应商【{bizId}】已修改','供应商信息已更新。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(113,'purchase.supplier.deleted','供应商删除','purchase','notification','office','normal',1,'[8]','供应商【{bizId}】已删除','供应商已删除。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(114,'purchase.supplier.status_updated','供应商状态变更','purchase','notification','office','normal',1,'[8]','供应商【{bizId}】状态已变更','供应商启用/停用已更新。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(115,'purchase.payment.created','付款申请创建','purchase','both','office','normal',1,'[8]','付款申请【{bizId}】已创建','新付款申请待审批。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(116,'purchase.payment.approved','付款审批','purchase','both','office','normal',1,'[8]','付款申请【{bizId}】已审批','付款申请审批完成。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(117,'purchase.payment.confirmed','付款确认','purchase','both','office','normal',1,'[8]','付款【{bizId}】已确认支付','付款已确认。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(118,'purchase.payment.deleted','付款删除','purchase','notification','office','normal',1,'[8]','付款【{bizId}】已删除','付款记录已删除。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(119,'purchase.document.created','采购单据创建','purchase','notification','office','normal',1,'[8]','采购单据【{bizId}】已创建','新采购单据已创建。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(120,'purchase.document.verified','采购单据核验','purchase','both','office','normal',1,'[8]','采购单据【{bizId}】已核验','采购单据核验完成。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(121,'purchase.document.deleted','采购单据删除','purchase','notification','office','normal',1,'[8]','采购单据【{bizId}】已删除','采购单据已删除。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(122,'purchase.material_inquiry.created','物料询价创建','purchase','both','office','normal',1,'[8]','物料询价【{bizId}】已创建','新物料询价待处理。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(123,'purchase.material_inquiry.updated','物料询价修改','purchase','notification','office','normal',1,'[8]','物料询价【{bizId}】已修改','物料询价已更新。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(124,'purchase.material_inquiry.deleted','物料询价删除','purchase','notification','office','normal',1,'[8]','物料询价【{bizId}】已删除','物料询价已删除。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(125,'purchase.material_inquiry.status_updated','物料询价状态变更','purchase','notification','office','normal',1,'[8]','物料询价状态已变更','物料询价状态已更新。','2026-08-01 14:04:07','2026-08-01 14:04:07',0),(126,'sales.customer.created','客户新增','sales','notification','office','normal',1,'[7]','客户【{bizId}】已新增','新客户已创建。','2026-08-01 14:05:51','2026-08-01 14:05:51',0),(127,'sales.customer.updated','客户修改','sales','notification','office','normal',1,'[7]','客户【{bizId}】已修改','客户信息已更新。','2026-08-01 14:05:51','2026-08-01 14:05:51',0),(128,'sales.customer.deleted','客户删除','sales','notification','office','normal',1,'[7]','客户【{bizId}】已删除','客户已删除。','2026-08-01 14:05:51','2026-08-01 14:05:51',0),(129,'sales.customer.status_updated','客户状态变更','sales','notification','office','normal',1,'[7]','客户【{bizId}】状态已变更','客户启用/停用已更新。','2026-08-01 14:05:51','2026-08-01 14:05:51',0),(130,'sales.customer.approved','客户审核通过','sales','both','office','normal',1,'[7]','客户【{bizId}】审核通过','客户审核已通过。','2026-08-01 14:05:51','2026-08-01 14:05:51',0),(131,'sales.invoice.updated','发票修改','sales','notification','office','normal',1,'[7]','发票【{bizId}】已修改','发票信息已更新。','2026-08-01 14:05:51','2026-08-01 14:05:51',0),(132,'sales.invoice.deleted','发票删除','sales','notification','office','normal',1,'[7]','发票【{bizId}】已删除','发票已删除。','2026-08-01 14:05:51','2026-08-01 14:05:51',0),(133,'sample.restarted','样品重新打样','sales','both','office','normal',1,'[9]','样品单【{bizId}】重新打样','样品已退回重新打样，请工程安排。','2026-08-01 14:45:10','2026-08-01 14:45:10',0),(134,'order.production_started','订单提交生产','sales','both','production','high',1,'[9, 1]','订单提交生产','订单{orderNo}已提交生产，请安排排产','2026-08-01 16:28:56','2026-08-01 16:28:56',0),(135,'sample.transferred','样品资料转移完成','sales','both','office','high',1,'[9]','样品单【{bizId}】资料转移完成，请完善产品/BOM/工艺档案并提交审核','样品打样成果已建档（产品/BOM/工艺路线），请工程完善后提交审核','2026-08-03 11:41:36','2026-08-03 11:41:36',0),(136,'sample.cancelled','样品单作废','sales','both','office','high',1,'[7, 9]','样品单【{bizId}】已作废','样品单已作废，请相关人员确认处理','2026-08-03 17:14:01','2026-08-03 17:14:01',0),(137,'stock.shortage','订单缺料预警','inventory','notification','dev','high',1,'[11, 1]','订单【{orderNo}】缺料{shortageCount}种物料','订单【{orderNo}】齐套检查发现缺料{shortageCount}种物料（无BOM产品{noBomCount}个），请及时安排补货。可在库存预警查看明细并生成采购建议。','2026-08-04 18:44:41','2026-08-04 18:44:41',0);
/*!40000 ALTER TABLE `sys_event_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_login_log`
--

DROP TABLE IF EXISTS `sys_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `username` varchar(50) DEFAULT NULL COMMENT '登录账号',
  `tenant_id` bigint DEFAULT '1' COMMENT '租户ID',
  `login_type` varchar(20) DEFAULT NULL COMMENT '登录类型(PASSWORD/SMS)',
  `login_ip` varchar(50) DEFAULT NULL COMMENT '登录IP',
  `login_location` varchar(100) DEFAULT NULL COMMENT '登录地点',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '浏览器UA',
  `login_time` datetime DEFAULT NULL COMMENT '登录时间',
  `status` tinyint DEFAULT NULL COMMENT '状态(0失败 1成功)',
  `fail_reason` varchar(200) DEFAULT NULL COMMENT '失败原因',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`login_time`),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_login_log`
--

LOCK TABLES `sys_login_log` WRITE;
/*!40000 ALTER TABLE `sys_login_log` DISABLE KEYS */;
INSERT INTO `sys_login_log` VALUES (1,26,'xiaoshou0',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:19:29',1,NULL),(2,26,'xiaoshou0',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:00:43',1,NULL),(3,1,'admin',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:01:36',1,NULL),(4,28,'gongcheng0',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 16:05:19',1,NULL),(5,28,'gongcheng0',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 16:52:04',1,NULL),(6,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 16:52:29',1,NULL),(7,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 16:53:06',1,NULL),(8,1,'admin',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:58:17',1,NULL),(9,26,'xiaoshou0',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:01:35',1,NULL),(10,28,'gongcheng0',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 17:07:43',1,NULL),(11,26,'xiaoshou0',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:12:11',1,NULL),(12,1,'admin',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:14:18',1,NULL),(13,1,'admin',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:33:38',1,NULL),(14,1,'admin',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:35:12',1,NULL),(15,1,'admin',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:57:12',1,NULL),(16,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:53:57',1,NULL),(17,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:54:03',1,NULL),(18,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:54:08',1,NULL),(19,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:54:16',1,NULL),(20,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:54:36',1,NULL),(21,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:54:44',1,NULL),(22,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:54:50',1,NULL),(23,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:54:55',1,NULL),(24,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:55:03',1,NULL),(25,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:55:08',1,NULL),(26,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:55:25',1,NULL),(27,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:55:40',1,NULL),(28,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:55:55',1,NULL),(29,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:56:00',1,NULL),(30,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:56:05',1,NULL),(31,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:56:29',1,NULL),(32,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:56:35',1,NULL),(33,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:57:33',1,NULL),(34,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:57:39',1,NULL),(35,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:57:53',1,NULL),(36,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:57:58',1,NULL),(37,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:58:25',1,NULL),(38,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:58:47',1,NULL),(39,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:58:51',1,NULL),(40,1,'admin',1,'PASSWORD','127.0.0.1','未知','curl/8.18.0','2026-08-04 21:59:25',1,NULL),(41,1,'admin',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-05 09:44:21',1,NULL),(42,1,'admin',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-05 09:48:11',1,NULL),(43,28,'gongcheng0',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-05 09:50:36',1,NULL),(44,26,'xiaoshou0',1,'PASSWORD','127.0.0.1','未知','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-05 09:50:53',1,NULL);
/*!40000 ALTER TABLE `sys_login_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '菜单名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单ID',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '路由参数',
  `is_frame` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT '是否为外链（0是 1否）',
  `is_cache` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '是否缓存（0缓存 1不缓存）',
  `menu_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
  `visible` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '显示状态（0显示 1隐藏）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `perms` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#' COMMENT '菜单图标',
  `ancestors` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '祖级列表',
  `route_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '路由名称',
  `requires_auth` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT '是否需要认证（1是 0否）',
  `redirect` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '重定向路径',
  `sort` int DEFAULT '0' COMMENT '排序值',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'admin' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'admin' COMMENT '更新者',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB AUTO_INCREMENT=241 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_menu`
--

LOCK TABLES `sys_menu` WRITE;
/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` VALUES (1,'系统管理',0,0,'/system','layout/index.vue',NULL,'1','0','M','0','0','system:view','setting','0','System','1','/system/user',100,'admin','2026-04-13 02:43:32','admin','2026-04-17 14:53:55',''),(2,'用户管理',1,1,'user','views/system/user/index.vue',NULL,'1','0','C','0','0','system:user:view','User','0,1','User','1',NULL,1,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(3,'角色管理',1,2,'role','views/system/role/index.vue',NULL,'1','0','C','0','0','system:role:view','UserFilled','0,1','Role','1',NULL,2,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(4,'菜单管理',1,3,'menu','views/system/menu/index.vue',NULL,'1','0','C','0','0','system:menu:view','Menu','0,1','Menu','1',NULL,3,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(5,'部门管理',1,4,'dept','views/system/dept/index.vue',NULL,'1','0','C','0','0','system:dept:view','OfficeBuilding','0,1','Dept','1',NULL,4,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(6,'产品管理',0,200,'/product','layout/index.vue',NULL,'1','0','M','0','0','product:view','Goods','0','Product','1','/product/list',200,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(7,'产品列表',6,1,'list','views/product/list/index.vue',NULL,'1','0','C','0','0','product:list:view','List','0,6','ProductList','1',NULL,1,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(8,'产品分类',6,2,'category','views/product/category/index.vue',NULL,'1','0','C','0','0','product:category:view','Folder','0,6','ProductCategory','1',NULL,2,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(9,'BOM管理',90,3,'bom','views/product/bom/index.vue',NULL,'1','0','C','0','0','engineering:bom:view','Document','0,6','Bom','1',NULL,3,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(10,'工艺路线',90,4,'route','views/product/route/index.vue',NULL,'1','0','C','0','0','engineering:routing:view','SetUp','0,6','ProductRoute','1',NULL,4,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(11,'产品实例',6,5,'instance','views/product/instance/index.vue',NULL,'1','0','C','0','0','product:instance:view','Box','0,6','ProductInstance','1',NULL,5,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(13,'销售管理',0,100,'/sales','layout/index.vue',NULL,'1','0','M','0','0','sales:view','ShoppingCart','0','Sales','1','/sales/customer',250,'admin','2026-04-13 02:43:32','admin','2026-04-17 14:54:11',''),(14,'客户管理',13,1,'customer','views/sales/customer/index.vue',NULL,'1','0','C','0','0','sales:customer:view','User','0,13','Customer','1',NULL,1,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(15,'报价管理',13,2,'quotation','views/sales/quotation/index.vue',NULL,'1','0','C','0','0','sales:quotation:view','PriceTag','0,13','Quotation','1',NULL,2,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(16,'销售订单',13,3,'order','views/sales/order/index.vue',NULL,'1','0','C','0','0','sales:order:view','Document','0,13','SalesOrder','1',NULL,4,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(17,'订单跟踪',13,4,'tracking','views/sales/tracking/index.vue',NULL,'1','0','C','0','0','sales:tracking:view','Location','0,13','SalesOrderTracking','1',NULL,5,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(18,'库存管理',0,250,'/inventory','layout/index.vue',NULL,'1','0','M','0','0','inventory:view','ShoppingCart','0','Inventory','1','/inventory/material',250,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(19,'材料管理',18,1,'material',NULL,NULL,'1','0','M','0','0','inventory:material:view','Folder','0,18','Material','1','/inventory/material/list',1,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(21,'材料分类',19,2,'category','views/inventory/material/category.vue',NULL,'1','0','C','0','0','inventory:material:category:view','Folder','0,18,19','MaterialCategory','1',NULL,2,'admin','2026-04-13 02:43:32','admin','2026-04-12 18:43:32',''),(22,'物料详情',19,3,'detail/:materialId','views/inventory/material/detail.vue',NULL,'1','0','C','1','0','inventory:material:detail:view','Document','0,18,19','MaterialDetail','1',NULL,3,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(23,'仓库管理',18,2,'warehouse',NULL,NULL,'1','0','M','0','0','inventory:warehouse:view','OfficeBuilding','0,18','Warehouse','1','/inventory/warehouse/list',2,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(24,'仓库列表',23,1,'list','views/inventory/warehouse/index.vue',NULL,'1','0','C','0','0','inventory:warehouse:list','OfficeBuilding','0,18,23','WarehouseList','1',NULL,1,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(25,'仓库位置',23,2,'location','views/inventory/warehouse/location.vue',NULL,'1','0','C','0','0','inventory:warehouse:location:view','Location','0,18,23','WarehouseLocation','1',NULL,2,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(26,'库存管理',18,3,'stock','views/inventory/stock/index.vue',NULL,'1','0','C','0','0','inventory:stock:view','User','0,18','Stock','1',NULL,3,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(27,'预警管理',18,4,'alert','views/inventory/alert/index.vue',NULL,'1','0','C','0','0','inventory:alert:view','BellFilled','0,18','Alert','1',NULL,4,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(28,'入库管理',18,5,'inbound',NULL,NULL,'1','0','M','0','0','inventory:inbound:view','CirclePlusFilled','0,18','Inbound','1','/inventory/inbound/list',5,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(30,'新建入库单',28,2,'create','views/inventory/inbound/create.vue',NULL,'1','0','C','1','0','inventory:inbound:create','Plus','0,18,28','InboundCreate','1',NULL,2,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(32,'编辑入库单',28,4,'edit/:id','views/inventory/inbound/create.vue',NULL,'1','0','C','1','0','inventory:inbound:edit','Edit','0,18,28','InboundEdit','1',NULL,4,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(33,'出库管理',18,6,'outbound','views/inventory/outbound/index.vue',NULL,'1','0','C','0','0','inventory:outbound:view','RemoveFilled','0,18','Outbound','1',NULL,6,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(34,'盘点管理',18,7,'stocktake','views/inventory/stocktake/index.vue',NULL,'1','0','C','0','0','inventory:stocktake:view','Check','0,18','Stocktake','1',NULL,7,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(35,'调拨管理',18,8,'transfer','views/inventory/transfer/index.vue',NULL,'1','0','C','0','0','inventory:transfer:view','Switch','0,18','Transfer','1',NULL,8,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(36,'采购管理',0,275,'/purchase','layout/index.vue',NULL,'1','0','M','0','0','purchase:view','ShoppingBag','0','Purchase','1','/purchase/supplier',275,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(37,'供应商管理',36,1,'supplier','views/purchase/supplier/index.vue',NULL,'1','0','C','0','0','purchase:supplier:view','User','0,36','PurchaseSupplier','1',NULL,1,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(38,'采购订单',36,2,'order','views/purchase/order/index.vue',NULL,'1','0','C','0','0','purchase:order:view','Document','0,36','PurchaseOrder','1',NULL,2,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(43,'生产管理',0,276,'/production','layout/index.vue',NULL,'1','0','M','0','0','production:view','production','0','Production','1','/production/order',276,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(45,'生产订单',43,1,'order','views/production/order/index.vue',NULL,'1','0','C','0','0','production:order:view','Document','0,43','ProductionOrder','1',NULL,2,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(48,'工序执行',43,2,'execution','views/production/execution/index.vue',NULL,'1','0','C','0','0','production:execution:view','run-stop','0,43','ProductionExecution','1',NULL,5,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(49,'设备管理',43,7,'equipment','views/production/equipment/index.vue',NULL,'1','0','C','0','0','production:equipment:view','Setting','0,43','ProductionEquipment','1',NULL,6,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(50,'质量检验',43,4,'quality','views/production/quality/index.vue',NULL,'1','0','C','0','0','production:quality:view','Check','0,43','ProductionQuality','1',NULL,7,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(51,'操作记录',43,3,'production-operation','views/production/production-operation/index.vue',NULL,'1','0','C','0','0','production:operation:view','Setting','0,43','ProductionOperation','1',NULL,8,'admin','2026-04-13 02:43:33','admin','2026-04-12 18:43:33',''),(52,'生产追溯',43,6,'trace','views/production/trace/index.vue',NULL,'1','0','C','0','0',NULL,'Connection',NULL,NULL,'1',NULL,9,'admin','2026-07-20 18:16:53','admin','2026-07-20 10:16:53',''),(54,'新增用户',2,0,'','',NULL,'1','0','F','0','0','system:user:add','CirclePlus','0,1,2',NULL,'1',NULL,0,'admin','2026-04-14 10:25:09','admin','2026-04-14 02:25:09',''),(55,'日志管理',0,50,'/log','layout/index.vue',NULL,'1','0','C','0','0','log:view','Document','0','Log','1','/log/operation',0,'admin','2026-04-16 11:29:13','admin','2026-04-17 14:51:18',''),(56,'操作日志',55,0,'operation','views/log/operation/index.vue',NULL,'1','0','C','0','0','log:operation:view','Document','0,55','LogOperation','1',NULL,0,'admin','2026-04-16 11:57:19','admin','2026-04-16 03:57:19',''),(57,'登录日志',55,0,'login','views/log/login/index.vue',NULL,'1','0','C','0','0','log:login:view','User','0,55','LogLogin','1',NULL,0,'admin','2026-04-16 12:03:12','admin','2026-04-16 04:03:12',''),(58,'异常日志',55,0,'exception','views/log/exception/index.vue',NULL,'1','0','C','0','0','log:exception:view','Document','0,55','LogException','1',NULL,0,'admin','2026-04-16 12:04:47','admin','2026-04-16 04:04:47',''),(61,'字典管理',1,10,'dict','views/system/dict/index.vue',NULL,'1','0','C','0','0','system:dict:view','Document','0,1','Dict','1',NULL,0,'admin','2026-05-17 17:07:55','admin','2026-05-17 09:07:55',''),(62,'添加订单',13,5,'order/add','views/sales/order/add.vue',NULL,'1','0','C','1','0','sales:order:add','Plus','0,13,16','SalesOrderAdd','1',NULL,0,'admin','2026-05-18 21:14:41','admin','2026-05-18 13:14:41',''),(63,'编辑订单',13,5,'order/edit/:id','views/sales/order/edit.vue',NULL,'1','0','C','1','0','sales:order:edit','Edit','0,13,16','SalesOrderEdit','1',NULL,0,'admin','2026-05-18 21:14:41','admin','2026-05-18 13:14:41',''),(64,'添加产品',6,10,'list/create','views/product/list/add.vue',NULL,'1','0','C','1','0','product:create','Plus','0,6','ProductAdd','1',NULL,0,'admin','2026-05-18 23:07:03','admin','2026-05-18 15:07:03',''),(65,'编辑产品',6,15,'list/edit/:id','views/product/list/edit.vue',NULL,'1','0','C','1','0','product:edit','Edit','0,6','ProductEdit','1',NULL,0,'admin','2026-05-18 23:07:03','admin','2026-05-18 15:07:03',''),(67,'标准工序',90,0,'standard-process','views/product/standard-process/index.vue',NULL,'1','0','C','0','0','engineering:standard-process:view','processRoute','0,6','StandardProcess','1',NULL,0,'admin','2026-05-28 21:57:45','admin','2026-05-28 13:57:45',''),(69,'新增客户',14,0,'','',NULL,'1','0','F','0','0','sales:customer:add','Plus',NULL,NULL,'1',NULL,0,'admin','2026-06-09 20:12:31','admin','2026-06-09 12:12:31',''),(70,'客户信息',14,5,'','',NULL,'1','0','F','0','0','sales:customer:detail','View',NULL,NULL,'1',NULL,0,'admin','2026-06-09 21:53:50','admin','2026-06-09 14:27:52',''),(71,'修改客户',14,10,'','',NULL,'1','0','F','0','0','sales:customer:edit','Edit',NULL,NULL,'1',NULL,0,'admin','2026-06-09 22:12:23','admin','2026-06-09 14:12:23',''),(76,'成本核算',43,9,'cost','views/production/cost/index.vue',NULL,'0','0','C','0','0',NULL,'Money',NULL,NULL,'1',NULL,0,'1','2026-07-21 11:43:58','admin','2026-07-21 03:43:58',''),(77,'生产报表',43,8,'report','views/production/report/index.vue',NULL,'0','0','C','0','0',NULL,'DataAnalysis',NULL,NULL,'1',NULL,0,'1','2026-07-21 11:44:29','admin','2026-07-21 03:44:29',''),(78,'删除订单',16,30,'',NULL,NULL,'1','0','F','0','0','sales:order:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-23 09:45:58',''),(79,'导出订单',16,40,'',NULL,NULL,'1','0','F','0','0','sales:order:export','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-23 09:45:58',''),(81,'审核批准',16,60,'',NULL,NULL,'1','0','F','0','0','sales:order:approve','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-23 09:45:58',''),(82,'新增报价',15,10,'',NULL,NULL,'1','0','F','0','0','sales:quotation:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-23 09:45:58',''),(83,'编辑报价',15,20,'',NULL,NULL,'1','0','F','0','0','sales:quotation:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-23 09:45:58',''),(84,'删除报价',15,30,'',NULL,NULL,'1','0','F','0','0','sales:quotation:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-23 09:45:58',''),(85,'导出报价',15,40,'',NULL,NULL,'1','0','F','0','0','sales:quotation:export','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-23 09:45:58',''),(86,'删除客户',14,20,'',NULL,NULL,'1','0','F','0','0','sales:customer:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-23 09:45:58',''),(87,'导出客户',14,30,'',NULL,NULL,'1','0','F','0','0','sales:customer:export','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-23 09:45:58',''),(88,'报价审核',15,50,'',NULL,NULL,'1','0','F','0','0','sales:quotation:approve','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-23 10:25:33',''),(90,'工程管理',0,210,'/engineering','layout/index.vue',NULL,'1','0','M','0','0','engineering:view','tools',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-23 11:11:34',''),(92,'薄膜管理',90,60,'film','views/product/film/index.vue',NULL,'1','0','C','0','0','engineering:film:view','document',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-23 11:11:34',''),(93,'产品配置模型',6,70,'config-model','views/engineering/config/index.vue',NULL,'1','0','C','0','0','engineering:config:view','setting',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-23 11:11:34',''),(94,'操作日志',13,6,'',NULL,NULL,'1','0','C','0','0','sales:log:view','#',NULL,NULL,'1',NULL,0,'admin','2026-07-24 15:38:30','admin','2026-07-24 07:38:30',''),(95,'导出日志',94,1,'',NULL,NULL,'1','0','F','0','0','sales:log:export','#',NULL,NULL,'1',NULL,0,'admin','2026-07-24 15:38:30','admin','2026-07-24 07:38:30',''),(96,'删除日志',94,2,'',NULL,NULL,'1','0','F','0','0','sales:log:delete','#',NULL,NULL,'1',NULL,0,'admin','2026-07-24 15:38:30','admin','2026-07-24 07:38:30',''),(97,'设置首页',6,1,'',NULL,NULL,'1','0','F','0','0','product:index:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(98,'删除产品',6,2,'',NULL,NULL,'1','0','F','0','0','product:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(99,'编辑产品字段',65,1,'',NULL,NULL,'1','0','F','0','0','product:product:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(100,'产品废弃',65,2,'',NULL,NULL,'1','0','F','0','0','product:product:obsolete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(106,'提交审核',7,1,'',NULL,NULL,'1','0','F','0','0','product:status:submit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(107,'审核通过',7,2,'',NULL,NULL,'1','0','F','0','0','product:status:approve','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(108,'审核驳回',7,3,'',NULL,NULL,'1','0','F','0','0','product:status:reject','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(109,'发布产品',7,4,'',NULL,NULL,'1','0','F','0','0','product:status:release','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(110,'新增材料',20,1,'',NULL,NULL,'1','0','F','0','0','inventory:material:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(111,'编辑材料',20,2,'',NULL,NULL,'1','0','F','0','0','inventory:material:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(112,'删除材料',20,3,'',NULL,NULL,'1','0','F','0','0','inventory:material:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(113,'新增分类',21,1,'',NULL,NULL,'1','0','F','0','0','inventory:category:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(114,'编辑分类',21,2,'',NULL,NULL,'1','0','F','0','0','inventory:category:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(115,'删除分类',21,3,'',NULL,NULL,'1','0','F','0','0','inventory:category:remove','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(116,'分类列表',21,4,'',NULL,NULL,'1','0','F','0','0','inventory:category:list','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(117,'分类查询',21,5,'',NULL,NULL,'1','0','F','0','0','inventory:category:query','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(118,'新增仓库',24,1,'',NULL,NULL,'1','0','F','0','0','inventory:warehouse:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(119,'编辑仓库',24,2,'',NULL,NULL,'1','0','F','0','0','inventory:warehouse:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(120,'删除仓库',24,3,'',NULL,NULL,'1','0','F','0','0','inventory:warehouse:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(121,'新增位置',25,1,'',NULL,NULL,'1','0','F','0','0','inventory:storage-location:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(122,'编辑位置',25,2,'',NULL,NULL,'1','0','F','0','0','inventory:storage-location:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(123,'删除位置',25,3,'',NULL,NULL,'1','0','F','0','0','inventory:storage-location:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(124,'位置查看',25,4,'',NULL,NULL,'1','0','F','0','0','inventory:storage-location:view','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(125,'导入库存',26,1,'',NULL,NULL,'1','0','F','0','0','inventory:stock:import','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(126,'库存流水',26,2,'',NULL,NULL,'1','0','F','0','0','inventory:transaction:view','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(127,'库存报表',26,3,'',NULL,NULL,'1','0','F','0','0','inventory:report:view','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(128,'编辑预警',27,1,'',NULL,NULL,'1','0','F','0','0','inventory:alert:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(129,'创建入库',28,1,'',NULL,NULL,'1','0','F','0','0','inventory:inbound:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(130,'审核入库',28,2,'',NULL,NULL,'1','0','F','0','0','inventory:inbound:approve','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(131,'创建出库',33,1,'',NULL,NULL,'1','0','F','0','0','inventory:outbound:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(132,'审核出库',33,2,'',NULL,NULL,'1','0','F','0','0','inventory:outbound:approve','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(133,'编辑出库',33,3,'',NULL,NULL,'1','0','F','0','0','inventory:outbound:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(134,'新增盘点',34,1,'',NULL,NULL,'1','0','F','0','0','inventory:stocktake:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(135,'编辑盘点',34,2,'',NULL,NULL,'1','0','F','0','0','inventory:stocktake:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(136,'审核盘点',34,3,'',NULL,NULL,'1','0','F','0','0','inventory:stocktake:approve','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(137,'新增调拨',35,1,'',NULL,NULL,'1','0','F','0','0','inventory:transfer:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(138,'编辑调拨',35,2,'',NULL,NULL,'1','0','F','0','0','inventory:transfer:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(139,'审核调拨',35,3,'',NULL,NULL,'1','0','F','0','0','inventory:transfer:approve','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(140,'新增工单',45,1,'',NULL,NULL,'1','0','F','0','0','production:order:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(141,'编辑工单',45,2,'',NULL,NULL,'1','0','F','0','0','production:order:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(142,'删除工单',45,3,'',NULL,NULL,'1','0','F','0','0','production:order:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(143,'导出工单',45,4,'',NULL,NULL,'1','0','F','0','0','production:order:export','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(144,'执行查看',48,1,'',NULL,NULL,'1','0','F','0','0','production:operation-execution:view','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(145,'新增执行',48,2,'',NULL,NULL,'1','0','F','0','0','production:operation-execution:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(146,'编辑执行',48,3,'',NULL,NULL,'1','0','F','0','0','production:operation-execution:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(147,'删除执行',48,4,'',NULL,NULL,'1','0','F','0','0','production:operation-execution:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(148,'导出行执行',48,5,'',NULL,NULL,'1','0','F','0','0','production:operation-execution:export','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(149,'导入执行',48,6,'',NULL,NULL,'1','0','F','0','0','production:operation-execution:import','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(150,'操作记录查看',51,1,'',NULL,NULL,'1','0','F','0','0','production:operation-record:view','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(151,'新增操作记录',51,2,'',NULL,NULL,'1','0','F','0','0','production:operation-record:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(152,'编辑操作记录',51,3,'',NULL,NULL,'1','0','F','0','0','production:operation-record:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(153,'删除操作记录',51,4,'',NULL,NULL,'1','0','F','0','0','production:operation-record:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(154,'导出操作记录',51,5,'',NULL,NULL,'1','0','F','0','0','production:operation-record:export','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(155,'导入操作记录',51,6,'',NULL,NULL,'1','0','F','0','0','production:operation-record:import','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(156,'新增供应商',37,1,'',NULL,NULL,'1','0','F','0','0','purchase:supplier:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(157,'编辑供应商',37,2,'',NULL,NULL,'1','0','F','0','0','purchase:supplier:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(158,'删除供应商',37,3,'',NULL,NULL,'1','0','F','0','0','purchase:supplier:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(159,'导出供应商',37,4,'',NULL,NULL,'1','0','F','0','0','purchase:supplier:export','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(160,'导入供应商',37,5,'',NULL,NULL,'1','0','F','0','0','purchase:supplier:import','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(161,'新增采购单',38,1,'',NULL,NULL,'1','0','F','0','0','purchase:order:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(162,'编辑采购单',38,2,'',NULL,NULL,'1','0','F','0','0','purchase:order:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(163,'审核采购单',38,3,'',NULL,NULL,'1','0','F','0','0','purchase:order:approve','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(164,'导出采购单',38,4,'',NULL,NULL,'1','0','F','0','0','purchase:order:export','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(165,'采购发票',36,3,'',NULL,NULL,'1','0','C','1','0','purchase:invoice:view','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(166,'新增发票',165,1,'',NULL,NULL,'1','0','F','0','0','purchase:invoice:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(167,'编辑发票',165,2,'',NULL,NULL,'1','0','F','0','0','purchase:invoice:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(168,'删除发票',165,3,'',NULL,NULL,'1','0','F','0','0','purchase:invoice:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(169,'导出发票',165,4,'',NULL,NULL,'1','0','F','0','0','purchase:invoice:export','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(170,'导入发票',165,5,'',NULL,NULL,'1','0','F','0','0','purchase:invoice:import','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(172,'采购付款',36,4,'',NULL,NULL,'1','0','C','1','0','purchase:payment:view','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(173,'新增付款',172,1,'',NULL,NULL,'1','0','F','0','0','purchase:payment:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(174,'编辑付款',172,2,'',NULL,NULL,'1','0','F','0','0','purchase:payment:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(175,'删除付款',172,3,'',NULL,NULL,'1','0','F','0','0','purchase:payment:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(176,'审核付款',172,4,'',NULL,NULL,'1','0','F','0','0','purchase:payment:approve','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(177,'导出付款',172,5,'',NULL,NULL,'1','0','F','0','0','purchase:payment:export','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(178,'导入付款',172,6,'',NULL,NULL,'1','0','F','0','0','purchase:payment:import','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(180,'采购收货',36,5,'',NULL,NULL,'1','0','C','1','0','purchase:receipt:view','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(181,'新增收货',180,1,'',NULL,NULL,'1','0','F','0','0','purchase:receipt:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(182,'编辑收货',180,2,'',NULL,NULL,'1','0','F','0','0','purchase:receipt:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(183,'删除收货',180,3,'',NULL,NULL,'1','0','F','0','0','purchase:receipt:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(184,'导出收货',180,4,'',NULL,NULL,'1','0','F','0','0','purchase:receipt:export','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(185,'导入收货',180,5,'',NULL,NULL,'1','0','F','0','0','purchase:receipt:import','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(187,'编辑用户',2,2,'',NULL,NULL,'1','0','F','0','0','system:user:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(188,'删除用户',2,3,'',NULL,NULL,'1','0','F','0','0','system:user:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(189,'重置密码',2,4,'',NULL,NULL,'1','0','F','0','0','system:user:resetPwd','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(190,'新增角色',3,2,'',NULL,NULL,'1','0','F','0','0','system:role:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(191,'编辑角色',3,3,'',NULL,NULL,'1','0','F','0','0','system:role:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(192,'删除角色',3,4,'',NULL,NULL,'1','0','F','0','0','system:role:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(193,'新增菜单',4,2,'',NULL,NULL,'1','0','F','0','0','system:menu:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(194,'编辑菜单',4,3,'',NULL,NULL,'1','0','F','0','0','system:menu:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(195,'删除菜单',4,4,'',NULL,NULL,'1','0','F','0','0','system:menu:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(196,'新增部门',5,2,'',NULL,NULL,'1','0','F','0','0','system:dept:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(197,'编辑部门',5,3,'',NULL,NULL,'1','0','F','0','0','system:dept:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(198,'删除部门',5,4,'',NULL,NULL,'1','0','F','0','0','system:dept:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(199,'新增字典',61,2,'',NULL,NULL,'1','0','F','0','0','system:dict:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(200,'编辑字典',61,3,'',NULL,NULL,'1','0','F','0','0','system:dict:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(201,'删除字典',61,4,'',NULL,NULL,'1','0','F','0','0','system:dict:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(202,'字典列表',61,5,'',NULL,NULL,'1','0','F','0','0','system:dict:list','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(203,'字典查询',61,6,'',NULL,NULL,'1','0','F','0','0','system:dict:query','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(204,'开始审核',17,1,'',NULL,NULL,'1','0','F','0','0','sales:order:review','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 07:43:28',''),(205,'排程管理',43,2,'schedule','views/production/order/index.vue',NULL,'1','0','C','0','0',NULL,'#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 11:24:22',''),(206,'新增BOM',90,1,'',NULL,NULL,'1','0','F','0','0','engineering:bom:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 11:48:32',''),(207,'编辑BOM',90,2,'',NULL,NULL,'1','0','F','0','0','engineering:bom:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 11:48:32',''),(208,'删除BOM',90,3,'',NULL,NULL,'1','0','F','0','0','engineering:bom:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 11:48:32',''),(209,'审核BOM',90,4,'',NULL,NULL,'1','0','F','0','0','engineering:bom:approve','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 11:48:32',''),(210,'驳回BOM',90,5,'',NULL,NULL,'1','0','F','0','0','engineering:bom:reject','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 11:48:32',''),(211,'新增工艺',90,6,'',NULL,NULL,'1','0','F','0','0','engineering:routing:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 11:48:32',''),(212,'编辑工艺',90,7,'',NULL,NULL,'1','0','F','0','0','engineering:routing:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 11:48:32',''),(213,'新增工序',90,8,'',NULL,NULL,'1','0','F','0','0','engineering:standard-process:add','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 11:48:32',''),(214,'编辑工序',90,9,'',NULL,NULL,'1','0','F','0','0','engineering:standard-process:edit','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-24 11:48:32',''),(215,'销售报表',13,7,'report','views/sales/report/index.vue',NULL,'1','0','C','0','0','sales:report:view','TrendCharts',NULL,NULL,'1',NULL,6,'admin',NULL,'admin','2026-07-25 00:21:29',''),(216,'采购报表',36,6,'report','views/purchase/report/index.vue',NULL,'1','0','C','0','0',NULL,'TrendCharts',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-25 00:21:29',''),(217,'质量报表',50,1,'report','views/production/quality/report.vue',NULL,'1','0','C','0','0',NULL,'TrendCharts',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-25 00:21:29',''),(218,'发货管理',13,8,'delivery',NULL,NULL,'1','0','C','0','0','sales:delivery:view','Van',NULL,NULL,'1',NULL,7,'admin',NULL,'admin','2026-07-25 04:11:12',''),(219,'删除采购单',38,8,NULL,NULL,NULL,'1','0','F','0','0','purchase:order:delete','#',NULL,NULL,'1',NULL,0,'admin',NULL,'admin','2026-07-25 04:51:26',''),(220,'新增工程',90,4,'#',NULL,NULL,'1','0','F','0','0','engineering:add','#',NULL,NULL,'1',NULL,0,'admin','2026-07-25 17:49:55','admin','2026-07-25 09:49:55',''),(221,'编辑工程',90,5,'#',NULL,NULL,'1','0','F','0','0','engineering:edit','#',NULL,NULL,'1',NULL,0,'admin','2026-07-25 17:49:55','admin','2026-07-25 09:49:55',''),(222,'删除工程',90,6,'#',NULL,NULL,'1','0','F','0','0','engineering:delete','#',NULL,NULL,'1',NULL,0,'admin','2026-07-25 17:49:55','admin','2026-07-25 09:49:55',''),(223,'询价管理',13,1,'inquiry','views/sales/inquiry/index.vue',NULL,'1','0','C','0','0','sales:inquiry:view','Document','0,13',NULL,'1',NULL,0,'admin','2026-07-29 10:50:36','admin','2026-07-29 02:50:36','销售询价单管理'),(224,'新增询价',223,1,NULL,NULL,NULL,'1','0','F','0','0','sales:inquiry:add',NULL,'0,13,223',NULL,'1',NULL,0,'admin','2026-07-29 10:50:36','admin','2026-07-29 02:50:36','新增询价'),(225,'编辑询价',223,2,NULL,NULL,NULL,'1','0','F','0','0','sales:inquiry:edit',NULL,'0,13,223',NULL,'1',NULL,0,'admin','2026-07-29 10:50:36','admin','2026-07-29 02:50:36','编辑询价'),(226,'删除询价',223,3,NULL,NULL,NULL,'1','0','F','0','0','sales:inquiry:delete',NULL,'0,13,223',NULL,'1',NULL,0,'admin','2026-07-29 10:50:36','admin','2026-07-29 02:50:36','删除询价'),(227,'导出询价',223,4,NULL,NULL,NULL,'1','0','F','0','0','sales:inquiry:export',NULL,'0,13,223',NULL,'1',NULL,0,'admin','2026-07-29 10:50:36','admin','2026-07-29 02:50:36','导出询价'),(228,'转报价',223,5,NULL,NULL,NULL,'1','0','F','0','0','sales:inquiry:convert',NULL,'0,13,223',NULL,'1',NULL,0,'admin','2026-07-29 10:50:36','admin','2026-07-29 02:50:36','询价转报价'),(229,'样品单管理',13,2,'sample-order','views/sales/sample-order/index.vue',NULL,'1','0','C','0','0','sales:sample:view','Document','0,13',NULL,'1',NULL,3,'admin','2026-07-29 11:13:27','admin','2026-07-29 03:13:27','样品订单管理（独立生命周期）'),(230,'新增样品单',229,1,NULL,NULL,NULL,'1','0','F','0','0','sales:sample:add',NULL,'0,13,229',NULL,'1',NULL,0,'admin','2026-07-29 11:13:27','admin','2026-07-29 03:13:27','新增样品单'),(231,'编辑样品单',229,2,NULL,NULL,NULL,'1','0','F','0','0','sales:sample:edit',NULL,'0,13,229',NULL,'1',NULL,0,'admin','2026-07-29 11:13:27','admin','2026-07-29 03:13:27','编辑样品单'),(232,'删除样品单',229,3,NULL,NULL,NULL,'1','0','F','0','0','sales:sample:delete',NULL,'0,13,229',NULL,'1',NULL,0,'admin','2026-07-29 11:13:27','admin','2026-07-29 03:13:27','删除样品单'),(233,'样品单审核',229,4,NULL,NULL,NULL,'1','0','F','0','0','sales:sample:approve',NULL,'0,13,229',NULL,'1',NULL,0,'admin','2026-07-29 11:13:27','admin','2026-07-29 03:13:27','样品单审核'),(234,'工程接单',229,5,NULL,NULL,NULL,'1','0','F','0','0','sales:sample:engineering',NULL,'0,13,229',NULL,'1',NULL,0,'admin','2026-07-29 11:13:27','admin','2026-07-29 03:13:27','样品工程操作'),(235,'送样管理',229,6,NULL,NULL,NULL,'1','0','F','0','0','sales:sample:deliver',NULL,'0,13,229',NULL,'1',NULL,0,'admin','2026-07-29 11:13:27','admin','2026-07-29 03:13:27','送样/快递管理'),(236,'样品确认',229,7,NULL,NULL,NULL,'1','0','F','0','0','sales:sample:confirm',NULL,'0,13,229',NULL,'1',NULL,0,'admin','2026-07-29 11:13:27','admin','2026-07-29 03:13:27','客户确认/退回'),(237,'转量产',229,8,NULL,NULL,NULL,'1','0','F','0','0','sales:sample:convert',NULL,'0,13,229',NULL,'1',NULL,0,'admin','2026-07-29 11:13:27','admin','2026-07-29 03:13:27','样品转量产'),(238,'事件配置',1,11,'event-config','views/system/eventConfig/index.vue',NULL,'1','0','C','0','0','system:eventConfig:view','Setting',NULL,'EventConfig','1',NULL,0,'admin','2026-07-31 01:17:38','admin','2026-07-30 17:17:38',''),(239,'打样平台',90,9,'sample-workbench','views/engineering/sample-workbench/index.vue',NULL,'1','0','C','0','0','engineering:sample:workbench','Tools',NULL,NULL,'1',NULL,0,'admin','2026-08-03 16:54:45','admin','2026-08-03 08:54:45',''),(240,'物料列表',19,1,'index','views/inventory/material/index.vue',NULL,'1','0','C','0','0','inventory:material:view','Box',NULL,NULL,'1',NULL,0,'admin','2026-08-03 17:17:05','admin','2026-08-03 09:17:05','');
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_notification`
--

DROP TABLE IF EXISTS `sys_notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notification` (
  `notification_id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL COMMENT '标题',
  `content` text COMMENT '内容',
  `notification_type` varchar(20) NOT NULL COMMENT '类型: SYSTEM/EMAIL/APP/SMS',
  `event_code` varchar(50) DEFAULT NULL COMMENT '触发事件编码',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '业务类型: ORDER_UPDATE/QUALITY_ALERT/INVENTORY_WARN',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '业务ID',
  `sender_id` bigint DEFAULT NULL COMMENT '发送者ID',
  `sender_name` varchar(100) DEFAULT NULL COMMENT '发送者名称',
  `receiver_id` bigint NOT NULL COMMENT '接收者ID',
  `receiver_name` varchar(100) DEFAULT NULL COMMENT '接收者名称',
  `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读',
  `read_time` datetime DEFAULT NULL COMMENT '读取时间',
  `priority` varchar(10) DEFAULT 'NORMAL' COMMENT '优先级: LOW/NORMAL/HIGH/URGENT',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0待发送/1已发送',
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `send_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`notification_id`),
  KEY `idx_receiver` (`receiver_id`,`is_read`),
  KEY `idx_type` (`notification_type`,`status`),
  KEY `idx_send_time` (`send_time`)
) ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息通知表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_notification`
--

LOCK TABLES `sys_notification` WRITE;
/*!40000 ALTER TABLE `sys_notification` DISABLE KEYS */;
INSERT INTO `sys_notification` VALUES (1,'询价单【INQ2608040001】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','system',NULL,'inquiry.converted',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 15:22:31','2026-08-04 15:22:30','2026-08-04 15:22:30'),(2,'询价单【INQ2608040001】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','system',NULL,'inquiry.converted',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 15:22:31','2026-08-04 15:22:30','2026-08-04 15:22:30'),(3,'报价单【1】已提交审核','报价单已提交审核，请尽快处理。','system',NULL,'quotation.submitted',NULL,NULL,NULL,27,NULL,0,NULL,'normal',1,NULL,'2026-08-04 15:25:37','2026-08-04 15:25:36','2026-08-04 15:25:36'),(4,'报价单【1】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 15:36:03','2026-08-04 15:36:02','2026-08-04 15:36:02'),(5,'报价单【1】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 15:36:03','2026-08-04 15:36:02','2026-08-04 15:36:02'),(6,'报价单【1】已发送给客户','报价单已发送给客户，请关注客户反馈。','system',NULL,'quotation.sent',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 15:36:07','2026-08-04 15:36:07','2026-08-04 15:36:07'),(7,'报价单【1】已发送给客户','报价单已发送给客户，请关注客户反馈。','system',NULL,'quotation.sent',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 15:36:07','2026-08-04 15:36:07','2026-08-04 15:36:07'),(8,'报价单【1】客户已拒绝','客户拒绝了该报价单，请查看拒绝原因并跟进。','system',NULL,'quotation.rejected',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 15:36:20','2026-08-04 15:36:20','2026-08-04 15:36:20'),(9,'报价单【1】客户已拒绝','客户拒绝了该报价单，请查看拒绝原因并跟进。','system',NULL,'quotation.rejected',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 15:36:20','2026-08-04 15:36:20','2026-08-04 15:36:20'),(10,'询价单【INQ2608040002】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','system',NULL,'inquiry.converted',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 15:49:12','2026-08-04 15:49:11','2026-08-04 15:49:11'),(11,'询价单【INQ2608040002】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','system',NULL,'inquiry.converted',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 15:49:12','2026-08-04 15:49:11','2026-08-04 15:49:11'),(12,'询价单【INQ2608040003】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','system',NULL,'inquiry.converted',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 15:53:42','2026-08-04 15:53:41','2026-08-04 15:53:41'),(13,'询价单【INQ2608040003】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','system',NULL,'inquiry.converted',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 15:53:42','2026-08-04 15:53:41','2026-08-04 15:53:41'),(14,'报价单【1】已提交审核','报价单已提交审核，请尽快处理。','system',NULL,'quotation.submitted',NULL,NULL,NULL,27,NULL,0,NULL,'normal',1,NULL,'2026-08-04 16:03:04','2026-08-04 16:03:04','2026-08-04 16:03:04'),(15,'报价单【1】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 16:03:07','2026-08-04 16:03:07','2026-08-04 16:03:07'),(16,'报价单【1】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 16:03:07','2026-08-04 16:03:07','2026-08-04 16:03:07'),(17,'报价单【1】已发送给客户','报价单已发送给客户，请关注客户反馈。','system',NULL,'quotation.sent',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 16:03:20','2026-08-04 16:03:19','2026-08-04 16:03:19'),(18,'报价单【1】已发送给客户','报价单已发送给客户，请关注客户反馈。','system',NULL,'quotation.sent',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 16:03:20','2026-08-04 16:03:19','2026-08-04 16:03:19'),(19,'报价单【1】客户已确认','客户已确认报价单，请及时转为销售订单。','system',NULL,'quotation.confirmed',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 16:03:24','2026-08-04 16:03:24','2026-08-04 16:03:24'),(20,'报价单【1】客户已确认','客户已确认报价单，请及时转为销售订单。','system',NULL,'quotation.confirmed',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 16:03:24','2026-08-04 16:03:24','2026-08-04 16:03:24'),(21,'样品单【1】已创建，请安排打样','报价单已转为样品单，请工程部门安排打样工作。','system',NULL,'sample.created',NULL,NULL,NULL,28,NULL,0,NULL,'normal',1,NULL,'2026-08-04 16:03:27','2026-08-04 16:03:27','2026-08-04 16:03:27'),(22,'样品【1】已制作完成','样品已制作完成，请安排送样。','system',NULL,'sample.ready',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 16:06:54','2026-08-04 16:06:54','2026-08-04 16:06:54'),(23,'样品【1】已制作完成','样品已制作完成，请安排送样。','system',NULL,'sample.ready',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 16:06:54','2026-08-04 16:06:54','2026-08-04 16:06:54'),(24,'样品【1】已制作完成','样品已制作完成，请安排送样。','system',NULL,'sample.ready',NULL,NULL,NULL,28,NULL,0,NULL,'normal',1,NULL,'2026-08-04 16:06:54','2026-08-04 16:06:54','2026-08-04 16:06:54'),(25,'样品单【1】资料转移完成，请完善产品/BOM/工艺档案并提交审核','样品打样成果已建档（产品/BOM/工艺路线），请工程完善后提交审核','system',NULL,'sample.transferred',NULL,NULL,NULL,28,NULL,0,NULL,'normal',1,NULL,'2026-08-04 16:07:20','2026-08-04 16:07:19','2026-08-04 16:07:19'),(26,'工艺路线【1】已提交审核','工艺路线已提交审核，请审核。','system',NULL,'product.routing.submitted',NULL,NULL,NULL,28,NULL,0,NULL,'normal',1,NULL,'2026-08-04 17:08:30','2026-08-04 17:08:29','2026-08-04 17:08:29'),(27,'工艺路线【1】审核通过','工艺路线审核已通过。','system',NULL,'product.routing.approved',NULL,NULL,NULL,28,NULL,0,NULL,'normal',1,NULL,'2026-08-04 17:08:34','2026-08-04 17:08:33','2026-08-04 17:08:33'),(28,'BOM【{bizId}】已提交审核','BOM已提交审核，请尽快处理。','system',NULL,'bom.submitted',NULL,NULL,NULL,28,NULL,0,NULL,'normal',1,NULL,'2026-08-04 17:13:21','2026-08-04 17:13:20','2026-08-04 17:13:20'),(29,'BOM【{bizId}】审核通过','BOM已审核通过。','system',NULL,'bom.approved',NULL,NULL,NULL,28,NULL,0,NULL,'normal',1,NULL,'2026-08-04 17:13:29','2026-08-04 17:13:29','2026-08-04 17:13:29'),(30,'产品【{bizId}】审核通过','产品已审核通过。','system',NULL,'product.approved',NULL,NULL,NULL,28,NULL,0,NULL,'normal',1,NULL,'2026-08-04 17:13:47','2026-08-04 17:13:47','2026-08-04 17:13:47'),(31,'订单【{bizId}】已提交','销售订单已提交，请处理。','system',NULL,'order.submitted',NULL,NULL,NULL,27,NULL,0,NULL,'normal',1,NULL,'2026-08-04 17:18:13','2026-08-04 17:18:12','2026-08-04 17:18:12'),(32,'订单【2】开始审核','订单已进入审核流程，请尽快处理。','system',NULL,'order.review_started',NULL,NULL,NULL,27,NULL,0,NULL,'normal',1,NULL,'2026-08-04 17:18:17','2026-08-04 17:18:17','2026-08-04 17:18:17'),(33,'报价单【5】已提交审核','报价单已提交审核，请尽快处理。','system',NULL,'quotation.submitted',NULL,NULL,NULL,27,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:56:06','2026-08-04 21:56:19','2026-08-04 21:56:19'),(34,'报价单【5】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:56:29','2026-08-04 21:56:28','2026-08-04 21:56:28'),(35,'报价单【5】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:56:29','2026-08-04 21:56:28','2026-08-04 21:56:28'),(36,'报价单【5】已发送给客户','报价单已发送给客户，请关注客户反馈。','system',NULL,'quotation.sent',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:56:29','2026-08-04 21:56:28','2026-08-04 21:56:28'),(37,'报价单【5】已发送给客户','报价单已发送给客户，请关注客户反馈。','system',NULL,'quotation.sent',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:56:29','2026-08-04 21:56:28','2026-08-04 21:56:28'),(38,'报价单【5】客户已确认','客户已确认报价单，请及时转为销售订单。','system',NULL,'quotation.confirmed',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:56:35','2026-08-04 21:56:34','2026-08-04 21:56:34'),(39,'报价单【5】客户已确认','客户已确认报价单，请及时转为销售订单。','system',NULL,'quotation.confirmed',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:56:35','2026-08-04 21:56:34','2026-08-04 21:56:34'),(40,'报价单【6】已提交审核','报价单已提交审核，请尽快处理。','system',NULL,'quotation.submitted',NULL,NULL,NULL,27,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:58:20','2026-08-04 21:58:19','2026-08-04 21:58:19'),(41,'报价单【6】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:58:25','2026-08-04 21:58:24','2026-08-04 21:58:24'),(42,'报价单【6】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:58:25','2026-08-04 21:58:24','2026-08-04 21:58:24'),(43,'报价单【6】已发送给客户','报价单已发送给客户，请关注客户反馈。','system',NULL,'quotation.sent',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:58:25','2026-08-04 21:58:24','2026-08-04 21:58:24'),(44,'报价单【6】已发送给客户','报价单已发送给客户，请关注客户反馈。','system',NULL,'quotation.sent',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:58:25','2026-08-04 21:58:24','2026-08-04 21:58:24'),(45,'报价单【6】客户已拒绝','客户拒绝了该报价单，请查看拒绝原因并跟进。','system',NULL,'quotation.rejected',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:58:25','2026-08-04 21:58:25','2026-08-04 21:58:25'),(46,'报价单【6】客户已拒绝','客户拒绝了该报价单，请查看拒绝原因并跟进。','system',NULL,'quotation.rejected',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-04 21:58:25','2026-08-04 21:58:25','2026-08-04 21:58:25'),(47,'客户【[Ljava.lang.Long;@262bebea】已删除','客户已删除。','system',NULL,'sales.customer.deleted',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-05 09:51:47','2026-08-05 09:51:46','2026-08-05 09:51:46'),(48,'客户【[Ljava.lang.Long;@262bebea】已删除','客户已删除。','system',NULL,'sales.customer.deleted',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-05 09:51:47','2026-08-05 09:51:46','2026-08-05 09:51:46'),(49,'客户【CustomerEditDTO(customerId=9, customerCode=CST260730003)】已修改','客户信息已更新。','system',NULL,'sales.customer.updated',NULL,NULL,NULL,26,NULL,0,NULL,'normal',1,NULL,'2026-08-05 09:52:49','2026-08-05 09:52:49','2026-08-05 09:52:49'),(50,'客户【CustomerEditDTO(customerId=9, customerCode=CST260730003)】已修改','客户信息已更新。','system',NULL,'sales.customer.updated',NULL,NULL,NULL,29,NULL,0,NULL,'normal',1,NULL,'2026-08-05 09:52:49','2026-08-05 09:52:49','2026-08-05 09:52:49');
/*!40000 ALTER TABLE `sys_notification` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_oper_log`
--

DROP TABLE IF EXISTS `sys_oper_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_oper_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `username` varchar(50) DEFAULT NULL COMMENT '操作人账号',
  `real_name` varchar(50) DEFAULT NULL COMMENT '操作人姓名',
  `tenant_id` bigint DEFAULT '1' COMMENT '租户ID',
  `module` varchar(50) DEFAULT NULL COMMENT '操作模块',
  `business_type` bigint NOT NULL DEFAULT '1' COMMENT '业务类型',
  `biz_type` varchar(20) DEFAULT NULL COMMENT '业务类型：ORDER/PRODUCT/BOM/ROUTING/INVENTORY',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '业务ID：订单号/产品ID/BOM_ID等',
  `trace_id` varchar(50) DEFAULT NULL COMMENT '追踪ID，贯穿整笔请求',
  `biz_status` int DEFAULT NULL COMMENT '业务状态码',
  `oper_url` varchar(200) DEFAULT NULL COMMENT '请求URL',
  `oper_ip` varchar(50) DEFAULT NULL COMMENT '操作IP',
  `oper_param` varchar(2000) DEFAULT NULL COMMENT '请求参数(限制长度)',
  `detail` json DEFAULT NULL COMMENT '操作详情（JSON格式）',
  `cost_time` bigint DEFAULT NULL COMMENT '耗时(毫秒)',
  `status` tinyint DEFAULT '1' COMMENT '状态(0失败 1成功)',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '浏览器UA',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_module_time` (`module`,`create_time`),
  KEY `idx_biz` (`biz_type`,`biz_id`,`create_time`),
  KEY `idx_trace` (`trace_id`)
) ENGINE=InnoDB AUTO_INCREMENT=108 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_oper_log`
--

LOCK TABLES `sys_oper_log` WRITE;
/*!40000 ALTER TABLE `sys_oper_log` DISABLE KEYS */;
INSERT INTO `sys_oper_log` VALUES (1,26,'xiaoshou0','销售0',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,9,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:19:29'),(2,26,'xiaoshou0','销售0',1,'询价单管理',1,'inquiry','1','e32451d48af1460e',0,'/sales/inquiry','127.0.0.1','{\"inquiry\":{\"customerId\":1,\"customerName\":\"捷顺通电子科技有限公司\",\"contactPerson\":\"王经理\",\"contactPhone\":\"13800138001\",\"inquiryDate\":1785772800000,\"expectedQuantity\":1,\"productDescription\":\"\",\"sizeDescription\":\"\",\"materialRequirements\":\"\",\"circuitRequirements\":\"\",\"connectorRequirements\":\"\",\"specialRequirements\":\"\",\"hasDrawing\":0,\"inquiryStatus\":0,\"inquiryType\":2,\"remark\":\"\",\"salesPersonName\":\"\",\"params\":{}}}',NULL,31,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:20:05'),(3,26,'xiaoshou0','销售0',1,'询价单管理',2,'inquiry','1','e32451d48af1460e',3,'/sales/inquiry/convert/1','127.0.0.1','{\"inquiryId\":1}',NULL,218,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:22:31'),(4,26,'xiaoshou0','销售0',1,'报价单管理',2,NULL,NULL,NULL,NULL,'/sales/quotation/submit-review/1','127.0.0.1','{\"quotationId\":1,\"attachmentIds\":\"4\"}',NULL,10,0,'报价金额必须大于0','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:24:30'),(5,26,'xiaoshou0','销售0',1,'报价单管理',2,'quotation','1','e32451d48af1460e',0,'/sales/quotation','127.0.0.1','{\"quotation\":{\"quotationId\":1,\"traceId\":\"e32451d48af1460e\",\"quotationNo\":\"QT2608040001\",\"quotationType\":2,\"customerId\":1,\"customerName\":\"捷顺通电子科技有限公司\",\"contactPerson\":\"王经理\",\"contactPhone\":\"13800138001\",\"quotationDate\":1785772800000,\"validUntil\":1788364800000,\"currency\":\"CNY\",\"exchangeRate\":1,\"quotationStatus\":0,\"subtotalAmount\":660,\"taxRate\":0,\"taxAmount\":0,\"totalAmount\":660,\"discountAmount\":0,\"finalAmount\":660,\"remark\":\"由询价单[INQ2608040001]自动创建\",\"salesPersonId\":26,\"salesPersonName\":\"xiaoshou0\",\"items\":[{\"productCode\":\"QT2608040001\",\"productName\":\"QT2608040001\",\"quantity\":2,\"unitPrice\":330,\"unit\":\"PCS\",\"amount\":660}],\"deleted\":0,\"createBy\":\"xiaoshou0\",\"createTime\":1785828151000,\"updateBy\":\"xiaoshou0\",\"updateTime\":1785828151000,\"params\":{}}}',NULL,22,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:25:29'),(6,26,'xiaoshou0','销售0',1,'报价单管理',2,'quotation','1','e32451d48af1460e',5,'/sales/quotation/submit-review/1','127.0.0.1','{\"quotationId\":1,\"attachmentIds\":\"5\"}',NULL,36,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:25:37'),(7,26,'xiaoshou0','销售0',1,'报价单管理',6,'quotation','1','e32451d48af1460e',6,'/sales/quotation/review/1','127.0.0.1','{\"approved\":true,\"quotationId\":1,\"remark\":\"\"}',NULL,89,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:36:03'),(8,26,'xiaoshou0','销售0',1,'报价单管理',2,'quotation','1','e32451d48af1460e',1,'/sales/quotation/send/1','127.0.0.1','{\"quotationId\":1}',NULL,27,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:36:07'),(9,26,'xiaoshou0','销售0',1,'报价单管理',2,'quotation','1','e32451d48af1460e',3,'/sales/quotation/reject/1','127.0.0.1','{\"quotationId\":1}',NULL,28,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:36:20'),(10,26,'xiaoshou0','销售0',1,'询价单管理',1,'inquiry','2','cdb02bd1b7ab4dc2',0,'/sales/inquiry','127.0.0.1','{\"inquiry\":{\"customerId\":4,\"customerName\":\"德力通电子实业有限公司\",\"contactPerson\":\"赵总\",\"contactPhone\":\"13600136004\",\"inquiryDate\":1785772800000,\"expectedQuantity\":1,\"productDescription\":\"\",\"sizeDescription\":\"\",\"materialRequirements\":\"\",\"circuitRequirements\":\"\",\"connectorRequirements\":\"\",\"specialRequirements\":\"\",\"hasDrawing\":1,\"inquiryStatus\":0,\"inquiryType\":2,\"remark\":\"\",\"salesPersonName\":\"\",\"params\":{}}}',NULL,13,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:48:39'),(11,26,'xiaoshou0','销售0',1,'询价单管理',2,'inquiry','2','cdb02bd1b7ab4dc2',3,'/sales/inquiry/convert/2','127.0.0.1','{\"inquiryId\":2}',NULL,27,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:49:12'),(12,26,'xiaoshou0','销售0',1,'询价单管理',1,'inquiry','3','6240eb735b0a4585',0,'/sales/inquiry','127.0.0.1','{\"inquiry\":{\"customerId\":3,\"customerName\":\"李记精密电子科技\",\"contactPerson\":\"陈工\",\"contactPhone\":\"13700137003\",\"inquiryDate\":1785772800000,\"expectedQuantity\":1,\"productDescription\":\"\",\"sizeDescription\":\"\",\"materialRequirements\":\"\",\"circuitRequirements\":\"\",\"connectorRequirements\":\"\",\"specialRequirements\":\"\",\"hasDrawing\":0,\"inquiryStatus\":0,\"inquiryType\":2,\"remark\":\"\",\"salesPersonName\":\"\",\"params\":{}}}',NULL,10,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:50:18'),(13,26,'xiaoshou0','销售0',1,'询价单管理',2,'inquiry','3','6240eb735b0a4585',3,'/sales/inquiry/convert/3','127.0.0.1','{\"inquiryId\":3}',NULL,31,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:53:42'),(14,26,'xiaoshou0','销售0',1,'报价单管理',1,'quotation','1','e32451d48af1460e',0,'/sales/quotation/copy/1','127.0.0.1','{\"quotationId\":1}',NULL,19,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:55:35'),(15,26,'xiaoshou0','销售0',1,'报价单管理',1,NULL,NULL,NULL,NULL,'/sales/quotation/copy/1','127.0.0.1','{\"quotationId\":1}',NULL,6,0,'报价单号已存在','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 15:55:41'),(16,26,NULL,NULL,1,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,12,0,'class com.jjx.system.domain.vo.LoginUser cannot be cast to class com.jjx.system.domain.vo.LoginUser (com.jjx.system.domain.vo.LoginUser is in unnamed module of loader org.springframework.boot.devtools.restart.classloader.RestartClassLoader @284e6c8e; com.jjx.system.domain.vo.LoginUser is in unnamed module of loader org.springframework.boot.devtools.restart.classloader.RestartClassLoader @1f5f0a75)',NULL,'2026-08-04 16:00:13'),(17,26,'xiaoshou0','销售0',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,11,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:00:44'),(18,26,'xiaoshou0','销售0',1,'报价单管理',3,'quotation',NULL,NULL,0,'/sales/quotation/4','127.0.0.1','{\"quotationIds\":[4]}',NULL,21,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:00:53'),(19,26,'xiaoshou0','销售0',1,'报价单管理',3,'quotation',NULL,NULL,0,'/sales/quotation/4','127.0.0.1','{\"quotationIds\":[4]}',NULL,76,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:01:12'),(20,1,'admin','系统管理员',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,7,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:01:36'),(21,26,'xiaoshou0','销售0',1,'报价单管理',2,NULL,NULL,NULL,NULL,'/sales/quotation/submit-review/3','127.0.0.1','{\"quotationId\":3,\"attachmentIds\":\"8\"}',NULL,11,0,'报价金额必须大于0','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:02:54'),(22,26,'xiaoshou0','销售0',1,'报价单管理',2,'quotation','1','e32451d48af1460e',0,'/sales/quotation/status/1','127.0.0.1','{\"quotationId\":1,\"status\":0}',NULL,17,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:03:01'),(23,26,'xiaoshou0','销售0',1,'报价单管理',2,'quotation','1','e32451d48af1460e',5,'/sales/quotation/submit-review/1','127.0.0.1','{\"quotationId\":1}',NULL,34,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:03:04'),(24,26,'xiaoshou0','销售0',1,'报价单管理',6,'quotation','1','e32451d48af1460e',6,'/sales/quotation/review/1','127.0.0.1','{\"approved\":true,\"quotationId\":1,\"remark\":\"\"}',NULL,8859,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:03:16'),(25,26,'xiaoshou0','销售0',1,'报价单管理',2,'quotation','1','e32451d48af1460e',1,'/sales/quotation/send/1','127.0.0.1','{\"quotationId\":1}',NULL,33,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:03:20'),(26,26,'xiaoshou0','销售0',1,'报价单管理',2,'quotation','1','e32451d48af1460e',2,'/sales/quotation/confirm/1','127.0.0.1','{\"quotationId\":1}',NULL,29,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:03:24'),(27,26,'xiaoshou0','销售0',1,'样品单管理',1,'sample','1','e32451d48af1460e',1,'/sales/sample-order/create-from-quotation/1','127.0.0.1','{\"quotationId\":1,\"sampleQty\":10}',NULL,65,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:03:27'),(28,26,'xiaoshou0','销售0',1,'样品单管理',2,'sample','1','e32451d48af1460e',2,'/sales/sample-order/submit-review/1','127.0.0.1','{\"orderId\":1}',NULL,240,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:03:45'),(29,26,'xiaoshou0','销售0',1,'样品单管理',2,NULL,NULL,NULL,NULL,'/sales/sample-order/submit-review/1','127.0.0.1','{\"orderId\":1}',NULL,102,0,'样品单状态已变更(当前:待审核)，无法提交审核，请刷新后重试','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:03:58'),(30,26,'xiaoshou0','销售0',1,'样品单管理',6,'sample','1','e32451d48af1460e',3,'/sales/sample-order/approve/1','127.0.0.1','{\"orderId\":1,\"remark\":\"\"}',NULL,41,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:04:10'),(31,28,'gongcheng0','工程0',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,9,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 16:05:19'),(32,28,'gongcheng0','工程0',1,'样品单管理',2,'sample','1','e32451d48af1460e',3,'/sales/sample-order/accept-engineering/1','127.0.0.1','{\"orderId\":1,\"acceptorName\":\"工程\"}',NULL,82,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 16:05:33'),(33,28,'gongcheng0','工程0',1,'样品单管理',2,'sample','1','e32451d48af1460e',0,'/sales/sample-order/update-process/1','127.0.0.1','{\"orderId\":1,\"dto\":{\"process\":\"印刷\",\"materials\":\"[{\\\"name\\\":\\\"PET薄膜 0.125mm 透明\\\",\\\"spec\\\":\\\"0.125mm×1200mm卷\\\",\\\"qty\\\":1,\\\"unit\\\":\\\"M\\\",\\\"materialId\\\":1,\\\"materialCode\\\":\\\"MAT-001\\\"}]\",\"durationMinutes\":33}}',NULL,63,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 16:06:14'),(34,28,'gongcheng0','工程0',1,'样品单管理',2,'sample','1','e32451d48af1460e',0,'/sales/sample-order/update-process/1','127.0.0.1','{\"orderId\":1,\"dto\":{\"process\":\"冲切\",\"materials\":\"[{\\\"name\\\":\\\"导电银浆 BY-6000\\\",\\\"spec\\\":\\\"1kg/罐\\\",\\\"qty\\\":2,\\\"unit\\\":\\\"KG\\\",\\\"materialId\\\":4,\\\"materialCode\\\":\\\"MAT-004\\\"}]\"}}',NULL,25,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 16:06:46'),(35,28,'gongcheng0','工程0',1,'样品单管理',2,'sample','1','e32451d48af1460e',4,'/sales/sample-order/mark-ready/1','127.0.0.1','{\"orderId\":1}',NULL,49,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 16:06:54'),(36,26,'xiaoshou0','销售0',1,'样品单管理',2,'sample','1','e32451d48af1460e',5,'/sales/sample-order/send-sample/1','127.0.0.1','{\"orderId\":1,\"trackingNo\":\"\"}',NULL,17,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:07:11'),(37,26,'xiaoshou0','销售0',1,'样品单管理',2,'sample','1','e32451d48af1460e',6,'/sales/sample-order/confirm/1','127.0.0.1','{\"orderId\":1,\"clientName\":\"客户确认\"}',NULL,21,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:07:15'),(38,28,'gongcheng0','工程0',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,8,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 16:13:07'),(39,28,'gongcheng0','工程0',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,6,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 16:22:51'),(40,28,'gongcheng0','工程0',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,7,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 16:44:01'),(41,28,'gongcheng0','工程0',1,'产品BOM管理',2,NULL,NULL,NULL,0,'/engineering/bom','127.0.0.1','{\"dto\":{\"bomId\":1,\"bomCode\":\"BOM-QT2608040001-SAMPLE\",\"bomName\":\"BOM-QT2608040001-SAMPLE\",\"bomVersion\":\"V1\",\"productId\":1,\"productCode\":\"QT2608040001\",\"productName\":\"QT2608040001\",\"approveStatus\":1,\"isCurrent\":true,\"effectiveDate\":1785801600000,\"remark\":\"由样品单[SP2608040001]资料转移生成，请工程确认后批准\",\"items\":[{\"itemId\":1,\"materialId\":1,\"materialCode\":\"MAT-001\",\"materialName\":\"PET薄膜 0.125mm 透明\",\"specification\":\"0.125mm×1200mm卷\",\"unit\":\"M\",\"quantity\":1,\"lossRate\":0,\"layer\":\"印刷\",\"sourceType\":\"buy\",\"itemOrder\":1},{\"itemId\":2,\"materialId\":4,\"materialCode\":\"MAT-004\",\"materialName\":\"导电银浆 BY-6000\",\"specification\":\"1kg/罐\",\"unit\":\"KG\",\"quantity\":2,\"lossRate\":0,\"layer\":\"冲切\",\"sourceType\":\"buy\",\"itemOrder\":2}]}}',NULL,23,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 16:47:36'),(42,28,NULL,NULL,1,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,20,0,'class com.jjx.system.domain.vo.LoginUser cannot be cast to class com.jjx.system.domain.vo.LoginUser (com.jjx.system.domain.vo.LoginUser is in unnamed module of loader org.springframework.boot.devtools.restart.classloader.RestartClassLoader @3fdda283; com.jjx.system.domain.vo.LoginUser is in unnamed module of loader org.springframework.boot.devtools.restart.classloader.RestartClassLoader @6f0c4a6a)',NULL,'2026-08-04 16:48:41'),(43,28,'gongcheng0','工程0',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,18,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 16:52:05'),(44,1,'admin','系统管理员',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,8,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 16:58:18'),(45,26,'xiaoshou0','销售0',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,8,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:01:35'),(46,28,'gongcheng0','工程0',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,12,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 17:07:43'),(47,28,'gongcheng0','工程0',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,7,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 17:08:16'),(48,28,'gongcheng0','工程0',1,'产品BOM管理',6,NULL,NULL,NULL,0,'/engineering/bom/approve/1','127.0.0.1','{\"bomId\":1,\"dto\":{\"bomId\":1,\"remark\":\"审核通过\"}}',NULL,10,0,'操作失败','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 17:09:13'),(49,28,'gongcheng0','工程0',1,'产品BOM管理',6,NULL,NULL,NULL,0,'/engineering/bom/approve/1','127.0.0.1','{\"bomId\":1,\"dto\":{\"bomId\":1,\"remark\":\"审核通过\"}}',NULL,5,0,'操作失败','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 17:09:21'),(50,28,'gongcheng0','工程0',1,'产品BOM管理',6,NULL,NULL,NULL,0,'/engineering/bom/approve/1','127.0.0.1','{\"bomId\":1,\"dto\":{\"bomId\":1,\"remark\":\"审核通过\"}}',NULL,4,0,'操作失败','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 17:09:43'),(51,28,'gongcheng0','工程0',1,'产品BOM管理',6,NULL,NULL,NULL,0,'/engineering/bom/approve/1','127.0.0.1','{\"bomId\":1,\"dto\":{\"bomId\":1,\"remark\":\"审核通过\"}}',NULL,4,0,'操作失败','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 17:09:56'),(52,26,'xiaoshou0','销售0',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,8,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:12:11'),(53,28,'gongcheng0','工程0',1,'产品BOM管理',2,NULL,NULL,NULL,0,'/engineering/bom/submit/1','127.0.0.1','{\"bomId\":1}',NULL,30,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 17:13:21'),(54,28,'gongcheng0','工程0',1,'产品BOM管理',6,NULL,NULL,NULL,0,'/engineering/bom/approve/1','127.0.0.1','{\"bomId\":1,\"dto\":{\"bomId\":1,\"remark\":\"审核通过\"}}',NULL,31,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 17:13:29'),(55,28,'gongcheng0','工程0',1,'产品管理',2,'product','1',NULL,0,'/product/approve/1','127.0.0.1','{\"productId\":1,\"dto\":{\"productId\":1}}',NULL,26,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 17:13:47'),(56,28,'gongcheng0','工程0',1,'产品管理',2,'product','1',NULL,0,'/product/release/1','127.0.0.1','{\"productId\":1}',NULL,30,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-04 17:13:51'),(57,1,'admin','系统管理员',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,6,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:14:19'),(58,1,'admin','系统管理员',1,'样品单管理',2,'sample','1','e32451d48af1460e',7,'/sales/sample-order/convert-to-production/1','127.0.0.1','{\"orderId\":1}',NULL,181,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:16:37'),(59,NULL,'admin',NULL,1,'sales_order',0,'ORDER','SP2608040001',NULL,NULL,'order.cancel',NULL,'草稿 => 已取消 (1 -> 10)',NULL,NULL,1,NULL,NULL,'2026-08-04 17:17:27'),(60,1,'admin','系统管理员',1,'订单状态管理',2,'order','1',NULL,0,'/sales/orders/1/status','127.0.0.1','{\"orderId\":1}',NULL,113,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:17:27'),(61,NULL,'admin',NULL,1,'sales_order',0,'ORDER','SO2608040001',NULL,NULL,'order.submit_review',NULL,'草稿 => 待审核 (1 -> 2)',NULL,NULL,1,NULL,NULL,'2026-08-04 17:18:12'),(62,1,'admin','系统管理员',1,'订单状态管理',2,'order','2',NULL,0,'/sales/orders/2/status/submissions','127.0.0.1','{\"orderId\":2}',NULL,25,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:18:13'),(63,NULL,'admin',NULL,1,'sales_order',0,'ORDER','SO2608040001',NULL,NULL,'order.start_review',NULL,'待审核 => 审核中 (2 -> 3)',NULL,NULL,1,NULL,NULL,'2026-08-04 17:18:17'),(64,1,'admin','系统管理员',1,'订单状态管理',2,'order','2',NULL,0,'/sales/orders/2/status/review','127.0.0.1','{\"orderId\":2}',NULL,25,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:18:17'),(65,1,NULL,NULL,1,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,11,0,'class com.jjx.system.domain.vo.LoginUser cannot be cast to class com.jjx.system.domain.vo.LoginUser (com.jjx.system.domain.vo.LoginUser is in unnamed module of loader org.springframework.boot.devtools.restart.classloader.RestartClassLoader @5e5f3bbb; com.jjx.system.domain.vo.LoginUser is in unnamed module of loader org.springframework.boot.devtools.restart.classloader.RestartClassLoader @c99b969)',NULL,'2026-08-04 17:25:37'),(66,1,NULL,NULL,1,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,11,0,'class com.jjx.system.domain.vo.LoginUser cannot be cast to class com.jjx.system.domain.vo.LoginUser (com.jjx.system.domain.vo.LoginUser is in unnamed module of loader org.springframework.boot.devtools.restart.classloader.RestartClassLoader @73fb2ea0; com.jjx.system.domain.vo.LoginUser is in unnamed module of loader org.springframework.boot.devtools.restart.classloader.RestartClassLoader @c99b969)',NULL,'2026-08-04 17:32:53'),(67,1,NULL,NULL,1,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,0,'class com.jjx.system.domain.vo.LoginUser cannot be cast to class com.jjx.system.domain.vo.LoginUser (com.jjx.system.domain.vo.LoginUser is in unnamed module of loader org.springframework.boot.devtools.restart.classloader.RestartClassLoader @73fb2ea0; com.jjx.system.domain.vo.LoginUser is in unnamed module of loader org.springframework.boot.devtools.restart.classloader.RestartClassLoader @c99b969)',NULL,'2026-08-04 17:33:03'),(68,1,'admin','系统管理员',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,10,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:33:39'),(69,NULL,'admin',NULL,1,'sales_order',0,'ORDER','SO2608040001',NULL,NULL,'order.approve',NULL,'审核中 => 已审核 (3 -> 4)',NULL,NULL,1,NULL,NULL,'2026-08-04 17:33:47'),(70,1,'admin','系统管理员',1,'订单状态管理',6,'order','2',NULL,0,'/sales/orders/2/status/approval','127.0.0.1','{\"orderId\":2,\"reviewDTO\":{\"orderId\":2,\"remark\":\"\"}}',NULL,178,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:33:47'),(71,1,'admin','系统管理员',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,14,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:35:13'),(72,NULL,'admin',NULL,1,'sales_order',0,'ORDER','SO2608040001',NULL,NULL,'order.cancel',NULL,'已审核 => 已确认 (4 -> 6)',NULL,NULL,1,NULL,NULL,'2026-08-04 17:43:18'),(73,1,'admin','系统管理员',1,'订单状态管理',2,'order','2',NULL,0,'/sales/orders/2/status/send-to-customer','127.0.0.1','{\"orderId\":2,\"dto\":{\"orderId\":2}}',NULL,173,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:43:18'),(74,NULL,'admin',NULL,1,'sales_order',0,'ORDER','SO2608040001',NULL,NULL,'order.start_production',NULL,'开始生产，共创建1个生产工单',NULL,NULL,1,NULL,NULL,'2026-08-04 17:43:28'),(75,1,'admin','系统管理员',1,'订单状态管理',2,'order','2',NULL,0,'/sales/orders/2/status/start-production','127.0.0.1','{\"orderId\":2}',NULL,49,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:43:29'),(76,1,'admin','系统管理员',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,13,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-04 17:57:12'),(77,1,'admin','系统管理员',1,'报价单管理',2,NULL,NULL,NULL,NULL,'/sales/quotation/send/3','127.0.0.1','{\"quotationId\":3}',NULL,21,0,'只有审核通过的报价单可以发送','curl/8.18.0','2026-08-04 21:54:44'),(78,1,'admin','系统管理员',1,'报价单管理',2,NULL,NULL,NULL,NULL,'/sales/quotation/submit-review/3','127.0.0.1','{\"quotationId\":3}',NULL,6,0,'报价金额必须大于0','curl/8.18.0','2026-08-04 21:54:50'),(79,1,'admin','系统管理员',1,'报价单管理',2,NULL,NULL,NULL,NULL,'/sales/quotation/submit-review/4','127.0.0.1','{\"quotationId\":4}',NULL,7,0,'报价明细不能为空，请先添加报价明细','curl/8.18.0','2026-08-04 21:55:03'),(80,1,'admin','系统管理员',1,'报价单管理',2,NULL,NULL,NULL,NULL,'/sales/quotation/send/4','127.0.0.1','{\"quotationId\":4}',NULL,7,0,'只有审核通过的报价单可以发送','curl/8.18.0','2026-08-04 21:55:03'),(81,1,'admin','系统管理员',1,'报价单管理',1,NULL,NULL,NULL,NULL,'/sales/quotation/copy/1','127.0.0.1','{\"quotationId\":1}',NULL,8,0,'报价单号已存在','curl/8.18.0','2026-08-04 21:55:25'),(82,1,'admin','系统管理员',1,'报价单管理',1,'quotation','5','736d131a524042d2',0,'/sales/quotation','127.0.0.1','{\"quotation\":{\"quotationNo\":\"TEST-QT-412-02\",\"quotationType\":1,\"customerId\":1,\"customerName\":\"捷顺通电子科技有限公司\",\"quotationDate\":1785772800000,\"validUntil\":1788451200000,\"currency\":\"CNY\",\"items\":[{\"productId\":1,\"productCode\":\"P001\",\"productName\":\"薄膜开关测试品\",\"quantity\":10,\"unitPrice\":100,\"unit\":\"PCS\",\"amount\":1000}],\"params\":{}}}',NULL,23,1,NULL,'curl/8.18.0','2026-08-04 21:56:00'),(83,1,'admin','系统管理员',1,'报价单管理',2,'quotation','5','736d131a524042d2',5,'/sales/quotation/submit-review/5','127.0.0.1','{\"quotationId\":5}',NULL,14184,1,NULL,'curl/8.18.0','2026-08-04 21:56:20'),(84,1,'admin','系统管理员',1,'报价单管理',2,NULL,NULL,NULL,NULL,'/sales/quotation/send/5','127.0.0.1','{\"quotationId\":5}',NULL,6,0,'只有审核通过的报价单可以发送','curl/8.18.0','2026-08-04 21:56:20'),(85,1,'admin','系统管理员',1,'报价单管理',6,'quotation','5','736d131a524042d2',6,'/sales/quotation/review/5','127.0.0.1','{\"approved\":true,\"quotationId\":5,\"remark\":\"ok\"}',NULL,29,1,NULL,'curl/8.18.0','2026-08-04 21:56:29'),(86,1,'admin','系统管理员',1,'报价单管理',2,'quotation','5','736d131a524042d2',1,'/sales/quotation/send/5','127.0.0.1','{\"quotationId\":5}',NULL,26,1,NULL,'curl/8.18.0','2026-08-04 21:56:29'),(87,1,'admin','系统管理员',1,'报价单管理',2,'quotation','5','736d131a524042d2',2,'/sales/quotation/status/5','127.0.0.1','{\"quotationId\":5,\"status\":2}',NULL,24,1,NULL,'curl/8.18.0','2026-08-04 21:56:35'),(88,1,'admin','系统管理员',1,'报价单管理',2,NULL,NULL,NULL,NULL,'/sales/quotation/submit-review/4','127.0.0.1','{\"quotationId\":4}',NULL,44713,0,'报价明细不能为空，请先添加报价明细','curl/8.18.0','2026-08-04 21:57:20'),(89,1,'admin','系统管理员',1,'报价单管理',6,NULL,NULL,NULL,NULL,'/sales/quotation/review/4','127.0.0.1','{\"approved\":true,\"quotationId\":4}',NULL,5,0,'只有待审核状态的报价单可以审核','curl/8.18.0','2026-08-04 21:57:20'),(90,1,'admin','系统管理员',1,'报价单管理',2,NULL,NULL,NULL,NULL,'/sales/quotation/send/4','127.0.0.1','{\"quotationId\":4}',NULL,4,0,'只有审核通过的报价单可以发送','curl/8.18.0','2026-08-04 21:57:20'),(91,1,'admin','系统管理员',1,'报价单管理',2,NULL,NULL,NULL,NULL,'/sales/quotation/status/4','127.0.0.1','{\"quotationId\":4,\"status\":3}',NULL,5,0,'状态转换不合法：从状态0转换到状态3','curl/8.18.0','2026-08-04 21:57:20'),(92,1,'admin','系统管理员',1,'报价单管理',1,'quotation','5','736d131a524042d2',0,'/sales/quotation/copy/5','127.0.0.1','{\"quotationId\":5}',NULL,11,1,NULL,'curl/8.18.0','2026-08-04 21:57:33'),(93,1,'admin','系统管理员',1,'报价单管理',2,NULL,NULL,NULL,NULL,'/sales/quotation/submit-review/6','127.0.0.1','{\"quotationId\":6}',NULL,6,0,'报价明细不能为空，请先添加报价明细','curl/8.18.0','2026-08-04 21:57:39'),(94,1,'admin','系统管理员',1,'报价单管理',6,NULL,NULL,NULL,NULL,'/sales/quotation/review/6','127.0.0.1','{\"approved\":true,\"quotationId\":6}',NULL,5,0,'只有待审核状态的报价单可以审核','curl/8.18.0','2026-08-04 21:57:39'),(95,1,'admin','系统管理员',1,'报价单管理',2,NULL,NULL,NULL,NULL,'/sales/quotation/send/6','127.0.0.1','{\"quotationId\":6}',NULL,5,0,'只有审核通过的报价单可以发送','curl/8.18.0','2026-08-04 21:57:39'),(96,1,'admin','系统管理员',1,'报价单管理',2,NULL,NULL,NULL,NULL,'/sales/quotation/status/6','127.0.0.1','{\"quotationId\":6,\"status\":3}',NULL,5,0,'状态转换不合法：从状态0转换到状态3','curl/8.18.0','2026-08-04 21:57:39'),(97,1,'admin','系统管理员',1,'报价单管理',2,'quotation','6','913f6b1791454c22',0,'/sales/quotation','127.0.0.1','{\"quotation\":{\"quotationId\":6,\"quotationNo\":\"COPY_TEST-QT-412-02\",\"quotationType\":1,\"customerId\":1,\"customerName\":\"捷顺通电子科技有限公司\",\"quotationDate\":1785772800000,\"validUntil\":1788451200000,\"currency\":\"CNY\",\"items\":[{\"productId\":1,\"productCode\":\"P001\",\"productName\":\"薄膜开关测试品\",\"quantity\":10,\"unitPrice\":100,\"unit\":\"PCS\",\"amount\":1000}],\"params\":{}}}',NULL,16,1,NULL,'curl/8.18.0','2026-08-04 21:58:20'),(98,1,'admin','系统管理员',1,'报价单管理',2,'quotation','6','913f6b1791454c22',5,'/sales/quotation/submit-review/6','127.0.0.1','{\"quotationId\":6}',NULL,21,1,NULL,'curl/8.18.0','2026-08-04 21:58:20'),(99,1,'admin','系统管理员',1,'报价单管理',6,'quotation','6','913f6b1791454c22',6,'/sales/quotation/review/6','127.0.0.1','{\"approved\":true,\"quotationId\":6}',NULL,20,1,NULL,'curl/8.18.0','2026-08-04 21:58:25'),(100,1,'admin','系统管理员',1,'报价单管理',2,'quotation','6','913f6b1791454c22',1,'/sales/quotation/send/6','127.0.0.1','{\"quotationId\":6}',NULL,21,1,NULL,'curl/8.18.0','2026-08-04 21:58:25'),(101,1,'admin','系统管理员',1,'报价单管理',2,'quotation','6','913f6b1791454c22',3,'/sales/quotation/status/6','127.0.0.1','{\"quotationId\":6,\"status\":3}',NULL,23,1,NULL,'curl/8.18.0','2026-08-04 21:58:25'),(102,1,'admin','系统管理员',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,36,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-05 09:44:21'),(103,1,'admin','系统管理员',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,11,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36 Edg/151.0.0.0','2026-08-05 09:48:11'),(104,28,'gongcheng0','工程0',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,7,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-05 09:50:36'),(105,26,'xiaoshou0','销售0',1,'仪表盘',9,NULL,NULL,NULL,0,'/dashboard/my-stats','127.0.0.1','',NULL,6,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-05 09:50:53'),(106,26,'xiaoshou0','销售0',1,'客户管理',3,'custom',NULL,NULL,0,'/sales/customers/7','127.0.0.1','{\"customerIds\":[7]}',NULL,55,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-05 09:51:47'),(107,26,'xiaoshou0','销售0',1,'客户管理',2,'custom','9',NULL,0,'/sales/customers/9','127.0.0.1','{\"customerId\":9,\"dto\":{\"customerId\":9,\"customerCode\":\"CST260730003\",\"customerName\":\"测试客户\",\"customerShortName\":\"TEST\",\"customerType\":1,\"customerStatus\":4,\"customerLevel\":1,\"industryCategory\":\"\",\"customerSource\":1,\"contactPerson\":\"leo\",\"contactPhone\":\"13912345633\",\"contactEmail\":\"13912345632@139.com\",\"fax\":\"\",\"address\":\"{\\\"country\\\":\\\"CN\\\",\\\"province\\\":\\\"gd\\\",\\\"city\\\":\\\"十五\\\",\\\"district\\\":\\\"解决\\\",\\\"street\\\":\\\"详细\\\",\\\"zipCode\\\":\\\"512333\\\"}\",\"creditLimit\":0,\"paymentMethod\":1,\"vip\":false,\"customerScore\":3,\"remark\":\"\"}}',NULL,51,1,NULL,'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36','2026-08-05 09:52:49');
/*!40000 ALTER TABLE `sys_oper_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `role_id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色权限字符串',
  `role_sort` int NOT NULL COMMENT '显示顺序',
  `data_scope` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT '数据范围（1全部 2自定义）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '删除标志',
  `create_by` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '更新者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `menu_check_strictly` tinyint(1) DEFAULT NULL COMMENT '菜单树选择项是否关联显示',
  `dept_check_strictly` tinyint(1) DEFAULT NULL COMMENT '部门树选择项是否关联显示',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `idx_role_key` (`role_key`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1,'超级管理员','admin',1,'1','0','0','1','2026-03-18 15:57:47','1','2026-03-18 15:57:47','拥有所有权限',NULL,NULL),(5,'测试','test',1,'1','0','0','admin','2026-06-08 22:33:05','admin','2026-06-08 22:46:44','测试',1,NULL),(6,'系统用户','system',3,'1','0','0','admin','2026-06-08 23:09:10','admin','2026-06-08 23:09:10','系统用户',1,NULL),(7,'销售人员','sales:staff',0,'1','0','0','admin','2026-06-09 22:47:25','admin','2026-06-09 22:47:25','',1,NULL),(8,'订单审核员','order:review',0,'1','0','0','admin','2026-07-23 18:37:52','admin','2026-07-23 18:37:52','订单审核员',1,NULL),(9,'工程管理','工程管理',0,'1','0','0','admin','2026-07-23 18:43:42','admin','2026-07-23 18:43:42','工程管理',1,NULL),(10,'销售管理all','sales:all',0,'1','0','0','admin','2026-07-31 16:03:23','admin','2026-07-31 16:03:23','销售管理全权限',1,NULL),(11,'仓管','inventory:keeper',5,'1','0','0','admin','2026-08-01 13:57:54','',NULL,'库存管理角色',NULL,NULL),(12,'产品经理','product:all',0,'1','0','0','admin','2026-08-04 11:33:08','admin','2026-08-04 11:33:08','产品经理',1,NULL);
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_menu`
--

DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`,`menu_id`),
  KEY `idx_sys_role_menu_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色和菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_menu`
--

LOCK TABLES `sys_role_menu` WRITE;
/*!40000 ALTER TABLE `sys_role_menu` DISABLE KEYS */;
INSERT INTO `sys_role_menu` VALUES (1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(1,10),(1,11),(1,13),(1,14),(1,15),(1,16),(1,17),(1,18),(1,19),(1,21),(1,22),(1,23),(1,24),(1,25),(1,26),(1,27),(1,28),(1,30),(1,32),(1,33),(1,34),(1,35),(1,36),(1,37),(1,38),(1,39),(1,40),(1,41),(1,42),(1,43),(1,45),(1,46),(1,47),(1,48),(1,49),(1,50),(1,51),(1,52),(1,53),(1,54),(1,55),(1,56),(1,57),(1,58),(1,61),(1,62),(1,63),(1,64),(1,65),(1,67),(1,76),(1,77),(1,90),(1,92),(1,93),(1,94),(1,95),(1,96),(1,204),(1,205),(1,206),(1,207),(1,208),(1,209),(1,210),(1,211),(1,212),(1,213),(1,214),(1,215),(1,216),(1,217),(1,223),(1,224),(1,225),(1,226),(1,227),(1,228),(1,229),(1,230),(1,231),(1,232),(1,233),(1,234),(1,235),(1,236),(1,237),(1,238),(1,240),(2,6),(2,7),(2,8),(2,9),(2,10),(2,11),(2,13),(2,14),(2,15),(2,16),(2,17),(2,18),(2,19),(2,21),(2,22),(2,23),(2,24),(2,25),(2,26),(2,27),(2,28),(2,30),(2,32),(2,33),(2,34),(2,35),(2,36),(2,37),(2,38),(2,39),(2,40),(2,41),(2,42),(2,43),(2,45),(2,46),(2,47),(2,48),(2,49),(2,50),(2,51),(3,52),(3,53),(5,6),(5,7),(5,8),(5,9),(5,10),(5,11),(5,13),(5,14),(5,15),(5,16),(5,17),(5,18),(5,19),(5,21),(5,22),(5,23),(5,24),(5,25),(5,26),(5,27),(5,28),(5,30),(5,32),(5,33),(5,34),(5,35),(5,36),(5,37),(5,38),(5,43),(5,45),(5,46),(5,47),(5,48),(5,49),(5,50),(5,51),(5,62),(5,63),(5,64),(5,65),(5,67),(5,69),(5,70),(5,71),(5,240),(6,1),(6,2),(6,3),(6,4),(6,5),(6,61),(7,13),(7,14),(7,15),(7,16),(7,17),(7,62),(7,63),(7,69),(7,70),(7,71),(7,78),(7,79),(7,81),(7,82),(7,83),(7,84),(7,85),(7,86),(7,87),(7,88),(7,94),(7,95),(7,96),(7,204),(7,215),(7,218),(7,223),(7,224),(7,225),(7,226),(7,227),(7,228),(7,229),(7,230),(7,231),(7,232),(7,233),(7,234),(7,235),(7,236),(7,237),(8,13),(8,15),(8,16),(8,17),(8,36),(8,38),(8,81),(8,88),(8,163),(8,229),(8,233),(9,6),(9,7),(9,8),(9,9),(9,10),(9,11),(9,13),(9,43),(9,45),(9,48),(9,49),(9,50),(9,51),(9,52),(9,64),(9,65),(9,67),(9,76),(9,77),(9,90),(9,92),(9,93),(9,206),(9,207),(9,208),(9,209),(9,210),(9,211),(9,212),(9,213),(9,214),(9,220),(9,221),(9,222),(9,229),(9,234),(9,239),(10,13),(10,14),(10,15),(10,16),(10,17),(10,62),(10,63),(10,69),(10,70),(10,71),(10,78),(10,79),(10,81),(10,82),(10,83),(10,84),(10,85),(10,86),(10,87),(10,88),(10,94),(10,95),(10,96),(10,204),(10,215),(10,218),(10,223),(10,224),(10,225),(10,226),(10,227),(10,228),(10,229),(10,230),(10,231),(10,232),(10,233),(10,234),(10,235),(10,236),(10,237),(11,18),(11,19),(11,21),(11,22),(11,23),(11,24),(11,25),(11,26),(11,27),(11,28),(11,30),(11,32),(11,33),(11,34),(11,35),(11,110),(11,111),(11,112),(11,113),(11,114),(11,115),(11,116),(11,117),(11,118),(11,119),(11,120),(11,121),(11,122),(11,123),(11,124),(11,125),(11,126),(11,127),(11,128),(11,129),(11,130),(11,131),(11,132),(11,133),(11,134),(11,135),(11,136),(11,137),(11,138),(11,139),(11,240),(12,6),(12,7),(12,8),(12,11),(12,64),(12,65),(12,93),(12,97),(12,98),(12,99),(12,100),(12,106),(12,107),(12,108),(12,109);
/*!40000 ALTER TABLE `sys_role_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_task`
--

DROP TABLE IF EXISTS `sys_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_task` (
  `task_id` bigint NOT NULL AUTO_INCREMENT,
  `task_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务编码',
  `task_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务类型：design/review/production/sample',
  `kanban_module` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'office' COMMENT '看板模块: office/emergency/production/dev',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务标题',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '任务描述',
  `biz_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务类型：PRODUCT/ORDER/BOM/ROUTING',
  `biz_id` bigint DEFAULT NULL COMMENT '关联业务ID',
  `assignee_id` bigint DEFAULT NULL COMMENT '负责人ID',
  `assignee_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人姓名',
  `assign_role` bigint DEFAULT NULL COMMENT '按角色分配',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0待处理',
  `priority` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT 'normal' COMMENT '优先级：urgent/high/normal/low',
  `source_event` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源事件',
  `source_id` bigint DEFAULT NULL COMMENT '来源业务ID',
  `result_id` bigint DEFAULT NULL COMMENT '产出ID（BOM/路线/附件）',
  `result_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产出类型：bom/routing/drawing',
  `start_time` datetime DEFAULT NULL,
  `deadline` date DEFAULT NULL,
  `completed_time` datetime DEFAULT NULL,
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_task_code` (`task_code`),
  KEY `idx_biz` (`biz_type`,`biz_id`),
  KEY `idx_assignee` (`assignee_id`,`status`),
  KEY `idx_type_status` (`task_type`,`status`),
  KEY `idx_source` (`source_event`,`source_id`)
) ENGINE=InnoDB AUTO_INCREMENT=586 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_task`
--

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (221,'DEV-1','dev','dev','GitHub 仓库搭建','推送到 GitHub，建 dev / ai/dahuang 分支',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,1,NULL,NULL,'2026-07-18 09:00:00',NULL,NULL,'admin','2026-07-18 09:00:00',NULL,'2026-07-31 15:59:13','原date:07-18 | tags:[\"环境\"]'),(222,'DEV-2','dev','dev','开发环境搭建','Java 21 + Maven + MySQL + Redis + pnpm',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,2,NULL,NULL,'2026-07-18 09:00:00',NULL,NULL,'admin','2026-07-18 09:00:00',NULL,'2026-07-31 17:24:25','原date:07-18 | tags:[\"环境\"]'),(223,'DEV-3','dev','dev','项目跑起来','前端 :5174 / 后端 :8080',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,3,NULL,NULL,'2026-07-18 09:00:00',NULL,NULL,'admin','2026-07-18 09:00:00',NULL,'2026-07-31 15:59:14','原date:07-18 | tags:[\"环境\"]'),(224,'DEV-4','dev','dev','自动推送定时任务','每天 18:30 检查并推送',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,4,NULL,NULL,'2026-07-18 09:00:00',NULL,NULL,'admin','2026-07-18 09:00:00',NULL,'2026-07-31 15:59:16','原date:07-18 | tags:[\"运维\"]'),(225,'DEV-5','dev','dev','销售订单类型优化','API 32处 any → 0',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,5,NULL,NULL,'2026-07-18 09:00:00',NULL,NULL,'admin','2026-07-18 09:00:00',NULL,'2026-07-31 17:27:26','原date:07-18 | tags:[\"前端\", \"类型\"]'),(226,'DEV-6','dev','dev','清理死代码/死路由','TestInventoryController + 路由',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,6,NULL,NULL,'2026-07-18 09:00:00',NULL,NULL,'admin','2026-07-18 09:00:00',NULL,'2026-07-31 15:59:17','原date:07-18 | tags:[\"重构\"]'),(227,'DEV-7','dev','dev','分支合并','ai/dahuang → dev',NULL,NULL,NULL,NULL,NULL,10,'low',NULL,7,NULL,NULL,'2026-07-18 09:00:00',NULL,NULL,'admin','2026-07-18 09:00:00',NULL,'2026-07-31 17:29:06','原date:07-18 | tags:[\"Git\"]'),(228,'DEV-8','dev','dev','安全加固','密码/JWT 密钥环境变量化',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,8,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:27:27','原date:07-20 | tags:[\"安全\"]'),(229,'DEV-9','dev','dev','单元测试（15个）','销售/库存/采购/产品模块',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,9,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:27:29','原date:07-20 | tags:[\"测试\"]'),(230,'DEV-10','dev','dev','数据库导入','66 张表恢复',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,10,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 15:59:26','原date:07-20 | tags:[\"数据\"]'),(231,'DEV-11','dev','dev','API any 全面清理','12 文件 51 处 any → 0',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,11,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 15:59:29','原date:07-20 | tags:[\"前端\", \"类型\"]'),(232,'DEV-12','dev','dev','系统权限初始化','3 用户 / 4 角色 / 65 菜单',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,12,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 15:59:31','原date:07-20 | tags:[\"权限\"]'),(233,'DEV-13','dev','dev','仪表盘重做','真实数据指标卡 + 库存预警',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,13,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 15:59:33','原date:07-20 | tags:[\"前端\"]'),(234,'DEV-14','dev','dev','前端 UI 翻新','白底侧边栏 + 圆角卡片 + 骨架屏',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,14,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 15:59:34','原date:07-20 | tags:[\"前端\"]'),(235,'DEV-15','dev','dev','修复 TS 错误 + 删演示模块','mock/store/views + examples',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,15,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:33:06','原date:07-20 | tags:[\"前端\", \"类型\"]'),(236,'DEV-16','dev','dev','文档精简','6000行→1168行',NULL,NULL,NULL,NULL,NULL,10,'low',NULL,16,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 15:59:36','原date:07-20 | tags:[\"文档\"]'),(237,'DEV-17','dev','dev','生产追溯','后端+前端+菜单已就绪',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,17,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 15:59:39','原date:07-20 | tags:[\"生产\"]'),(238,'DEV-18','dev','dev','日志管理修复','DialogForm 组件补齐',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,18,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:29:59','原date:07-20 | tags:[\"前端\"]'),(239,'DEV-19','dev','dev','质量检验','后端+API+菜单',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,19,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:27:38','原date:07-20 | tags:[\"生产\"]'),(240,'DEV-20','dev','dev','设备管理','后端+API+菜单',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,20,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:33:12','原date:07-20 | tags:[\"生产\"]'),(241,'DEV-21','dev','dev','生产看板 API','后端统计接口',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,21,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:27:39','原date:07-20 | tags:[\"生产\"]'),(242,'DEV-22','dev','dev','生产执行前端','views/production/execution/index.vue',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,22,NULL,NULL,'2026-07-20 09:00:00',NULL,'2026-07-31 21:49:54','admin','2026-07-20 09:00:00',NULL,'2026-07-31 21:49:54','原date:07-20 | tags:[\"生产\", \"前端\"]'),(243,'DEV-23','dev','dev','质量检验前端','views/production/quality/index.vue',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,23,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:27:41','原date:07-20 | tags:[\"生产\", \"前端\"]'),(244,'DEV-24','dev','dev','设备管理前端','views/production/equipment/index.vue',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,24,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:27:43','原date:07-20 | tags:[\"生产\", \"前端\"]'),(245,'DEV-25','dev','dev','排程管理','菜单+路由已就绪',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,25,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:37:54','原date:07-20 | tags:[\"生产\"]'),(246,'DEV-26','dev','dev','采购-入库联动','createFromPurchase 已实现',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,26,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:37:57','原date:07-20 | tags:[\"采购\", \"库存\"]'),(247,'DEV-27','dev','dev','库存预警','AlertController + AlertService 已实现',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,27,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:27:45','原date:07-20 | tags:[\"库存\"]'),(248,'DEV-28','dev','dev','库存周转率','GET /turnover endpoint 已实现',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,28,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:27:47','原date:07-20 | tags:[\"库存\"]'),(249,'DEV-29','dev','dev','工程管理','后端包 + 前端 views 已就绪',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,29,NULL,NULL,'2026-07-20 09:00:00',NULL,NULL,'admin','2026-07-20 09:00:00',NULL,'2026-07-31 17:37:58','原date:07-20 | tags:[\"工程\"]'),(250,'DEV-30','dev','dev','生产报表','产量/效率/质量报表（后端已就绪）',NULL,NULL,NULL,NULL,NULL,3,'high',NULL,30,NULL,NULL,'2026-07-21 09:00:00',NULL,NULL,'admin','2026-07-21 09:00:00',NULL,'2026-08-03 11:46:12','原date:07-21 | tags:[\"生产\"]'),(251,'DEV-31','dev','dev','成本核算','生产成本计算（前后端已就绪）',NULL,NULL,NULL,NULL,NULL,3,'high',NULL,31,NULL,NULL,'2026-07-21 09:00:00',NULL,NULL,'admin','2026-07-21 09:00:00',NULL,'2026-08-03 11:46:14','原date:07-21 | tags:[\"生产\"]'),(252,'DEV-32','dev','dev','看板前端对接','骨架页对接后端 API（board-real.ts 自动降级）',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,32,NULL,NULL,'2026-07-21 09:00:00',NULL,NULL,'admin','2026-07-21 09:00:00',NULL,'2026-07-31 17:38:15','原date:07-21 | tags:[\"生产\"]'),(253,'DEV-33','dev','dev','消息通知','站内信、邮件通知（前后端已就绪）',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,33,NULL,NULL,'2026-07-21 09:00:00',NULL,NULL,'admin','2026-07-21 09:00:00',NULL,'2026-07-31 17:30:01','原date:07-21 | tags:[\"通知\"]'),(254,'DEV-34','dev','dev','车间工单看板','方案已出，待对接前端',NULL,NULL,NULL,NULL,NULL,4,'high',NULL,34,NULL,NULL,'2026-07-21 09:00:00',NULL,NULL,'admin','2026-07-21 09:00:00',NULL,'2026-07-31 17:30:05','原date:07-21 | tags:[\"生产\"]'),(255,'DEV-35','dev','dev','工单下发与排程','计划→工单转换，生成工序记录（后端已完成）',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,35,NULL,NULL,'2026-07-21 09:00:00',NULL,'2026-07-31 21:49:54','admin','2026-07-21 09:00:00',NULL,'2026-07-31 21:49:54','原date:07-21 | tags:[\"生产\"]'),(256,'DEV-36','dev','dev','样品单阶段一：基础准备','建 sys_attachment 表，sales_order_product 加字段',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,36,NULL,NULL,'2026-07-22 09:00:00',NULL,NULL,'admin','2026-07-22 09:00:00',NULL,'2026-07-31 18:09:51','原date:07-22 | tags:[\"样品单\"]'),(257,'DEV-37','dev','dev','样品单阶段二：后端逻辑改造','校验区分样品单vs标准单，附件API',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,37,NULL,NULL,'2026-07-22 09:00:00',NULL,NULL,'admin','2026-07-22 09:00:00',NULL,'2026-07-31 18:09:52','原date:07-22 | tags:[\"样品单\"]'),(258,'DEV-38','dev','dev','样品单阶段三：前端改造','产品编码下拉→可自定义，附件上传+预览',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,38,NULL,NULL,'2026-07-22 09:00:00',NULL,NULL,'admin','2026-07-22 09:00:00',NULL,'2026-07-31 18:09:54','原date:07-22 | tags:[\"样品单\"]'),(259,'DEV-39','dev','dev','路由标签页卡顿/刷新重构','routeHelper补name + permissionStore + layout修复',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,39,NULL,NULL,'2026-07-22 09:00:00',NULL,NULL,'admin','2026-07-22 09:00:00',NULL,'2026-07-31 18:09:57','原date:07-22 | tags:[\"前端\"]'),(260,'DEV-40','dev','dev','环境标识徽章','Navbar右上角显示当前git分支名',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,40,NULL,NULL,'2026-07-22 09:00:00',NULL,NULL,'admin','2026-07-22 09:00:00',NULL,'2026-07-31 17:56:08','原date:07-22 | tags:[\"前端\"]'),(261,'DEV-41','dev','dev','动态路由加载修复','路由未及时注入，需重新登录才生效',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,41,NULL,NULL,'2026-07-22 09:00:00',NULL,NULL,'admin','2026-07-22 09:00:00',NULL,'2026-07-31 17:56:16','原date:07-22 | tags:[\"前端\"]'),(262,'DEV-42','dev','dev','消息提醒铃铛','Navbar头像左侧加消息铃铛+未读红点',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,42,NULL,NULL,'2026-07-22 09:00:00',NULL,NULL,'admin','2026-07-22 09:00:00',NULL,'2026-07-31 17:56:17','原date:07-22 | tags:[\"前端\"]'),(263,'DEV-43','dev','dev','首次菜单点击卡顿','首次点击菜单页面卡死1-3秒',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,43,NULL,NULL,'2026-07-22 09:00:00',NULL,NULL,'admin','2026-07-22 09:00:00',NULL,'2026-07-31 18:09:58','原date:07-22 | tags:[\"前端\"]'),(264,'DEV-44','dev','dev','客户管理状态Bug','CustomerConverter忽略customerStatus字段',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,44,NULL,NULL,'2026-07-22 09:00:00',NULL,NULL,'admin','2026-07-22 09:00:00',NULL,'2026-07-31 15:59:46','原date:07-22 | tags:[\"后端\"]'),(265,'DEV-45','dev','dev','客户管理路由404','Vue Router: No match found',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,45,NULL,NULL,'2026-07-22 09:00:00',NULL,NULL,'admin','2026-07-22 09:00:00',NULL,'2026-07-31 15:59:45','原date:07-22 | tags:[\"前端\"]'),(266,'DEV-46','dev','dev','报价管理API报错','获取报价单列表 Failed to convert',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,46,NULL,NULL,'2026-07-22 09:00:00',NULL,NULL,'admin','2026-07-22 09:00:00',NULL,'2026-07-31 15:59:47','原date:07-22 | tags:[\"后端\"]'),(267,'DEV-47','dev','dev','【工程】阶段一：菜单树重组','新增工程管理顶级菜单，BOM/工艺/图纸/标准工序迁移',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,47,NULL,NULL,'2026-07-24 09:00:00',NULL,NULL,'admin','2026-07-24 09:00:00',NULL,'2026-07-31 17:56:21','原date:07-24 | tags:[\"工程\"]'),(268,'DEV-48','dev','dev','全模块权限标准化','view/list/detail 统一标准CRUD权限',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,48,NULL,NULL,'2026-07-24 09:00:00',NULL,NULL,'admin','2026-07-24 09:00:00',NULL,'2026-07-31 17:56:26','原date:07-24 | tags:[\"权限\"]'),(269,'DEV-49','dev','dev','非Sales模块权限普查','逐一校验 inventory/product/production/purchase/system',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,49,NULL,NULL,'2026-07-24 09:00:00',NULL,NULL,'admin','2026-07-24 09:00:00',NULL,'2026-07-31 17:56:24','原date:07-24 | tags:[\"权限\"]'),(270,'DEV-50','dev','dev','提交审核校验责任人','非超级管理员只能提交本人负责的订单',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,50,NULL,NULL,'2026-07-24 09:00:00',NULL,NULL,'admin','2026-07-24 09:00:00',NULL,'2026-07-31 17:56:33','原date:07-24 | tags:[\"权限\"]'),(271,'DEV-51','dev','dev','sales:log 独立授权','将SalesLogController权限标准化',NULL,NULL,NULL,NULL,NULL,4,'normal',NULL,51,NULL,NULL,'2026-07-24 09:00:00',NULL,NULL,'admin','2026-07-24 09:00:00',NULL,'2026-07-31 17:36:44','原date:07-24 | tags:[\"权限\"]'),(272,'DEV-52','dev','dev','前端 v-permission 梳理','全局搜索 v-permission/hasPermi 使用情况',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,52,NULL,NULL,'2026-07-24 09:00:00',NULL,NULL,'admin','2026-07-24 09:00:00',NULL,'2026-07-31 17:56:28','原date:07-24 | tags:[\"权限\"]'),(273,'DEV-53','dev','dev','模块结构优化','删重复菜单、隐藏编辑页、重排序、更新文档',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,53,NULL,NULL,'2026-07-24 09:00:00',NULL,NULL,'admin','2026-07-24 09:00:00',NULL,'2026-07-31 17:56:29','原date:07-24 | tags:[\"重构\"]'),(274,'DEV-54','dev','dev','【P0】修复前端404 API','StandardProcessController新增/enabled接口，前端3处路径单复数修正',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,54,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 18:10:03','原date:07-25 | tags:[\"Bug\"]'),(275,'DEV-55','dev','dev','【P0】出入库核心逻辑','InboundServiceImpl+OutboundServiceImpl 8处TODO实现',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,55,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 18:10:05','原date:07-25 | tags:[\"库存\"]'),(276,'DEV-56','dev','dev','【P0】status参数转换','MaterialEnum/UserEnum 值对齐后端StatusEnum',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,56,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 18:10:06','原date:07-25 | tags:[\"Bug\"]'),(277,'DEV-57','dev','dev','【P1】库存预警推送','AlertServiceImpl补齐page()分页+事件联动',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,57,NULL,NULL,'2026-07-25 09:00:00',NULL,'2026-07-31 21:49:54','admin','2026-07-25 09:00:00',NULL,'2026-07-31 21:49:54','原date:07-25 | tags:[\"库存\"]'),(278,'DEV-58','dev','dev','【P2】TS类型错误修复','50+个TS错误，27个文件',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,58,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 17:36:51','原date:07-25 | tags:[\"类型\"]'),(279,'DEV-59','dev','dev','【P2】报表对接真实数据','后端新增统计接口+前端3个报表从mock切真实API',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,59,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 17:36:55','原date:07-25 | tags:[\"报表\"]'),(280,'DEV-60','dev','dev','【P2】生产执行vs操作分离优化','execution页加\'我的任务\'标签',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,60,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-08-01 20:43:32','原date:07-25 | tags:[\"生产\"]'),(281,'DEV-61','dev','dev','销售/采购/质量报表页面','3个Vue页面+数据库菜单+权限',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,61,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 17:37:03','原date:07-25 | tags:[\"报表\"]'),(282,'DEV-62','dev','dev','任务清单清理','全面审计，31个待办→17个真实待办',NULL,NULL,NULL,NULL,NULL,10,'low',NULL,62,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 17:56:37','原date:07-25 | tags:[\"文档\"]'),(283,'DEV-63','dev','dev','修复 Swagger v3/api-docs 返回0条路径','Knife4j配置问题',NULL,NULL,NULL,NULL,NULL,3,'high',NULL,63,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-07-31 15:50:02','tags:[\"文档\"]'),(284,'DEV-64','dev','dev','清理多余权限记录','56条权限在后端找不到对应@SaCheckPermission',NULL,NULL,NULL,NULL,NULL,10,'low',NULL,64,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 17:56:39','原date:07-25 | tags:[\"权限\"]'),(285,'DEV-65','dev','dev','补齐遗漏唯一权限','后端engineering:*权限和库存部分权限未同步到sys_menu',NULL,NULL,NULL,NULL,NULL,10,'low',NULL,65,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 17:37:08','原date:07-25 | tags:[\"权限\"]'),(286,'DEV-67','dev','dev','国际站门户开发','portal表已存在',NULL,NULL,NULL,NULL,NULL,3,'low',NULL,67,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-07-31 15:50:29','tags:[\"门户\"]'),(287,'DEV-68','dev','dev','了解 Metersphere','学习开源持续测试平台',NULL,NULL,NULL,NULL,NULL,3,'low',NULL,68,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-07-31 15:50:07','tags:[\"学习\"]'),(288,'DEV-69','dev','dev','生产领料自动扣库存','createFromProduction(outbound) 是 TODO 空壳',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,69,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-08-01 20:43:52','原date:07-25 | tags:[\"库存\", \"生产\", \"后端\"]'),(289,'DEV-70','dev','dev','完工自动加成品库存','createFromProduction(inbound) 是 TODO 空壳',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,70,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 18:34:02','原date:07-25 | tags:[\"库存\", \"生产\", \"后端\"]'),(290,'DEV-71','dev','dev','销售发货扣库存','createFromSales(outbound) 是 TODO 空壳',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,71,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-08-01 20:43:12','原date:07-25 | tags:[\"库存\", \"销售\", \"后端\"]'),(291,'DEV-72','dev','dev','填充演示数据','product_bom 0行、product_routing 0行',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,72,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-07-31 15:59:07','tags:[\"数据\"]'),(292,'DEV-73','dev','dev','清理死代码：未使用的私有方法','Inventory*ServiceImpl 中大量 private static get*Name 定义但无调用',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,73,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 17:37:13','原date:07-25 | tags:[\"后端\", \"重构\"]'),(293,'DEV-74','dev','dev','优化 VO 属性拷贝','setter逐行赋值→BeanUtils.copyProperties/MapStruct',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,74,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 17:37:14','原date:07-25 | tags:[\"后端\", \"重构\"]'),(294,'DEV-76','dev','dev','国际化','多语言门户',NULL,NULL,NULL,NULL,NULL,3,'low',NULL,76,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-07-31 15:51:44','tags:[\"门户\"]'),(295,'DEV-77','dev','dev','生产执行 vs 操作合并评估','execution(48)和production-operation(51)界限模糊',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,77,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-07-31 15:59:09','tags:[\"重构\", \"生产\"]'),(296,'DEV-78','dev','dev','🌐 企业官网：核心生成器','Node.js+EJS+Tailwind，20页+sitemap+后台管理',NULL,NULL,NULL,NULL,NULL,3,'high',NULL,78,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 18:34:04','原date:07-25 | tags:[\"官网\"]'),(297,'DEV-82','dev','dev','P0 — LoginUser ClassCastException','DevTools 双加载器冲突，spring-devtools.properties 排除数据类',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,82,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 18:10:15','原date:07-25 | tags:[\"Bug\", \"后端\", \"devtools\"]'),(298,'DEV-83','dev','dev','【Bug】物料选项SQL语法错误','GET /inventory/material/options 返回SQL语法错误，检查Mapper XML',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,83,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 12:07:03','原date:07-26 | tags:[\"Bug\", \"后端\", \"库存\", \"已修复\"]'),(299,'DEV-84','dev','dev','【Bug】生产工单分页MyBatis异常','GET /production/order/page MyBatis执行异常',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,84,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 20:43:38','原date:07-26 | tags:[\"Bug\", \"后端\", \"生产\", \"已修复\"]'),(300,'DEV-85','dev','dev','【Bug】生产报表查询异常','GET /production/report/output MyBatis执行异常',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,85,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 20:43:37','原date:07-26 | tags:[\"Bug\", \"后端\", \"生产\", \"已修复\"]'),(301,'DEV-86','dev','dev','【Bug】客户统计500错误','GET /sales/customer/statistics 系统繁忙',NULL,NULL,NULL,NULL,NULL,10,'low',NULL,86,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-07-31 18:17:16','原date:07-28 | tags:[\"Bug\", \"后端\", \"销售\"]'),(302,'DEV-87','dev','dev','【Bug】预警分页500错误','GET /inventory/alert/page 系统繁忙，刚修复的AlertServiceImpl可能有问题',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,87,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 20:43:51','原date:07-26 | tags:[\"Bug\", \"后端\", \"库存\", \"已修复\"]'),(303,'DEV-88','dev','dev','【Bug】库存报表/周转率500错误','GET /inventory/report/stock-summary 和 /turnover 均报错',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,88,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-08-01 20:43:31','原date:07-25 | tags:[\"Bug\", \"后端\", \"库存\"]'),(304,'DEV-90','dev','dev','【Bug】工程管理config/film 500错误','GET /engineering/config 和 /engineering/film 系统繁忙',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,90,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 18:33:51','原date:07-25 | tags:[\"Bug\", \"后端\", \"工程\"]'),(305,'DEV-91','dev','dev','【Bug】供应商统计未实现','GET /purchase/supplier/statistics 返回\'统计功能暂未实现\'',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,91,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 20:43:35','原date:07-26 | tags:[\"功能\", \"后端\", \"采购\", \"已修复\"]'),(306,'DEV-92','dev','dev','【Bug】Swagger v3/api-docs返回500','Springdoc 2.3.0不兼容Spring Boot 3.4.12，已升级Springdoc到2.6.0但需mvn package验证',NULL,NULL,NULL,NULL,NULL,3,'high',NULL,92,NULL,NULL,'2026-07-25 09:00:00',NULL,NULL,'admin','2026-07-25 09:00:00',NULL,'2026-07-31 18:34:08','原date:07-25 | tags:[\"Bug\", \"后端\", \"文档\"]'),(307,'DEV-93','dev','dev','产品审批→自动派工程任务+通知工程师','产品APPROVED后自动创建工程任务给工程师做BOM/路线+sys_notification',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,93,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 20:43:35','原date:07-26 | tags:[\"功能\", \"联动\"]'),(308,'DEV-94','dev','dev','BOM提交审核加PENDING状态','当前DRAFT直通APPROVED，需加submit→PENDING，跟路线对齐',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,94,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-07-31 17:35:18','原date:07-26 | tags:[\"功能\"]'),(309,'DEV-95','dev','dev','产品发布前校验BOM和路线','releaseProduct检查BOM/路线是否已配置且审批通过',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,95,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-07-31 17:35:21','原date:07-26 | tags:[\"功能\"]'),(310,'DEV-96','dev','dev','所有审批事件联动','产品/BOM/路线/订单审核通过时→自动派任务+消息通知',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,96,NULL,NULL,'2026-07-26 09:00:00',NULL,'2026-08-04 21:50:51','admin','2026-07-26 09:00:00',NULL,'2026-08-04 21:50:51','原date:07-26 | tags:[\"功能\", \"联动\"]'),(311,'DEV-97','dev','dev','生产工单记录BOM/路线ID','避免换BOM/路线后历史工单追溯不清',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,97,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:17:13','原date:07-26 | tags:[\"功能\"]'),(312,'DEV-98','dev','dev','提交生产前校验','BOM已审批+路线已审批+物料库存足够→通知车间+看板创任务',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,98,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 20:43:34','原date:07-26 | tags:[\"功能\"]'),(313,'DEV-99','dev','dev','销售订单选产品只显示RELEASED','未发布产品不可选',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,99,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 20:43:33','原date:07-26 | tags:[\"功能\"]'),(314,'DEV-100','dev','dev','客户确认流程完善','方案一+四：确认弹窗(人/方式/时间/备注)→CONFIRMED+PDF确认书+附件',NULL,NULL,NULL,NULL,NULL,3,'urgent',NULL,100,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-05 09:46:47','原date:07-26 | tags:[\"功能\"]'),(315,'DEV-101','dev','dev','提交生产→消息通知+任务派送','通知车间/生产计划+看板创建生产任务',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,101,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:17:04','原date:07-26 | tags:[\"功能\", \"联动\"]'),(316,'DEV-102','dev','dev','通知模块：sys_notification 加 event_code 字段','新增 event_code varchar(50) 字段，关联触发事件',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,102,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:17:02','原date:07-26 | tags:[\"通知\", \"后端\"]'),(317,'DEV-182','dev','dev','通知模块：初始化事件配置数据','填充 sys_event_config 表：产品审批通过/BOM提交/BOM审批/路线提交/路线审批/订单提交/订单审批等事件定义',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,182,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:17:00','原date:07-26 | tags:[\"通知\", \"数据\"]'),(318,'DEV-103','dev','dev','通知模块：初始化事件→通知映射数据','填充 sys_event_notification 表：每个事件配置通知对象(创建人/审核人/角色等)',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,103,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:55','原date:07-26 | tags:[\"通知\", \"数据\"]'),(319,'DEV-104','dev','dev','通知模块：初始化通知模板数据','填充 sys_notification_template 表：每个事件配置标题模板和内容模板（支持变量替换）',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,104,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:51','原date:07-26 | tags:[\"通知\", \"数据\"]'),(320,'DEV-105','dev','dev','通知模块：实现 EventBus.fire() 核心逻辑','①查事件定义→②查通知映射→③查角色用户→④查模板生成内容→⑤批量写入 sys_notification',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,105,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:48','原date:07-26 | tags:[\"通知\", \"后端\"]'),(321,'DEV-106','dev','dev','通知模块：支持按角色通知','notify_target=ROLE 时，查 sys_user_role + sys_role 找到所有该角色的用户',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,106,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:45','原date:07-26 | tags:[\"通知\", \"后端\"]'),(322,'DEV-107','dev','dev','通知模块：模板变量替换','title_pattern/content_pattern 中 {productName}/{operator}/{time} 等变量自动替换',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,107,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:53','原date:07-26 | tags:[\"通知\", \"后端\"]'),(323,'DEV-108','dev','dev','通知模块：业务代码改抛事件','手动调 createNotification 的地方逐步改为 eventBus.fire()：产品审批/BOM审批/路线审批/订单审批等',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,108,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:35','原date:07-26 | tags:[\"通知\", \"后端\"]'),(324,'DEV-109','dev','dev','通知模块：通知偏好表(可选)','sys_notification_preference(user_id, event_code, is_muted)，用户可静默某类通知',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,109,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:37','原date:07-26 | tags:[\"通知\", \"后端\"]'),(325,'DEV-110','dev','dev','日志模块：sys_oper_log 加 biz_type/biz_id/trace_id 字段','新增biz_type(varchar20)/biz_id(varchar64)/trace_id(varchar50)/detail(json)，支持按业务单据查询日志',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,110,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:30','原date:07-26 | tags:[\"日志\", \"后端\"]'),(326,'DEV-111','dev','dev','日志模块：sales_log 合并到 sys_oper_log','统一操作日志入口，停止向 sales_log 写数据，迁移历史数据或保留sales_log为视图',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,111,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:28','原date:07-26 | tags:[\"日志\", \"后端\"]'),(327,'DEV-112','dev','dev','日志模块：sys_oper_log 按月分区/定期归档','按月分区表或定期归档旧数据，防止单表过大影响查询性能',NULL,NULL,NULL,NULL,NULL,3,'urgent',NULL,112,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 18:01:19','原date:07-26 | tags:[\"日志\", \"运维\"]'),(328,'DEV-113','dev','dev','日志模块：sys_oper_log 加按 biz_id 索引','新增 idx_biz(biz_type, biz_id, create_time) 索引，支持按业务单据快速查询操作记录',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,113,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:21','原date:07-26 | tags:[\"日志\", \"后端\"]'),(329,'DEV-114','dev','dev','任务模块：新建统一 sys_task 表','合并 engineering_design_task + kanban_task + engineering_base 为一张 sys_task，含task_type/biz_type/biz_id/assignee/status/priority/source_event等字段',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,114,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:15','原date:07-26 | tags:[\"任务\", \"后端\"]'),(330,'DEV-115','dev','dev','任务模块：迁移 engineering_design_task 数据到 sys_task','将现 engineering_design_task 的数据按对应字段迁移到 sys_task，task_type=\'design\'',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,115,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 10:49:34','原date:07-29 | tags:[\"任务\", \"后端\"]'),(331,'DEV-116','dev','dev','任务模块：迁移 kanban_task 数据到 sys_task','将现 kanban_task 的数据迁移到 sys_task，kanban_type→task_type，status→column_id',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,116,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 10:49:36','原date:07-29 | tags:[\"任务\", \"后端\"]'),(332,'DEV-117','dev','dev','任务模块：废弃旧表 engineering_design_task/kanban_task/engineering_base','数据迁移完成并验证后，标记旧表废弃或删除',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,117,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 10:49:39','原date:07-29 | tags:[\"任务\", \"后端\"]'),(333,'DEV-118','dev','dev','任务模块：EventBus 联动创建任务','sys_event_kanban 配置不变，事件触发时自动创建 sys_task 记录',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,118,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:10','原date:07-26 | tags:[\"任务\", \"后端\"]'),(334,'DEV-120','dev','dev','EventBus：定义 @Event 注解 + AOP 切面','定义@Event注解，实现AOP切面拦截，方法成功后触发事件，支持SpEL解析bizId，事务提交后执行',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,120,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:12','原date:07-26 | tags:[\"EventBus\", \"后端\"]'),(335,'DEV-121','dev','dev','EventBus：实现核心 EventBus + 三个 Handler','EventBus.fire() + LogHandler(写日志)/TaskHandler(写任务)/NotifHandler(写通知)，Handler通过配置驱动',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,121,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:13','原date:07-26 | tags:[\"EventBus\", \"后端\"]'),(336,'DEV-122','dev','dev','EventBus：初始化事件配置数据','填充sys_event_config + sys_event_kanban + sys_event_notification + sys_notification_template 配置数据',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,122,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:23','原date:07-26 | tags:[\"EventBus\", \"数据\"]'),(337,'DEV-123','dev','dev','EventBus：业务代码逐步加 @Event 注解','在关键业务流程加@Event注解：产品审批/BOM审批/路线审批/订单审批/提交生产等',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,123,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 11:16:18','原date:07-26 | tags:[\"EventBus\", \"后端\"]'),(338,'DEV-124','dev','dev','移动端适配','',NULL,NULL,NULL,NULL,NULL,3,'normal',NULL,124,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-07-31 15:51:44','tags:[\"前端\"]'),(339,'DEV-125','dev','dev','🌐 企业官网：AI客服真后端','',NULL,NULL,NULL,NULL,NULL,3,'normal',NULL,125,NULL,NULL,'2026-07-28 09:00:00',NULL,'2026-07-28 09:00:00','admin','2026-07-28 09:00:00',NULL,'2026-07-31 17:49:55','原date:07-28 | tags:[\"官网\"]'),(340,'DEV-126','dev','dev','🌐 企业官网：favicon+下载文件+替换图片','',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,126,NULL,NULL,'2026-07-28 09:00:00',NULL,'2026-07-31 18:54:05','admin','2026-07-28 09:00:00',NULL,'2026-07-31 18:54:05','原date:07-28 | tags:[\"官网\"]'),(341,'DEV-127','dev','dev','🌐 企业官网：站内搜索+分页','',NULL,NULL,NULL,NULL,NULL,3,'low',NULL,127,NULL,NULL,'2026-07-28 09:00:00',NULL,'2026-07-28 09:00:00','admin','2026-07-28 09:00:00',NULL,'2026-07-31 17:50:03','原date:07-28 | tags:[\"官网\"]'),(342,'DEV-128','dev','dev','日志模块：@Log 注解支持 biz_id/biz_type（SpEL提取）','当前@Log注解不填biz_id/biz_type，加SpEL参数让AOP自动从方法参数/返回值提取并写入sys_oper_log',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,128,NULL,NULL,'2026-07-26 09:00:00',NULL,NULL,'admin','2026-07-26 09:00:00',NULL,'2026-08-01 12:07:01','原date:07-26 | tags:[\"日志\", \"后端\"]'),(343,'DEV-129','dev','dev','【销售】订单客户确认流程完善','CONFIRMED状态+确认记录(人/方式/时间)+PDF确认书+附件',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,129,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-08-01 20:43:42','原date:07-28 | tags:[\"销售\"]'),(344,'DEV-130','dev','dev','【产品】发布前校验BOM和路线已审批','产品RELEASED前校验BOM和路线是否已审批通过',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,130,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-08-01 20:43:42','原date:07-28 | tags:[\"产品\"]'),(345,'DEV-131','dev','dev','【全局】审批事件联动','T1+T4：产品/BOM/路线/订单审核通过→自动派工程任务+通知相关人',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,131,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-07-31 18:17:27','原date:07-28 | tags:[\"系统\"]'),(346,'DEV-132','dev','dev','【全局】消息通知接入','T3：所有审核事件触发sys_notification消息通知',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,132,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-07-31 18:17:25','原date:07-28 | tags:[\"系统\"]'),(347,'DEV-133','dev','dev','【BOM】加PENDING中间状态','T2：DRAFT→PENDING→APPROVED，跟路线状态机对齐',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,133,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-08-01 20:43:56','原date:07-28 | tags:[\"工程\"]'),(348,'DEV-134','dev','dev','【生产】工单记录BOM/路线ID快照','创建工单时记录当时使用的BOM_ID和ROUTE_ID，避免产品换BOM后历史追溯不清',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,134,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-08-01 20:43:41','原date:07-28 | tags:[\"生产\"]'),(349,'DEV-135','dev','dev','【生产】提交生产前校验+BOM警告','校验BOM已审批+路线已审批+库存足够，提交后通知车间+看板创建任务',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,135,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-08-01 20:43:40','原date:07-28 | tags:[\"生产\"]'),(350,'DEV-136','dev','dev','【销售】订单创建检查产品状态=RELEASED','未发布产品在订单中不可选，非仅检查已发布产品是否存在',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,136,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-08-01 20:43:40','原date:07-28 | tags:[\"销售\"]'),(351,'DEV-137','dev','dev','【库存】三个TODO空壳补齐','生产领料/完工入库/销售发货 三个库存功能完善',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,137,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-07-31 18:17:36','原date:07-28 | tags:[\"库存\"]'),(352,'DEV-138','dev','dev','【采购】采购退货功能','退货后库存扣减+退货记录写入',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,138,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-08-01 20:43:39','原date:07-28 | tags:[\"采购\"]'),(353,'DEV-139','dev','dev','【销售】报价→订单转换流程','客户确认报价后自动/手动转为销售订单',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,139,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-08-01 12:07:05','原date:07-28 | tags:[\"销售\"]'),(354,'DEV-140','dev','dev','填充演示数据','product_bom/product_routing等表目前0行，补充演示数据',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,140,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-07-31 15:59:12','tags:[\"数据\"]'),(355,'DEV-141','dev','dev','Swagger v3/api-docs修复','#63返回0条路径 + #92返回500，springdoc版本兼容问题',NULL,NULL,NULL,NULL,NULL,3,'high',NULL,141,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-07-31 15:51:44','tags:[\"后端\"]'),(356,'DEV-142','dev','dev','@Log注解支持biz_id/biz_type(SpEL)','#128 日志模块：从参数/返回值提取业务ID和类型',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,142,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-07-31 17:37:48','tags:[\"后端\"]'),(357,'DEV-143','dev','dev','【系统】字典管理优化(Redis缓存+分组+导入导出)','当前每次查数据库，改为Redis缓存，按模块分组，支持批量导入导出',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,143,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-08-01 20:43:38','原date:07-28 | tags:[\"系统\"]'),(358,'DEV-144','dev','dev','【系统】新建系统配置表(sys_config)','替代application-dev.yml硬编码，统一管理业务参数/邮箱/SMS等',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,144,NULL,NULL,'2026-07-28 09:00:00',NULL,NULL,'admin','2026-07-28 09:00:00',NULL,'2026-08-01 16:38:11','原date:07-28 | tags:[\"系统\"]'),(359,'DEV-145','dev','dev','【系统】数据库表结构审计','分析所有表，清理未使用的表和字段，解决表冲突和设计过度问题',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,145,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 11:12:22','原date:07-29 | tags:[\"系统\", \"数据库\"]'),(360,'DEV-146','dev','dev','【测试】API测试修复(6个失败接口)','API测试全部21个失败: 阶段一6个+阶段四~八6个+全覆盖6个(含film/page/turnover/cost/kanban/supplier/attachment)',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,146,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 14:43:09','原date:07-29 | tags:[\"测试\", \"API\"]'),(361,'DEV-147','dev','dev','测试计划重写 v2.0','按真实薄膜开关业务流重构 tests/test-plan-table.md：新增询价管理(P1)+样品管理(P3)，14个Phase/77条用例，每表加代码状态字段，附录改动清单+3个端到端场景',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,147,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 11:12:26','原date:07-29 | tags:[\"测试\", \"文档\"]'),(362,'DEV-148','dev','dev','测试工作台同步更新 v2.0','docs/test/index.html 同步新版测试计划：14个Phase模块、77条用例、侧边栏代码状态标识(✅🆕⚠️)、用例卡片标需开发标记',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,148,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 11:12:29','原date:07-29 | tags:[\"测试\", \"文档\"]'),(363,'DEV-149','dev','dev','【P0】询价单模块','新模块：客户询价CRUD+文件上传+转报价。TC1-4',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,149,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 11:12:41','原date:07-29 | tags:[\"销售\", \"新模块\"]'),(364,'DEV-150','dev','dev','【P0】样品单独立状态机','新建SampleOrderStatus枚举：CREATED→PENDING_REVIEW→ENGINEERING→SAMPLE_READY→SAMPLE_SENT→CONFIRMED→TRANSFERRED→CLOSED。TC14-24',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,150,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 14:43:14','原date:07-29 | tags:[\"销售\", \"样品\"]'),(365,'DEV-151','dev','dev','【P0】样品单前端','样品单列表页/详情页/操作按钮，适配独立状态机。TC14-24',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,151,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 14:43:19','原date:07-29 | tags:[\"销售\", \"样品\", \"前端\"]'),(366,'DEV-152','dev','dev','【P0】样品转量产服务','样品确认后→一键转标准订单，复制客户/产品/规格，追溯关联。TC24-25',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,152,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 20:43:50','原date:07-29 | tags:[\"销售\", \"后端\"]'),(367,'DEV-153','dev','dev','【P1】工程介入UI','样品单详情页加工程区（图纸上传/工艺参数/样品完成标记）。TC17-19',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,153,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 20:43:49','原date:07-29 | tags:[\"工程\", \"样品\", \"前端\"]'),(368,'DEV-154','dev','dev','【P1】领料单','工单→生成领料单→仓库发料→库存扣减。TC41-42',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,154,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 20:43:49','原date:07-29 | tags:[\"生产\", \"库存\"]'),(369,'DEV-155','dev','dev','【P1】报价→样品单转换','报价确认后\'转为样品单\'按钮+后端逻辑。TC12',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,155,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 20:43:48','原date:07-29 | tags:[\"销售\", \"报价\", \"样品\"]'),(370,'DEV-156','dev','dev','【P1】生产校验完善','提交生产时校验BOM/路线已配置且已审批。TC32',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,156,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 20:43:47','原date:07-29 | tags:[\"生产\", \"校验\"]'),(371,'DEV-157','dev','dev','【P2】工序首检/巡检','工序执行中可做首检/巡检，不合格触发暂停。TC45',NULL,NULL,NULL,NULL,NULL,3,'normal',NULL,157,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-03 10:37:55','原date:07-29 | tags:[\"生产\", \"质检\"]'),(372,'DEV-158','dev','dev','【P2】询价→报价联动','询价单一键跳转报价页，预填客户/需求信息。TC4-5',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,158,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 20:43:46','原date:07-29 | tags:[\"销售\", \"联动\"]'),(373,'DEV-159','dev','dev','【P2】样品迭代记录','数据库加字段+UI展示多轮修改历史（退回原因/时间/版本）。TC23',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,159,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-01 20:43:44','原date:07-29 | tags:[\"销售\", \"样品\"]'),(374,'DEV-160','dev','dev','单据PDF打印','报价单/销售订单/采购订单/生产工单/入库单/出库单/送货单 PDF 生成（exportPdf 空壳待实现）',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,160,NULL,NULL,'2026-07-29 09:00:00',NULL,'2026-07-31 15:46:01','admin','2026-07-29 09:00:00',NULL,'2026-07-31 17:50:29','原date:07-29 | tags:[\"打印\", \"PDF\"]'),(375,'DEV-161','dev','dev','标签打印','产品标签(编码/批次号/日期/数量) + 物料标签 + 箱标/托盘标，扫码追溯',NULL,NULL,NULL,NULL,NULL,3,'high',NULL,161,NULL,NULL,'2026-07-29 09:00:00',NULL,'2026-07-31 15:46:01','admin','2026-07-29 09:00:00',NULL,'2026-07-31 17:50:29','原date:07-29 | tags:[\"打印\", \"标签\"]'),(376,'DEV-162','dev','dev','质检报告导出','PDF/Excel 格式，给客户看的质检报告',NULL,NULL,NULL,NULL,NULL,3,'high',NULL,162,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-07-31 19:04:09','原date:07-29 | tags:[\"打印\", \"质检\"]'),(377,'DEV-163','dev','dev','【Bug】Transition 组件报错（询价单页）','select.vue:32 Component inside <Transition> renders non-element root node that cannot be animated. 触发页面：<SalesInquiry>',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,163,NULL,NULL,'2026-07-30 09:00:00',NULL,NULL,'admin','2026-07-30 09:00:00',NULL,'2026-08-01 11:12:13','原date:07-30 | tags:[\"Bug\", \"前端\"]'),(378,'DEV-164','dev','dev','统一 traceId：OperLogAspect 自动扫描参数提取','不改245处@Log注解，改OperLogAspect一处：方法执行后自动扫描参数对象(bizId/bizType/traceId)和Result.data，不再依赖UUID兜底。已改完。',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,164,NULL,NULL,'2026-07-30 09:00:00',NULL,NULL,'admin','2026-07-30 09:00:00',NULL,'2026-08-01 10:49:28','原date:07-30 | tags:[\"日志\", \"后端\"]'),(379,'DEV-165','dev','dev','询价转报价 traceId 链路打通','新建InquiryConvertVO，Service返回带traceId，Controller @Log 加traceId=#result.data.traceId。已改完。',NULL,NULL,NULL,NULL,NULL,10,'urgent',NULL,165,NULL,NULL,'2026-07-30 09:00:00',NULL,NULL,'admin','2026-07-30 09:00:00',NULL,'2026-08-01 10:49:31','原date:07-30 | tags:[\"日志\", \"销售\"]'),(380,'DEV-166','dev','dev','业务类型字典对齐后端 BusinessType enum','前端businessTypeMap用的是RuoYi旧的code(0=其它...9=清空数据)，后端BusinessType code从1开始(INSERT=1...OTHER=9,RESET=10)，需要前端字典对齐。',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,166,NULL,NULL,'2026-07-30 09:00:00',NULL,NULL,'admin','2026-07-30 09:00:00',NULL,'2026-08-01 11:12:20','原date:07-30 | tags:[\"日志\", \"前端\", \"Bug\"]'),(381,'DEV-167','dev','dev','库存-采购联动：安全库存检查逻辑','出库/入库确认后检查 stock < safe_stock，自动写入 inventory_alert_log',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,167,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-08-01 20:43:24','原date:07-31 | tags:[\"库存\"]'),(382,'DEV-168','dev','dev','库存-采购联动：安全库存事件触发','新建 StockAlertService.checkAndAlert()，fire stock.low/stock.over 事件',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,168,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-08-01 20:43:25','原date:07-31 | tags:[\"库存\"]'),(383,'DEV-169','dev','dev','调拨单：补 create + getDetail','实现调拨单创建和详情查询（含明细行）',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,169,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-08-01 20:43:14','原date:07-31 | tags:[\"库存\"]'),(384,'DEV-170','dev','dev','调拨单：补 confirmOut + confirmIn','调出扣库存 + 调入加库存 + 写库存流水',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,170,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-08-01 20:43:25','原date:07-31 | tags:[\"库存\"]'),(385,'DEV-171','dev','dev','盘点单：补 create + getDetail','实现盘点单创建和详情查询',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,171,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-08-01 20:43:56','原date:07-31 | tags:[\"库存\"]'),(386,'DEV-172','dev','dev','盘点单：补 inputStocktakeData + calculateDiff','录入实盘数量 + 计算差异（实盘-系统库存）',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,172,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-08-01 20:43:27','原date:07-31 | tags:[\"库存\"]'),(387,'DEV-173','dev','dev','盘点单：补 processDiff（盈亏处理）','盘盈→入库单 / 盘亏→出库单',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,173,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-08-01 20:44:10','原date:07-31 | tags:[\"库存\"]'),(388,'DEV-174','dev','dev','采购-库存事件桥接完善','InventoryEventBridge：生产完工→入库、销售发货→出库、stock.low→采购建议',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,174,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-08-01 20:43:53','原date:07-31 | tags:[\"库存\", \"采购\"]'),(389,'DEV-175','dev','dev','purchase_order.order_type 类型修复','实体映射 int→String，对齐 varchar(20) 数据库字段',NULL,NULL,NULL,NULL,NULL,10,'low',NULL,175,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-07-31 17:30:38','原date:07-31 | tags:[\"采购\"]'),(390,'DEV-176','dev','dev','物料删除校验扩展：检查采购/生产/销售订单引用','deleteWithCheck() 目前只查 inventory_stock；扩展检查 purchase_order_item、production_order_material、销售订单明细，有引用则拒绝删除（对应代码中遗留 TODO）',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,176,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-08-01 20:43:50','原date:07-31 | tags:[\"库存\"]'),(391,'DEV-177','dev','dev','jjx-kanban 对接：数据库字段扩展','sys_event_config 加 kanban_module(默认office) + priority(默认normal)；sys_task 加 kanban_module 字段',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,177,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-07-31 18:18:20','原date:07-31 | tags:[\"看板\", \"数据库\"]'),(392,'DEV-178','dev','dev','jjx-kanban 对接：后端任务路由','LocalEventPublisher 建任务改读配置（setPriority/setKanbanModule 不再写死 normal）；KanbanTaskController 新建 GET /kanban/board/{module}/tasks 列表接口（按 kanban_module/status/priority 过滤）',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,178,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-07-31 18:18:19','原date:07-31 | tags:[\"看板\", \"后端\"]'),(393,'DEV-179','dev','dev','jjx-kanban 对接：事件配置页加路由字段','eventConfig/index.ts 表单+表格加『看板模块』『优先级』两个下拉；初始配置：stock.low/stock.over→emergency/urgent，order/purchase审核→office/high，其余审批→office/normal',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,179,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-07-31 18:18:17','原date:07-31 | tags:[\"看板\", \"配置页\"]'),(394,'DEV-180','dev','dev','jjx-kanban 对接：前端看板接入','board-real.ts 成功码 code===0→200（根因，否则永远降级Mock）；office接 /kanban/board/office/tasks；emergency按优先级分组(紧急/高/普通/低)；board.ts 模板数组留扩展位(未来purchase/warehouse/product)',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,180,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-07-31 18:18:16','原date:07-31 | tags:[\"看板\", \"前端\"]'),(395,'DEV-181','dev','dev','jjx-kanban production 模块接通','production 读 production_order 工单表（ProductionKanbanController 已有），前端修好成功码即可用；卡片详情接 GET /production/order/{orderId}；拖拽暂只做状态流转(start/complete/cancel)',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,181,NULL,NULL,'2026-07-31 09:00:00',NULL,NULL,'admin','2026-07-31 09:00:00',NULL,'2026-07-31 18:18:06','原date:07-31 | tags:[\"看板\", \"生产\"]'),(397,'DEV-183','dev','dev','用户管理新建用户时选择角色无效','新建用户时勾选角色不生效，需要排查前端角色选择/提交逻辑',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 21:51:32','admin','2026-07-31 16:10:27',NULL,'2026-08-04 21:51:32',NULL),(399,'DEV-184','dev','dev','事件配置页查询空字符串导致列表为空','EventConfigController 分页/列表查询把空字符串参数当过滤条件（!= null 漏判空串），导致前端搜索框传空参数时 total=0。已改为 StringUtils.hasText() 判断，待重启验证',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-07-31 16:22:08',NULL,'2026-07-31 18:18:02',NULL),(401,'DEV-185','dev','dev','登出接口路径错误导致405+日志记录NPE','前端 authApi.logout() 调 DELETE /sessions/current，后端登出接口是 DELETE /sessions/current/out → 405；GlobalExceptionHandler.recordErrorLog 里 SecurityUtils.getUsername() 的 userInfo 为 null 二次 NPE。已修：前端路径改 /sessions/current/out，SecurityUtils getUsername/getRealName 加空值兜底，待重启验证',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-07-31 16:29:25',NULL,'2026-07-31 17:32:41',NULL),(403,'DEV-186','dev','dev','通知页面挂载到侧边栏菜单','通知页面 src/views/notification/index.vue 已存在但无路由/菜单入口，需要配置数据库菜单（如\"消息通知\"）挂到侧边栏，登录用户可查看全部通知',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-07-31 16:43:59',NULL,'2026-07-31 17:51:06',NULL),(405,'DEV-187','dev','dev','标准品询价选择产品功能（待测试）','sales_inquiry 加 product_id 字段+演示产品3条；新建/编辑表单标准品时显示产品下拉自动带出描述，样品手填；详情页显示关联产品名。代码已完成编译通过，待测试验证',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-07-31 17:02:25',NULL,'2026-07-31 18:17:54',NULL),(406,'DEV-188','dev','dev','报价单明细：样品行支持自定义产品编码/名称','现状：明细产品编码是下拉选产品库、名称readonly，样品单无法手输。方案：编码列 el-select 加 allow-create（可搜可选可输入），库内选中自动带名称，自定义输入时名称放开可编辑；明细行可选标记类型(standard/sample)供后续事件派送（样品→打样任务）判断。另：searchProduct 目前是模拟数据(P001-P005)需接真实产品接口',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-07-31 17:16:39',NULL,'2026-07-31 18:14:58',NULL),(407,'DEV-189','dev','dev','报价单加类型字段+操作按钮按类型显隐','方案A：sales_quotation 加 quotation_type(1标准品/2样品)，询价单转换时继承 inquiryType，手动建报价可选；操作栏按类型显示：样品只显转样品单、标准品只显转订单',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-07-31 17:42:35',NULL,'2026-08-01 10:46:45',NULL),(408,'DEV-190','dev','dev','报价单审核流程缺失（提交审核/通过/驳回）','报价单状态枚举已有 PENDING_REVIEW(5)/APPROVED(6)，但 Controller 无审核接口，前端操作栏无审核按钮，审核事件未接通。需补：提交审核(DRAFT→PENDING_REVIEW)、审核通过(PENDING_REVIEW→APPROVED)、审核驳回(PENDING_REVIEW→REJECTED)、前端按钮、事件联动(quotation.submitted/reviewed)',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-07-31 17:43:31',NULL,'2026-07-31 18:23:10',NULL),(409,'dev-1785492303157','general','dev','报价管理报错','\", \"quotationType\", \"sendRemark\", \"createTime\", \"taxRate\", \"salesPersonName\", \"quotationNo\", \"contactPhone\", \"approverName\", \"createBy\", \"sendTime\", \"updateTime\", \"discountAmount\", \"approveTime\", \"subtotalAmount\", \"remark\", \"sendMethod\", \"deleted\", \"taxAmount\", \"approveRemark\", \"finalAmount\", \"totalAmount\", \"traceId\", \"salesPersonId\", \"approverId\")\n at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 431] (through reference chain: com.jjx.sales.domain.entity.SalesQuotation[\"items\"])\n        at com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.from(UnrecognizedPropertyException.java:61)\n        at com.fasterxml.jackson.databind.DeserializationContext.handleUnknownProperty(DeserializationContext.java:1200)\n        at com.fasterxml.jackson.databind.deser.std.StdDeserializer.handleUnknownProperty(StdDeserializer.java:2380)\n        at com.fasterxml.jackson.databind.deser.BeanDeserializerBase.handleUnknownProperty(BeanDeserializerBase.java:1823)\n        at com.fasterxml.jackson.databind.deser.BeanDeserializerBase.handleUnknownVanilla(BeanDeserializerBase.java:1801)\n        at com.fasterxml.jackson.databind.deser.BeanDeserializer.vanillaDeserialize(BeanDeserializer.java:308)\n        at com.fasterxml.jackson.databind.deser.BeanDeserializer.deserialize(BeanDeserializer.java:169)\n        at com.fasterxml.jackson.databind.deser.DefaultDeserializationContext.readRootValue(DefaultDeserializationContext.java:342)\n        at com.fasterxml.jackson.databind.ObjectReader._bindAndClose(ObjectReader.java:2148)\n        at com.fasterxml.jackson.databind.ObjectReader.readValue(ObjectReader.java:1504)\n        at org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter.readJavaType(AbstractJackson2HttpMessageConverter.java:397)\n        ... 58 more\n2026-07-31 18:04:04 [http-nio-8080-exec-9] WARN  c.j.s.config.GlobalExceptionHandler - 运行时异常: com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException: Unrecognized field \"items\" (class com.jjx.sales.domain.entity.SalesQuotation), not marked as ignorable (37 known properties: \"updateBy\", \"validUntil\", \"params\", \"quotationId\", \"customerName\", \"currency\", \"contactPerson\", \"convertedOrderId\", \"customerId\", \"quotationDate\", \"quotationStatus\", \"convertTime\", \"exchangeRate\", \"quotationType\", \"sendRemark\", \"createTime\", \"taxRate\", \"salesPersonName\", \"quotationNo\", \"contactPhone\", \"approverName\", \"createBy\", \"sendTime\", \"updateTime\", \"discountAmount\", \"approveTime\", \"subtotalAmount\", \"remark\", \"sendMethod\", \"deleted\", \"taxAmount\", \"approveRemark\", \"finalAmount\", \"totalAmount\", \"traceId\", \"salesPersonId\", \"approverId\")','production',NULL,NULL,'张三',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-07-31',NULL,NULL,'2026-07-31 18:05:03',NULL,'2026-08-01 11:12:00',NULL),(410,'dev-1785492844213','general','dev','任务看板数据排序默认按更新时间从最新开始排序','数据排序默认按更新时间从最新开始排序','production',NULL,NULL,'李四',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-07-31 18:14:04',NULL,'2026-07-31 18:30:26',NULL),(411,'dev-1785493822371','general','dev','为什么报价单这几个功能都没有流水或任务派送，是没配置吗','为什么报价单这几个功能都没有流水或任务派送，是没配置吗','production',NULL,NULL,'张三',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-07-31',NULL,NULL,'2026-07-31 18:30:22',NULL,'2026-08-01 14:42:51',NULL),(412,'DEV-191','dev','dev','报价单客户确认/拒绝流程补全（待测试）','报价单发送后缺客户确认/拒绝操作。已补：前端API changeStatus + 顶部操作栏/行内按钮（状态=已发送时显示客户确认/拒绝），确认=2/拒绝=3，带确认弹窗。代码完成待测试验证',NULL,NULL,NULL,NULL,NULL,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 22:14:35','admin','2026-07-31 18:31:27',NULL,'2026-08-04 22:14:35',NULL),(414,'dev-1785551574470','general','dev','报价单操作栏优化','操作栏操作按钮过多，把详情按钮做到报价单号上，点击报价单号可以查看详情，然后报价单号右边加一个复制按钮可以负责单号','production',NULL,NULL,'张三',NULL,3,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-13',NULL,NULL,'2026-08-01 10:32:54',NULL,'2026-08-01 15:25:38',NULL),(416,'dev-1785552002614','general','dev','系统管理事件配置','新增修改角色栏，应该是一个多选框，选择角色，不是让用户填入，用户难道还要去查看数据库\n','production',NULL,NULL,'李四',NULL,10,'urgent',NULL,NULL,NULL,NULL,NULL,'2026-08-07',NULL,NULL,'2026-08-01 10:40:03',NULL,'2026-08-01 16:16:36',NULL),(417,'dev-1785552793126','general','dev','系统备份方案','数据库，文档，截图，备份周期，备份任务','production',NULL,NULL,'王五',NULL,3,'urgent',NULL,NULL,NULL,NULL,NULL,'2026-08-07',NULL,NULL,'2026-08-01 10:53:13',NULL,'2026-08-01 10:53:13',NULL),(418,'dev-1785552958000','general','dev','报价单状态流转组件','列表新增\"状态流转\"按钮：时间线展示流转记录（动作/状态变化/操作人/说明/附件）；按状态提供操作（提交审核/审核通过/驳回/发送/客户确认/拒绝）；操作可填说明+上传附件（拒绝必填原因）；新建 sales_quotation_flow 流转记录表；4个流转接口支持挂附件；发送放开到已审核后',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 10:55:58',NULL,'2026-08-04 11:14:14',NULL),(419,'dev-1785553054925','general','dev','报价单错误','\n﻿\npermission.ts:186\n 路由错误: SyntaxError: The requested module \'/node_modules/.vite/deps/@element-plus_icons-vue.js?v=3bc90f18\' does not provide an export named \'Send\' (at QuotationFlowDialog.vue:124:76)\nQuotationFlowDialog.vue:124\n Uncaught (in promise) SyntaxError: The requested module \'/node_modules/.vite/deps/@element-plus_icons-vue.js?v=3bc90f18\' does not provide an export named \'Send\' (at QuotationFlowDialog.vue:124:76)\n[新] 使用 Edge 中的 Copilot 来解释控制台错误: 单击 \n 以说明错误。了解更多信息\n','production',NULL,NULL,'张三',NULL,10,'urgent',NULL,NULL,NULL,NULL,NULL,'2026-08-26',NULL,NULL,'2026-08-01 10:57:35',NULL,'2026-08-01 11:00:41',NULL),(420,'dev-1785553231621','general','dev','组件优化','4\nindex.vue:833\n ElementPlusError: [ElOnlyChild] no valid child node found\nPromise.then		\n(匿名)	@	index.vue:833\nawait in (匿名)		\n(匿名)	@	index.vue:1314\nPromise.then		\n(匿名)	@	main.ts:31\n','production',NULL,NULL,'张三',NULL,10,'low',NULL,NULL,NULL,NULL,NULL,'2026-08-07',NULL,NULL,'2026-08-01 11:00:32',NULL,'2026-08-01 11:44:04',NULL),(421,'dev-1785553400504','general','dev','组件优化2','QuotationFlowDialog.vue:316\n ElementPlusError: [el-link] [API] The underline option (boolean) is about to be deprecated in version 3.0.0, please use \'always\' | \'hover\' | \'never\' instead.\nFor more detail, please visit: https://element-plus.org/en-US/component/link.html#underline\n\n','production',NULL,NULL,'李四',NULL,10,'low',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 11:03:21',NULL,'2026-08-01 11:44:16',NULL),(422,'dev-1785553562313','general','dev','任务卡片详情width调到1200','','production',NULL,NULL,'李四',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 11:06:02',NULL,'2026-08-01 11:44:31',NULL),(423,'dev-1785553907969','general','dev','事件配置','事件配置列表角色列不能显示数字，要显示角色名称','production',NULL,NULL,'王五',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-12',NULL,NULL,'2026-08-01 11:11:48',NULL,'2026-08-01 12:06:42',NULL),(424,'dev-1785554018000','general','dev','枚举数据字典化：状态/类型字段导入配置表','把散落在50+枚举类的状态/类型字段解析导入 sys_dict + sys_dict_item（编码规则：模块_表_字段），先备份现有字典；前端下拉统一走字典、后端枚举与字典一致性校验为后续步骤。',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 11:13:38',NULL,'2026-08-01 12:06:47',NULL),(425,'dev-1785554458000','general','dev','报价单操作栏按钮按业务状态显隐','按后端状态机规则调整报价单列表操作栏+工具栏按钮显隐：发送=草稿/已审核、转订单=已确认、提交审核=草稿、客户确认/拒绝=已发送、审核通过/驳回=待审核、删除=禁已发送/已确认、修改=禁流转中状态；统一封装状态机工具函数',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 11:20:58',NULL,'2026-08-01 12:40:51',NULL),(426,'dev-1785555044000','general','dev','报价单新增状态8改单/9已完成+转订单后完结','报价单状态新增：8改单、9已完成；报价转订单成功后报价单状态改为已完成(9)；已完成状态下禁用所有操作（不可查看流水、不可修改、不可发送/审核/确认/删除等）',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 11:30:44',NULL,'2026-08-01 12:09:40',NULL),(427,'dev-1785556848000','general','dev','详情页相关文档查看+附件上传独立入口','新建只读通用组件 AttachmentPanel（列表+下载/预览+空态），接入报价单/订单/询价单/样品单详情页；新建 AttachmentUploadDialog 通用上传弹窗，报价单列表页加附件按钮（上传/删除/下载）；询价单旧附件代码清理',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 12:00:48',NULL,'2026-08-01 12:07:06',NULL),(430,'dev-1785558349726','general','dev','讨论','为什么@log el不支持int','production',NULL,NULL,'未分配',NULL,3,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 12:25:50',NULL,'2026-08-01 14:44:27',NULL),(431,'dev-1785559225025','general','dev','样品单','添加作废操作','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 12:40:25',NULL,'2026-08-01 14:00:11',NULL),(432,'dev-1785561037081','general','dev','样品单流水','样品单操作栏新增查看流水，参考报价单','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 13:10:37',NULL,'2026-08-01 14:00:09',NULL),(435,'dev-1785563421000','general','dev','全功能事件覆盖：核心126个操作补@Event+事件配置','除查询外，后台所有写操作加入事件并配置通知/任务。\n\n【规则细化】\n- 业务单据类新增（入库/出库/调拨/盘点/采购/转样品/转报价）→ both（通知+派任务）\n- 主数据新增（物料/分类/库位/仓库/客户/供应商）→ notification（仅通知）\n- 关键流转（提交/审核/通过/驳回/完成/转单）→ both\n- 普通增删改 → notification\n\n【分批】\n第1批：库存8个service约40个操作（入库/出库/调拨/盘点/物料/分类/库位/仓库/预警）\n第2批：产品工程子模块约40个\n第3批：采购约26个（发票/付款/收货/供应商）\n第4批：销售约10个（客户/收付款）\n\n【配置能力】\n- sys_event_config 已支持启用/禁用/类型/角色配置，补全事件注册后管理员可在事件配置页面统一管理\n\n【待定】库存事件目标角色：新建仓管角色 or 复用现有角色',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 21:50:51','admin','2026-08-01 13:50:21',NULL,'2026-08-04 21:50:51',NULL),(436,'dev-1785564091000','general','dev','DEV-435第1批验证：库存事件','重启8080后验证：①创建入库单→仓管cangkou0收到通知+办公室看板出现任务 ②出库/调拨/盘点同样验证 ③主数据（物料新增）仅通知不派任务。测试完拖状态。',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 14:01:31',NULL,'2026-08-05 09:47:23',NULL),(437,'dev-1785564491000','general','dev','全功能事件覆盖-剩余边缘操作（暂缓，后续评估）','DEV-435核心88个已完成，剩余未覆盖写操作约200+（含部分统计重复/导出类），后续决定是否补。\n\n【模块分布】\n- 生产执行：工单11（createOrder/updateOrder/deleteOrder/pauseOrder/completeOrder/cancelOrder/closeOrder/copyOrder/updateOrderStatus等）+ 工序执行8（startExecution/pauseExecution/completeExecution/cancelExecution等）+ 记录4\n- 销售订单/审核：订单12（insertOrder/updateOrder/approveOrder/confirmOrder等）+ 审核14（submitOrderForReview/startOrderReview/approveOrder/rejectOrder/returnOrder等，部分已在OrderStatusService覆盖）\n- 系统管理：用户15（insertUser/updateUser/resetPwd/updateUserStatus等）+ 角色10 + 菜单4 + 部门3 + 附件3\n- 采购：订单13（insertOrder/updateOrder/cancelOrder/export等）+ 付款7 + 供应商6 + 单据8 + 物料询价9（部分export为导出非写操作）\n- 库存：出入库/调拨/盘点 updateStatus等残留 + 报告8（纯统计导出，不建议加）\n- 产品：Bom8（createBom/updateBom/submitApprove/approve等）+ 产品7 + 菲林/路线/实例残留\n- 其他：质检3、设备3、通知4、工程基础2、销售发票/收款3、客户2（creditLimit/export）\n\n【建议】\n- 系统管理类（用户/角色/菜单/附件）事件意义有限，建议仅通知超级管理员或不做\n- 导出类（exportXxx）不是写操作，不加\n- 生产执行/销售订单/采购订单属高频业务，若做建议 both 派任务\n\n【状态】暂缓，等核心批次验证通过后再评估。',NULL,NULL,NULL,NULL,7,3,'low',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 14:08:11',NULL,'2026-08-03 16:57:04',NULL),(440,'dev-1785565604000','general','dev','单据详情文档追溯：报价单/样品单显示来源询价单图纸','【全部完成】单据详情文档追溯：\n\n【已完成】\n1. 询价单新增后附件bizId=0修复（add返回inquiryId+traceId，前端用返回ID上传）\n2. 历史孤儿附件回填（biz_id + trace_id）\n3. sys_attachment加trace_id列+实体字段\n4. 后端新增 /system/attachment/by-trace/{traceId} 接口\n5. AttachmentPanel支持traceId参数（查本类型+同traceId来源文档，去重合并）\n6. 报价单/样品单详情页传traceId，可见来源询价单图纸\n7. 上传接口支持traceId，新建单据上传即关联链路\n\n【验证】报价单6按traceId查到询价单4图纸（温度检测.jpg）✅\n样品单详情同样生效。',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 14:26:44',NULL,'2026-08-01 18:00:03',NULL),(443,'dev-1785566541000','general','dev','【P0】样品流程修复：退回重打断链+事件角色补齐','来源：样品管理+工程管理业务流程分析（2026-08-01）。\n\n【问题1】退回后重新打样断链（致命）：\n- 客户退回后状态=REJECTED(9)，前端\"重新开始打样\"调 approve 接口\n- 但后端 approve 只允许 PENDING_REVIEW(2)→ENGINEERING(3)，REJECTED(9)→ENGINEERING 校验失败\n- 实际数据 order 15（SP2608010010）卡在状态9无法继续\n- 修复：新增 restartEngineering 方法允许 REJECTED(9)→ENGINEERING(3)，前端改调该接口\n\n【问题2】样品单事件 target_role 全空：\n- 除 sample.created（工程9）外，approved/sent/confirmed/rejected_by_customer 等 target_role 全 NULL\n- 通知发不出去（数据：任务4条但通知0条）\n- 修复：approved/ready→工程9；sent/confirmed→销售7；rejected_by_customer→工程9；submitted→订单审核员8\n\n【问题3】审核驳回/客户退回共用状态9（语义混淆）：\n- rejectReview（审核驳回）和 rejectSample（客户退回）都到 REJECTED(9)\n- 建议：统一语义为\"回工程重打\"，前端详情页显示退回原因+重新打样按钮即可，不强拆状态（P2再评估拆状态）\n\n【验证】退回→重新打样→标记完成→送样→确认→转量产 全链路走通。',NULL,NULL,NULL,NULL,7,10,'urgent',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 14:42:21',NULL,'2026-08-04 11:26:29',NULL),(444,'dev-1785566548000','general','dev','【P1】工程管理完善：接单/拒单/工序进度/轮次快照','来源：样品管理+工程管理业务流程分析（2026-08-01）。\n\n【现状】工程区已有：工艺备注(2000字)、图纸上传(3/4/5状态可传≤10MB)、标记完成、退回记录+重新打样按钮、轮次sampleRound。\n\n【缺口与建议】\n1. 工程接单/拒单动作：startEngineering目前只是记备注，状态3靠审核通过自动进；建议加\"接单确认\"步骤，工程可主动接单或拒单（拒单需原因）\n2. 工序级进度：打样过程无工序进度（印刷/冲切/贴合/模切），工程无法标记\"当前在哪个工序\"；建议复用生产工序概念或轻量进度字段\n3. 轮次快照：多轮打样只有sampleRound数字+备注，没有每轮的图纸/工艺参数归档；建议每轮保存快照（轮次+图纸+参数）\n4. 退回原因佐证：退回卡片未接附件区，建议退回时能传截图佐证\n5. 打样成本/工时：无记录，无法核算打样成本\n\n【关联】DEV-364/365/366/367 样品单既有任务。',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 14:42:28',NULL,'2026-08-01 18:00:46',NULL),(445,'dev-1785566554000','general','dev','【P2】样品流程增强：工艺传承/状态语义拆分/版本管理','来源：样品管理+工程管理业务流程分析（2026-08-01）。\n\n【建议】\n1. 转量产工艺传承：engineeringNote（工艺参数/材料规格/丝印/模切）转量产时应带入标准订单，当前是纯文本无结构化\n2. 状态语义拆分：审核驳回(改单重新提交) 与 客户退回(工程重打) 拆成不同状态，当前共用REJECTED(9)\n3. 多轮版本管理：每轮打样独立版本（图纸+参数+结果），支持对比回溯\n4. 样品单详情完整链路：查看流水已加（DEV-432），文档追溯已加（DEV-440）\n\n【关联】DEV-443（P0修复）完成后评估。',NULL,NULL,NULL,NULL,7,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 14:42:34',NULL,'2026-08-01 18:00:40',NULL),(446,'dev-1785568410000','general','dev','角色权限补全：工程/仓管/审核员','权限检查发现3个角色权限缺失，已修复（数据库sys_role_menu）：\n\n【工程管理(9)】补样品单全套9权限（229-237，含工程接单sales:sample:engineering）+父级销售管理13\n【仓管(11)】补库存全权限45条（入库/出库/调拨/盘点/物料/预警/报表，菜单18-139）\n【订单审核员(8)】补报价审核88/样品单审核233/采购审核163+父级菜单（原只有销售订单4条）\n\n【用户关联】销售sales_zhang+xiaoshou0/审核office0/工程gongcheng0/仓管cangkou0/销售管理sales\n\n【注意】权限登录时加载，相关用户需重新登录生效。',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 15:13:30',NULL,'2026-08-01 15:13:30',NULL),(447,'dev-1785568977000','general','dev','设计任务（作废）','工程管理菜单\"设计任务\"评估结论：不独立做，复用看板即可（sys_task+看板模板已覆盖任务管理，设计任务本质是任务，独立页面会导致两套任务系统分裂）。菜单91指向空壳页，处理：隐藏菜单或后续在看板加design模板。',NULL,NULL,NULL,NULL,7,4,'low',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 15:22:57',NULL,'2026-08-01 15:22:57',NULL),(449,'dev-178556898900001','general','dev','工程管理前端补全：薄膜管理/产品配置模型页面','【已完成】薄膜管理前端补全：\n- 新建 api/product/film.ts（对接/engineering/films 11个接口）\n- 新建 views/product/film/index.vue（列表/按产品筛选/增删改/提交审批/通过/驳回/新版本/设当前/下发生产）\n- 菜单92指向新页面\n\n【暂缓】产品配置模型：后端ConfigModelController只有2个查询接口（list/page），无实体无CRUD操作，属半成品模块。建前端页面只能看到空列表。需后端先补全CRUD（新建实体/表/增删改接口）再做前端。已建任务登记后续。\n\n【完成度】DEV-449部分完成：薄膜✅ 配置模型暂缓。',NULL,NULL,NULL,NULL,7,3,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 15:23:09',NULL,'2026-08-05 09:48:30',NULL),(450,'dev-1785569491275','general','dev','样品单工程接单','已解答+改进：列表页新增工程接单列（待接单/已接单+接单人）和当前工序列，接单后列表可见变化。完整流程说明见会话记录：①审核通过→工程打样中 ②工程接单（记录接单人）③填工艺参数 ④选当前工序 ⑤标记样品完成（归档轮次）⑥送样→确认/退回。BOM/工艺路线在工程管理菜单维护（产品→BOM/工艺路线），与样品单独立；样品单打样用样品的工艺参数字段。','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 15:31:31',NULL,'2026-08-01 18:00:16',NULL),(451,'dev-1785569714717','general','dev','样品单工程接单2','已解答+改进：列表页新增工程接单列（待接单/已接单+接单人）和当前工序列，接单后列表可见变化。完整流程说明见会话记录：①审核通过→工程打样中 ②工程接单（记录接单人）③填工艺参数 ④选当前工序 ⑤标记样品完成（归档轮次）⑥送样→确认/退回。BOM/工艺路线在工程管理菜单维护（产品→BOM/工艺路线），与样品单独立；样品单打样用样品的工艺参数字段。','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 15:35:15',NULL,'2026-08-01 18:00:15',NULL),(452,'dev-1785569741840','general','dev','任务看板','无法保存备注的的','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 15:35:42',NULL,'2026-08-01 17:03:07',NULL),(453,'dev-178556979300002','general','dev','产品配置模型补全：后端CRUD+前端页面','DEV-449 检查发现：产品配置模型（菜单93）后端半成品，需补全。\n\n【现状】\n- 表：product_config_model（model_id/code/name/product_id/is_default/status等）+ product_config_option（选项表）已存在\n- 实体：ConfigModel 已存在\n- Mapper：BaseMapper（自带CRUD）\n- Service：只有 listPage 查询\n- Controller：只暴露 GET /engineering/config 和 /page 两个查询接口\n\n【需补】\n后端：\n1. IConfigModelService 加 create/update/delete/setDefault/changeStatus 方法\n2. ConfigModelController 加 POST/PUT/DELETE 接口（含配置选项CRUD）\n3. 校验：model_code 唯一、product_id 必填\n\n前端：\n4. 新建 api/product/configModel.ts\n5. 新建 views/product/config-model/index.vue（列表/增删改/默认配置/启用停用）\n6. 菜单93指向新页面\n\n【关联】DEV-449（薄膜完成，配置模型暂缓待本任务）。',NULL,NULL,NULL,NULL,7,3,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 15:36:33',NULL,'2026-08-05 09:48:28',NULL),(454,'dev-178557087700001','general','dev','打样工序历史记录表','打样过程工序历史缺失：当前 current_process 是单个字段覆盖，走过哪些工序/每道耗时/操作人无记录。\n\n【方案】新建轻量工序记录表 sales_sample_process（process_id/order_id/process_name/start_time/end_time/operator/remark），工程区工序选择改为\"新增记录\"而非覆盖，保留完整工序历史。\n\n【关联影响】①前端工程区工序选择从覆盖改新增记录 ②T3轮次快照补全依赖本表数据归档 ③与T2(打样BOM录入)相互独立。\n\n【依赖】无前置。',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 15:54:37',NULL,'2026-08-01 17:47:49',NULL),(455,'dev-178557087700002','general','dev','打样BOM结构化录入','打样过程BOM数据缺失：当前无结构化物料录入，物料使用散落在工艺参数文本里，转量产无BOM可引用。\n\n【方案】工程区加\"物料清单\"录入（层结构：面板/线路/间隔/背胶 + 规格/用量），新建打样BOM表或复用字段。\n\n【关联影响】①工程区布局加物料清单tab ②T4转建档联动依赖本表数据生成BOM草稿 ③与T1(工序记录)相互独立。\n\n【依赖】无前置。',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 21:50:51','admin','2026-08-01 15:54:37',NULL,'2026-08-04 21:50:51',NULL),(456,'dev-178557087700003','general','dev','打样轮次快照补全（图纸+BOM+工序）','轮次快照不完整：sales_sample_round 有 attachment_ids 字段但归档时未存图纸；无BOM/工序快照。\n\n【方案】标记样品完成归档时：①写入该轮图纸附件ID ②存BOM物料快照 ③存工序记录汇总。\n\n【关联影响】①sales_sample_round表结构变更+归档逻辑改 ②依赖T1工序表、T2 BOM表的归档数据。\n\n【依赖】T1、T2完成后做。',NULL,NULL,NULL,NULL,7,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 15:54:37',NULL,'2026-08-01 17:59:39',NULL),(457,'dev-178557087700004','general','dev','打样转建档联动（BOM/工艺路线）','打样成功转量产时无建档联动：BOM/工艺路线不自动创建，标准订单无工艺依据可引用。\n\n【方案】①样品确认后自动派\"工程建档\"任务给工程角色 ②转量产时检查产品是否已有BOM/工艺路线，无则拦截或提醒 ③根据打样BOM数据生成BOM草稿供工程确认。\n\n【关联影响】①新增事件配置（建档任务）②转量产接口逻辑改 ③依赖T2打样BOM数据。\n\n【依赖】T2完成后做。',NULL,NULL,NULL,NULL,7,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 15:54:37',NULL,'2026-08-01 17:59:55',NULL),(458,'dev-178557163500001','general','dev','P0 补全业务流程文档（采购/库存/生产/工程/事件）','深度解析结论：目前只有销售有端点级流程分析（docs/sales-flow-analysis.md），其他模块只有HTML概要流程图。\n\n【要做】以代码为准逐模块补：\n1. 采购流程分析（供应商/采购单/审批/到货/付款，端点级）\n2. 库存流程分析（入库/出库/调拨/盘点/预警）\n3. 生产流程分析（工单/排产/工序/完工）\n4. 工程流程分析（BOM/工艺路线/菲林/样品接单）\n5. 事件联动文档（每个事件→通知谁→派任务给谁）\n\n【作用】这是测试和设计的地基，测试工作台核对、新功能设计都基于它。\n\n【关联】docs/sales-flow-analysis.md 为销售样板。',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 16:07:15',NULL,'2026-08-05 09:47:32',NULL),(459,'dev-178557163500002','general','dev','P0 跨模块链路E2E测试（按测试工作台全量核对）','深度解析结论：测试工作台（docs/test/index.html）定义了14模块77条用例，但实际只测过销售+基础数据25条，P5采购/P6生产/P7质检/P8库存基本没执行。\n\n【要做】按测试工作台全量执行并逐条核对蓝图vs现实：\n1. 优先跨模块联动用例：TC-38订单→工单(BOM/路线校验)、TC-44采购到货→库存增加、TC-49/50领料单、TC-56完工→质检\n2. 每条记录：蓝图期望 vs 现实行为 vs 缺失/偏差\n3. 输出缺失清单登记新任务\n\n【已知差异】\n- TC-38 createInstances只校验订单已确认，未校验BOM/路线审批\n- TC-44 receiveOrderItem无库存联动\n- TC-49/50 领料单功能完全缺失（DEV-368已完成领料单？待核对）\n- TC-19样品审核驳回蓝图说REJECTED，现实已改CREATED（蓝图需同步）\n- TC-27转量产蓝图说通知生产部，现实通知销售\n\n【关联】DEV-362测试工作台同步。',NULL,NULL,NULL,NULL,7,3,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 16:07:15',NULL,'2026-08-05 09:49:15',NULL),(460,'dev-178557163500003','general','dev','P1 事件/权限矩阵文档','深度解析结论：事件配置、角色权限是分散的，没有一张总表。\n\n【要做】建一张矩阵文档（docs/event-permission-matrix.md）：\n- 行：每个业务操作（提交审核/审核通过/转单等）\n- 列：触发事件 / 通知谁 / 派任务给谁 / 谁有权限操作\n- 数据来源：sys_event_config + sys_role_menu + 代码@Event\n\n【作用】发现配置缺失（曾出现target_role全NULL、角色权限0条），新模块接入时对照填表。\n\n【关联】DEV-435事件覆盖、权限补全记录。',NULL,NULL,NULL,NULL,7,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 16:07:15',NULL,'2026-08-01 20:43:09',NULL),(461,'dev-178557163500004','general','dev','P1 状态机统一规范','深度解析结论：各模块状态枚举独立开发，无统一规范，导致语义混淆（如样品单审核驳回/客户退回曾共用状态9）。\n\n【要做】建文档 docs/state-machine-spec.md：\n1. 统一状态机设计要求：流转图+终态+拒绝路径+回退路径\n2. 命名规范：状态枚举名、编码规则（数字段分配）\n3. 前端枚举同步规范（enums/目录，createEnum工厂）\n4. 新模块状态机必须过审（对照本文档）\n\n【作用】防止再出现\"两个业务共用状态\"、前后端枚举漂移。',NULL,NULL,NULL,NULL,7,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 16:07:15',NULL,'2026-08-04 18:19:53',NULL),(462,'dev-178557163500005','general','dev','P2 数据传承规则（单据转换字段/附件/参数）','深度解析结论：单据转换时数据传承无统一规则，靠traceId事后补救。\n\n【已知传承链】：\n- 询价→报价：客户/需求/图纸（traceId继承）✅\n- 报价→样品：客户/产品明细/traceId ✅\n- 样品→量产：产品明细+工艺参数文本（REMARK）✅，但BOM/工艺路线不建档（DEV-457）\n- 报价→订单：产品明细 ✅\n\n【要做】建文档 docs/data-inheritance-spec.md：\n1. 每个转换点：哪些字段必须带、附件是否随转、参数如何传承\n2. 缺什么补什么（如报价单无sourceInquiryId字段）\n3. 新单据转换必须对照文档\n\n【关联】DEV-440文档追溯、DEV-457转建档联动。',NULL,NULL,NULL,NULL,7,3,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 16:07:15',NULL,'2026-08-05 09:48:44',NULL),(463,'dev-178557165200006','general','dev','P1 测试数据真实性：建立真实业务场景测试数据集','深度解析补充：现有测试数据是\"造的\"（E2E客户/测试产品），真实业务约束（产品未发布不可下单、BOM未审批不可生产、客户等级、价格阶梯等）测不出来。\n\n【要做】建一套贴近真实业务的测试数据集：\n1. 完整产品线（已发布/待审核/停产各状态）\n2. 已审批BOM+工艺路线\n3. 真实客户（不同等级/信用额度）\n4. 供应商+物料+价格\n5. 跨模块依赖数据齐备，能跑通 询价→量产→发货 全链路\n\n【作用】测试工作台核对时用真实数据，才能暴露业务约束类bug。\n\n【关联】DEV-459跨模块链路测试。',NULL,NULL,NULL,NULL,7,3,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 16:07:32',NULL,'2026-08-05 09:49:04',NULL),(464,'dev-178557165200007','general','dev','P2 测试工作台蓝图同步更新（8月状态语义）','深度解析补充：测试工作台（docs/test/index.html）是7月设计，8月改了大量状态语义未同步：\n\n【需更新】\n1. TC-19样品审核驳回：蓝图写REJECTED，现实已改CREATED(1)（DEV-445语义拆分）\n2. TC-27转量产：蓝图写通知生产部，现实通知销售(7)\n3. TC-20工程接单入口：蓝图写\"工程看板接单\"，现实在样品单详情工程区\n4. 新增状态：样品单8改单/9已完成（报价单）\n5. 事件角色变更（sample.* target_role补齐）\n\n【作用】让蓝图反映现实，测试核对才准确。\n\n【关联】DEV-362（v2.0已建），本任务是内容同步。',NULL,NULL,NULL,NULL,7,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 16:07:32',NULL,'2026-08-04 18:19:44',NULL),(465,'dev-178557165200008','general','dev','P2 单据状态可视化：看板/列表统一状态色规范','深度解析补充：前端状态展示曾出现枚举漂移（硬编码映射、TraceTimeline不识别）、角色看不到状态变化（工程接单后列表无变化）。\n\n【要做】统一状态展示规范：\n1. 所有列表/详情状态用枚举（enums/目录）+统一色板\n2. 关键节点状态变化在列表可见（如样品单工程接单列）\n3. 看板卡片状态色与枚举一致\n\n【作用】防止状态展示漂移、用户看不到流转变化。\n\n【关联】DEV-450/451（接单列已加）。',NULL,NULL,NULL,NULL,7,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 21:51:32','admin','2026-08-01 16:07:32',NULL,'2026-08-04 21:51:32',NULL),(466,'dev-178557165200009','general','dev','P2 系统级文档目录规范：docs/ 结构整理','深度解析补充：docs/ 目录文档散乱（analysis-report/db-audit/event-driven/eventbus/module-redesign等历史文档与现状脱节）。\n\n【要做】\n1. 建立 docs/ 目录规范：flows/（业务流程）、specs/（规范：状态机/数据传承/事件权限矩阵）、analysis/（模块分析）、test/（测试）\n2. 标注每份文档的\"最后更新时间+基于代码版本\"，防止陈旧文档误导\n3. 清理与现状脱节的历史文档（或标注废弃）\n\n【作用】文档作为系统\"活资料\"，可追溯可信赖。',NULL,NULL,NULL,NULL,7,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 16:07:32',NULL,'2026-08-01 17:59:11',NULL),(467,'dev-178557203900001','general','dev','字典维护规范落地：状态码vs业务选项分离保障','深度解析补充：明确\"状态码≠字典项\"原则，防误用。\n\n【已完成】\n- 建文档 docs/dict-maintenance-spec.md：\n  1. 核心原则：状态码（*_status）必须静态枚举，业务选项才走动态字典\n  2. 判断标准：业务人员加新值代码要不要改\n  3. 必须静态枚举清单（所有*_status）\n  4. 建议动态字典清单（币种/付款方式/供应商类型/客户等级/物料类型/工序类型/预警/单位）\n  5. 保障机制：新增状态必须建枚举、字典管理只维护业务选项、评审检查点\n\n【待做】\n- 字典管理页对 *_status 类字典标\"只读参考\"标识\n- 前端 useDict 缓存层（按需接入，不做全量）\n- 币种/单位等真正业务选项类字段，按需接入动态字典\n\n【作用】防止状态码被当字典改导致代码断裂；字典只服务真正需要动态维护的业务选项。',NULL,NULL,NULL,NULL,7,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 16:13:59',NULL,'2026-08-01 18:00:05',NULL),(470,'dev-178557258700001','general','dev','【E2E缺口】订单提交生产→创建生产工单（TC-38）','E2E核对发现（DEV-459报告）：createInstances是空壳，只改订单状态为生产中(4)，不创建生产工单、不校验BOM/路线审批。\n\n【要做】\n1. createInstances 调用生产模块创建工单（含BOM+工艺路线校验：BOM已批准+路线已批准才可提交）\n2. 工单记录 BOM ID + 路线 ID（追溯）\n3. 状态流转：订单→IN_PRODUCTION(4)，工单→待排产\n4. 通知生产计划/车间\n\n【依赖】BOM/工艺路线已批准数据（DEV-463已建）\n【关联】DEV-459 E2E报告 docs/e2e-check-report-20260801.md',NULL,NULL,NULL,NULL,9,10,'urgent',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 21:50:51','admin','2026-08-01 16:23:07',NULL,'2026-08-04 21:50:51',NULL),(471,'dev-178557258700002','general','dev','【E2E缺口】采购到货→库存增加（TC-44）','E2E核对发现（DEV-459报告）：receiveOrderItem实测收货数量更新但库存不涨（500→500）。\n\n【要做】\n1. 到货登记后：增加库存流水+库存数量（按物料+仓库）\n2. 或自动创建入库单（联动TC-64）\n3. 来料检验结果=FAIL时：不增加库存或进待检区\n4. 通知仓库\n\n【实测】POST /purchase/receipt 收货10，inventory_stock_item 仍500\n【关联】DEV-459报告',NULL,NULL,NULL,NULL,11,10,'urgent',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:29:54','admin','2026-08-01 16:23:07',NULL,'2026-08-04 19:29:54',NULL),(472,'dev-178557258700003','general','dev','【E2E缺口】工单→生成领料单（TC-49/50）','E2E核对发现（DEV-459报告）：无领料单模块，出库单有\"生产领料\"类型但无\"工单→生成领料单\"自动入口。\n\n【要做】\n1. 工单→\"生成领料单\"→按BOM生成物料清单→保存\n2. 仓库备料/发料（扣库存）\n3. 状态：领料单→已发料\n4. 工单领料状态更新\n\n【依赖】TC-38工单创建 + BOM（DEV-463已建）\n【关联】DEV-459报告',NULL,NULL,NULL,NULL,11,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 21:50:51','admin','2026-08-01 16:23:07',NULL,'2026-08-04 21:50:51',NULL),(473,'dev-178557258700004','general','dev','【E2E缺口】生产完工→质检联动（TC-56）','E2E核对发现（DEV-459报告）：completeOrder存在但不触发质检。\n\n【要做】\n1. 工单完工后自动创建质检单（关联工单/产品）\n2. 通知质检员\n3. 质检结果PASS→可入库；FAIL→返修（TC-57~60）\n\n【依赖】质检模块已有（QualityInspectionController）\n【关联】DEV-459报告',NULL,NULL,NULL,NULL,8,3,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 16:23:07',NULL,'2026-08-03 10:37:54',NULL),(476,'dev-1785576448800','general','dev','优先级视图优化','优先级视图\n拖拽视图要能改变视图优先级','production',NULL,NULL,'张三',NULL,3,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-21',NULL,NULL,'2026-08-01 17:27:29',NULL,'2026-08-01 17:27:28',NULL),(478,'dev-1785577386018','general','dev','样品单工程接单','工程打样操作不要搞抽屉，换弹出框，width1200','production',NULL,NULL,'李四',NULL,10,'urgent',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 17:43:06',NULL,'2026-08-01 17:52:28',NULL),(479,'dev-1785577481905','general','dev','样品单工程打样卡片','当前工序选择后保存才生效，不要选择就生效','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 17:44:42',NULL,'2026-08-01 17:52:32',NULL),(481,'dev-178557825700001','general','dev','操作结果展示器（多视图：标准卡/单据/聊天通知/文档登记）','【背景】状态流转操作反馈太简单（仅ElMessage轻提示），做文档登记、截图都难。\n\n【方案】新建 OperationResultDialog.vue 组件，状态流转成功后弹窗，内置4种视图一键切换+复制：\n1. 标准结果卡：操作名/单据号/旧状态→新状态/操作人/时间/下一步指引\n2. 单据视图：审核单据样式（审核意见+审核人+时间）、快递单据样式（送样登记后显示快递单号/收件人/地址/日期）\n3. 聊天通知视图：模拟系统通知消息样式【系统通知】xxx，截图像聊天记录\n4. 文档登记视图：生成 Markdown 登记文本，一键复制\n\n【接入】样品单8个操作（提交审核/通过/驳回/完成/送样/确认/退回/转量产），送样带快递信息；报价单/订单后续复用\n\n【props】{ 操作名, 单据号, 旧状态, 新状态, 备注, 单据类型(审核/快递/普通), 快递信息?, 下一步[] }\n\n【状态】先登记，到时看先在哪个功能用比较合适',NULL,NULL,NULL,NULL,7,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-01 17:57:37',NULL,'2026-08-03 09:52:10',NULL),(483,'dev-1785586282996','general','dev','报价管理','报价转样品单成功，状态却还是已确认','production',NULL,NULL,'李四',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 20:11:23',NULL,'2026-08-01 20:43:10',NULL),(484,'dev-1785586969326','general','dev','脚本新建','scripts/\n ├── start-backend.sh\n ├── start-frontend.sh\n └── start-all.sh','production',NULL,NULL,'未分配',NULL,3,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 20:22:49',NULL,'2026-08-01 20:25:40',NULL),(489,'dev-1785721391330','general','dev','报价管理优化','只有审核通过的报价单才能上传报价','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-03',NULL,NULL,'2026-08-03 09:43:11',NULL,'2026-08-03 11:24:27',NULL),(491,'dev-1785721899719','general','dev','样品单管理样品完成前置','优化样品完成前置条件，分析给出方案','production',NULL,NULL,'李四',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-03',NULL,NULL,'2026-08-03 09:51:40',NULL,'2026-08-03 10:08:56',NULL),(494,'dev-1785723162764','general','dev','报价单业务不闭环','如已拒绝状态之后应该有可以重新报价的操作，先分析这个流程','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-03',NULL,NULL,'2026-08-03 10:12:43',NULL,'2026-08-03 11:10:18',NULL),(495,'dev-1785723200000','general','dev','操作预览器 Phase 2（采购/生产/库存模块接入）','操作预览器已接销售模块（报价单 8 + 样品单 8）。待拍板：①字段类型是否需下拉选择器 ②事件预告粒度 ③证据是否强制 ④Phase 2 范围。确认后往 operationRegistry 加配置即可，组件零改动。',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:29:54','admin','2026-08-03 10:37:12',NULL,'2026-08-04 19:29:54',NULL),(496,'dev-1785724908698','general','dev','客户管理','客户状态流转，改成选择器，或者提供更优方案','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-03',NULL,NULL,'2026-08-03 10:41:49',NULL,'2026-08-04 11:11:03',NULL),(498,'dev-1785726016888','general','dev','检查样品单业务逻辑是否闭环','已发现，样品退回后，下一轮不知如何流转','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-03',NULL,NULL,'2026-08-03 11:00:17',NULL,'2026-08-03 11:10:13',NULL),(500,'dev-1785726305287','general','dev','工程打样工作台','能不能按轮次展示，然后历史轮次如何查询，给个方案','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-03','2026-08-04 19:29:54',NULL,'2026-08-03 11:05:05',NULL,'2026-08-04 19:29:54',NULL),(502,'dev-1785726598681','general','dev','样品单详情','为什么会有操作按钮','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-03',NULL,NULL,'2026-08-03 11:09:59',NULL,'2026-08-04 16:18:37',NULL),(503,'dev-1785727070000','general','dev','样品单转量产业务验证','验证转量产闭环（业务预期：①已确认(6)→已转量产(7) ②生成标准SO订单（草稿，备注含工艺参数传承/最后工序/材料成本/打样工时） ③从打样工序单元自动聚合生成标准BOM+工艺路线（草稿待审核） ④样品单/报价单回写 convertedOrderId 追溯链路 ⑤后续流程：标准订单→提交审核→订单确认→提交生产）。；\n⚠️ 验证前提：产品需已发布(status=6)，当前演示数据产品为待审核(2)——可先 UPDATE product SET product_status=6 或走产品审核流。资料转移已实测通过（TF2608030005，产品/BOM/路线建档+通知+任务508）。',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:29:54','admin','2026-08-03 11:17:44',NULL,'2026-08-04 19:29:54',NULL),(504,'dev-1785727140000','general','dev','样品单全按钮权限控制','样品单页面按钮全部接入 v-hasPermi 权限控制（当前只做了转量产）。对应权限码：提交审核/通过/驳回→sales:sample:approve，送样→sales:sample:deliver，确认/退回→sales:sample:confirm，重新打样/工程接单→sales:sample:engineering，作废→sales:sample:delete，样品完成→待定（可复用 sales:sample:edit 或新增）。注意 v-hasPermi 移除 DOM 机制与 el-tooltip 层级。',NULL,NULL,NULL,NULL,NULL,3,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-03 11:20:04',NULL,'2026-08-03 11:21:51',NULL),(505,'dev-1785728460000','general','dev','产品资料转移功能（转量产前置建档）','【方案】样品确认(6)后、转量产前，销售触发【资料转移】（可配置勾选内容）：①产品建档（报价单产品→正式产品档案，无则建，状态初始化待审核，走产品审核流）②BOM建档（打样工序单元材料聚合生成草稿 approve_status=1）③工艺路线建档（打样工序聚合草稿）④图纸/文件关联（可选）。建档后事件通知（产品管理+工程管理）+ 派任务（完善档案并提交审核，office看板），样品单标记已转移。转量产瘦身：校验资料已转移+产品已发布 → 生成标准订单（明细从报价单复制）。DEV-457 自动建档逻辑拆出。需新增 sales_sample_transfer 表 + 转移接口 + 事件 sample.transferred + 任务模板。拍板项：触发人（销售）、可重复（未转量产前可覆盖）、聚合源（最新Round）、任务角色（产品/工程）。',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-03 11:41:11',NULL,'2026-08-04 16:18:45',NULL),(514,'dev-1785742782666','general','dev','工艺路线编辑','==> Parameters: 8ed41106b43645ea8ab4b0081f0f6da6(String), 28(Long), gongcheng0(String), org.springframework.web.servlet.resource.NoResourceFoundException(String), No static resource engineering/bom/4.(String), /engineering/bom/4(String), GET(String), 127.0.0.1(String), 2026-08-03T15:39:01.411090147(LocalDateTime), 0(Integer)\n2026-08-03 15:39:01 [http-nio-8080-exec-1] ERROR c.j.s.config.GlobalExceptionHandler - 系统异常 - traceId: 8ed41106b43645ea8ab4b0081f0f6da6\norg.springframework.web.servlet.resource.NoResourceFoundException: No static resource engineering/bom/4.\n        at org.springframework.web.servlet.resource.ResourceHttpRequestHandler.handleRequest(ResourceHttpRequestHandler.java:585)\n        at org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter.handle(HttpRequestHandlerAdapter.java:52)\n        at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1089)\n        at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:979)\n        at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)\n        at org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:903)','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-03',NULL,NULL,'2026-08-03 15:39:43',NULL,'2026-08-03 15:54:33',NULL),(518,'dev-1785730000000','general','dev','工程模块遗留修复：薄膜路径不匹配+冗余Controller清理','①薄膜管理：后端 FilmController 是 /engineering/film（单数），前端 api 用 /engineering/films（复数）——疑似404，需统一路径并验证 ②冗余旧Controller：RoutingController（/engineering/routing 只有page）+ EngineeringController（/engineering 通用CRUD，对应旧表）疑似废弃残留，确认后清理 ③标准工序删除保护（被路线引用时拦截）。工程模块主体已闭环（BOM/路线审核流+资料转移+生产校验），此为收尾。',NULL,NULL,NULL,NULL,NULL,3,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-03 16:04:50',NULL,'2026-08-03 16:57:15',NULL),(519,'dev-1785744569412','general','dev','询价单转报价单','询价单转报价单金额汇总有吗，询价单新增修改汇总金额要明细变得才有汇总金额，这里是不是可以看成同一类型错误\n','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-03',NULL,NULL,'2026-08-03 16:09:29',NULL,'2026-08-03 16:49:33',NULL),(526,'dev-1785746847332','general','dev','工程打样平台移到工程管理','样品单管理点击样品打样，显示操作预览，确定，状态流转，然后工程管理多一个子菜单(打样平台\n或其他)，显示已经接单的样品单，在这里操作工程打样平台\n','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-03',NULL,NULL,'2026-08-03 16:47:27',NULL,'2026-08-03 17:09:36',NULL),(527,'dev-1785733700000','general','dev','样品单作废提醒+派任务到接单人','【方案】作废(cancelSample)时：①事件提醒：新增事件配置 sample.cancelled（通知销售[7]+工程管理[9]），后端 cancelSample 挂 @Event ②派任务到接单人：若已接单（engineeringAcceptor 非空），建 sys_task 任务 assignee_name=接单人（标题\"样品单【x】已作废，请停止打样并确认\"）；未接单则事件按角色派给工程管理 ③前端作废确认弹窗提示\"将通知接单人\"。注意：事件系统目前按角色派任务，派给具体接单人需在 cancelSample 服务内直接建任务（assignee_name 字段）。',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-03 17:08:05',NULL,'2026-08-03 17:16:58',NULL),(528,'dev-1785748436227','general','dev','工程打样如果当前工序选择添加材料，材料如果不是空行要材料编号如果为空不应该保存成功','工程打样如果当前工序选择添加材料，材料如果不是空行要材料编号如果为空不应该保存成功','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-03',NULL,NULL,'2026-08-03 17:13:56',NULL,'2026-08-03 17:16:55',NULL),(529,'dev-1785748573876','general','dev','材料管理怎么只有材料分类','材料管理怎么只有材料分类','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-03',NULL,NULL,'2026-08-03 17:16:14',NULL,'2026-08-04 10:51:34',NULL),(530,'dev-1785751398554','general','dev','全单材料汇总','全单材料汇总，工序保存没及时汇总','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-03',NULL,NULL,'2026-08-03 18:03:19',NULL,'2026-08-04 10:51:29',NULL),(533,'dev-1785813687844','general','dev','产品管理','### Error querying database. Cause: java.sql.SQLSyntaxErrorException: Table \'jjx_erp_db.product_film\' doesn\'t exist ### The error may exist in com/jjx/product/mapper/ProductFilmMapper.java (best guess) ### The error may involve com.jjx.product.mapper.ProductFilmMapper.selectByProductId-Inline ### The error occurred while setting parameters ### SQL: SELECT * FROM product_film WHERE product_id = ? AND deleted = 0 ORDER BY FIELD(film_type, \'OVERLAY\', \'UPPER_CIRCUIT\', \'SPACER\', \'LOWER_CIRCUIT\', \'BACK_ADHESIVE\') ### Cause: java.sql.SQLSyntaxErrorException: Table \'jjx_erp_db.product_film\' doesn\'t exist ; bad SQL grammar []','production',NULL,NULL,'未分配',NULL,4,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04',NULL,NULL,'2026-08-04 11:21:28',NULL,'2026-08-04 12:17:11',NULL),(534,'dev-1785814126562','general','dev',' http://127.0.0.1:3000/api/sales/customers/search?keyword=jst','{\"code\":500,\"msg\":\"无此权限：sales:customer:view\",\"data\":null,\"success\":false}','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04',NULL,NULL,'2026-08-04 11:28:47',NULL,'2026-08-04 18:19:17',NULL),(535,'dev-1785814765054','general','dev','bom修改','产品列要显示产品的名称','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04','2026-08-04 19:29:54',NULL,'2026-08-04 11:39:25',NULL,'2026-08-04 19:29:54',NULL),(536,'dev-1785815144552','general','dev','产品新增确认 http://127.0.0.1:3000/api/product','{\"code\":500,\"msg\":\"无此权限：product:index:add\",\"data\":null,\"success\":false}','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04',NULL,NULL,'2026-08-04 11:45:45',NULL,'2026-08-04 18:19:08',NULL),(537,'dev-1785815231312','general','dev','权限不一致','product:create product:index:add','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04',NULL,NULL,'2026-08-04 11:47:11',NULL,'2026-08-04 18:18:27',NULL),(541,'dev-1785816948447','general','dev','产品列表，配置bom和配置工艺指向旧路由','产品列表，配置bom和配置工艺指向旧路由','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04',NULL,NULL,'2026-08-04 12:15:48',NULL,'2026-08-04 18:18:33',NULL),(542,'dev-1785817066476','general','dev','任务看板任务新增支持截图复制','任务看板任务新增支持截图复制','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04',NULL,NULL,'2026-08-04 12:17:46',NULL,'2026-08-04 18:18:46',NULL),(543,'dev-1785817066000','general','dev','迁移止血：旧表名SQL修复+补全engineering BomController','product→engineering迁移遗留问题-阶段A（止血）：\n1. 修3个Mapper的8处旧表名SQL：ProductRoutingMapper(6处 product_routing)、ProductBomItemMapper(1处 delete product_bom_item)、SalesOrderProductMapper(2处 LEFT JOIN product_bom/product_routing) → 全部改 engineering_*\n2. 补全 engineering/BomController 缺失接口（POST/、PUT/、DELETE/{id}、setDefault、calculateCost、items增删改、export），逻辑从 product/ProductBomController 复制，前端bom.ts路径不动\n验证：编译 + 逐接口curl。BOM以engineering路径为唯一入口，/product/bom废弃',NULL,NULL,NULL,NULL,11,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 12:37:50',NULL,'2026-08-04 14:58:36',NULL),(544,'dev-1785817066001','general','dev','迁移收敛：删除重复Controller/Service统一','product→engineering迁移遗留问题-阶段B（收敛）：\n1. 删product废弃Controller：ProductBomController（已被engineering替代）；film/routing/standard-process前端用复数路径由product包Controller提供 → 删engineering包单数残缺Controller（FilmController/RoutingController/StandardProcessController），确认ProductRoutingItemController归属\n2. Service统一：确认IBomService/IRoutingService（engineering，sales在用）方法能被product完整service覆盖 → sales的OrderServiceImpl/SampleOrderServiceImpl改注入product service，删engineering精简service接口\n3. 清理：product模块不再被引用的旧DTO/常量，/product/bom路径残留检查\n验证：删前grep引用无遗漏，全量编译 + vue-tsc + sales相关接口回归',NULL,NULL,NULL,NULL,11,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 12:37:50',NULL,'2026-08-04 14:58:31',NULL),(545,'dev-1785817066002','general','dev','迁移重命名：ProductBom/ProductRouting/ProductFilm→Engineering*','product→engineering迁移遗留问题-阶段C（重命名）：\n1. 实体重命名：ProductBom→EngineeringBom、ProductBomItem→EngineeringBomItem、ProductRouting→EngineeringRouting、ProductFilm→EngineeringFilm（+对应VO/DTO/Mapper/Service，约25个后端文件）\n2. 前端类型重命名：ProductBomVO/ProductBomItem等（约16个文件），API路径/engineering/*不变\n3. 包结构归位：engineering实体收进engineering/domain/entity包（可选）\n验证：编译兜底 + grep复查旧类名残留 + vue-tsc + 页面回归',NULL,NULL,NULL,NULL,11,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 12:37:50',NULL,'2026-08-04 14:58:27',NULL),(546,'dev-1785817066003','general','dev','BOM列表导出功能缺失（/engineering/bom/export 404）','544阶段B收敛时发现：BOM列表页\"导出\"按钮调用 productBomApi.exportProductBom → GET /engineering/bom/export，但后端无此接口（404）。\n\n【要做】\n1. 后端：engineering/BomController 增加 GET /engineering/bom/export（Excel导出BOM列表+明细，参照采购/销售模块的导出实现）\n2. 前端：确认导出 blob 下载处理（responseType: blob + 触发下载）\n3. 验证：导出文件可打开，内容与列表一致',NULL,NULL,NULL,NULL,11,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 13:04:38',NULL,'2026-08-04 14:58:39',NULL),(547,'dev-1785817066004','general','dev','重复实体合并：engineering.Bom/Routing 与 product.EngineeringBom/EngineeringRouting 映射同一张表','DEV-545阶段C发现：两套实体映射同一张表，存在数据一致性与维护风险。\n\n【现状】\n- engineering/domain/entity/Bom.java ↔ engineering_bom 表\n- product/domain/entity/EngineeringBom.java ↔ engineering_bom 表（同一张表！）\n- engineering/domain/entity/Routing.java ↔ engineering_routing 表\n- product/domain/entity/EngineeringRouting.java ↔ engineering_routing 表（同一张表！）\n- sales 模块同时使用两套（OrderServiceImpl/SampleOrderServiceImpl 用 engineering 的；其他用 product 的）\n\n【要做】\n1. 梳理两套实体的字段差异和使用方\n2. 统一为一套实体（建议保留 product/EngineeringBom + EngineeringRouting，删除 engineering/Bom + Routing，或反向）\n3. 同步合并 Mapper/Service（IBomService vs IEngineeringBomService）\n4. 验证：BOM/工艺 CRUD、销售转生产、样品单 BOM 生成等全链路回归\n\n【风险】sales 的 getOne/save/updateById 逻辑依赖具体实体字段，合并需逐处核对',NULL,NULL,NULL,NULL,11,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 13:08:13',NULL,'2026-08-04 14:58:41',NULL),(548,'dev-1785817066005','general','dev','重启后功能回归：验证DEV-543/544/545迁移改动','DEV-543(旧表名SQL+补全BomController)/544(删4重复Controller+路径统一)/545(71文件重命名) 三阶段迁移改动已推送，重启后端+前端后需功能回归。\n\n【验证清单】\n1. BOM管理：列表/新增/编辑/删除/审核/设为默认/计算成本/导出（导出属DEV-546）\n2. 工艺路线：列表/新增/编辑/审批/启用工序下拉\n3. 薄膜管理：列表/详情/审批\n4. 标准工序：下拉框（enabled接口已改复数路径）\n5. 产品表单：选择已审批BOM（/engineering/bom/approved）\n6. 销售链路：订单→产品校验（SalesOrderProductMapper JOIN 已改 engineering 表名）、转生产\n7. 样品单：BOM生成/工艺路线生成（sales 用 engineering 实体）\n8. 生产：工单→领料单（InventoryOutboundServiceImpl 引 EngineeringBom）\n9. 库存：出库单列表类型/状态显示\n\n发现问题直接报，或登记新任务',NULL,NULL,NULL,NULL,11,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 13:08:56',NULL,'2026-08-04 14:58:43',NULL),(549,'dev-1785817066006','general','dev','重复实体合并：engineering/StandardProcess 与 product/ProductStandardProcess 映射同一张表','DEV-547遗留：标准工序也存在双实体映射同一张表(engineering_standard_process)。\n\n【现状】\n- engineering/domain/entity/StandardProcess.java ↔ engineering_standard_process 表（sales 的 standardProcessMapper 在用）\n- product/domain/entity/ProductStandardProcess.java ↔ engineering_standard_process 表（product 的完整业务在用，含 ProductStandardProcessController/Service）\n\n【要做】\n1. 合并为一套实体（建议保留 product/ProductStandardProcess，engineering/StandardProcess 的引用方（SampleOrderServiceImpl.standardProcessMapper）改到 product 的 Mapper/实体）\n2. 统一 Mapper/Service\n3. 验证：标准工序管理页面、样品单工序引用、工艺路线编辑器工序选择\n\n【风险】engineering/StandardProcess 字段较简，product/ProductStandardProcess 字段全，合并需核对 sales 的 selectOne 查询字段',NULL,NULL,NULL,NULL,11,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 13:13:38',NULL,'2026-08-04 14:58:44',NULL),(550,'inquiry.converted-1785828150575','general','office','询价单【INQ2608040001】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','sales',NULL,NULL,NULL,7,0,'normal','inquiry.converted',NULL,NULL,NULL,'2026-08-04 15:22:31',NULL,NULL,NULL,'2026-08-04 15:22:31',NULL,'2026-08-04 15:22:30',NULL),(551,'quotation.submitted-1785828336851','general','office','报价单【1】已提交审核','报价单已提交审核，请尽快处理。','sales',NULL,NULL,NULL,8,0,'normal','quotation.submitted',NULL,NULL,NULL,'2026-08-04 15:25:37',NULL,NULL,NULL,'2026-08-04 15:25:37',NULL,'2026-08-04 15:25:36',NULL),(552,'dev-1785829069677','general','dev','分析报价单重新报价，现在的逻辑是什么','Closing non transactional SqlSession [org.apache.ibatis.session.defaults.DefaultSqlSession@153d4d38]\n2026-08-04 15:36:47 [http-nio-8080-exec-10] ERROR c.j.s.config.GlobalExceptionHandler - 系统异常 - traceId: 30db824186104a56b6ef88c444dfce7a\norg.springframework.web.servlet.resource.NoResourceFoundException: No static resource sales/quotation/1/copy.\n        at org.springframework.web.servlet.resource.ResourceHttpRequestHandler.handleRequest(ResourceHttpRequestHandler.java:585) ~[spring-webmvc-6.2.14.jar:6.2.14]\n        at org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter.handle(HttpRequestHandlerAdapter.java:52) ~[spring-webmvc-6.2.14.jar:6.2.14]\n        at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1089) ~[spring-webmvc-6.2.14.jar:6.2.14]\n报价单重新报价，现在的逻辑是什么\n','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04',NULL,NULL,'2026-08-04 15:37:50',NULL,'2026-08-04 15:58:43',NULL),(553,'inquiry.converted-1785829751648','general','office','询价单【INQ2608040002】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','sales',NULL,NULL,NULL,7,0,'normal','inquiry.converted',NULL,NULL,NULL,'2026-08-04 15:49:12',NULL,NULL,NULL,'2026-08-04 15:49:12',NULL,'2026-08-04 15:49:11',NULL),(554,'inquiry.converted-1785830021531','general','office','询价单【INQ2608040003】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','sales',NULL,NULL,NULL,7,0,'normal','inquiry.converted',NULL,NULL,NULL,'2026-08-04 15:53:42',NULL,NULL,NULL,'2026-08-04 15:53:42',NULL,'2026-08-04 15:53:41',NULL),(555,'dev-1785830523065','general','dev','报价单管理','删除报价单后数据还在','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04',NULL,NULL,'2026-08-04 16:02:03',NULL,'2026-08-04 18:20:21',NULL),(556,'quotation.submitted-1785830584135','general','office','报价单【1】已提交审核','报价单已提交审核，请尽快处理。','sales',NULL,NULL,NULL,8,0,'normal','quotation.submitted',NULL,NULL,NULL,'2026-08-04 16:03:04',NULL,NULL,NULL,'2026-08-04 16:03:04',NULL,'2026-08-04 16:03:04',NULL),(557,'sample.created-1785830607113','sample','office','样品单【1】已创建，请安排打样','报价单已转为样品单，请工程部门安排打样工作。','sales',NULL,NULL,NULL,9,0,'normal','sample.created',NULL,NULL,NULL,'2026-08-04 16:03:27',NULL,NULL,NULL,'2026-08-04 16:03:27',NULL,'2026-08-04 16:03:27',NULL),(558,'sample.ready-1785830814088','sample','office','样品【1】已制作完成','样品已制作完成，请安排送样。','sales',NULL,NULL,NULL,7,0,'normal','sample.ready',NULL,NULL,NULL,'2026-08-04 16:06:54',NULL,NULL,NULL,'2026-08-04 16:06:54',NULL,'2026-08-04 16:06:54',NULL),(559,'sample.transferred-1785830839532','sample','office','样品单【1】资料转移完成，请完善产品/BOM/工艺档案并提交审核','样品打样成果已建档（产品/BOM/工艺路线），请工程完善后提交审核','sales',NULL,NULL,NULL,9,0,'high','sample.transferred',NULL,NULL,NULL,'2026-08-04 16:07:20',NULL,NULL,NULL,'2026-08-04 16:07:20',NULL,'2026-08-04 16:07:19',NULL),(560,'dev-1785831762000','general','dev','/engineering/film 路由404排查修复（薄膜管理菜单无法打开）','页面报错: [Vue Router warn]: No match found for location with path \"/engineering/film\"\n\n【已排查】\n- sys_menu 表 menu_id=92 薄膜管理 存在: path=film, component=views/product/film/index.vue, 父菜单90(/engineering)\n- 前端组件 src/views/product/film/index.vue 存在\n- 问题: 动态路由未生成 /engineering/film 这条路由(菜单接口getRouters返回中无film或前端动态路由生成逻辑未包含)\n\n【要做】\n1. 查动态路由生成逻辑(permission.ts/RouteTemplates.ts/routeHelper.ts)为何film菜单未生成路由\n2. 对比其他正常菜单(如bom/route)与film的差异(菜单字段/父级/权限)\n3. 修复后验证: 菜单点薄膜管理能打开, 直接访问/engineering/film正常\n\n【注意】排查过程中不要碰运行中的服务进程, 只改代码',NULL,NULL,NULL,NULL,11,3,'high',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 16:56:41',NULL,'2026-08-04 18:20:32',NULL),(561,'dev-1785833979993','general','dev','工艺路线保存失败','edit.vue:215 保存工艺路线失败: Error: \n### Error updating database.  Cause: java.sql.SQLException: Field \'process_category\' doesn\'t have a default value\n### The error may exist in com/jjx/product/mapper/EngineeringRoutingItemMapper.java (best guess)\n### The error may involve com.jjx.product.mapper.EngineeringRoutingItemMapper.insert-Inline\n### The error occurred while setting parameters\n### SQL: INSERT INTO engineering_routing_item  ( routing_id, group_id, group_order, group_name, process_id, process_order, custom_labor_hours, custom_machine_hours,  description,  create_time, update_time )  VALUES (  ?, ?, ?, ?, ?, ?, ?, ?,  ?,  ?, ?  )\n### Cause: java.sql.SQLException: Field \'process_category\' doesn\'t have a default value\n; Field \'process_category\' doesn\'t have a default value\n    at service.interceptors.response.use.message (request.ts:71:29)\n    at async handleSubmit (edit.vue:211:5)\n','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04',NULL,NULL,'2026-08-04 16:59:40',NULL,'2026-08-04 18:17:01',NULL),(562,'dev-1785834053360','general','dev','任务看板截图能不能类似有个文本框对话那种给粘贴','不用给选择了','production',NULL,NULL,'未分配',NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04',NULL,NULL,'2026-08-04 17:00:53',NULL,'2026-08-04 17:02:49',NULL),(563,'product.routing.submitted-1785834509575','general','office','工艺路线【1】已提交审核','工艺路线已提交审核，请审核。','product',NULL,NULL,NULL,9,0,'normal','product.routing.submitted',NULL,NULL,NULL,'2026-08-04 17:08:30',NULL,NULL,NULL,'2026-08-04 17:08:30',NULL,'2026-08-04 17:08:29',NULL),(564,'product.routing.approved-1785834513837','general','office','工艺路线【1】审核通过','工艺路线审核已通过。','product',NULL,NULL,NULL,9,0,'normal','product.routing.approved',NULL,NULL,NULL,'2026-08-04 17:08:34',NULL,NULL,NULL,'2026-08-04 17:08:34',NULL,'2026-08-04 17:08:33',NULL),(565,'bom.submitted-1785834800791','general','office','BOM【{bizId}】已提交审核','BOM已提交审核，请尽快处理。','product',NULL,NULL,NULL,9,0,'normal','bom.submitted',NULL,NULL,NULL,'2026-08-04 17:13:21',NULL,NULL,NULL,'2026-08-04 17:13:21',NULL,'2026-08-04 17:13:20',NULL),(566,'order.review_started-1785835097272','general','office','订单【2】开始审核','订单已进入审核流程，请尽快处理。','sales',NULL,NULL,NULL,8,0,'high','order.review_started',NULL,NULL,NULL,'2026-08-04 17:18:17',NULL,NULL,NULL,'2026-08-04 17:18:17',NULL,'2026-08-04 17:18:17',NULL),(567,'dev-1785832800001','general','dev','链路追踪A：补齐5模块@Log的bizId/bizType（~160处）','全模块链路追踪-阶段A（补@Log配置）：\n现状: inventory(65个)/purchase(48)/production(31)/product(9)/engineering(10) 的@Log只配了module+businessType, 无bizId/bizType → 操作日志无业务关联, traceId无法继承串联, 前端查不到流水\n\n【要做】\n1. 5个模块~160个@Log补 bizType + bizId SpEL\n2. 规则统一: 新增bizId=#result.data / 修改=#dto.xxxId或#xxxId / 删除=#ids[0], bizType用模块代码\n3. 关键状态流转操作加bizStatus(审核/确认/发料等)\n4. 验证: 各单据增删改后 sys_oper_log 有 biz_id/biz_type, 前端TraceTimeline能查到\n\n【参照】sales模块已完善(69个@Log 59个带bizId), 照其格式补',NULL,NULL,NULL,NULL,11,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:25:20',NULL,'2026-08-04 17:21:07',NULL,'2026-08-04 19:25:20',NULL),(568,'dev-1785832800002','general','dev','链路追踪B：主实体补traceId字段+创建生成+跨模块透传','全模块链路追踪-阶段B（打通跨模块链路）：\n现状: traceId靠同一单据历史继承, 跨模块断链(销售订单→生产工单 traceId不同)\n\n【要做】\n1. 各模块主实体补traceId字段(销售已有, purchase/production/inventory/product/engineering补)\n2. 创建时生成traceId(可用UUID或雪花)\n3. 跨模块调用时透传: 销售订单转生产→工单继承traceId; 工单领料→出库单继承; 采购到货→入库单继承\n4. 验证: 跨模块链路一查到底(订单→工单→领料单 同一traceId)',NULL,NULL,NULL,NULL,11,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:25:20',NULL,'2026-08-04 17:21:07',NULL,'2026-08-04 19:25:20',NULL),(569,'dev-1785832800003','general','dev','链路追踪C：前端列表页统一加查看流水入口','全模块链路追踪-阶段C（前端入口）：\n1. 各列表页加\"查看流水\"按钮(参照销售订单现有实现showTrace)\n2. 统一接TraceTimeline组件\n3. 或集成进BizFlowDetail操作弹窗的操作流水tab(已建好)\n4. 涉及: 采购订单/生产工单/库存出入库/产品/BOM/工艺路线等列表页',NULL,NULL,NULL,NULL,11,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:25:20',NULL,'2026-08-04 17:21:07',NULL,'2026-08-04 19:25:20',NULL),(570,'dev-1785832800004','general','dev','链路追踪D：关键状态流转补bizStatus','全模块链路追踪-阶段D（状态流转追踪）：\n1. 关键操作补bizStatus: 审核通过/驳回/确认/发料/到货/完工等\n2. 前端TraceTimeline能显示每次操作前后的状态变化(如 草稿→审核中→已批准)\n3. 与BizFlowDetail状态流转条联动',NULL,NULL,NULL,NULL,11,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:25:20',NULL,'2026-08-04 17:21:07',NULL,'2026-08-04 19:25:20',NULL),(571,'dev-1785836938053','general','dev','生产中订单取消闭环：联动取消生产工单+事件+工单取消侧补漏','分析结论：销售订单取消(cancelOrder)只改自身状态，无任何联动，生产工单/发货单/领料全被晾着，无闭环。\n子项：\n1. 销售订单取消时联动：查 salesOrderId=orderId 且未终结工单→逐单调用生产工单取消；有不可取消(已完成)则提示部分工单已完成无法全部取消\n2. cancelOrder 补 @Event(\"order.cancelled\") 事件\n3. 修生产工单 canCancelOrder 漏项：补 PENDING_START(待开始)/PAUSED(已暂停)\n4. 可选：工单全部取消后回写销售订单状态',NULL,NULL,NULL,NULL,11,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:17:12',NULL,'2026-08-04 17:48:58',NULL,'2026-08-04 19:17:12',NULL),(572,'dev-1785837784140','general','dev','订单确认齐套检查：CONFIRMED时按BOM算料缺料预警','背景：销售订单确认(4→6)后按单生产，需在接单时评估原材料是否够。系统无成品库存，做原材料BOM齐套检查。\n方案：\n1. sendToCustomer(4→6确认)时自动执行齐套检查；另加手动\"重新检查\"按钮\n2. 逻辑：订单明细产品→生效已审批BOM(is_current=1,approve_status=3)→需求=Σ(产品数量×BOM明细quantity×(1+loss_rate/100))按物料汇总→对比available_quantity(可用=总量-预留)→缺口生成预警\n3. 新增预警类型 order_shortage(订单缺料)，记录物料/需求/可用/缺口+关联订单号\n4. 幂等：同订单重算先清旧未处理缺料预警再重建\n5. 边界：无BOM产品跳过不阻断(提示未检查)；样品单/无明细跳过；缺口统一warning级\n拍板：挂sendToCustomer确认时检查；检查结果暂不回写订单表(可选后续加)',NULL,NULL,NULL,NULL,11,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:05:46',NULL,'2026-08-04 18:03:04',NULL,'2026-08-04 19:05:46',NULL),(573,'dev-1785837784141','general','dev','缺料预警联动：事件通知采购+衔接采购建议','背景：订单齐套检查发现缺料后，需让采购/计划感知并行动。\n方案：\n1. 缺料时触发事件(如 stock.shortage)通知采购/计划角色\n2. 缺料物料与现有生成采购建议接口(generatePurchaseSuggestions)衔接，可一键转采购申请\n3. 预警处理流程：采购补货后标记处理(现有processAlert流程)\n优先级normal，等齐套检查(核心)完成后做',NULL,NULL,NULL,NULL,11,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:05:46',NULL,'2026-08-04 18:03:04',NULL,'2026-08-04 19:05:46',NULL),(574,'dev-1785837784142','general','dev','库存预警前端真实化：预警页mock数据接真实接口','背景：views/inventory/alert/index.vue 的 getList 用的是 mockAlertData+setTimeout模拟数据，未接后端 /inventory/alert/list，真实预警不可见。\n方案：\n1. 接真实接口：/inventory/alert/list(分页)、/inventory/alert/unprocessed(未处理)、/inventory/alert/mark-read、/batch-mark-read、/process\n2. 新增 order_shortage 缺料预警展示(物料/需求/可用/缺口/关联订单号)\n3. 可选：订单确认缺料时前端弹窗提示(待拍板)\n4. 权限按钮 v-hasPermi 对齐 inventory:alert:view/edit',NULL,NULL,NULL,NULL,11,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:05:46',NULL,'2026-08-04 18:03:04',NULL,'2026-08-04 19:05:46',NULL),(575,'dev-1785837784143','general','dev','库存预警后端顺手修复：出库重复检查+安全库存取值错','背景：排查库存预警触发机制时发现2个bug。\n方案：\n1. InventoryOutboundServiceImpl 出库确认处 for循环内每次调 checkSafeStockAlert()(全量) → 移到循环外只调一次\n2. InventoryAlertServiceImpl.checkSafeStockAlert 中 alert.setSafeStock(stock.getTotalQuantity()) 把当前库存当安全库存 → 改为从 inventory_material 表取 safe_stock 字段\n3. 完成后验证入库/出库确认预警正常',NULL,NULL,NULL,NULL,11,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:05:46',NULL,'2026-08-04 18:03:04',NULL,'2026-08-04 19:05:46',NULL),(576,'dev-1785837913153','general','dev','产品(成品)库存需求分析与方案选型：先库存后生产(MTS/MTO混合)','需求已定案(2026-08-04 用户拍板)：\n1. 方案选型：方案A 产品建物料档案(material_type新增P=成品，产品编码即物料编码，库存体系全复用)\n2. 订单明细行不拆列展示(内部仍按缺货量生成生产工单)\n3. 全库存满足订单：确认后手动进入发货流程，不自动跳转\n4. 生产完成→自动生成成品入库单(走入库流程)\n5. 预留机制要做(订单确认预留reserved+，取消释放，出库扣减同步释放)\n需求文档：docs/requirements/product-stock-requirement.html\n下一步：拆成品库存开发任务(产品发布建物料档案联动/订单确认库存检查+预留/工单完成自动入库/发货扣库存)',NULL,NULL,NULL,NULL,11,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:11:22',NULL,'2026-08-04 18:05:13',NULL,'2026-08-04 19:11:22',NULL),(577,'dev-1785838594352','general','dev','产品发布自动建物料档案联动(方案A落地)','背景：方案A已定(产品建物料档案，material_type新增P=成品，产品编码即物料编码，库存体系全复用)。\n方案：\n1. inventory_material 支持 material_type=P(成品)，product发布(status=6 RELEASED)时自动同步建物料档案(无则建，已建不重复)\n2. 建立 product_id ↔ material_id 映射(建议product表加material_id字段或单独映射表，分析时定)\n3. 产品改名/停用同步物料；物料列表/选择器按类型过滤(成品/原材料区分)\n4. 删除/作废产品时联动处理物料档案\n依赖：DEV-576方案已定；前置：无',NULL,NULL,NULL,NULL,11,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:11:22',NULL,'2026-08-04 18:16:34',NULL,'2026-08-04 19:11:22',NULL),(578,'dev-1785838594353','general','dev','订单确认成品库存检查+预留机制','背景：业务规则：客户下产品订单优先考虑库存，有库存先发，不够的部分再生产(MTS/MTO混合)。\n方案：\n1. sendToCustomer(4→6确认)时按订单明细产品检查成品可用库存(available_quantity)\n2. 库存充足→预留(reserved+数量)；不足→拆两部分：库存部分预留直接发货，缺货部分(订单量-库存量)进生产\n3. 生产工单数量=缺货量(不是整单！)；全库存满足不生成工单，手动进入发货流程(拍板3)\n4. 预留释放：订单取消→释放；出库扣减→同步释放\n5. 订单明细不拆列展示(拍板2)，内部逻辑按缺货量\n6. 无库存档案/新产品→视为0库存全量生产；样品单跳过\n依赖：产品建物料档案(先)；关联：DEV-572原材料齐套检查',NULL,NULL,NULL,NULL,11,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:25:20',NULL,'2026-08-04 18:16:34',NULL,'2026-08-04 19:25:20',NULL),(579,'dev-1785838594354','general','dev','生产工单完成自动生成成品入库单','背景：拍板4：生产完成→自动生成成品入库单(走入库流程)。\n方案：\n1. 生产工单完成(状态→COMPLETED)时自动生成成品入库单(InventoryInbound，物料=成品物料档案P类型)\n2. 入库数量=工单合格数量；入库确认后成品库存增加\n3. 支持手动触发/失败重试；工单完成事件联动\n依赖：产品建物料档案(先)；关联生产工单完成逻辑',NULL,NULL,NULL,NULL,11,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:25:20',NULL,'2026-08-04 18:16:34',NULL,'2026-08-04 19:25:20',NULL),(580,'dev-1785838594355','general','dev','销售发货出库扣成品库存+释放预留','背景：拍板5：预留机制要做。订单确认预留(reserved+)，发货出库时扣减库存并释放预留。\n方案：\n1. 销售发货出库(SalesDelivery→InventoryOutbound)确认时扣减成品库存\n2. 出库数量与预留联动：扣减total-，预留reserved-同步释放\n3. 部分发货场景：分批扣减，剩余预留保留\n4. 与订单缺货量/生产入库衔接，保证账实一致\n依赖：产品建物料档案+订单确认预留(先)',NULL,NULL,NULL,NULL,11,10,'high',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:25:20',NULL,'2026-08-04 18:16:34',NULL,'2026-08-04 19:25:20',NULL),(581,'dev-1785839403631','general','dev','工序跳要测试','','production',NULL,NULL,'未分配',NULL,3,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04',NULL,NULL,'2026-08-04 18:30:04',NULL,'2026-08-04 18:30:19',NULL),(582,'dev-1785840254799','general','dev','agent 提示词','收尾提示词','production',NULL,NULL,'未分配',NULL,3,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04',NULL,NULL,'2026-08-04 18:44:15',NULL,'2026-08-04 18:44:14',NULL),(583,'dev-1785840720000','general','dev','订单确认缺料前端弹窗提示（待拍板）','背景：DEV-572/573 已实现订单齐套检查+缺料预警+联动，574 已让预警列表真实化。缺最后一环：订单确认(CONFIRMED)时若齐套检查发现缺料，前端弹窗提示用户。\n待拍板：\n1. 弹窗时机：发送客户确认后立即弹？还是订单列表可见时提示？\n2. 提示方式：确认弹窗 / 红点角标 / 列表内标记\n3. 是否阻断操作（建议不阻断，仅提示）',NULL,NULL,NULL,NULL,NULL,10,'normal',NULL,NULL,NULL,NULL,NULL,NULL,'2026-08-04 19:25:20','agent','2026-08-04 18:52:35',NULL,'2026-08-04 19:25:20',NULL),(584,'quotation.submitted-1785851779679','general','office','报价单【5】已提交审核','报价单已提交审核，请尽快处理。','sales',NULL,NULL,NULL,8,0,'normal','quotation.submitted',NULL,NULL,NULL,'2026-08-04 21:56:20',NULL,NULL,NULL,'2026-08-04 21:56:20',NULL,'2026-08-04 21:56:19',NULL),(585,'quotation.submitted-1785851899729','general','office','报价单【6】已提交审核','报价单已提交审核，请尽快处理。','sales',NULL,NULL,NULL,8,0,'normal','quotation.submitted',NULL,NULL,NULL,'2026-08-04 21:58:20',NULL,NULL,NULL,'2026-08-04 21:58:20',NULL,'2026-08-04 21:58:19',NULL);
/*!40000 ALTER TABLE `sys_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `nick_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '昵称',
  `user_type` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '用户类型',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '邮箱',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '手机号',
  `sex` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '性别（0男 1女 2未知）',
  `avatar` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '头像地址',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `salt` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '盐值',
  `status` tinyint DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '删除标志（0正常 2删除）',
  `login_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `dept_id` bigint DEFAULT NULL COMMENT '部门Id',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `idx_user_name` (`user_name`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,'admin','系统管理员','','admin@jjx.com','13800138000','0','','$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-03-18 15:57:47','admin','2026-03-18 15:57:47','管理员账号',2),(26,'xiaoshou0','销售0','','13012345678@130.com','13012345678','0','','$2a$10$Qx.VUAClCS1.cVI5MvXY9eVDun69Mh28xG0Y46Ni1iEA.RIQjFUN6','',0,'0','',NULL,'admin','2026-07-22 14:39:20','admin','2026-07-22 14:39:20','',3),(27,'office0','办公室管理员0','','13112345670@131.com','13112345670','0','','$2a$10$Qx.VUAClCS1.cVI5MvXY9eVDun69Mh28xG0Y46Ni1iEA.RIQjFUN6','',0,'0','',NULL,'admin','2026-07-23 18:50:50','admin','2026-07-23 18:50:50','',4),(28,'gongcheng0','工程0','','13212345670@132.com','13212345670','0','','$2a$10$Qx.VUAClCS1.cVI5MvXY9eVDun69Mh28xG0Y46Ni1iEA.RIQjFUN6','',0,'0','',NULL,'admin','2026-07-23 19:04:02','admin','2026-07-23 19:04:02','',4),(29,'sales_zhang','销售张三','','','','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','',0,'0','',NULL,'admin','2026-07-31 00:46:37','',NULL,'',NULL),(30,'sales','销售管理','','19912345670@199.com','19912345670','0','','$2a$10$WHH8geBWL8EaubtdxJp9je3/uUZ7RzLaAI8.z3Lvb5BmXXPrCPqgq','',0,'0','',NULL,'admin','2026-07-31 16:05:58','admin','2026-07-31 16:05:58','',3),(32,'cangkou0','仓管0','','','','0','','$2a$10$Qx.VUAClCS1.cVI5MvXY9eVDun69Mh28xG0Y46Ni1iEA.RIQjFUN6','',0,'0','',NULL,'admin','2026-08-01 13:59:58','',NULL,'',NULL),(33,'JJX','JJX','','13912345670@139.COM','13912345670','0','','$2a$10$d8Z509yRfeP4nKwvGwTTQ.reRTdahb2/tI8fah8SxCwVh/rVxMEXO','',0,'0','',NULL,'admin','2026-08-03 17:51:25','admin','2026-08-03 17:51:25','',4);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `idx_sys_user_role_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户和角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1,1),(26,7),(27,8),(28,9),(28,12),(29,7),(30,10),(32,11),(33,1);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Temporary view structure for view `v_inventory_transaction`
--

DROP TABLE IF EXISTS `v_inventory_transaction`;
/*!50001 DROP VIEW IF EXISTS `v_inventory_transaction`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_inventory_transaction` AS SELECT 
 1 AS `transaction_id`,
 1 AS `transaction_type`,
 1 AS `transaction_type_name`,
 1 AS `material_code`,
 1 AS `material_name`,
 1 AS `batch_no`,
 1 AS `warehouse_id`,
 1 AS `warehouse_name`,
 1 AS `location_id`,
 1 AS `location_name`,
 1 AS `quantity`,
 1 AS `before_quantity`,
 1 AS `after_quantity`,
 1 AS `unit_cost`,
 1 AS `amount`,
 1 AS `source_type`,
 1 AS `source_no`,
 1 AS `transaction_time`,
 1 AS `operator_name`,
 1 AS `remark`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `v_material_latest_inquiry`
--

DROP TABLE IF EXISTS `v_material_latest_inquiry`;
