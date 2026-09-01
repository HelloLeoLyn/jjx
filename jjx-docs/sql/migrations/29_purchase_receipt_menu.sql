-- dev-20260901-063 采购收货管理：挂载收货菜单页面
-- 幂等：component 为 NULL 才更新
UPDATE sys_menu SET route_name = 'PurchaseReceipt', component = 'views/purchase/receipt/index.vue'
WHERE menu_id = 180 AND component IS NULL;
