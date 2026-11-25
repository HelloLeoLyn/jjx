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
  `parent_id` bigint DEFAULT NULL COMMENT '上级库位；空值为仓库下根位置',
  `capacity_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '用户是否启用容量限制',
  `capacity_unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '容量单位；仅同单位账量可比较',
  PRIMARY KEY (`location_id`),
  UNIQUE KEY `uk_location_code` (`location_code`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_status` (`status`),
  KEY `idx_location_parent` (`parent_id`),
  CONSTRAINT `fk_location_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `inventory_warehouse` (`warehouse_id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库位表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_storage_location`
--

LOCK TABLES `inventory_storage_location` WRITE;
/*!40000 ALTER TABLE `inventory_storage_location` DISABLE KEYS */;
INSERT INTO `inventory_storage_location` VALUES (5,2,'WH02-L000001','D区','normal',NULL,0.00,NULL,NULL,NULL,0,'0','admin','2026-11-25 10:13:48','admin','2026-11-25 10:13:48','',NULL,0,NULL),(6,2,'WH02-L000002','架一','normal',NULL,0.00,NULL,NULL,NULL,0,'0','admin','2026-11-25 10:13:54','admin','2026-11-25 10:13:54','',5,0,NULL),(7,2,'WH02-L000003','二层','normal',NULL,0.00,NULL,NULL,NULL,0,'0','admin','2026-11-25 10:14:02','admin','2026-11-25 10:14:02','',6,0,NULL),(8,2,'WH02-L000004','2格','normal',NULL,0.00,NULL,NULL,NULL,0,'0','admin','2026-11-25 10:14:07','admin','2026-11-25 10:14:07','',7,0,NULL);
/*!40000 ALTER TABLE `inventory_storage_location` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-25 10:33:21
