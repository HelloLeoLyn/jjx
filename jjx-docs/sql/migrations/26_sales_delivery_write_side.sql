-- dev-20260901-051 发货单写入侧补齐
-- 1) sales_delivery 补签收操作人列 receive_by / receive_name（entity 已新增对应字段）
-- 2) 挂载发货管理菜单页面（menu 218）
-- 幂等：information_schema 判断缺列才 ALTER；菜单 component 为 NULL 才更新

SET @need_alter = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sales_delivery'
      AND COLUMN_NAME = 'receive_by'
);

SET @stmt = IF(@need_alter = 0,
    'ALTER TABLE sales_delivery ADD COLUMN receive_by BIGINT NULL COMMENT ''签收操作人ID'' AFTER receive_remark, ADD COLUMN receive_name VARCHAR(100) NULL COMMENT ''签收操作人姓名'' AFTER receive_by',
    'DO 0');

PREPARE alter_delivery FROM @stmt;
EXECUTE alter_delivery;
DEALLOCATE PREPARE alter_delivery;

UPDATE sys_menu SET route_name = 'SalesDelivery', component = 'views/sales/delivery/index.vue'
WHERE menu_id = 218 AND component IS NULL;
