-- dev-20261018-030；用户已批准新增独立客户联系人表。
-- 前置：已有用户手工全库备份（排除hr_employee）；仅通过db-migrate.sh执行。
-- DDL失败不自动重跑；先核实账本/结构，再按入口的record/retry流程处理。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sales_customer_contact (
    contact_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '联系人ID',
    customer_id BIGINT NOT NULL COMMENT '归属客户',
    contact_person VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '姓名',
    contact_phone VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '电话',
    remark VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    create_by VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    active_key TINYINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN 1 ELSE NULL END) VIRTUAL,
    PRIMARY KEY (contact_id),
    KEY idx_customer_active (customer_id, deleted),
    UNIQUE KEY uk_customer_contact_active (customer_id, contact_person, contact_phone, active_key)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='客户联系人通讯录';

SET @ddl_contact = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
           AND TABLE_NAME='sales_customer_address' AND COLUMN_NAME='contact_id'),
    'SELECT 1', 'ALTER TABLE sales_customer_address ADD COLUMN contact_id BIGINT NULL COMMENT ''客户联系人ID，姓名和电话为选用时快照''');
PREPARE stmt_contact FROM @ddl_contact;
EXECUTE stmt_contact;
DEALLOCATE PREPARE stmt_contact;

SET @ddl_contact_index = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
           AND TABLE_NAME='sales_customer_address' AND INDEX_NAME='idx_customer_contact'),
    'SELECT 1', 'ALTER TABLE sales_customer_address ADD INDEX idx_customer_contact (customer_id, contact_id, deleted)');
PREPARE stmt_contact FROM @ddl_contact_index;
EXECUTE stmt_contact;
DEALLOCATE PREPARE stmt_contact;

START TRANSACTION;
-- 同一客户+姓名+电话去重；电话空值统一为空串，不用电话号码推断身份。
INSERT INTO sales_customer_contact (customer_id, contact_person, contact_phone, create_by)
SELECT s.customer_id, s.contact_person, s.contact_phone, 'Codex'
  FROM (
      SELECT customer_id, TRIM(contact_person) AS contact_person, TRIM(COALESCE(contact_phone,'')) AS contact_phone
        FROM sales_customer WHERE deleted=0 AND TRIM(COALESCE(contact_person,''))<>''
      UNION
      SELECT a.customer_id, TRIM(a.contact_person), TRIM(COALESCE(a.contact_phone,''))
        FROM sales_customer_address a JOIN sales_customer c ON c.customer_id=a.customer_id AND c.deleted=0
       WHERE a.deleted=0 AND TRIM(COALESCE(a.contact_person,''))<>''
  ) s
 WHERE NOT EXISTS (
     SELECT 1 FROM sales_customer_contact t WHERE t.customer_id=s.customer_id AND t.deleted=0
        AND t.contact_person=s.contact_person AND t.contact_phone=s.contact_phone
 );

-- 只补可确定的引用，不改姓名、电话、地址及默认项。
UPDATE sales_customer_address a
  JOIN sales_customer c ON c.customer_id=a.customer_id AND c.deleted=0
  JOIN sales_customer_contact t ON t.customer_id=a.customer_id AND t.deleted=0
       AND t.contact_person=TRIM(a.contact_person) AND t.contact_phone=TRIM(COALESCE(a.contact_phone,''))
   SET a.contact_id=t.contact_id, a.update_by='Codex', a.update_time=NOW()
 WHERE a.deleted=0 AND a.contact_id IS NULL AND TRIM(COALESCE(a.contact_person,''))<>'';
COMMIT;
