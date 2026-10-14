-- 282_attachment_upload_state_comment_purged.sql
-- dev-20261014-033（2026-10-14）：047 §9 留痕式释放（PURGED）收尾——补列注释
--   sys_attachment.upload_state 列注释原为 'LEGACY/TEMP/BOUND/ORPHAN'，漏了 0871ca75 引入的 PURGED。
--   仅改列注释（COMMENT），不改类型/可空/默认值/字符集；PURGED 值本身早已可直接写入（无 CHECK/ENUM 约束），
--   本迁移是纯粹的文档性订正，对数据 0 影响。
-- 幂等：MODIFY COLUMN 可重复执行，重复执行结果一致（0 数据影响）。
-- 回滚：ALTER TABLE sys_attachment
--         MODIFY COLUMN upload_state varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LEGACY'
--         COMMENT 'LEGACY/TEMP/BOUND/ORPHAN';

ALTER TABLE sys_attachment
  MODIFY COLUMN upload_state varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LEGACY'
  COMMENT 'LEGACY/TEMP/BOUND/ORPHAN/PURGED';
