-- ============================================================================
-- 171_dict_delivery_method_carrier.sql
-- 任务码：dev-20260921-039（发货弹窗人性化：交货方式 / 承运商 可维护字典）
--
-- 背景：用户反馈发货弹窗「交货方式」是自由文本、承运商没得选。
--   字典表 sys_dict / sys_dict_item 已存在（字典管理页面可维护），故不再代码内置清单。
--
-- 口径：
--   · item_value = 中文（真正落库到 sales_delivery.delivery_method / carrier，打印送货单直接显示）
--   · item_key   = 英文代码（便于外部对接）
--   · 前端下拉「可选择 + 可输入新值」，新值后续可在字典管理里补录
--
-- 幂等：按 dict_code 判断存在则跳过；字典项按 (dict_code,item_key) 去重插入。
-- ============================================================================

SET @db := DATABASE();

-- 1) 字典头
INSERT INTO sys_dict (dict_code, dict_name, dict_group, remark, sort_order, is_active)
SELECT 'sales_delivery_method', '销售交货方式', 'sales', '发货单「交货方式」下拉（值即中文，直接落库/打印）', 100, 1
 WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'sales_delivery_method' AND deleted = 0);

INSERT INTO sys_dict (dict_code, dict_name, dict_group, remark, sort_order, is_active)
SELECT 'sales_carrier', '销售承运商', 'sales', '发货单「承运商」下拉（值即中文，直接落库/打印）', 101, 1
 WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'sales_carrier' AND deleted = 0);

-- 2) 交货方式字典项
INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, sort_order, is_active)
SELECT * FROM (
  SELECT 'sales_delivery_method' AS dict_code, 'express'     AS item_key, '快递'     AS item_value, '快递'     AS label, 1 AS sort_order, 1 AS is_active UNION ALL
  SELECT 'sales_delivery_method', 'logistics',   '物流',     '物流',     2, 1 UNION ALL
  SELECT 'sales_delivery_method', 'self_pickup', '自提',     '自提',     3, 1 UNION ALL
  SELECT 'sales_delivery_method', 'delivery',    '送货上门', '送货上门', 4, 1
) t
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item i WHERE i.dict_code = t.dict_code AND i.item_key = t.item_key AND i.deleted = 0);

-- 3) 承运商字典项
INSERT INTO sys_dict_item (dict_code, item_key, item_value, label, sort_order, is_active)
SELECT * FROM (
  SELECT 'sales_carrier' AS dict_code, 'sf'      AS item_key, '顺丰速运' AS item_value, '顺丰速运' AS label, 1 AS sort_order, 1 AS is_active UNION ALL
  SELECT 'sales_carrier', 'jd',       '京东物流', '京东物流',  2, 1 UNION ALL
  SELECT 'sales_carrier', 'deppon',   '德邦物流', '德邦物流',  3, 1 UNION ALL
  SELECT 'sales_carrier', 'zto',      '中通快递', '中通快递',  4, 1 UNION ALL
  SELECT 'sales_carrier', 'yto',      '圆通速递', '圆通速递',  5, 1 UNION ALL
  SELECT 'sales_carrier', 'yunda',    '韵达快递', '韵达快递',  6, 1 UNION ALL
  SELECT 'sales_carrier', 'sto',      '申通快递', '申通快递',  7, 1 UNION ALL
  SELECT 'sales_carrier', 'ems',      '邮政EMS',  '邮政EMS',   8, 1 UNION ALL
  SELECT 'sales_carrier', 'jtexpress','极兔速递', '极兔速递',  9, 1 UNION ALL
  SELECT 'sales_carrier', 'huolala',  '货拉拉',   '货拉拉',   10, 1 UNION ALL
  SELECT 'sales_carrier', 'pickup',   '客户自提', '客户自提', 11, 1 UNION ALL
  SELECT 'sales_carrier', 'other',    '其他',     '其他',     99, 1
) t
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item i WHERE i.dict_code = t.dict_code AND i.item_key = t.item_key AND i.deleted = 0);
