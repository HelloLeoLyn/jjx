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
-- Table structure for table `production_order`
--

DROP TABLE IF EXISTS `production_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_order` (
  `order_id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '链路追踪ID',
  `order_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单编号',
  `order_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单类型：PLAN生产计划/WORK_ORDER生产工单',
  `parent_order_id` bigint DEFAULT NULL COMMENT '父订单ID（计划生成工单时使用）',
  `sales_order_id` bigint DEFAULT NULL COMMENT '销售订单ID',
  `sales_order_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '销售订单编号',
  `product_id` bigint NOT NULL COMMENT '产品ID',
  `product_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品编码',
  `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品名称',
  `product_spec` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产品规格',
  `product_unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PCS' COMMENT '产品单位',
  `bom_id` bigint DEFAULT NULL COMMENT '创建时使用的BOM ID',
  `bom_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建时使用的BOM编码',
  `routing_id` bigint DEFAULT NULL COMMENT '使用的工艺路线ID',
  `routing_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工艺路线编码',
  `planned_quantity` decimal(18,4) NOT NULL COMMENT '计划数量',
  `completed_quantity` decimal(18,4) DEFAULT '0.0000' COMMENT '已完成数量',
  `finished_quantity` decimal(18,4) DEFAULT '0.0000' COMMENT '成品完工数量（最后一道工序合格数，052口径）',
  `remaining_quantity` decimal(18,4) DEFAULT '0.0000' COMMENT '剩余数量',
  `plan_start_date` date NOT NULL COMMENT '计划开始日期',
  `plan_end_date` date NOT NULL COMMENT '计划结束日期',
  `actual_start_time` datetime DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_time` datetime DEFAULT NULL COMMENT '实际结束时间',
  `completed_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '完工操作人(053留痕)',
  `quality_inspection_id` bigint DEFAULT NULL COMMENT '关联完工质检单ID(053留痕)',
  `inbound_pending_flag` tinyint DEFAULT '0' COMMENT '入库待处理标记：0正常 1入库失败待重试(056)',
  `inbound_pending_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '入库失败原因(056)',
  `order_status` tinyint DEFAULT '0' COMMENT '订单状态: 0草稿/1待审核/2已审核/3已驳回/4已计划/5待开始/6进行中/7已暂停/8已完成/9已取消/10已关闭/11已超期',
  `approval_status` tinyint DEFAULT '0' COMMENT '审批状态: 0草稿/1待审批/2已批准/3已驳回/4已取消',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `approver_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人姓名',
  `approval_time` datetime DEFAULT NULL COMMENT '审批时间',
  `approval_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批备注',
  `priority` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '优先级：LOW低/MEDIUM中/HIGH高/URGENT紧急',
  `dispatch_team_id` bigint DEFAULT NULL COMMENT '负责班组(部门ID)',
  `dispatch_team_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责班组名称',
  `dispatch_leader_id` bigint DEFAULT NULL COMMENT '工单负责人(用户ID)',
  `dispatch_leader_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工单负责人姓名',
  `department_id` bigint DEFAULT NULL COMMENT '生产部门ID',
  `department_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生产部门名称',
  `material_cost` decimal(18,4) DEFAULT '0.0000' COMMENT '材料成本',
  `labor_cost` decimal(18,4) DEFAULT '0.0000' COMMENT '人工成本',
  `total_cost` decimal(18,4) DEFAULT '0.0000' COMMENT '总成本',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `material_status` tinyint DEFAULT '0' COMMENT '领料状态:0未领料/1待发料/2已领料',
  `rework_flag` tinyint DEFAULT '0' COMMENT '返工标记：0正常 1质检FAIL待返工(053)',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_order_type` (`order_type`),
  KEY `idx_order_status` (`order_status`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_plan_date` (`plan_start_date`,`plan_end_date`),
  KEY `idx_priority` (`priority`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_parent_order` (`parent_order_id`),
  KEY `idx_sales_order` (`sales_order_id`),
  KEY `idx_production_order_type_status` (`order_type`,`order_status`,`plan_start_date`),
  KEY `idx_production_order_priority_date` (`priority`,`plan_start_date`,`plan_end_date`),
  KEY `idx_production_order_department` (`department_id`,`order_status`,`create_time`),
  CONSTRAINT `fk_order_parent` FOREIGN KEY (`parent_order_id`) REFERENCES `production_order` (`order_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产订单表（合并计划和工单）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `production_order`
--

LOCK TABLES `production_order` WRITE;
/*!40000 ALTER TABLE `production_order` DISABLE KEYS */;
INSERT INTO `production_order` VALUES (1,'6c8a7e3b2f2948eb','PL2609090001','PLAN',NULL,2,'SO260909001',1,'JST001MEML','JST001MEML','','PCS',1,NULL,1,NULL,100.0000,0.0000,0.0000,0.0000,'2026-09-09','2026-10-07',NULL,NULL,NULL,NULL,0,NULL,10,0,NULL,NULL,NULL,NULL,'MEDIUM',NULL,NULL,NULL,NULL,NULL,NULL,0.0000,0.0000,0.0000,'admin','2026-09-09 10:25:24','admin','2026-09-09 10:25:23','销售订单生成计划，自动提交审批',0,0),(2,'6c8a7e3b2f2948eb','WO-PL2609090001-01','WORK_ORDER',1,2,'SO260909001',1,'JST001MEML','JST001MEML','','PCS',NULL,NULL,1,NULL,100.0000,500.0000,50.0000,-400.0000,'2026-09-09','2026-10-07','2026-09-09 11:55:34','2026-09-09 18:11:19','prod_manager',NULL,0,NULL,8,0,NULL,NULL,NULL,NULL,'medium',NULL,NULL,NULL,NULL,NULL,NULL,0.0000,0.0000,0.0000,'admin','2026-09-09 10:26:07','admin','2026-09-09 10:26:07','',1,0);
/*!40000 ALTER TABLE `production_order` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-09 18:19:29
