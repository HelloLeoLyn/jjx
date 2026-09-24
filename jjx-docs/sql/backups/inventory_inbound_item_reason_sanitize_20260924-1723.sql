-- 备份人: hermes-agent
-- 原因: 029 A 段存量订正前 guard —— inventory_inbound_item.item_id=1.reject_reason 被界面提示串污染（选多行可整批合格），置空前备份
-- 时间: 2026-09-24 17:23:32
-- 涉表: inventory_inbound_item
-- 任务码: dev-20260924-029
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
INSERT INTO `inventory_inbound_item` VALUES (1,1,2060,1585,'RM001585','DG68 样品 (新亚洲)',NULL,'PCS',150.0000,150.0000,NULL,'FAIL','PARTIAL_ACCEPT',5.0000,750.02,'IN260924001-1',5,NULL,NULL,NULL,145.0000,5.0000,145.0000,145.0000,'选多行可整批合格',1,NULL,1),(2,1,2069,1557,'AUX001557','3.5mm38度B料黑色EVA（3.5T无胶)',NULL,'PCS',300.0000,300.0000,NULL,'PASS',NULL,3.0000,900.03,'IN260924001-2',NULL,NULL,NULL,NULL,300.0000,0.0000,300.0000,300.0000,NULL,2,NULL,2),(3,2,NULL,1585,'RM001585','DG68 样品 (新亚洲)',NULL,'PCS',20.0000,20.0000,NULL,'FAIL','PARTIAL_ACCEPT',5.0000,99.96,'IN260924002-1',6,NULL,NULL,NULL,18.0000,2.0000,18.0000,0.0000,'黑点',1,NULL,3),(4,2,NULL,1557,'AUX001557','3.5mm38度B料黑色EVA（3.5T无胶)',NULL,'PCS',20.0000,20.0000,NULL,'PASS',NULL,3.0000,59.99,'IN260924002-2',NULL,NULL,NULL,NULL,20.0000,0.0000,20.0000,0.0000,NULL,2,NULL,4);
/*!40000 ALTER TABLE `inventory_inbound_item` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-24 17:23:42
