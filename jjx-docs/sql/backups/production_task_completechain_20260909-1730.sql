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
-- Table structure for table `production_task`
--

DROP TABLE IF EXISTS `production_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_task` (
  `task_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä»»åŠ¡IDï¼ˆç»Ÿä¸€æ ‘èŠ‚ç‚¹IDï¼Œç¬¬ä¸€å±‚ä¸Žæ‰€æœ‰ä¸‹çº§ä¸€è‡´ï¼‰',
  `task_no` varchar(96) NOT NULL COMMENT '任务号：{orderNo}-P{processOrder}-T{taskSeq}',
  `execution_id` bigint NOT NULL COMMENT 'å·¥åºæ‰§è¡ŒIDï¼ˆå·¥åºä¸Šä¸‹æ–‡ï¼›FK production_operation_executionï¼‰',
  `parent_task_id` bigint DEFAULT NULL COMMENT 'çˆ¶ä»»åŠ¡IDï¼›NULL=ç¬¬ä¸€å±‚çœŸå®žä»»åŠ¡ï¼ˆéž System Rootï¼‰',
  `assignee_id` bigint DEFAULT NULL COMMENT 'å½“å‰æ‰§è¡Œäººï¼ˆå•å€¼ï¼‰ï¼›NULL=ç¬¬ä¸€å±‚æœªåˆ†é…',
  `task_quantity` decimal(14,2) NOT NULL COMMENT 'æœ¬ä»»åŠ¡èŽ·å¾—çš„æœ‰æ•ˆä»»åŠ¡æ€»é‡ï¼ˆåˆ›å»º/åˆ†é…æ—¶å¿«ç…§ï¼›æ”¶å›žæ—¶æ¡ä»¶æ‰£å‡ï¼‰',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING=æœªåˆ†é… / ACTIVE=å·²åˆ†é…ï¼ˆP1 æœ€å°ï¼›å®Œæ•´çŠ¶æ€æœº P5ï¼‰',
  `version` int NOT NULL DEFAULT '0' COMMENT 'ä¹è§‚é”ç‰ˆæœ¬ï¼ˆP2 åˆ†é…/æ”¶å›ž/é€€å›žå¹¶å‘åœ°åŸºï¼‰',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT NULL,
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
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ç”Ÿäº§ä»»åŠ ï¼ˆç»Ÿä¸€ä»»åŠ¡è´£ä»»æ ‘ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_task`
--

LOCK TABLES `production_task` WRITE;
/*!40000 ALTER TABLE `production_task` DISABLE KEYS */;
INSERT INTO `production_task` (`task_id`, `task_no`, `execution_id`, `parent_task_id`, `assignee_id`, `task_quantity`, `status`, `version`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (1,'WO-PL2609090001-01-P01-T001',1,NULL,94,100.00,'COMPLETED',2,NULL,'2026-09-09 10:26:07','prod_manager','2026-09-09 15:22:35'),(2,'WO-PL2609090001-01-P02-T001',2,NULL,94,100.00,'ACTIVE',1,NULL,'2026-09-09 10:26:07','prod_manager','2026-09-09 10:53:24'),(3,'WO-PL2609090001-01-P03-T001',3,NULL,94,100.00,'COMPLETED',2,NULL,'2026-09-09 10:26:07','prod_manager','2026-09-09 15:23:44'),(4,'WO-PL2609090001-01-P04-T001',4,NULL,94,100.00,'ACTIVE',1,NULL,'2026-09-09 10:26:07','prod_manager','2026-09-09 10:54:09'),(5,'WO-PL2609090001-01-P05-T001',5,NULL,94,100.00,'ACTIVE',1,NULL,'2026-09-09 10:26:07','prod_manager','2026-09-09 10:54:15'),(6,'WO-PL2609090001-01-P01-T002',1,1,96,100.00,'COMPLETED',2,NULL,'2026-09-09 10:53:17','punch_mgr','2026-09-09 15:22:30'),(7,'WO-PL2609090001-01-P02-T002',2,2,97,100.00,'ACTIVE',1,NULL,'2026-09-09 10:53:24','assembly_mgr','2026-09-09 15:28:11'),(8,'WO-PL2609090001-01-P03-T002',3,3,96,100.00,'COMPLETED',2,NULL,'2026-09-09 10:53:37','punch_mgr','2026-09-09 15:23:17'),(9,'WO-PL2609090001-01-P04-T002',4,4,97,100.00,'ACTIVE',1,NULL,'2026-09-09 10:54:10','assembly_mgr','2026-09-09 15:28:16'),(10,'WO-PL2609090001-01-P05-T002',5,5,95,100.00,'ACTIVE',0,NULL,'2026-09-09 10:54:16',NULL,'2026-09-09 10:54:15'),(11,'WO-PL2609090001-01-P01-T003',1,6,100,100.00,'COMPLETED',2,NULL,'2026-09-09 10:59:35','punch_leader1','2026-09-09 15:22:12'),(12,'WO-PL2609090001-01-P03-T003',3,8,106,100.00,'COMPLETED',1,NULL,'2026-09-09 10:59:48','punch_mgr','2026-09-09 12:01:44'),(13,'WO-PL2609090001-01-P01-T004',1,11,114,100.00,'COMPLETED',1,NULL,'2026-09-09 11:01:30','punch_leader1','2026-09-09 15:21:09'),(14,'WO-PL2609090001-01-P02-T003',2,7,108,100.00,'COMPLETED',1,NULL,'2026-09-09 15:28:11','prod_manager','2026-09-09 16:54:44'),(15,'WO-PL2609090001-01-P04-T003',4,9,108,100.00,'COMPLETED',1,NULL,'2026-09-09 15:28:17','prod_manager','2026-09-09 16:54:46');
/*!40000 ALTER TABLE `production_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `production_task_event`
--

DROP TABLE IF EXISTS `production_task_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_task_event` (
  `event_id` bigint NOT NULL AUTO_INCREMENT COMMENT '事件ID',
  `task_id` bigint NOT NULL COMMENT '动作主任务ID（FK production_task）',
  `related_task_id` bigint DEFAULT NULL COMMENT '关联任务ID（ASSIGN=新child / RECALL=被收回child / RETURN=父任务；FIRST_ASSIGN/UNASSIGN=NULL）',
  `action` varchar(20) NOT NULL COMMENT 'FIRST_ASSIGN/ASSIGN/RECALL/RETURN/UNASSIGN',
  `operator_id` bigint NOT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人姓名',
  `from_assignee_id` bigint DEFAULT NULL COMMENT '动作前执行人ID',
  `to_assignee_id` bigint DEFAULT NULL COMMENT '动作后执行人ID',
  `quantity` decimal(14,2) NOT NULL COMMENT '本次流转数量',
  `before_task_quantity` decimal(14,2) NOT NULL COMMENT 'event.task_id 的 task_quantity 动作前值',
  `after_task_quantity` decimal(14,2) NOT NULL COMMENT 'event.task_id 的 task_quantity 动作后值',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  KEY `idx_event_task` (`task_id`),
  KEY `idx_event_related` (`related_task_id`),
  KEY `idx_event_action` (`action`),
  CONSTRAINT `fk_event_task` FOREIGN KEY (`task_id`) REFERENCES `production_task` (`task_id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生产任务流转事件（业务流水，非操作审计；树=当前状态，流水=为什么变成现在这样）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_task_event`
--

LOCK TABLES `production_task_event` WRITE;
/*!40000 ALTER TABLE `production_task_event` DISABLE KEYS */;
INSERT INTO `production_task_event` VALUES (1,1,6,'ASSIGN',94,'生产中心主任',94,96,100.00,100.00,100.00,NULL,'2026-09-09 10:53:17',NULL),(2,2,7,'ASSIGN',94,'生产中心主任',94,97,100.00,100.00,100.00,NULL,'2026-09-09 10:53:24',NULL),(3,3,8,'ASSIGN',94,'生产中心主任',94,96,100.00,100.00,100.00,NULL,'2026-09-09 10:53:37',NULL),(4,4,9,'ASSIGN',94,'生产中心主任',94,97,100.00,100.00,100.00,NULL,'2026-09-09 10:54:10',NULL),(5,5,10,'ASSIGN',94,'生产中心主任',94,95,100.00,100.00,100.00,NULL,'2026-09-09 10:54:16',NULL),(6,6,11,'ASSIGN',96,'冲型车间主任',96,100,100.00,100.00,100.00,NULL,'2026-09-09 10:59:35',NULL),(7,8,12,'ASSIGN',96,'冲型车间主任',96,106,100.00,100.00,100.00,NULL,'2026-09-09 10:59:48',NULL),(8,11,13,'ASSIGN',100,'冲型一组组长',100,114,100.00,100.00,100.00,NULL,'2026-09-09 11:01:30',NULL),(9,11,NULL,'COMPLETE',94,'生产中心主任',100,100,0.00,100.00,100.00,NULL,'2026-09-09 15:22:13',NULL),(10,6,NULL,'COMPLETE',94,'生产中心主任',96,96,0.00,100.00,100.00,NULL,'2026-09-09 15:22:30',NULL),(11,1,NULL,'COMPLETE',94,'生产中心主任',94,94,0.00,100.00,100.00,NULL,'2026-09-09 15:22:36',NULL),(12,8,NULL,'COMPLETE',96,'冲型车间主任',96,96,0.00,100.00,100.00,NULL,'2026-09-09 15:23:18',NULL),(13,3,NULL,'COMPLETE',94,'生产中心主任',94,94,0.00,100.00,100.00,NULL,'2026-09-09 15:23:45',NULL),(14,7,14,'ASSIGN',97,'组装车间主任',97,108,100.00,100.00,100.00,NULL,'2026-09-09 15:28:11',NULL),(15,9,15,'ASSIGN',97,'组装车间主任',97,108,100.00,100.00,100.00,NULL,'2026-09-09 15:28:17',NULL);
/*!40000 ALTER TABLE `production_task_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `production_work_report`
--

DROP TABLE IF EXISTS `production_work_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_work_report` (
  `report_id` bigint NOT NULL AUTO_INCREMENT COMMENT '报工ID',
  `report_no` varchar(50) DEFAULT NULL COMMENT '报工单号（业务编号，唯一）',
  `order_id` bigint NOT NULL COMMENT '生产订单ID(冗余引用，便于追溯查询)',
  `order_no` varchar(50) DEFAULT NULL COMMENT '工单编号(冗余)',
  `execution_id` bigint NOT NULL COMMENT '工序执行记录ID(生产事实主体)',
  `task_id` bigint DEFAULT NULL COMMENT '任务树节点ID(Task Tree 报工锚点；P2 起新报工必须绑定)',
  `reporter_id` bigint NOT NULL COMMENT '报工提交人ID(P2-C 默认须=ACTIVE assignee，库不强制)',
  `reporter_name` varchar(64) NOT NULL COMMENT '报工提交人姓名快照',
  `proxy_id` bigint DEFAULT NULL COMMENT '代操作人ID（空=本人报工）',
  `proxy_name` varchar(64) DEFAULT NULL COMMENT '代操作人姓名快照',
  `equipment_id` bigint DEFAULT NULL COMMENT '本次实际使用设备ID(可空=人工工序无设备)',
  `equipment_name` varchar(200) DEFAULT NULL COMMENT '本次实际使用设备名称(快照)',
  `qualified_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '本次合格数量',
  `defective_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '本次不良数量',
  `labor_hours` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '本次人工工时',
  `machine_hours` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '本次机器工时',
  `work_start_time` datetime DEFAULT NULL COMMENT '本次生产开始时间(可空)',
  `work_end_time` datetime DEFAULT NULL COMMENT '本次生产结束时间(可空；P2-C 校验 end>=start)',
  `report_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报工正式提交时间(Service 显式设置)',
  `defect_reason` varchar(500) DEFAULT NULL COMMENT '不良原因(P2 V1 单字段，P3 再做缺陷明细)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注(提交后不可变)',
  `report_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待审批/APPROVED已通过/REJECTED已驳回/CANCELLED已撤销',
  `pending_reviewer_id` bigint DEFAULT NULL COMMENT '提交时点应审批人ID（空=生产管理兜底）',
  `pending_reviewer_name` varchar(64) DEFAULT NULL COMMENT '提交时点应审批人姓名快照',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审批人ID（approve/reject 落库；一笔只审批一次）',
  `reviewer_name` varchar(64) DEFAULT NULL COMMENT '审批人姓名快照（历史事实）',
  `review_time` datetime DEFAULT NULL COMMENT '审批时间',
  `review_remark` varchar(500) DEFAULT NULL COMMENT '审批备注（驳回必填）',
  `cancelled_by` bigint DEFAULT NULL COMMENT '撤销人ID',
  `cancelled_by_name` varchar(64) DEFAULT NULL COMMENT '撤销人姓名',
  `cancelled_at` datetime DEFAULT NULL COMMENT '撤销时间',
  `cancel_reason` varchar(500) DEFAULT NULL COMMENT '撤销原因(P2-C 必填)',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`report_id`),
  UNIQUE KEY `uk_work_report_no` (`report_no`),
  KEY `idx_execution` (`execution_id`),
  KEY `idx_execution_status` (`execution_id`,`report_status`),
  KEY `idx_reporter_status` (`reporter_id`,`report_status`),
  KEY `idx_report_time` (`report_time`),
  KEY `idx_task_node` (`task_id`),
  CONSTRAINT `fk_work_report_task` FOREIGN KEY (`task_id`) REFERENCES `production_task` (`task_id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生产报工(一次不可覆盖的生产数量/工时事实)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_work_report`
--

LOCK TABLES `production_work_report` WRITE;
/*!40000 ALTER TABLE `production_work_report` DISABLE KEYS */;
INSERT INTO `production_work_report` VALUES (1,'WR-20260909-0001',2,'WO-PL2609090001-01',3,12,106,'冲型一组工人',NULL,NULL,NULL,NULL,100.0000,0.0000,0.03,0.00,'2026-09-09 11:55:43','2026-09-09 11:57:27','2026-09-09 11:57:42',NULL,NULL,'APPROVED',96,'冲型车间主任',96,'冲型车间主任','2026-09-09 12:01:44',NULL,NULL,NULL,NULL,NULL,'punch_op1','2026-09-09 11:57:42','punch_mgr','2026-09-09 12:01:44'),(2,'WR-20260909-0002',2,'WO-PL2609090001-01',1,13,114,'冲型一组工人B',NULL,NULL,NULL,NULL,100.0000,0.0000,3.29,0.00,'2026-09-09 12:03:11','2026-09-09 15:20:18','2026-09-09 15:20:26',NULL,NULL,'APPROVED',100,'冲型一组组长',100,'冲型一组组长','2026-09-09 15:21:09',NULL,NULL,NULL,NULL,NULL,'punch_op1b','2026-09-09 15:20:26','punch_leader1','2026-09-09 15:21:09'),(3,'WR-20260909-0003',2,'WO-PL2609090001-01',4,15,108,'组装一组工人',NULL,NULL,NULL,NULL,100.0000,0.0000,0.00,0.00,NULL,NULL,'2026-09-09 16:54:09',NULL,NULL,'APPROVED',97,'组装车间主任',94,'生产中心主任','2026-09-09 16:54:47',NULL,NULL,NULL,NULL,NULL,'assembly_op1','2026-09-09 16:54:09','prod_manager','2026-09-09 16:54:47'),(4,'WR-20260909-0004',2,'WO-PL2609090001-01',2,14,108,'组装一组工人',NULL,NULL,NULL,NULL,100.0000,0.0000,0.00,0.00,NULL,NULL,'2026-09-09 16:54:23',NULL,NULL,'APPROVED',97,'组装车间主任',94,'生产中心主任','2026-09-09 16:54:45',NULL,NULL,NULL,NULL,NULL,'assembly_op1','2026-09-09 16:54:23','prod_manager','2026-09-09 16:54:45');
/*!40000 ALTER TABLE `production_work_report` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-09 17:29:41
