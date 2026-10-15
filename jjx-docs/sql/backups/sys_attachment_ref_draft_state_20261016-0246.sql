-- guard 备份：sys_attachment_ref（ddl 前）20261016-0246
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
-- Table structure for table `sys_attachment_ref`
--

DROP TABLE IF EXISTS `sys_attachment_ref`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_attachment_ref` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attachment_id` bigint NOT NULL,
  `source_type` varchar(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_id` bigint NOT NULL,
  `source_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产品编码（行级附件归属，内部字段，界面不可见）',
  `product_id` bigint DEFAULT NULL,
  `document_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `display_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `deleted_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL,
  `active_product_id` bigint GENERATED ALWAYS AS (ifnull(`product_id`,0)) STORED,
  `active_marker` tinyint GENERATED ALWAYS AS (if((`deleted` = 0),1,NULL)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attachment_ref_active` (`attachment_id`,`source_type`,`source_id`,`active_product_id`,`active_marker`),
  KEY `idx_attachment_ref_source` (`source_type`,`source_id`,`deleted`,`sort_order`),
  KEY `idx_attachment_ref_product` (`product_id`,`deleted`),
  CONSTRAINT `fk_attachment_ref_file` FOREIGN KEY (`attachment_id`) REFERENCES `sys_attachment` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_attachment_ref_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_attachment_ref_deleted` CHECK ((`deleted` in (0,1))),
  CONSTRAINT `chk_attachment_ref_ids` CHECK (((`source_id` > 0) and ((`product_id` is null) or (`product_id` > 0))))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公共附件业务引用，解绑保留审计';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_attachment_ref`
--

LOCK TABLES `sys_attachment_ref` WRITE;
/*!40000 ALTER TABLE `sys_attachment_ref` DISABLE KEYS */;
INSERT INTO `sys_attachment_ref` (`id`, `attachment_id`, `source_type`, `source_id`, `source_no`, `product_code`, `product_id`, `document_type`, `display_name`, `remark`, `sort_order`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted_by`, `deleted_at`) VALUES (1,94,'quotation',1,'QT261015001','YL002MEOO',2,NULL,NULL,NULL,0,0,'1','2026-10-15 19:14:11',NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `sys_attachment_ref` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-16  2:46:13
