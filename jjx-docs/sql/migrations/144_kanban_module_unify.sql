-- ============================================================================
-- 144_kanban_module_unify.sql
-- 任务码：dev-20260921-001
--
-- 目的：看板模块取值「根治」统一，与新看板页签一一对应：
--         biz = 业务   prod = 生产   dev = 开发任务
--       紧急性由 priority 表达，不再用 emergency 模块（页签里本来也没有它）。
--
-- 背景（实测问题）：
--   ① 事件配置页「看板模块」下拉还写着"办公室任务/紧急任务/生产工单"，和界面上
--      生产/业务/开发三个页签名字对不上，用户按名字找不到配置。
--   ② 看板列表接口对 biz/prod 是「非 dev + 按角色分」，所以 office/emergency 卡片
--      虽然能列出来（落"业务"页签），但**详情/拖拽接口要求 module 与 task.kanban_module
--      完全相等**，于是点开就报"任务不存在"——页面能看到、点不动。
--   ③ 3 条业务事件误挂 dev：会被 biz/prod 两条分支同时排除，卡片只能落到"开发任务"看板
--      （开发看板被业务任务污染）。
--
-- 代码侧同任务改动：写死 "office" 的默认值/过滤条件改为 biz（LocalEventPublisher、
--   NotifyTaskServiceImpl、SampleOrderServiceImpl），BoardTaskController 详情/拖拽加
--   moduleMatches 归一（office/emergency→biz、production→prod）做历史值兜底；
--   前端事件配置页下拉与筛选同步。
--
-- 幂等：UPDATE 直接写目标值，可重复执行。
-- ============================================================================

-- 1) 事件配置：历史值归一
UPDATE sys_event_config SET kanban_module = 'biz'  WHERE kanban_module IN ('office', 'emergency');
UPDATE sys_event_config SET kanban_module = 'prod' WHERE kanban_module = 'production';

-- 2) 修正误挂 dev 的业务事件（原来被 biz/prod 同时排除，只出现在开发看板）
--    订单客户确认 → 收件人 [29] PRODUCTION 业务操作，归"生产"
--    生产工单开始 → 收件人 [29]，归"生产"
--    订单缺料预警 → 收件人 [26] 采购 / [23] 仓库，归"业务"
UPDATE sys_event_config SET kanban_module = 'prod' WHERE event_code IN ('order.confirmed', 'production.started');
UPDATE sys_event_config SET kanban_module = 'biz'  WHERE event_code = 'stock.shortage';

-- 3) 存量任务卡：历史值归一（否则详情/拖拽仍会"任务不存在"）
UPDATE sys_task SET kanban_module = 'biz'  WHERE kanban_module IN ('office', 'emergency');
UPDATE sys_task SET kanban_module = 'prod' WHERE kanban_module = 'production';

-- 4) 事件名称对齐业务动作，便于按业务词搜索（原"样品单提交审核"搜"打样/申请"命不中）
UPDATE sys_event_config SET event_name = '样品单提交打样申请' WHERE event_code = 'sample.submitted';
