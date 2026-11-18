-- 任务码：dev-20261118-005
-- 物料双单位策略，规格仍复用 width_mm/length_mm/grammage；不猜测回填历史单位。
-- 品类默认在既有 inventory_attribute_template.attribute_schema.measurement_defaults 维护。
ALTER TABLE inventory_material ADD COLUMN measurement_policy_json JSON NULL COMMENT '实物单位/计价单位/统一公式及单重策略';
SELECT COUNT(*) AS policy_column_exists FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='inventory_material' AND column_name='measurement_policy_json';
