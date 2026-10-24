-- dev-20261024-006：物料分类树收口 + name_path 中文全路径 + 模板对齐核对
-- risk: medium（会删除 11 条旧一级分类，并把引用它们的物料 category_id 置空；不改物料其它字段、不删物料）
-- 目标库：jjx_erp_db（由 db-migrate.sh 按 ops.schema.applied 记账）
--
-- 回滚方式（应用前必须先做全库备份，本迁移不保留「物料→旧分类」快照）：
--   1) 从备份恢复 inventory_material.category_id（DELETE 后无法用 SQL 反推原归属）；
--   2) 旧分类节点按下方「旧节点映射」注释重建（catogory_code/name 见注释），或整库恢复；
--   3) name_path 列可保留（无副作用），如需彻底回退：ALTER TABLE inventory_material_category DROP COLUMN name_path;
--
-- 幂等性：所有 DDL 走 information_schema 判断；INSERT 走 NOT EXISTS；name_path 回填为覆盖式；删除带三重存在性护栏。

-- ─────────────────────────────────────────────
-- 1) name_path：中文全路径（如 原材料/辅材/保护膜）
-- ─────────────────────────────────────────────
SET @ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_material_category' AND COLUMN_NAME = 'name_path'),
    'SELECT 1',
    'ALTER TABLE inventory_material_category ADD COLUMN name_path VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''中文全路径，如 原材料/辅材/保护膜'' AFTER category_path'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────
-- 2) 分类树收口：一级 3 / 二级 3 / 三级 13（幂等补齐，已存在则跳过）
-- ─────────────────────────────────────────────
INSERT INTO inventory_material_category (category_code, category_name, parent_id, material_type, category_level, category_path, sort_order, status, create_by, update_by, remark)
SELECT 'RAW', '原材料', 0, 'R', 1, 'RAW', 10, '0', 'mig-305', 'mig-305', 'dev-20261024-006 分类树收口'
 WHERE NOT EXISTS (SELECT 1 FROM inventory_material_category WHERE category_code = 'RAW');
INSERT INTO inventory_material_category (category_code, category_name, parent_id, material_type, category_level, category_path, sort_order, status, create_by, update_by, remark)
SELECT 'SEMI', '半成品', 0, 'S', 1, 'SEMI', 20, '0', 'mig-305', 'mig-305', 'dev-20261024-006 分类树收口'
 WHERE NOT EXISTS (SELECT 1 FROM inventory_material_category WHERE category_code = 'SEMI');
INSERT INTO inventory_material_category (category_code, category_name, parent_id, material_type, category_level, category_path, sort_order, status, create_by, update_by, remark)
SELECT 'FINISHED', '成品', 0, 'F', 1, 'FINISHED', 30, '0', 'mig-305', 'mig-305', 'dev-20261024-006 分类树收口'
 WHERE NOT EXISTS (SELECT 1 FROM inventory_material_category WHERE category_code = 'FINISHED');

INSERT INTO inventory_material_category (category_code, category_name, parent_id, material_type, category_level, category_path, sort_order, status, create_by, update_by, remark)
SELECT 'RAW_BASE', '基材', p.category_id, 'R', 2, 'RAW/RAW_BASE', 10, '0', 'mig-305', 'mig-305', 'dev-20261024-006 分类树收口'
  FROM inventory_material_category p WHERE p.category_code = 'RAW'
   AND NOT EXISTS (SELECT 1 FROM inventory_material_category c WHERE c.category_code = 'RAW_BASE');
INSERT INTO inventory_material_category (category_code, category_name, parent_id, material_type, category_level, category_path, sort_order, status, create_by, update_by, remark)
SELECT 'RAW_AUX', '辅材', p.category_id, 'R', 2, 'RAW/RAW_AUX', 20, '0', 'mig-305', 'mig-305', 'dev-20261024-006 分类树收口'
  FROM inventory_material_category p WHERE p.category_code = 'RAW'
   AND NOT EXISTS (SELECT 1 FROM inventory_material_category c WHERE c.category_code = 'RAW_AUX');
INSERT INTO inventory_material_category (category_code, category_name, parent_id, material_type, category_level, category_path, sort_order, status, create_by, update_by, remark)
SELECT 'RAW_INK', '油墨', p.category_id, 'R', 2, 'RAW/RAW_INK', 30, '0', 'mig-305', 'mig-305', 'dev-20261024-006 分类树收口'
  FROM inventory_material_category p WHERE p.category_code = 'RAW'
   AND NOT EXISTS (SELECT 1 FROM inventory_material_category c WHERE c.category_code = 'RAW_INK');

-- 三级 13 个叶子：基材 5 + 辅材 8
INSERT INTO inventory_material_category (category_code, category_name, parent_id, material_type, category_level, category_path, sort_order, status, create_by, update_by, remark)
SELECT v.code, v.name, p.category_id, 'R', 3, CONCAT('RAW/RAW_BASE/', v.code), v.sort_order, '0', 'mig-305', 'mig-305', 'dev-20261024-006 分类树收口'
  FROM inventory_material_category p
  JOIN (SELECT 'RAW_BASE_PC' AS code, 'PC膜/片' AS name, 10 AS sort_order
        UNION ALL SELECT 'RAW_BASE_PET', 'PET膜/片', 20
        UNION ALL SELECT 'RAW_BASE_PVC', 'PVC膜/片', 30
        UNION ALL SELECT 'RAW_BASE_PMMA', '压克力板', 40
        UNION ALL SELECT 'RAW_BASE_OTHER', '其他基材', 50) v
 WHERE p.category_code = 'RAW_BASE'
   AND NOT EXISTS (SELECT 1 FROM inventory_material_category c WHERE c.category_code = v.code);

INSERT INTO inventory_material_category (category_code, category_name, parent_id, material_type, category_level, category_path, sort_order, status, create_by, update_by, remark)
SELECT v.code, v.name, p.category_id, 'R', 3, CONCAT('RAW/RAW_AUX/', v.code), v.sort_order, '0', 'mig-305', 'mig-305', 'dev-20261024-006 分类树收口'
  FROM inventory_material_category p
  JOIN (SELECT 'RAW_AUX_PROTECT' AS code, '保护膜' AS name, 60 AS sort_order
        UNION ALL SELECT 'RAW_AUX_RELEASE', '离型材料', 70
        UNION ALL SELECT 'RAW_AUX_TAPE', '胶带/胶膜', 80
        UNION ALL SELECT 'RAW_AUX_FOAM', '泡棉/硅胶/EVA', 90
        UNION ALL SELECT 'RAW_AUX_FOIL', '金属箔/屏蔽', 100
        UNION ALL SELECT 'RAW_AUX_OPTICAL', '光学/扩散', 110
        UNION ALL SELECT 'RAW_AUX_ELECTRONIC', '电子元件/辅料', 120
        UNION ALL SELECT 'RAW_AUX_OTHER', '其他辅材', 130) v
 WHERE p.category_code = 'RAW_AUX'
   AND NOT EXISTS (SELECT 1 FROM inventory_material_category c WHERE c.category_code = v.code);

-- ─────────────────────────────────────────────
-- 3) name_path 回填（按层级，覆盖式，可重复执行）
-- ─────────────────────────────────────────────
UPDATE inventory_material_category
   SET name_path = category_name
 WHERE category_level = 1;

UPDATE inventory_material_category c
  JOIN inventory_material_category p ON p.category_id = c.parent_id
   SET c.name_path = CONCAT(p.name_path, '/', c.category_name)
 WHERE c.category_level = 2;

UPDATE inventory_material_category c
  JOIN inventory_material_category p ON p.category_id = c.parent_id
   SET c.name_path = CONCAT(p.name_path, '/', c.category_name)
 WHERE c.category_level = 3;

-- ─────────────────────────────────────────────
-- 4) 旧一级节点映射与删除
--    映射口径（008 卡按「原名称 + 原规格」重算，本迁移只做归位说明，不写物料分类）：
--      RM-FILM         片材            → RAW/RAW_BASE/{PC|PET|PVC|PMMA|OTHER}
--      RM-PAPER        纸材            → RAW/RAW_AUX/离型材料 或 其他辅材
--      RM-ADHESIVE     胶粘类-原材     → RAW/RAW_AUX/{胶带|保护膜}
--      RM-INK          油墨印刷        → RAW/RAW_INK
--      AUX-ADHESIVE    胶粘类-辅料     → RAW/RAW_AUX/RAW_AUX_TAPE
--      AUX-ELEC        电子元件        → RAW/RAW_AUX/RAW_AUX_ELECTRONIC
--      AUX-HARDWARE    五金件          → RAW/RAW_AUX/{金属箔·屏蔽|其他辅材}
--      AUX-PACK        包材            → RAW/RAW_AUX/RAW_AUX_OTHER
--      AUX-TOOL        耗材工具        → RAW/RAW_AUX/RAW_AUX_OTHER
--      OTHER-R         其他（原材料）  → RAW/RAW_BASE/RAW_BASE_OTHER 或 其他辅材
--      OTHER-A         其他（辅助材料）→ RAW/RAW_AUX/RAW_AUX_OTHER
--      RM-Release-film 离型膜          → RAW/RAW_AUX/RAW_AUX_RELEASE
-- ─────────────────────────────────────────────

-- 4.1 先置空引用（不删物料；重算分类由 dev-20261024-008 负责）
UPDATE inventory_material m
  JOIN inventory_material_category c ON c.category_id = m.category_id
   SET m.category_id = NULL,
       m.update_by = 'mig-305',
       m.update_time = NOW()
 WHERE c.category_code IN ('RM-FILM','RM-PAPER','RM-ADHESIVE','RM-INK','AUX-ADHESIVE','AUX-ELEC',
                           'AUX-HARDWARE','AUX-PACK','AUX-TOOL','OTHER-R','OTHER-A','RM-Release-film');

-- 4.2 删除旧节点（有物料引用/模板绑定/子节点的跳过，保证幂等与安全）
DELETE c FROM inventory_material_category c
  LEFT JOIN inventory_material m ON m.category_id = c.category_id
  LEFT JOIN inventory_attribute_template t ON t.category_id = c.category_id
  LEFT JOIN inventory_material_category ch ON ch.parent_id = c.category_id
 WHERE c.category_code IN ('RM-FILM','RM-PAPER','RM-ADHESIVE','RM-INK','AUX-ADHESIVE','AUX-ELEC',
                           'AUX-HARDWARE','AUX-PACK','AUX-TOOL','OTHER-R','OTHER-A','RM-Release-film')
   AND m.material_id IS NULL
   AND t.template_id IS NULL
   AND ch.category_id IS NULL;

-- ─────────────────────────────────────────────
-- 5) 模板对齐核对（只读，应用后手工执行；期望：无行 = 全部末级节点恰好 1 个模板，分组节点 0 个）
-- ─────────────────────────────────────────────
-- SELECT c.category_code, c.category_name, COUNT(t.template_id) AS template_count
--   FROM inventory_material_category c
--   LEFT JOIN inventory_attribute_template t ON t.category_id = c.category_id
--  WHERE NOT EXISTS (SELECT 1 FROM inventory_material_category ch WHERE ch.parent_id = c.category_id)
--  GROUP BY c.category_id, c.category_code, c.category_name
-- HAVING COUNT(t.template_id) <> 1;
--
-- SELECT c.category_code, COUNT(t.template_id) AS template_count
--   FROM inventory_material_category c
--   LEFT JOIN inventory_attribute_template t ON t.category_id = c.category_id
--  WHERE EXISTS (SELECT 1 FROM inventory_material_category ch WHERE ch.parent_id = c.category_id)
--  GROUP BY c.category_id, c.category_code
-- HAVING COUNT(t.template_id) <> 0;
--
-- 层级计数期望：一级 3 / 二级 3 / 三级 13；旧编码计数期望：0
-- SELECT category_level, COUNT(*) FROM inventory_material_category GROUP BY category_level;
-- SELECT COUNT(*) FROM inventory_material_category WHERE category_code IN ('RM-FILM','RM-PAPER','RM-ADHESIVE','RM-INK','AUX-ADHESIVE','AUX-ELEC','AUX-HARDWARE','AUX-PACK','AUX-TOOL','OTHER-R','OTHER-A','RM-Release-film');
