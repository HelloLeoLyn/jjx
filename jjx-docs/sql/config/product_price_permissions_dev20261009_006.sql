-- dev-20261009-006：仅幂等新增菜单/权限配置，无DDL，无业务数据更新。
-- 用户部署时执行；不自动给现有角色授权。执行后在角色管理中分别分配查看/修改权限。
START TRANSACTION;
SET @product_price_parent = (
    SELECT menu_id FROM sys_menu WHERE menu_type='M' AND route_name='Product' LIMIT 1
);
INSERT INTO sys_menu
    (menu_name,parent_id,order_num,path,component,menu_type,visible,status,perms,icon,ancestors,route_name,requires_auth,create_by,create_time,remark)
SELECT '产品价格维护',@product_price_parent,9,'price','views/product/price/index.vue','C','0','0',
       'product:price:view','Money',CONCAT('0,',@product_price_parent),'ProductPrice','1','codex',NOW(),
       'dev-20261009-006：查看产品基础售价与标准成本，修改需另授权'
WHERE @product_price_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='product:price:view');
SET @product_price_menu = (SELECT menu_id FROM sys_menu WHERE perms='product:price:view' LIMIT 1);
INSERT INTO sys_menu
    (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,ancestors,requires_auth,create_by,create_time,remark)
SELECT '维护产品价格',@product_price_menu,1,'','F','0','0','product:price:edit',
       CONCAT('0,',@product_price_parent,',',@product_price_menu),'1','codex',NOW(),
       'dev-20261009-006：专用价格维护操作，需同时持有查看权限'
WHERE @product_price_menu IS NOT NULL AND @product_price_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='product:price:edit');
COMMIT;
SELECT menu_id,parent_id,menu_name,component,perms FROM sys_menu WHERE perms IN ('product:price:view','product:price:edit');
