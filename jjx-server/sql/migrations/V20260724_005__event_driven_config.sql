-- ============================================================
-- Migration: V20260724_005__event_driven_config.sql
-- 事件驱动配置表：事件定义 + 通知映射 + 看板映射
-- Applied: 2026-07-24
-- ============================================================

-- 1. 事件定义表
CREATE TABLE IF NOT EXISTS sys_event_config (
  event_id      BIGINT       AUTO_INCREMENT PRIMARY KEY,
  event_code    VARCHAR(50)  NOT NULL UNIQUE COMMENT '事件编码，如 order.confirmed',
  event_name    VARCHAR(100) NOT NULL COMMENT '事件名称',
  biz_module    VARCHAR(50)  NOT NULL COMMENT '所属模块：sales/production/inventory/purchase',
  is_enabled    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
  remark        VARCHAR(500) NULL COMMENT '备注',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_event_module (biz_module),
  INDEX idx_event_enabled (is_enabled)
) ENGINE=InnoDB COMMENT='事件定义配置';

-- 2. 事件→通知映射表
CREATE TABLE IF NOT EXISTS sys_event_notification (
  id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
  event_id      BIGINT       NOT NULL COMMENT '关联事件 ID',
  role_id       BIGINT       NOT NULL COMMENT '接收通知的角色 ID',
  template_id   BIGINT       NULL COMMENT '通知模板 ID (sys_notification_template)',
  priority      TINYINT      NOT NULL DEFAULT 0 COMMENT '优先级，越大越优先',
  is_enabled    TINYINT(1)   NOT NULL DEFAULT 1,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_en_notification_event (event_id),
  INDEX idx_en_notification_role (role_id),
  CONSTRAINT fk_en_notification_event FOREIGN KEY (event_id) REFERENCES sys_event_config(event_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='事件→通知映射配置';

-- 3. 事件→看板映射表
CREATE TABLE IF NOT EXISTS sys_event_kanban (
  id                  BIGINT       AUTO_INCREMENT PRIMARY KEY,
  event_id            BIGINT       NOT NULL COMMENT '关联事件 ID',
  kanban_type         VARCHAR(50)  NOT NULL COMMENT '看板类型：engineering/production/purchase',
  target_column       VARCHAR(50)  NOT NULL COMMENT '目标看板列：todo/doing/done',
  card_title_template VARCHAR(200) NOT NULL COMMENT '卡片标题模板，支持 ${orderNo} 等变量',
  card_desc_template  VARCHAR(500) NULL COMMENT '卡片描述模板',
  assign_role_id      BIGINT       NULL COMMENT '指派角色 ID',
  is_enabled          TINYINT(1)   NOT NULL DEFAULT 1,
  create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ek_event (event_id),
  INDEX idx_ek_type (kanban_type),
  CONSTRAINT fk_ek_event FOREIGN KEY (event_id) REFERENCES sys_event_config(event_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='事件→看板映射配置';

-- ============================================================
-- 初始事件数据
-- ============================================================
INSERT INTO sys_event_config (event_code, event_name, biz_module, is_enabled) VALUES
('order.confirmed',      '销售订单确认',    'sales',      1),
('order.bom_missing',    '产品缺少BOM',     'sales',      1),
('production.completed', '生产工单完成',    'production', 1),
('purchase.arrived',     '采购到货',        'purchase',   1),
('qc.failed',            '质检不合格',      'production', 1),
('stock.low',            '库存低于安全库存', 'inventory',  1);
