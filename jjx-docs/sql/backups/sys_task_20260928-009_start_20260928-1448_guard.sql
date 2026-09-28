-- guard 备份：dev-20260928-009 开工（置 status=1）前快照
-- 备份人：hermes  时间：2026-09-28 14:48:01
-- 风险：低（1 行状态字段）  涉及表：sys_task
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
) ENGINE=InnoDB AUTO_INCREMENT=2422 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_task`
--
-- WHERE:  task_id=2420

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (2420,'dev-20260928-009','DEV','dev','【口径·P1】入库必须经「确认入库」步骤 + 库存流水必须完整清晰（含 IQC 让步接收直接过账的缺口）','【来源】2026-09-28 E2E 联调：核查 IQC 让步接收（IQC_RELEASE）发现——品质主管点一下即直接加库存，未经「确认入库」。\n【用户口径（12:12 明确，正确模型）】处置权与入库权是两个决策、互不冲突：① 不合格品「怎么处置」（让步接收/退货/返工/报废）由品质主管定——这条不变、正确；② 「库存入不入账」由仓库通过「确认入库」定。\n【现状缺陷（实现层）】代码把这两道决策合并成一步：handleQuarantine 的 RELEASE 分支经 addReleasedQuarantineStock 直接 applyDelta 加库存（IN260928001 实测：RELEASE 2 件使库存 498→500），越过仓库的确认入库；处置单 RELEASE 直接 COMPLETED（仅 SCRAP 走 PENDING_APPROVAL）；品质主管角色(34)仅凭 quality:ncr:dispose 即可过账。\n【正确做法（拆回两步）】品质点「让步接收」→ 只生成一张「待确认入库」单据，库存不动 → 仓库在该单据上「确认入库」→ 才加库存并出流水。处置权仍归品质、入库权仍归仓库，各自独立、互不越界。\n【其他待办】③ 隔离/退货/返工/报废 目前无任何库存流水（dev-20260924-026 已删旁路写），需明确是否补规范流水；④ handleQuarantine 无 @Log，sys_oper_log 相关 0 条，需补操作日志。\n【白名单】jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java（handleQuarantine/addReleasedQuarantineStock 等）；jjx-server/src/main/java/com/jjx/inventory/controller/InventoryInboundController.java（@Log）；前端确认交互限 iqc-quarantine 相关页面。\n【验收】让步接收不再直接加库存，改为生成待确认入库单；仓库确认后库存与流水正确（含批次/来源/操作人/前后数量）；其余三类动作流水与日志清晰；mvn -o compile 通过。',NULL,NULL,NULL,NULL,NULL,0,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'dahuang','2026-09-28 12:09:26','dahuang','2026-09-28 12:12:35','描述已按用户 12:12 口径更新：处置权（品质主管）与入库权（仓库确认入库）是两个决策、互不冲突；缺陷在实现层把两步合并（让步接收直接过账）。待办收窄为：拆两步 + 补流水/日志。',NULL);
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
