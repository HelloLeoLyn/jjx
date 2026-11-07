-- 任务码：dev-20261107-017
-- 目的：按用户 2026-11-07 复核结果订正一家供应商缩写——SUP00055 新晟 XC → XS（晟按 shèng 读音）。
--       00016 深圳华利三和科技 维持 HLSH，本条不改动。
-- 幂等：按 supplier_code 定位且仅当当前值为 XC/空时写入；重复执行影响 0 行。
-- 影响：单行业务数据订正；执行前需手工全库备份（仓库内已有 2026-11-07 最新备份，db-migrate 自动选用）。

UPDATE purchase_supplier
SET abbr = 'XS', update_time = NOW()
WHERE supplier_code = 'SUP00055' AND del_flag = '0'
  AND (abbr = 'XC' OR abbr IS NULL OR abbr = '');

SELECT supplier_code, supplier_name, short_name, abbr
FROM purchase_supplier WHERE supplier_code IN ('SUP00016', 'SUP00055') AND del_flag = '0';
