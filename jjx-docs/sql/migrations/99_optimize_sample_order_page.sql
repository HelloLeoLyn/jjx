-- dev-20260911-009 样品单列表改为数据库条件分页

ALTER TABLE sales_order
  ADD INDEX idx_sample_list (order_type, deleted, sample_status, create_time),
  ADD INDEX idx_sample_sales_manager (order_type, deleted, sales_manager_id, create_time);
