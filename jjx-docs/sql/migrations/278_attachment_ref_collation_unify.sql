-- ============================================================================
-- 278: 附件引用表 collation 订正 —— sys_attachment_ref / order_shortage_ledger
--      转回 utf8mb4_unicode_ci（修 ERROR 1267 Illegal mix of collations）
-- 任务码：dev-20261014-006    日期：2026-10-14
--
-- 背景：迁移 271 建 sys_attachment_ref 时只写 DEFAULT CHARSET=utf8mb4、未写 COLLATE，
--       MySQL 8 取"字符集自己的默认排序规则" utf8mb4_0900_ai_ci（≠ 库默认
--       utf8mb4_unicode_ci）；迁移 276 加的 product_code 是显式 utf8mb4_unicode_ci，
--       于是同表混排两种 collation。
--       业务触发：sys_dict_item.item_key(utf8mb4_unicode_ci) =
--                 sys_attachment_ref.document_type(utf8mb4_0900_ai_ci) → ERROR 1267
--       （ProductCustomerDocumentService 产品客供资料字典 join）。
--       order_shortage_ledger 同为"建表漏写 COLLATE"的漂移表，一并订正。
--
-- 范围：只订正这两张表的字符串列与表默认排序规则；不改数据结构/表语义/数据。
-- 关键：sys_attachment_ref.source_type 是刻意 ascii_bin（且在唯一索引
--       uk_attachment_ref_active 内），故必须【按列 MODIFY】而非整表 CONVERT TO
--       （后者会冲掉 ascii_bin 并改索引键 collation → 撞键风险）。
--
-- 执行前：bash scripts/check-collation.sh --preflight   # 串列外键 / 唯一索引撞键（期望 0）
-- 执行：  bash scripts/db-migrate.sh 278_attachment_ref_collation_unify.sql \
--           --yes --task dev-20261014-006 --backup <手工全库备份.sql>
-- 收尾：  bash scripts/check-collation.sh --strict        # 期望 0 漂移、exit 0
-- ============================================================================

ALTER TABLE sys_attachment_ref
  MODIFY source_no     varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  MODIFY document_type varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  MODIFY display_name  varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  MODIFY remark        varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  MODIFY create_by     varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  MODIFY update_by     varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  MODIFY deleted_by    varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- order_shortage_ledger：无字符串外键、唯一索引在数值列（order_id,material_id）上 → 可整表 CONVERT
ALTER TABLE order_shortage_ledger
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
