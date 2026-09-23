-- dev-20260922-023：统一业务单号配置（存量单号不改，仅影响新生成单号）
-- 默认：无分隔符、yyMMdd、3 位日流水；报工保留 4 位以覆盖高频场景。
-- 流水超过配置位数后由 RedisSequenceService 自动进位并发布告警。

INSERT INTO sys_config
    (config_key, config_value, config_name, config_group, remark, sort_order, is_active)
VALUES
    ('biz_no_rule.inquiry',          '{"prefix":"INQ","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', '询价单编号',       'biz_no_rule', 'INQ+yyMMdd+3位日流水', 20, 1),
    ('biz_no_rule.quotation',        '{"prefix":"QT","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}',  '报价单编号',       'biz_no_rule', 'QT+yyMMdd+3位日流水', 21, 1),
    ('biz_no_rule.purchase_order',   '{"prefix":"PO","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}',  '采购订单编号',     'biz_no_rule', 'PO+yyMMdd+3位日流水', 30, 1),
    ('biz_no_rule.inbound',          '{"prefix":"IN","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}',  '采购入库单编号',   'biz_no_rule', 'IN+yyMMdd+3位日流水（独立出单改造预留）', 40, 1),
    ('biz_no_rule.outbound',         '{"prefix":"OUT","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', '出库单编号',       'biz_no_rule', 'OUT+yyMMdd+3位日流水', 41, 1),
    ('biz_no_rule.stocktake',        '{"prefix":"ST","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}',  '盘点单编号',       'biz_no_rule', 'ST+yyMMdd+3位日流水', 42, 1),
    ('biz_no_rule.stock_gain',       '{"prefix":"SI","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}',  '盘盈入库单编号',   'biz_no_rule', 'SI+yyMMdd+3位日流水', 43, 1),
    ('biz_no_rule.stock_loss',       '{"prefix":"SKL","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', '盘亏出库单编号',   'biz_no_rule', 'SKL+yyMMdd+3位日流水', 44, 1),
    ('biz_no_rule.production_plan',  '{"prefix":"PL","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}',  '生产主单编号',     'biz_no_rule', 'PL+yyMMdd+3位日流水；改 PM 前缀留待高风险批次', 60, 1),
    ('biz_no_rule.production_order', '{"prefix":"WPO","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', '生产工单编号',     'biz_no_rule', 'WPO+yyMMdd+3位日流水；两套工单号合一留待高风险批次', 61, 1),
    ('biz_no_rule.work_report',      '{"prefix":"WR","dateFormat":"yyMMdd","digits":4,"startValue":1,"resetCycle":"DAILY"}',  '报工单编号',       'biz_no_rule', 'WR+yyMMdd+4位日流水（高频例外）', 70, 1),
    ('biz_no_rule.product',          '{"prefix":"PROD","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', '产品编码',         'biz_no_rule', 'PROD+yyMMdd+3位日流水', 72, 1),
    ('biz_no_rule.biz_requirement',  '{"prefix":"RQ","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}',  '业务需求单编号',   'biz_no_rule', 'RQ+yyMMdd+3位日流水', 80, 1),
    ('biz_no_rule.material_transfer','{"prefix":"TF","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}',  '资料转移单编号',   'biz_no_rule', 'TF+yyMMdd+3位日流水', 81, 1),
    ('biz_no_rule.quality_lot',      '{"prefix":"QL","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}',  '检验批编号',       'biz_no_rule', 'QL+yyMMdd+3位日流水', 90, 1),
    ('biz_no_rule.quality_ncr',      '{"prefix":"NCR","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', '不良台账编号',     'biz_no_rule', 'NCR+yyMMdd+3位日流水', 91, 1),
    ('biz_no_rule.quality_capa',     '{"prefix":"CAPA","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}','CAPA编号',         'biz_no_rule', 'CAPA+yyMMdd+3位日流水', 92, 1),
    ('biz_no_rule.iqc_disposition',  '{"prefix":"IQD","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', 'IQC处置单编号',    'biz_no_rule', 'IQD+yyMMdd+3位日流水', 93, 1),
    ('biz_no_rule.iqc_return',       '{"prefix":"IQR","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', 'IQC退货单编号',    'biz_no_rule', 'IQR+yyMMdd+3位日流水', 94, 1),
    ('biz_no_rule.iqc_rework',       '{"prefix":"IQW","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', 'IQC返工单编号',    'biz_no_rule', 'IQW+yyMMdd+3位日流水', 95, 1),
    ('biz_no_rule.iqc_scrap',        '{"prefix":"IQS","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}', 'IQC报废单编号',    'biz_no_rule', 'IQS+yyMMdd+3位日流水', 96, 1),
    ('biz_no_rule.finish_inbound',   '{"prefix":"WF","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}',  '完工入库单编号',   'biz_no_rule', 'WF+yyMMdd+3位日流水（独立出单预留）', 97, 1),
    ('biz_no_rule.task',             '{"prefix":"TASK","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}','生产任务编号',     'biz_no_rule', '从属编号仍按工单派生，本配置登记备用', 98, 1),
    ('biz_no_rule.pick',             '{"prefix":"PICK","dateFormat":"yyMMdd","digits":3,"startValue":1,"resetCycle":"DAILY"}','领料单编号',       'biz_no_rule', '从属编号仍按工单派生，本配置登记备用', 99, 1)
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    config_name = VALUES(config_name),
    config_group = VALUES(config_group),
    remark = VALUES(remark),
    sort_order = VALUES(sort_order),
    is_active = VALUES(is_active),
    update_time = CURRENT_TIMESTAMP;
