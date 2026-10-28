-- dev-20261028-010 退回修复：二级目录「物料管理」component 置空
-- 口径对齐 297/298/299（其它二级目录的 component 为空），避免库存管理 layout 内再套一层 layout。
-- 幂等：已为空时不会产生变更，可重复执行。
UPDATE sys_menu m
  JOIN sys_menu p ON p.menu_id = m.parent_id
   SET m.component = NULL
 WHERE p.parent_id = 0
   AND p.menu_name = '库存管理'
   AND m.menu_name = '物料管理'
   AND m.menu_type = 'M'
   AND m.component IS NOT NULL;
