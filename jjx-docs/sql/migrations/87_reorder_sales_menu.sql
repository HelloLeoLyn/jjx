-- ============================================================================
-- 迁移: 87_reorder_sales_menu.sql
-- 执行人: hermes-agent
-- 任务码: dev-20260910-015（销售菜单元数据整改：order_num 重复 / 发货管理隐藏 / 隐藏路由号段）
-- 原因:
--   1) parent_id=13（销售管理）子项 order_num 存在重复：14 与 223 均=1；15 与 229 均=2。
--      后端 SysMenuMapper.selectMenuTreeByRoles 仅 ORDER BY parent_id, order_num（无 tiebreaker），
--      同值顺序为未定义行为 → 左菜单并列项显示顺序不确定。本次重排为唯一且连续。
--   2) 发货管理(218) visible='1'（隐藏），但其页面（列表/详情/打印/签收）功能完整，
--      且是全系统唯一可"签收"入口；同段履约菜单（收款/发票/退货/对账）均可见，
--      且 SALES 审核员 被授予 sales:delivery:view 却无任何 UI 入口（死授权）。
--      本次恢复 visible='0' 回左菜单，使该授权有对应入口。
--   3) 隐藏路由 添加订单(62)/编辑订单(63) order_num=5 与可见项 3/4 号段交错；
--      隐藏项不参与渲染，本次仅为号码整理（13/14）。
-- 类型: 幂等 UPDATE，无破坏性语句（DROP/TRUNCATE/DELETE）。
-- 影响面: 仅 sys_menu 显示元数据（order_num / visible），不涉及权限校验代码与业务数据。
-- 备份: scripts/db-migrate.sh 执行前自动全库备份（before-87）。
-- ============================================================================

UPDATE sys_menu SET order_num=1,  update_by='hermes-agent' WHERE menu_id=14;   -- 客户管理
UPDATE sys_menu SET order_num=2,  update_by='hermes-agent' WHERE menu_id=223;  -- 询价管理
UPDATE sys_menu SET order_num=3,  update_by='hermes-agent' WHERE menu_id=15;   -- 报价管理
UPDATE sys_menu SET order_num=4,  update_by='hermes-agent' WHERE menu_id=229;  -- 样品单管理
UPDATE sys_menu SET order_num=5,  update_by='hermes-agent' WHERE menu_id=16;   -- 销售订单
UPDATE sys_menu SET order_num=6,  update_by='hermes-agent' WHERE menu_id=17;   -- 订单跟踪
UPDATE sys_menu SET order_num=7,  update_by='hermes-agent' WHERE menu_id=215;  -- 销售报表
UPDATE sys_menu SET order_num=8, visible='0', update_by='hermes-agent' WHERE menu_id=218;  -- 发货管理（恢复显示）
UPDATE sys_menu SET order_num=9,  update_by='hermes-agent' WHERE menu_id=295;  -- 收款单管理
UPDATE sys_menu SET order_num=10, update_by='hermes-agent' WHERE menu_id=296;  -- 销售发票
UPDATE sys_menu SET order_num=11, update_by='hermes-agent' WHERE menu_id=310;  -- 退货管理
UPDATE sys_menu SET order_num=12, update_by='hermes-agent' WHERE menu_id=326;  -- 业务对账
UPDATE sys_menu SET order_num=13, update_by='hermes-agent' WHERE menu_id=62;   -- 添加订单（隐藏路由，仅号码整理）
UPDATE sys_menu SET order_num=14, update_by='hermes-agent' WHERE menu_id=63;   -- 编辑订单（隐藏路由，仅号码整理）

-- 说明: 323 销售工作台（F 型权限位）order_num=99 保持不动，不参与左菜单渲染。
-- 执行后验证（手工）:
--   SELECT menu_id, order_num, menu_name, visible FROM sys_menu WHERE parent_id=13 ORDER BY order_num;
--   -- 期望: 1..12 唯一连续，发货管理(218) visible=0，隐藏项 62/63 = 13/14
