-- 备份人: hermes-agent
-- 原因: 部门组织架构重建（清空旧 sys_dept）前的表级 guard 备份
-- 任务码: 待登记
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
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `dept_id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父部门ID',
  `dept_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门名称',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人',
  `leader_user_id` bigint DEFAULT NULL COMMENT '部门负责人用户ID（候选责任树：负责人→该部门全部后代部门人员=下属）',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '删除标志',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`dept_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_dept_leader_user` (`leader_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dept`
--

LOCK TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` VALUES (1,0,'JJX公司',0,'admin',1,'13800138001','zhangsan@jjx.com','0','0','1','2026-03-18 15:57:47','1','2026-03-18 15:57:47'),(2,1,'研发部',1,'engineer_clerk',89,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(3,1,'市场部',2,'sales_clerk',85,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(4,1,'办公室',3,'office_mgr',84,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(5,1,'生产中心',5,'prod_manager',94,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(6,5,'印刷车间',1,'print_mgr',95,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(7,6,'印刷一组',1,'print_leader1',98,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(8,6,'印刷二组',2,'print_leader2',99,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(9,5,'冲型车间',2,'punch_mgr',96,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(10,9,'冲型一组',1,'punch_leader1',100,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(11,9,'冲型二组',2,'punch_leader2',101,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(12,5,'组装车间',3,'assembly_mgr',97,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(13,12,'组装一组',1,'assembly_leader1',102,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(14,12,'组装二组',2,'assembly_leader2',103,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(15,1,'采购部',4,'buyer_clerk',91,NULL,NULL,'0','0','1','2026-08-13 19:08:35','1','2026-08-13 19:08:35'),(16,0,'深圳市精捷信科技有限公司',0,'林仪增',134,'13316940388','xxx@jjx.com','0','0','admin','2026-09-02 16:51:59','admin','2026-09-02 17:05:50'),(17,16,'部门1',0,'',NULL,'','','0','0','admin','2026-09-09 11:35:37','admin','2026-09-09 11:35:37'),(18,1,'品质部',6,NULL,NULL,NULL,NULL,'0','0','dahuang','2026-09-10 17:17:01','dahuang','2026-09-10 17:17:01');
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-10 18:46:07
