-- guard 备份：dev-20260928-005 / -007 收尾（置 status=2 + 写 remark）前快照
-- 备份人：hermes  时间：2026-09-28 11:59:27
-- 风险：低（2 行状态与备注字段）
-- 涉及表：sys_task
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
) ENGINE=InnoDB AUTO_INCREMENT=2417 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_task`
--
-- WHERE:  task_id IN (2411,2415)

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (2411,'dev-20260928-005','DEV','dev','【缺陷】质量报表 PASS/FAIL 大小写比对错误：/production/report/quality 合格/不合格数恒为 0','【来源】2026-09-28 E2E 联调排查 dev-20260928-004 同类点时发现（读码确认）。\n【现象】GET /production/report/quality（ProductionReportController 质量报表）的 passCount / failCount / passRate 恒为 0（只要有质检数据就明显偏低）。\n【根因】ProductionReportController:127-131 用大写字面量 PASS/FAIL 做大小写敏感 equals 比对 QualityLot.result；而该字段实际写入的是小写 pass/fail：QualityLotServiceImpl.applyJudgement:509 落库 result，两个调用方都传小写（InventoryInboundServiceImpl:1300 传 pass/fail；QualityFinishServiceImpl:92 默认 fail/pass），创建时 :127 亦为 pending。→ 大写比对永不命中。\n【修复】改为 equalsIgnoreCase，或统一用 QualityInspectionResultEnum.getCode()（建议后者，见 dev-20260928-006）。\n【白名单】jjx-server/src/main/java/com/jjx/production/controller/ProductionReportController.java（127-131 及同类比较）。\n【验收】库中造 1 条 result=pass + 1 条 result=fail 的 quality_lot 后，报表 passCount/failCount/passRate 正确；mvn -o compile 通过。',NULL,NULL,NULL,NULL,NULL,0,'P2',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'dahuang','2026-09-28 11:37:03',NULL,'2026-09-28 11:37:03',NULL,NULL),(2415,'dev-20260928-007','DEV','dev','【缺陷】IQC 提交时检验项 CR/MA/MI 未透传落库，导致不合格分级/原因文本/NCR 主缺陷失真','【来源】2026-09-28 E2E 联调：IN260928001 提交 + 品质主管审核通过后查库发现。用户录入 RM001572 检测项目「规格」MA=2、结论=不合格；行级 rejected=2 正确落库，但 quality_lot_item(规格) 的 cr/ma/mi 全为 0，quality_lot.defect_reason 退化为「规格：不合格」（应「规格：MA 2」）。\n【根因】InventoryInboundServiceImpl.syncIqcLot 把 InspectionItemDTO 映射为 QualityLotItemDTO 时只拷了 checkItem/standard/actualValue/result/remark/sortOrder，未拷 crQuantity/maQuantity/miQuantity（而 QualityLotServiceImpl.saveItems:455-457 本可接收这三个字段）。→ 检验项缺陷分级在 IQC 提交链路丢失。\n【影响】① 不合格原因文本丢分级（deriveIqcDefectReason 拿不到 CR/MA/MI → 只写「不合格」）；② judgeIqcLot 的 crSum/maSum/miSum 恒 0 → NCR 台账分级与主缺陷分配（CR>MA>MI）失真；③ 质量报表/追溯无法按缺陷分级统计。\n【现场证据】quality_lot_id=1；quality_lot_item（规格）result=fail、cr/ma/mi=0；quality_lot.defect_reason=规格：不合格；inventory_inbound_item(item_id=1) rejected=2（行级正确、项级丢失）。\n【修复】syncIqcLot 映射段补 target.setCrQuantity/setMaQuantity/setMiQuantity（源 DTO 已有同名 BigDecimal 字段）。\n【白名单】jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java（syncIqcLot 映射段）。\n【验收】重跑该场景：quality_lot_item（规格）ma_quantity=2、defect_reason=「规格：MA 2」；NCR 分级正确；mvn -o compile 通过。',NULL,NULL,NULL,NULL,NULL,0,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'dahuang','2026-09-28 11:41:30',NULL,'2026-09-28 11:41:30',NULL,NULL);
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
