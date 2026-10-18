-- dev-20261018-030：从客户信息补齐公司地址，只插入、不修改已有地址和默认项。
-- 前置：手工全库备份（排除 hr_employee），仅通过 scripts/db-migrate.sh 执行。
-- 兼容旧纯文本及 InternationalAddress JSON；无详细地址不建，重复执行不重复插入。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
START TRANSACTION;

INSERT INTO sales_customer_address
    (customer_id, label, contact_person, contact_phone, country, province, city,
     address, postal_code, is_default, create_by, create_time, deleted)
WITH source AS (
    SELECT c.*,
           CASE WHEN JSON_VALID(c.address) AND LEFT(TRIM(c.address), 1) = '{'
                THEN c.address ELSE '{}' END AS address_json,
           CASE WHEN JSON_VALID(c.address) AND LEFT(TRIM(c.address), 1) = '{'
                THEN 1 ELSE 0 END AS structured
      FROM sales_customer c WHERE c.deleted = 0
), normalized AS (
    SELECT customer_id, contact_person, contact_phone,
           TRIM(COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(address_json, '$.country')), 'null'), country, '')) AS country,
           TRIM(COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(address_json, '$.province')), 'null'), province, '')) AS province,
           TRIM(COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(address_json, '$.city')), 'null'), city, '')) AS city,
           TRIM(CASE WHEN structured = 1 THEN COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(address_json, '$.street')), 'null'), '')
                     ELSE COALESCE(address, '') END) AS street,
           TRIM(COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(address_json, '$.zipCode')), 'null'), postal_code, '')) AS postal_code
      FROM source
)
SELECT c.customer_id, '公司地址', c.contact_person, c.contact_phone, c.country, c.province, c.city,
       c.street, c.postal_code,
       CASE WHEN EXISTS (SELECT 1 FROM sales_customer_address d WHERE d.customer_id = c.customer_id AND d.deleted = 0)
            THEN 0 ELSE 1 END,
       'Codex', NOW(), 0
  FROM normalized c
 WHERE c.street <> ''
   AND NOT EXISTS (
       SELECT 1 FROM sales_customer_address a
        WHERE a.customer_id = c.customer_id AND a.deleted = 0
          AND (a.label = '公司地址' OR (
              TRIM(COALESCE(a.address, '')) = c.street
              AND TRIM(COALESCE(a.country, '')) = c.country
              AND TRIM(COALESCE(a.province, '')) = c.province
              AND TRIM(COALESCE(a.city, '')) = c.city
              AND TRIM(COALESCE(a.postal_code, '')) = c.postal_code))
   );

COMMIT;
