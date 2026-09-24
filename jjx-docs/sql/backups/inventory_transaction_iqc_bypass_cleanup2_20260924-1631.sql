-- 备份人: 大黄(OpenClaw)
-- 原因: 清理 026 修复前旧运行实例产生的 IQC 旁路流水（txn 5 / IN260924002-1 / 16:17:58 / Hermes 2026-09-24 16:2x 发现）
-- 时间: 2026-09-24 16:31:40
-- 涉表: inventory_transaction
-- 任务码: dev-20260924-026
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
-- Table structure for table `inventory_transaction`
--

DROP TABLE IF EXISTS `inventory_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_transaction` (
  `transaction_id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `inventory_item_id` bigint DEFAULT NULL COMMENT '统一库存物品ID',
  `material_id` bigint DEFAULT NULL COMMENT '材料来源ID（产品流水为NULL）',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码（冗余）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称（冗余）',
  `warehouse_id` bigint NOT NULL COMMENT '仓库ID',
  `location_id` bigint DEFAULT NULL COMMENT '库位ID',
  `transaction_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '交易类型：inbound入库/outbound出库/transfer_in调拨入库/transfer_out调拨出库/adjust盘盈盘亏',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源类型：purchase_order/work_order/sales_order/stocktake',
  `source_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `source_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单号',
  `lot_id` bigint DEFAULT NULL COMMENT '检验批ID',
  `ncr_id` bigint DEFAULT NULL COMMENT '不良台账ID',
  `batch_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `iqc_batch_id` bigint DEFAULT NULL,
  `quantity` decimal(12,4) NOT NULL COMMENT '变动数量（正数增加，负数减少）',
  `before_quantity` decimal(12,4) NOT NULL COMMENT '变动前数量',
  `after_quantity` decimal(12,4) NOT NULL COMMENT '变动后数量',
  `unit_cost` decimal(12,4) DEFAULT NULL COMMENT '单位成本',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '变动金额',
  `transaction_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '交易时间',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人姓名',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`transaction_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_source` (`source_type`,`source_id`),
  KEY `idx_transaction_time` (`transaction_time`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_transaction_type` (`transaction_type`),
  KEY `idx_transaction_inventory_item` (`inventory_item_id`,`transaction_time`),
  KEY `idx_tx_lot` (`lot_id`),
  KEY `idx_transaction_iqc_batch` (`iqc_batch_id`),
  CONSTRAINT `fk_transaction_inventory_item` FOREIGN KEY (`inventory_item_id`) REFERENCES `inventory_item` (`inventory_item_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_transaction`
--

LOCK TABLES `inventory_transaction` WRITE;
/*!40000 ALTER TABLE `inventory_transaction` DISABLE KEYS */;
INSERT INTO `inventory_transaction` VALUES (3,2060,1585,'RM001585','DG68 样品 (新亚洲)',2,NULL,'INBOUND','PURCHASE',1,'IN260924001',1,NULL,'IN260924001-1',5,145.0000,0.0000,145.0000,5.0000,725.00,'2026-09-24 16:07:56',1,'系统管理员','确认入库','admin','2026-09-24 16:07:56','admin','2026-09-24 16:07:56'),(4,2069,1557,'AUX001557','3.5mm38度B料黑色EVA（3.5T无胶)',2,NULL,'INBOUND','PURCHASE',1,'IN260924001',2,NULL,'IN260924001-2',NULL,300.0000,0.0000,300.0000,3.0000,900.00,'2026-09-24 16:07:56',1,'系统管理员','确认入库','admin','2026-09-24 16:07:56','admin','2026-09-24 16:07:56'),(5,NULL,1585,'RM001585','DG68 样品 (新亚洲)',2,NULL,'IQC_QUARANTINE','INBOUND_IQC',2,'IN260924002',NULL,NULL,'IN260924002-1',6,2.0000,0.0000,2.0000,5.0000,10.00,'2026-09-24 16:17:58',1,'系统管理员','IQC 不合格品隔离','admin','2026-09-24 16:17:58','admin','2026-09-24 16:17:58');
/*!40000 ALTER TABLE `inventory_transaction` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-24 16:31:40
