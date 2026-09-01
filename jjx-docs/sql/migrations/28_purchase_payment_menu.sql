-- dev-20260901-062 采购付款管理：挂载付款菜单页面
-- 幂等：component 为 NULL 才更新
UPDATE sys_menu SET route_name = 'PurchasePayment', component = 'views/purchase/payment/index.vue'
WHERE menu_id = 172 AND component IS NULL;
