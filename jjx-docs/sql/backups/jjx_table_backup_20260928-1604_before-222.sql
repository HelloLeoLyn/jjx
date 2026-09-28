-- 备份人: agent
-- 原因: 执行迁移 222_quality_lot_lineage_and_inbound_item_time.sql 前的表级备份
-- 风险: low（文件头 -- risk: low（显式降级））
-- 涉表: CURRENT_TIMESTAMP inventory_inbound_item quality_lot quality_lot_history
-- 任务码: dev-20260928-013
-- MySQL dump 10.13  Distrib 8.4.10, for Linux (x86_64)
--
-- Host: 127.0.0.1    Database: jjx_erp_db
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
-- Table structure for table `inventory_inbound_item`
--

DROP TABLE IF EXISTS `inventory_inbound_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_inbound_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `inbound_id` bigint NOT NULL COMMENT '入库单ID',
  `inventory_item_id` bigint DEFAULT NULL COMMENT '统一库存物品ID',
  `material_id` bigint DEFAULT NULL COMMENT '材料来源ID（产品入库为NULL）',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码（冗余）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称（冗余）',
  `specification` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PCS' COMMENT '单位',
  `quantity` decimal(12,4) NOT NULL COMMENT '入库数量',
  `sampled_quantity` decimal(18,4) DEFAULT NULL COMMENT '抽检数量',
  `inspection_id` bigint DEFAULT NULL COMMENT '当前IQC检验ID',
  `inspection_result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前检验结论',
  `disposition` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '不合格处置',
  `unit_price` decimal(12,4) DEFAULT NULL COMMENT '单价',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '金额',
  `batch_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `iqc_batch_id` bigint DEFAULT NULL,
  `production_date` date DEFAULT NULL COMMENT '生产日期',
  `expiry_date` date DEFAULT NULL COMMENT '有效期至',
  `location_id` bigint DEFAULT NULL COMMENT '实际存放库位',
  `qualified_quantity` decimal(12,4) DEFAULT NULL COMMENT '合格数量',
  `rejected_quantity` decimal(12,4) DEFAULT NULL COMMENT '不合格数量',
  `accepted_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '最终允收数量',
  `posted_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已过账数量',
  `reject_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '不合格原因',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `lot_id` bigint DEFAULT NULL COMMENT 'IQC 归一：关联 quality_lot（dev-20260918-026）',
  PRIMARY KEY (`item_id`),
  KEY `idx_inbound_id` (`inbound_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_location_id` (`location_id`),
  KEY `idx_inbound_item_inspection` (`inspection_id`),
  KEY `idx_inbound_inventory_item` (`inventory_item_id`),
  KEY `idx_inbound_item_iqc_batch` (`iqc_batch_id`),
  CONSTRAINT `fk_inbound_item_inventory_item` FOREIGN KEY (`inventory_item_id`) REFERENCES `inventory_item` (`inventory_item_id`),
  CONSTRAINT `fk_inbound_item_order` FOREIGN KEY (`inbound_id`) REFERENCES `inventory_inbound_order` (`inbound_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入库单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_inbound_item`
--

LOCK TABLES `inventory_inbound_item` WRITE;
/*!40000 ALTER TABLE `inventory_inbound_item` DISABLE KEYS */;
INSERT INTO `inventory_inbound_item` VALUES (1,1,2071,1572,'RM001572','FR83 0.175（LJ-084客供）',NULL,'PCS',200.0000,200.0000,NULL,'FAIL',NULL,2.0000,400.00,'IN260928003-1',1,NULL,NULL,NULL,190.0000,10.0000,190.0000,190.0000,NULL,1,NULL,1),(2,1,2072,1592,'RM001592','CY50W 样品 (新亚洲)',NULL,'PCS',100.0000,100.0000,NULL,'FAIL',NULL,2.3000,230.00,'IN260928003-2',2,NULL,NULL,NULL,90.0000,10.0000,90.0000,90.0000,NULL,2,NULL,2),(3,2,NULL,1592,'RM001592','CY50W 样品 (新亚洲)',NULL,'PCS',5.0000,NULL,NULL,NULL,NULL,2.3000,NULL,'IN260928003-2',2,NULL,NULL,NULL,NULL,NULL,0.0000,0.0000,NULL,1,NULL,2),(4,3,NULL,1572,'RM001572','FR83 0.175（LJ-084客供）',NULL,'PCS',50.0000,NULL,NULL,NULL,NULL,2.0000,100.00,'IN260928005-1',NULL,NULL,NULL,NULL,NULL,NULL,0.0000,0.0000,NULL,1,NULL,NULL);
/*!40000 ALTER TABLE `inventory_inbound_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quality_lot`
--

DROP TABLE IF EXISTS `quality_lot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quality_lot` (
  `lot_id` bigint NOT NULL AUTO_INCREMENT COMMENT '检验批ID',
  `lot_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检验批号',
  `lot_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型：IQC来料/IPQC过程/FQC成品',
  `source_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源类型：INBOUND_ITEM收货行/WORK_REPORT报工批/EXECUTION工序',
  `source_id` bigint DEFAULT NULL COMMENT '来源单据ID（入库单/工单/工序）',
  `source_item_id` bigint DEFAULT NULL COMMENT '来源行ID（入库行/报工ID/工序ID）',
  `order_id` bigint DEFAULT NULL COMMENT '生产工单ID（成品/过程）',
  `execution_id` bigint DEFAULT NULL COMMENT '工序执行ID',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID（来料）',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_id` bigint DEFAULT NULL COMMENT '产品ID（成品）',
  `product_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `batch_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次/生产批号',
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
  `result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '判定：pending/pass/fail/concession',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待检/INSPECTING检验中/JUDGED已判定/CLOSED已关闭',
  `review_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'DRAFT/PENDING/APPROVED/REJECTED',
  `parent_lot_id` bigint DEFAULT NULL COMMENT '复检来源批（复检=同批新版本）',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号（复检递增）',
  `inspector` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检验员',
  `reviewer_id` bigint DEFAULT NULL,
  `reviewer_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `review_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `defect_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `inspect_time` datetime DEFAULT NULL COMMENT '检验时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`lot_id`),
  UNIQUE KEY `uk_quality_lot_no` (`lot_no`),
  KEY `idx_quality_lot_source` (`source_type`,`source_id`),
  KEY `idx_quality_lot_order` (`order_id`,`execution_id`),
  KEY `idx_quality_lot_material` (`material_code`),
  KEY `idx_quality_lot_status` (`lot_type`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检验批（IQC/IPQC/FQC 统一模型）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quality_lot`
--

LOCK TABLES `quality_lot` WRITE;
/*!40000 ALTER TABLE `quality_lot` DISABLE KEYS */;
INSERT INTO `quality_lot` VALUES (1,'QL260928006','IQC','INBOUND',1,1,NULL,NULL,1572,'RM001572','FR83 0.175（LJ-084客供）',NULL,NULL,NULL,'IN260928003-1',200.0000,200.0000,190.0000,10.0000,0.0000,5.0000,NULL,NULL,NULL,NULL,'fail','JUDGED','APPROVED',NULL,1,'系统管理员',1,'系统管理员','2026-09-28 15:39:12',NULL,'规格：MA 5；颜色：MA 5','2026-09-28 15:39:11','FAIL：合格 190，不良 10',NULL,'2026-09-28 15:38:38',NULL,'2026-09-28 15:38:38',0),(2,'QL260928007','IQC','INBOUND',1,2,NULL,NULL,1592,'RM001592','CY50W 样品 (新亚洲)',NULL,NULL,NULL,'IN260928003-2',100.0000,100.0000,90.0000,10.0000,0.0000,10.0000,NULL,NULL,NULL,NULL,'fail','JUDGED','APPROVED',NULL,1,'系统管理员',1,'系统管理员','2026-09-28 15:39:17',NULL,'外观：MI 5；长度：MI 5','2026-09-28 15:39:17','FAIL：合格 90，不良 10',NULL,'2026-09-28 15:38:38',NULL,'2026-09-28 15:38:38',0);
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

-- Dump completed on 2026-09-28 16:04:45
