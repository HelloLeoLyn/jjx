-- ============================================================================
-- 173_delivery_method_carrier_link.sql
-- 任务码：dev-20260921-048（发货界面：交货方式 ↔ 承运商 联动）
--
-- 问题（用户 19:19 指出）：发货弹窗的承运商是一张平铺清单，没跟「交货方式」联动——
--   自提/我方送货根本不需要承运商，快递与物流也该分属不同承运商池。
--
-- 口径（本迁移把规则写进字典，前端只按 ext_data 过滤，不再硬编码）：
--   · sales_delivery_method 项 ext_data: {"needCarrier": true/false}
--       快递/物流 → true（需要承运商与物流单号）；自提/送货上门 → false（隐藏并清空）
--   · sales_carrier 项 ext_data: {"methods": ["快递","物流"]}
--       未标注 = 所有交货方式都适用
--
-- 幂等：全量覆盖这两类字典的 ext_data（可重复执行）。
-- ============================================================================

-- 1) 交货方式：是否需要承运商
UPDATE sys_dict_item SET ext_data = '{"needCarrier": true}'  WHERE dict_code = 'sales_delivery_method' AND item_value IN ('快递', '物流');
UPDATE sys_dict_item SET ext_data = '{"needCarrier": false}' WHERE dict_code = 'sales_delivery_method' AND item_value IN ('自提', '送货上门');

-- 2) 承运商适用交货方式
UPDATE sys_dict_item SET ext_data = '{"methods": ["快递"]}'        WHERE dict_code = 'sales_carrier' AND item_value IN ('中通快递', '圆通速递', '韵达快递', '申通快递', '邮政EMS', '极兔速递');
UPDATE sys_dict_item SET ext_data = '{"methods": ["快递", "物流"]}' WHERE dict_code = 'sales_carrier' AND item_value IN ('顺丰速运', '京东物流');
UPDATE sys_dict_item SET ext_data = '{"methods": ["物流"]}'        WHERE dict_code = 'sales_carrier' AND item_value IN ('德邦物流', '货拉拉');
UPDATE sys_dict_item SET ext_data = '{"methods": ["自提"]}'        WHERE dict_code = 'sales_carrier' AND item_value IN ('客户自提');
UPDATE sys_dict_item SET ext_data = NULL                          WHERE dict_code = 'sales_carrier' AND item_value IN ('其他');
