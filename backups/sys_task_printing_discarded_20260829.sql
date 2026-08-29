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
-- Dumping data for table `sys_task`
--
-- WHERE:  task_code IN ('DEV-161','dev-1786096000001','dev-1786096000002','dev-1786097000005','dev-1786098000002')

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (375,'DEV-161','dev','dev','标签打印','产品标签(编码/批次号/日期/数量) + 物料标签 + 箱标/托盘标，扫码追溯\n【打印体系定位·2026-08-28】打印体系后续演进项（层3/标签），待层0-2 落地后评估。',NULL,NULL,NULL,NULL,NULL,1,'high',NULL,161,NULL,NULL,'2026-07-29 09:00:00',NULL,'2026-07-31 15:46:01','admin','2026-07-29 09:00:00','admin','2026-08-29 11:19:38','2026-08-28 打印体系整合：已并入 dev-20260827-034'),(712,'dev-1786096000001','general','dev','打印模板可编辑化：配置式 JSON 模板（方案1）','打印方案B的模板可编辑升级：新增 print_template 表（按单据类型存 JSON），JSON 定义区块列表/顺序/内容/样式参数；前端 PrintTemplateRenderer 读 JSON 渲染 + 后台配置页（勾选/排序/填文本）；业务人员可自改模板不动代码。\n【打印体系定位·2026-08-28】打印体系后续演进项（层3/标签），待层0-2 落地后评估。',NULL,NULL,NULL,NULL,NULL,1,'medium',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-07 18:19:53','admin','2026-08-29 11:19:35','2026-08-28 打印体系整合：已并入 dev-20260827-033'),(713,'dev-1786096000002','general','dev','打印模板拖拽式设计器评估（方案2，可编辑升级备选）','配置式JSON模板（方案1）不够自由时的升级：拖拽区块到画布/调位置大小字体，模板存JSON含坐标。开发量大，暂缓，先评估。\n【打印体系定位·2026-08-28】打印体系后续演进项（层3/标签），待层0-2 落地后评估。',NULL,NULL,NULL,NULL,NULL,1,'low',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-07 18:19:53','admin','2026-08-29 11:19:37','2026-08-28 打印体系整合：已并入 dev-20260827-033'),(720,'dev-1786097000005','general','dev','收款单/销售发票/采购计划 导出','这三类单据无导出接口。补 A4Canvas 打印页或 Excel 导出。\n【打印体系定位·2026-08-28】层1业务单据打印缺口：收款单/销售发票/采购计划 A4Canvas 打印页，参照已有 print.vue 模式+层0抬头组件。',NULL,NULL,NULL,NULL,NULL,1,'P2',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-07 19:16:48','admin','2026-08-29 11:18:12','2026-08-28 打印体系整合：已并入 dev-20260827-032'),(722,'dev-1786098000002','general','dev','单据编号规则配置化','各单据编号前缀（报价QT/订单SO/入库IN等）与日期格式/序号位数可配置，替代代码写死。\n【打印体系定位·2026-08-28】单据编号规则=打印体系基础层（层0）前置项：打印单据统一编号规则，质量记录/业务单据打印共用。',NULL,NULL,NULL,NULL,NULL,1,'medium',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-07 19:28:52','admin','2026-08-29 11:18:04','2026-08-28 打印体系整合：已并入 dev-20260827-031');
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
