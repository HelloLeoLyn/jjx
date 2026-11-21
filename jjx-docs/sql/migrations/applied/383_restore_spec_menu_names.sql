-- 任务码：dev-20261121-003
-- 目的：用户否决「产品技术文件包」命名方案，恢复菜单原名称（382 的回退）。
-- 影响面：仅 sys_menu.menu_name 四行；权限点、路由、组件不变。

UPDATE sys_menu SET menu_name='产品作业规范' WHERE menu_id=407;
UPDATE sys_menu SET menu_name='编辑作业规范' WHERE menu_id=405;
UPDATE sys_menu SET menu_name='导出打印作业规范' WHERE menu_id=406;
UPDATE sys_menu SET menu_name='发布作业规范版本' WHERE menu_id=418;

SELECT menu_id, menu_name FROM sys_menu WHERE menu_id IN (405,406,407,418);
