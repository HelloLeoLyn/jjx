-- guard 备份：dev-20260928-003/004 开写前快照
-- 备份人：hermes  时间：2026-09-28 11:38:23
-- 原因：本次要改 sys_task 这两行的 status/remark（行级 guard）
-- 风险：低（仅 2 行状态字段）
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
) ENGINE=InnoDB AUTO_INCREMENT=2413 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_task`
--
-- WHERE:  task_id IN (2409,2410)

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (2409,'dev-20260928-003','DEV','dev','【缺陷·口径】来料检验录入：行判定（CR/MA/MI 数值链）与检验项结论（文本链）依据不一致（洞A/洞B）','【来源】2026-09-28 E2E 联调问询「CR/MA/MI 与结论的联系」，读码核实后登记。\n【现状】行判定由前端 syncIqcRowFromChecks 以 Σ(CR+MA+MI) 汇总：不良=min(收货数量,Σ)，合格=收货数量−不良，判定=Σ(CR+MA+MI)>0 或 ΣCR>0 → 不合格，否则 合格>0 → 合格；信息链②「不合格原因」由 deriveIqcReasonText / 后端 deriveIqcDefectReason 只取「检验项结论=FAIL」的项目拼接 CR/MA/MI，两条链依据不同。\n【洞A】检验项判「不合格」但 CR/MA/MI 全填 0 → 数值链 Σ=0 → 行判「合格」（合格=收货数量），与项结论自相矛盾；且 iqcRowProblems 的「判定不合格但无不合格检验项须填补充说明」校验不触发 → 存在把不良品当合格提交/接收的风险。\n【洞B】检验项判「合格」但 CR/MA/MI >0 → 数值链判「不合格」，但原因文本不列该项（非FAIL）→ 不合格原因可能为空或缺失。\n【自洽口径建议】行判定与行原因同源：以检验项结论为基准，或强约束「CR/MA/MI>0 ↔ 该项结论=不合格」（录入即时联动+校验），并把前端 iqcRowRules/detail.vue 与后端 deriveIqcDefectReason、建 NCR 判定统一（后端注释已注明与前端需同步）。\n【待确认】后端检验批判定（QualityLot 判定护栏）是否同样以 CR/MA/MI 数值为准、与检验项结论是否可能背离。\n【白名单】jjx-web/src/views/inventory/iqc/iqcRowRules.ts；jjx-web/src/views/inventory/iqc/components/MaterialChecksDialog.vue；jjx-web/src/views/inventory/iqc/detail.vue；jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java（deriveIqcDefectReason 及判定/建 NCR 链路）；如需同步 quality 域判定护栏，限相关文件。\n【验收】明确并落实唯一判定依据；洞A/洞B 场景下判定与原因一致、不再出现矛盾；相关单测与编译通过。',NULL,NULL,NULL,NULL,NULL,0,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'dahuang','2026-09-28 11:23:41',NULL,'2026-09-28 11:23:41',NULL,NULL),(2410,'dev-20260928-004','DEV','dev','【缺陷·阻塞】IQC 提交：检验项结论 PASS/FAIL 大小写比对不一致，导致「判定不合格但未录入不合格检验项」误拦（大小写 bug 复发）','【来源】2026-09-28 质量/采购 E2E 真实联调现场：RM001572（入库单 IN260928001）检测项目「规格」MA=2、结论选「不合格」，前端校验通过，提交后端报「物料RM001572判定不合格但未录入不合格检验项，请填写补充说明或补录检验项目」。\n【根因】InventoryInboundServiceImpl:1131 的 FAIL 比较用大写且大小写敏感；而检验项结论的规范值是小写 fail（QualityInspectionResultEnum.FAIL.getCode()=fail；同一文件 validateIqcInspectionItems:1393 就是 getResult().toLowerCase() 比对；前端 iqcRowRules 用 toUpperCase() 兜底）。用户确实录了不合格项，仍被误判为「没录」。\n【引入/回归】commit 9ae8e3c1（dev-20260924-013，9/24）：原校验比的是行级 itemResult（该值后端已 toUpperCase()，用大写正确）；改成看项级 chk.getResult() 时未同步改大小写口径，导致回归。\n【影响】所有「行判不合格 + 有不合格检验项 + 未填补充说明」的 IQC 提交被硬拦，品质侧无法提交不合格判定；阻塞级（本次 E2E 首跑即撞）。\n【本次修复】1131 已改为 equalsIgnoreCase（配套修复随本任务提交）。\n【同类复发点（系统性）】同一类「PASS/FAIL 大小写不统一 + 散落字面量比较」已多次出现：\n  ① ProductionReportController:127-131 用大写 PASS/FAIL 比对 quality_lot.result，而该字段实际写入小写 pass/fail（QualityLotServiceImpl:127/509，judgeIqcLot 传 pass/fail）→ 质量报表合格/不合格数恒为 0（待确认，建议另开任务）。\n  ② 各层规范值不统一：质量主枚举小写；InventoryInboundOrderMapper:28 的 SQL 用大写 FAIL 比 inspection_result（该列存的是大写 itemResult）；前端 first-piece.vue:116 直接比 PASS/FAIL。\n【治本建议】统一以 QualityInspectionResultEnum.getCode() 为唯一真源（字符串比较一律 equalsIgnoreCase 或统一大小写）、抽公共判定方法、补单测守卫；评审清单增加「PASS/FAIL 大小写」检查项。\n【白名单】jjx-server/src/main/java/com/jjx/inventory/service/impl/InventoryInboundServiceImpl.java（本次仅 1131 行）；同类治理如另开任务按清单。\n【验收】RM001572 场景（规格 MA2 + 结论不合格 + 不填补充说明）提交成功；mvn -o compile 通过；合格/不合格/复检提交不回归。',NULL,NULL,NULL,NULL,NULL,2,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'dahuang','2026-09-28 11:33:22','dahuang','2026-09-28 11:35:07','已修 InventoryInboundServiceImpl:1131 的 FAIL 比较改为 equalsIgnoreCase（大小写回归，dev-20260924-013 引入）。commit 31e8e83d，dev 已推送。验证：JDK21 mvn -o compile BUILD SUCCESS。附带备案：sys_task guard 备份 1123/1133 + backup-index.tsv 已同提交（index 另含 hermes 为 dev-20260928-002 追加的 1 行）。遗留：①同类点 ProductionReportController:127-131 用大写 PASS/FAIL 比 quality_lot.result（实际存小写 pass/fail），报表合格/不合格数疑恒 0，未修；②InventoryInboundOrderMapper:28 SQL 比大写 FAIL、前端 first-piece.vue:116 直接比 PASS/FAIL，待系统性统一（建议以枚举 getCode() 为唯一真源）；③改动需后端重新打包+重启才生效。',NULL);
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
