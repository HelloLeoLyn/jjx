-- dev-20260930-026
-- risk: low
-- 齐套 P4d（用户 2026-09-30 拍板 A）：订单缺料「欠交台账」。
-- 背景：此前的"待生产占用/缺料预警"是瞬时值，没有生命周期。本表把每张有效订单每个物料的
--   未满足需求落成一条台账（形成→补货→关闭），缺口变小/清零时闭环（status=2 已满足）。
-- 建表已登记 scripts/model-baseline.json → approvedNewTables（§15.7：带任务码/日期/提案链接）。
-- 幂等：IF NOT EXISTS。
CREATE TABLE IF NOT EXISTS order_shortage_ledger (
  ledger_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '台账ID',
  order_id        BIGINT       NOT NULL COMMENT '销售订单ID',
  order_no        VARCHAR(64)  DEFAULT NULL COMMENT '订单号',
  product_id      BIGINT       DEFAULT NULL COMMENT '产品ID',
  material_id     BIGINT       NOT NULL COMMENT '物料ID',
  material_code   VARCHAR(64)  DEFAULT NULL COMMENT '物料编码',
  material_name   VARCHAR(128) DEFAULT NULL COMMENT '物料名称',
  required_qty    DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '该单该料毛需求',
  shortage_qty    DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '当前缺口',
  fulfilled_qty   DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已满足量',
  status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=未满足 1=部分满足 2=已满足(闭环) 3=已关闭(取消)',
  due_date        DATE         DEFAULT NULL COMMENT '订单交期',
  last_checked_at DATETIME     DEFAULT NULL COMMENT '最近一次齐套重算时间',
  remark          VARCHAR(255) DEFAULT NULL,
  create_by       VARCHAR(64)  DEFAULT NULL,
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by       VARCHAR(64)  DEFAULT NULL,
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (ledger_id),
  UNIQUE KEY uk_order_shortage_ledger (order_id, material_id),
  KEY idx_shortage_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单缺料欠交台账（齐套P4d, dev-20260930-026）';

-- 收尾自检（应返回 1）
SELECT COUNT(*) AS has_ledger_table FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'order_shortage_ledger';
