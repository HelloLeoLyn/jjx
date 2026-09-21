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
) ENGINE=InnoDB AUTO_INCREMENT=2113 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_task`
--
-- WHERE:  task_id=2111

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (2111,'dev-20260921-049','DEV','dev','送货单打印：删系统版 + 新增三联纸(241×140 针式)版式 + 纸版对齐模板','【背景】2026-09-21 用户口径：① 系统版不要了；② 纸版增加「复合三联纸」打印模式（用户已定 B 路=针式连续纸）；③ 纸版与 jjx-docs/print_template/JJX-QR-026送货单.xlsx 有 3 处出入需对齐。\n【方案】spec = jjx-docs/history/delivery-print-triplicate-dev-20260921-049.md。白名单仅 1 文件：jjx-web/src/views/sales/delivery/print.vue。\n  1) 删 layout==system 整段分支 + usePrintLayout 里的 system 选项 + 失效的 PrintCompanyHeader import（localStorage 旧值 system 由 usePrintLayout 校验自动回退到第一项）；\n  2) 新增三联版式：纸宽 241mm×高 140mm 针式连续纸，容器不用 A4Canvas，尺寸/字号全用 mm（照 production/label-print 的非 A4 先例：@page{size:auto;margin:0} + 打印时隐藏非单据内容），内缩预留走纸孔位，**不打二维码**，明细容量 6 行超出自然分页，结构照 QR-026 模板（公司名→标题行+右侧地址→TO/Attm 与 NO/DATE→9 列表头→说明行→经手人→底部公司名）；\n  3) 纸版对齐：地址移回标题行右侧（删抬头下那行）、说明行改回繁体「貨」、保留合计金额行与单价金额列。\n【未纳入】quality_template_registry id=26 的 print_mode 仍为 dual（删系统版后该值语义过期）→ 词表新值与迁移另立，待拍板，不在本任务。\n【验证】codex 自跑 vue-tsc --noEmit + check:status-enums；用户侧：打印对话框选 241×140 出三联、9 列不截断、无二维码、明细超 6 行顺延。',NULL,NULL,NULL,NULL,NULL,0,'P2',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'Hermes','2026-09-21 19:33:29',NULL,'2026-09-21 19:33:29',NULL,NULL);
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
