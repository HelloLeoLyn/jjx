-- 任务码：dev-20261113-004
-- 目的：清洗剩余中文单位，并补齐字典项：
--   ① sys_dict_item(unit) 新增 BOT（瓶），幂等；
--   ② inventory_material.unit：支→ROLL（卷状纸类 AUX000332-336）、条→PCS（胶刮 AUX000237-238）、瓶→BOT（INK000239）。
-- 用户口径（2026-11-13）：支=ROLL、条=PCS、瓶=BOT。
-- 幂等：字典用 ON DUPLICATE KEY；数据按 unit 值条件更新，重复执行影响 0 行。
-- 影响：字典配置 + 8 行业务数据；执行前需手工全库备份（已存在 2026-11-13 13:48 备份）。

INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, sort_order, is_active, tenant_id)
SELECT 'unit', 'master_BOT', 'BOT', '瓶', IFNULL(MAX(sort_order), 0) + 1, 1, 1
FROM sys_dict_item WHERE dict_code = 'unit'
GROUP BY 'unit'
ON DUPLICATE KEY UPDATE label = VALUES(label), is_active = 1, deleted = 0;

UPDATE inventory_material SET unit = 'ROLL' WHERE unit = '支';
UPDATE inventory_material SET unit = 'PCS'  WHERE unit = '条';
UPDATE inventory_material SET unit = 'BOT'  WHERE unit = '瓶';

SELECT unit, COUNT(*) AS cnt FROM inventory_material GROUP BY unit ORDER BY cnt DESC;
