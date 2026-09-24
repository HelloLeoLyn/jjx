-- MySQL dump 10.13  Distrib 8.4.10, for Linux (x86_64)
--
-- Host: localhost    Database: jjx_erp_db
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
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `config_id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `config_value` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置值',
  `config_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置名称',
  `config_group` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'system' COMMENT '分组(system/business/email/sms)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `is_active` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=454 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_config`
--

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
INSERT INTO `sys_config` VALUES (1,'default_lead_time','15','默认交期天数','business',NULL,1,1,'2026-07-28 20:20:19','2026-07-28 20:20:19'),(2,'low_stock_threshold','10','低库存预警阈值','inventory',NULL,2,1,'2026-07-28 20:20:19','2026-07-28 20:20:19'),(3,'order_auto_close_days','30','订单自动关闭天数','sales',NULL,3,1,'2026-07-28 20:20:19','2026-07-28 20:20:19'),(4,'smtp_host','smtp.example.com','SMTP服务器','email',NULL,4,1,'2026-07-28 20:20:19','2026-07-28 20:20:19'),(5,'smtp_port','587','SMTP端口','email',NULL,5,1,'2026-07-28 20:20:19','2026-07-28 20:20:19'),(6,'sms_api_key','','短信API密钥','sms',NULL,6,1,'2026-07-28 20:20:19','2026-07-28 20:20:19'),(7,'company_name','深圳市精捷信科技有限公司','公司名称','pdf_template','PDF单据抬头公司名称',1,1,'2026-08-07 17:50:23','2026-09-03 20:47:35'),(8,'company_address','深圳市宝安区沙井街共和村丽城工业园F栋4楼','公司地址','pdf_template','PDF单据抬头地址',2,1,'2026-08-07 17:50:23','2026-09-04 17:16:11'),(9,'company_phone','0755-21507378','联系电话','pdf_template','PDF单据抬头电话',3,1,'2026-08-07 17:50:23','2026-09-04 17:16:11'),(10,'company_email','cg@jjx.cc','邮箱','pdf_template','PDF单据抬头邮箱',4,1,'2026-08-07 17:50:23','2026-09-04 17:16:11'),(11,'theme_color','#535a69','主题色','pdf_template','PDF单据主题色（十六进制）',5,1,'2026-08-07 17:50:23','2026-09-16 15:33:54'),(12,'show_header','1','显示公司抬头','pdf_template','是否在PDF顶部显示公司信息（1=显示 0=隐藏）',6,1,'2026-08-07 17:50:23','2026-08-07 17:50:23'),(13,'show_footer','1','显示页脚','pdf_template','是否显示页脚页号（1=显示 0=隐藏）',7,1,'2026-08-07 17:50:23','2026-08-07 17:50:23'),(14,'signature_label1','销售负责人','签名栏1标题','pdf_template','签名区第一栏标题',8,1,'2026-08-07 17:50:23','2026-08-07 17:50:23'),(15,'signature_label2','客户确认','签名栏2标题','pdf_template','签名区第二栏标题',9,1,'2026-08-07 17:50:23','2026-08-07 17:50:23'),(16,'signature_label3','日期','签名栏3标题','pdf_template','签名区第三栏标题',10,1,'2026-08-07 17:50:23','2026-08-07 17:50:23'),(17,'company_tax_no','91440300MADF8P8N5G','税号','pdf_template','公司税号（票据抬头）',11,1,'2026-08-07 19:44:48','2026-09-03 20:47:35'),(18,'company_bank','中国工商银行股份有限公司深圳沙井支行','开户行','pdf_template','公司开户银行',12,1,'2026-08-07 19:44:48','2026-09-03 20:47:35'),(19,'company_account','9007199254740991','银行账号','pdf_template','公司银行账号',13,1,'2026-08-07 19:44:48','2026-09-03 20:47:35'),(20,'company_legal','','法人代表','pdf_template','公司法人代表',14,1,'2026-08-07 19:44:48','2026-08-07 19:44:48'),(21,'company_website','','公司官网','pdf_template','公司官网地址',15,1,'2026-08-07 19:44:48','2026-08-07 19:44:48'),(22,'company_logo','','Logo地址','pdf_template','公司Logo图片URL（选填）',16,1,'2026-08-07 19:44:48','2026-08-07 19:44:48'),(23,'system_name','JJX ERP','系统名称','system','系统名称（登录页/侧边栏/浏览器标题显示）',1,1,'2026-08-07 19:49:48','2026-08-07 19:49:48'),(24,'login_title','智能制造管理系统','登录页标题','system','登录页左侧标语',2,1,'2026-08-07 19:49:48','2026-08-07 19:49:48'),(25,'login_subtitle','欢迎使用JJX ERP系统','登录页副标题','system','登录页表单上方副标题',3,1,'2026-08-07 19:49:48','2026-08-07 19:49:48'),(26,'login_copyright','© 2026 JJX ERP 版权所有','登录页版权','system','登录页底部版权信息',4,1,'2026-08-07 19:49:48','2026-08-07 19:49:48'),(27,'session_timeout','120','会话超时(分钟)','system','登录会话超时时间（分钟）',5,1,'2026-08-07 19:49:48','2026-08-07 19:49:48'),(28,'date_format','yyyy-MM-dd','日期格式','system','系统日期显示格式',6,1,'2026-08-07 19:49:48','2026-08-07 19:49:48'),(30,'production_admin','production:all','生产管理者角色名单','production_config','多个 role_key 用英文逗号分隔，填写 sys_role.role_key，如 production:all,production:dispatch_mgr',0,1,'2026-08-27 00:00:00','2026-08-29 10:59:40'),(31,'biz_no_rule.sales_order','{\"prefix\":\"SO\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','销售订单编号','biz_no_rule','SO+yyMMdd+3位日流水',10,1,'2026-08-28 18:44:01','2026-09-11 21:43:24'),(32,'biz_no_rule.quotation','{\"prefix\":\"QT\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','报价单编号','biz_no_rule','QT+yyMMdd+3位日流水',21,1,'2026-08-28 18:44:01','2026-09-23 10:30:05'),(33,'biz_no_rule.purchase_order','{\"prefix\":\"PO\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','采购订单编号','biz_no_rule','PO+yyMMdd+3位日流水',30,1,'2026-08-28 18:44:01','2026-09-23 10:30:05'),(34,'biz_no_rule.inbound','{\"prefix\":\"IN\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','采购入库单编号','biz_no_rule','IN+yyMMdd+3位日流水（采购入库已拆号，不再复用采购单号；dev-20260923-029）',40,1,'2026-08-28 18:44:01','2026-09-23 20:15:23'),(35,'biz_no_rule.outbound','{\"prefix\":\"OUT\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','出库单编号','biz_no_rule','OUT+yyMMdd+3位日流水',41,1,'2026-08-28 18:44:01','2026-09-23 10:30:05'),(36,'biz_no_rule.production_plan','{\"digits\": 3, \"prefix\": \"PM\", \"dateFormat\": \"yyMMdd\", \"resetCycle\": \"DAILY\", \"startValue\": 1}','生产主单编号','biz_no_rule','PM+yyMMdd+3位日流水（原 PL；dev-20260923-029 第 5 批）',60,1,'2026-08-28 18:44:01','2026-09-23 20:15:23'),(37,'biz_no_rule.work_report','{\"prefix\":\"WR\",\"dateFormat\":\"yyMMdd\",\"digits\":4,\"startValue\":1,\"resetCycle\":\"DAILY\"}','报工单编号','biz_no_rule','WR+yyMMdd+4位日流水（高频例外）',70,1,'2026-08-28 18:44:01','2026-09-23 10:30:05'),(74,'production_global_scope','production:all','全局生产数据范围角色名单','production_config','多个 role_key 用英文逗号分隔，填写 sys_role.role_key，如 production:all,production:dispatch_mgr',1,1,'2026-08-29 10:59:40','2026-08-29 10:59:40'),(75,'company_bank_code','1102584002258','开户行行号','pdf_template',NULL,12,1,'2026-09-03 20:47:35','2026-09-03 20:47:35'),(76,'company_fax','0755-29856700','公司传真','pdf_template','PDF单据抬头传真（2026-09-04 Leo 提供）',0,1,'2026-09-04 17:16:11','2026-09-04 17:16:11'),(77,'company_qq','1452049538','公司QQ','pdf_template','PDF单据抬头QQ（2026-09-04 Leo 提供）',0,1,'2026-09-04 17:16:11','2026-09-04 17:16:11'),(78,'biz_no_rule.sample_order','{\"prefix\":\"SP\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','样品单编号','biz_no_rule','SP+yyMMdd+3位日流水',11,1,'2026-09-07 18:50:23','2026-09-11 21:43:24'),(79,'notify_task_default_roles','production:all,admin','通用催办默认通知角色','business','dev-20260909-003 /common/notify-task 未传 roleKeys 时的默认接收角色，逗号分隔 role_key（如 production:all,admin）',90,1,'2026-09-09 11:49:22','2026-09-09 11:49:22'),(80,'ops.schema.version','218','已应用迁移版本','ops','接管登记：2026-09-10 15:33 由 hermes-agent 登记为 78',0,1,'2026-09-10 15:33:00','2026-09-24 12:06:13'),(81,'hr.emp_no.prefix','JJX','人事-工号前缀','hr','dev-20260910',0,1,'2026-09-10 17:17:01','2026-09-10 17:17:01'),(82,'hr.emp_no.digits','4','人事-工号流水位数','hr','dev-20260910',0,1,'2026-09-10 17:17:01','2026-09-10 17:17:01'),(83,'hr.idcard.key','jjx-hr-idcard-key-2026-change-me','人事-身份证加密密钥','hr','dev-20260910；生产环境请更换为强随机密钥（16/24/32 字节）',0,1,'2026-09-10 17:17:01','2026-09-10 17:17:01'),(84,'ops.schema.applied','66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,100,101,102,103,104,105,106,107,108,109,110,111,112,113,114,115,116,117,118,119,120,121,122,123,124,125,126,127,128,129,130,131,132,133,134,135,136,137,138,139,140,141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,193,194,195,196,197,198,199,200,201,202,203,204,205,206,207,208,209,210,211,214,215,216,217,218','已应用迁移集合','ops','由 scripts/db-migrate.sh 维护（逗号分隔）',0,1,'2026-09-10 17:38:52','2026-09-24 12:06:13'),(120,'biz_no_rule.sales_return','{\"prefix\":\"RTN\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','销售退货单编号','biz_no_rule','RTN+yyMMdd+3位日流水',12,1,'2026-09-11 21:43:24','2026-09-11 21:43:24'),(121,'biz_no_rule.sales_delivery','{\"prefix\":\"DL\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','销售发货单编号','biz_no_rule','DL+yyMMdd+3位日流水',13,1,'2026-09-11 21:43:24','2026-09-11 21:43:24'),(122,'biz_no_rule.customer','{\"prefix\":\"CUS\",\"dateFormat\":\"\",\"digits\":5,\"startValue\":1,\"resetCycle\":\"NONE\"}','客户编码','biz_no_rule','CUS+5位全局流水',14,1,'2026-09-11 21:43:24','2026-09-11 21:43:24'),(123,'biz_no_rule.inquiry','{\"prefix\":\"INQ\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','询价单编号','biz_no_rule','INQ+yyMMdd+3位日流水',20,1,'2026-09-11 21:43:24','2026-09-23 10:30:05'),(124,'biz_no_rule.supplier','{\"prefix\":\"SUP\",\"dateFormat\":\"\",\"digits\":5,\"startValue\":1,\"resetCycle\":\"NONE\"}','供应商编码','biz_no_rule','SUP+5位全局流水',31,1,'2026-09-11 21:43:24','2026-09-11 21:43:24'),(125,'biz_no_rule.stocktake','{\"prefix\":\"ST\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','盘点单编号','biz_no_rule','ST+yyMMdd+3位日流水',42,1,'2026-09-11 21:43:24','2026-09-23 10:30:05'),(126,'biz_no_rule.stock_gain','{\"prefix\":\"SI\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','盘盈入库单编号','biz_no_rule','SI+yyMMdd+3位日流水',43,1,'2026-09-11 21:43:24','2026-09-23 10:30:05'),(127,'biz_no_rule.stock_loss','{\"prefix\":\"SKL\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','盘亏出库单编号','biz_no_rule','SKL+yyMMdd+3位日流水',44,1,'2026-09-11 21:43:24','2026-09-23 10:30:05'),(128,'biz_no_rule.material','{\"prefix\":\"MTR\",\"dateFormat\":\"\",\"digits\":6,\"startValue\":1,\"resetCycle\":\"NONE\"}','物料编码','biz_no_rule','物料类型动态前缀+6位全局流水',45,1,'2026-09-11 21:43:24','2026-09-11 21:43:24'),(129,'biz_no_rule.production_order','{\"digits\": 3, \"prefix\": \"WO\", \"dateFormat\": \"yyMMdd\", \"resetCycle\": \"DAILY\", \"startValue\": 1}','生产工单编号','biz_no_rule','WO+yyMMdd+3位日流水（两套工单号已合一；dev-20260923-029 第 5 批）',61,1,'2026-09-11 21:43:24','2026-09-23 20:15:23'),(131,'biz_no_rule.product','{\"prefix\":\"PROD\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','产品编码','biz_no_rule','PROD+yyMMdd+3位日流水',72,1,'2026-09-11 21:43:24','2026-09-23 10:30:05'),(132,'biz_no_rule.biz_requirement','{\"prefix\":\"RQ\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','业务需求单编号','biz_no_rule','RQ+yyMMdd+3位日流水',80,1,'2026-09-11 21:43:24','2026-09-23 10:30:05'),(133,'biz_no_rule.material_transfer','{\"prefix\":\"TF\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','资料转移单编号','biz_no_rule','TF+yyMMdd+3位日流水',81,1,'2026-09-11 21:43:24','2026-09-23 10:30:05'),(364,'biz_no_rule.quality_lot','{\"prefix\":\"QL\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','检验批编号','biz_no_rule','QL+yyMMdd+3位日流水',90,1,'2026-09-23 10:07:20','2026-09-23 10:30:05'),(365,'biz_no_rule.quality_ncr','{\"prefix\":\"NCR\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','不良台账编号','biz_no_rule','NCR+yyMMdd+3位日流水',91,1,'2026-09-23 10:07:20','2026-09-23 10:30:05'),(366,'biz_no_rule.quality_capa','{\"prefix\":\"CAPA\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','CAPA编号','biz_no_rule','CAPA+yyMMdd+3位日流水',92,1,'2026-09-23 10:07:20','2026-09-23 10:30:05'),(367,'biz_no_rule.iqc_disposition','{\"prefix\":\"IQD\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','IQC处置单编号','biz_no_rule','IQD+yyMMdd+3位日流水',93,1,'2026-09-23 10:07:20','2026-09-23 10:30:05'),(368,'biz_no_rule.iqc_return','{\"prefix\":\"IQR\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','IQC退货单编号','biz_no_rule','IQR+yyMMdd+3位日流水',94,1,'2026-09-23 10:07:20','2026-09-23 10:30:05'),(369,'biz_no_rule.iqc_rework','{\"prefix\":\"IQW\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','IQC返工单编号','biz_no_rule','IQW+yyMMdd+3位日流水',95,1,'2026-09-23 10:07:20','2026-09-23 10:30:05'),(370,'biz_no_rule.iqc_scrap','{\"prefix\":\"IQS\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','IQC报废单编号','biz_no_rule','IQS+yyMMdd+3位日流水',96,1,'2026-09-23 10:07:20','2026-09-23 10:30:05'),(371,'biz_no_rule.finish_inbound','{\"prefix\":\"WF\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','完工入库单编号','biz_no_rule','WF+yyMMdd+3位日流水（独立出单预留）',97,1,'2026-09-23 10:07:20','2026-09-23 10:30:05'),(372,'biz_no_rule.task','{\"prefix\":\"TASK\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','生产任务编号','biz_no_rule','从属编号仍按工单派生，本配置登记备用',98,1,'2026-09-23 10:07:20','2026-09-23 10:30:05'),(373,'biz_no_rule.pick','{\"prefix\":\"PICK\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','领料单编号','biz_no_rule','从属编号仍按工单派生，本配置登记备用',99,1,'2026-09-23 10:07:20','2026-09-23 10:30:05'),(442,'quality.ncr.scrap.approval-threshold','5','报废审批件数阈值（超过该值需品质主管审批）','quality','dev-20260924-005：≤阈值一步到底；>阈值进入待审批，审批通过才计入台账',0,1,'2026-09-24 11:48:00','2026-09-24 11:48:00'),(447,'biz_no_rule.quality_scrap','{\"prefix\":\"SCR\",\"dateFormat\":\"yyMMdd\",\"digits\":3,\"startValue\":1,\"resetCycle\":\"DAILY\"}','成品报废单号规则','biz','dev-20260924-006：SCR+yyMMdd+3位，按日重置',0,1,'2026-09-24 12:00:12','2026-09-24 12:00:12');
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quality_sampling_plan`
--

DROP TABLE IF EXISTS `quality_sampling_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quality_sampling_plan` (
  `plan_id` bigint NOT NULL AUTO_INCREMENT,
  `plan_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方案名（如 AQL1.0 一般检验II级）',
  `lot_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'IQC' COMMENT '适用类型：IQC/FQC/ALL',
  `aql_value` decimal(8,3) NOT NULL DEFAULT '1.000' COMMENT 'AQL 值',
  `inspection_level` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'II' COMMENT '检验水平（I/II/III）',
  `lot_min` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '批量下限（含）',
  `lot_max` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '批量上限（含）',
  `sample_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '样本量 n',
  `accept_number` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '允收数 AC',
  `reject_number` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '拒收数 RE',
  `is_enabled` tinyint NOT NULL DEFAULT '1',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`plan_id`),
  KEY `idx_sampling_match` (`lot_type`,`aql_value`,`lot_min`,`lot_max`,`is_enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质量抽样方案（AQL）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quality_sampling_plan`
--

LOCK TABLES `quality_sampling_plan` WRITE;
/*!40000 ALTER TABLE `quality_sampling_plan` DISABLE KEYS */;
INSERT INTO `quality_sampling_plan` VALUES (2,'AQL1.0 II级 91-150','IQC',1.000,'II',91.0000,150.0000,20.0000,1.0000,2.0000,1,'示例配置，可按实际标准调整','Hermes','2026-09-17 11:53:27',NULL,'2026-09-17 11:53:27',0),(3,'AQL1.0 II级 151-280','IQC',1.000,'II',151.0000,280.0000,32.0000,1.0000,2.0000,1,'示例配置，可按实际标准调整','Hermes','2026-09-17 11:53:27',NULL,'2026-09-17 11:53:27',0),(4,'AQL1.0 II级 281-500','IQC',1.000,'II',281.0000,500.0000,50.0000,1.0000,2.0000,1,'示例配置，可按实际标准调整','Hermes','2026-09-17 11:53:27',NULL,'2026-09-17 11:53:27',0),(5,'AQL1.0 II级 501-1200','IQC',1.000,'II',501.0000,1200.0000,80.0000,2.0000,3.0000,1,'示例配置，可按实际标准调整','Hermes','2026-09-17 11:53:27',NULL,'2026-09-17 11:53:27',0),(6,'OQC-91.0000-150.0000','OQC',1.000,'II',91.0000,150.0000,20.0000,1.0000,2.0000,1,'复用 IQC AQL 区间作为 OQC 初始方案','Codex','2026-09-22 10:06:49','Codex','2026-09-22 10:06:49',0),(7,'OQC-151.0000-280.0000','OQC',1.000,'II',151.0000,280.0000,32.0000,1.0000,2.0000,1,'复用 IQC AQL 区间作为 OQC 初始方案','Codex','2026-09-22 10:06:49','Codex','2026-09-22 10:06:49',0),(8,'OQC-281.0000-500.0000','OQC',1.000,'II',281.0000,500.0000,50.0000,1.0000,2.0000,1,'复用 IQC AQL 区间作为 OQC 初始方案','Codex','2026-09-22 10:06:49','Codex','2026-09-22 10:06:49',0),(9,'OQC-501.0000-1200.0000','OQC',1.000,'II',501.0000,1200.0000,80.0000,2.0000,3.0000,1,'复用 IQC AQL 区间作为 OQC 初始方案','Codex','2026-09-22 10:06:49','Codex','2026-09-22 10:06:49',0);
/*!40000 ALTER TABLE `quality_sampling_plan` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-24 15:25:31
