-- 44_event_1269_verification.sql
-- 跨模块空转事件统一核销（任务1269，2026-09-03）
-- 核销结论：
--  ① inventory 16 条 + sales 4 条候选（9-2 分析报告列"空转"）——代码核实均有 @Event 触发
--     （8-01 7a3329d 第1批库存事件 42 操作 / 0be8587 第4批销售事件），报告误判，保留不动
--  ② production work-report 3 条 → 任务 1246 已单独处理
--  ③ purchase 事件 → 任务 1265（dev-20260901-080）另行核对
--  ④ 真死事件仅 1 条：order.sent_to_customer（8-28 客户确认残留清理后无触发点，代码 0 引用）
-- 处置：停用（is_enabled=0，可回退；与 inventory stock.over/expiry/obsolete 停用惯例一致）
UPDATE sys_event_config SET is_enabled = 0
WHERE event_code = 'order.sent_to_customer' AND is_enabled = 1;
