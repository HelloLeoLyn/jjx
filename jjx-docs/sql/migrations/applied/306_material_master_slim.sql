-- dev-20261024-007 阶段A：只加列，不删列；risk: medium（DDL不可事务回滚）。
-- 顺序：007A/306加列 → 008回填分类与属性 → 007B/307删6列并适配跨模块。
-- 用户已手工备份（排除hr_employee）；经db-migrate.sh校验备份、执行、记账、归档。
-- 本迁移保留现有49列、search_blob表达式与所有历史业务数据，不做回填。
-- material_type / material_category / material_name_en / spec_supplement /
-- cost_price_from / cost_price_time 的删除由后续307在008回填后执行。
-- 307需同时适配跨模块依赖，并先调整依赖material_name_en的search_blob表达式。
-- 回滚：由用户决定恢复备份结构；本轮新增列尚无历史回填数据。
-- original_name/original_specification和302两张历史表继续保留。

ALTER TABLE inventory_material
  ADD COLUMN template_id BIGINT NULL COMMENT '属性模板；由属性保存入口同源写入',
  ADD COLUMN thickness VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '厚度/克重原样串；由属性入口派生',
  ADD COLUMN width_size VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '宽或长×宽；由属性入口派生',
  ADD COLUMN attr_meta VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    GENERATED ALWAYS AS (CASE
      WHEN JSON_TYPE(JSON_EXTRACT(attribute_json,'$.material')) = 'STRING'
      THEN JSON_UNQUOTE(JSON_EXTRACT(attribute_json,'$.material')) ELSE NULL END) STORED,
  -- 仅接受完整十进制数串；空串、混合单位、区间及其它非数字均为NULL。
  -- 先用REGEXP限定整数位/小数位，再CAST，避免严格模式下脏值或溢出导致DDL失败。
  ADD COLUMN attr_thickness_num DECIMAL(12,4)
    GENERATED ALWAYS AS (CASE
      WHEN TRIM(thickness) REGEXP '^[0-9]{1,8}([.][0-9]{1,4})?$'
      THEN CAST(TRIM(thickness) AS DECIMAL(12,4)) ELSE NULL END) STORED,
  ADD KEY idx_material_template (template_id),
  ADD KEY idx_material_attr_meta (attr_meta),
  ADD KEY idx_material_attr_thickness_num (attr_thickness_num),
  ADD CONSTRAINT fk_material_template FOREIGN KEY (template_id)
    REFERENCES inventory_attribute_template(template_id) ON DELETE SET NULL;

ALTER TABLE inventory_material_spec_attr_def
  COMMENT = 'deprecated 已废弃：属性真源为 inventory_material.attribute_json（dev-20261024-007）';
ALTER TABLE inventory_material_spec_value
  COMMENT = 'deprecated 已废弃：属性真源为 inventory_material.attribute_json（dev-20261024-007）';

-- 后续：008完成回填、Excel留档且用户确认后，另卡删除original_name/original_specification。
-- 本卡保留这两列及302两张历史表；standard_price/cost_price仍被业务读取，保留。
