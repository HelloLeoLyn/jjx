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
-- Table structure for table `sales_contract`
--

DROP TABLE IF EXISTS `sales_contract`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_contract` (
  `contract_id` bigint NOT NULL AUTO_INCREMENT COMMENT '合同ID',
  `contract_no` varchar(50) NOT NULL COMMENT '合同编号',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `customer_id` bigint NOT NULL COMMENT '客户ID',
  `customer_name` varchar(200) NOT NULL COMMENT '客户名称',
  `contract_name` varchar(200) NOT NULL COMMENT '合同名称',
  `contract_type` tinyint DEFAULT '1' COMMENT '合同类型 (1: 销售合同, 2: 框架协议, 3: 补充协议)',
  `sign_date` date NOT NULL COMMENT '签订日期',
  `effective_date` date NOT NULL COMMENT '生效日期',
  `expiry_date` date DEFAULT NULL COMMENT '到期日期',
  `contract_amount` decimal(15,2) NOT NULL COMMENT '合同金额',
  `currency` varchar(10) DEFAULT 'CNY' COMMENT '币种',
  `payment_terms` text COMMENT '付款条款',
  `delivery_terms` text COMMENT '交货条款',
  `quality_terms` text COMMENT '质量条款',
  `warranty_terms` text COMMENT '保修条款',
  `other_terms` text COMMENT '其他条款',
  `contract_status` tinyint DEFAULT '1' COMMENT '合同状态 (1: 草稿, 2: 已签订, 3: 执行中, 4: 已完成, 5: 已终止, 6: 已过期)',
  `attachment_path` varchar(500) DEFAULT NULL COMMENT '附件路径',
  `remark` text COMMENT '备注',
  `signatory_id` bigint DEFAULT NULL COMMENT '签署人ID',
  `signatory_name` varchar(100) DEFAULT NULL COMMENT '签署人姓名',
  `approver_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `approver_name` varchar(100) DEFAULT NULL COMMENT '审核人姓名',
  `approve_time` datetime DEFAULT NULL COMMENT '审核时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 (0: 正常, 1: 删除)',
  PRIMARY KEY (`contract_id`),
  UNIQUE KEY `uk_contract_no` (`contract_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_sign_date` (`sign_date`),
  KEY `idx_contract_status` (`contract_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售合同表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_contract`
--

LOCK TABLES `sales_contract` WRITE;
/*!40000 ALTER TABLE `sales_contract` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_contract` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sales_performance`
--

DROP TABLE IF EXISTS `sales_performance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_performance` (
  `performance_id` bigint NOT NULL AUTO_INCREMENT COMMENT '业绩ID',
  `sales_person_id` bigint NOT NULL COMMENT '销售员ID',
  `sales_person_name` varchar(100) NOT NULL COMMENT '销售员姓名',
  `stat_year` int NOT NULL COMMENT '统计年份',
  `stat_month` int NOT NULL COMMENT '统计月份',
  `new_customer_count` int DEFAULT '0' COMMENT '新增客户数',
  `quotation_count` int DEFAULT '0' COMMENT '报价单数',
  `quotation_amount` decimal(15,2) DEFAULT '0.00' COMMENT '报价金额',
  `order_count` int DEFAULT '0' COMMENT '订单数',
  `order_amount` decimal(15,2) DEFAULT '0.00' COMMENT '订单金额',
  `delivery_count` int DEFAULT '0' COMMENT '发货单数',
  `delivery_amount` decimal(15,2) DEFAULT '0.00' COMMENT '发货金额',
  `receipt_amount` decimal(15,2) DEFAULT '0.00' COMMENT '收款金额',
  `invoice_amount` decimal(15,2) DEFAULT '0.00' COMMENT '开票金额',
  `performance_score` decimal(5,2) DEFAULT '0.00' COMMENT '业绩评分',
  `remark` text COMMENT '备注',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标志 (0: 正常, 1: 删除)',
  PRIMARY KEY (`performance_id`),
  UNIQUE KEY `uk_sales_person_stat` (`sales_person_id`,`stat_year`,`stat_month`),
  KEY `idx_sales_person_id` (`sales_person_id`),
  KEY `idx_stat_year_month` (`stat_year`,`stat_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售业绩统计表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sales_performance`
--

LOCK TABLES `sales_performance` WRITE;
/*!40000 ALTER TABLE `sales_performance` DISABLE KEYS */;
/*!40000 ALTER TABLE `sales_performance` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed
