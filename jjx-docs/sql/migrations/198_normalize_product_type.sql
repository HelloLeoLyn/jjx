-- ============================================================================
-- 198_normalize_product_type.sql
-- 任务：dev-20260922-008（用户 2026-09-22 15:14 选 A：口径统一为 1=成品 / 2=样品）
--
-- 问题（用户 15:08 报「编辑产品 产品类型为什么是 standard」）：
--   `product.product_type` 列默认值 = 字符串 'standard'；前端 ProductTypeEnum 只认 1/2，
--   于是英文值匹配不到枚举 → el-select/详情/审批弹窗显示裸值（standard/custom 或空）。
-- 口径依据：dev-20260911-008（提交 3f3c4333）只把 1/2 的**文案**由「标准产品/定制产品」
--   改为「成品/样品」→ 因此 standard ≡ 1、custom ≡ 2。
--
-- 处理：
--   ① 历史英文值归一：standard→1、custom→2（大小写/空格容错）
--   ② 断根：列默认值 'standard' → '1'
--   ③ 空值兜底：NULL/'' → '1'（当前 0 行）
-- 幂等：UPDATE 均带条件；ALTER ... SET DEFAULT 可重复执行。
-- 前置 guard 备份：~/jjx-backups/sys_task_product_type_before_20260922-1516.sql
--   （md5 92aaf2477395fbc8924721505863db0c，含 product 全量数据 + sys_task）
-- ============================================================================
USE `jjx_erp_db`;

-- ① 英文值 → 枚举编码
UPDATE product SET product_type = '1', update_time = NOW()
 WHERE LOWER(TRIM(IFNULL(product_type, ''))) IN ('standard', 'std', '标准', '标准产品');
UPDATE product SET product_type = '2', update_time = NOW()
 WHERE LOWER(TRIM(IFNULL(product_type, ''))) IN ('custom', '定制', '定制产品');

-- ③ 空值兜底
UPDATE product SET product_type = '1', update_time = NOW()
 WHERE product_type IS NULL OR TRIM(product_type) = '';

-- ② 断根：列默认值 'standard' → '1'
ALTER TABLE product ALTER COLUMN product_type SET DEFAULT '1';

-- ── 核验 ──────────────────────────────────────────────────────────────────
SELECT column_default AS product_type_default
  FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'product' AND column_name = 'product_type';
SELECT product_type, COUNT(*) AS rows_cnt FROM product GROUP BY product_type;
SELECT COUNT(*) AS not_1_or_2_should_be_0 FROM product WHERE product_type NOT IN ('1', '2');
