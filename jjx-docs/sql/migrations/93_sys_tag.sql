-- ============================================================================
-- 93_sys_tag.sql
-- 系统标签表（通用标签体系）+ 供应商标签接入（dev-20260911-007）
--   背景：供应商导入数据里「供应商类型」列实际是「大类*明细」的供货品类（如 塑料制品*薄膜），
--         单值枚举 supplier_type(M/E/O) 装不下多值、长文本，且无法跨模块复用。
--   改动：新增通用标签体系两张表 + 字典分组 + 菜单权限；不改动 supplier_type 语义。
--   幂等：CREATE TABLE IF NOT EXISTS / INSERT..WHERE NOT EXISTS，可重复执行。
--   无破坏性语句（无 DROP/TRUNCATE/DELETE 业务数据）。
--   作者/任务：大黄(OpenClaw) / dev-20260911-007
-- ============================================================================

SET @db := DATABASE();

-- ---- 1. 标签主数据 --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_tag` (
  `tag_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `tag_code`   varchar(64)  NOT NULL COMMENT '标签编码（分组内唯一）',
  `tag_name`   varchar(100) NOT NULL COMMENT '标签名称',
  `tag_group`  varchar(50)  NOT NULL COMMENT '标签分组（对应字典 sys_tag_group）',
  `parent_id`  bigint       DEFAULT NULL COMMENT '父标签ID（二级标签用）',
  `sort_order` int          NOT NULL DEFAULT 0 COMMENT '排序',
  `status`     tinyint      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
  `remark`     varchar(255) DEFAULT NULL COMMENT '备注',
  `create_by`  varchar(50)  DEFAULT NULL,
  `create_time` datetime    DEFAULT CURRENT_TIMESTAMP,
  `update_by`  varchar(50)  DEFAULT NULL,
  `update_time` datetime    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`   char(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1已删除',
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_tag_group_code` (`tag_group`,`tag_code`,`del_flag`),
  KEY `idx_tag_group` (`tag_group`,`status`),
  KEY `idx_tag_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统标签主数据（通用）';

-- ---- 2. 标签关联（通用多对多：任何业务表都能挂） --------------------------
CREATE TABLE IF NOT EXISTS `sys_tag_rel` (
  `rel_id`      bigint      NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `tag_id`      bigint      NOT NULL COMMENT '标签ID',
  `biz_type`    varchar(50) NOT NULL COMMENT '业务类型，如 purchase_supplier',
  `biz_id`      bigint      NOT NULL COMMENT '业务主键',
  `create_by`   varchar(50) DEFAULT NULL,
  `create_time` datetime    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`rel_id`),
  UNIQUE KEY `uk_tag_rel` (`biz_type`,`biz_id`,`tag_id`),
  KEY `idx_rel_biz` (`biz_type`,`biz_id`),
  KEY `idx_rel_tag` (`tag_id`,`biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统标签关联（通用多对多）';

-- ---- 3. 字典：标签分组 ----------------------------------------------------
INSERT INTO sys_dict (dict_code, dict_name, dict_group, remark, sort_order, is_active, deleted, tenant_id)
SELECT 'sys_tag_group', '标签分组', 'system', 'dev-20260911-007 系统标签分组', 0, 1, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'sys_tag_group');

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, remark, sort_order, is_active, deleted, tenant_id)
SELECT 'sys_tag_group', 'supplier_goods', 'supplier_goods', '供应商供货品类', '供应商标签分组', 1, 1, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code = 'sys_tag_group' AND item_key = 'supplier_goods');

-- ---- 4. 菜单：系统管理 / 基础配置 / 标签管理 ------------------------------
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,redirect,sort,create_by,create_time,update_by,update_time,remark)
SELECT '标签管理',298,4,'tag','views/system/tag/index.vue',NULL,'1','0','C','0','0','system:tag:view','PriceTag','0,1,298',NULL,'1',NULL,4,'dahuang',NOW(),'dahuang',NOW(),'dev-20260911-007 系统标签管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:tag:view');

SET @tag_menu := (SELECT menu_id FROM sys_menu WHERE perms = 'system:tag:view' ORDER BY menu_id LIMIT 1);

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,redirect,sort,create_by,create_time,update_by,update_time,remark)
SELECT '标签新增',@tag_menu,1,'',NULL,NULL,'1','0','F','0','0','system:tag:add',NULL,CONCAT('0,1,298,',@tag_menu),NULL,'1',NULL,1,'dahuang',NOW(),'dahuang',NOW(),'dev-20260911-007 按钮级权限'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:tag:add');

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,redirect,sort,create_by,create_time,update_by,update_time,remark)
SELECT '标签编辑',@tag_menu,2,'',NULL,NULL,'1','0','F','0','0','system:tag:edit',NULL,CONCAT('0,1,298,',@tag_menu),NULL,'1',NULL,2,'dahuang',NOW(),'dahuang',NOW(),'dev-20260911-007 按钮级权限'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:tag:edit');

INSERT INTO sys_menu (menu_name,parent_id,order_num,path,component,query,is_frame,is_cache,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,redirect,sort,create_by,create_time,update_by,update_time,remark)
SELECT '标签删除',@tag_menu,3,'',NULL,NULL,'1','0','F','0','0','system:tag:delete',NULL,CONCAT('0,1,298,',@tag_menu),NULL,'1',NULL,3,'dahuang',NOW(),'dahuang',NOW(),'dev-20260911-007 按钮级权限'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:tag:delete');

-- ---- 5. 授权：已持有「基础配置(298)」的角色，自动获得标签管理及其按钮 ----
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.menu_id
FROM sys_role_menu rm
JOIN sys_menu m ON m.perms IN ('system:tag:view','system:tag:add','system:tag:edit','system:tag:delete')
WHERE rm.menu_id = 298
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id = rm.role_id AND e.menu_id = m.menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, @tag_menu
FROM sys_role_menu rm
WHERE rm.menu_id = 298 AND @tag_menu IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu e WHERE e.role_id = rm.role_id AND e.menu_id = @tag_menu);
