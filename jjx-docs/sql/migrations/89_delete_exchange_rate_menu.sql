-- 89_delete_exchange_rate_menu.sql
-- 按 Leo 2026-09-10 20:06 要求（「系统管理/基础配置/汇率管理删掉…不给兜底，兜底害人」）：
--   1) 删除「汇率管理」菜单与页面（该页面只是只读展示实时汇率，删后不影响业务）
--   2) 删除 12 条兜底汇率配置 exchange_rate.*（既然不给兜底，留着只会误导）
-- 保留：/system/exchange-rate/latest|rate 接口 —— 销售订单表单、报价单表单选外币时要用；
--       接口已改造为「只认外部实时汇率 + 10 分钟缓存 + 3s 超时 + 取不到明确报错」。
-- 前置备份：jjx-docs/sql/backups/sys_menu_config_exchange_rate_20260910-2007.sql
-- 注意：sys_menu 的删除不能用同表子查询（MySQL 1093），故先取变量、先删子再删父。

SET @rate_menu := (SELECT menu_id FROM sys_menu WHERE perms = 'system:exchangeRate:view' LIMIT 1);

-- 1) 角色授权（父 + 可能的子按钮）
DELETE FROM sys_role_menu
 WHERE menu_id = @rate_menu
    OR menu_id IN (SELECT menu_id FROM sys_menu WHERE parent_id = @rate_menu);

-- 2) 菜单（先子后父，幂等）
DELETE FROM sys_menu WHERE parent_id = @rate_menu;
DELETE FROM sys_menu WHERE menu_id = @rate_menu;

-- 3) 兜底汇率配置（12 条）
DELETE FROM sys_config WHERE config_key LIKE 'exchange_rate.%';

-- 4) 核验
SELECT '剩余汇率菜单' AS chk, COUNT(*) AS cnt FROM sys_menu WHERE perms = 'system:exchangeRate:view'
UNION ALL SELECT '剩余兜底汇率配置', COUNT(*) FROM sys_config WHERE config_key LIKE 'exchange_rate.%';
