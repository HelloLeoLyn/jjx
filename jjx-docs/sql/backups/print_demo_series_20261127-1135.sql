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
-- Dumping data for table `engineering_routing_item`
--

LOCK TABLES `engineering_routing_item` WRITE;
/*!40000 ALTER TABLE `engineering_routing_item` DISABLE KEYS */;
INSERT INTO `engineering_routing_item` VALUES (1,1,76,NULL,'ASSEMBLY',1,0.00,0.00,NULL,NULL,NULL,NULL,NULL,'2026-11-25 18:45:32','2026-11-25 18:45:32',NULL,NULL,NULL,NULL,'PANEL',1,NULL,NULL,NULL,0,0,NULL,NULL,NULL),(2,1,77,NULL,'ASSEMBLY',2,0.00,0.00,NULL,NULL,NULL,NULL,NULL,'2026-11-25 18:45:32','2026-11-25 18:45:32',NULL,NULL,NULL,NULL,'PANEL',1,NULL,NULL,NULL,0,0,NULL,NULL,NULL),(3,1,NULL,NULL,'PRINT',3,0.00,0.00,NULL,'{\"colorNo\": \"PANTONE 032C\", \"printName\": \"\"}',NULL,NULL,NULL,'2026-11-25 18:45:32','2026-11-25 18:45:32',NULL,NULL,NULL,NULL,'PANEL',1,NULL,NULL,NULL,0,0,NULL,NULL,NULL);
/*!40000 ALTER TABLE `engineering_routing_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `print_frame_stretch`
--

LOCK TABLES `print_frame_stretch` WRITE;
/*!40000 ALTER TABLE `print_frame_stretch` DISABLE KEYS */;
/*!40000 ALTER TABLE `print_frame_stretch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `engineering_screen_plate`
--

LOCK TABLES `engineering_screen_plate` WRITE;
/*!40000 ALTER TABLE `engineering_screen_plate` DISABLE KEYS */;
/*!40000 ALTER TABLE `engineering_screen_plate` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `production_order`
--

LOCK TABLES `production_order` WRITE;
/*!40000 ALTER TABLE `production_order` DISABLE KEYS */;
/*!40000 ALTER TABLE `production_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `production_operation_execution`
--

LOCK TABLES `production_operation_execution` WRITE;
/*!40000 ALTER TABLE `production_operation_execution` DISABLE KEYS */;
/*!40000 ALTER TABLE `production_operation_execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `product_work_spec_version`
--

LOCK TABLES `product_work_spec_version` WRITE;
/*!40000 ALTER TABLE `product_work_spec_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_work_spec_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping data for table `product_work_spec_item`
--

LOCK TABLES `product_work_spec_item` WRITE;
/*!40000 ALTER TABLE `product_work_spec_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_work_spec_item` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-27 11:35:29
