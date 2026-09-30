-- dev-20260930-037
-- risk: low
-- 出库作业的两个隐藏子页（新建出库单 247 / 编辑出库单 248）从「出库作业」(33) 列表页挪到「库存管理」(18) 下，
-- 变成独立页面路由，并补上 route_name（原为 NULL，前端只能按 path 兜底生成 'Create' / 'Edit:id'）。
-- 原因：子页挂在列表页下时，列表页（views/inventory/outbound/index.vue）里没有 <router-view>，
--   子路由无处渲染 → 点「新建出库单」地址变了但界面不动，用户看到的是"点击没反应"。
-- 前端跳转路径不变：/inventory/outbound/create、/inventory/outbound/edit/:id
-- 入库作业(28) 下的同类菜单 30/32 保持不动（入库作业列表页已没有「新建」入口，属历史遗留）。
-- 影响：仅 sys_menu 两条行的 parent_id/path/route_name；visible 仍为 1（不出现在侧边栏）；
--   sys_role_menu 不变（超级管理员已授 247/248）。生效需用户重新登录（路由/权限快照在登录时生成）。

UPDATE sys_menu
   SET parent_id = 18,
       path = 'outbound/create',
       route_name = 'OutboundCreate',
       update_time = NOW()
 WHERE menu_id = 247 AND path = 'create';

UPDATE sys_menu
   SET parent_id = 18,
       path = 'outbound/edit/:id',
       route_name = 'OutboundEdit',
       update_time = NOW()
 WHERE menu_id = 248 AND path = 'edit/:id';

-- 自检：两条都应在「库存管理(18)」下，path 带 outbound/ 前缀，route_name 非空
SELECT menu_id, parent_id, menu_name, path, route_name, visible
  FROM sys_menu WHERE menu_id IN (247, 248) ORDER BY menu_id;
