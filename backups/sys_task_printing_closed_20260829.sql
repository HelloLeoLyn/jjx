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
-- WHERE:  task_code IN ('dev-1786098000001','dev-20260827-026','dev-20260827-027','dev-20260827-028','dev-20260827-030','dev-20260827-031','dev-20260827-032','dev-20260827-034','dev-1785988031785')

LOCK TABLES `sys_task` WRITE;
/*!40000 ALTER TABLE `sys_task` DISABLE KEYS */;
INSERT INTO `sys_task` VALUES (662,'dev-1785988031785','general','dev','所有打印pdf都没有预览效果','追加打印pdf预览效果方案','production',NULL,NULL,'未分配',NULL,1,'normal',NULL,NULL,NULL,NULL,NULL,'2026-08-06',NULL,NULL,'2026-08-06 11:47:12',NULL,'2026-08-29 11:19:40',NULL),(721,'dev-1786098000001','general','dev','公司信息配置扩展：Logo/税号/开户行/银行账号/法人/官网','票据打印需要的完整公司信息。sys_config 加配置项（company_logo 图片上传/company_tax_no/company_bank/company_account/company_legal/company_website），打印页抬头渲染；Logo 涉及文件上传。\n【打印体系定位·2026-08-28】公司抬头=打印体系基础层（层0）：sys_config 驱动已实现大半，收尾=把 print.vue 中重复的抬头读取逻辑提取为共享抬头组件（companyLogo/税号/开户行/账号/法人/官网），全部打印页复用，避免每页重复代码。',NULL,NULL,NULL,NULL,NULL,1,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-07 19:28:52','admin','2026-08-29 11:18:08','2026-08-28 打印体系整合：已并入 dev-20260827-030'),(1183,'dev-20260827-026','DEV','dev','质量记录模板注册表：目录管理+版本+Excel导入（打印体系层1）','【根因】100 个 ISO 质量记录模板（JJX-QR-001~100，jjx-docs/JJX-质量记录一览表-提取.xlsx）需要系统化管理：版次(A/A.1)/主管部门/保存期限(2年)/模板文件/生效状态；模板文件后续逐个提供。\n【方案】1) 新表 quality_template_registry：record_no(唯一)/record_name/version/owner_dept/retention_years/category(blank=空白表/data=数据联动)/biz_type(联动型关联业务)/file_id(sys_attachment)/status(草稿/生效/停用)/sort；2) 把 Excel 导入为初始数据（100 条）；3) 管理页面：目录列表+上传模板文件+换版(新文件+version+is_current，参照 engineering_bom 版本模式)+生效/停用；4) 模板文件存 sys_attachment 复用上传/版本/回收站。\n【要求】层1 不涉及打印实现；纯管理端。\n【体系依赖】基于打印体系层0：A4Canvas(718已定)+公司抬头共享组件(721)+编号规则(722)+打印审计(@Log/TraceTimeline)。',NULL,NULL,NULL,NULL,NULL,2,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-28 17:01:04','admin','2026-08-28 18:31:23','登记：质量记录打印体系 2026-08-28；2026-08-28 代码已交付，待用户审核'),(1184,'dev-20260827-027','DEV','dev','质量记录打印中心：目录筛选+空白表打印（打印体系层2）','【根因】100 个质量记录模板需要统一打印入口；打印方式已定=前端 A4 布局（A4Canvas 组件，任务 716-720 成果：各模块 print.vue + demo/A4PrintDemo.vue）。\n【方案】1) 打印中心页面：按 主管部门/类别(空白表/数据联动)/关键字 筛选目录；2) 空白表：以 A4Canvas 布局渲染模板内容（或加载模板文件打印），走现有打印链路；3) 数据联动型：入口显示待联动状态（层3 未做前先不开放或提示）；4) 打印动作记 @Log（入 sys_oper_log，后续接 TraceTimeline）。\n【依赖】dev-20260827-026 注册表。\n【体系依赖】基于打印体系层0：A4Canvas(718已定)+公司抬头共享组件(721)+编号规则(722)+打印审计(@Log/TraceTimeline)。',NULL,NULL,NULL,NULL,NULL,2,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-28 17:01:04','admin','2026-08-28 18:31:23','登记：质量记录打印体系 2026-08-28；2026-08-28 代码已交付，待用户审核'),(1185,'dev-20260827-028','DEV','dev','质量记录数据联动打印第一批：成品检验/进料检验/生产日报/首件/返工返修（层3 P0）','【根因】5 个高频质量记录有现成业务数据，应自动带数打印：QR-039成品检验报告←FQC、QR-037进料检验报告←采购收货质检、QR-043生产日报表←报工聚合、QR-082/083首件检查表←工序首检、QR-073返工返修单←FQC FAIL/rework。\n【方案】1) 每个模板一个 A4Canvas 打印页（参照 production/quality/print.vue 等现有 print.vue 模式）；2) 入口：对应业务模块（质检单/报工/工序）加打印按钮 + 打印中心联动入口；3) 数据带出：FQC 检验单数据/报工数量工时聚合/首检记录/返工信息，带出后可人工补充再打印；4) 打印记录入 @Log。\n【依赖】026/027。\n【备注】P1/P2 联动批次（制造指令单/送货单/领料单/QC日报/合同评审等）后续按此模式逐个排期，非最终清单，继续讨论。\n【体系依赖】基于打印体系层0：A4Canvas(718已定)+公司抬头共享组件(721)+编号规则(722)+打印审计(@Log/TraceTimeline)。',NULL,NULL,NULL,NULL,NULL,2,'P2',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-28 17:01:04','admin','2026-08-28 18:31:23','登记：质量记录打印体系 2026-08-28；2026-08-28 代码已交付，待用户审核'),(1187,'dev-20260827-030','DEV','dev','打印体系·基础层：公司抬头共享组件（整合 DEV 721）','【整合来源】原任务 721（作废）。\n【内容】1) sys_config 配置项收尾：company_logo(图片上传)/company_tax_no/company_bank/company_account/company_legal/company_website；2) 把各 print.vue（采购/质检/出入库等）重复的公司抬头读取逻辑提取为共享组件 CompanyHeader（Logo/名称/税号/开户行/账号/法人/官网），全部打印页复用，消除重复代码；3) 配置管理页（系统参数）。\n【体系】打印体系层0 基础能力，所有打印（业务单据+质量记录）前置依赖。\n【组件可扩展要求·2026-08-28】PrintCompanyHeader 组件 props 设计：variant(布局变体 left/center/compact)、showFields(控制显示字段数组：taxNo/legal/bank/account/website/contact，空=全显)、size(compact 精简模式用于标签打印等场景)；默认值全部读 sys_config，props 可覆盖；未来新增字段只改组件内部，调用方零改动。',NULL,NULL,NULL,NULL,NULL,2,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-28 17:07:30','admin','2026-08-28 18:31:23','打印体系整合 2026-08-28；2026-08-28 代码已交付，待用户审核'),(1188,'dev-20260827-031','DEV','dev','打印体系·基础层：单据编号规则配置化（整合 DEV 722）','【整合来源】原任务 722（作废）。\n【内容】各单据编号前缀（报价QT/订单SO/入库IN等）与日期格式/序号位数改为可配置（替代代码写死）；质量记录 report_no（WR-）与业务单据号统一走配置。\n【体系】打印体系层0 基础能力前置项。',NULL,NULL,NULL,NULL,NULL,2,'P1',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-28 17:07:30','admin','2026-08-28 18:44:27','打印体系整合 2026-08-28；2026-08-28 代码已交付，待用户审核'),(1189,'dev-20260827-032','DEV','dev','打印体系·层1：收款单/销售发票/采购计划 A4Canvas 打印（整合 DEV 720）','【整合来源】原任务 720（作废）。\n【内容】三类单据无导出接口：补 A4Canvas 打印页（参照现有 print.vue 模式 + 层0 公司抬头组件）。\n【体系】打印体系层1 业务单据打印缺口补齐。',NULL,NULL,NULL,NULL,NULL,2,'P2',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-28 17:07:30','admin','2026-08-28 19:27:39','打印体系整合 2026-08-28；2026-08-28 代码已交付，待用户审核'),(1191,'dev-20260827-034','DEV','dev','打印体系·标签打印：产品/物料标签+箱标/托盘标（整合 DEV-161）','【整合来源】原任务 DEV-161（作废，原 blocked）。\n【内容】产品标签（编码/批次号/日期/数量）+ 物料标签 + 箱标/托盘标，支持扫码追溯；待层0-2 落地后评估实现方案（前端 A4 标签布局或打印组件）。\n【体系】打印体系层0 标签能力。',NULL,NULL,NULL,NULL,NULL,2,'P3',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-08-28 17:07:30','admin','2026-08-28 18:59:52','打印体系整合 2026-08-28；2026-08-28 代码已交付，待用户审核');
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
