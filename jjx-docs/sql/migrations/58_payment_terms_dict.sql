-- 58_payment_terms_dict.sql
-- 供应商付款条件业务字典（2026-09-04，任务 dev-20260904-015 跟进：付款方式显示中文）
-- 用户后续在 系统管理→基础配置→字典管理 自行增删条目
INSERT INTO sys_dict (dict_code, dict_name, dict_group, remark, sort_order, is_active, deleted, create_time)
SELECT 'payment_terms', '付款条件', 'purchase', '供应商付款条件业务字典（NET_30 等码→中文），2026-09-04 建，用户可自行维护', 0, 1, 0, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'payment_terms');

INSERT IGNORE INTO sys_dict_item (dict_code, item_key, item_value, label, sort_order, is_active, deleted)
VALUES
  ('payment_terms', 'NET_30', 'NET_30', '月结30天', 1, 1, 0),
  ('payment_terms', 'NET_60', 'NET_60', '月结60天', 2, 1, 0),
  ('payment_terms', 'COD', 'COD', '货到付款', 3, 1, 0),
  ('payment_terms', 'PREPAID', 'PREPAID', '预付', 4, 1, 0);
