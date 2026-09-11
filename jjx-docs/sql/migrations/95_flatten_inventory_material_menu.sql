-- dev-20260911-008：库存物料菜单扁平化
-- 破坏性说明：按用户明确要求删除“物料档案”(19)与“材料分类”(21)菜单及其角色关联；
-- 物料列表(240)和隐藏详情路由(22)保留并改挂“库存管理”(18)，不删除业务表、接口或页面代码。

UPDATE sys_menu
SET parent_id = 18,
    order_num = 1,
    path = 'material',
    ancestors = '0,18',
    update_by = 'Codex',
    update_time = NOW(),
    remark = CONCAT(COALESCE(remark, ''), ' | dev-20260911-008：直接挂库存管理')
WHERE menu_id = 240
  AND component = 'views/inventory/material/index.vue';

UPDATE sys_menu
SET parent_id = 18,
    order_num = 99,
    path = 'material/detail/:materialId',
    ancestors = '0,18',
    update_by = 'Codex',
    update_time = NOW(),
    remark = CONCAT(COALESCE(remark, ''), ' | dev-20260911-008：随物料列表改挂库存管理')
WHERE menu_id = 22
  AND component = 'views/inventory/material/detail.vue';

DELETE FROM sys_role_menu
WHERE menu_id IN (19, 21);

DELETE FROM sys_menu
WHERE menu_id = 21
  AND component = 'views/inventory/material/category.vue';

DELETE FROM sys_menu
WHERE menu_id = 19
  AND menu_name = '物料档案'
  AND parent_id = 18;
