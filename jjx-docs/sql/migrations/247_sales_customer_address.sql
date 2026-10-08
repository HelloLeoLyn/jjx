-- dev-20261008-029
-- risk: low（仅新建表，不改/删既有数据）
-- 内容：新建「客户收货地址簿」sales_customer_address（一个客户多个收货地址，含默认标记）。
-- 幂等：建表前查 information_schema，已存在则跳过。
-- 执行：bash scripts/db-migrate.sh 247_sales_customer_address.sql --yes --task dev-20261008-029
-- 背景：销售订单/发货管理的收货地址此前只有「订单地址 + 客户档案单地址」两个来源，
--       无法维护一个客户的多个收货地址；本表作为客户维度的地址簿，供下单/发货选择与默认。

SET @jjx_has_sca = (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'sales_customer_address'
);
SET @jjx_ddl_sca = IF(@jjx_has_sca = 0,
'CREATE TABLE sales_customer_address (
  address_id BIGINT NOT NULL AUTO_INCREMENT COMMENT ''地址ID'',
  customer_id BIGINT NOT NULL COMMENT ''客户ID（sales_customer.customer_id）'',
  label VARCHAR(50) DEFAULT NULL COMMENT ''地址标签，如 上海总部/东莞仓'',
  contact_person VARCHAR(50) DEFAULT NULL COMMENT ''收货联系人'',
  contact_phone VARCHAR(50) DEFAULT NULL COMMENT ''收货联系电话'',
  country VARCHAR(50) DEFAULT NULL COMMENT ''国家/地区'',
  province VARCHAR(50) DEFAULT NULL COMMENT ''省份/州'',
  city VARCHAR(50) DEFAULT NULL COMMENT ''城市'',
  address VARCHAR(255) DEFAULT NULL COMMENT ''详细地址'',
  postal_code VARCHAR(20) DEFAULT NULL COMMENT ''邮政编码'',
  is_default TINYINT NOT NULL DEFAULT 0 COMMENT ''是否客户默认收货地址 0否 1是'',
  remark VARCHAR(255) DEFAULT NULL COMMENT ''备注'',
  create_by VARCHAR(64) DEFAULT NULL COMMENT ''创建者'',
  create_time DATETIME DEFAULT NULL COMMENT ''创建时间'',
  update_by VARCHAR(64) DEFAULT NULL COMMENT ''更新者'',
  update_time DATETIME DEFAULT NULL COMMENT ''更新时间'',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除 0否 1是'',
  PRIMARY KEY (address_id),
  KEY idx_sca_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''客户收货地址簿''',
'SELECT 1');
PREPARE jjx_ddl_sca FROM @jjx_ddl_sca;
EXECUTE jjx_ddl_sca;
DEALLOCATE PREPARE jjx_ddl_sca;
