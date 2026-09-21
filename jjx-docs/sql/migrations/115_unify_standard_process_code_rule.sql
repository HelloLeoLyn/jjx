-- ============================================================================
-- 115: 标准工序编码统一为单规则 SP-<段位><序号>
-- 任务码: dev-20260916-002
-- 背景: 2026-09-16 用户定口径——工序编码只保留一条规则，不再两套并存。
--       原存在两套：① SP-1xx/2xx/3xx/4xx/5xx（2026-08-08 导入的历史码，段位=结构）
--                   ② T<工序类型>C<工序类别><3位序号>（旧界面生成逻辑，如 TOTHERCOTHER003 / TPANELCPANEL001）
-- 单规则: SP-<段位><序号>
--       段位固定 1 位：1 面板(PANEL) / 2 上线(UP_LINE) / 3 下线(DOWN_LINE) / 4 其他(OTHER)
--       序号 = 段内现有最大序号 + 1，2 位起不足补 0（超 99 自动扩位），不回收、不复用
--       解析口径：SP- 之后第 1 位字符 = 段位，其余数字 = 段内序号
--       真源：jjx-server .../product/enums/ProcessCategoryEnum.java（enum 字段 segment）
-- 做法: 逐段位收口——凡"工序类别与编码段位不一致"或"编码不符合规则"的行，按段位重排为该段下一个可用号；
--       已符合规则的行（含 1xx/2xx/3xx/4xx 全部历史码）保持不动，零改写。
--       排序确定性：display_order, process_id。
--       另：engineering_film.process_code 是工序编码文本快照（其余业务关联均按 process_id）→ 一并同步。
-- 幂等: 每段只处理"该类别下不符合 ^SP-<段位>\d{2,}$"的行，重复执行匹配 0 行。
-- 执行顺序: 先执行本迁移，再重启后端（后端新生成逻辑按 1-4 段发码）；顺序反了会多发 T 码，需再跑一次本迁移。
-- 备注: 第二次修订——首版只处理"其他"段且菲林同步语句有排序规则冲突（utf8mb4_0900_ai_ci vs utf8mb4_unicode_ci），
--       本版改为四段通用 + CONVERT(... COLLATE ...) 规避；首版 UPDATE 已生效部分（其他段重排）在本版中匹配 0 行，可安全重跑。
-- ============================================================================
USE `jjx_erp_db`;

-- ① 执行前体检：列出全库不符合单规则的编码
SELECT '体检：段位与工序类别不一致的行（重排前）' AS check_point;
SELECT process_id, process_code, process_name, process_category
FROM engineering_standard_process
WHERE (process_category = 'PANEL'     AND process_code NOT REGEXP '^SP-1[0-9]{2,}$')
   OR (process_category = 'UP_LINE'   AND process_code NOT REGEXP '^SP-2[0-9]{2,}$')
   OR (process_category = 'DOWN_LINE' AND process_code NOT REGEXP '^SP-3[0-9]{2,}$')
   OR (process_category = 'OTHER'     AND process_code NOT REGEXP '^SP-4[0-9]{2,}$')
   OR (process_category IS NULL      AND process_code NOT REGEXP '^SP-[1-4][0-9]{2,}$')
ORDER BY process_category, display_order, process_id;

-- ② 1 段｜面板（PANEL）
UPDATE engineering_standard_process p
JOIN (
    SELECT s.process_id,
           CONCAT('SP-1', LPAD(base.max_seq + ROW_NUMBER() OVER (ORDER BY s.display_order, s.process_id), 2, '0')) AS new_code
    FROM engineering_standard_process s
    CROSS JOIN (SELECT COALESCE(MAX(CAST(SUBSTRING(process_code, 5) AS UNSIGNED)), 0) AS max_seq
                FROM engineering_standard_process
                WHERE process_code REGEXP '^SP-1[0-9]{2,}$') base
    WHERE s.process_category = 'PANEL'
      AND s.process_code NOT REGEXP '^SP-1[0-9]{2,}$'
) x ON x.process_id = p.process_id
SET p.process_code = x.new_code,
    p.update_time = NOW();

-- ② 2 段｜上线（UP_LINE）
UPDATE engineering_standard_process p
JOIN (
    SELECT s.process_id,
           CONCAT('SP-2', LPAD(base.max_seq + ROW_NUMBER() OVER (ORDER BY s.display_order, s.process_id), 2, '0')) AS new_code
    FROM engineering_standard_process s
    CROSS JOIN (SELECT COALESCE(MAX(CAST(SUBSTRING(process_code, 5) AS UNSIGNED)), 0) AS max_seq
                FROM engineering_standard_process
                WHERE process_code REGEXP '^SP-2[0-9]{2,}$') base
    WHERE s.process_category = 'UP_LINE'
      AND s.process_code NOT REGEXP '^SP-2[0-9]{2,}$'
) x ON x.process_id = p.process_id
SET p.process_code = x.new_code,
    p.update_time = NOW();

-- ② 3 段｜下线（DOWN_LINE）
UPDATE engineering_standard_process p
JOIN (
    SELECT s.process_id,
           CONCAT('SP-3', LPAD(base.max_seq + ROW_NUMBER() OVER (ORDER BY s.display_order, s.process_id), 2, '0')) AS new_code
    FROM engineering_standard_process s
    CROSS JOIN (SELECT COALESCE(MAX(CAST(SUBSTRING(process_code, 5) AS UNSIGNED)), 0) AS max_seq
                FROM engineering_standard_process
                WHERE process_code REGEXP '^SP-3[0-9]{2,}$') base
    WHERE s.process_category = 'DOWN_LINE'
      AND s.process_code NOT REGEXP '^SP-3[0-9]{2,}$'
) x ON x.process_id = p.process_id
SET p.process_code = x.new_code,
    p.update_time = NOW();

-- ② 4 段｜其他（OTHER）——含历史 5 段（弹片/其他）与全部 T<类型>C<类别> 码
UPDATE engineering_standard_process p
JOIN (
    SELECT s.process_id,
           CONCAT('SP-4', LPAD(base.max_seq + ROW_NUMBER() OVER (ORDER BY s.display_order, s.process_id), 2, '0')) AS new_code
    FROM engineering_standard_process s
    CROSS JOIN (SELECT COALESCE(MAX(CAST(SUBSTRING(process_code, 5) AS UNSIGNED)), 0) AS max_seq
                FROM engineering_standard_process
                WHERE process_code REGEXP '^SP-4[0-9]{2,}$') base
    WHERE s.process_category = 'OTHER'
      AND s.process_code NOT REGEXP '^SP-4[0-9]{2,}$'
) x ON x.process_id = p.process_id
SET p.process_code = x.new_code,
    p.update_time = NOW();

-- ③ 同步菲林表的工序编码快照（按 process_id 关联；两表 process_code 排序规则不同，显式统一）
UPDATE engineering_film f
JOIN engineering_standard_process s ON s.process_id = f.process_id
SET f.process_code = s.process_code
WHERE f.process_code IS NULL
   OR f.process_code <> CONVERT(s.process_code USING utf8mb4) COLLATE utf8mb4_unicode_ci;

-- ④ 核验：0 行 = 全库编码符合单规则且段位与类别一致
SELECT '核验：段位与工序类别不一致的行（应为 0 行）' AS check_point;
SELECT process_id, process_code, process_name, process_category
FROM engineering_standard_process
WHERE (process_category = 'PANEL'     AND process_code NOT REGEXP '^SP-1[0-9]{2,}$')
   OR (process_category = 'UP_LINE'   AND process_code NOT REGEXP '^SP-2[0-9]{2,}$')
   OR (process_category = 'DOWN_LINE' AND process_code NOT REGEXP '^SP-3[0-9]{2,}$')
   OR (process_category = 'OTHER'     AND process_code NOT REGEXP '^SP-4[0-9]{2,}$')
   OR (process_category IS NULL      AND process_code NOT REGEXP '^SP-[1-4][0-9]{2,}$');

-- ⑤ 段位分布核验
SELECT LEFT(process_code, 4) AS seg, COUNT(*) AS cnt
FROM engineering_standard_process
WHERE process_code LIKE 'SP-%'
GROUP BY LEFT(process_code, 4)
ORDER BY seg;
