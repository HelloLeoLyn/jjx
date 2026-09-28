-- guard 备份：dev-20260928-006 收尾（置 status=2 + 写 remark）前快照
-- 备份人：hermes  时间：2026-09-28 12:06:22
-- 风险：低（1 行状态与备注）  涉及表：sys_task
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
) ENGINE=InnoDB AUTO_INCREMENT=2419 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_task`
--
-- WHERE:  task_id=2412

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (2412,'dev-20260928-006','DEV','dev','【治理·防复发】PASS/FAIL 大小写口径统一：以枚举为唯一真源 + 静态扫描门禁 + 单测守卫','【来源】2026-09-28 E2E 联调：同类「PASS/FAIL 大小写不统一」反复复发（dev-20260928-004 项级比较回归、dev-20260928-005 报表恒 0），用户质疑「怎么老是没改过来」。\n【现状】规范值不统一：质量主枚举 QualityInspectionResultEnum 为小写 pass/fail/pending；但行级 inspectionResult 经 toUpperCase 后写成大写入 inventory_inbound_item.inspection_result（InventoryInboundOrderMapper:28 的 SQL 亦按大写 FAIL 比对）；前端有的地方 toUpperCase 兜底（iqcRowRules.ts），有的直接比大写（quality-print/first-piece.vue:116）。跨层靠约定、无强制，改一处易漏另一处。\n【目标】① 明确唯一真源（建议 QualityInspectionResultEnum.getCode()，全链路小写）；② 字符串比较统一 equalsIgnoreCase 或统一大小写；③ 抽公共判定方法/常量（后端、前端各一处），禁止散落字面量；④ 加静态扫描门禁或单测守卫（扫描 equals(\"PASS\")/equals(\"FAIL\") 与 result 列写入大小写是否一致）；⑤ 评审清单增加「PASS/FAIL 大小写」检查项。\n【范围】jjx-server（quality/inventory/production 的比较与写入）、jjx-web（result 展示与比较）、门禁脚本。\n【白名单】待实施时按扫描结果确定（需先产出清单）。\n【验收】产出扫描清单（位置+风险+修法）；004/005 已确认点修复；门禁或单测能拦住新增的大小写敏感 PASS/FAIL 比较；编译/构建通过。',NULL,NULL,NULL,NULL,NULL,0,'P2',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'dahuang','2026-09-28 11:37:03',NULL,'2026-09-28 11:37:03',NULL,NULL);
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
