-- guard backup ATT-V1B-11 (dev-20261012-036) 测试附件 逻辑删除前快照 2026-10-13 00:56:57
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
-- WHERE:  id IN (14,15,16,17,18,19,20,22,23,25,26,27,28,29,30,31,33,34,35)

LOCK TABLES `sys_attachment` WRITE;
/*!40000 ALTER TABLE `sys_attachment` DISABLE KEYS */;
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (14,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-A.png','inquiry/2026-10-12/5de1e829-be5f-4a98-892a-ed742460742a.png',70,'a4bcd7b80c65e14e3809783fc4c587ce66a995dcc142e25bf61c9337ef6f3443','image/png',0,'','admin','2026-10-12 11:34:17','','2026-10-12 11:34:16',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (15,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-B.png','inquiry/2026-10-12/7810d8ce-a0da-40b5-a4c3-fc4516d746df.png',70,'f9789207ad28e30bb69a70b8a8e4fd533bfd0efa7015c6905d24642ed2dc1f88','image/png',0,'','admin','2026-10-12 11:34:17','','2026-10-12 11:34:16',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (16,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-A.png','inquiry/2026-10-12/ee5dd885-36cd-409a-bdeb-3b1cd3ea56d7.png',70,'a4bcd7b80c65e14e3809783fc4c587ce66a995dcc142e25bf61c9337ef6f3443','image/png',0,'','admin','2026-10-12 11:34:56','','2026-10-12 11:34:56',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (17,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-A.png','inquiry/2026-10-12/7b794f9e-5c87-4088-a7dd-9ceab79b8c2b.png',70,'a4bcd7b80c65e14e3809783fc4c587ce66a995dcc142e25bf61c9337ef6f3443','image/png',0,'','admin','2026-10-12 11:34:56','','2026-10-12 11:34:56',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (18,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-A.png','inquiry/2026-10-12/47f6ce66-2055-48b6-a4a8-8bc9905af2c4.png',70,'a4bcd7b80c65e14e3809783fc4c587ce66a995dcc142e25bf61c9337ef6f3443','image/png',0,'','admin','2026-10-12 11:34:56','','2026-10-12 11:34:56',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (19,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-A.png','inquiry/2026-10-12/86f99748-411b-4807-9d29-2e918874fea5.png',70,'a4bcd7b80c65e14e3809783fc4c587ce66a995dcc142e25bf61c9337ef6f3443','image/png',0,'','admin','2026-10-12 11:34:56','','2026-10-12 11:34:56',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (20,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-A.png','inquiry/2026-10-12/3f814c44-a312-4f61-9e37-15584ebf103f.png',70,'a4bcd7b80c65e14e3809783fc4c587ce66a995dcc142e25bf61c9337ef6f3443','image/png',0,'','admin','2026-10-12 11:34:56','','2026-10-12 11:34:56',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (22,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-A.png','inquiry/2026-10-12/697e5e01-0903-46d5-a25a-1791cc22d7eb.png',70,'a4bcd7b80c65e14e3809783fc4c587ce66a995dcc142e25bf61c9337ef6f3443','image/png',0,'','admin','2026-10-12 11:35:24','','2026-10-12 11:35:24',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (23,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-B.png','inquiry/2026-10-12/c3619985-fb5b-4eba-8faa-b579a25da79d.png',70,'f9789207ad28e30bb69a70b8a8e4fd533bfd0efa7015c6905d24642ed2dc1f88','image/png',0,'','admin','2026-10-12 11:35:24','','2026-10-12 11:35:24',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (25,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-B.png','inquiry/2026-10-12/b64ae2e6-de8f-46d3-968d-757a7cc4559f.png',70,'f9789207ad28e30bb69a70b8a8e4fd533bfd0efa7015c6905d24642ed2dc1f88','image/png',0,'','admin','2026-10-12 11:35:24','','2026-10-12 15:16:50',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY','2026-11-11 15:16:50',NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (26,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-B.png','inquiry/2026-10-12/13047e4f-d58b-494f-a913-816817d6ab13.png',70,'f9789207ad28e30bb69a70b8a8e4fd533bfd0efa7015c6905d24642ed2dc1f88','image/png',0,'','admin','2026-10-12 11:35:50','','2026-10-12 11:35:49',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY','2026-11-11 11:35:50',NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (27,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-A.png','inquiry/2026-10-12/c6d60ea7-23b6-45dc-bc28-65ac0cdececc.png',70,'a4bcd7b80c65e14e3809783fc4c587ce66a995dcc142e25bf61c9337ef6f3443','image/png',0,'','admin','2026-10-12 11:36:10','','2026-10-12 11:36:09',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (28,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-A.png','inquiry/2026-10-12/cc4896a9-33fb-4677-a408-ba7157287a06.png',70,'a4bcd7b80c65e14e3809783fc4c587ce66a995dcc142e25bf61c9337ef6f3443','image/png',0,'','admin','2026-10-12 11:36:10','','2026-10-12 11:36:09',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (29,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-A.png','inquiry/2026-10-12/e8ba0c06-5d59-40a0-a370-06c366b9b1b5.png',70,'a4bcd7b80c65e14e3809783fc4c587ce66a995dcc142e25bf61c9337ef6f3443','image/png',0,'','admin','2026-10-12 11:36:10','','2026-10-12 11:36:09',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'LEGACY',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (30,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-A.png','attachment-temp/a5b66427-8255-49a4-ad9b-93cd98b19d23.bin',70,'a4bcd7b80c65e14e3809783fc4c587ce66a995dcc142e25bf61c9337ef6f3443','image/png',0,'','1','2026-10-12 12:25:21','','2026-10-12 12:25:21',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'ORPHAN','2026-11-11 12:25:21','9b946c65e64b8e6587a16e98cae729ce407f3637b98172ca27fa35d2b4a6c9fe','1');
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (31,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-B.png','inquiry/2026-10-12/7437cf00-a1d8-447d-8e3c-7134a6e81cd6.png',70,'f9789207ad28e30bb69a70b8a8e4fd533bfd0efa7015c6905d24642ed2dc1f88','image/png',0,'','admin','2026-10-12 12:25:21','','2026-10-12 12:25:21',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'BOUND',NULL,NULL,NULL);
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (33,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-B.png','attachment-temp/e36c4f72-aafd-4082-995b-2ecee6a82468.bin',70,'f9789207ad28e30bb69a70b8a8e4fd533bfd0efa7015c6905d24642ed2dc1f88','image/png',0,'','1','2026-10-12 12:25:45','','2026-10-12 12:25:45',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'TEMP','2026-10-19 12:25:45','0c1c0797f3ef98468de7678330b79da541cceadab5613519d110b155f9492e21','1');
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (34,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-A.png','attachment-temp/a6dbfc67-9431-451c-bc56-ded16f8921c0.bin',70,'a4bcd7b80c65e14e3809783fc4c587ce66a995dcc142e25bf61c9337ef6f3443','image/png',0,'','1','2026-10-12 15:54:37','','2026-10-12 15:54:37',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'TEMP','2026-10-19 15:54:37','b71ebdf2ab294cf92fac9d8ecd7d6fcd7f7eb0eefabafc427fb94e540958cbe3','1');
INSERT INTO `sys_attachment` (`id`, `biz_type`, `biz_id`, `category`, `version`, `drawing_no`, `drawing_name`, `file_role`, `trace_id`, `file_name`, `file_path`, `file_size`, `sha256`, `file_type`, `sort_order`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted`, `is_current`, `is_controlled`, `released`, `released_at`, `released_by`, `customer_product_code`, `customer_doc_type`, `customer_doc_seq`, `display_name`, `upload_state`, `expires_at`, `upload_key`, `upload_owner`) VALUES (35,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'v1a-A.png','attachment-temp/0c76e6b6-e19b-4baa-973b-afb45949fdd8.bin',70,'a4bcd7b80c65e14e3809783fc4c587ce66a995dcc142e25bf61c9337ef6f3443','image/png',0,'','1','2026-10-12 15:54:37','','2026-10-12 15:54:37',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,'TEMP','2026-10-19 15:54:37','2e834a6ecfb78de6301c2ff70b7aaf547209648e0184e6fd54d1cd4de9fe4fba','1');
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

-- Dump completed on 2026-10-13  0:56:57
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
-- WHERE:  attachment_id IN (14,15,16,17,18,19,20,22,23,25,26,27,28,29,30,31,33,34,35)

LOCK TABLES `sys_attachment_ref` WRITE;
/*!40000 ALTER TABLE `sys_attachment_ref` DISABLE KEYS */;
INSERT INTO `sys_attachment_ref` (`id`, `attachment_id`, `source_type`, `source_id`, `source_no`, `product_id`, `document_type`, `display_name`, `remark`, `sort_order`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted_by`, `deleted_at`) VALUES (7,26,'inquiry',1,'INQ261012001',NULL,NULL,NULL,NULL,0,1,'1','2026-10-12 11:35:50','1','2026-10-12 11:35:49','1','2026-10-12 11:35:49');
INSERT INTO `sys_attachment_ref` (`id`, `attachment_id`, `source_type`, `source_id`, `source_no`, `product_id`, `document_type`, `display_name`, `remark`, `sort_order`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted_by`, `deleted_at`) VALUES (8,30,'inquiry',1,'INQ261012001',NULL,NULL,NULL,NULL,0,1,'1','2026-10-12 12:25:21','1','2026-10-12 12:25:21','1','2026-10-12 12:25:21');
INSERT INTO `sys_attachment_ref` (`id`, `attachment_id`, `source_type`, `source_id`, `source_no`, `product_id`, `document_type`, `display_name`, `remark`, `sort_order`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted_by`, `deleted_at`) VALUES (11,25,'inquiry',1,'INQ261012001',NULL,NULL,NULL,NULL,0,1,'1','2026-10-12 15:10:38','1','2026-10-12 15:16:50','1','2026-10-12 15:16:50');
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

-- Dump completed on 2026-10-13  0:56:57
