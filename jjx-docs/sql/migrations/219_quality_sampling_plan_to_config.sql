-- risk: low
-- dev-20260924-021（抽样方案 quality_sampling_plan → sys_config JSON）
-- 依据 CONVENTIONS §14：该数据 8 行、基本不变 → 用系统配置承载，不占表。
-- 1) 宽化 sys_config.config_value（原 varchar(2000) 对本条 JSON（约 1.9K 字符）余量太小；
--    改动仅扩宽，不改类型、不动已有数据）。
-- 2) 种子：8 条方案写入 sys_config（幂等：已存在则不覆盖，保留用户后续修改）。
-- 3) 旧表 quality_sampling_plan 删除（数据已完整落入配置；回滚路径：
--    jjx-docs/sql/backups/quality_sampling_plan_to_config_20260924-1525-guard.sql 含该表 DDL+数据）。

ALTER TABLE sys_config MODIFY COLUMN config_value VARCHAR(4000) NOT NULL COMMENT '配置值';

INSERT INTO sys_config (config_key, config_value, config_name, config_group, remark, sort_order, is_active, create_time, update_time)
SELECT 'quality.sampling_plan',
       '[{"planId":2,"planName":"AQL1.0 II级 91-150","lotType":"IQC","aqlValue":1.0,"inspectionLevel":"II","lotMin":91,"lotMax":150,"sampleQuantity":20,"acceptNumber":1,"rejectNumber":2,"isEnabled":1,"remark":"示例配置，可按实际标准调整"},{"planId":3,"planName":"AQL1.0 II级 151-280","lotType":"IQC","aqlValue":1.0,"inspectionLevel":"II","lotMin":151,"lotMax":280,"sampleQuantity":32,"acceptNumber":1,"rejectNumber":2,"isEnabled":1,"remark":"示例配置，可按实际标准调整"},{"planId":4,"planName":"AQL1.0 II级 281-500","lotType":"IQC","aqlValue":1.0,"inspectionLevel":"II","lotMin":281,"lotMax":500,"sampleQuantity":50,"acceptNumber":1,"rejectNumber":2,"isEnabled":1,"remark":"示例配置，可按实际标准调整"},{"planId":5,"planName":"AQL1.0 II级 501-1200","lotType":"IQC","aqlValue":1.0,"inspectionLevel":"II","lotMin":501,"lotMax":1200,"sampleQuantity":80,"acceptNumber":2,"rejectNumber":3,"isEnabled":1,"remark":"示例配置，可按实际标准调整"},{"planId":6,"planName":"OQC-91.0000-150.0000","lotType":"OQC","aqlValue":1.0,"inspectionLevel":"II","lotMin":91,"lotMax":150,"sampleQuantity":20,"acceptNumber":1,"rejectNumber":2,"isEnabled":1,"remark":"复用 IQC AQL 区间作为 OQC 初始方案"},{"planId":7,"planName":"OQC-151.0000-280.0000","lotType":"OQC","aqlValue":1.0,"inspectionLevel":"II","lotMin":151,"lotMax":280,"sampleQuantity":32,"acceptNumber":1,"rejectNumber":2,"isEnabled":1,"remark":"复用 IQC AQL 区间作为 OQC 初始方案"},{"planId":8,"planName":"OQC-281.0000-500.0000","lotType":"OQC","aqlValue":1.0,"inspectionLevel":"II","lotMin":281,"lotMax":500,"sampleQuantity":50,"acceptNumber":1,"rejectNumber":2,"isEnabled":1,"remark":"复用 IQC AQL 区间作为 OQC 初始方案"},{"planId":9,"planName":"OQC-501.0000-1200.0000","lotType":"OQC","aqlValue":1.0,"inspectionLevel":"II","lotMin":501,"lotMax":1200,"sampleQuantity":80,"acceptNumber":2,"rejectNumber":3,"isEnabled":1,"remark":"复用 IQC AQL 区间作为 OQC 初始方案"}]',
       '抽样方案(AQL)', 'quality_config',
       '来料/成品建批按批量区间匹配样本量/AC/RE（dev-20260924-021 由 quality_sampling_plan 表迁入）',
       0, 1, NOW(), NOW()
FROM (SELECT 1) d
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT config_key FROM sys_config WHERE config_key = 'quality.sampling_plan') x
);

DROP TABLE IF EXISTS quality_sampling_plan;
