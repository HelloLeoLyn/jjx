-- 任务码：dev-20261028-001
-- 用途：把历史 size 文本回填到结构化尺寸列（宽/长/厚；克重与公差留空待人工）。
-- 前置：① 已执行 migrations/311_material_size_structure.sql；② 已做全库备份（用户手工执行）。
-- 执行方式：先跑「一、预演」看命中数，再跑「二、回填」，最后「三、核对」。
-- 口径：
--   1) 两段「A×B」→ 宽=A、长=B
--   2) 三段「A×B×C」→ 宽=A、长=B、厚=C
--   3) 单值 ≥100 → 宽；单值 <1 → 厚；单值 1~100 → 不回填，进人工确认队列
--   4) 其余（含字母的料号，如 DN25.4-ODN-280-10C）→ 不回填，进人工确认队列
-- 说明：不改 size 文本（展示仍由 size 派生），只写数值列；单位先剥离再做数值转换。

-- 一、预演（只读）
SELECT
  SUM(width_mm IS NULL AND length_mm IS NULL AND thickness_mm IS NULL) AS 尚未回填,
  SUM(size REGEXP '[0-9.]+[[:space:]]*[x×X*][[:space:]]*[0-9.]+') AS 两段以上,
  SUM(size REGEXP '^[[:space:]]*[0-9.]+[[:space:]]*(mm|cm|m)?[[:space:]]*$') AS 单值,
  COUNT(*) AS 总数
FROM inventory_material;

-- 二、回填（写库；已备份）
-- 归一：先把 ×/X/* 统一成 x，再剥掉其余非数字字符，避免 340×340 被拼成 340340。
-- 2.1 两段：宽×长
UPDATE inventory_material
SET width_mm  = CAST(SUBSTRING_INDEX(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(size,'×','x'),'*','x'),'X','x'), '[^0-9.x]', ''), 'x', 1) AS DECIMAL(10, 2)),
    length_mm = CAST(SUBSTRING_INDEX(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(size,'×','x'),'*','x'),'X','x'), '[^0-9.x]', ''), 'x', -1) AS DECIMAL(12, 2)),
    spec_basis = 'SIZE'
WHERE size REGEXP '[0-9.]+[[:space:]]*[x×X*][[:space:]]*[0-9.]+'
  AND NOT size REGEXP '([0-9.]+[[:space:]]*[x×X*][[:space:]]*){2}';

-- 2.2 三段：宽×长×厚
UPDATE inventory_material
SET width_mm   = CAST(SUBSTRING_INDEX(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(size,'×','x'),'*','x'),'X','x'), '[^0-9.x]', ''), 'x', 1) AS DECIMAL(10, 2)),
    length_mm  = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(size,'×','x'),'*','x'),'X','x'), '[^0-9.x]', ''), 'x', 2), 'x', -1) AS DECIMAL(12, 2)),
    thickness_mm = CAST(SUBSTRING_INDEX(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(size,'×','x'),'*','x'),'X','x'), '[^0-9.x]', ''), 'x', -1) AS DECIMAL(10, 4)),
    spec_basis = 'THICKNESS'
WHERE size REGEXP '([0-9.]+[[:space:]]*[x×X*][[:space:]]*){2}';

-- 2.3 单值 ≥100 → 宽
UPDATE inventory_material
SET width_mm = CAST(TRIM(REPLACE(size, 'mm', '')) AS DECIMAL(10, 2)),
    size_unit = 'mm',
    spec_basis = 'SIZE'
WHERE size REGEXP '^[[:space:]]*[0-9.]+[[:space:]]*(mm)?[[:space:]]*$'
  AND CAST(TRIM(REPLACE(size, 'mm', '')) AS DECIMAL(12, 4)) >= 100;

-- 2.4 单值 <1 → 厚
UPDATE inventory_material
SET thickness_mm = CAST(TRIM(REPLACE(size, 'mm', '')) AS DECIMAL(10, 4)),
    size_unit = 'mm',
    spec_basis = 'THICKNESS'
WHERE size REGEXP '^[[:space:]]*[0-9.]+[[:space:]]*(mm)?[[:space:]]*$'
  AND CAST(TRIM(REPLACE(size, 'mm', '')) AS DECIMAL(12, 4)) < 1;

-- 三、回填后核对（只读）
SELECT
  SUM(width_mm IS NOT NULL) AS 有宽,
  SUM(length_mm IS NOT NULL) AS 有长,
  SUM(thickness_mm IS NOT NULL) AS 有厚,
  SUM(width_mm IS NULL AND length_mm IS NULL AND thickness_mm IS NULL) AS 仍待人工,
  SUM(spec_basis = 'SIZE') AS 口径SIZE,
  SUM(spec_basis = 'THICKNESS') AS 口径THICKNESS
FROM inventory_material;
