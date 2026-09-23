-- risk: low
-- dev-20260924-001：库存菜单按业内口径拆为「即时库存 / 收发明细」。
-- 复用既有 menu_id=126 与 inventory:transaction:view，保留现有角色授权。

UPDATE sys_menu
SET menu_name = '即时库存',
    remark = 'dev-20260924-001：原库存台账更名，职责为当前库存余额与批次库存'
WHERE menu_id = 26
  AND perms = 'inventory:stock:view';

UPDATE sys_menu
SET parent_id = 18,
    ancestors = '0,18',
    menu_name = '收发明细',
    order_num = 4,
    path = 'transaction',
    component = 'views/inventory/transaction/index.vue',
    menu_type = 'C',
    visible = '0',
    status = '0',
    is_frame = '1',
    is_cache = '0',
    route_name = 'InventoryTransactionLedger',
    icon = 'List',
    remark = 'dev-20260924-001：逐笔库存收发流水，可从即时库存联查'
WHERE menu_id = 126
  AND perms = 'inventory:transaction:view';
