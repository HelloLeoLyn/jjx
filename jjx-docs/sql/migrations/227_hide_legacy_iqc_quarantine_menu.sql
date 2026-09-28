-- risk: high
-- task: dev-20260928-038
-- 统一 IQC 工作台后隐藏旧的不合格处置菜单；保留旧路由组件与接口用于历史链接兼容。
UPDATE sys_menu
SET visible = '1', update_by = 'codex', remark = '已并入 /inventory/iqc，旧路由仅保留兼容跳转'
WHERE path = '/inventory/iqc-quarantine';
