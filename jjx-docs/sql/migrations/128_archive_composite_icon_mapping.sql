-- ============================================================================
-- 128: 历史档案复合工序图标子工序映射 —— dev-20260917-008
-- 一个复合图标可能对应多个标准工序，不能复用单值 process_id。
-- ============================================================================
USE `jjx_erp_db`;

CREATE TABLE IF NOT EXISTS engineering_process_icon_component (
  mapping_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  sample_id BIGINT NOT NULL,
  component_order INT NOT NULL,
  process_id BIGINT NOT NULL,
  work_instruction VARCHAR(500) NULL,
  create_by VARCHAR(50) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(50) NOT NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_icon_component_order (sample_id, component_order),
  KEY idx_icon_component_process (process_id),
  CONSTRAINT fk_icon_component_sample FOREIGN KEY (sample_id)
    REFERENCES engineering_process_icon_sample(sample_id),
  CONSTRAINT fk_icon_component_process FOREIGN KEY (process_id)
    REFERENCES engineering_standard_process(process_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SELECT '128 历史档案复合工序图标子工序映射表就绪' AS check_point;
