-- risk: low
-- task: dev-20260923-037
-- 建立事件变量累积表，收敛二期事件变量键集合漂移。
-- 幂等：重复执行安全
-- 注意：COLLATE 必须显式写 $SCHEMATA 默认（utf8mb4_unicode_ci）——2026-09-28 首次执行时漏写，
--   落成服务器默认 utf8mb4_0900_ai_ci（全库唯一），已用 ALTER 校正；本文件同步补上，重放不再踩。

SET @sql := (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.tables
          WHERE table_schema = DATABASE()
            AND table_name = 'sys_event_var'),
  'SELECT 1',
  'CREATE TABLE sys_event_var (
    id bigint AUTO_INCREMENT PRIMARY KEY,
    event_code varchar(100) NOT NULL,
    var_key varchar(100) NOT NULL,
    description varchar(255) NULL,
    example varchar(255) NULL,
    source varchar(20) NOT NULL DEFAULT ''collected'',
    last_seen_at datetime NULL,
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_event_var (event_code, var_key),
    KEY idx_var_key (var_key)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''事件变量累积表（二期：收敛键集合漂移；source=collected/manual）'''
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 兜底校正：若历史执行已落成其它 collation，重放本文件时纠正
SET @fix := (SELECT IF(
  EXISTS (SELECT 1 FROM information_schema.tables
          WHERE table_schema = DATABASE()
            AND table_name = 'sys_event_var'
            AND table_collation <> 'utf8mb4_unicode_ci'),
  'ALTER TABLE sys_event_var CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci',
  'SELECT 1'
));
PREPARE stmt2 FROM @fix; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
