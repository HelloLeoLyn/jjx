-- 55_sys_config_company_profile.sql
-- 打印抬头公司真值（任务1328，2026-09-03）
-- 来源：Leo 提供的开票资料及银行资料（2026-09-03 20:47）
UPDATE sys_config SET config_value = '深圳市精捷信科技有限公司' WHERE config_key = 'company_name';
UPDATE sys_config SET config_value = '深圳市宝安区沙井街道共和社区新和大道丽城科技工业园F栋四层' WHERE config_key = 'company_address';
UPDATE sys_config SET config_value = '0755-29856711' WHERE config_key = 'company_phone';
UPDATE sys_config SET config_value = '91440300MADF8P8N5G' WHERE config_key = 'company_tax_no';
UPDATE sys_config SET config_value = '中国工商银行股份有限公司深圳沙井支行' WHERE config_key = 'company_bank';
UPDATE sys_config SET config_value = '4000022509201159539' WHERE config_key = 'company_account';

-- 开户行行号（新键备用，抬头暂不展示）
INSERT INTO sys_config (config_key, config_value, config_name, config_group, sort_order, is_active)
SELECT 'company_bank_code', '1102584002258', '开户行行号', 'pdf_template', 12, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'company_bank_code');

-- 部门名统一（Leo 2026-09-03 20:49）：深圳市精捷信有限公司 → 深圳市精捷信科技有限公司（与开票户名一致）
UPDATE sys_dept SET dept_name = '深圳市精捷信科技有限公司' WHERE dept_id = 16;
