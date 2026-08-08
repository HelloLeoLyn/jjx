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
-- Table structure for table `sales_inquiry`
--

DROP TABLE IF EXISTS `sales_inquiry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_inquiry` (
  `inquiry_id` bigint NOT NULL AUTO_INCREMENT COMMENT '询价单ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪ID',
  `inquiry_no` varchar(50) NOT NULL COMMENT '询价单编号',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(200) NOT NULL COMMENT '客户名称',
  `contact_person` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `inquiry_date` date NOT NULL COMMENT '询价日期',
  `expected_quantity` int DEFAULT NULL COMMENT '预估数量',
  `product_description` text COMMENT '产品描述/规格要求',
  `product_id` bigint DEFAULT NULL COMMENT '关联产品ID(标准品)',
  `key_count` int DEFAULT NULL COMMENT '按键数量',
  `size_description` varchar(200) DEFAULT NULL COMMENT '尺寸要求',
  `material_requirements` text COMMENT '材料要求',
  `circuit_requirements` text COMMENT '线路要求',
  `connector_requirements` text COMMENT '连接器要求',
  `special_requirements` text COMMENT '特殊要求',
  `has_drawing` tinyint(1) DEFAULT '0' COMMENT '是否有图纸文件',
  `inquiry_status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0草稿/1待处理/2已发送/3已转报价/4已确认/5已拒绝',
  `inquiry_type` tinyint DEFAULT '1' COMMENT '询价类型: 1标准品 2样品',
  `converted_quotation_id` bigint DEFAULT NULL COMMENT '转报价单ID',
  `convert_time` datetime DEFAULT NULL COMMENT '转换时间',
  `remark` text COMMENT '备注',
  `sales_person_id` bigint DEFAULT NULL COMMENT '销售负责人ID',
  `sales_person_name` varchar(100) DEFAULT NULL COMMENT '销售负责人姓名',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 0正常 1删除',
  PRIMARY KEY (`inquiry_id`),
  UNIQUE KEY `inquiry_no` (`inquiry_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_inquiry_status` (`inquiry_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售询价单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_inquiry`
--

LOCK TABLES `sales_inquiry` WRITE;
/*!40000 ALTER TABLE `sales_inquiry` DISABLE KEYS */;
INSERT INTO `sales_inquiry` VALUES (1,'e1a93415f2ad4427','INQ2608070001',1,'江苏盛泰科技有限公司','张伟','13812345678','2026-08-07',10,'产品描述有没有链路显示',NULL,10,'100','','','','特殊要求有没有链路显示',1,3,2,1,'2026-08-07 12:01:28','备注有没有链路显示',1,'admin','admin','2026-08-07 12:00:29','admin','2026-08-07 12:01:03',0),(2,'2b75d191617e4973','INQ2608070002',3,'AD科技有限公司','John Smith','13105551234','2026-08-07',8,'',NULL,8,'80','','','','',1,3,2,2,'2026-08-07 18:44:01','',1,'admin','admin','2026-08-07 18:43:59','admin','2026-08-07 18:43:59',0);
/*!40000 ALTER TABLE `sales_inquiry` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_quotation`
--

DROP TABLE IF EXISTS `sales_quotation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_quotation` (
  `quotation_id` bigint NOT NULL AUTO_INCREMENT COMMENT '报价单ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪ID',
  `quotation_no` varchar(50) NOT NULL COMMENT '报价单编号',
  `quotation_type` tinyint DEFAULT '1' COMMENT '报价单类型: 1标准品 2样品',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(200) NOT NULL COMMENT '客户名称',
  `contact_person` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `quotation_date` date NOT NULL COMMENT '报价日期',
  `valid_until` date NOT NULL COMMENT '有效期至',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '币种',
  `exchange_rate` decimal(10,4) DEFAULT '1.0000' COMMENT '汇率',
  `quotation_status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0草稿/1已发送/2已确认/3已拒绝/4已过期',
  `subtotal_amount` decimal(15,2) DEFAULT '0.00' COMMENT '小计金额',
  `tax_rate` decimal(5,2) DEFAULT '0.00' COMMENT '税率',
  `tax_amount` decimal(15,2) DEFAULT '0.00' COMMENT '税额',
  `total_amount` decimal(15,2) DEFAULT '0.00' COMMENT '总金额',
  `discount_amount` decimal(15,2) DEFAULT '0.00' COMMENT '折扣金额',
  `final_amount` decimal(15,2) DEFAULT '0.00' COMMENT '最终金额',
  `remark` text COMMENT '备注',
  `sales_person_id` bigint DEFAULT NULL COMMENT '销售员ID',
  `sales_person_name` varchar(100) DEFAULT NULL COMMENT '销售员姓名',
  `approver_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `approver_name` varchar(100) DEFAULT NULL COMMENT '审核人姓名',
  `approve_time` datetime DEFAULT NULL COMMENT '审核时间',
  `approve_remark` text COMMENT '审核备注',
  `send_time` datetime DEFAULT NULL COMMENT '发送时间',
  `send_method` varchar(50) DEFAULT NULL COMMENT '发送方式',
  `send_remark` text COMMENT '发送备注',
  `converted_order_id` bigint DEFAULT NULL COMMENT '转为订单ID',
  `convert_time` datetime DEFAULT NULL COMMENT '转为订单时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 (0: 正常, 1: 删除)',
  PRIMARY KEY (`quotation_id`),
  UNIQUE KEY `uk_quotation_no` (`quotation_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_quotation_date` (`quotation_date`),
  KEY `idx_quotation_status` (`quotation_status`),
  KEY `idx_sales_person_id` (`sales_person_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售报价表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_quotation`
--

LOCK TABLES `sales_quotation` WRITE;
/*!40000 ALTER TABLE `sales_quotation` DISABLE KEYS */;
INSERT INTO `sales_quotation` VALUES (1,'e1a93415f2ad4427','QT2608070001',2,1,'江苏盛泰科技有限公司','张伟','13812345678','2026-08-07','2026-09-06','CNY',1.0000,9,500.00,0.00,0.00,500.00,0.00,500.00,'由询价单[INQ2608070001]自动创建',1,'admin',33,'JJX','2026-08-07 17:28:10','','2026-08-08 11:08:41','email',NULL,NULL,'2026-08-08 11:52:12','admin','2026-08-07 12:01:28','sales01','2026-08-08 11:09:44',0),(2,'2b75d191617e4973','QT2608070002',2,3,'AD科技有限公司','John Smith','13105551234','2026-08-07','2026-09-06','CNY',1.0000,9,264.00,0.00,0.00,264.00,0.00,264.00,'由询价单[INQ2608070002]自动创建',1,'admin',1,'admin','2026-08-07 18:44:50','','2026-08-08 10:27:33','email',NULL,1,'2026-08-08 10:27:43','admin','2026-08-07 18:44:01','sales01','2026-08-08 10:27:43',0);
/*!40000 ALTER TABLE `sales_quotation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_quotation_item`
--

DROP TABLE IF EXISTS `sales_quotation_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_quotation_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `quotation_id` bigint NOT NULL COMMENT '报价单ID',
  `product_id` bigint DEFAULT NULL COMMENT '产品ID',
  `product_code` varchar(50) DEFAULT NULL COMMENT '产品编码',
  `product_name` varchar(200) DEFAULT NULL COMMENT '产品名称',
  `key_count` int DEFAULT NULL COMMENT '按键数量',
  `width` decimal(10,2) DEFAULT NULL COMMENT '宽度mm',
  `height` decimal(10,2) DEFAULT NULL COMMENT '高度mm',
  `thickness` decimal(10,2) DEFAULT NULL COMMENT '厚度mm',
  `material_type` varchar(50) DEFAULT NULL COMMENT '材料类型',
  `color` varchar(50) DEFAULT NULL COMMENT '颜色',
  `circuit_type` varchar(50) DEFAULT NULL COMMENT '线路类型',
  `connector_type` varchar(50) DEFAULT NULL COMMENT '连接器类型',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `unit_price` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '单价',
  `unit` varchar(20) DEFAULT 'PCS' COMMENT '单位',
  `amount` decimal(12,2) DEFAULT '0.00' COMMENT '金额',
  `delivery_days` int DEFAULT NULL COMMENT '交期天数',
  `estimated_delivery_date` date DEFAULT NULL COMMENT '预计交期',
  `custom_requirements` varchar(500) DEFAULT NULL COMMENT '自定义要求',
  `logo_requirement` varchar(500) DEFAULT NULL COMMENT 'Logo要求',
  `certification_requirement` varchar(500) DEFAULT NULL COMMENT '认证要求',
  `item_order` int DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`item_id`),
  KEY `idx_quotation_id` (`quotation_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报价单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_quotation_item`
--

LOCK TABLES `sales_quotation_item` WRITE;
/*!40000 ALTER TABLE `sales_quotation_item` DISABLE KEYS */;
INSERT INTO `sales_quotation_item` VALUES (3,1,NULL,'jst001','产品描述有没有链路显示',10,NULL,NULL,NULL,NULL,NULL,NULL,NULL,10,50.00,'PCS',500.00,NULL,NULL,'尺寸要求：100；特殊要求：特殊要求有没有链路显示',NULL,NULL,1,'2026-08-07 12:01:28','2026-08-07 12:01:28'),(5,2,NULL,'ad001','QT2608070002',8,NULL,NULL,NULL,NULL,NULL,NULL,NULL,8,33.00,'PCS',264.00,NULL,NULL,'尺寸要求：80',NULL,NULL,1,'2026-08-07 18:44:01','2026-08-07 18:44:01');
/*!40000 ALTER TABLE `sales_quotation_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_quotation_flow`
--

DROP TABLE IF EXISTS `sales_quotation_flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_quotation_flow` (
  `flow_id` bigint NOT NULL AUTO_INCREMENT COMMENT '流转记录ID',
  `quotation_id` bigint NOT NULL COMMENT '报价单ID',
  `action_code` varchar(50) NOT NULL COMMENT '动作编码: SUBMIT_REVIEW/APPROVE/REJECT/SEND/CUSTOMER_CONFIRM/CUSTOMER_REJECT',
  `action_name` varchar(50) NOT NULL COMMENT '动作名称',
  `from_status` int DEFAULT NULL COMMENT '流转前状态',
  `to_status` int DEFAULT NULL COMMENT '流转后状态',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(100) DEFAULT NULL COMMENT '操作人姓名',
  `remark` varchar(1000) DEFAULT NULL COMMENT '流转说明/审核意见',
  `attachment_ids` varchar(500) DEFAULT NULL COMMENT '附件ID列表(JSON数组)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`flow_id`),
  KEY `idx_quotation` (`quotation_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报价单状态流转记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_quotation_flow`
--

LOCK TABLES `sales_quotation_flow` WRITE;
/*!40000 ALTER TABLE `sales_quotation_flow` DISABLE KEYS */;
INSERT INTO `sales_quotation_flow` VALUES (1,1,'SUBMIT_REVIEW','提交审核',0,5,NULL,'系统',NULL,NULL,'2026-08-07 16:24:37'),(2,1,'REJECT','审核驳回',5,3,NULL,'系统','超管权限测试','2','2026-08-07 17:12:34'),(3,1,'STATUS_CHANGE','状态变更',3,0,NULL,'系统',NULL,NULL,'2026-08-07 17:12:43'),(4,1,'SUBMIT_REVIEW','提交审核',0,5,NULL,'系统',NULL,NULL,'2026-08-07 17:27:34'),(5,1,'APPROVE','审核通过',5,6,NULL,'系统','',NULL,'2026-08-07 17:28:10'),(6,2,'SUBMIT_REVIEW','提交审核',0,5,NULL,'系统',NULL,NULL,'2026-08-07 18:44:44'),(7,2,'APPROVE','审核通过',5,6,NULL,'系统','',NULL,'2026-08-07 18:44:50'),(8,2,'SEND','发送报价',6,1,NULL,'系统',NULL,NULL,'2026-08-08 10:27:33'),(9,2,'CUSTOMER_CONFIRM','客户确认报价',1,2,NULL,'系统',NULL,NULL,'2026-08-08 10:27:40'),(10,1,'SEND','发送报价',6,1,NULL,'系统',NULL,NULL,'2026-08-08 11:08:41'),(11,1,'CUSTOMER_CONFIRM','客户确认报价',1,2,NULL,'系统',NULL,'4','2026-08-08 11:09:05');
/*!40000 ALTER TABLE `sales_quotation_flow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_order`
--

DROP TABLE IF EXISTS `sales_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_order` (
  `order_id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪ID',
  `order_no` varchar(50) NOT NULL COMMENT '订单编号',
  `quotation_id` bigint DEFAULT NULL COMMENT '报价单ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(200) NOT NULL COMMENT '客户名称',
  `contact_person` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `order_date` date NOT NULL COMMENT '订单日期',
  `delivery_date` date DEFAULT NULL COMMENT '客户要求交货日期',
  `order_type` tinyint NOT NULL DEFAULT '1' COMMENT '订单类型: 1标准订单,2样品订单',
  `order_status` tinyint NOT NULL DEFAULT '1' COMMENT '订单状态: 1草稿,2已确认,3部分发货,4已发货,5部分完成,6已完成,7已取消',
  `prod_status` tinyint NOT NULL DEFAULT '1' COMMENT '生产状态: 1无生产,2部分生产中,3全部生产中,4生产完成',
  `is_urgent` tinyint NOT NULL DEFAULT '0' COMMENT '是否急单: 0否,1是',
  `urgent_reason` varchar(200) DEFAULT NULL COMMENT '加急原因',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '币种',
  `exchange_rate` decimal(10,4) DEFAULT '1.0000' COMMENT '汇率',
  `payment_terms` varchar(500) DEFAULT NULL COMMENT '付款条件',
  `delivery_terms` varchar(500) DEFAULT NULL COMMENT '交货条件',
  `delivery_address` varchar(500) DEFAULT NULL COMMENT '交货地址',
  `total_amount` decimal(15,2) DEFAULT '0.00' COMMENT '总金额',
  `tax_rate` decimal(5,2) DEFAULT '0.00' COMMENT '税率',
  `tax_amount` decimal(15,2) DEFAULT '0.00' COMMENT '税额',
  `total_amount_with_tax` decimal(15,2) DEFAULT '0.00' COMMENT '含税总金额',
  `discount_rate` decimal(5,2) DEFAULT '0.00' COMMENT '折扣率',
  `discount_amount` decimal(15,2) DEFAULT '0.00' COMMENT '折扣金额',
  `final_amount` decimal(15,2) DEFAULT '0.00' COMMENT '最终金额',
  `payment_status` tinyint NOT NULL DEFAULT '1' COMMENT '支付状态: 1未支付,2支付中,3已支付,4部分支付,5已退款',
  `paid_amount` decimal(15,2) DEFAULT '0.00' COMMENT '已付金额',
  `unpaid_amount` decimal(15,2) DEFAULT '0.00' COMMENT '未付金额',
  `total_quantity` int DEFAULT '0' COMMENT '总数量',
  `shipped_quantity` int DEFAULT '0' COMMENT '已发货数量',
  `produced_quantity` int DEFAULT '0' COMMENT '已生产数量',
  `sales_manager_id` bigint DEFAULT NULL COMMENT '销售负责人ID',
  `sales_manager_name` varchar(100) DEFAULT NULL COMMENT '销售负责人姓名',
  `remark` text COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志: 0正常,1删除',
  `sample_status` tinyint DEFAULT '0' COMMENT '样品单状态: 1=已创建,2=待审核,3=工程打样中,4=样品待送样,5=已送样待确认,6=样品确认,7=已转量产,8=已关闭',
  `sample_round` int DEFAULT '0' COMMENT '样品迭代轮次',
  `sample_qty` int DEFAULT NULL COMMENT '打样数量',
  `engineering_note` text COMMENT '工程备注',
  `engineering_acceptor` varchar(50) DEFAULT NULL COMMENT '工程接单人',
  `engineering_accept_time` datetime DEFAULT NULL COMMENT '工程接单时间',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '工程拒单原因',
  `current_process` varchar(50) DEFAULT NULL COMMENT '打样当前工序',
  `sample_cost` decimal(12,2) DEFAULT '0.00' COMMENT '打样成本',
  `sample_work_hours` decimal(8,2) DEFAULT '0.00' COMMENT '打样工时(小时)',
  `sample_tracking_no` varchar(100) DEFAULT NULL COMMENT '送样快递单号',
  `sample_send_date` datetime DEFAULT NULL COMMENT '送样日期',
  `sample_confirm_date` datetime DEFAULT NULL COMMENT '客户确认日期',
  `confirm_by` varchar(50) DEFAULT NULL COMMENT '客户确认人',
  `confirm_method` varchar(20) DEFAULT NULL COMMENT '确认方式',
  `confirm_time` datetime DEFAULT NULL COMMENT '确认时间',
  `sample_client_name` varchar(100) DEFAULT NULL COMMENT '客户方确认人',
  `converted_order_id` bigint DEFAULT NULL COMMENT '转量产后的标准订单ID',
  `convert_order_time` datetime DEFAULT NULL COMMENT '转量产时间',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `order_no` (`order_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_order_date` (`order_date`),
  KEY `idx_order_status` (`order_status`),
  KEY `idx_prod_status` (`prod_status`),
  KEY `idx_urgent` (`is_urgent`,`order_status`),
  KEY `idx_payment_status` (`payment_status`),
  KEY `idx_sample_status` (`sample_status`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售订单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_order`
--

LOCK TABLES `sales_order` WRITE;
/*!40000 ALTER TABLE `sales_order` DISABLE KEYS */;
INSERT INTO `sales_order` VALUES (1,'2b75d191617e4973','SP2608080001',2,3,'AD科技有限公司','John Smith','13105551234','2026-08-08','2026-09-06',2,1,1,0,NULL,'CNY',1.0000,NULL,NULL,NULL,264.00,0.00,0.00,0.00,0.00,0.00,264.00,1,0.00,0.00,10,0,0,1,'admin','ces','sales01','2026-08-08 10:27:43','sales01','2026-08-08 11:46:33',0,9,2,10,NULL,'工程','2026-08-08 11:44:30',NULL,'SMT贴片',0.00,0.00,'','2026-08-08 11:46:10',NULL,NULL,NULL,NULL,NULL,NULL,NULL),(2,'e1a93415f2ad4427','SP2608080002',1,1,'江苏盛泰科技有限公司','张伟','13812345678','2026-08-08','2026-09-06',2,1,1,0,NULL,'CNY',1.0000,NULL,NULL,NULL,500.00,0.00,0.00,0.00,0.00,0.00,500.00,1,0.00,0.00,10,0,0,1,'admin','','sales01','2026-08-08 11:09:44','sales01','2026-08-08 12:04:27',0,6,1,10,NULL,'工程','2026-08-08 11:32:09',NULL,'贴合',0.00,0.00,'','2026-08-08 11:46:06','2026-08-08 11:46:23',NULL,NULL,NULL,'客户确认',NULL,NULL);
/*!40000 ALTER TABLE `sales_order` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_order_product`
--

DROP TABLE IF EXISTS `sales_order_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_order_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `quantity` int DEFAULT NULL COMMENT '产品数量',
  `amount` double NOT NULL COMMENT '产品金额',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `product_id` bigint DEFAULT NULL COMMENT '产品ID（样品单可为空）',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位',
  `unit_price` double DEFAULT NULL COMMENT '单价',
  `product_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产品编码',
  `product_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产品名称',
  `remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `specification` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '规格描述',
  `customer_material_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '客户物料号',
  `line_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '行备注',
  PRIMARY KEY (`id`),
  KEY `sales_order_product_product_code_index` (`product_code`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单产品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_order_product`
--

LOCK TABLES `sales_order_product` WRITE;
/*!40000 ALTER TABLE `sales_order_product` DISABLE KEYS */;
INSERT INTO `sales_order_product` VALUES (1,8,264,1,NULL,'PCS',33,'ad001','QT2608070002',NULL,'尺寸要求：80','',''),(2,10,500,2,1,'PCS',50,'jst001','产品描述有没有链路显示',NULL,'尺寸要求：100；特殊要求：特殊要求有没有链路显示','','');
/*!40000 ALTER TABLE `sales_order_product` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_order_review`
--

DROP TABLE IF EXISTS `sales_order_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_order_review` (
  `review_id` bigint NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(50) DEFAULT NULL COMMENT '订单编号',
  `review_type` tinyint DEFAULT '1' COMMENT '审核类型 (1: 订单审核, 2: 价格审核, 3: 技术审核)',
  `review_stage` tinyint DEFAULT '1' COMMENT '审核阶段',
  `stage_name` varchar(100) DEFAULT NULL COMMENT '审核阶段名称',
  `previous_status` tinyint DEFAULT NULL COMMENT '审核前状态',
  `current_status` tinyint DEFAULT NULL COMMENT '审核后状态',
  `reviewer_role` varchar(100) DEFAULT '' COMMENT '审核人角色',
  `review_status` tinyint DEFAULT '1' COMMENT '审核状态 (1: 待审核, 2: 审核通过, 3: 审核驳回)',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `reviewer_name` varchar(100) DEFAULT NULL COMMENT '审核人姓名',
  `review_result` tinyint DEFAULT NULL COMMENT '审核结果',
  `result_description` varchar(500) DEFAULT NULL COMMENT '审核结果描述',
  `review_comment` text COMMENT '审核意见',
  `attachments` text COMMENT '审核附件',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `review_duration` int DEFAULT NULL COMMENT '审核耗时(分钟)',
  `review_remark` text COMMENT '审核备注',
  `next_reviewer_id` bigint DEFAULT NULL COMMENT '下一审核人ID',
  `next_reviewer_name` varchar(100) DEFAULT NULL COMMENT '下一审核人姓名',
  `next_handler_id` bigint DEFAULT NULL COMMENT '下一处理人ID',
  `next_handler_name` varchar(100) DEFAULT NULL COMMENT '下一处理人姓名',
  `is_final_review` tinyint(1) DEFAULT NULL COMMENT '是否最终审核',
  `review_process_id` varchar(64) DEFAULT NULL COMMENT '审核流程ID',
  `node_sequence` int DEFAULT NULL COMMENT '审核节点序号',
  `node_name` varchar(100) DEFAULT NULL COMMENT '审核节点名称',
  `review_requirements` text COMMENT '审核要求',
  `review_criteria` text COMMENT '审核标准',
  `review_score` int DEFAULT NULL COMMENT '审核得分',
  `risk_level` tinyint DEFAULT NULL COMMENT '风险等级',
  `risk_description` text COMMENT '风险描述',
  `improvement_suggestions` text COMMENT '改进建议',
  `notify_customer` tinyint(1) DEFAULT NULL COMMENT '是否通知客户',
  `notification_method` varchar(100) DEFAULT NULL COMMENT '通知方式',
  `customer_feedback` text COMMENT '客户反馈',
  `is_urgent` tinyint(1) DEFAULT NULL COMMENT '是否紧急',
  `urgent_reason` varchar(500) DEFAULT NULL COMMENT '紧急原因',
  `review_version` int DEFAULT NULL COMMENT '审核版本号',
  `related_business_id` bigint DEFAULT NULL COMMENT '关联业务ID',
  `related_business_type` varchar(50) DEFAULT NULL COMMENT '关联业务类型',
  `review_process_type` tinyint DEFAULT NULL COMMENT '审核流程类型',
  `process_status` tinyint DEFAULT NULL COMMENT '流程状态',
  `process_start_time` datetime DEFAULT NULL COMMENT '流程开始时间',
  `process_end_time` datetime DEFAULT NULL COMMENT '流程结束时间',
  `process_timeout_time` datetime DEFAULT NULL COMMENT '流程超时时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 (0: 正常, 1: 删除)',
  PRIMARY KEY (`review_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_review_status` (`review_status`),
  KEY `idx_reviewer_id` (`reviewer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单审核记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_order_review`
--

LOCK TABLES `sales_order_review` WRITE;
/*!40000 ALTER TABLE `sales_order_review` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_order_review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_order_stock_reserve`
--

DROP TABLE IF EXISTS `sales_order_stock_reserve`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_order_stock_reserve` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '销售订单ID',
  `order_no` varchar(50) DEFAULT NULL COMMENT '订单号',
  `product_id` bigint DEFAULT NULL COMMENT '产品ID',
  `material_id` bigint NOT NULL COMMENT '成品物料ID',
  `material_code` varchar(50) DEFAULT NULL COMMENT '物料编码',
  `material_name` varchar(200) DEFAULT NULL COMMENT '物料名称',
  `reserve_quantity` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '预留数量',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0=有效 1=已释放',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_material` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售订单成品库存预留表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_order_stock_reserve`
--

LOCK TABLES `sales_order_stock_reserve` WRITE;
/*!40000 ALTER TABLE `sales_order_stock_reserve` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_order_stock_reserve` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_sample_bom`
--

DROP TABLE IF EXISTS `sales_sample_bom`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_sample_bom` (
  `bom_id` bigint NOT NULL AUTO_INCREMENT COMMENT '打样BOM记录ID',
  `order_id` bigint NOT NULL COMMENT '样品单ID(sales_order.order_id)',
  `round_no` int DEFAULT '1' COMMENT '打样轮次',
  `layer_name` varchar(50) NOT NULL COMMENT '层结构(面板/线路/间隔/背胶/其他)',
  `material_name` varchar(200) NOT NULL COMMENT '物料名称',
  `specification` varchar(500) DEFAULT NULL COMMENT '规格',
  `quantity` decimal(12,4) NOT NULL DEFAULT '1.0000' COMMENT '用量',
  `unit` varchar(20) DEFAULT 'PCS' COMMENT '单位',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(50) DEFAULT NULL COMMENT '录入人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`bom_id`),
  KEY `idx_order` (`order_id`,`round_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打样BOM物料清单(结构化)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_sample_bom`
--

LOCK TABLES `sales_sample_bom` WRITE;
/*!40000 ALTER TABLE `sales_sample_bom` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_sample_bom` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_sample_process`
--

DROP TABLE IF EXISTS `sales_sample_process`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_sample_process` (
  `process_id` bigint NOT NULL AUTO_INCREMENT COMMENT '工序记录ID',
  `order_id` bigint NOT NULL COMMENT '样品单ID(sales_order.order_id)',
  `round_no` int DEFAULT '1' COMMENT '打样轮次',
  `process_name` varchar(100) NOT NULL COMMENT '工序名称',
  `materials` text COMMENT '该工序材料明细(JSON: [{name,spec,qty,unit}])',
  `process_note` text COMMENT '工艺说明(怎么做的)',
  `operator` varchar(50) DEFAULT NULL COMMENT '操作人',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration_minutes` int DEFAULT NULL COMMENT '耗时(分钟)',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`process_id`),
  KEY `idx_order` (`order_id`,`round_no`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打样工序历史记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_sample_process`
--

LOCK TABLES `sales_sample_process` WRITE;
/*!40000 ALTER TABLE `sales_sample_process` DISABLE KEYS */;
INSERT INTO `sales_sample_process` VALUES (1,2,1,'印刷','[{\"name\":\"0.125中砂PC(尚昇)\",\"spec\":\"470\",\"qty\":1,\"unit\":\"PCS\",\"materialId\":1,\"materialCode\":\"MTR202608073235\"}]',NULL,'engineer01','2026-08-08 11:43:07',NULL,30,'工序进度更新','2026-08-08 11:43:06'),(2,2,1,'冲切','[{\"name\":\"8B35 0.175(兴富成）亮面\",\"spec\":\"230\",\"qty\":1,\"unit\":\"PCS\",\"materialId\":4,\"materialCode\":\"MTR202608073238\"}]',NULL,'engineer01','2026-08-08 11:43:19',NULL,NULL,'工序进度更新','2026-08-08 11:43:19'),(3,2,1,'冲切','[{\"name\":\"8B35 0.175(兴富成）亮面\",\"spec\":\"230\",\"qty\":1,\"unit\":\"PCS\",\"materialId\":4,\"materialCode\":\"MTR202608073238\"}]',NULL,'engineer01','2026-08-08 11:43:39',NULL,NULL,'工序进度更新','2026-08-08 11:43:39'),(4,2,1,'贴合','[{\"name\":\"0.8mm 茶色PC（DB11008）（地博）\",\"spec\":\"232*210\",\"qty\":1,\"unit\":\"PCS\",\"materialId\":152,\"materialCode\":\"MTR202608073386\"}]',NULL,'engineer01','2026-08-08 11:43:57',NULL,NULL,'工序进度更新','2026-08-08 11:43:56'),(5,1,1,'印刷',NULL,NULL,'engineer01','2026-08-08 11:44:37',NULL,30,'工序进度更新','2026-08-08 11:44:37'),(6,1,1,'冲切','[{\"name\":\"3M9448HK(万绰）\",\"spec\":\"100\",\"qty\":1,\"unit\":\"PCS\",\"materialId\":597,\"materialCode\":\"MTR202608073831\"}]',NULL,'engineer01','2026-08-08 11:44:49',NULL,20,'工序进度更新','2026-08-08 11:44:49'),(7,1,1,'贴合',NULL,NULL,'engineer01','2026-08-08 11:45:01',NULL,44,'工序进度更新','2026-08-08 11:45:00'),(8,1,1,'SMT贴片','[{\"name\":\"0.175中砂面PC-DB6842(地博）\",\"spec\":\"410\",\"qty\":1,\"unit\":\"PCS\",\"materialId\":9,\"materialCode\":\"MTR202608073243\"}]',NULL,'engineer01','2026-08-08 11:45:16',NULL,40,'工序进度更新','2026-08-08 11:45:16');
/*!40000 ALTER TABLE `sales_sample_process` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_sample_round`
--

DROP TABLE IF EXISTS `sales_sample_round`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_sample_round` (
  `round_id` bigint NOT NULL AUTO_INCREMENT COMMENT '轮次记录ID',
  `order_id` bigint NOT NULL COMMENT '样品单ID',
  `round_no` int NOT NULL COMMENT '轮次号',
  `engineering_note` text COMMENT '该轮工艺参数快照',
  `attachment_ids` varchar(500) DEFAULT NULL COMMENT '该轮图纸附件ID(JSON数组)',
  `bom_snapshot` text COMMENT '该轮BOM物料快照(JSON)',
  `process_snapshot` text COMMENT '该轮工序记录汇总(JSON)',
  `result` varchar(20) DEFAULT NULL COMMENT '该轮结果: pending/confirmed/rejected',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '该轮退回原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`round_id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='样品打样轮次快照';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_sample_round`
--

LOCK TABLES `sales_sample_round` WRITE;
/*!40000 ALTER TABLE `sales_sample_round` DISABLE KEYS */;
INSERT INTO `sales_sample_round` VALUES (1,2,1,NULL,NULL,'[{\"process\":\"印刷\",\"name\":\"0.125中砂PC(尚昇)\",\"spec\":\"470\",\"qty\":1,\"unit\":\"PCS\",\"materialId\":1,\"materialCode\":\"MTR202608073235\"},{\"process\":\"冲切\",\"name\":\"8B35 0.175(兴富成）亮面\",\"spec\":\"230\",\"qty\":1,\"unit\":\"PCS\",\"materialId\":4,\"materialCode\":\"MTR202608073238\"},{\"process\":\"冲切\",\"name\":\"8B35 0.175(兴富成）亮面\",\"spec\":\"230\",\"qty\":1,\"unit\":\"PCS\",\"materialId\":4,\"materialCode\":\"MTR202608073238\"},{\"process\":\"贴合\",\"name\":\"0.8mm 茶色PC（DB11008）（地博）\",\"spec\":\"232*210\",\"qty\":1,\"unit\":\"PCS\",\"materialId\":152,\"materialCode\":\"MTR202608073386\"}]',NULL,'confirmed',NULL,'2026-08-08 11:44:01'),(2,1,1,NULL,'[3]','[{\"process\":\"冲切\",\"name\":\"3M9448HK(万绰）\",\"spec\":\"100\",\"qty\":1,\"unit\":\"PCS\",\"materialId\":597,\"materialCode\":\"MTR202608073831\"},{\"process\":\"SMT贴片\",\"name\":\"0.175中砂面PC-DB6842(地博）\",\"spec\":\"410\",\"qty\":1,\"unit\":\"PCS\",\"materialId\":9,\"materialCode\":\"MTR202608073243\"}]',NULL,'rejected','ces','2026-08-08 11:45:20');
/*!40000 ALTER TABLE `sales_sample_round` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_sample_transfer`
--

DROP TABLE IF EXISTS `sales_sample_transfer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_sample_transfer` (
  `transfer_id` bigint NOT NULL AUTO_INCREMENT COMMENT '转移记录ID',
  `order_id` bigint NOT NULL COMMENT '样品单ID',
  `order_no` varchar(50) DEFAULT NULL COMMENT '样品单号',
  `transfer_no` varchar(50) DEFAULT NULL COMMENT '转移单号',
  `product_id` bigint DEFAULT NULL COMMENT '建档产品ID',
  `bom_id` bigint DEFAULT NULL COMMENT '建档BOM ID',
  `routing_id` bigint DEFAULT NULL COMMENT '建档路线ID',
  `product_action` varchar(20) DEFAULT 'NONE' COMMENT '产品建档动作: NONE/CREATE/UPDATE',
  `bom_action` varchar(20) DEFAULT 'NONE' COMMENT 'BOM动作: NONE/CREATE/SKIP_NO_PROCESS',
  `routing_action` varchar(20) DEFAULT 'NONE' COMMENT '路线动作: NONE/CREATE/SKIP_NO_PROCESS',
  `status` varchar(20) DEFAULT 'SUCCESS' COMMENT '转移结果: SUCCESS/PARTIAL/FAILED',
  `detail` text COMMENT '转移明细说明',
  `create_by` varchar(50) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`transfer_id`),
  KEY `idx_transfer_order` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='样品单产品资料转移记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_sample_transfer`
--

LOCK TABLES `sales_sample_transfer` WRITE;
/*!40000 ALTER TABLE `sales_sample_transfer` DISABLE KEYS */;
INSERT INTO `sales_sample_transfer` VALUES (1,2,'SP2608080002','TF2608080001',1,1,1,'CREATE','CREATE','CREATE','SUCCESS','产品[jst001]新建建档(待审核)\nBOM[BOM-jst001-SAMPLE]生成草稿(3条明细)\n工艺路线[RTE-jst001-SAMPLE]生成草稿(4道工序)','sales01','2026-08-08 12:15:14'),(2,2,'SP2608080002','TF2608080002',1,1,1,'UPDATE','EXISTS','EXISTS','SUCCESS','产品[jst001]已存在，档案核对\n产品[jst001]已有BOM[BOM-jst001-SAMPLE]\n产品[jst001]已有路线[RTE-jst001-SAMPLE]','sales01','2026-08-08 12:26:59');
/*!40000 ALTER TABLE `sales_sample_transfer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_notification`
--

DROP TABLE IF EXISTS `sys_notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notification` (
  `notification_id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL COMMENT '标题',
  `content` text COMMENT '内容',
  `notification_type` varchar(20) NOT NULL COMMENT '类型: SYSTEM/EMAIL/APP/SMS',
  `event_code` varchar(50) DEFAULT NULL COMMENT '触发事件编码',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '业务类型: ORDER_UPDATE/QUALITY_ALERT/INVENTORY_WARN',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '业务ID',
  `sender_id` bigint DEFAULT NULL COMMENT '发送者ID',
  `sender_name` varchar(100) DEFAULT NULL COMMENT '发送者名称',
  `receiver_id` bigint NOT NULL COMMENT '接收者ID',
  `receiver_name` varchar(100) DEFAULT NULL COMMENT '接收者名称',
  `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读',
  `read_time` datetime DEFAULT NULL COMMENT '读取时间',
  `priority` varchar(10) DEFAULT 'NORMAL' COMMENT '优先级: LOW/NORMAL/HIGH/URGENT',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0待发送/1已发送',
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `send_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`notification_id`),
  KEY `idx_receiver` (`receiver_id`,`is_read`),
  KEY `idx_type` (`notification_type`,`status`),
  KEY `idx_send_time` (`send_time`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息通知表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_notification`
--

LOCK TABLES `sys_notification` WRITE;
/*!40000 ALTER TABLE `sys_notification` DISABLE KEYS */;
INSERT INTO `sys_notification` VALUES (1,'询价单【INQ2608070001】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','system',NULL,'inquiry.converted',NULL,NULL,NULL,34,NULL,0,NULL,'normal',1,NULL,'2026-08-07 12:01:28','2026-08-07 12:01:28','2026-08-07 12:01:28'),(2,'询价单【INQ2608070001】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','system',NULL,'inquiry.converted',NULL,NULL,NULL,35,NULL,0,NULL,'normal',1,NULL,'2026-08-07 12:01:28','2026-08-07 12:01:28','2026-08-07 12:01:28'),(3,'报价单【1】已提交审核','报价单已提交审核，请尽快处理。','system',NULL,'quotation.submitted',NULL,NULL,NULL,37,NULL,0,NULL,'normal',1,NULL,'2026-08-07 16:24:37','2026-08-07 16:24:37','2026-08-07 16:24:37'),(4,'报价单【1】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,34,NULL,0,NULL,'normal',1,NULL,'2026-08-07 17:12:34','2026-08-07 17:12:34','2026-08-07 17:12:34'),(5,'报价单【1】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,35,NULL,0,NULL,'normal',1,NULL,'2026-08-07 17:12:34','2026-08-07 17:12:34','2026-08-07 17:12:34'),(6,'报价单【1】已提交审核','报价单已提交审核，请尽快处理。','system',NULL,'quotation.submitted',NULL,NULL,NULL,37,NULL,0,NULL,'normal',1,NULL,'2026-08-07 17:27:34','2026-08-07 17:27:34','2026-08-07 17:27:34'),(7,'报价单【1】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,34,NULL,0,NULL,'normal',1,NULL,'2026-08-07 17:28:10','2026-08-07 17:28:09','2026-08-07 17:28:09'),(8,'报价单【1】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,35,NULL,0,NULL,'normal',1,NULL,'2026-08-07 17:28:10','2026-08-07 17:28:09','2026-08-07 17:28:09'),(9,'询价单【INQ2608070002】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','system',NULL,'inquiry.converted',NULL,NULL,NULL,34,NULL,0,NULL,'normal',1,NULL,'2026-08-07 18:44:01','2026-08-07 18:44:01','2026-08-07 18:44:01'),(10,'询价单【INQ2608070002】已转为报价单','客户询价单已成功转为报价单，请及时处理后续流程。','system',NULL,'inquiry.converted',NULL,NULL,NULL,35,NULL,0,NULL,'normal',1,NULL,'2026-08-07 18:44:01','2026-08-07 18:44:01','2026-08-07 18:44:01'),(11,'报价单【2】已提交审核','报价单已提交审核，请尽快处理。','system',NULL,'quotation.submitted',NULL,NULL,NULL,37,NULL,0,NULL,'normal',1,NULL,'2026-08-07 18:44:44','2026-08-07 18:44:43','2026-08-07 18:44:43'),(12,'报价单【2】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,34,NULL,0,NULL,'normal',1,NULL,'2026-08-07 18:44:50','2026-08-07 18:44:49','2026-08-07 18:44:49'),(13,'报价单【2】审核结果','报价单审核已完成，请查看结果。','system',NULL,'quotation.reviewed',NULL,NULL,NULL,35,NULL,0,NULL,'normal',1,NULL,'2026-08-07 18:44:50','2026-08-07 18:44:49','2026-08-07 18:44:49'),(14,'报价单【2】已发送给客户','报价单已发送给客户，请关注客户反馈。','system',NULL,'quotation.sent',NULL,NULL,NULL,34,NULL,0,NULL,'normal',1,NULL,'2026-08-08 10:27:33','2026-08-08 10:27:32','2026-08-08 10:27:32'),(15,'报价单【2】已发送给客户','报价单已发送给客户，请关注客户反馈。','system',NULL,'quotation.sent',NULL,NULL,NULL,35,NULL,0,NULL,'normal',1,NULL,'2026-08-08 10:27:33','2026-08-08 10:27:32','2026-08-08 10:27:32'),(16,'报价单【2】客户已确认','客户已确认报价单，请及时转为销售订单。','system',NULL,'quotation.confirmed',NULL,NULL,NULL,34,NULL,0,NULL,'normal',1,NULL,'2026-08-08 10:27:40','2026-08-08 10:27:40','2026-08-08 10:27:40'),(17,'报价单【2】客户已确认','客户已确认报价单，请及时转为销售订单。','system',NULL,'quotation.confirmed',NULL,NULL,NULL,35,NULL,0,NULL,'normal',1,NULL,'2026-08-08 10:27:40','2026-08-08 10:27:40','2026-08-08 10:27:40'),(18,'样品单【1】已创建，请安排打样','报价单已转为样品单，请工程部门安排打样工作。','system',NULL,'sample.created',NULL,NULL,NULL,38,NULL,0,NULL,'normal',1,NULL,'2026-08-08 10:27:43','2026-08-08 10:27:42','2026-08-08 10:27:42'),(19,'调拨单【{transferType=normal, fromWarehouseId=1, toWarehouseId=1, transferDate=2026-08-08, items=[{materialId=1535, materialCode=MTR202608074769, materialName=0.38mm 幼砂加硬PC(共耘), quantity=1}]}】已创建','有新调拨单待处理，请及时执行调拨。','system',NULL,'inventory.transfer.created',NULL,NULL,NULL,39,NULL,0,NULL,'normal',1,NULL,'2026-08-08 10:37:17','2026-08-08 10:37:16','2026-08-08 10:37:16'),(20,'报价单【1】已发送给客户','报价单已发送给客户，请关注客户反馈。','system',NULL,'quotation.sent',NULL,NULL,NULL,34,NULL,0,NULL,'normal',1,NULL,'2026-08-08 11:08:41','2026-08-08 11:08:41','2026-08-08 11:08:41'),(21,'报价单【1】已发送给客户','报价单已发送给客户，请关注客户反馈。','system',NULL,'quotation.sent',NULL,NULL,NULL,35,NULL,0,NULL,'normal',1,NULL,'2026-08-08 11:08:41','2026-08-08 11:08:41','2026-08-08 11:08:41'),(22,'报价单【1】客户已确认','客户已确认报价单，请及时转为销售订单。','system',NULL,'quotation.confirmed',NULL,NULL,NULL,34,NULL,0,NULL,'normal',1,NULL,'2026-08-08 11:09:05','2026-08-08 11:09:04','2026-08-08 11:09:04'),(23,'报价单【1】客户已确认','客户已确认报价单，请及时转为销售订单。','system',NULL,'quotation.confirmed',NULL,NULL,NULL,35,NULL,0,NULL,'normal',1,NULL,'2026-08-08 11:09:05','2026-08-08 11:09:04','2026-08-08 11:09:04'),(24,'样品单【2】已创建，请安排打样','报价单已转为样品单，请工程部门安排打样工作。','system',NULL,'sample.created',NULL,NULL,NULL,38,NULL,0,NULL,'normal',1,NULL,'2026-08-08 11:09:44','2026-08-08 11:09:44','2026-08-08 11:09:44'),(25,'样品【2】已制作完成','样品已制作完成，请安排送样。','system',NULL,'sample.ready',NULL,NULL,NULL,34,NULL,0,NULL,'normal',1,NULL,'2026-08-08 11:44:01','2026-08-08 11:44:00','2026-08-08 11:44:00'),(26,'样品【2】已制作完成','样品已制作完成，请安排送样。','system',NULL,'sample.ready',NULL,NULL,NULL,35,NULL,0,NULL,'normal',1,NULL,'2026-08-08 11:44:01','2026-08-08 11:44:00','2026-08-08 11:44:00'),(27,'样品【2】已制作完成','样品已制作完成，请安排送样。','system',NULL,'sample.ready',NULL,NULL,NULL,38,NULL,0,NULL,'normal',1,NULL,'2026-08-08 11:44:01','2026-08-08 11:44:00','2026-08-08 11:44:00'),(28,'样品【1】已制作完成','样品已制作完成，请安排送样。','system',NULL,'sample.ready',NULL,NULL,NULL,34,NULL,0,NULL,'normal',1,NULL,'2026-08-08 11:45:20','2026-08-08 11:45:20','2026-08-08 11:45:20'),(29,'样品【1】已制作完成','样品已制作完成，请安排送样。','system',NULL,'sample.ready',NULL,NULL,NULL,35,NULL,0,NULL,'normal',1,NULL,'2026-08-08 11:45:20','2026-08-08 11:45:20','2026-08-08 11:45:20'),(30,'样品【1】已制作完成','样品已制作完成，请安排送样。','system',NULL,'sample.ready',NULL,NULL,NULL,38,NULL,0,NULL,'normal',1,NULL,'2026-08-08 11:45:20','2026-08-08 11:45:20','2026-08-08 11:45:20'),(31,'样品单【2】资料转移完成，请完善产品/BOM/工艺档案并提交审核','样品打样成果已建档（产品/BOM/工艺路线），请工程完善后提交审核','system',NULL,'sample.transferred',NULL,NULL,NULL,38,NULL,0,NULL,'normal',1,NULL,'2026-08-08 12:15:14','2026-08-08 12:15:14','2026-08-08 12:15:14'),(32,'样品单【2】资料转移完成，请完善产品/BOM/工艺档案并提交审核','样品打样成果已建档（产品/BOM/工艺路线），请工程完善后提交审核','system',NULL,'sample.transferred',NULL,NULL,NULL,38,NULL,0,NULL,'normal',1,NULL,'2026-08-08 12:27:00','2026-08-08 12:26:59','2026-08-08 12:26:59');
/*!40000 ALTER TABLE `sys_notification` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-08 12:28:48
