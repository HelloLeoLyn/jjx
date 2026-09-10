-- 备份人: dahuang(OpenClaw)
-- 原因: 按 Leo 2026-09-10 18:43 新组织架构 TRUNCATE 重建 sys_dept 前的 guard 备份
-- 任务码: dev-20260910-011
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

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `nick_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '昵称',
  `user_type` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '用户类型',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '邮箱',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '手机号',
  `sex` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '性别（0男 1女 2未知）',
  `avatar` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '头像',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `salt` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '盐值',
  `status` tinyint DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '0' COMMENT '删除标志（0正常 2删除）',
  `login_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '备注',
  `dept_id` bigint DEFAULT NULL COMMENT '部门Id',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `idx_user_name` (`user_name`)
) ENGINE=InnoDB AUTO_INCREMENT=137 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,'admin','系统管理员','','admin@jjx.com','13800138000','0','','$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-03-18 15:57:47','admin','2026-03-18 15:57:47','管理员账号',2),(84,'office_mgr','办公室管理员','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','办公室/系统管理',4),(85,'sales_clerk','销售业务员','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','销售',3),(86,'sales_reviewer','销售审核员','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','销售审核',3),(87,'product_clerk','产品业务员','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','产品',2),(88,'product_reviewer','产品审核员','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','产品审核',2),(89,'engineer_clerk','工程业务员','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','工程',2),(90,'engineer_reviewer','工程审核员','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','工程审核',2),(91,'buyer_clerk','采购业务员','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','采购',15),(92,'buyer_reviewer','采购审核员','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','采购审核',15),(93,'warehouse_keeper','仓管员','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','库存/仓管',4),(94,'prod_manager','生产中心主任','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','派工主管：派到班组级',5),(95,'print_mgr','印刷车间主任','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','派工主管',6),(96,'punch_mgr','冲型车间主任','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','派工主管',9),(97,'assembly_mgr','组装车间主任','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','派工主管',12),(98,'print_leader1','印刷一组组长','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','班组长',7),(99,'print_leader2','印刷二组组长','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','班组长',8),(100,'punch_leader1','冲型一组组长','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','班组长',10),(101,'punch_leader2','冲型二组组长','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','班组长',11),(102,'assembly_leader1','组装一组组长','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','班组长',13),(103,'assembly_leader2','组装二组组长','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','班组长',14),(104,'print_op1','印刷一组工人','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','操作工',7),(105,'print_op2','印刷二组工人','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','操作工',8),(106,'punch_op1','冲型一组工人','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','操作工',10),(107,'punch_op2','冲型二组工人','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','操作工',11),(108,'assembly_op1','组装一组工人','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','操作工',13),(109,'assembly_op2','组装二组工人','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-08-13 19:17:18','admin','2026-08-13 19:17:18','操作工',14),(110,'print_op1b','印刷一组工人B','00','','','0',NULL,'$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','',NULL,'admin','2026-08-20 18:07:52','admin','2026-08-20 18:07:52','WP-E 测试队员',7),(111,'print_op1c','印刷一组工人C','00','','','0',NULL,'$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','',NULL,'admin','2026-08-20 18:07:52','admin','2026-08-20 18:07:52','WP-E 测试队员',7),(112,'print_op2b','印刷二组工人B','00','','','0',NULL,'$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','',NULL,'admin','2026-08-20 18:07:52','admin','2026-08-20 18:07:52','WP-E 测试队员',8),(113,'print_op2c','印刷二组工人C','00','','','0',NULL,'$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','',NULL,'admin','2026-08-20 18:07:52','admin','2026-08-20 18:07:52','WP-E 测试队员',8),(114,'punch_op1b','冲型一组工人B','00','','','0',NULL,'$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','',NULL,'admin','2026-08-20 18:07:52','admin','2026-08-20 18:07:52','WP-E 测试队员',10),(115,'punch_op1c','冲型一组工人C','00','','','0',NULL,'$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','',NULL,'admin','2026-08-20 18:07:52','admin','2026-08-20 18:07:52','WP-E 测试队员',10),(116,'punch_op2b','冲型二组工人B','00','','','0',NULL,'$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','',NULL,'admin','2026-08-20 18:07:52','admin','2026-08-20 18:07:52','WP-E 测试队员',11),(117,'punch_op2c','冲型二组工人C','00','','','0',NULL,'$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','',NULL,'admin','2026-08-20 18:07:52','admin','2026-08-20 18:07:52','WP-E 测试队员',11),(118,'assembly_op1b','组装一组工人B','00','','','0',NULL,'$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','',NULL,'admin','2026-08-20 18:07:52','admin','2026-08-20 18:07:52','WP-E 测试队员',13),(119,'assembly_op1c','组装一组工人C','00','','','0',NULL,'$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','',NULL,'admin','2026-08-20 18:07:52','admin','2026-08-20 18:07:52','WP-E 测试队员',13),(120,'assembly_op2b','组装二组工人B','00','','','0',NULL,'$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','',NULL,'admin','2026-08-20 18:07:52','admin','2026-08-20 18:07:52','WP-E 测试队员',14),(121,'assembly_op2c','组装二组工人C','00','','','0',NULL,'$2a$10$JXqIOgxJKkPYknmh4lnXeuJoBnjEMvtbFgmVhLySPHNeQL0HSCdgS','',0,'0','',NULL,'admin','2026-08-20 18:07:52','admin','2026-08-20 18:07:52','WP-E 测试队员',14),(134,'gudy','林仪增','','gudy@xx.com','13316940388','0',NULL,'$2a$10$F03b77L8ITNV5nig1bNOeuaoCQ0NXTRTLXKM7tvKZF10NS5u2VEEq','',0,'0','',NULL,'admin','2026-09-02 17:05:03','admin','2026-09-02 17:05:03','',16),(135,'quality_inspector','来料检验员','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-09-05 22:16:03','',NULL,'',5),(136,'quality_manager','品质主管','','','','0',NULL,'$2a$10$9L/y98gzm7FD6FLGLmoZE.lyb7EJV1NFFisBQiiQcVXEHRhfVTaIi','',0,'0','',NULL,'admin','2026-09-05 22:16:03','',NULL,'',5);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `hr_employee`
--

DROP TABLE IF EXISTS `hr_employee`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hr_employee` (
  `emp_id` bigint NOT NULL AUTO_INCREMENT COMMENT '员工ID',
  `emp_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工号',
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '姓名',
  `sex` tinyint DEFAULT NULL COMMENT '性别 1男 2女',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID(sys_dept.dept_id)',
  `position` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '岗位（字典 hr_position）',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `hire_date` date DEFAULT NULL COMMENT '进厂日期',
  `leave_date` date DEFAULT NULL COMMENT '离职日期',
  `employment_status` tinyint NOT NULL DEFAULT '2' COMMENT '在职状态 1试用 2正式 3停薪留职 9离职',
  `id_card_no` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '身份证号（AES 加密存储）',
  `id_card_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '身份证地址（敏感）',
  `current_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '现住址（敏感）',
  `education` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学历（字典 hr_education）',
  `major` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '专业',
  `resume` text COLLATE utf8mb4_unicode_ci COMMENT '个人履历',
  `user_id` bigint DEFAULT NULL COMMENT '关联系统账号 sys_user.user_id（可空，一对一）',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '删除标记 0正常 2删除',
  `create_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`emp_id`),
  UNIQUE KEY `uk_hr_emp_no` (`emp_no`),
  UNIQUE KEY `uk_hr_emp_phone` (`phone`),
  UNIQUE KEY `uk_hr_emp_user` (`user_id`),
  UNIQUE KEY `uk_hr_emp_idcard` (`id_card_no`),
  KEY `idx_hr_emp_dept` (`dept_id`),
  KEY `idx_hr_emp_status` (`employment_status`)
) ENGINE=InnoDB AUTO_INCREMENT=53 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工档案（人事主数据；账号可选关联；岗位走字典 hr_position）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `hr_employee`
--

LOCK TABLES `hr_employee` WRITE;
/*!40000 ALTER TABLE `hr_employee` DISABLE KEYS */;
INSERT INTO `hr_employee` VALUES (1,'JJX0001','高剑锋',1,2,NULL,'13926576350',NULL,NULL,NULL,2,'e70bc17b759a2869ee52903ca721527b3a38ada18f9f9179861ffe350bca4a49','广东省陆丰市东海镇龙辉北路西十五巷6号','共和社区福和路西九巷22号','3',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(2,'JJX0002','叶泽清',1,5,NULL,'13662217328',NULL,'2010-09-01',NULL,2,'dc72b80f9db86ca2123b5240df34d00680d9304c19dd033da31150e092370201','广东省海丰县大湖镇渡头村624号','共和福永路西巷59号','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(3,'JJX0003','黄保金',1,4,NULL,'15118172762',NULL,'2010-03-16',NULL,2,'0c75f9d3c6893096f9c72fa8e1667daf78a6fda1d45f9dc9ebf3aad9f738251c','贵州省松桃苗族自治县黄板乡凤凰村一组','共和社区福和路西七巷59号','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(4,'JJX0004','刘三元',2,18,NULL,'13537713628',NULL,NULL,NULL,2,'09ee5a00c7d459cb0da0068f990fe903317197017ec0cfea0bc14ab3c1ae3e6b','湖南省沅江市新湾镇明月村球海村民组245号','深圳市松岗镇朗下新村','3',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(5,'JJX0005','覃建英',2,2,NULL,'15377896167','729907848@qq.com','2021-10-19',NULL,2,'7acf8bf83178923e912e126d73e2f78e62dd0853f7312bc51f1ad34d9edcd675','广西贵港市覃塘区东龙镇京榜村乔站屯66号','沙井街道步涌新村兴隆路13号1505',NULL,NULL,'2006.9~2009.6在古樟高中就读;\n学习经历\n2010.1加入百领训练机构学习公司的基本流程;\n2011.8加入光明电脑学校学习平面设计;\n2014.10加入东方培训机构学习会计;\n2017.1加入东泰驾校学习考驾照;\n2011.9~2012.5在青松广告任平面设计;\n2012.6~2014.8在名宫照明任电话销售兼设计;\n工作经历\n2014.12~2016.1在广东金矿任理财经理兼设计;\n2016.2~2016.8在广东贝莱尔任设计师;\n2016.9~2017.5在印之彩任设计师兼财务;\n2017.9~2020.1在浩彩任设计兼业务跟单;',NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(6,'JJX0006','张嘉聪',1,2,NULL,'15916012495','aja12333@163.com',NULL,NULL,2,'d84398cccc982afe484c27fac349c7057a88eb14597348660b4ed5433d1731b0','广东省佛山市南海区大沥镇黄岐洞庭路碧翠华庭5座803房','新和大道西丽城科技工业园宿舍C栋C2 603','6',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(7,'JJX0007','黄婷',2,2,NULL,'18680378969',NULL,'2026-09-01',NULL,2,'a526946cf297967290f9fd6274845b0cf30eb74bad90c4bddcffc11f1fc88939','贵州省松桃苗族自治县黄板乡凤凰村一组','共和社区福和路西七巷59号','6',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(8,'JJX0008','林辉燕',2,3,NULL,'13316589836',NULL,NULL,NULL,2,'9e1a81ae1adce2c67f67e743fbd9b04d5f56e5694cef2f3cfab39434122b9e46','广东省陆丰市潭西镇潭西村委会新溪村16号','深圳市沙进街道共和社区家和路4号','4',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(9,'JJX0009','袁辉',2,15,NULL,'13714562840',NULL,'2024-04-07',NULL,2,'c29b52f85065ec85cb588e2f4276a03ab3cfc6745259f1cdc801af7037e69d83','广东省深圳市宝安区沙井街道新沙路416号华盛新沙荟茗庭二期4栋二单元11B','广东省深圳市宝安区新桥街道新桥社区陂口一区二1号2楼','6','计算机',NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(10,'JJX0010','曹建松',1,5,NULL,'13620935512',NULL,NULL,NULL,2,'b136e4d92a7f6853e9d64498cae9e1df0a0a73a3dd6ecc5b6ab950260f4161ad','江西省上饶市鄱阳县鄱阳镇曹家村6020','深圳市沙进街道步涌围后底 2巷7号','3',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(11,'JJX0011','曾光华',1,9,'组长','13632962242',NULL,NULL,NULL,2,'3c3e57e3e776035470b4a55c0b0ead964f9c8e6f055412a1df766266ad62d459','湖南省洞口县高沙镇石门村新院组10号','共和社区丽城工业园C2栋602室','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(12,'JJX0012','温丽云',2,5,NULL,'18665889113',NULL,NULL,NULL,2,'c84d63ba9990ed47e718f0c3e19a154c317197017ec0cfea0bc14ab3c1ae3e6b','贵州省松桃苗族自治县黄板乡凤凰村一组','共和社区福和路西七巷59号','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(13,'JJX0013','刘洋',1,6,NULL,'13828717390',NULL,'2014-02-14',NULL,2,'5548ffafa6fb1f5767d5fe9f9c38cad90d1dbf955c615027042e6a4aad432b75','重庆市奉节县石岗乡田树村2组49号','共和社区丽城工业园C2栋601室','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(14,'JJX0014','林浩',1,6,NULL,'18688693141',NULL,NULL,NULL,2,'3cbbe567d92e51455cafca9bdd097f8cbfbb5ff6c0bc0f8c4789f85d343fc733','湖南省安化县东坪镇迎春路213号','共和社区福和路西十巷29号','4',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(15,'JJX0015','黄志有',1,6,NULL,'13750146403',NULL,NULL,NULL,2,'20d38d910b796de19a9c61ff051ef4d8eddc493cd4e3a20874060eceeadcf326','广东省阳山县岭背镇马落桥村委会六洞坪村6号','共和社区福和路十三巷23号505室','3',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(16,'JJX0016','林俊延',1,6,NULL,'13428207006',NULL,NULL,NULL,2,'bcf13fcc70d400d89d36c028fd3af8f54c29349bd9c1cbd864f375c55292a890','广东省陆丰市潭西镇潭东村委会新溪村104号','步涌社区上浦路100号','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(17,'JJX0017','欧志华',1,6,NULL,'15012477664',NULL,NULL,NULL,2,'a114c54bb813171a6012a011401611d5d803e563fe3f79e0d403bb5a4d386d42','广西灵川县海洋乡大庙圹村委大庙圹街上97号','共和社区福和路西十二巷26号','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(18,'JJX0018','王之鲁',1,6,NULL,'13267080850',NULL,NULL,NULL,2,'34413f65f858d5d9f9163f3b5e6016e7ed48328bc23d4f77f9325b9dbe441019','山东省阳谷县西湖镇大王楼村150号','东莞市长安镇新民村','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(19,'JJX0019','黄正亮',1,6,NULL,'17707869743',NULL,'2026-05-06',NULL,2,'4bc287dea971bb76c3bbd7b17ad923d815e2a3f860d9920ef47f5fb364b56f90','广西靖西市化峒镇群策村弄置屯7号','共和社区福和路西十三巷26-3','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(20,'JJX0020','蔡万烨',1,6,NULL,'18027413874',NULL,'2021-04-24',NULL,2,'e1863d2bd3518198b62d595c1b9f900e4f9c8e6f055412a1df766266ad62d459','广东省陆丰市河西镇湖口村委会湖口村42号','沙井街道丽城公寓A1栋','6',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(21,'JJX0021','吴启明',1,6,NULL,'15119428639',NULL,'2021-09-01',NULL,2,'7c5bb19b2174383a9bb4504f896a446fb6f24c5b58b3dae7398eea461bdf60b6','广东省陆丰市金厢镇城美村委会城美村八巷3号','共和社区丽城工业园C2栋602室','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(22,'JJX0022','林美诗',2,6,NULL,'13729510813',NULL,'2025-02-06',NULL,2,'573dda113e30f438a29a76314bc1302bcc658ccfceb8994e6b0a29c7146fe25a','广东省陆丰市潭西镇潭东村委会香巷村10号','共和社区丽城工业园C2栋606室','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(23,'JJX0023','温凤琴',2,6,NULL,'15077320778',NULL,'2025-12-09',NULL,2,'29f40a783d878780b381f112f9e949f040db62f34bb16d5c0a3a81fdc185654c','广西灵川县海洋乡大庙圹村委大庙圹街上97号','共和社区福和路西十二巷26号','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:36',NULL,NULL),(24,'JJX0024','林宋锋',1,6,NULL,'13580968914',NULL,'2026-03-16',NULL,2,'31f6cbe3d207ad782791f14c43e14ec91ecccbaba23c9912705222345dd0c039','广东省陆丰市潭西镇潭东村委会新溪村9号','共和社区福和路西五巷47号','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(25,'JJX0025','陈家俊',1,6,NULL,'19899388138',NULL,'2026-04-28',NULL,2,'8a1de70db2e4d5df280782c7295062d64f9c8e6f055412a1df766266ad62d459','广西宁明县板棍乡国华村国华屯154号','共和社区丽城工业园C2栋603室','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(26,'JJX0026','郑海红',2,9,NULL,'15012627132',NULL,NULL,NULL,2,'c5e7350b8e49d6a92c0b4b7310cb3f48af3b818752e73fba6557c5430c761742','广东省普宁市梅塘镇社山村顶圩111号','共和社区西边旧区86号','1',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(27,'JJX0027','李浩东',1,9,NULL,'13822914630',NULL,NULL,NULL,2,'04e8435571ad08d5c9440a460477cd3b6e8d6da022cb245960e3bd4c5d37d11c','广西宁明县板棍乡国华村国华屯87号','共和社区丽城工业园C2栋603室','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(28,'JJX0028','杨小明',1,9,NULL,'18681592098',NULL,'2022-05-11',NULL,2,'58f233a3a2a171a5adf0cc36631391f6f960771b44e0ef32f8ef8b617103f60b','湖南省衡阳市珠晖区东风住址北路319号','共和社区福和路西七巷59号','3',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(29,'JJX0029','陈金苹',1,9,NULL,'16626875334',NULL,'2020-10-19',NULL,2,'e519ab07b6a917c242e92c25c67f89513281f86deabf1a0e078068e1620951ac','广东省普宁市梅塘镇社山村东门46号','共和社区丽城工业园C2栋601室','4',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(30,'JJX0030','陈浩杰',1,9,NULL,'13128314563',NULL,NULL,NULL,2,'9f9c925fd726ba7081da01151b6a909278a6fda1d45f9dc9ebf3aad9f738251c','广东省普宁市梅塘镇社山村顶寨49号','共和社区丽城工业园C2栋601室','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(31,'JJX0031','林美静',2,9,NULL,'14715662771',NULL,NULL,NULL,2,'3f3afc295c79fc256b92ed698994546f5f56e5694cef2f3cfab39434122b9e46','广东省陆丰市潭西镇潭东村委会香巷村10号','共和社区丽城工业园C2栋606室','5',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(32,'JJX0032','李雪梅',2,9,NULL,'17620466921',NULL,'2026-04-08',NULL,2,'2da68f05186ed362f4264c2a85e8e1a9af3b818752e73fba6557c5430c761742','广东省阳山县大崀镇松林村委会水圳头16号','共和社区福和路十三巷23号505室','3',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(33,'JJX0033','梁均沛',1,9,NULL,'13530595834',NULL,'2026-04-16',NULL,2,'7837ad678960ed2c9d3a7da1ad0d04e73281f86deabf1a0e078068e1620951ac','广东省高州市石鼓镇尖山宁轧村23号','共和社区福和路九巷17号301室','3',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(34,'JJX0034','蔡丽君',2,5,NULL,'13077893439',NULL,NULL,NULL,2,'08fe9edb271d830bd3f55d1eae7b4df687fcf563aacff2ce5f6a6bb8f16553ff','广东省陆丰市河西镇湖口村委会湖口村42号','共和社区丽城工业园C2栋606室',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(35,'JJX0035','蔡小格',2,5,NULL,'13692937840',NULL,NULL,NULL,2,'b69e39173fe7cc581ae83cee5cd89494d6509599e4f89b636e06c714e326e3c8','广东省陆丰市河西镇湖口村委会湖口村42号','共和社区丽城工业园C2栋606室',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(36,'JJX0036','傅金枝',2,5,NULL,'15099924349',NULL,'2024-04-29',NULL,2,'f3b78818a915042d0b90f9dd968b6b8f1364f73bbe6167c5fd0456c86c6b503f','广东省陆丰市陂洋镇三岭村委会三岭九斗凹村38号','共和社区福和路西14巷201号',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(37,'JJX0037','毛春红',2,5,NULL,'19977480763',NULL,NULL,NULL,2,'ee672c85ae7f69a82a90873842f35cba75fa5a6332bb3f9e19b69a0ff65ca0a6','广西贺州市平桂区望高镇岩口温屋045-3号','共和社区丽城工业园01栋201室',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(38,'JJX0038','黄舒',2,5,NULL,'13538269877',NULL,'2025-02-18',NULL,2,'a7a35bc57c2eb981cdfad3bca15d14edbaaca5c5b6023e417a38ddb250da8579','广东省平远县长田镇长庆村耙岗','共和社区福和路东5巷3号',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(39,'JJX0039','张杜娟',2,5,NULL,'15516837783',NULL,NULL,NULL,2,'33a0c8fd0511df15a6b1b518afde92d140db62f34bb16d5c0a3a81fdc185654c','河南省正阳县油坊店乡油坊店村富民街西12号','共和社区福和路西5巷6号303室',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(40,'JJX0040','蔡小丽',2,5,NULL,'13682430851',NULL,NULL,NULL,2,'bc9198f6b9d45e72e66caadb4f98adb19959e0ea72cf6533d8dc80d3643cfa77','广东省陆丰市河西镇湖口村委会湖口村42号','共和社区福和家和路5号403室',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(41,'JJX0041','庄美祝',2,5,NULL,'18316033785',NULL,NULL,NULL,2,'bc12da104c46d4283dd29729456038fb6bc053d1402872be9038cd5f5a31b315','广东省陆丰市潭西镇崎头村委会崎头村195号','共和社区丽城工业园C2栋605室',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(42,'JJX0042','林惠婷',2,5,NULL,'16625133547',NULL,NULL,NULL,2,'2ac79a414604c849cb498964177393aa87fcf563aacff2ce5f6a6bb8f16553ff','广东省陆丰市潭西镇上埔村委会上埔村1-2号','共和社区丽城工业园C2栋605室',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(43,'JJX0043','朱秋花',2,5,NULL,'13530319741',NULL,NULL,NULL,2,'8ebbb367351b6135f1efabb294d9c79920cc916265ebc96d85bc75adc7255c3d','广东省龙川县黎咀镇满村村委会老正村9号',NULL,NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(44,'JJX0044','黎冬霞',2,5,NULL,'15766095500',NULL,NULL,NULL,2,'425fcbc7be4fd356be4a99ab14c9cb10808fd0e3fb0edb159f5c7575bd9a8941','广东省化洲市文楼镇新德四马村62号','共和社区丽城工业园C2栋604室',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(45,'JJX0045','黄沙沙',2,5,NULL,'18285685281',NULL,'2026-03-23',NULL,2,'2656c3053c55efa9660c2a46d422443abaaca5c5b6023e417a38ddb250da8579','贵洲省松桃苗族自治县黄板镇凤凰村一组','共和社区丽城工业园C2栋605室',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(46,'JJX0046','陈晓蓉',2,5,NULL,'13424254491',NULL,NULL,NULL,2,'89196a09f728de49271de581eced3832baaca5c5b6023e417a38ddb250da8579','广东省普宁市云落镇崩坎村上圩18号',NULL,NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(47,'JJX0047','黄凤格',2,5,NULL,'19943113037',NULL,'2026-05-06',NULL,2,'bf435eae1df71fe4956ffef2b4b37cb39959e0ea72cf6533d8dc80d3643cfa77','广西靖西市化峒镇群策村弄置屯7号','共和社区福和路西十三巷26-3',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(48,'JJX0048','朱思帆',2,5,NULL,'14776132642',NULL,'2026-08-21',NULL,2,'631c58c5d1b1f2b381ad8252dd95226c9c48efddbe851d807d5a84ed642592ab','广东省东源县顺天镇牛生塘村委会花坪小组50号','宝安区沙井街道大兴路',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(49,'JJX0049','杨银辉',1,5,NULL,'15298103691',NULL,'2026-08-24',NULL,2,'38ad10eaabf6833fe1bbe4d419c1cec411be969a56f544fe9f8f9d13dee167b9','四川省渠县宝城镇石桌村五组74号','共和社区福和路五巷8号',NULL,NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(50,'JJX0050','刘哲',2,18,'QC','18681034928',NULL,'2014-09-09',NULL,2,'e1b68553fdd83432fb469839a7c6d6a58893128f1702dd21e839de403302a5b2','湖南省湘阴县南阳镇仁西村四组','共和社区福和路西十巷29号','3',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(51,'JJX0051','林惠告',2,18,'QC','15119448941',NULL,NULL,NULL,2,'14e8e536082ec0f2170e07d1e419caee6c8cc610b5f6bbfd9c3885bfed9c1f9a','广东省陆丰市潭西镇恢丰村委会丰盛村112号','共和社区丽城工业园C2栋605室','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL),(52,'JJX0052','曾素凤',2,18,'IPQC','13043425943',NULL,NULL,NULL,2,'b9e2d7a999267f7ed5134de7ad672c86d6509599e4f89b636e06c714e326e3c8','广东省海丰县大湖镇湖仔村委会渡头村624号','共和福永路西巷59号','2',NULL,NULL,NULL,NULL,'0','admin','2026-09-10 18:03:37',NULL,NULL);
/*!40000 ALTER TABLE `hr_employee` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `hr_dept_mapping`
--

DROP TABLE IF EXISTS `hr_dept_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hr_dept_mapping` (
  `mapping_id` bigint NOT NULL AUTO_INCREMENT,
  `source_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '导入文件中的部门文本',
  `dept_id` bigint NOT NULL COMMENT '映射到 sys_dept.dept_id',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`mapping_id`),
  UNIQUE KEY `uk_hr_dept_mapping_source` (`source_name`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人事导入部门映射';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `hr_dept_mapping`
--

LOCK TABLES `hr_dept_mapping` WRITE;
/*!40000 ALTER TABLE `hr_dept_mapping` DISABLE KEYS */;
INSERT INTO `hr_dept_mapping` VALUES (1,'工程部',2,'dev-20260910 人事导入映射','2026-09-10 17:17:01'),(2,'业务部',3,'dev-20260910 人事导入映射','2026-09-10 17:17:01'),(3,'仓库',4,'dev-20260910 人事导入映射','2026-09-10 17:17:01'),(4,'资材部',15,'dev-20260910 人事导入映射','2026-09-10 17:17:01'),(5,'品质部',18,'dev-20260910 人事导入映射','2026-09-10 17:17:01'),(6,'品管部',18,'dev-20260910 人事导入映射','2026-09-10 17:17:01'),(7,'部品管',18,'dev-20260910 人事导入映射','2026-09-10 17:17:01'),(8,'制造部',5,'dev-20260910 人事导入映射','2026-09-10 17:17:01'),(9,'加工',5,'dev-20260910 人事导入映射','2026-09-10 17:17:01'),(10,'刀模',5,'dev-20260910 人事导入映射','2026-09-10 17:17:01'),(11,'冲型',9,'dev-20260910 人事导入映射','2026-09-10 17:17:01'),(12,'印刷',6,'dev-20260910 人事导入映射','2026-09-10 17:17:01'),(13,'组装',12,'dev-20260910 人事导入映射','2026-09-10 17:17:01');
/*!40000 ALTER TABLE `hr_dept_mapping` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-10 18:48:33
