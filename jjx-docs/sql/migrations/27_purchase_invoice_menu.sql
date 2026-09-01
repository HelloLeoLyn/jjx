-- dev-20260901-061 采购发票管理：挂载发票菜单页面
-- 幂等：component 为 NULL 才更新
UPDATE sys_menu SET route_name = 'PurchaseInvoice', component = 'views/purchase/invoice/index.vue'
WHERE menu_id = 165 AND component IS NULL;
