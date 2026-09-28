-- guard 备份：dev-20260928-020 开工（置 status=1）前快照
-- 备份人：hermes  时间：2026-09-28 16:53:18  风险：低  涉及表：sys_task
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
) ENGINE=InnoDB AUTO_INCREMENT=2442 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_task`
--
-- WHERE:  task_id=2441

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (2441,'dev-20260928-020','DEV','dev','【P0·规范】制定并落地「数据建模 / 建表规范」——防\"每个问题加一张补丁表\"（含建表准入门槛 + 表数基线门禁）','【来源】2026-09-28 用户明确要求：新建表设计规范很重要、优先级要高，否则每个问题都会被加一张补丁表（当天实例：IQC 处置按类型拆成 6 张表 inventory_iqc_*，全库已 124 张）。\n【要做的事】把「建表闸」写成可执行规范并落地门禁（详见 jjx-docs/history/quality-model-governance-merged-20260928.md §0）：\n① **默认拒绝**：现有业务的类型/状态变体 → 加 action_type 字段；汇总/派生值 → 由事实重算；行数少结构简单不变 → sys_config / sys_dict / JSON（§14）；为了页面方便 → 不建表，后端只读聚合；与现有表职责重叠/第二真源 → 先合并。\n② **允许建表须同时满足 ≥4 条**：独立业务对象与生命周期；会被外键引用或落业务数据；一对多/多对多或需库级约束；规模与查询超出配置/字典承载；需独立权限点+管理页+留痕；能证明无法复用现有表。\n③ **流程**：提案（模板：为什么不能复用 / 与现有表关系 / 迁移与兼容 / 回滚 / 风险）→ 用户拍板 → 更新表数基线 → 走 db-migrate.sh（先备份）。\n④ **门禁**：表数基线（当前 124，只许缩小；新增须说明并同步基线）+ 反向指标扫描（表名带 _scrap/_rework/_return 等类型后缀、同一实体 ≥3 张同构表、页面按表拆 Tab）。\n【落地位置】jjx-docs/standards/CONVENTIONS.md（扩 §14 为「建表闸」或新增 §15）+ scripts/ 门禁脚本；配套 docs 门禁类似机制。\n【白名单】jjx-docs/standards/CONVENTIONS.md；scripts/（新增 check-model-baseline 之类）；必要时 jjx-docs/design/ 规范附录。\n【验收】规范文本入库并登记；表数基线脚本可运行（当前 124 为基线，新增即失败/告警）；反向指标扫描可跑；用户审核通过。\n【前置】需用户拍板：是否同意\"同类业务默认禁止新建类型表\"（= 处置建议报告 §12.8）。',NULL,NULL,NULL,NULL,NULL,0,'P0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'dahuang','2026-09-28 16:44:09',NULL,'2026-09-28 16:44:09',NULL,NULL);
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
