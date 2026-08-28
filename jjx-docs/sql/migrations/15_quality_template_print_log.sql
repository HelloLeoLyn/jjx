-- dev-20260827-027 质量记录模板打印留痕
CREATE TABLE IF NOT EXISTS quality_template_print_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_id BIGINT NOT NULL COMMENT '质量记录模板ID',
  record_no VARCHAR(30) NOT NULL COMMENT '打印时记录编号快照',
  operator_id BIGINT NULL COMMENT '打印人用户ID',
  operator_name VARCHAR(64) NULL COMMENT '打印人姓名快照',
  print_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '打印时间',
  KEY idx_template_id (template_id),
  KEY idx_print_time (print_time)
) COMMENT='质量记录模板打印留痕';
