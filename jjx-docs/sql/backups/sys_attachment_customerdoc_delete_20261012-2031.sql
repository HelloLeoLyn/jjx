-- guard backup: 客供资料上传通道旧数据删除前快照（2 条测试：sys_attachment 12/13 + sys_attachment_ref 1/2）
-- Leo 2026-10-12 20:30 口径：旧数据删除、不与 025 字典合并
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
-- Dumping data for table `sys_attachment`
--
-- WHERE:  id IN (12,13)

LOCK TABLES `sys_attachment` WRITE;
/*!40000 ALTER TABLE `sys_attachment` DISABLE KEYS */;
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (12,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'客供稿.jpg','inquiry/2026-10-12/f7964a45-f972-410a-b044-722e1e85df4e.jpg',186991,'47bd734aee7a21495dcf764353582f437ba3f76f72cfb4ff7cc9400ec1ca1ae0','image/jpeg',0,'','admin','2026-10-12 10:01:57','admin','2026-10-12 10:01:57',0,0,0,0,NULL,NULL,'YL001MEOO','customer_drawing',1,'INQ261012001-YL001MEOO-客户图纸-01.jpg','LEGACY',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (13,'quotation',2,NULL,NULL,NULL,NULL,NULL,NULL,'客供稿2.jpg','quotation/2026-10-12/18f9b064-c796-4c08-87be-c926c98ff15a.jpg',255541,'7d4459bbcee7f28ef9bc93724bac85d7208e8da9c7568184ab1606827072e224','image/jpeg',0,'','admin','2026-10-12 10:03:25','admin','2026-10-12 10:03:25',0,0,0,0,NULL,NULL,'YL002MEOO','other',1,'QT261012002-YL002MEOO-其他资料-01.jpg','LEGACY',NULL,NULL,NULL);
/*!40000 ALTER TABLE `sys_attachment` ENABLE KEYS */;
UNLOCK TABLES;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `trg_attachment_v1_no_delete` BEFORE DELETE ON `sys_attachment` FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V1-A: physical attachment deletion disabled; preview only' */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-12 20:31:44
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
-- Dumping data for table `sys_attachment_ref`
--
-- WHERE:  id IN (1,2)

LOCK TABLES `sys_attachment_ref` WRITE;
/*!40000 ALTER TABLE `sys_attachment_ref` DISABLE KEYS */;
INSERT INTO `sys_attachment_ref` (`id`, `attachment_id`, `source_type`, `source_id`, `source_no`, `product_id`, `document_type`, `display_name`, `remark`, `sort_order`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted_by`, `deleted_at`) VALUES (1,12,'inquiry',1,'INQ261012001',NULL,NULL,NULL,NULL,0,0,'migration-272','2026-10-12 11:01:53',NULL,NULL,NULL,NULL);
INSERT INTO `sys_attachment_ref` (`id`, `attachment_id`, `source_type`, `source_id`, `source_no`, `product_id`, `document_type`, `display_name`, `remark`, `sort_order`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted_by`, `deleted_at`) VALUES (2,13,'quotation',2,'QT261012002',NULL,NULL,NULL,NULL,0,0,'migration-272','2026-10-12 11:01:53',NULL,NULL,NULL,NULL);
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

-- Dump completed on 2026-10-12 20:31:44
