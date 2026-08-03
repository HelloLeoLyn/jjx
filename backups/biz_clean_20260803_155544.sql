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
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售订单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_order`
--

LOCK TABLES `sales_order` WRITE;
/*!40000 ALTER TABLE `sales_order` DISABLE KEYS */;
INSERT INTO `sales_order` VALUES (1,'8f77b3f26f514337','SP2608030001',1,3,'李记精密电子科技','陈工','13700137003','2026-08-03','2026-09-02',2,1,1,0,NULL,'CNY',1.0000,NULL,NULL,NULL,25.00,0.00,0.00,0.00,0.00,0.00,25.00,1,0.00,0.00,10,0,0,1,'admin','ces','admin','2026-08-03 09:48:50','admin','2026-08-03 12:02:33',0,6,2,10,NULL,'工程','2026-08-03 10:43:56',NULL,'冲切',0.00,0.00,'','2026-08-03 11:05:50','2026-08-03 11:05:57','客户确认',NULL,NULL),(2,NULL,'SO2608030001',1,3,'李记精密电子科技','陈工','13700137003','2026-08-03','2026-09-02',1,1,1,0,NULL,'CNY',1.0000,NULL,NULL,NULL,25.00,0.00,0.00,0.00,0.00,0.00,25.00,1,0.00,0.00,10,0,0,1,'admin','由样品单[SP2608030001]转量产生成\n【最后工序】冲切\n【打样工时】2.28小时','gongcheng0','2026-08-03 11:13:25','gongcheng0','2026-08-03 11:13:25',0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,0.00,0.00,NULL,NULL,NULL,NULL,NULL,NULL),(3,NULL,'SO2608030002',1,3,'李记精密电子科技','陈工','13700137003','2026-08-03','2026-09-02',1,1,1,0,NULL,'CNY',1.0000,NULL,NULL,NULL,25.00,0.00,0.00,0.00,0.00,0.00,25.00,1,0.00,0.00,10,0,0,1,'admin','由样品单[SP2608030001]转量产生成\n【最后工序】冲切\n【打样工时】2.28小时','admin','2026-08-03 11:25:28','admin','2026-08-03 11:25:28',0,0,0,NULL,NULL,NULL,NULL,NULL,NULL,0.00,0.00,NULL,NULL,NULL,NULL,NULL,NULL),(4,'0158317bef884102','SP2608030002',3,4,'德力通电子实业有限公司','赵总','13600136004','2026-08-03','2026-09-02',2,1,1,0,NULL,'CNY',1.0000,NULL,NULL,NULL,0.00,0.00,0.00,0.00,0.00,0.00,0.00,1,0.00,0.00,10,0,0,26,'xiaoshou0',NULL,'xiaoshou0','2026-08-03 11:51:58','admin','2026-08-03 12:27:05',0,4,1,10,NULL,'engineer','2026-08-03 12:27:05',NULL,'印刷',0.00,0.00,NULL,NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `sales_order` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售报价表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_quotation`
--

LOCK TABLES `sales_quotation` WRITE;
/*!40000 ALTER TABLE `sales_quotation` DISABLE KEYS */;
INSERT INTO `sales_quotation` VALUES (1,'8f77b3f26f514337','QT2608030001',2,3,'李记精密电子科技','陈工','13700137003','2026-08-03','2026-09-02','CNY',1.0000,5,25.00,0.00,0.00,25.00,0.00,25.00,'由询价单[INQ2608030001]自动创建',1,'admin',NULL,NULL,NULL,NULL,'2026-08-03 09:48:39','email',NULL,3,'2026-08-03 11:25:28','admin','2026-08-03 09:42:21','admin','2026-08-03 09:48:50',0),(2,'b4c37b4b093b4303','QT2608030002',1,2,'金泰通电子有限公司','李小姐','13900139002','2026-08-03','2026-09-02','CNY',1.0000,3,3.00,0.00,0.00,3.00,0.00,3.00,'由询价单[INQ2608030002]自动创建',26,'xiaoshou0',1,'系统管理员','2026-08-03 10:10:33',NULL,'2026-08-03 10:10:35','email',NULL,NULL,NULL,'xiaoshou0','2026-08-03 10:03:45','xiaoshou0','2026-08-03 10:10:27',0),(3,'0158317bef884102','QT2608030003',2,4,'德力通电子实业有限公司','赵总','13600136004','2026-08-03','2026-09-02','CNY',1.0000,9,0.00,0.00,0.00,0.00,0.00,0.00,'由询价单[INQ2608030003]自动创建',26,'xiaoshou0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'xiaoshou0','2026-08-03 11:51:42','xiaoshou0','2026-08-03 11:51:58',0);
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
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报价单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_quotation_item`
--

LOCK TABLES `sales_quotation_item` WRITE;
/*!40000 ALTER TABLE `sales_quotation_item` DISABLE KEYS */;
INSERT INTO `sales_quotation_item` VALUES (8,1,NULL,'222','2222',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,5,5.00,'PCS',25.00,NULL,NULL,NULL,NULL,NULL,0,'2026-08-01 21:57:24','2026-08-01 21:57:24'),(9,2,NULL,'111','111',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,3.00,'PCS',3.00,NULL,NULL,NULL,NULL,NULL,0,'2026-08-03 10:10:27','2026-08-03 10:10:27');
/*!40000 ALTER TABLE `sales_quotation_item` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售询价单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_inquiry`
--

LOCK TABLES `sales_inquiry` WRITE;
/*!40000 ALTER TABLE `sales_inquiry` DISABLE KEYS */;
INSERT INTO `sales_inquiry` VALUES (1,'8f77b3f26f514337','INQ2608030001',3,'李记精密电子科技','陈工','13700137003','2026-08-03',NULL,'',NULL,NULL,'','','','','',1,3,2,1,'2026-08-03 09:42:21','',1,'admin','admin','2026-08-03 09:41:49','admin','2026-08-03 09:41:56',0),(2,'b4c37b4b093b4303','INQ2608030002',2,'金泰通电子有限公司','李小姐','13900139002','2026-08-03',2,'',NULL,1,'','','','','',1,3,1,2,'2026-08-03 10:03:45','',26,'xiaoshou0','xiaoshou0','2026-08-03 10:03:40','xiaoshou0','2026-08-03 10:03:40',0),(3,'0158317bef884102','INQ2608030003',4,'德力通电子实业有限公司','赵总','13600136004','2026-08-13',1,'',NULL,NULL,'','','','','',0,3,2,3,'2026-08-03 11:51:42','',26,'xiaoshou0','xiaoshou0','2026-08-03 11:51:37','xiaoshou0','2026-08-03 11:51:37',0);
/*!40000 ALTER TABLE `sales_inquiry` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打样工序历史记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_sample_process`
--

LOCK TABLES `sales_sample_process` WRITE;
/*!40000 ALTER TABLE `sales_sample_process` DISABLE KEYS */;
INSERT INTO `sales_sample_process` VALUES (1,20,1,'贴合','[\n  {\"name\":\"PET面板膜\",\"spec\":\"0.25mm\",\"qty\":0.01,\"unit\":\"M\"},\n  {\"name\":\"银浆导电线路膜\",\"spec\":\"印刷线路\",\"qty\":0.01,\"unit\":\"M\"},\n  {\"name\":\"3M双面胶\",\"spec\":\"0.1mm\",\"qty\":0.01,\"unit\":\"M\"},\n  {\"name\":\"背胶层\",\"spec\":\"0.05mm\",\"qty\":0.01,\"unit\":\"M\"}\n]',NULL,'gongcheng0','2026-08-01 16:37:56',NULL,NULL,'历史数据补录(DEV-454)','2026-08-01 16:37:56'),(2,19,1,'印刷',NULL,NULL,'admin','2026-08-01 16:41:21',NULL,NULL,'工序进度更新','2026-08-01 16:41:20'),(3,19,1,'冲切',NULL,NULL,'gongcheng0','2026-08-01 17:10:10',NULL,NULL,'工序进度更新','2026-08-01 17:10:10'),(4,19,1,'SMT贴片',NULL,NULL,'gongcheng0','2026-08-01 17:10:16',NULL,NULL,'工序进度更新','2026-08-01 17:10:16'),(5,19,1,'装配',NULL,NULL,'gongcheng0','2026-08-01 17:10:24',NULL,NULL,'工序进度更新','2026-08-01 17:10:23'),(6,19,1,'SMT贴片',NULL,NULL,'gongcheng0','2026-08-01 17:12:26',NULL,NULL,'工序进度更新','2026-08-01 17:12:26'),(7,19,1,'印刷',NULL,NULL,'gongcheng0','2026-08-01 17:12:30',NULL,NULL,'工序进度更新','2026-08-01 17:12:30'),(8,19,1,'冲切',NULL,NULL,'gongcheng0','2026-08-01 17:12:33',NULL,NULL,'工序进度更新','2026-08-01 17:12:32'),(11,1,1,'印刷',NULL,NULL,'gongcheng0','2026-08-01 22:01:22',NULL,NULL,'工序进度更新','2026-08-01 22:01:21'),(12,1,1,'冲切',NULL,NULL,'gongcheng0','2026-08-01 22:02:28',NULL,NULL,'工序进度更新','2026-08-01 22:02:27'),(13,1,1,'冲切','[{\"name\":\"xxx\",\"spec\":\"xxx\",\"qty\":1,\"unit\":\"PCS\"},{\"name\":\"xxx\",\"spec\":\"xx\",\"qty\":1,\"unit\":\"PCS\"}]','asdfasdf','gongcheng0','2026-08-01 22:19:03',NULL,45,'工序进度更新','2026-08-01 22:19:02'),(14,1,1,'印刷',NULL,NULL,'gongcheng0','2026-08-03 10:44:53',NULL,2,'工序进度更新','2026-08-03 10:44:52'),(15,1,1,'SMT贴片','[{\"name\":\"222\",\"spec\":\"333\",\"qty\":1,\"unit\":\"PCS\"}]',NULL,'gongcheng0','2026-08-03 10:45:27',NULL,30,'工序进度更新','2026-08-03 10:45:26'),(16,1,2,'印刷',NULL,NULL,'gongcheng0','2026-08-03 11:03:02',NULL,30,'工序进度更新','2026-08-03 11:03:01'),(17,1,2,'冲切','[{\"name\":\"1\",\"spec\":\"1\",\"qty\":1,\"unit\":\"PCS\"}]',NULL,'gongcheng0','2026-08-03 11:03:23',NULL,30,'工序进度更新','2026-08-03 11:03:22'),(18,4,1,'印刷',NULL,NULL,'admin','2026-08-03 12:27:05',NULL,30,'工序进度更新','2026-08-03 12:27:04');
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='样品打样轮次快照';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_sample_round`
--

LOCK TABLES `sales_sample_round` WRITE;
/*!40000 ALTER TABLE `sales_sample_round` DISABLE KEYS */;
INSERT INTO `sales_sample_round` VALUES (1,20,1,'阿斯顿发',NULL,NULL,NULL,'confirmed',NULL,'2026-08-01 16:34:23'),(3,19,1,NULL,'[5]',NULL,NULL,'confirmed',NULL,'2026-08-01 20:09:32'),(4,1,1,NULL,'[1,2,3]','[{\"process\":\"冲切\",\"name\":\"xxx\",\"spec\":\"xxx\",\"qty\":1,\"unit\":\"PCS\"},{\"process\":\"冲切\",\"name\":\"xxx\",\"spec\":\"xx\",\"qty\":1,\"unit\":\"PCS\"},{\"process\":\"SMT贴片\",\"name\":\"222\",\"spec\":\"333\",\"qty\":1,\"unit\":\"PCS\"}]',NULL,'rejected','ces','2026-08-03 10:50:24'),(5,1,2,NULL,'[1,2,3]','[{\"process\":\"冲切\",\"name\":\"xxx\",\"spec\":\"xxx\",\"qty\":1,\"unit\":\"PCS\"},{\"process\":\"冲切\",\"name\":\"xxx\",\"spec\":\"xx\",\"qty\":1,\"unit\":\"PCS\"},{\"process\":\"SMT贴片\",\"name\":\"222\",\"spec\":\"333\",\"qty\":1,\"unit\":\"PCS\"},{\"process\":\"冲切\",\"name\":\"1\",\"spec\":\"1\",\"qty\":1,\"unit\":\"PCS\"}]',NULL,'confirmed',NULL,'2026-08-03 11:05:29'),(6,4,1,NULL,NULL,NULL,NULL,'pending',NULL,'2026-08-03 12:27:05');
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='样品单产品资料转移记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_sample_transfer`
--

LOCK TABLES `sales_sample_transfer` WRITE;
/*!40000 ALTER TABLE `sales_sample_transfer` DISABLE KEYS */;
INSERT INTO `sales_sample_transfer` VALUES (1,1,'SP2608030001','TF2608030005',4,4,2,'CREATE','CREATE','CREATE','SUCCESS','产品[222]新建建档(待审核)\nBOM[BOM-222-SAMPLE]生成草稿(1条明细)\n工艺路线[RTE-222-SAMPLE]生成草稿(2道工序)','admin','2026-08-03 12:02:04'),(2,1,'SP2608030001','TF2608030006',4,4,2,'UPDATE','EXISTS','EXISTS','SUCCESS','产品[222]已存在，档案核对\n产品[222]已有BOM[BOM-222-SAMPLE]\n产品[222]已有路线[RTE-222-SAMPLE]','gongcheng0','2026-08-03 12:17:46'),(3,1,'SP2608030001','TF2608030007',4,4,2,'UPDATE','EXISTS','EXISTS','SUCCESS','产品[222]已存在，档案核对\n产品[222]已有BOM[BOM-222-SAMPLE]\n产品[222]已有路线[RTE-222-SAMPLE]','xiaoshou0','2026-08-03 14:56:57'),(4,1,'SP2608030001','TF2608030008',4,4,2,'UPDATE','EXISTS','EXISTS','SUCCESS','产品[222]已存在，档案核对\n产品[222]已有BOM[BOM-222-SAMPLE]\n产品[222]已有路线[RTE-222-SAMPLE]','xiaoshou0','2026-08-03 14:57:35');
/*!40000 ALTER TABLE `sales_sample_transfer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product`
--

DROP TABLE IF EXISTS `product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product` (
  `product_id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品ID',
  `product_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品编码',
  `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品名称',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `product_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'standard' COMMENT '类型：standard标准/custom定制',
  `spec_json` json DEFAULT NULL COMMENT '规格参数',
  `base_price` decimal(12,2) DEFAULT NULL COMMENT '基础售价',
  `cost_price` decimal(12,2) DEFAULT NULL COMMENT '标准成本',
  `min_order_qty` int DEFAULT '1' COMMENT '最小起订量',
  `lead_time` int DEFAULT '15' COMMENT '标准交期(天)',
  `product_status` bigint NOT NULL DEFAULT '1' COMMENT '状态',
  `current_bom_id` bigint DEFAULT NULL COMMENT '当前BOM ID',
  `current_route_id` bigint DEFAULT NULL COMMENT '当前工艺路线ID',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位',
  `approve_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '审核批注',
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `uk_product_code` (`product_code`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_product_status` (`product_status`),
  KEY `idx_product_name` (`product_name`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product`
--

LOCK TABLES `product` WRITE;
/*!40000 ALTER TABLE `product` DISABLE KEYS */;
INSERT INTO `product` VALUES (4,'222','2222',NULL,'standard',NULL,NULL,NULL,1,15,2,NULL,NULL,'admin','2026-08-03 12:02:05','admin','2026-08-03 12:02:05',NULL,'PCS','');
/*!40000 ALTER TABLE `product` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `engineering_bom`
--

DROP TABLE IF EXISTS `engineering_bom`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_bom` (
  `bom_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'BOM ID',
  `bom_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BOM编码',
  `product_id` bigint NOT NULL COMMENT '产品ID',
  `bom_version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号（v1.0, v1.1）',
  `bom_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'manufacturing' COMMENT '类型：engineering工程/manufacturing制造',
  `is_current` tinyint(1) DEFAULT '1' COMMENT '是否当前版本',
  `effective_date` date DEFAULT NULL COMMENT '生效日期',
  `expiry_date` date DEFAULT NULL COMMENT '失效日期',
  `approve_status` bigint NOT NULL DEFAULT '1' COMMENT '审核状态',
  `approve_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批准人',
  `approve_time` datetime DEFAULT NULL COMMENT '批准时间',
  `approve_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批准备注',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `bom_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'bom名称',
  PRIMARY KEY (`bom_id`),
  UNIQUE KEY `uk_bom_code_version` (`bom_code`,`bom_version`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_is_current` (`is_current`),
  KEY `idx_approve_status` (`approve_status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BOM主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `engineering_bom`
--

LOCK TABLES `engineering_bom` WRITE;
/*!40000 ALTER TABLE `engineering_bom` DISABLE KEYS */;
INSERT INTO `engineering_bom` VALUES (4,'BOM-222-SAMPLE',4,'V1','manufacturing',1,NULL,NULL,1,NULL,NULL,NULL,'admin','2026-08-03 12:02:04','1','2026-08-03 12:02:04','由样品单[SP2608030001]资料转移生成，请工程确认后批准','2222（打样传承BOM）');
/*!40000 ALTER TABLE `engineering_bom` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `engineering_bom_item`
--

DROP TABLE IF EXISTS `engineering_bom_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_bom_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `bom_id` bigint NOT NULL COMMENT 'BOM ID',
  `material_id` bigint DEFAULT NULL COMMENT '物料ID（可空，工程后续匹配）',
  `material_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物料编码（可空）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称',
  `quantity` decimal(12,4) NOT NULL COMMENT '用量（每个成品消耗的物料数量）',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PCS' COMMENT '单位',
  `loss_rate` decimal(5,2) DEFAULT '0.00' COMMENT '损耗率(%)',
  `module_qty` decimal(12,4) DEFAULT NULL COMMENT '模数：每份材料可产出产品数量',
  `base_qty` decimal(12,4) DEFAULT NULL COMMENT '基数：每个产品所需材料份数',
  `min_issue_qty` decimal(12,4) DEFAULT NULL COMMENT '最低投料量',
  `width_mm` decimal(10,2) DEFAULT NULL COMMENT '规格-宽度(mm)',
  `length_mm` decimal(10,2) DEFAULT NULL COMMENT '规格-长度(mm)',
  `layer` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '层（overlay/upper_circuit/...）',
  `position_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '位号',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'buy' COMMENT '来源：buy/make',
  `substitute_json` json DEFAULT NULL COMMENT '替代物料列表',
  `item_order` int DEFAULT '0' COMMENT '排序',
  `specification` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号（原始串）',
  `remark` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_by` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`item_id`),
  KEY `idx_bom_id` (`bom_id`),
  KEY `idx_material_id` (`material_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BOM明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `engineering_bom_item`
--

LOCK TABLES `engineering_bom_item` WRITE;
/*!40000 ALTER TABLE `engineering_bom_item` DISABLE KEYS */;
INSERT INTO `engineering_bom_item` VALUES (3,4,NULL,NULL,'1',1.0000,'PCS',0.00,NULL,NULL,NULL,NULL,NULL,'冲切',NULL,'buy',NULL,1,'1',NULL,'2026-08-03 12:02:05','2026-08-03 12:02:05','admin','admin');
/*!40000 ALTER TABLE `engineering_bom_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `engineering_routing`
--

DROP TABLE IF EXISTS `engineering_routing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_routing` (
  `routing_id` bigint NOT NULL AUTO_INCREMENT COMMENT '路线ID',
  `routing_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '路线编码',
  `routing_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '路线名称',
  `product_id` bigint NOT NULL COMMENT '产品ID',
  `product_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品编码',
  `product_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '产品名称',
  `routing_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工艺路线类型',
  `routing_version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号',
  `is_current` tinyint(1) DEFAULT '1' COMMENT '是否当前版本：0否 1是',
  `approve_status` tinyint NOT NULL DEFAULT '1' COMMENT '审核状态',
  `total_labor_hours` decimal(10,2) DEFAULT '0.00' COMMENT '总人工工时',
  `total_machine_hours` decimal(10,2) DEFAULT '0.00' COMMENT '总机器工时',
  `process_count` int DEFAULT '0' COMMENT '工序数量',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '路线说明',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`routing_id`),
  UNIQUE KEY `uk_routing_code_version` (`routing_code`,`routing_version`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_is_current` (`is_current`),
  KEY `idx_approve_status` (`approve_status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_routing_product_status` (`product_id`,`is_current`,`approve_status`),
  KEY `idx_routing_code_name` (`routing_code`,`routing_name`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品工艺路线表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `engineering_routing`
--

LOCK TABLES `engineering_routing` WRITE;
/*!40000 ALTER TABLE `engineering_routing` DISABLE KEYS */;
INSERT INTO `engineering_routing` VALUES (2,'RTE-222-SAMPLE','2222（打样传承工艺路线）',4,'222','2222',NULL,'V1',1,1,1.00,1.00,2,NULL,'admin','2026-08-03 12:02:04','1','2026-08-03 12:02:04',NULL);
/*!40000 ALTER TABLE `engineering_routing` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `engineering_routing_item`
--

DROP TABLE IF EXISTS `engineering_routing_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_routing_item` (
  `detail_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `routing_id` bigint NOT NULL COMMENT '路线ID',
  `process_id` bigint DEFAULT NULL COMMENT '标准工序ID（可空，匹配不到时由工程后续完善）',
  `process_order` int NOT NULL COMMENT '工序顺序',
  `custom_labor_hours` decimal(10,2) DEFAULT NULL COMMENT '定制人工工时',
  `custom_machine_hours` decimal(10,2) DEFAULT NULL COMMENT '定制机器工时',
  `custom_process_params` json DEFAULT NULL COMMENT '定制工艺参数（JSON格式）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `group_id` bigint DEFAULT NULL COMMENT '组合ID（同一组合的工序共享此ID，NULL表示独立工序）',
  `group_order` int DEFAULT NULL COMMENT '组合顺序（第几组）',
  `group_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组合名称',
  `process_category` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工艺类别',
  PRIMARY KEY (`detail_id`),
  UNIQUE KEY `uk_routing_process_order` (`routing_id`,`process_order`),
  KEY `idx_routing_id` (`routing_id`),
  KEY `idx_process_id` (`process_id`),
  KEY `idx_process_order` (`process_order`),
  KEY `idx_detail_routing_order` (`routing_id`,`process_order`),
  KEY `idx_group_id` (`group_id`),
  CONSTRAINT `fk_routing_detail_process` FOREIGN KEY (`process_id`) REFERENCES `engineering_standard_process` (`process_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_routing_detail_routing` FOREIGN KEY (`routing_id`) REFERENCES `engineering_routing` (`routing_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品路线明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `engineering_routing_item`
--

LOCK TABLES `engineering_routing_item` WRITE;
/*!40000 ALTER TABLE `engineering_routing_item` DISABLE KEYS */;
INSERT INTO `engineering_routing_item` VALUES (2,2,NULL,1,0.50,0.50,NULL,'打样传承: 印刷','2026-08-03 12:02:04','2026-08-03 12:02:04',NULL,NULL,NULL,'M'),(3,2,NULL,2,0.50,0.50,NULL,'打样传承: 冲切','2026-08-03 12:02:04','2026-08-03 12:02:04',NULL,NULL,NULL,'M');
/*!40000 ALTER TABLE `engineering_routing_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `engineering_standard_process`
--

DROP TABLE IF EXISTS `engineering_standard_process`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_standard_process` (
  `process_id` bigint NOT NULL AUTO_INCREMENT COMMENT '工序ID',
  `process_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序编码',
  `process_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序名称',
  `process_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序类型：PRINTING印刷/CUTTING模切/LAMINATING贴合/TESTING测试/PACKAGING包装',
  `process_category` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序类别：PREPARATION准备/MAIN主要/FINISHING后处理/QUALITY质量',
  `standard_labor_hours` decimal(10,2) DEFAULT '0.00' COMMENT '标准人工工时(小时)',
  `standard_machine_hours` decimal(10,2) DEFAULT '0.00' COMMENT '标准机器工时(小时)',
  `process_param_template` json DEFAULT NULL COMMENT '工艺参数模板（JSON格式）',
  `skill_requirement` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '技能要求',
  `equipment_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备类型',
  `quality_standard` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '质量标准',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序说明',
  `is_enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用：0否 1是',
  `display_order` int DEFAULT '0' COMMENT '显示顺序',
  `icon` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '图标',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`process_id`),
  UNIQUE KEY `uk_process_code` (`process_code`),
  KEY `idx_process_type` (`process_type`),
  KEY `idx_is_enabled` (`is_enabled`),
  KEY `idx_display_order` (`display_order`),
  KEY `idx_standard_process_type_category` (`process_type`,`process_category`,`is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品标准工序表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `engineering_standard_process`
--

LOCK TABLES `engineering_standard_process` WRITE;
/*!40000 ALTER TABLE `engineering_standard_process` DISABLE KEYS */;
/*!40000 ALTER TABLE `engineering_standard_process` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单产品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_order_product`
--

LOCK TABLES `sales_order_product` WRITE;
/*!40000 ALTER TABLE `sales_order_product` DISABLE KEYS */;
INSERT INTO `sales_order_product` VALUES (4,5,25,1,4,'PCS',5,'222','2222',NULL,'','','');
/*!40000 ALTER TABLE `sales_order_product` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-03 15:55:44
