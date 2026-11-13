-- 363 单位口径统一（dev-20261113-003）
-- 真源：sys_dict_item(dict_code='unit')；面积统一为 ㎡，M2 不再作为 code。
-- 清洗范围：inventory_material.unit 与 purchase_order_item.unit 的已确认映射；
--            支/条/瓶 不猜不清洗，仅输出清单（见任务记录）。
-- 风险：medium（字典值变更 + 有限列清洗）；回退：按全库备份恢复。
-- 前置：当天手工全库备份（用户执行；db-migrate.sh 不自动生成备份）。

-- 1) 字典：M2 → ㎡（幂等：仅在 M2 存在且 ㎡ 不存在时改写）
UPDATE sys_dict_item
   SET item_value = '㎡', label = '平方米'
 WHERE dict_code = 'unit' AND item_value = 'M2'
   AND NOT EXISTS (SELECT 1 FROM (SELECT item_value FROM sys_dict_item WHERE dict_code='unit') t WHERE t.item_value='㎡');

-- 2) 已确认映射的清洗（只改单位文本，不动数量/金额/流水）
UPDATE inventory_material SET unit = '㎡'  WHERE unit IN ('平方', 'M2', 'm2', 'm²', '平方米');
UPDATE inventory_material SET unit = 'KG'  WHERE unit IN ('公斤', '千克');
UPDATE inventory_material SET unit = 'ROLL' WHERE unit IN ('卷');

UPDATE purchase_order_item SET unit = '㎡' WHERE unit IN ('平方', 'M2', 'm2', 'm²', '平方米');

-- 3) 待定项保持原值（支/条/瓶），由用户确认映射后再清洗；此处不写任何更新。
