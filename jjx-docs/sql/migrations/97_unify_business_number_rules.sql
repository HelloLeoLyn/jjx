-- dev-20260911-006 统一业务编码规则与数据库原子取号

CREATE TABLE IF NOT EXISTS `sys_number_sequence` (
  `sequence_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sequence_key` varchar(100) NOT NULL COMMENT '业务编码类型',
  `period_key` varchar(20) NOT NULL COMMENT '重置周期键，GLOBAL/日/月/年',
  `current_value` bigint NOT NULL COMMENT '当前流水值',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`sequence_id`),
  UNIQUE KEY `uk_sequence_period` (`sequence_key`, `period_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务编码原子序列';

INSERT INTO sys_config
  (config_key, config_value, config_name, config_group, remark, sort_order, is_active)
VALUES
  ('biz_no_rule.sales_order',      '{"prefix":"SO","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', '销售订单编号', 'biz_no_rule', 'SO+yyMMdd+3位日流水', 10, 1),
  ('biz_no_rule.sample_order',     '{"prefix":"SP","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', '样品单编号', 'biz_no_rule', 'SP+yyMMdd+3位日流水', 11, 1),
  ('biz_no_rule.sales_return',     '{"prefix":"RTN","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', '销售退货单编号', 'biz_no_rule', 'RTN+yyMMdd+3位日流水', 12, 1),
  ('biz_no_rule.sales_delivery',   '{"prefix":"DL","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', '销售发货单编号', 'biz_no_rule', 'DL+yyMMdd+3位日流水', 13, 1),
  ('biz_no_rule.customer',         '{"prefix":"CUS","dateFormat":"","digits":5,"startValue":1,"resetCycle":"NONE"}', '客户编码', 'biz_no_rule', 'CUS+5位全局流水', 14, 1),
  ('biz_no_rule.quotation',        '{"prefix":"QT","dateFormat":"yyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}', '报价单编号', 'biz_no_rule', 'QT+yyMMdd+4位日流水', 20, 1),
  ('biz_no_rule.inquiry',          '{"prefix":"INQ","dateFormat":"yyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}', '询价单编号', 'biz_no_rule', 'INQ+yyMMdd+4位日流水', 21, 1),
  ('biz_no_rule.purchase_order',   '{"prefix":"PO","dateFormat":"yyyyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}', '采购订单编号', 'biz_no_rule', 'PO+yyyyMMdd+4位日流水', 30, 1),
  ('biz_no_rule.supplier',         '{"prefix":"SUP","dateFormat":"","digits":5,"startValue":1,"resetCycle":"NONE"}', '供应商编码', 'biz_no_rule', 'SUP+5位全局流水', 31, 1),
  ('biz_no_rule.inbound',          '{"prefix":"IN","dateFormat":"yyyyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}', '入库单编号', 'biz_no_rule', 'IN+yyyyMMdd+4位日流水', 40, 1),
  ('biz_no_rule.outbound',         '{"prefix":"OUT","dateFormat":"yyyyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}', '出库单编号', 'biz_no_rule', 'OUT+yyyyMMdd+4位日流水', 41, 1),
  ('biz_no_rule.stocktake',        '{"prefix":"ST","dateFormat":"yyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}', '盘点单编号', 'biz_no_rule', 'ST+yyMMdd+4位日流水', 42, 1),
  ('biz_no_rule.stock_gain',       '{"prefix":"SI","dateFormat":"yyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}', '盘盈入库单编号', 'biz_no_rule', 'SI+yyMMdd+4位日流水', 43, 1),
  ('biz_no_rule.stock_loss',       '{"prefix":"SKL","dateFormat":"yyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}', '盘亏出库单编号', 'biz_no_rule', 'SKL+yyMMdd+4位日流水', 44, 1),
  ('biz_no_rule.material',         '{"prefix":"MTR","dateFormat":"","digits":6,"startValue":1,"resetCycle":"NONE"}', '物料编码', 'biz_no_rule', '物料类型动态前缀+6位全局流水', 45, 1),
  ('biz_no_rule.production_plan',  '{"prefix":"PL","dateFormat":"yyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}', '生产计划编号', 'biz_no_rule', 'PL+yyMMdd+4位日流水', 60, 1),
  ('biz_no_rule.production_order', '{"prefix":"WPO","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', '生产订单编号', 'biz_no_rule', 'WPO+yyMMdd+3位日流水', 61, 1),
  ('biz_no_rule.work_report',      '{"prefix":"WR-","dateFormat":"yyyyMMdd-","digits":4,"startValue":1,"resetCycle":"DAILY"}', '报工单编号', 'biz_no_rule', 'WR-yyyyMMdd-+4位日流水', 70, 1),
  ('biz_no_rule.tooling',          '{"prefix":"WK","dateFormat":"yyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}', '工装模具编号', 'biz_no_rule', '类型动态前缀+yyMMdd+4位日流水', 71, 1),
  ('biz_no_rule.product',          '{"prefix":"PROD","dateFormat":"yyyyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}', '产品编码', 'biz_no_rule', 'PROD+yyyyMMdd+4位日流水', 72, 1),
  ('biz_no_rule.biz_requirement',  '{"prefix":"RQ","dateFormat":"yyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}', '业务需求单编号', 'biz_no_rule', 'RQ+yyMMdd+4位日流水', 80, 1),
  ('biz_no_rule.material_transfer','{"prefix":"TF","dateFormat":"yyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}', '资料转移单编号', 'biz_no_rule', 'TF+yyMMdd+4位日流水', 81, 1)
ON DUPLICATE KEY UPDATE
  config_value = VALUES(config_value), config_name = VALUES(config_name),
  config_group = VALUES(config_group), remark = VALUES(remark),
  sort_order = VALUES(sort_order), is_active = VALUES(is_active), update_time = NOW();

-- 破坏性语句说明：旧工装模板规则已由 biz_no_rule.tooling 完全替代，删除重复配置入口。
DELETE FROM sys_config WHERE config_key = 'tooling_no_rule';
