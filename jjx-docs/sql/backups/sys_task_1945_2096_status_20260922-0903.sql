-- MySQL dump 10.13  Distrib 8.0.45, for Win64 (x86_64)
--
-- Host: localhost    Database: jjx_erp_db
-- ------------------------------------------------------
-- Server version	8.0.45

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
-- WHERE:  task_id IN (1945,2096)

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` (`task_id`, `task_code`, `task_type`, `kanban_module`, `title`, `description`, `biz_type`, `biz_id`, `assignee_id`, `assignee_name`, `assign_role`, `status`, `priority`, `source_event`, `source_id`, `result_id`, `result_type`, `start_time`, `deadline`, `completed_time`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `test_cases`) VALUES (1945,'dev-20260918-027','DEV','dev','【质量归一·阶段3】旧表退役 + 136/137 迁移登记对账','范围：①旧质量表降只读→归档→删除（破坏性，单独迁移+备份）；②136/137 未登记对账：只读核对实际结构→生成补偿迁移→登记 ops.schema.applied→全新库由 db-migrate.sh 全量执行验收。验收：旧表删除后无残留引用；全新库迁移链可通过。',NULL,NULL,NULL,NULL,NULL,0,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'dahuang','2026-09-18 16:30:08',NULL,'2026-09-18 16:30:08',NULL,NULL),(2096,'dev-20260921-035','DEV','dev','【待拍板】来料是否并入「判定即写不良台账」：两套不良台账的重复入库隐患','【来源】同 033/034。\n【现状证据】来料现在同时写两套检验记录：老表 production_quality_inspection（来料检验页在用）+ 新表 quality_lot（来料入库流程 InventoryInboundServiceImpl:1118 也建一份）；而写 quality_ncr 的唯一入口是 judgeLot（QualityNcrController→QualityLotController:95，只有检验批页面的「判定」能触发）。实测：QL2609210002（IQC，RM001581）有 1 件不良，但 quality_ncr 为 0 行 ⇒ **来料不良只进隔离台账（inventory_iqc_quarantine），不进不良台账**。\n【风险】若将来来料也走检验批判定，同一件不良会同时进 quality_ncr 与 inventory_iqc_quarantine，而两边都能把不良转良品（CONCESSION→adjustFinishStock / RELEASE→addReleasedQuarantineStock）⇒ 可能重复入库、账实不符。\n【待拍板】A 保持现状（来料不良的唯一台账 = 隔离台账，不良台账只服务成品）；B 来料并入不良台账（必须先定\"不良的唯一入口\"与\"唯一库存动作\"，并关掉另一侧的库存动作）。\n【不做】未拍板前不改任何写入路径、不动数据。\n【关联】033、034；相关口径见 QualityNcrServiceImpl:226-247 与 InventoryInboundServiceImpl:818-830。',NULL,NULL,NULL,NULL,NULL,0,'P2',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'dahuang','2026-09-21 18:00:52','dahuang','2026-09-21 18:00:59',NULL,NULL);
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

-- Dump completed on 2026-09-22  9:03:36
