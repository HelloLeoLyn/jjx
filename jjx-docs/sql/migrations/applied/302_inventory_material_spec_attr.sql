-- dev-20261023-022：物料规格属性化（品类属性定义 + 物料属性值 + 规格补充列）。
-- 用户 2026-10-23 采纳：规格不再靠人自由编写，按品类给可选项；生成文本仍写回 inventory_material.specification，
-- 下游（BOM/打印/采购/来料检验）继续读同一文本，不新增第二真源。
-- risk: low（纯新增表 + 新增可空列 + 字典种子；不改任何存量行数据）
-- 手工全库备份：由用户执行并放在 jjx-docs/sql/backups/（db-migrate.sh 自动取当日最新一份）。

CREATE TABLE IF NOT EXISTS inventory_material_spec_attr_def (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  category_code VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '品类编码，对应 inventory_material_category.category_code',
  attr_code VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '属性编码，如 thickness/surface',
  attr_name VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '属性名称，如 厚度/表面处理',
  input_type VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'number 数值 / range 区间 / select 下拉 / text 文本',
  unit VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '单位，如 mm/g/℃',
  options_json VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'select 选项 JSON 数组',
  required TINYINT NOT NULL DEFAULT 0 COMMENT '1 新增时必填 0 选填',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_spec_attr (category_code, attr_code),
  KEY idx_spec_attr_category (category_code, status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料规格属性定义（按品类的可选项）';

CREATE TABLE IF NOT EXISTS inventory_material_spec_value (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  material_id BIGINT NOT NULL COMMENT '物料ID，对应 inventory_material.material_id',
  attr_code VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '属性编码',
  attr_name VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '属性名称冗余，便于统计不连定义表',
  unit VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '单位冗余',
  value_text VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '文本值/下拉值',
  value_num DECIMAL(14,4) NULL COMMENT '数值（range 时为下限）',
  value_num2 DECIMAL(14,4) NULL COMMENT 'range 上限',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '取值时属性排序',
  create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_spec_value (material_id, attr_code),
  KEY idx_spec_value_attr (attr_code, value_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料规格属性值（规格的结构化明细）';

-- 规格补充：生成文本之外的少量说明，≤30 字；单独成列，统计只认属性值。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' AND COLUMN_NAME='spec_supplement'), 'SELECT 1', 'ALTER TABLE inventory_material ADD COLUMN spec_supplement VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''规格补充（生成文本之外，≤30字）'' AFTER specification');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 种子：8 个品类共 27 个属性。OTHER-R / OTHER-A / AUX-TOOL 不配属性，保留自由填写（特殊物料不阻断）。
INSERT INTO inventory_material_spec_attr_def
  (category_code, attr_code, attr_name, input_type, unit, options_json, required, sort_order, status)
VALUES
  ('RM-FILM','thickness','厚度','number','mm',NULL,1,10,1),
  ('RM-FILM','surface','表面处理','select',NULL,'["光面","雾面","幼砂","中砂","加硬","幼砂加硬","防刮","抗UV"]',0,20,1),
  ('RM-FILM','adhesive_side','胶面','select',NULL,'["无胶","单面","双面"]',0,30,1),
  ('RM-FILM','form','形态','select',NULL,'["卷料","片料"]',0,40,1),
  ('RM-PAPER','gram','克重','number','g',NULL,1,10,1),
  ('RM-PAPER','release_force','离型力','range','g',NULL,0,20,1),
  ('RM-PAPER','liner','结构','select',NULL,'["单面离型","双面离型"]',0,30,1),
  ('RM-PAPER','base_paper','底纸','select',NULL,'["格拉辛","牛皮纸","PET膜"]',0,40,1),
  ('RM-ADHESIVE','adhesive_system','胶系','select',NULL,'["亚克力","硅胶","橡胶","聚氨酯"]',0,10,1),
  ('RM-ADHESIVE','thickness','厚度','number','mm',NULL,1,20,1),
  ('RM-ADHESIVE','initial_tack','初粘','number','号',NULL,0,30,1),
  ('RM-ADHESIVE','adhesive_side','胶面','select',NULL,'["单面","双面"]',0,40,1),
  ('RM-INK','ink_type','油墨类型','select',NULL,'["UV油墨","水性油墨","溶剂型油墨"]',1,10,1),
  ('RM-INK','cure','固化方式','select',NULL,'["UV固化","热风干燥","自然干燥"]',0,20,1),
  ('RM-INK','substrate','适用基材','select',NULL,'["PET","PVC","纸张","PC"]',0,30,1),
  ('AUX-ADHESIVE','substrate','基材','select',NULL,'["无基材","PET","PVC","泡棉","无纺布"]',1,10,1),
  ('AUX-ADHESIVE','adhesive_system','胶系','select',NULL,'["亚克力","硅胶","橡胶"]',0,20,1),
  ('AUX-ADHESIVE','thickness','厚度','number','mm',NULL,0,30,1),
  ('AUX-ADHESIVE','temp_resist','耐温','number','℃',NULL,0,40,1),
  ('AUX-HARDWARE','material','材质','select',NULL,'["SUS304","SUS301","黄铜","铝","碳钢"]',1,10,1),
  ('AUX-HARDWARE','surface','表面处理','select',NULL,'["钝化","镀镍","镀金","发黑","喷砂"]',0,20,1),
  ('AUX-PACK','layers','层数','select',NULL,'["三层","五层","七层"]',1,10,1),
  ('AUX-PACK','printing','印刷','select',NULL,'["无印刷","单色","双色","多色"]',0,20,1),
  ('AUX-PACK','pack_material','材质','select',NULL,'["瓦楞纸","白卡纸","PE","PP"]',0,30,1),
  ('AUX-ELEC','package','封装','select',NULL,'["0802","0603","1206","插件"]',0,10,1),
  ('AUX-ELEC','voltage','电压','number','V',NULL,0,20,1),
  ('AUX-ELEC','light_color','发光色','select',NULL,'["红","绿","蓝","白","黄","双色"]',0,30,1)
ON DUPLICATE KEY UPDATE
  attr_name=VALUES(attr_name), input_type=VALUES(input_type), unit=VALUES(unit),
  options_json=VALUES(options_json), required=VALUES(required), sort_order=VALUES(sort_order), status=VALUES(status);
