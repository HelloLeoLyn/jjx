-- 样品订单独立表及产品一对一拆分
-- task: dev-20260912-001
-- 前置：已完成全库备份；当前环境无需保留历史样品单

CREATE TABLE IF NOT EXISTS sales_sample_order (
  sample_order_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '样品订单扩展ID',
  order_id BIGINT NOT NULL COMMENT '销售订单ID（一对一）',
  product_id BIGINT DEFAULT NULL COMMENT '产品ID',
  product_code VARCHAR(100) DEFAULT NULL COMMENT '产品编码快照',
  product_name VARCHAR(200) DEFAULT NULL COMMENT '产品名称快照',
  product_specification VARCHAR(500) DEFAULT NULL COMMENT '产品规格快照',
  customer_material_no VARCHAR(100) DEFAULT NULL COMMENT '客户物料号',
  sample_qty INT DEFAULT 0 COMMENT '打样数量',
  unit VARCHAR(20) DEFAULT NULL COMMENT '单位',
  unit_price DECIMAL(15,4) DEFAULT 0 COMMENT '参考单价',
  amount DECIMAL(15,2) DEFAULT 0 COMMENT '样品金额',
  product_remark VARCHAR(500) DEFAULT NULL COMMENT '产品备注',
  sample_status TINYINT NOT NULL DEFAULT 1 COMMENT '样品状态',
  sample_round INT NOT NULL DEFAULT 1 COMMENT '样品迭代轮次',
  engineering_note TEXT COMMENT '工程备注',
  engineering_acceptor VARCHAR(50) DEFAULT NULL COMMENT '工程接单人',
  engineering_accept_time DATETIME DEFAULT NULL COMMENT '工程接单时间',
  reject_reason VARCHAR(500) DEFAULT NULL COMMENT '拒单原因',
  current_process VARCHAR(50) DEFAULT NULL COMMENT '当前工序',
  sample_cost DECIMAL(12,2) DEFAULT 0 COMMENT '打样成本',
  sample_work_hours DECIMAL(8,2) DEFAULT 0 COMMENT '打样工时',
  sample_tracking_no VARCHAR(100) DEFAULT NULL COMMENT '送样快递单号',
  sample_send_date DATETIME DEFAULT NULL COMMENT '送样日期',
  sample_confirm_date DATETIME DEFAULT NULL COMMENT '客户确认日期',
  confirm_by VARCHAR(50) DEFAULT NULL COMMENT '客户确认人',
  confirm_method VARCHAR(20) DEFAULT NULL COMMENT '确认方式',
  confirm_time DATETIME DEFAULT NULL COMMENT '确认时间',
  confirm_sent_time DATETIME DEFAULT NULL COMMENT '发送确认时间',
  sample_client_name VARCHAR(100) DEFAULT NULL COMMENT '客户方确认人',
  converted_order_id BIGINT DEFAULT NULL COMMENT '转量产订单ID',
  convert_order_time DATETIME DEFAULT NULL COMMENT '转量产时间',
  formal_version VARCHAR(20) DEFAULT NULL COMMENT '正式版本号',
  last_transfer_time DATETIME DEFAULT NULL COMMENT '最近资料转移时间',
  create_by VARCHAR(64) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT NULL,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (sample_order_id),
  UNIQUE KEY uk_sample_order_order (order_id),
  KEY idx_sample_order_product (product_id),
  KEY idx_sample_order_status (sample_status),
  CONSTRAINT fk_sample_order_order FOREIGN KEY (order_id) REFERENCES sales_order(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='样品订单一对一产品扩展表';

INSERT INTO sales_sample_order (
  order_id, product_id, product_code, product_name, product_specification,
  customer_material_no, sample_qty, unit, unit_price, amount,
  sample_status, sample_round, engineering_note, engineering_acceptor,
  engineering_accept_time, reject_reason, current_process, sample_cost,
  sample_work_hours, sample_tracking_no, sample_send_date, sample_confirm_date,
  confirm_by, confirm_method, confirm_time, confirm_sent_time, sample_client_name,
  converted_order_id, convert_order_time, formal_version, last_transfer_time,
  create_by, create_time, update_by, update_time, deleted
)
SELECT o.order_id, p.product_id, p.product_code, p.product_name, p.specification,
       p.customer_material_no, COALESCE(o.sample_qty, 0), p.unit, p.unit_price, p.amount,
       COALESCE(o.sample_status, 1), COALESCE(o.sample_round, 1), o.engineering_note,
       o.engineering_acceptor, o.engineering_accept_time, o.reject_reason, o.current_process,
       o.sample_cost, o.sample_work_hours, o.sample_tracking_no, o.sample_send_date,
       o.sample_confirm_date, o.confirm_by, o.confirm_method, o.confirm_time,
       o.confirm_sent_time, o.sample_client_name, o.converted_order_id, o.convert_order_time,
       o.formal_version, o.last_transfer_time, o.create_by, o.create_time, o.update_by,
       o.update_time, o.deleted
FROM sales_order o
LEFT JOIN sales_order_product p ON p.order_id = o.order_id
WHERE o.order_type = 2 AND o.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sales_sample_order s WHERE s.order_id = o.order_id);

ALTER TABLE sales_order
  DROP COLUMN sample_status,
  DROP COLUMN sample_round,
  DROP COLUMN sample_qty,
  DROP COLUMN engineering_note,
  DROP COLUMN engineering_acceptor,
  DROP COLUMN engineering_accept_time,
  DROP COLUMN reject_reason,
  DROP COLUMN current_process,
  DROP COLUMN sample_cost,
  DROP COLUMN sample_work_hours,
  DROP COLUMN sample_tracking_no,
  DROP COLUMN sample_send_date,
  DROP COLUMN sample_confirm_date,
  DROP COLUMN confirm_by,
  DROP COLUMN confirm_method,
  DROP COLUMN confirm_time,
  DROP COLUMN confirm_sent_time,
  DROP COLUMN sample_client_name,
  DROP COLUMN converted_order_id,
  DROP COLUMN convert_order_time,
  DROP COLUMN formal_version,
  DROP COLUMN last_transfer_time;
