-- 备份人: agent
-- 原因: 执行迁移 223_iqc_disposition_quality_action_link.sql 前的表级备份
-- 风险: low（文件头 -- risk: low（显式降级））
-- 涉表: inventory_iqc_disposition_order
-- 任务码: dev-20260928-022
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
-- Table structure for table `inventory_iqc_disposition_order`
--

DROP TABLE IF EXISTS `inventory_iqc_disposition_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_iqc_disposition_order` (
  `disposition_id` bigint NOT NULL AUTO_INCREMENT,
  `disposition_no` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `quarantine_id` bigint NOT NULL,
  `inbound_id` bigint NOT NULL,
  `inbound_item_id` bigint NOT NULL,
  `inspection_id` bigint DEFAULT NULL COMMENT '历史列：IQC 归一(dev-20260918-026)后由 lot_id 承载关联，不再写入',
  `action` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'RELEASE/RETURN/REWORK/SCRAP',
  `quantity` decimal(18,4) NOT NULL,
  `material_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `material_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `batch_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `iqc_batch_id` bigint DEFAULT NULL,
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPLETED',
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `lot_id` bigint DEFAULT NULL COMMENT 'IQC 归一：关联 quality_lot（dev-20260918-026）',
  PRIMARY KEY (`disposition_id`),
  UNIQUE KEY `uk_iqc_disposition_no` (`disposition_no`),
  KEY `idx_iqc_disposition_quarantine` (`quarantine_id`),
  KEY `idx_iqc_disposition_inbound` (`inbound_id`),
  KEY `idx_iqc_disposition_batch` (`iqc_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IQC隔离处置单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_iqc_disposition_order`
--

LOCK TABLES `inventory_iqc_disposition_order` WRITE;
/*!40000 ALTER TABLE `inventory_iqc_disposition_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_iqc_disposition_order` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-28 17:43:59
