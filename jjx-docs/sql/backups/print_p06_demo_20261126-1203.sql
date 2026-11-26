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
-- Dumping data for table `print_artwork`
--

LOCK TABLES `print_artwork` WRITE;
/*!40000 ALTER TABLE `print_artwork` DISABLE KEYS */;
INSERT INTO `print_artwork` VALUES (16,'PW-001','PW-001','面板图案','面板',NULL,NULL,NULL,0,'admin','2026-11-26 12:01:14.869',NULL,NULL);
/*!40000 ALTER TABLE `print_artwork` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `print_artwork_version`
--

LOCK TABLES `print_artwork_version` WRITE;
/*!40000 ALTER TABLE `print_artwork_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `print_artwork_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `print_film_copy`
--

LOCK TABLES `print_film_copy` WRITE;
/*!40000 ALTER TABLE `print_film_copy` DISABLE KEYS */;
/*!40000 ALTER TABLE `print_film_copy` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `engineering_resource_product_rel`
--

LOCK TABLES `engineering_resource_product_rel` WRITE;
/*!40000 ALTER TABLE `engineering_resource_product_rel` DISABLE KEYS */;
INSERT INTO `engineering_resource_product_rel` VALUES (11,'ARTWORK',16,1,NULL,1,'admin',NULL,'2026-11-26 12:01:14','2026-11-26 12:01:14');
/*!40000 ALTER TABLE `engineering_resource_product_rel` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `engineering_resource_maintenance`
--

LOCK TABLES `engineering_resource_maintenance` WRITE;
/*!40000 ALTER TABLE `engineering_resource_maintenance` DISABLE KEYS */;
INSERT INTO `engineering_resource_maintenance` VALUES (86,'ARTWORK',16,'createArtwork',NULL,NULL,NULL,NULL,NULL,'admin','2026-11-26 12:01:14','p06-1795665674824-6bwpwhr5','ca6a00d11d0e38e72b3a217dfa44ddbfb61aac759bfe6f2a2809f4bce6cb2800','{\"revision\": 0, \"artworkId\": 16}');
/*!40000 ALTER TABLE `engineering_resource_maintenance` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `sys_attachment_ref`
--

LOCK TABLES `sys_attachment_ref` WRITE;
/*!40000 ALTER TABLE `sys_attachment_ref` DISABLE KEYS */;
INSERT INTO `sys_attachment_ref` (`id`, `attachment_id`, `source_type`, `source_id`, `source_no`, `product_code`, `product_id`, `document_type`, `display_name`, `remark`, `sort_order`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `deleted_by`, `deleted_at`, `draft_key`, `ref_state`, `source_oper_id`) VALUES (1,316,'sample',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,0,'1','2026-11-17 16:51:53',NULL,NULL,NULL,NULL,'sample-mwkgebv8-7wpuh3nh7ql','draft',NULL),(2,315,'purchase_payment',2,'PAY202611170002',NULL,NULL,'PAYMENT_RECEIPT','PAY-2-P0-BIZ-PAYMENT_RECEIPT-付款回单-01',NULL,0,0,'1','2026-11-17 16:59:00','1','2026-11-17 16:58:59',NULL,NULL,NULL,'active',59),(3,317,'purchase_invoice',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,0,'1','2026-11-17 17:58:12',NULL,NULL,NULL,NULL,'invoice-create-1794909483570-ccdazumqi98','draft',NULL),(4,318,'purchase_invoice',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,0,'1','2026-11-17 17:59:17',NULL,NULL,NULL,NULL,'invoice-create-1794909555731-bodbc06ic0l','draft',NULL),(5,319,'purchase_invoice',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,0,'1','2026-11-17 17:59:51',NULL,NULL,NULL,NULL,'invoice-create-1794909589196-74b0yl33umr','draft',NULL),(6,320,'purchase_invoice',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,0,'1','2026-11-17 18:01:03',NULL,NULL,NULL,NULL,'invoice-create-1794909646037-pxjy58ro11','draft',NULL),(7,321,'purchase_invoice',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,0,'1','2026-11-17 22:01:19',NULL,NULL,NULL,NULL,'invoice-create-1794924066284-7wawh9f9yoj','draft',NULL),(8,322,'purchase_invoice',NULL,NULL,NULL,NULL,'VAT_SPECIAL_INVOICE',NULL,NULL,0,0,'1','2026-11-17 22:06:08','1','2026-11-17 22:06:13',NULL,NULL,'invoice-create-1794924354513-l1gczvonthm','draft',NULL),(9,314,'purchase_payment',1,'PAY202611180001',NULL,NULL,'PAYMENT_RECEIPT','PAY-1-P0-BIZ-PAYMENT_RECEIPT-付款回单-01',NULL,0,0,'1','2026-11-18 15:18:42','1','2026-11-18 15:18:42',NULL,NULL,NULL,'active',42);
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

-- Dump completed on 2026-11-26 12:03:46
