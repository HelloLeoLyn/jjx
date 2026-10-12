-- dev-20261012-002: V1-A 运行时 P0 修复——TEMP 上传因 biz_type/biz_id NOT NULL 无默认值而失败。
-- 只放开可空性：不改任何数据、不改索引、不改历史行（历史行原 biz_type/biz_id 值原样保留）。
-- 背景：AttachmentLifecycleService.upload() 写 TEMP 行时不带业务来源（正式关联统一走 sys_attachment_ref），
--       而两列 NOT NULL 且无默认值（sql_mode 含 STRICT_TRANS_TABLES）→ 每次临时上传都报
--       "Field 'biz_type' doesn't have a default value"；事务回滚但磁盘文件已落盘，产生孤立文件。
-- 幂等：重复执行即 MODIFY 为同一目标定义，不改数据、无副作用。
ALTER TABLE sys_attachment
  MODIFY COLUMN biz_type varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL
    COMMENT '业务类型（如: sales_order, product等）；TEMP/未绑定引用时为空',
  MODIFY COLUMN biz_id bigint NULL
    COMMENT '业务记录ID；TEMP/未绑定引用时为空';
