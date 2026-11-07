-- 任务码：dev-20261107-017
-- 目的：供应商新增「简称」与「首字母缩写」，供物料命名规则「供应商·简称/缩写」勾选项取值。
-- 口径（用户 2026-11-07 定稿）：简称 short_name（如 艾高科）、缩写 abbr（如 AGK，字母或数字 1-8 位，保存转大写）。
-- 唯一性：由服务层校验（排除软删除行），不建唯一索引——软删除会让缩写被永久占用、无法复用。
-- 幂等：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS；本迁移由 db-migrate 账本保证只执行一次。
-- 影响：表结构变更（purchase_supplier 加两列 + 一个普通索引）；执行前需手工全库备份（2026-11-07 备份已在仓库内）。

ALTER TABLE purchase_supplier
    ADD COLUMN short_name varchar(50) NULL COMMENT '供应商简称（物料命名规则「供应商·简称」取值）' AFTER supplier_name,
    ADD COLUMN abbr varchar(16) NULL COMMENT '首字母缩写（物料命名规则「供应商·缩写」取值）' AFTER short_name,
    ADD KEY idx_supplier_abbr (abbr);

SELECT column_name, column_type, is_nullable, column_comment
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'purchase_supplier'
  AND column_name IN ('short_name', 'abbr');
