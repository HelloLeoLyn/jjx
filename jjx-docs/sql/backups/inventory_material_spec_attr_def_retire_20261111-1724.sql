-- 退役归档: inventory_material_spec_attr_def + inventory_material_spec_value
-- 任务: dev-20261111-011；导出人 dahuang；只读取数，不改库
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
-- Table structure for table `inventory_material_spec_attr_def`
--

DROP TABLE IF EXISTS `inventory_material_spec_attr_def`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_material_spec_attr_def` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '品类编码，对应 inventory_material_category.category_code',
  `attr_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '属性编码，如 thickness/surface',
  `attr_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '属性名称，如 厚度/表面处理',
  `input_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'number 数值 / range 区间 / select 下拉 / text 文本',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位，如 mm/g/℃',
  `options_json` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'select 选项 JSON 数组',
  `required` tinyint NOT NULL DEFAULT '0' COMMENT '1 新增时必填 0 选填',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 启用 0 停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_spec_attr` (`category_code`,`attr_code`),
  KEY `idx_spec_attr_category` (`category_code`,`status`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='deprecated 已废弃：属性真源为 inventory_material.attribute_json（dev-20261024-007）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_material_spec_attr_def`
--

/*!40000 ALTER TABLE `inventory_material_spec_attr_def` DISABLE KEYS */;
INSERT INTO `inventory_material_spec_attr_def` VALUES (1,'RM-FILM','thickness','厚度','number','mm',NULL,1,10,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(2,'RM-FILM','surface','表面处理','select',NULL,'[\"光面\",\"雾面\",\"幼砂\",\"中砂\",\"加硬\",\"幼砂加硬\",\"防刮\",\"抗UV\"]',0,20,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(3,'RM-FILM','adhesive_side','胶面','select',NULL,'[\"无胶\",\"单面\",\"双面\"]',0,30,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(4,'RM-FILM','form','形态','select',NULL,'[\"卷料\",\"片料\"]',0,40,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(5,'RM-PAPER','gram','克重','number','g',NULL,1,10,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(6,'RM-PAPER','release_force','离型力','range','g',NULL,0,20,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(7,'RM-PAPER','liner','结构','select',NULL,'[\"单面离型\",\"双面离型\"]',0,30,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(8,'RM-PAPER','base_paper','底纸','select',NULL,'[\"格拉辛\",\"牛皮纸\",\"PET膜\"]',0,40,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(9,'RM-ADHESIVE','adhesive_system','胶系','select',NULL,'[\"亚克力\",\"硅胶\",\"橡胶\",\"聚氨酯\"]',0,10,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(10,'RM-ADHESIVE','thickness','厚度','number','mm',NULL,1,20,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(11,'RM-ADHESIVE','initial_tack','初粘','number','号',NULL,0,30,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(12,'RM-ADHESIVE','adhesive_side','胶面','select',NULL,'[\"单面\",\"双面\"]',0,40,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(13,'RM-INK','ink_type','油墨类型','select',NULL,'[\"UV油墨\",\"水性油墨\",\"溶剂型油墨\"]',1,10,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(14,'RM-INK','cure','固化方式','select',NULL,'[\"UV固化\",\"热风干燥\",\"自然干燥\"]',0,20,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(15,'RM-INK','substrate','适用基材','select',NULL,'[\"PET\",\"PVC\",\"纸张\",\"PC\"]',0,30,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(16,'AUX-ADHESIVE','substrate','基材','select',NULL,'[\"无基材\",\"PET\",\"PVC\",\"泡棉\",\"无纺布\"]',1,10,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(17,'AUX-ADHESIVE','adhesive_system','胶系','select',NULL,'[\"亚克力\",\"硅胶\",\"橡胶\"]',0,20,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(18,'AUX-ADHESIVE','thickness','厚度','number','mm',NULL,0,30,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(19,'AUX-ADHESIVE','temp_resist','耐温','number','℃',NULL,0,40,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(20,'AUX-HARDWARE','material','材质','select',NULL,'[\"SUS304\",\"SUS301\",\"黄铜\",\"铝\",\"碳钢\"]',1,10,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(21,'AUX-HARDWARE','surface','表面处理','select',NULL,'[\"钝化\",\"镀镍\",\"镀金\",\"发黑\",\"喷砂\"]',0,20,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(22,'AUX-PACK','layers','层数','select',NULL,'[\"三层\",\"五层\",\"七层\"]',1,10,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(23,'AUX-PACK','printing','印刷','select',NULL,'[\"无印刷\",\"单色\",\"双色\",\"多色\"]',0,20,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(24,'AUX-PACK','pack_material','材质','select',NULL,'[\"瓦楞纸\",\"白卡纸\",\"PE\",\"PP\"]',0,30,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(25,'AUX-ELEC','package','封装','select',NULL,'[\"0802\",\"0603\",\"1206\",\"插件\"]',0,10,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(26,'AUX-ELEC','voltage','电压','number','V',NULL,0,20,1,'2026-10-23 19:44:46','2026-10-23 19:44:46'),(27,'AUX-ELEC','light_color','发光色','select',NULL,'[\"红\",\"绿\",\"蓝\",\"白\",\"黄\",\"双色\"]',0,30,1,'2026-10-23 19:44:46','2026-10-23 19:44:46');
/*!40000 ALTER TABLE `inventory_material_spec_attr_def` ENABLE KEYS */;

--
-- Table structure for table `inventory_material_spec_value`
--

DROP TABLE IF EXISTS `inventory_material_spec_value`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_material_spec_value` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_id` bigint NOT NULL COMMENT '物料ID，对应 inventory_material.material_id',
  `attr_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '属性编码',
  `attr_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '属性名称冗余，便于统计不连定义表',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '单位冗余',
  `value_text` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文本值/下拉值',
  `value_num` decimal(14,4) DEFAULT NULL COMMENT '数值（range 时为下限）',
  `value_num2` decimal(14,4) DEFAULT NULL COMMENT 'range 上限',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '取值时属性排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_spec_value` (`material_id`,`attr_code`),
  KEY `idx_spec_value_attr` (`attr_code`,`value_num`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='deprecated 已废弃：属性真源为 inventory_material.attribute_json（dev-20261024-007）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `inventory_material_spec_value`
--

/*!40000 ALTER TABLE `inventory_material_spec_value` DISABLE KEYS */;
/*!40000 ALTER TABLE `inventory_material_spec_value` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-11 17:24:08
