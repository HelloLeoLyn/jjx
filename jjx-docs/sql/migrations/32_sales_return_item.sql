-- dev-20260901-1235 第二阶段：退货明细表
-- 幂等：IF NOT EXISTS
CREATE TABLE IF NOT EXISTS sales_return_item (
  item_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  return_id BIGINT NOT NULL COMMENT '退货单ID',
  material_id BIGINT NULL COMMENT '物料ID',
  material_code VARCHAR(64) NULL COMMENT '物料编码',
  material_name VARCHAR(200) NULL COMMENT '物料名称',
  material_spec VARCHAR(200) NULL COMMENT '规格',
  unit VARCHAR(20) NULL COMMENT '单位',
  quantity DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '退货数量',
  unit_price DECIMAL(15,2) NULL COMMENT '单价',
  amount DECIMAL(15,2) NULL COMMENT '金额',
  remark VARCHAR(500) NULL COMMENT '备注',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (item_id),
  KEY idx_return_id (return_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='销售退货单明细表';
