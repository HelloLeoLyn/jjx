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
-- WHERE:  task_id IN (1046,725,371,417,283,355,663,430,484,582,287,698)

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (283,'DEV-63','dev','dev','修复 Swagger v3/api-docs 返回0条路径','Knife4j配置问题',NULL,NULL,NULL,NULL,NULL,3,'high',NULL,63,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-07-31 15:50:02','tags:[\"文档\"]'),(287,'DEV-68','dev','dev','了解 Metersphere','学习开源持续测试平台',NULL,NULL,NULL,NULL,NULL,4,'low',NULL,68,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-08-29 12:25:52','tags:[\"学习\"]'),(355,'DEV-141','dev','dev','Swagger v3/api-docs修复','#63返回0条路径 + #92返回500，springdoc版本兼容问题',NULL,NULL,NULL,NULL,NULL,3,'high',NULL,141,NULL,NULL,NULL,NULL,NULL,'admin',NULL,NULL,'2026-07-31 15:51:44','tags:[\"后端\"]'),(371,'DEV-157','dev','dev','【P2】工序首检/巡检','工序执行中可做首检/巡检，不合格触发暂停。TC45',NULL,NULL,NULL,NULL,NULL,3,'normal',NULL,157,NULL,NULL,'2026-07-29 09:00:00',NULL,NULL,'admin','2026-07-29 09:00:00',NULL,'2026-08-03 10:37:55','原date:07-29 | tags:[\"生产\", \"质检\"]'),(417,'dev-1785552793126','general','dev','系统备份方案','数据库，文档，截图，备份周期，备份任务','production',NULL,NULL,'王五',NULL,3,'urgent',NULL,NULL,NULL,NULL,NULL,'2026-08-07',NULL,NULL,'2026-08-01 10:53:13',NULL,'2026-08-01 10:53:13',NULL),(430,'dev-1785558349726','general','dev','讨论','为什么@log el不支持int','production',NULL,NULL,'未分配',NULL,4,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 12:25:50',NULL,'2026-08-29 12:25:38',NULL),(484,'dev-1785586969326','general','dev','脚本新建','scripts/\n ├── start-backend.sh\n ├── start-frontend.sh\n └── start-all.sh','production',NULL,NULL,'未分配',NULL,3,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-01',NULL,NULL,'2026-08-01 20:22:49',NULL,'2026-08-01 20:25:40',NULL),(582,'dev-1785840254799','general','dev','agent 提示词','收尾提示词','production',NULL,NULL,'未分配',NULL,4,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-04',NULL,NULL,'2026-08-04 18:44:15',NULL,'2026-08-29 12:26:17',NULL),(663,'dev-1785988291974','general','dev','搜索一套满足erp的icon','【2026-08-06 最终决定：图标全部清空，后续统一风格】\n\n方案C（EP图标补齐）执行后发现：①侧边栏同级菜单缩进不一致（根因=EP 的 el-sub-menu__title 无 --el-menu-level 变量，目录项 padding-left 退化为 20px 而叶子项 40px）；②图标风格不统一（EP/SVG 混用）。\n\n【用户拍板】①缩进修复放弃，回退到原生行为；②所有菜单 icon 清空（sys_menu 215 条 icon 置 NULL）；③后续再选一套风格统一的图标方案重新配。\n\n【遗留】缩进差异为 EP 原生行为（目录20px/叶子40px），待后续选图标时一并处理。','production',NULL,NULL,'未分配',NULL,4,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-06','2026-08-06 15:39:22',NULL,'2026-08-06 11:51:32',NULL,'2026-08-29 12:26:28',NULL),(698,'dev-1786074000003','general','dev','库存导入模式②：大文件预览导入（只读+前端分页，方案A）','解析后表格改只读预览+前端分页（每页50行），去掉每行 el-input 组件（1.5万组件→几百）；校验用模式③的批量校验接口；错误行支持\"下载错误行Excel\"或\"跳过错误行仅导入正确行\"。',NULL,NULL,NULL,'未分配',NULL,3,'medium',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'dahuang','2026-08-07 11:13:26',NULL,'2026-08-07 15:51:13',NULL),(725,'dev-1786098000005','general','dev','系统参数配置：系统名称/登录页/通知开关','系统名称、登录页标题/Logo、通知方式开关（站内/邮件/短信），sys_config 配置。',NULL,NULL,NULL,NULL,NULL,1,'low',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-07 19:28:52',NULL,'2026-08-08 10:28:57',NULL),(1046,'dev-1787039000001','general','dev','流水/变更日志扩展到报价/样品/采购单据','背景：8-18 已完成销售订单的流水修复（biz_status 正确、操作名中文、编辑字段级变更对比 order.update）。其它单据（报价/样品单/采购单/工单）的 @Log bizStatus 默认0、saveOrderLog 类记录 NULL、无字段级变更对比。方案要点：①按单据枚举目标状态补 bizStatus ②编辑类接口加变更 diff（复用 OrderServiceImpl.saveOrderUpdateChangeLog 模式）③TraceTimeline 事件码中文映射扩展。拍板项：优先级/范围。','dev',NULL,NULL,NULL,NULL,2,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-18 17:16:50',NULL,'2026-08-29 12:26:48',NULL);
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
