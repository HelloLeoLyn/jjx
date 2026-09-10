-- 备份人: dahuang(OpenClaw)
-- 原因: 清理 inventory_stock 中 inventory_item_id IS NULL 的历史脏行（统一库存改造遗留）
-- 任务码: 待登记
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
-- Table structure for table `inventory_stock`
--

DROP TABLE IF EXISTS `inventory_stock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_stock` (
  `stock_id` bigint NOT NULL AUTO_INCREMENT COMMENT '汇总记录ID',
  `inventory_item_id` bigint DEFAULT NULL COMMENT '统一库存物品ID',
  `material_id` bigint DEFAULT NULL COMMENT '材料来源ID（产品库存为NULL）',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码（冗余）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称（冗余）',
  `total_quantity` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '总库存数量',
  `total_reserved` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '总预留数量',
  `available_quantity` decimal(12,4) GENERATED ALWAYS AS ((`total_quantity` - `total_reserved`)) STORED COMMENT '可用数量',
  `earliest_expiry` date DEFAULT NULL COMMENT '当前最早有效期（来自最早批次的 expiry_date）',
  `location_id` bigint DEFAULT NULL COMMENT '当前最早批次所在的库位ID',
  `last_update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`stock_id`),
  UNIQUE KEY `uk_inventory_item` (`inventory_item_id`),
  KEY `location_id` (`location_id`),
  CONSTRAINT `fk_stock_inventory_item` FOREIGN KEY (`inventory_item_id`) REFERENCES `inventory_item` (`inventory_item_id`),
  CONSTRAINT `inventory_stock_ibfk_2` FOREIGN KEY (`location_id`) REFERENCES `inventory_storage_location` (`location_id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存汇总表（按物料汇总，动态反映最早批次的库位和有效期）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_stock`
--

LOCK TABLES `inventory_stock` WRITE;
/*!40000 ALTER TABLE `inventory_stock` DISABLE KEYS */;
INSERT INTO `inventory_stock` (`stock_id`, `inventory_item_id`, `material_id`, `material_code`, `material_name`, `total_quantity`, `total_reserved`, `earliest_expiry`, `location_id`, `last_update_time`) VALUES (1,1514,1514,'MTR202608131515','DT125BL 样品 (新亚洲)',0.0000,0.0000,NULL,NULL,'2026-09-10 11:15:45'),(2,1532,1532,'MTR202608131533','CY3602W 样品 (新亚洲)',0.0000,0.0000,NULL,NULL,'2026-09-10 11:15:45'),(3,1498,1498,'MTR202608131499','无纺布1.0硬',0.0000,0.0000,NULL,NULL,'2026-09-10 11:15:45'),(4,1487,1487,'MTR202608131488','2.0mm E-4308泡棉',1.0000,0.0000,NULL,NULL,'2026-09-10 11:15:45'),(5,NULL,1514,'MTR202608131515','DT125BL 样品 (新亚洲)',3.0000,3.0000,NULL,NULL,'2026-09-10 11:15:24'),(6,NULL,1487,'MTR202608131488','2.0mm E-4308泡棉',3.0000,2.0000,NULL,NULL,'2026-09-10 11:15:24'),(7,NULL,1532,'MTR202608131533','CY3602W 样品 (新亚洲)',5.0000,5.0000,NULL,NULL,'2026-09-10 11:15:24'),(8,NULL,1498,'MTR202608131499','无纺布1.0硬',5.0000,5.0000,NULL,NULL,'2026-09-10 11:15:24'),(9,NULL,1514,'MTR202608131515','DT125BL 样品 (新亚洲)',3.0000,0.0000,NULL,NULL,'2026-09-10 11:15:45'),(11,NULL,1487,'MTR202608131488','2.0mm E-4308泡棉',3.0000,0.0000,NULL,NULL,'2026-09-10 11:15:45'),(13,NULL,1532,'MTR202608131533','CY3602W 样品 (新亚洲)',5.0000,0.0000,NULL,NULL,'2026-09-10 11:15:45'),(15,NULL,1498,'MTR202608131499','无纺布1.0硬',5.0000,0.0000,NULL,NULL,'2026-09-10 11:15:45'),(17,2048,NULL,'JST001MEOL','JST001MEOL',100.0000,0.0000,NULL,NULL,'2026-09-10 16:02:54');
/*!40000 ALTER TABLE `inventory_stock` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-10 16:34:31
