-- 任务码：dev-20261107-017
-- 目的：55 家在用供应商补「简称」与「首字母缩写」（用户 2026-11-07 确认草稿后写入）。
-- 规则：简称＝去地名/公司后缀/行业词后的字号；缩写＝字号拼音首字母（大写）。
-- 幂等：按 supplier_code 定位，且仅在该行两字段均为空时写入（已人工维护的行不覆盖）。
-- 影响：业务数据批量 UPDATE（55 行）；执行前需手工全库备份（仓库内已有 2026-11-07 备份）。

UPDATE purchase_supplier SET short_name = '艾高科', abbr = 'AGK', update_time = NOW()
WHERE supplier_code = 'SUP00001' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '爱赛雅', abbr = 'ASY', update_time = NOW()
WHERE supplier_code = 'SUP00002' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '博明', abbr = 'BM', update_time = NOW()
WHERE supplier_code = 'SUP00003' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '彩荣', abbr = 'CR', update_time = NOW()
WHERE supplier_code = 'SUP00004' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '成浩林', abbr = 'CHL', update_time = NOW()
WHERE supplier_code = 'SUP00005' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '诚亿光', abbr = 'CYG', update_time = NOW()
WHERE supplier_code = 'SUP00006' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '贯昌', abbr = 'GC', update_time = NOW()
WHERE supplier_code = 'SUP00007' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '鸿发', abbr = 'HF', update_time = NOW()
WHERE supplier_code = 'SUP00008' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '鸿钜', abbr = 'HJ', update_time = NOW()
WHERE supplier_code = 'SUP00009' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '鸿盛', abbr = 'HS', update_time = NOW()
WHERE supplier_code = 'SUP00010' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '佳华实', abbr = 'JHS', update_time = NOW()
WHERE supplier_code = 'SUP00011' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '集鑫福', abbr = 'JXF', update_time = NOW()
WHERE supplier_code = 'SUP00012' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '聚美', abbr = 'JM', update_time = NOW()
WHERE supplier_code = 'SUP00013' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '利星辉', abbr = 'LXH', update_time = NOW()
WHERE supplier_code = 'SUP00014' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '瑞擎', abbr = 'RQ', update_time = NOW()
WHERE supplier_code = 'SUP00015' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '华利三和', abbr = 'HLSH', update_time = NOW()
WHERE supplier_code = 'SUP00016' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '尚昇', abbr = 'SS', update_time = NOW()
WHERE supplier_code = 'SUP00017' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '天影', abbr = 'TY', update_time = NOW()
WHERE supplier_code = 'SUP00018' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '万绰', abbr = 'WC', update_time = NOW()
WHERE supplier_code = 'SUP00019' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '向日葵', abbr = 'XRK', update_time = NOW()
WHERE supplier_code = 'SUP00020' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '新创艺', abbr = 'XCY', update_time = NOW()
WHERE supplier_code = 'SUP00021' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '新亚洲', abbr = 'XYZ', update_time = NOW()
WHERE supplier_code = 'SUP00022' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '鑫旺达', abbr = 'XWD', update_time = NOW()
WHERE supplier_code = 'SUP00023' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '轩腾', abbr = 'XT', update_time = NOW()
WHERE supplier_code = 'SUP00024' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '雅艺铭', abbr = 'YYM', update_time = NOW()
WHERE supplier_code = 'SUP00025' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '依美', abbr = 'YM', update_time = NOW()
WHERE supplier_code = 'SUP00026' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '众志达', abbr = 'ZZD', update_time = NOW()
WHERE supplier_code = 'SUP00027' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '富华盛', abbr = 'FHS', update_time = NOW()
WHERE supplier_code = 'SUP00028' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '桥宇', abbr = 'QY', update_time = NOW()
WHERE supplier_code = 'SUP00029' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '新华安', abbr = 'XHA', update_time = NOW()
WHERE supplier_code = 'SUP00030' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '春深', abbr = 'CS', update_time = NOW()
WHERE supplier_code = 'SUP00031' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '粤明华', abbr = 'YMH', update_time = NOW()
WHERE supplier_code = 'SUP00032' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '鑫信泰', abbr = 'XXT', update_time = NOW()
WHERE supplier_code = 'SUP00033' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '博一', abbr = 'BY', update_time = NOW()
WHERE supplier_code = 'SUP00034' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '博恒', abbr = 'BH', update_time = NOW()
WHERE supplier_code = 'SUP00035' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '峰瑞创', abbr = 'FRC', update_time = NOW()
WHERE supplier_code = 'SUP00036' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '龙跃宇', abbr = 'LYY', update_time = NOW()
WHERE supplier_code = 'SUP00037' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '舜禹通', abbr = 'SYT', update_time = NOW()
WHERE supplier_code = 'SUP00038' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '菘翊', abbr = 'SY', update_time = NOW()
WHERE supplier_code = 'SUP00039' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '伟壹昊', abbr = 'WYH', update_time = NOW()
WHERE supplier_code = 'SUP00040' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '鑫洋', abbr = 'XY', update_time = NOW()
WHERE supplier_code = 'SUP00041' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '立凡', abbr = 'LF', update_time = NOW()
WHERE supplier_code = 'SUP00042' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '闽盛', abbr = 'MS', update_time = NOW()
WHERE supplier_code = 'SUP00043' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '拓图', abbr = 'TT', update_time = NOW()
WHERE supplier_code = 'SUP00044' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '银亮', abbr = 'YL', update_time = NOW()
WHERE supplier_code = 'SUP00045' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '永安兴', abbr = 'YAX', update_time = NOW()
WHERE supplier_code = 'SUP00046' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '加韵', abbr = 'JY', update_time = NOW()
WHERE supplier_code = 'SUP00047' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '佳晔盛', abbr = 'JYS', update_time = NOW()
WHERE supplier_code = 'SUP00048' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '金地鑫', abbr = 'JDX', update_time = NOW()
WHERE supplier_code = 'SUP00049' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '明昶', abbr = 'MC', update_time = NOW()
WHERE supplier_code = 'SUP00050' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '色彩', abbr = 'SC', update_time = NOW()
WHERE supplier_code = 'SUP00051' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '凯利', abbr = 'KL', update_time = NOW()
WHERE supplier_code = 'SUP00052' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '夏欣', abbr = 'XX', update_time = NOW()
WHERE supplier_code = 'SUP00053' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '全通', abbr = 'QT', update_time = NOW()
WHERE supplier_code = 'SUP00054' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

UPDATE purchase_supplier SET short_name = '新晟', abbr = 'XC', update_time = NOW()
WHERE supplier_code = 'SUP00055' AND del_flag = '0'
  AND (short_name IS NULL OR short_name = '') AND (abbr IS NULL OR abbr = '');

SELECT COUNT(*) AS total, SUM(short_name IS NOT NULL AND short_name <> '') AS with_short,
       SUM(abbr IS NOT NULL AND abbr <> '') AS with_abbr
FROM purchase_supplier WHERE del_flag = '0';
