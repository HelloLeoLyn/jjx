-- dev-20261023-006：物料主数据规范化（字段落地 + 品类绑大类 + 必填规则表）
-- risk: medium（加列 + 新增分类/规则数据；不改动既有物料名称与规格）
-- 执行前必须由用户手工备份（排除 hr_employee）；agent 不生成备份。
-- 原则：只新增字段与分类数据，material_name / specification 原值一律不动，拆错可回退。

-- 1) 品类表绑定大类
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material_category' AND COLUMN_NAME='material_type'), 'SELECT 1', 'ALTER TABLE inventory_material_category
  ADD COLUMN material_type CHAR(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''所属大类：R/S/F/A/I'' AFTER parent_id,
  ADD INDEX idx_category_type (material_type, status)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 物料表新增结构化字段（先建列）
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material' AND COLUMN_NAME='material_grade'), 'SELECT 1', 'ALTER TABLE inventory_material
  ADD COLUMN material_grade VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''材料：含形态的受控复合词，如PE保护膜/PC膜/EVA泡棉'' AFTER specification,
  ADD COLUMN model VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''型号/料号/规格档（3M5604、PET125#、M1.6*3）'' AFTER material_grade,
  ADD COLUMN color VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''颜色（透明/白色/黑色/黄色/红色/蓝色/灰色/银色）'' AFTER model,
  ADD COLUMN brand VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''品牌（与供应商区分）'' AFTER color,
  ADD COLUMN search_keywords VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''别名/客户叫法，逗号分隔'' AFTER brand');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE inventory_material
  MODIFY COLUMN remark VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '备注（含原名称/原规格留痕）';

-- 3) 搜索生成列（必须在上面各列之后单独加）
ALTER TABLE inventory_material
  ADD COLUMN search_blob VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    GENERATED ALWAYS AS (CONCAT_WS(' ',
      IFNULL(material_name,''), IFNULL(material_name_en,''), IFNULL(material_code,''),
      IFNULL(model,''), IFNULL(material_grade,''), IFNULL(color,''),
      IFNULL(specification,''), IFNULL(search_keywords,'')
    )) STORED,
  ADD INDEX idx_material_grade (material_grade),
  ADD INDEX idx_model (model),
  ADD INDEX idx_color (color),
  ADD INDEX idx_brand (brand),
  ADD INDEX idx_search_blob (search_blob(768));

-- 4) 品类初始化（10 条；另沿用 INK 行作为 RM-INK，共 11 行（OTHER 分 R/A））
UPDATE inventory_material_category SET category_code='RM-INK', material_type='R', category_path='RM-INK', category_level=1 WHERE category_code='INK';

INSERT INTO inventory_material_category
  (category_code, category_name, parent_id, material_type, category_level, category_path, sort_order, status, remark)
VALUES
  ('RM-FILM',      '片材/膜材',            0, 'R', 1, 'RM-FILM',      10, '1', 'PC/PET/PVC/PP/PE 等片材与膜材'),
  ('RM-PAPER',     '纸材（离型纸/离型膜）', 0, 'R', 1, 'RM-PAPER',     20, '1', '离型纸、离型膜、纸类'),
  ('RM-ADHESIVE',  '胶粘类-原材',           0, 'R', 1, 'RM-ADHESIVE',  30, '1', '保护膜、双面胶卷材等'),
  ('AUX-ADHESIVE', '胶粘类-辅料',           0, 'A', 1, 'AUX-ADHESIVE', 40, '1', '3M 胶带、胶水等'),
  ('AUX-ELEC',     '电子元件',             0, 'A', 1, 'AUX-ELEC',     50, '1', 'LED/电阻/电容/连接器/IC'),
  ('AUX-HARDWARE', '五金件',               0, 'A', 1, 'AUX-HARDWARE', 60, '1', '螺丝/垫片/弹片/铆钉'),
  ('AUX-PACK',     '包材',                 0, 'A', 1, 'AUX-PACK',     70, '1', '纸箱/袋/泡棉/标签'),
  ('AUX-TOOL',     '耗材工具',             0, 'A', 1, 'AUX-TOOL',     80, '1', '刀模/治具/砂纸/手套'),
  ('OTHER-R',      '其他（原材料）',        0, 'R', 1, 'OTHER-R',      90, '1', '原材料兜底'),
  ('OTHER-A',      '其他（辅助材料）',      0, 'A', 1, 'OTHER-A',      91, '1', '辅助材料兜底');

-- 5) 品类必填规则表 + 初始化（★=1 必填，缺省 0 选填）
CREATE TABLE IF NOT EXISTS inventory_material_required_field (
  id BIGINT NOT NULL AUTO_INCREMENT,
  category_code VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '品类编码',
  field_key VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段：material_grade/model/color/specification/brand',
  required TINYINT NOT NULL DEFAULT 0 COMMENT '1必填 0选填',
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_field (category_code, field_key)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='品类必填字段规则';

INSERT INTO inventory_material_required_field (category_code, field_key, required) VALUES
  ('RM-FILM','material_grade',1),('RM-FILM','model',0),('RM-FILM','color',1),('RM-FILM','specification',1),('RM-FILM','brand',0),
  ('RM-PAPER','material_grade',1),('RM-PAPER','model',0),('RM-PAPER','color',1),('RM-PAPER','specification',1),('RM-PAPER','brand',0),
  ('RM-ADHESIVE','material_grade',1),('RM-ADHESIVE','model',0),('RM-ADHESIVE','color',1),('RM-ADHESIVE','specification',1),('RM-ADHESIVE','brand',0),
  ('RM-INK','material_grade',0),('RM-INK','model',0),('RM-INK','color',1),('RM-INK','specification',1),('RM-INK','brand',0),
  ('AUX-ADHESIVE','material_grade',0),('AUX-ADHESIVE','model',1),('AUX-ADHESIVE','color',0),('AUX-ADHESIVE','specification',1),('AUX-ADHESIVE','brand',1),
  ('AUX-ELEC','material_grade',0),('AUX-ELEC','model',1),('AUX-ELEC','color',1),('AUX-ELEC','specification',0),('AUX-ELEC','brand',0),
  ('AUX-HARDWARE','material_grade',1),('AUX-HARDWARE','model',1),('AUX-HARDWARE','color',0),('AUX-HARDWARE','specification',1),('AUX-HARDWARE','brand',0),
  ('AUX-PACK','material_grade',0),('AUX-PACK','model',0),('AUX-PACK','color',0),('AUX-PACK','specification',1),('AUX-PACK','brand',0),
  ('AUX-TOOL','material_grade',0),('AUX-TOOL','model',0),('AUX-TOOL','color',0),('AUX-TOOL','specification',0),('AUX-TOOL','brand',0),
  ('OTHER-R','material_grade',0),('OTHER-R','model',0),('OTHER-R','color',0),('OTHER-R','specification',0),('OTHER-R','brand',0),
  ('OTHER-A','material_grade',0),('OTHER-A','model',0),('OTHER-A','color',0),('OTHER-A','specification',0),('OTHER-A','brand',0);
