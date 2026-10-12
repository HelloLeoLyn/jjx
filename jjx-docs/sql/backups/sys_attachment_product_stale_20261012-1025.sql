-- 备份人: dahuang
-- 原因: 清除 2026-10-10 遗留的 7 条 product 类附件（旧产品 YL1025PWOO 上传；因 product 表 TRUNCATE 后自增 ID 复用，被错挂到新建 YL001MEOO 的客供资料）
-- 风险与依据: 仅删 sys_attachment 中 id 5-11（biz_type='product'，文件路径均在 product/YL1025PWOO/...）；删前 guard 备份整表；依据 CONVENTIONS §2
-- 涉及表: sys_attachment
-- 任务码: dev-20261012-001

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
-- Table structure for table `sys_attachment`
--

DROP TABLE IF EXISTS `sys_attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '附件ID',
  `biz_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型（如: sales_order, product等）',
  `biz_id` bigint NOT NULL COMMENT '业务记录ID',
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件类别（产品文件：客供稿/模具/确认图/菲林/规范等，业务附件可空）',
  `version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本号（产品文件用）',
  `drawing_no` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图纸编号（同产品内唯一标识一张图纸）',
  `drawing_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图纸名称',
  `file_role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'ORIGINAL工程原稿/PRINT预览打印件',
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '链路追踪ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储路径',
  `file_size` bigint DEFAULT '0' COMMENT '文件大小（字节）',
  `sha256` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SHA-256(实际文件字节，服务端计算)',
  `file_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT 'MIME类型',
  `sort_order` int DEFAULT '0' COMMENT '排序号',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT '0' COMMENT '删除标记',
  `is_current` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否现行版（产品文件库，dev-20260929-023）',
  `is_controlled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否受控（工程图/技术文档）',
  `released` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已下发',
  `released_at` datetime DEFAULT NULL COMMENT '下发时间',
  `released_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '下发人',
  `customer_product_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客供资料所属产品编码',
  `customer_doc_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客供资料类型',
  `customer_doc_seq` int DEFAULT NULL COMMENT '同来源单据和资料类型的序号',
  `display_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源和资料类型组成的整理名称',
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`,`biz_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_attachment_drawing` (`biz_type`,`biz_id`,`drawing_no`,`version`,`deleted`),
  KEY `idx_attachment_hash_scope` (`sha256`,`biz_type`,`biz_id`,`deleted`),
  KEY `idx_customer_doc_source` (`biz_type`,`biz_id`,`customer_doc_type`,`customer_doc_seq`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用附件表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_attachment`
--

LOCK TABLES `sys_attachment` WRITE;
/*!40000 ALTER TABLE `sys_attachment` DISABLE KEYS */;
INSERT INTO `sys_attachment` VALUES (2,'engineering_jjx_icon',0,'others',NULL,NULL,NULL,NULL,NULL,'撕贴保护膜.svg','engineering_jjx_icon/2026-09-16/c020cde7-d497-4285-b487-93353977cf29.svg',876,NULL,'image/svg+xml',0,'','dahuang','2026-10-10 10:53:31','dahuang','2026-10-10 10:53:31',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),(3,'engineering_jjx_icon',0,'others',NULL,NULL,NULL,NULL,NULL,'防水圈.svg','engineering_jjx_icon/2026-09-16/3165f429-d35d-4d72-a34d-ecea81b80c52.svg',2772,NULL,'image/svg+xml',0,'','dahuang','2026-10-10 10:53:31','dahuang','2026-10-10 10:53:31',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),(4,'engineering_jjx_icon',0,'others',NULL,NULL,NULL,NULL,NULL,'连接器.svg','engineering_jjx_icon/2026-09-16/2d70be52-c6f2-4796-966f-638dd275508b.svg',1147,NULL,'image/svg+xml',0,'','dahuang','2026-10-10 10:53:31','dahuang','2026-10-10 10:53:31',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),(5,'product',1,'客供稿',NULL,NULL,NULL,NULL,NULL,'YL-1025PWOO(YL-208ND)-客供稿.jpg','product/YL1025PWOO/客供稿/2026-10-10/YL-1025PWOO(YL-208ND)-客供稿.jpg',415785,NULL,'image/jpeg',0,'','admin','2026-10-10 11:36:17','','2026-10-10 11:36:16',0,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),(6,'product',1,'样品照片',NULL,NULL,NULL,NULL,NULL,'样品.jpg','product/YL1025PWOO/样品照片/2026-10-10/样品.jpg',216201,NULL,'image/jpeg',0,'','admin','2026-10-10 12:19:29','','2026-10-11 18:30:06',1,0,0,0,NULL,NULL,NULL,NULL,NULL,NULL),(7,'product',1,'结构图','A','工程图5 (1)','工程图5 (1)','PRINT',NULL,'工程图5 (1).jpg','product/YL1025PWOO/结构图/2026-10-10/工程图5 (1).jpg',134215,NULL,'image/jpeg',0,'','admin','2026-10-10 15:39:43','','2026-10-10 15:40:07',0,1,1,1,'2026-10-10 15:40:08','1',NULL,NULL,NULL,NULL),(8,'product',1,'面板图','A','工程图','工程图','PRINT',NULL,'工程图.jpg','product/YL1025PWOO/面板图/2026-10-10/工程图.jpg',197826,NULL,'image/jpeg',0,'','admin','2026-10-10 17:49:50','','2026-10-10 17:50:14',0,1,1,1,'2026-10-10 17:50:15','1',NULL,NULL,NULL,NULL),(9,'product',1,'线路图','A','工程图2','工程图2','PRINT',NULL,'工程图2.jpg','product/YL1025PWOO/线路图/2026-10-10/工程图2.jpg',112320,NULL,'image/jpeg',0,'','admin','2026-10-10 17:49:50','','2026-10-10 17:50:12',0,1,1,1,'2026-10-10 17:50:13','1',NULL,NULL,NULL,NULL),(10,'product',1,'刀模参数图','A','工程图3','工程图3','PRINT',NULL,'工程图3.jpg','product/YL1025PWOO/刀模参数图/2026-10-10/工程图3.jpg',140087,NULL,'image/jpeg',0,'','admin','2026-10-10 17:49:50','','2026-10-10 17:50:11',0,1,1,1,'2026-10-10 17:50:11','1',NULL,NULL,NULL,NULL),(11,'product',1,'结构图','A','工程图4','工程图4','PRINT',NULL,'工程图4.jpg','product/YL1025PWOO/结构图/2026-10-10/工程图4.jpg',131639,NULL,'image/jpeg',0,'','admin','2026-10-10 17:49:50','','2026-10-10 17:50:09',0,1,1,1,'2026-10-10 17:50:10','1',NULL,NULL,NULL,NULL),(12,'inquiry',1,NULL,NULL,NULL,NULL,NULL,NULL,'客供稿.jpg','inquiry/2026-10-12/f7964a45-f972-410a-b044-722e1e85df4e.jpg',186991,'47bd734aee7a21495dcf764353582f437ba3f76f72cfb4ff7cc9400ec1ca1ae0','image/jpeg',0,'','admin','2026-10-12 10:01:57','admin','2026-10-12 10:01:57',0,0,0,0,NULL,NULL,'YL001MEOO','customer_drawing',1,'INQ261012001-YL001MEOO-客户图纸-01.jpg'),(13,'quotation',2,NULL,NULL,NULL,NULL,NULL,NULL,'客供稿2.jpg','quotation/2026-10-12/18f9b064-c796-4c08-87be-c926c98ff15a.jpg',255541,'7d4459bbcee7f28ef9bc93724bac85d7208e8da9c7568184ab1606827072e224','image/jpeg',0,'','admin','2026-10-12 10:03:25','admin','2026-10-12 10:03:25',0,0,0,0,NULL,NULL,'YL002MEOO','other',1,'QT261012002-YL002MEOO-其他资料-01.jpg');
/*!40000 ALTER TABLE `sys_attachment` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-12 10:25:08
