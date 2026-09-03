-- 48_purchase_event_target_role_fix.sql
-- 采购事件收件人修正（任务1265，2026-09-03）
-- 背景：8-01 第3批采购事件批量建配置时角色错位——purchase.* 20 条 target_role 全为 [21 SALES审核员]（销售域）
-- 后果：采购通知发到销售头上；both 类建任务 assign_role=21 采购任务派给销售审核员；received 里 16 空壳角色通知静默
-- 修正：审核类→[27 采购审核员]；业务/结果类→[26 采购业务]；到货→[26,23]（采购+仓管）

-- 审核/待审类：提审/审批/付款审批确认/单据核验/询价创建 → 采购审核员
UPDATE sys_event_config SET target_role = '[27]' WHERE event_code IN (
  'purchase.submitted', 'purchase.approved',
  'purchase.payment.created', 'purchase.payment.approved', 'purchase.payment.confirmed',
  'purchase.document.verified', 'purchase.material_inquiry.created'
);

-- 业务/结果/主数据类 → 采购业务
UPDATE sys_event_config SET target_role = '[26]' WHERE event_code IN (
  'purchase.payment_updated', 'purchase.item_received',
  'purchase.supplier.created', 'purchase.supplier.updated', 'purchase.supplier.deleted', 'purchase.supplier.status_updated',
  'purchase.payment.deleted',
  'purchase.document.created', 'purchase.document.deleted',
  'purchase.material_inquiry.updated', 'purchase.material_inquiry.deleted', 'purchase.material_inquiry.status_updated'
);

-- 采购到货 → 采购业务 + 仓管（原含空壳 16）
UPDATE sys_event_config SET target_role = '[26, 23]' WHERE event_code = 'purchase.received';
