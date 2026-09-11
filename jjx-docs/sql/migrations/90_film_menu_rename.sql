-- ============================================================================
-- 90_film_menu_rename.sql
-- 菲林菜单/按钮命名统一：菜单里的「薄膜」→「菲林」
--   背景：表 engineering_film、接口 /engineering/films、枚举 FilmTypeEnum 全部是 film/菲林，
--         只有 sys_menu 的菜单名与按钮名还写着「薄膜」，菜单名与实现不一致（dev-20260911-003）。
--   范围：仅改 menu_name（菜单展示名），不动 perms 权限码、不动 component 路径，
--         因此不影响任何角色授权与前端路由。
--   幂等：按 menu_id 精确 UPDATE 成固定值，可重复执行。
--   作者/任务：Hermes Agent / dev-20260911-003
-- ============================================================================

UPDATE `sys_menu` SET `menu_name` = '菲林管理'       WHERE `menu_id` = 92  AND `menu_name` = '薄膜管理';
UPDATE `sys_menu` SET `menu_name` = '新增菲林'       WHERE `menu_id` = 269 AND `menu_name` = '新增薄膜';
UPDATE `sys_menu` SET `menu_name` = '删除菲林'       WHERE `menu_id` = 270 AND `menu_name` = '删除薄膜';
UPDATE `sys_menu` SET `menu_name` = '提交菲林审批'   WHERE `menu_id` = 271 AND `menu_name` = '提交薄膜审批';
UPDATE `sys_menu` SET `menu_name` = '菲林审批通过'   WHERE `menu_id` = 272 AND `menu_name` = '薄膜审批通过';
UPDATE `sys_menu` SET `menu_name` = '菲林审批驳回'   WHERE `menu_id` = 273 AND `menu_name` = '薄膜审批驳回';
UPDATE `sys_menu` SET `menu_name` = '菲林下发生产'   WHERE `menu_id` = 274 AND `menu_name` = '薄膜下发生产';
