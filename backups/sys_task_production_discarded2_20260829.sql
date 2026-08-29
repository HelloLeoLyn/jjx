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
-- WHERE:  task_id IN (1049,1082,1093,250,254)

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (250,'DEV-30','dev','dev','生产报表','产量/效率/质量报表（后端已就绪）',NULL,NULL,NULL,NULL,NULL,3,'high',NULL,30,NULL,NULL,'2026-07-21 09:00:00',NULL,NULL,'admin','2026-07-21 09:00:00',NULL,'2026-08-03 11:46:12','原date:07-21 | tags:[\"生产\"]'),(254,'DEV-34','dev','dev','车间工单看板','方案已出，待对接前端',NULL,NULL,NULL,NULL,NULL,4,'high',NULL,34,NULL,NULL,'2026-07-21 09:00:00',NULL,NULL,'admin','2026-07-21 09:00:00',NULL,'2026-07-31 17:30:05','原date:07-21 | tags:[\"生产\"]'),(1049,'dev-1787040000001','general','dev','生产领料仓库按出库类型映射','背景：8-18 分析生产领料发现领料出库单仓库取 getDefaultWarehouseOrThrow（第一个启用仓库），生产领料从 WH01 成品仓出库，语义不对（领料应从原料仓）。方案要点：出库单创建时按 outbound_type 映射仓库（production→原料仓/WH02，SALES→成品仓/WH01），或前端/工单指定仓库；需定仓库映射规则。拍板项：映射规则。','dev',NULL,NULL,NULL,NULL,4,'normal',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-18 17:46:04',NULL,'2026-08-21 10:08:09',NULL),(1082,'dev-1787279240402','general','dev','分析： 当前登录 punch_mgr（车间主任），派工管理中未派工的三道工序仍全部显示“初始派工”。','\n当前登录 punch_mgr（车间主任），派工管理中未派工的三道工序仍全部显示“初始派工”。\n\n最终规则已拍板：\nASSIGN 初始派工只允许生产管理员/生产调度：\n- 状态允许\n- production:dispatch:assign 权限\n\n车间主任、班组长、工人一律不给 assign。\n\n要求：\n1. 检查 sys_role_menu / 用户实际角色权限，确认 punch_mgr 为什么仍有 assign。\n2. 检查 allowedActions 的 ASSIGN 投影是否只依赖 production:dispatch:assign。\n3. 修复后 punch_mgr 对未派工工序只能查看，不能看到“初始派工”。\n4. prod_manager / 派工主管仍能正常初始派工。\n5. 后端 assign API 同样必须拒绝 punch_mgr，不能只隐藏按钮。\n6. 只跑相关权限定向测试 + compile，禁止全量测试。\n7. 不提交 Git，修完报告根因和结果。','production',NULL,NULL,'未分配',NULL,4,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-21',NULL,NULL,'2026-08-21 10:27:20',NULL,'2026-08-27 23:07:35',NULL),(1093,'dev-178730365451690916308','general','dev','TT-UI-08 分配任务弹窗与收回','问题：分配与收回交互未在一个弹窗内闭环（多人选择、逐人数量、部分分配、查看直接子任务、可收回、收回后恢复容量）。\n原因：弹窗能力分散/不完整，未收敛为单一完整交互。\n修改范围：分配任务弹窗收口为单弹窗完成 多人选择+每人任务数量+部分分配保留剩余+直接子任务列表+task:recall 收回+收回后刷新恢复可分配数量。\n验收标准：一个弹窗完成上述全部操作；收回成功后弹窗立即刷新且父节点可分配数量恢复；数量校验（合计≤可分配、每人>0）前后端一致。',NULL,NULL,NULL,NULL,NULL,4,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-21 17:14:14',NULL,'2026-08-21 22:07:37',NULL);
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
