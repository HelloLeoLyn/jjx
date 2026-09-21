-- ============================================================================
-- 143_sample_notify_engineering.sql
-- 任务码：dev-20260921-001
--
-- 目的：修复「打样申请没有通知/任务发给工程」的配置侧问题（代码侧同任务修的是
--       自调用绕过 @Event + OrderMapper.updateById 缺 total_quantity）。
--
-- 1) sample.created（样品单创建/拆单/复制）标题原先用 {bizId}（=内部 order_id），
--    发出来是「样品单【1】已创建」，看不出单号；代码侧已给 payload 补 orderNo，
--    这里把标题换成业务单号。收件人 [16] 不动（角色 16 已绑用户，通知可达）。
--
-- 2) sample.submitted（销售提交打样申请，状态 1→2 待打样）原收件人只有 [21]
--    （SALES 审核员，该角色 0 用户 → 通知静默丢失），工程角色完全不在列。
--    这里补工程 17 ENGINEERING 业务操作 / 18 ENGINEERING 审核员，保留 21；
--    标题同样换成业务单号。
--
-- ⚠ 依赖代码侧 payload 补了 orderNo（@Event params = {"orderNo=#result.orderNo"}）：
--    重新打包 + 重启后端后，标题里的 {orderNo} 才会替换成 SP… 单号；
--    重启前触发的事件标题会原样显示 {orderNo}（老 jar 的 payload 没这个键）。
--    同理，重启前 sample.created 依旧不发布（自调用问题只在代码里修）。
--
-- 幂等：UPDATE 直接写目标值，可重复执行。
-- ============================================================================

-- 1) 样品单创建（通知 + 任务，收件人 [16] ENGINEERING 全权限）
UPDATE sys_event_config
   SET title = '样品单【{orderNo}】已创建，请安排打样'
 WHERE event_code = 'sample.created';

-- 2) 样品单提交打样申请（通知，收件人 = 工程 17/18 + 销售审核员 21）
UPDATE sys_event_config
   SET title = '样品单【{orderNo}】已提交审核',
       target_role = '[17,18,21]'
 WHERE event_code = 'sample.submitted';
