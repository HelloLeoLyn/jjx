-- guard 备份：采购收货入库单金额分位缺陷更正（dev-20260928-002）
-- 备份人：hermes  时间：2026-09-28 11:30:34
-- 原因：修正 inventory_inbound_order/item 中 IN260928001 的行金额与合计（写库前快照）
-- 风险：低（仅 2 行明细 + 1 行主表金额字段；该单待检验、未过账）
-- 涉及表：inventory_inbound_order, inventory_inbound_item, sys_task
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
-- Table structure for table `inventory_inbound_order`
--

DROP TABLE IF EXISTS `inventory_inbound_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_inbound_order` (
  `inbound_id` bigint NOT NULL AUTO_INCREMENT COMMENT '入库单ID',
  `inbound_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入库单号，格式：IN+YYYYMMDD+流水号',
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '链路追踪ID',
  `inbound_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '入库类型：purchase采购入库/production生产入库/return退货入库/transfer调拨入库/adjust盘盈入库',
  `source_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源类型：purchase_order/work_order/sales_return',
  `source_id` bigint DEFAULT NULL COMMENT '来源单据ID',
  `source_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源单号',
  `warehouse_id` bigint NOT NULL COMMENT '入库仓库ID',
  `location_id` bigint DEFAULT NULL COMMENT '建议库位ID',
  `supplier_id` bigint DEFAULT NULL COMMENT '供应商ID（采购入库时使用）',
  `supplier_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商名称',
  `inbound_date` date NOT NULL COMMENT '入库日期',
  `total_quantity` decimal(12,4) DEFAULT '0.0000' COMMENT '总数量',
  `total_amount` decimal(12,2) DEFAULT '0.00' COMMENT '总金额',
  `inspector_id` bigint DEFAULT NULL COMMENT '检验员ID',
  `inspector_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检验员姓名',
  `inspection_result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检验结果：pass合格/fail不合格/partial部分合格',
  `inspection_time` datetime DEFAULT NULL COMMENT '检验时间',
  `inspection_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检验备注',
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
  PRIMARY KEY (`inbound_id`),
  UNIQUE KEY `uk_inbound_no` (`inbound_no`),
  KEY `idx_source` (`source_type`,`source_id`),
  KEY `idx_warehouse_id` (`warehouse_id`),
  KEY `idx_inbound_date` (`inbound_date`),
  KEY `idx_order_status` (`order_status`),
  KEY `idx_approve_status` (`approve_status`),
  CONSTRAINT `fk_inbound_warehouse` FOREIGN KEY (`warehouse_id`) REFERENCES `inventory_warehouse` (`warehouse_id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入库单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_inbound_order`
--
-- WHERE:  inbound_id=1

LOCK TABLES `inventory_inbound_order` WRITE;
/*!40000 ALTER TABLE `inventory_inbound_order` DISABLE KEYS */;
INSERT INTO `inventory_inbound_order` VALUES (1,'IN260928001','737de8b25aaa469e9990934c3a474fe7','PURCHASE','PURCHASE',1,'PO260928001',2,NULL,55,'新晟精密工業有限公司','2026-09-28',600.0000,1840.10,NULL,NULL,NULL,NULL,NULL,1,1,NULL,NULL,NULL,NULL,'admin','2026-09-28 11:09:53','admin','2026-09-28 11:09:53','采购收货自动入库（DEV-624）批次0');
/*!40000 ALTER TABLE `inventory_inbound_order` ENABLE KEYS */;
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
-- Table structure for table `inventory_inbound_item`
--

DROP TABLE IF EXISTS `inventory_inbound_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_inbound_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `inbound_id` bigint NOT NULL COMMENT '入库单ID',
  `inventory_item_id` bigint DEFAULT NULL COMMENT '统一库存物品ID',
  `material_id` bigint DEFAULT NULL COMMENT '材料来源ID（产品入库为NULL）',
  `material_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料编码（冗余）',
  `material_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称（冗余）',
  `specification` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PCS' COMMENT '单位',
  `quantity` decimal(12,4) NOT NULL COMMENT '入库数量',
  `sampled_quantity` decimal(18,4) DEFAULT NULL COMMENT '抽检数量',
  `inspection_id` bigint DEFAULT NULL COMMENT '当前IQC检验ID',
  `inspection_result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前检验结论',
  `disposition` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '不合格处置',
  `unit_price` decimal(12,4) DEFAULT NULL COMMENT '单价',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '金额',
  `batch_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '批次号',
  `iqc_batch_id` bigint DEFAULT NULL,
  `production_date` date DEFAULT NULL COMMENT '生产日期',
  `expiry_date` date DEFAULT NULL COMMENT '有效期至',
  `location_id` bigint DEFAULT NULL COMMENT '实际存放库位',
  `qualified_quantity` decimal(12,4) DEFAULT NULL COMMENT '合格数量',
  `rejected_quantity` decimal(12,4) DEFAULT NULL COMMENT '不合格数量',
  `accepted_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '最终允收数量',
  `posted_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '已过账数量',
  `reject_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '不合格原因',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `lot_id` bigint DEFAULT NULL COMMENT 'IQC 归一：关联 quality_lot（dev-20260918-026）',
  PRIMARY KEY (`item_id`),
  KEY `idx_inbound_id` (`inbound_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_location_id` (`location_id`),
  KEY `idx_inbound_item_inspection` (`inspection_id`),
  KEY `idx_inbound_inventory_item` (`inventory_item_id`),
  KEY `idx_inbound_item_iqc_batch` (`iqc_batch_id`),
  CONSTRAINT `fk_inbound_item_inventory_item` FOREIGN KEY (`inventory_item_id`) REFERENCES `inventory_item` (`inventory_item_id`),
  CONSTRAINT `fk_inbound_item_order` FOREIGN KEY (`inbound_id`) REFERENCES `inventory_inbound_order` (`inbound_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入库单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_inbound_item`
--
-- WHERE:  inbound_id=1

LOCK TABLES `inventory_inbound_item` WRITE;
/*!40000 ALTER TABLE `inventory_inbound_item` DISABLE KEYS */;
INSERT INTO `inventory_inbound_item` VALUES (1,1,NULL,1572,'RM001572','FR83 0.175（LJ-084客供）',NULL,'PCS',500.0000,NULL,NULL,NULL,NULL,3.0000,1500.03,'IN260928001-1',NULL,NULL,NULL,NULL,NULL,NULL,0.0000,0.0000,NULL,1,NULL,NULL),(2,1,NULL,1592,'RM001592','CY50W 样品 (新亚洲)',NULL,'PCS',100.0000,NULL,NULL,NULL,NULL,3.4000,340.07,'IN260928001-2',NULL,NULL,NULL,NULL,NULL,NULL,0.0000,0.0000,NULL,2,NULL,NULL);
/*!40000 ALTER TABLE `inventory_inbound_item` ENABLE KEYS */;
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
-- Table structure for table `sys_task`
--

DROP TABLE IF EXISTS `sys_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_task` (
  `task_id` bigint NOT NULL AUTO_INCREMENT,
  `task_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务编码',
  `task_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务类型：design/review/production/sample',
  `kanban_module` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'office' COMMENT '看板模块: office/emergency/production/dev',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务标题',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '任务描述',
  `biz_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务类型：PRODUCT/ORDER/BOM/ROUTING',
  `biz_id` bigint DEFAULT NULL COMMENT '关联业务ID',
  `assignee_id` bigint DEFAULT NULL COMMENT '负责人ID',
  `assignee_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人姓名',
  `assign_role` bigint DEFAULT NULL COMMENT '按角色分配',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0待处理',
  `priority` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'normal' COMMENT '优先级：urgent/high/normal/low',
  `source_event` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源事件',
  `source_id` bigint DEFAULT NULL COMMENT '来源业务ID',
  `result_id` bigint DEFAULT NULL COMMENT '产出ID（BOM/路线/附件）',
  `result_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '产出类型：bom/routing/drawing',
  `start_time` datetime DEFAULT NULL,
  `deadline` date DEFAULT NULL,
  `completed_time` datetime DEFAULT NULL,
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `test_cases` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '验收用例TC列表（逗号分隔）',
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_task_code` (`task_code`),
  KEY `idx_biz` (`biz_type`,`biz_id`),
  KEY `idx_assignee` (`assignee_id`,`status`),
  KEY `idx_type_status` (`task_type`,`status`),
  KEY `idx_source` (`source_event`,`source_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2410 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_task`
--
-- WHERE:  task_id=2408

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (2408,'dev-20260928-002','DEV','dev','【缺陷·金额】采购收货生成入库单：行金额按「收货比例先舍入4位」计算，导致单价×数量≠金额','【来源】2026-09-28 质量/采购模块 E2E 真实联调：核查 IN260928001（inbound_id=1，源 PO260928001）时发现。\n【现象】入库单明细行金额与「单价×数量」不符：行1 RM001572 收500×3.0000 应=1500.00，落库 1500.03；行2 RM001592 收100×3.4000 应=340.00，落库 340.07；主表 total_amount=1840.10（应 1840.00），合计偏差 0.10。\n【根因】InventoryInboundServiceImpl.createInboundRecordFromPurchase 计算行金额：itemAmt = item.getAmount().multiply(toIn.divide(item.getQuantity(), 4, HALF_UP)) —— 收货比例先四舍五入到 4 位小数再乘，误差被放大。\n【正确口径】itemAmt = amount × toIn ÷ quantity，中间不提前舍入，最终再舍入到 2 位（金额列 decimal(12,2)）。\n【复现条件】PO 行「金额÷数量」不可整除且分次收货时（本例 2100 收 500、600 收 100）。\n【白名单】jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java（createInboundRecordFromPurchase 行金额计算处）；排查采购退货/生产入库等同类比例金额计算。\n【验收】收货生成入库单每行 amount = ROUND(单价×收货数,2)，主表合计=各行之和；比例不可整除场景不再产生分位偏差；IN260928001 复算应为 1500.00/340.00/1840.00；编译通过。',NULL,NULL,NULL,NULL,NULL,0,'P2',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'dahuang','2026-09-28 11:23:41',NULL,'2026-09-28 11:23:41',NULL,NULL);
/*!40000 ALTER TABLE `sys_task` ENABLE KEYS */;
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
