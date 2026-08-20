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
-- Table structure for table `production_dispatch`
--

DROP TABLE IF EXISTS `production_dispatch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_dispatch` (
  `dispatch_id` bigint NOT NULL AUTO_INCREMENT COMMENT '派工单ID',
  `order_id` bigint NOT NULL COMMENT '生产订单ID',
  `order_no` varchar(50) DEFAULT NULL COMMENT '工单编号(冗余)',
  `execution_id` bigint NOT NULL COMMENT '工序执行记录ID(production_operation_execution)',
  `process_name` varchar(200) DEFAULT NULL COMMENT '工序名称(冗余)',
  `process_order` int DEFAULT NULL COMMENT '工序顺序(冗余)',
  `team_id` bigint DEFAULT NULL COMMENT '责任班组(部门ID)',
  `team_name` varchar(100) DEFAULT NULL COMMENT '责任班组名称',
  `equipment_id` bigint DEFAULT NULL COMMENT '设备ID(空=不限)',
  `equipment_name` varchar(200) DEFAULT NULL COMMENT '设备名称',
  `operators` varchar(500) DEFAULT NULL COMMENT '执行人(JSON数组 [{userId,userName}])',
  `assigned_by` bigint DEFAULT NULL COMMENT '派工主管(用户ID)',
  `assigned_by_name` varchar(64) DEFAULT NULL COMMENT '派工主管姓名',
  `assign_time` datetime DEFAULT NULL COMMENT '最近指派时间',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0待派工 1已派工 2执行中 3已完成 4已退回（静态枚举）',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '退回原因(退回时必填)',
  `re_dispatch_count` int NOT NULL DEFAULT '0' COMMENT '改派次数',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志 0正常 1删除',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`dispatch_id`),
  UNIQUE KEY `uk_execution` (`execution_id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_team_status` (`team_id`,`status`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=99992 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工序派工单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_dispatch`
--

LOCK TABLES `production_dispatch` WRITE;
/*!40000 ALTER TABLE `production_dispatch` DISABLE KEYS */;
INSERT INTO `production_dispatch` VALUES (1,2,'WO-PL2608190001-01',1,'面板冲孔',1,6,'印刷车间',NULL,NULL,'[{\"userId\":96,\"userName\":\"冲型车间主任\",\"level\":1}]',94,'prod_manager','2026-08-19 12:25:33',1,'面板冲孔',1,'','0','prod_manager','2026-08-19 11:57:33','prod_manager','2026-08-19 11:57:33'),(2,2,'WO-PL2608190001-01',2,'面板保护膜',2,9,'冲型车间',NULL,NULL,'[{\"userId\":96,\"userName\":\"冲型车间主任\",\"level\":1}]',94,'prod_manager','2026-08-19 11:57:55',2,NULL,0,'','0','prod_manager','2026-08-19 11:57:55',NULL,'2026-08-19 11:57:55'),(3,2,'WO-PL2608190001-01',3,NULL,3,6,'印刷车间',NULL,NULL,'[{\"userId\":98,\"userName\":\"印刷一组组长\",\"level\":1},{\"userId\":104,\"userName\":\"印刷一组工人\",\"level\":1}]',95,'print_mgr','2026-08-19 12:26:58',1,NULL,1,'','0','prod_manager','2026-08-19 11:58:09','print_mgr','2026-08-19 11:58:08');
/*!40000 ALTER TABLE `production_dispatch` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `production_dispatch_node`
--

DROP TABLE IF EXISTS `production_dispatch_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_dispatch_node` (
  `node_id` bigint NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `dispatch_id` bigint NOT NULL COMMENT '派工单ID(production_dispatch.dispatch_id)',
  `parent_node_id` bigint DEFAULT NULL COMMENT '上级节点ID(第1级=NULL，表示源头主管直派；责任来源节点)',
  `assignee_type` varchar(20) NOT NULL DEFAULT 'USER' COMMENT '责任主体类型：USER(P1第一版仅支持)',
  `assignee_id` bigint NOT NULL COMMENT '责任主体ID(用户ID)',
  `assignee_name` varchar(64) NOT NULL COMMENT '责任主体姓名快照(改昵称不影响历史)',
  `org_id` bigint DEFAULT NULL COMMENT '责任主体当时所属组织ID快照',
  `org_name` varchar(100) DEFAULT NULL COMMENT '责任主体当时所属组织名称快照',
  `org_path` varchar(500) DEFAULT NULL COMMENT '责任主体当时所属组织祖先路径快照(如"1/5/6/7")',
  `node_status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '节点状态：ACTIVE/DELEGATED/REASSIGNED/RETURNED/COMPLETED/CANCELLED',
  `assigned_by` bigint DEFAULT NULL COMMENT '本次责任由谁指派(用户ID)',
  `assigned_by_name` varchar(64) DEFAULT NULL COMMENT '指派人姓名快照',
  `assigned_at` datetime DEFAULT NULL COMMENT '本次责任正式生效时间',
  `closed_at` datetime DEFAULT NULL COMMENT '本次责任周期结束时间(流转走/完成/取消)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注/退回原因/迁移说明(LEGACY_BACKFILL)',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `active_guard` tinyint GENERATED ALWAYS AS ((case when (`node_status` = _utf8mb4'ACTIVE') then 1 else NULL end)) STORED COMMENT '唯一ACTIVE守卫列(ACTIVE→1，其他→NULL；DB生成，Java不写)',
  PRIMARY KEY (`node_id`),
  UNIQUE KEY `uk_dispatch_active` (`dispatch_id`,`active_guard`),
  KEY `idx_dispatch` (`dispatch_id`),
  KEY `idx_assignee_status` (`assignee_id`,`node_status`),
  KEY `idx_parent` (`parent_node_id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='派工责任链节点(责任持有实例)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_dispatch_node`
--

LOCK TABLES `production_dispatch_node` WRITE;
/*!40000 ALTER TABLE `production_dispatch_node` DISABLE KEYS */;
/*!40000 ALTER TABLE `production_dispatch_node` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `production_dispatch_log`
--

DROP TABLE IF EXISTS `production_dispatch_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_dispatch_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `dispatch_id` bigint NOT NULL COMMENT '派工单ID',
  `order_id` bigint DEFAULT NULL COMMENT '工单ID(冗余)',
  `action` varchar(20) NOT NULL COMMENT '操作：ASSIGN指派/REASSIGN改派/REJECT退回/START开始/COMPLETE完成',
  `content` varchar(1000) DEFAULT NULL COMMENT '变更内容（如：由生产一组改派给生产二组，设备由3#印刷机改为5#印刷机）',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) DEFAULT NULL COMMENT '操作人姓名',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_dispatch` (`dispatch_id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='派工操作流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_dispatch_log`
--

LOCK TABLES `production_dispatch_log` WRITE;
/*!40000 ALTER TABLE `production_dispatch_log` DISABLE KEYS */;
INSERT INTO `production_dispatch_log` VALUES (1,1,2,'ASSIGN','指派：班组=印刷车间，执行人=印刷车间主任，第1级执行人，主管：prod_manager',94,'prod_manager','2026-08-19 11:57:33'),(2,2,2,'ASSIGN','指派：班组=冲型车间，执行人=冲型车间主任，第1级执行人，主管：prod_manager',94,'prod_manager','2026-08-19 11:57:55'),(3,3,2,'ASSIGN','指派：班组=印刷车间，执行人=印刷车间主任，第1级执行人，主管：prod_manager',94,'prod_manager','2026-08-19 11:58:09'),(4,1,2,'REJECT','退回：面板冲孔',94,'prod_manager','2026-08-19 11:58:44'),(5,1,2,'REASSIGN','第1级执行人：冲型车间主任，班组=印刷车间，执行人=印刷车间主任 → 班组=印刷车间，执行人=冲型车间主任',94,'prod_manager','2026-08-19 12:25:33'),(6,3,2,'REASSIGN','第1级执行人：印刷一组组长、印刷一组工人，班组=印刷车间，执行人=印刷车间主任 → 班组=印刷车间，执行人=印刷一组组长、印刷一组工人',95,'print_mgr','2026-08-19 12:26:58');
/*!40000 ALTER TABLE `production_dispatch_log` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-19 17:01:14
