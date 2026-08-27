-- 08_notification_config_integrity.sql
-- 通知配置完整性修复 V1（基于 2026-08-27 只读核查报告《缺失通知标题配置核查 V1》）
--
-- 修复内容：
--   1. 10 条启用但缺少 title 的通知配置 → 补自然语言标题（不含数字主键/事件编码）
--   2. sample.rejected 收件人角色 工程[16] → 销售[20]（驳回后由销售修改并重新提交，PENDING_REVIEW→CREATED）
--   3. 6 条 title 含 {bizId} 但 payload 无 bizId 的既有配置 → 移除无效变量（限定旧 title 完整值，避免覆盖人工修改）
--   4. stock.over 无代码触发点 → 仅停用（不删除，保留历史通知/任务/配置记录）
--
-- 幂等设计：每条 UPDATE 均精确限定 event_code + 旧值条件，重复执行结果保持不变；
--           修复缺标题仅更新 title 为 NULL/空串的记录；不修改 sys_notification 表结构；
--           不增加数据库默认标题；不允许 title 为 NULL。

-- ============ 1. 10 条缺 title 配置补标题 ============
UPDATE sys_event_config SET title = '样品单审核通过'
WHERE event_code = 'sample.approved' AND (title IS NULL OR TRIM(title) = '');

UPDATE sys_event_config SET title = '样品单审核驳回'
WHERE event_code = 'sample.rejected' AND (title IS NULL OR TRIM(title) = '');

UPDATE sys_event_config SET title = '样品已送样'
WHERE event_code = 'sample.sent' AND (title IS NULL OR TRIM(title) = '');

UPDATE sys_event_config SET title = '样品被客户退回'
WHERE event_code = 'sample.rejected_by_customer' AND (title IS NULL OR TRIM(title) = '');

UPDATE sys_event_config SET title = '样品单已转量产'
WHERE event_code = 'sample.converted' AND (title IS NULL OR TRIM(title) = '');

UPDATE sys_event_config SET title = '销售订单审核通过'
WHERE event_code = 'order.approved' AND (title IS NULL OR TRIM(title) = '');

UPDATE sys_event_config SET title = '销售订单审核驳回'
WHERE event_code = 'order.rejected' AND (title IS NULL OR TRIM(title) = '');

UPDATE sys_event_config SET title = '销售订单已重新提交'
WHERE event_code = 'order.resubmitted' AND (title IS NULL OR TRIM(title) = '');

UPDATE sys_event_config SET title = '销售订单已取消'
WHERE event_code = 'order.cancelled' AND (title IS NULL OR TRIM(title) = '');

UPDATE sys_event_config SET title = '销售订单已发送客户确认'
WHERE event_code = 'order.sent_to_customer' AND (title IS NULL OR TRIM(title) = '');

-- ============ 2. sample.rejected 收件人修正：工程[16] → 销售[20] ============
-- 业务流：PENDING_REVIEW(2) → CREATED(1)，驳回后由销售修改并重新提交
-- 限定当前 target_role 仍为 '[16]'，避免覆盖人工调整；JSON 格式沿用当前库真实格式
-- 注意：target_role 列为 JSON 类型，字符串 = 比较不可靠，须 CAST 为 JSON 再比较
UPDATE sys_event_config SET target_role = CAST('[20]' AS JSON)
WHERE event_code = 'sample.rejected' AND target_role = CAST('[16]' AS JSON);

-- ============ 3. 6 条变量残留 title 修复（限定旧 title 完整值） ============
-- 这些事件 @Event 简写（无 bizId）或手动 fire（无 bizId），payload 无 bizId 可解析，
-- 原 title 会残留 {bizId} 字面量。以下均为销售订单/产品/BOM 真实业务事件。
UPDATE sys_event_config SET title = '销售订单已提交审核'
WHERE event_code = 'order.submitted' AND title = '订单【{bizId}】已提交';

UPDATE sys_event_config SET title = '销售订单已确认'
WHERE event_code = 'order.confirmed' AND title = '订单【{bizId}】已获客户确认';

UPDATE sys_event_config SET title = '产品已提交审核'
WHERE event_code = 'product.submitted' AND title = '产品【{bizId}】已提交审核';

UPDATE sys_event_config SET title = '产品审核通过'
WHERE event_code = 'product.approved' AND title = '产品【{bizId}】审核通过';

UPDATE sys_event_config SET title = 'BOM已提交审核'
WHERE event_code = 'bom.submitted' AND title = 'BOM【{bizId}】已提交审核';

UPDATE sys_event_config SET title = 'BOM审核通过'
WHERE event_code = 'bom.approved' AND title = 'BOM【{bizId}】审核通过';

-- ============ 4. 停用 stock.over（仓库内无代码触发点，仅停用不删除） ============
-- 原配置：event_type=notification, target_role=[20, 21], update_time=2026-08-05 18:27:56
-- 限定当前仍为启用状态
UPDATE sys_event_config SET is_enabled = 0
WHERE event_code = 'stock.over' AND is_enabled = 1;
