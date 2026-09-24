-- 备份人: 大黄(OpenClaw)
-- 原因: 013 存量订正：quality_ncr/quality_lot 的 defect_reason 被界面文案污染（NCR260924001 / QL260924001）
-- 时间: 2026-09-24 16:36:42
-- 涉及表: quality_ncr, quality_lot
-- 任务码: dev-20260924-013
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
-- Table structure for table `quality_ncr`
--

DROP TABLE IF EXISTS `quality_ncr`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quality_ncr` (
  `ncr_id` bigint NOT NULL AUTO_INCREMENT COMMENT '不良台账ID',
  `ncr_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '不良单号',
  `lot_id` bigint NOT NULL COMMENT '检验批ID',
  `lot_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'IQC/IPQC/FQC',
  `order_id` bigint DEFAULT NULL COMMENT '生产工单ID（成品/过程）',
  `execution_id` bigint DEFAULT NULL COMMENT '工序执行ID',
  `material_id` bigint DEFAULT NULL,
  `material_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `material_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `product_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `batch_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `defect_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '不良数量',
  `cr_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '致命缺点数',
  `ma_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '主要缺点数',
  `mi_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '次要缺点数',
  `defect_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '不良原因/描述',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待处置/DISPOSING处置中/CLOSED已结',
  `disposed_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已处置数量',
  `scrapped_amount` decimal(14,2) DEFAULT NULL COMMENT '报废金额（预留，口径A暂不启用）',
  `inspector` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint(1) NOT NULL DEFAULT '0',
  `main_check_item` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '首因检验项目（dev-20260924-004）',
  `main_defect_level` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '首因分级 CR/MA/MI（dev-20260924-004）',
  PRIMARY KEY (`ncr_id`),
  UNIQUE KEY `uk_quality_ncr_no` (`ncr_no`),
  KEY `idx_ncr_lot` (`lot_id`),
  KEY `idx_ncr_order` (`order_id`,`execution_id`),
  KEY `idx_ncr_status` (`status`,`lot_type`),
  KEY `idx_ncr_material` (`material_code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不良台账';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quality_ncr`
--

LOCK TABLES `quality_ncr` WRITE;
/*!40000 ALTER TABLE `quality_ncr` DISABLE KEYS */;
INSERT INTO `quality_ncr` VALUES (1,'NCR260924001',1,'IQC',NULL,NULL,1585,'RM001585','DG68 样品 (新亚洲)',NULL,NULL,NULL,'IN260924001-1',5.0000,0.0000,5.0000,0.0000,'选多行可整批合格','DISPOSING',1.0000,NULL,'系统管理员',NULL,NULL,'2026-09-24 15:32:50',NULL,'2026-09-24 15:32:50',0,NULL,NULL),(2,'NCR260924002',3,'IQC',NULL,NULL,1585,'RM001585','DG68 样品 (新亚洲)',NULL,NULL,NULL,'IN260924002-1',2.0000,0.0000,2.0000,0.0000,'黑点','PENDING',0.0000,NULL,'系统管理员',NULL,NULL,'2026-09-24 16:17:55',NULL,'2026-09-24 16:17:55',0,NULL,NULL);
/*!40000 ALTER TABLE `quality_ncr` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quality_lot`
--

DROP TABLE IF EXISTS `quality_lot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quality_lot` (
  `lot_id` bigint NOT NULL AUTO_INCREMENT COMMENT '检验批ID',
  `lot_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验批号',
  `lot_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型：IQC来料/IPQC过程/FQC成品',
  `source_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源类型：INBOUND_ITEM收货行/WORK_REPORT报工批/EXECUTION工序',
  `source_id` bigint DEFAULT NULL COMMENT '来源单据ID（入库单/工单/工序）',
  `source_item_id` bigint DEFAULT NULL COMMENT '来源行ID（入库行/报工ID/工序ID）',
  `order_id` bigint DEFAULT NULL COMMENT '生产工单ID（成品/过程）',
  `execution_id` bigint DEFAULT NULL COMMENT '工序执行ID',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID（来料）',
  `material_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `material_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_id` bigint DEFAULT NULL COMMENT '产品ID（成品）',
  `product_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `batch_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次/生产批号',
  `lot_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '批量（本批应检总量）',
  `inspected_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已检数量',
  `pass_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '合格数量',
  `fail_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '不良数量',
  `stored_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已入库/已放行数量（防超入）',
  `disposed_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已处置不良数量（返工+让步+报废）',
  `sampling_plan_id` bigint DEFAULT NULL COMMENT '抽样方案ID（来料）',
  `sample_quantity` decimal(18,4) DEFAULT NULL COMMENT '抽样数量',
  `accept_number` decimal(18,4) DEFAULT NULL COMMENT '允收数 AC',
  `reject_number` decimal(18,4) DEFAULT NULL COMMENT '拒收数 RE',
  `result` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '判定：pending/pass/fail/concession',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待检/INSPECTING检验中/JUDGED已判定/CLOSED已关闭',
  `review_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DRAFT/PENDING/APPROVED/REJECTED',
  `parent_lot_id` bigint DEFAULT NULL COMMENT '复检来源批（复检=同批新版本）',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号（复检递增）',
  `inspector` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检验员',
  `reviewer_id` bigint DEFAULT NULL,
  `reviewer_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `review_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `defect_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `inspect_time` datetime DEFAULT NULL COMMENT '检验时间',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`lot_id`),
  UNIQUE KEY `uk_quality_lot_no` (`lot_no`),
  KEY `idx_quality_lot_source` (`source_type`,`source_id`),
  KEY `idx_quality_lot_order` (`order_id`,`execution_id`),
  KEY `idx_quality_lot_material` (`material_code`),
  KEY `idx_quality_lot_status` (`lot_type`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检验批（IQC/IPQC/FQC 统一模型）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quality_lot`
--

LOCK TABLES `quality_lot` WRITE;
/*!40000 ALTER TABLE `quality_lot` DISABLE KEYS */;
INSERT INTO `quality_lot` VALUES (1,'QL260924001','IQC','INBOUND',1,1,NULL,NULL,1585,'RM001585','DG68 样品 (新亚洲)',NULL,NULL,NULL,'IN260924001-1',150.0000,150.0000,145.0000,5.0000,0.0000,1.0000,NULL,NULL,NULL,NULL,'fail','JUDGED','APPROVED',NULL,1,'系统管理员',1,'系统管理员','2026-09-24 15:32:50',NULL,'选多行可整批合格','2026-09-24 15:32:50','FAIL：合格 145，不良 5',NULL,'2026-09-24 15:32:29',NULL,'2026-09-24 15:32:29',0),(2,'QL260924002','IQC','INBOUND',1,2,NULL,NULL,1557,'AUX001557','3.5mm38度B料黑色EVA（3.5T无胶)',NULL,NULL,NULL,'IN260924001-2',300.0000,300.0000,300.0000,0.0000,0.0000,0.0000,NULL,NULL,NULL,NULL,'pass','JUDGED','APPROVED',NULL,1,'系统管理员',1,'系统管理员','2026-09-24 15:40:24',NULL,NULL,'2026-09-24 15:40:24','PASS：合格 300，不良 0',NULL,'2026-09-24 15:32:29',NULL,'2026-09-24 15:32:29',0),(3,'QL260924003','IQC','INBOUND',2,3,NULL,NULL,1585,'RM001585','DG68 样品 (新亚洲)',NULL,NULL,NULL,'IN260924002-1',20.0000,20.0000,18.0000,2.0000,0.0000,0.0000,NULL,NULL,NULL,NULL,'fail','JUDGED','APPROVED',NULL,1,'系统管理员',1,'系统管理员','2026-09-24 16:17:56',NULL,'黑点','2026-09-24 16:17:56','FAIL：合格 18，不良 2',NULL,'2026-09-24 16:17:42',NULL,'2026-09-24 16:17:42',0),(4,'QL260924004','IQC','INBOUND',2,4,NULL,NULL,1557,'AUX001557','3.5mm38度B料黑色EVA（3.5T无胶)',NULL,NULL,NULL,'IN260924002-2',20.0000,20.0000,20.0000,0.0000,0.0000,0.0000,NULL,NULL,NULL,NULL,'pass','JUDGED','APPROVED',NULL,1,'系统管理员',1,'系统管理员','2026-09-24 16:17:58',NULL,NULL,'2026-09-24 16:17:58','PASS：合格 20，不良 0',NULL,'2026-09-24 16:17:42',NULL,'2026-09-24 16:17:42',0);
/*!40000 ALTER TABLE `quality_lot` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-24 16:36:42
