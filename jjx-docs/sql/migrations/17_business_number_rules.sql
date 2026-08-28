-- dev-20260827-031 单据编号规则配置化
-- prefix/dateFormat/digits 均以迁移前实际生成格式为准。
INSERT INTO sys_config
    (config_key, config_value, config_name, config_group, remark, sort_order, is_active)
VALUES
    ('biz_no_rule.sales_order',     '{"prefix":"SO","dateFormat":"yyMMdd","digits":3}',     '销售订单编号', 'biz_no_rule', 'SO+yyMMdd+3位序号', 10, 1),
    ('biz_no_rule.quotation',       '{"prefix":"QT","dateFormat":"yyMMdd","digits":4}',     '报价单编号', 'biz_no_rule', 'QT+yyMMdd+4位序号', 20, 1),
    ('biz_no_rule.purchase_order',  '{"prefix":"PO","dateFormat":"yyyyMMdd","digits":4}',   '采购订单编号', 'biz_no_rule', 'PO+yyyyMMdd+4位序号', 30, 1),
    ('biz_no_rule.inbound',         '{"prefix":"IN","dateFormat":"yyyyMMdd","digits":4}',   '入库单编号', 'biz_no_rule', 'IN+yyyyMMdd+4位序号', 40, 1),
    ('biz_no_rule.outbound',        '{"prefix":"OUT","dateFormat":"yyyyMMdd","digits":4}',  '出库单编号', 'biz_no_rule', 'OUT+yyyyMMdd+4位序号', 50, 1),
    ('biz_no_rule.production_plan', '{"prefix":"PL","dateFormat":"yyMMdd","digits":4}',     '生产计划编号', 'biz_no_rule', 'PL+yyMMdd+4位序号', 60, 1),
    ('biz_no_rule.work_report',     '{"prefix":"WR-","dateFormat":"yyyyMMdd-","digits":4}', '报工单编号', 'biz_no_rule', 'WR-yyyyMMdd-+4位序号', 70, 1)
ON DUPLICATE KEY UPDATE
    config_key = VALUES(config_key);
