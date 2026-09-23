-- 209_hide_product_instance_menu.sql
-- 任务：dev-20260921-021（看板 2060「产品实例」模块收口）
-- 背景：2026-09-23 用户拍板——产品实例功能不上线，**入口关闭 / 菜单下架即可，其他不修**。
--       该模块残留缺陷留着不动（新建恒 6004、前端 3 端点缺失、3 查询恒 null、状态双列等，见
--       jjx-docs/history/product-instance-module-gaps-dev-20260921-021.md）。
-- 动作（幂等）：把「产品管理」下的产品实例入口与两个按钮置 visible='1'（隐藏，项目既有先例：菜单 64/65）。
--       注：菜单树查询不过滤 visible/status，侧边栏按 meta.hidden 生效；如需连路由一起关（URL 不可达），
--           另需 status='1' 或删前端 views/product/instance 页面 —— 本次按用户指示不做。
UPDATE sys_menu SET visible = '1', update_time = NOW()
 WHERE menu_id IN (11, 275, 276) AND IFNULL(visible, '0') <> '1';
