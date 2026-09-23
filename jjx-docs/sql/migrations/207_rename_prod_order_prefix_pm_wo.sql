-- dev-20260923-029：单号·第 5 批（A 方案）—— 生产主单 PL→PM、生产工单 WPO→WO（仅改配置前缀，存量单号不追改）
--
-- 背景：dev-20260922-023《单号规则总表》§6 第 5 批（该表标注为高风险、建议单独立项）。
--       用户 2026-09-23 拍板 A：工单号统一走号段（废弃 `WO-<计划号>-NN` 派生路径）、采购入库拆号，
--       并决定主单 PL→PM 与本批**一起做**（否则要占两个迁移号）。
--       设计稿：jjx-docs/design/doc-no-batch5-workorder-merge-dev-20260923-029.md
--
-- 本迁移只做"配置前缀换名"，一行存量单据都不动（总则 §6：存量不追改）：
--   ① biz_no_rule.production_plan   PL  → PM   （主单，总则 §4 前缀登记表：PM=生产订单主单）
--   ② biz_no_rule.production_order  WPO → WO   （工单唯一格式，两套工单号合一的关键）
--   ③ biz_no_rule.inbound remark 去掉"预留"字样（采购入库本次开始真正独立出号）
--
-- 上线顺序（关键）：必须与代码改动同批上线 —— 停用 ProductionOrderServiceImpl.generateWorkOrderNo()
--   派生日志、采购入库改走号段（InventoryInboundServiceImpl:1759/:266）、任务号 T 位 2 位
--   （ProductionTaskServiceImpl:1037）、前端工单号识别正则（jjx-web/src/composables/useScanner.ts:30）。
--   只跑迁移不改代码 → 旧派生路径仍会产出 `WO-<主单号>-NN`，同一实体两种格式，比现状更糟。
--
-- 幂等：条件带"当前前缀仍为旧值"（JSON_EXTRACT 比对），重复执行第二次为 0 行；
--       不改 digits/dateFormat/resetCycle/startValue 等其它字段；不动 sys_number_sequence
--       （号段按 bizType + 周期键计数，前缀换名不影响当日流水，新老号并存）。
--
-- 风险分级与备份：仅 sys_config 三行、无 DDL、无存量数据改写 → 低风险幂等配置变更
--   （AGENTS.md 备份分级：低风险幂等配置/字典变更由用户按需决定是否备份；
--     若决定备份，表级 guard 即可：mysqldump --where="config_key LIKE 'biz_no_rule.%'" sys_config）。
-- 回滚：把两处 prefix 改回 PL / WPO（同样条件更新即可）；已生成的新号不回收、不追改。

-- ① 生产主单：PL → PM
UPDATE sys_config
   SET config_value = JSON_SET(config_value, '$.prefix', 'PM'),
       remark       = 'PM+yyMMdd+3位日流水（原 PL；dev-20260923-029 第 5 批）',
       update_time  = CURRENT_TIMESTAMP
 WHERE config_key = 'biz_no_rule.production_plan'
   AND JSON_UNQUOTE(JSON_EXTRACT(config_value, '$.prefix')) = 'PL';

-- ② 生产工单：WPO → WO（两套工单号合一）
UPDATE sys_config
   SET config_value = JSON_SET(config_value, '$.prefix', 'WO'),
       remark       = 'WO+yyMMdd+3位日流水（两套工单号已合一；dev-20260923-029 第 5 批）',
       update_time  = CURRENT_TIMESTAMP
 WHERE config_key = 'biz_no_rule.production_order'
   AND JSON_UNQUOTE(JSON_EXTRACT(config_value, '$.prefix')) = 'WPO';

-- ③ 采购入库单：本批开始独立出号（配置键与 IN 前缀原已存在，仅更新说明）
UPDATE sys_config
   SET remark      = 'IN+yyMMdd+3位日流水（采购入库已拆号，不再复用采购单号；dev-20260923-029）',
       update_time = CURRENT_TIMESTAMP
 WHERE config_key = 'biz_no_rule.inbound'
   AND IFNULL(remark, '') NOT LIKE '%已拆号%';

-- 验收（只读，迁移后人工核对；期望：两行前缀分别 = PM / WO）
--   SELECT config_key,
--          JSON_UNQUOTE(JSON_EXTRACT(config_value,'$.prefix')) AS prefix,
--          JSON_UNQUOTE(JSON_EXTRACT(config_value,'$.digits')) AS digits,
--          remark
--     FROM sys_config
--    WHERE config_key IN ('biz_no_rule.production_plan','biz_no_rule.production_order','biz_no_rule.inbound');
