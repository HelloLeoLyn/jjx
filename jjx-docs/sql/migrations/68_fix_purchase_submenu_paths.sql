-- dev-20260908-017（任务1622，同批 1243/1244）：采购子菜单补 path/visible + 排序理顺
-- 根因：165 采购发票 / 172 采购付款 / 180 采购收货 三个菜单 path 为空且 visible=1，
-- 前端 routeHelper 过滤空 path → 路由不注册、菜单不可见、页面打不开（页面文件均已在 views/purchase/{invoice,payment,receipt}/）。
-- 本脚本前先执行 sys_menu 单表备份（mysqldump → jjx-docs/sql/backups/）。

-- 1) 采购发票：补 path、显示、order_num=4（采购计划之后）
UPDATE sys_menu SET path = 'invoice', visible = 0, order_num = 4 WHERE menu_id = 165;
-- 2) 采购付款：补 path、显示、order_num=5
UPDATE sys_menu SET path = 'payment', visible = 0, order_num = 5 WHERE menu_id = 172;
-- 3) 采购收货：补 path、显示、order_num=6
UPDATE sys_menu SET path = 'receipt', visible = 0, order_num = 6 WHERE menu_id = 180;
-- 4) 采购报表：order_num 6 → 7（为收货让位，保证同级排序 1..7 无撞号：supplier1/order2/plan3/invoice4/payment5/receipt6/report7）
UPDATE sys_menu SET order_num = 7 WHERE menu_id = 324;
