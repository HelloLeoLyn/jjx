-- 任务码：dev-20261121-003
-- 目的：产品作业规范/规范定版叫法不精确，按方案A统一为「产品技术文件包 / 发布版本」。
-- 影响面：仅 sys_menu.menu_name 四行；权限点、路由、组件均不变。

UPDATE sys_menu SET menu_name='产品技术文件包' WHERE menu_id=407 AND menu_name='产品作业规范';
UPDATE sys_menu SET menu_name='编辑技术文件包' WHERE menu_id=405 AND menu_name='编辑作业规范';
UPDATE sys_menu SET menu_name='导出打印技术文件包' WHERE menu_id=406 AND menu_name='导出打印作业规范';
UPDATE sys_menu SET menu_name='发布技术文件包版本' WHERE menu_id=418 AND menu_name='发布作业规范版本';

SELECT menu_id, menu_name FROM sys_menu WHERE menu_id IN (405,406,407,418);
