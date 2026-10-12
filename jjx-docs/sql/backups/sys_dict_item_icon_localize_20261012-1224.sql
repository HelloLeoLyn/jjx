-- 备份人: hermes
-- 原因: JJX 工序图标由下载URL迁移为本地图标（dev-20261012-006）
-- 时间: 2026-10-12 12:24:56
-- 涉表: sys_dict_item (dict_code=engineering_jjx_icon)
-- 回滚: 恢复下列 3 行; 同步回滚 engineering_standard_process icon 与 Redis 键 system:dict:engineering_jjx_icon
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典项ID',
  `dict_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典编码(关联sys_dict)',
  `item_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典键(英文标识，如: draft, paid)',
  `item_value` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典值(实际存储值，如: 0, 1, 2 或 draft, paid)',
  `label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '显示文本(前端展示用，如: 草稿, 已支付)',
  `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '备注',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `is_active` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `ext_data` json DEFAULT NULL COMMENT '扩展数据(如: {color:"red", icon:"star"})',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  PRIMARY KEY (`item_id`),
  UNIQUE KEY `uk_dict_item` (`dict_code`,`item_key`,`tenant_id`,`deleted`),
  KEY `idx_dict_active_sort` (`dict_code`,`is_active`,`sort_order`,`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1263 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表(key-英文键, value-存储值, label-显示文本)';
/*!40101 SET character_set_client = @saved_cs_client */;
INSERT INTO `sys_dict_item` VALUES (1202,'engineering_jjx_icon','jjx-upload-2','/api/system/attachment/download/2','撕贴保护膜','{\"source\": \"UPLOAD\", \"category\": \"others\", \"previewUrl\": \"/api/system/attachment/download/2\"}',999,1,NULL,'2026-09-16 10:42:43','2026-09-16 11:14:11',0,1),(1203,'engineering_jjx_icon','jjx-upload-3','/api/system/attachment/download/3','防水圈','{\"source\": \"UPLOAD\", \"category\": \"others\", \"previewUrl\": \"/api/system/attachment/download/3\"}',999,1,NULL,'2026-09-16 10:45:24','2026-09-16 11:14:11',0,1),(1204,'engineering_jjx_icon','jjx-upload-4','/api/system/attachment/download/4','连接器','{\"source\": \"UPLOAD\", \"category\": \"others\", \"previewUrl\": \"/api/system/attachment/download/4\"}',999,1,NULL,'2026-09-16 10:45:31','2026-09-16 11:14:11',0,1);
