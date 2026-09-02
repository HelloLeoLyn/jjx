-- ============================================================
-- 业务需求管理（biz_requirement）2026-09-02
-- 顶层菜单「业务管理」→ 子菜单「需求管理」
-- 通用需求载体：变更(ECN)/新增/改善/问题 统一入口
-- ============================================================

-- 需求主表
CREATE TABLE IF NOT EXISTS `biz_requirement` (
  `requirement_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '需求ID',
  `requirement_no`    VARCHAR(50)  NOT NULL COMMENT '需求单号(RQ-xxxx)',
  `requirement_type`  VARCHAR(20)  NOT NULL DEFAULT 'CHANGE' COMMENT '需求类型: CHANGE变更/ADD新增/IMPROVE改善/ISSUE问题',
  `title`             VARCHAR(200) NOT NULL COMMENT '需求标题',
  `description`       TEXT         NULL COMMENT '需求描述/变更内容',
  `source`            VARCHAR(20)  NULL COMMENT '来源: CUSTOMER客户/SALES销售/QUALITY品质/ENGINEERING工程/PRODUCTION生产/MANAGEMENT管理/OTHER其他',
  `urgency`           VARCHAR(10)  NULL DEFAULT 'normal' COMMENT '紧急度: urgent/high/normal/low',
  `expect_date`       DATE         NULL COMMENT '期望完成日期',
  `biz_type`          VARCHAR(50)  NULL COMMENT '关联业务类型(多态): product/engineering_bom/engineering_routing/sales_order/material/...',
  `biz_id`            BIGINT       NULL COMMENT '关联业务ID',
  `biz_no`            VARCHAR(100) NULL COMMENT '关联业务单号/编码(冗余展示)',
  `requirement_status` TINYINT     NOT NULL DEFAULT 1 COMMENT '状态: 1草稿/2评审中/3已通过/4执行中/5已关闭/6已驳回',
  -- ECN 扩展（type=CHANGE 工程变更场景，可空）
  `change_type`       VARCHAR(30)  NULL COMMENT '变更类型: DESIGN设计改版/PROCESS工艺调整/MATERIAL材料变更/DRAWING图纸更新/OTHER其他',
  `cutover_mode`      VARCHAR(20)  NULL COMMENT '切入方式: IMMEDIATE立即切入/BATCH按批切换',
  `need_resample`     TINYINT      NULL DEFAULT 0 COMMENT '是否重打样: 0否/1是',
  `version_before`    VARCHAR(50)  NULL COMMENT '变更前版本(BOM/路线)',
  `version_after`     VARCHAR(50)  NULL COMMENT '变更后版本(BOM/路线)',
  -- 流程
  `applicant_id`      BIGINT       NULL COMMENT '申请人ID',
  `applicant_name`    VARCHAR(50)  NULL COMMENT '申请人姓名',
  `apply_time`        DATETIME     NULL COMMENT '申请时间',
  `reviewer_id`       BIGINT       NULL COMMENT '最后审批人ID',
  `reviewer_name`     VARCHAR(50)  NULL COMMENT '最后审批人姓名',
  `review_time`       DATETIME     NULL COMMENT '审批时间',
  `review_remark`     VARCHAR(500) NULL COMMENT '审批意见',
  `close_time`        DATETIME     NULL COMMENT '关闭时间',
  `remark`            VARCHAR(500) NULL COMMENT '备注',
  `create_by`         VARCHAR(64)  NULL,
  `create_time`       DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by`         VARCHAR(64)  NULL,
  `update_time`       DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`requirement_id`),
  UNIQUE KEY `uk_req_no` (`requirement_no`),
  KEY `idx_type_status` (`requirement_type`, `requirement_status`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务需求单（通用：变更/新增/改善/问题）';

-- 会签子表（预留，MVP 可先不用，评审通过后启用）
CREATE TABLE IF NOT EXISTS `biz_requirement_approval` (
  `approval_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会签ID',
  `requirement_id` BIGINT       NOT NULL COMMENT '需求ID',
  `round_no`       INT          NOT NULL DEFAULT 1 COMMENT '会签轮次',
  `approval_role`  VARCHAR(50)  NULL COMMENT '会签角色/部门: ENGINEERING工程/MAKING制造/PURCHASE采购仓库/QUALITY品管',
  `approval_user_id` BIGINT     NULL COMMENT '会签人ID',
  `approval_user_name` VARCHAR(50) NULL COMMENT '会签人姓名',
  `approve_result`  TINYINT      NULL COMMENT '结果: 1通过/2驳回',
  `comment`         VARCHAR(500) NULL COMMENT '会签意见',
  `approve_time`    DATETIME     NULL COMMENT '会签时间',
  `create_time`     DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`approval_id`),
  KEY `idx_req` (`requirement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务需求会签记录';

-- ============================================================
-- 菜单：业务管理(顶层) → 需求管理
-- 权限: biz:requirement:view/add/edit/remove
-- ============================================================
-- 先查最大 menu_id 再插入（避免冲突）
SET @max_menu := (SELECT IFNULL(MAX(menu_id), 0) FROM sys_menu);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
(@max_menu + 1, '业务管理', 0, 850, '/biz', 'layout/index.vue', '', 1, 0, 'M', '0', '0', '', 'biz', NOW(), '业务需求统一入口（2026-09-02）'),
(@max_menu + 2, '需求管理', @max_menu + 1, 1, 'requirement', 'views/biz/requirement/index.vue', '', 1, 0, 'C', '0', '0', 'biz:requirement:view', 'list', NOW(), '业务需求单管理（变更/新增/改善/问题）'),
(@max_menu + 3, '新增需求', @max_menu + 2, 1, '', NULL, '', 1, 0, 'F', '0', '0', 'biz:requirement:add', '', NOW(), ''),
(@max_menu + 4, '编辑需求', @max_menu + 2, 2, '', NULL, '', 1, 0, 'F', '0', '0', 'biz:requirement:edit', '', NOW(), ''),
(@max_menu + 5, '删除需求', @max_menu + 2, 3, '', NULL, '', 1, 0, 'F', '0', '0', 'biz:requirement:remove', '', NOW(), '');

-- 管理员角色绑定新菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id IN (@max_menu + 1, @max_menu + 2, @max_menu + 3, @max_menu + 4, @max_menu + 5)
ON DUPLICATE KEY UPDATE menu_id = menu_id;
