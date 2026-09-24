-- Backup by: Codex
-- Reason: Guard before schema changes for dev-20260924-002
-- Risk: low; additive schema fields and generated-index update
-- Tables: inventory_outbound_order, production_task; task: dev-20260924-002
-- MySQL dump 10.13  Distrib 8.0.45, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: jjx_erp_db
-- ------------------------------------------------------
-- Server version	8.0.45

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
-- Table structure for table `inventory_outbound_order`
--

DROP TABLE IF EXISTS `inventory_outbound_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_outbound_order` (
  `outbound_id` bigint NOT NULL AUTO_INCREMENT COMMENT '出库单ID',
  `outbound_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出库单号，格式：OUT+YYYYMMDD+流水号',
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '链路追踪ID',
  `outbound_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出库类型：production生产领料/sales销售出库/return退货出库/scrap报废出库/transfer调拨出库/adjust盘亏出库',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源类型：work_order/sales_order/purchase_return',
  `source_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `source_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单号',
  `warehouse_id` bigint NOT NULL COMMENT '出库仓库ID',
  `customer_id` bigint DEFAULT NULL COMMENT '客户ID（销售出库时使用）',
  `customer_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户名称',
  `outbound_date` date NOT NULL COMMENT '出库日期',
  `total_quantity` decimal(12,4) DEFAULT '0.0000' COMMENT '总数量',
  `total_amount` decimal(12,2) DEFAULT '0.00' COMMENT '总金额',
  `order_status` tinyint NOT NULL DEFAULT '0' COMMENT '单据状态: 0草稿,1待审批,2已批准,3已驳回,4处理中,5已确认,6已出库,7已入库,8已关闭,9已取消,10已完成,11已处理,12调拨中',
  `approve_status` tinyint NOT NULL DEFAULT '1' COMMENT '审批状态: 1待审批/2已批准/3已驳回',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `approver_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人姓名',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `approve_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批意见',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`outbound_id`),
  UNIQUE KEY `uk_outbound_no` (`outbound_no`),
  KEY `idx_source` (`source_type`,`source_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_outbound_date` (`outbound_date`),
  KEY `idx_order_status` (`order_status`),
  CONSTRAINT `fk_outbound_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `inventory_warehouse` (`warehouse_id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出库单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_outbound_order`
--

LOCK TABLES `inventory_outbound_order` WRITE;
/*!40000 ALTER TABLE `inventory_outbound_order` DISABLE KEYS */;
INSERT INTO `inventory_outbound_order` VALUES (1,'PICK-WO-PL260923001-01-1','e64152dfbbb041cf','production','work_order',2,'WO-PL260923001-01',1,NULL,NULL,'2026-09-23',310.0000,0.00,10,1,NULL,NULL,NULL,NULL,'yezeqing','2026-09-23 10:51:01','yezeqing','2026-09-23 10:51:01',NULL),(2,'PICK-WO-PL260923001-01-2','e64152dfbbb041cf','production','work_order',2,'WO-PL260923001-01',1,NULL,NULL,'2026-09-23',290.0000,0.00,10,1,NULL,NULL,NULL,NULL,'yezeqing','2026-09-23 11:33:54','yezeqing','2026-09-23 11:33:54',NULL),(3,'PICK-WO260923001-1','5608fc8e810747d0acfd2b3910021bac','production','work_order',4,'WO260923001',1,NULL,NULL,'2026-09-23',600.0000,0.00,10,1,NULL,NULL,NULL,NULL,'yezeqing','2026-09-23 23:37:29','yezeqing','2026-09-23 23:37:29',NULL),(4,'PICK-WO260923002-1','21b3d7e5720c48e08ccf1fa02a158c90','production','work_order',6,'WO260923002',1,NULL,NULL,'2026-09-23',240.0000,0.00,10,1,NULL,NULL,NULL,NULL,'yezeqing','2026-09-23 23:47:20','yezeqing','2026-09-23 23:47:20',NULL);
/*!40000 ALTER TABLE `inventory_outbound_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `production_task`
--

DROP TABLE IF EXISTS `production_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_task` (
  `task_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä»»åŠ¡IDï¼ˆç»Ÿä¸€æ ‘èŠ‚ç‚¹IDï¼Œç¬¬ä¸€å±‚ä¸Žæ‰€æœ‰ä¸‹çº§ä¸€è‡´ï¼‰',
  `task_no` varchar(96) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务号：{orderNo}-P{processOrder}-T{taskSeq}',
  `execution_id` bigint NOT NULL COMMENT 'å·¥åºæ‰§è¡ŒIDï¼ˆå·¥åºä¸Šä¸‹æ–‡ï¼›FK production_operation_executionï¼‰',
  `parent_task_id` bigint DEFAULT NULL COMMENT 'çˆ¶ä»»åŠ¡IDï¼›NULL=ç¬¬ä¸€å±‚çœŸå®žä»»åŠ¡ï¼ˆéž System Rootï¼‰',
  `assignee_id` bigint DEFAULT NULL COMMENT 'å½“å‰æ‰§è¡Œäººï¼ˆå•å€¼ï¼‰ï¼›NULL=ç¬¬ä¸€å±‚æœªåˆ†é…',
  `task_quantity` decimal(14,2) NOT NULL COMMENT 'æœ¬ä»»åŠ¡èŽ·å¾—çš„æœ‰æ•ˆä»»åŠ¡æ€»é‡ï¼ˆåˆ›å»º/åˆ†é…æ—¶å¿«ç…§ï¼›æ”¶å›žæ—¶æ¡ä»¶æ‰£å‡ï¼‰',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING=æœªåˆ†é… / ACTIVE=å·²åˆ†é…ï¼ˆP1 æœ€å°ï¼›å®Œæ•´çŠ¶æ€æœº P5ï¼‰',
  `version` int NOT NULL DEFAULT '0' COMMENT 'ä¹è§‚é”ç‰ˆæœ¬ï¼ˆP2 åˆ†é…/æ”¶å›ž/é€€å›žå¹¶å‘åœ°åŸºï¼‰',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `first_level_flag` bigint GENERATED ALWAYS AS (if((`parent_task_id` is null),1,NULL)) STORED COMMENT 'ç”Ÿæˆåˆ—ï¼šç¬¬ä¸€å±‚=1ï¼Œå­å±‚=NULLï¼›é…åˆ uk_exec_first ä¿è¯æ¯ execution å”¯ä¸€ First Task',
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_production_task_task_no` (`task_no`),
  UNIQUE KEY `uk_exec_first` (`execution_id`,`first_level_flag`),
  KEY `idx_parent_task` (`parent_task_id`),
  KEY `idx_assignee` (`assignee_id`),
  KEY `idx_execution` (`execution_id`),
  CONSTRAINT `fk_task_execution` FOREIGN KEY (`execution_id`) REFERENCES `production_operation_execution` (`execution_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_task_parent` FOREIGN KEY (`parent_task_id`) REFERENCES `production_task` (`task_id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ç”Ÿäº§ä»»åŠ ï¼ˆç»Ÿä¸€ä»»åŠ¡è´£ä»»æ ‘ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_task`
--

LOCK TABLES `production_task` WRITE;
/*!40000 ALTER TABLE `production_task` DISABLE KEYS */;
INSERT INTO `production_task` (`task_id`, `task_no`, `execution_id`, `parent_task_id`, `assignee_id`, `task_quantity`, `status`, `version`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (1,'WO-PL260923001-01-P01-T001',1,NULL,140,200.00,'ACTIVE',1,NULL,'2026-09-23 10:50:12','yezeqing','2026-09-23 10:52:12'),(2,'WO-PL260923001-01-P02-T001',2,NULL,140,200.00,'ACTIVE',1,NULL,'2026-09-23 10:50:12','yezeqing','2026-09-23 10:52:21'),(3,'WO-PL260923001-01-P01-T002',1,1,182,200.00,'COMPLETED',3,NULL,'2026-09-23 10:52:12','lihaodong','2026-09-23 11:39:00'),(4,'WO-PL260923001-01-P02-T002',2,2,182,200.00,'COMPLETED',2,NULL,'2026-09-23 10:52:21','lihaodong','2026-09-23 11:38:59'),(5,'WO-PL260923001-01-P01-T003',1,3,152,100.00,'COMPLETED',1,NULL,'2026-09-23 10:55:18','lihaodong','2026-09-23 11:39:00'),(6,'WO-PL260923001-01-P01-T004',1,3,153,100.00,'COMPLETED',1,NULL,'2026-09-23 10:55:29','lihaodong','2026-09-23 11:01:07'),(7,'WO-PL260923001-01-P02-T003',2,4,153,200.00,'COMPLETED',1,NULL,'2026-09-23 10:55:34','lihaodong','2026-09-23 11:38:59'),(8,'NCR-4-REWORK',3,NULL,140,2.00,'ACTIVE',1,'刘三元','2026-09-23 18:38:16','yezeqing','2026-09-23 19:13:01'),(9,'WO-PL260923001-01-P03-T002',3,8,153,2.00,'COMPLETED',1,NULL,'2026-09-23 19:13:01','admin','2026-09-23 19:13:22'),(10,'WO260923001-P01-T01',4,NULL,NULL,200.00,'PENDING',0,NULL,'2026-09-23 23:32:51',NULL,'2026-09-23 23:32:51'),(11,'WO260923001-P02-T01',5,NULL,NULL,200.00,'PENDING',0,NULL,'2026-09-23 23:32:51',NULL,'2026-09-23 23:32:51'),(12,'WO260923002-P01-T01',6,NULL,140,80.00,'ACTIVE',1,NULL,'2026-09-23 23:47:16','yezeqing','2026-09-24 00:02:38'),(13,'WO260923002-P02-T01',7,NULL,140,80.00,'ACTIVE',1,NULL,'2026-09-23 23:47:16','yezeqing','2026-09-24 00:02:45'),(14,'WO260923002-P01-T02',6,12,161,80.00,'COMPLETED',1,NULL,'2026-09-24 00:02:39','yezeqing','2026-09-24 00:42:07'),(15,'WO260923002-P02-T02',7,13,161,80.00,'COMPLETED',1,NULL,'2026-09-24 00:02:46','yezeqing','2026-09-24 00:42:05');
/*!40000 ALTER TABLE `production_task` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-24  8:04:33
