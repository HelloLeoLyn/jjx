-- 备份人: hermes
-- 原因: 引用上述图标的工序 icon 同步改本地图标名（dev-20261012-006）
-- 时间: 2026-10-12 12:24:56
-- 涉表: engineering_standard_process (process_id 61/69)
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `engineering_standard_process` (
  `process_id` bigint NOT NULL AUTO_INCREMENT COMMENT '工序ID',
  `process_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序编码',
  `process_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序名称',
  `process_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工序类型：PRINTING印刷/CUTTING模切/LAMINATING贴合/TESTING测试/PACKAGING包装',
  `process_category` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序类别：PREPARATION准备/MAIN主要/FINISHING后处理/QUALITY质量',
  `standard_labor_hours` decimal(10,2) DEFAULT '0.00' COMMENT '标准人工工时(小时)',
  `standard_machine_hours` decimal(10,2) DEFAULT '0.00' COMMENT '标准机器工时(小时)',
  `process_param_template` json DEFAULT NULL COMMENT '工艺参数模板（JSON格式）',
  `skill_requirement` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '技能要求',
  `equipment_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备类型',
  `quality_standard` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '质量标准',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工序说明',
  `is_enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用：0否 1是',
  `display_order` int DEFAULT '0' COMMENT '显示顺序',
  `icon` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '图标',
  `has_index` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否带下标：0-不带,1-带',
  `has_work_instruction` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否常规携带作业说明：0否1是',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_final_process` tinyint NOT NULL DEFAULT '0' COMMENT '是否末道(成品)工序 工程标记',
  `is_system` tinyint(1) NOT NULL DEFAULT '0' COMMENT '系统内置：1=内置（禁编辑/禁删除），0=普通',
  PRIMARY KEY (`process_id`),
  UNIQUE KEY `uk_process_code` (`process_code`),
  KEY `idx_process_type` (`process_type`),
  KEY `idx_is_enabled` (`is_enabled`),
  KEY `idx_display_order` (`display_order`),
  KEY `idx_standard_process_type_category` (`process_type`,`process_category`,`is_enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=78 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品标准工序表';
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `engineering_standard_process` VALUES (61,'SP-418','连接器','CONNECTOR','OTHER',0.00,0.00,NULL,NULL,NULL,NULL,'连接器装配',1,61,'/api/system/attachment/download/4',0,0,'admin','2026-08-08 17:15:25','admin','2026-09-16 11:26:49',0,0),(69,'SP-406','防水圈','OTHER','OTHER',0.00,0.00,NULL,'','','','',1,0,'/api/system/attachment/download/3',0,0,'admin','2026-09-15 10:19:21','admin','2026-09-16 11:26:49',0,0);
