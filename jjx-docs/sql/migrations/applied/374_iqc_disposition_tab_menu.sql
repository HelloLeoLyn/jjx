-- 任务码：dev-20261117-031
-- 目的：来料不合格处置并入 /inventory/iqc?tab=disposition，固定隐藏平级菜单333。
-- 仅调整展示；保留原组件、旧路由、quality:ncr:view权限、角色授权及全部处置动作。
-- 当前目标库菜单已隐藏；守卫更新可重复执行，目标状态已满足时不改行。

UPDATE sys_menu SET visible = '1'
WHERE menu_id = 333
  AND path = '/inventory/iqc-quarantine'
  AND perms = 'quality:ncr:view'
  AND visible = '0';

SELECT menu_id, menu_name, path, component, visible, status, perms
FROM sys_menu WHERE menu_id = 333;
