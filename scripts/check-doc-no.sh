#!/usr/bin/env bash
# dev-20260922-023：业务单号规则巡检（只读，不修改数据库）。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"
DB_NAME="${DB_NAME:-jjx_erp_db}"
export MYSQL_PWD="$DB_PASS"
MYSQL=(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 -N -B "$DB_NAME")

required=(sales_order sample_order sales_delivery sales_return inquiry quotation purchase_order \
  production_plan production_order work_report quality_lot quality_ncr quality_capa \
  iqc_disposition iqc_return iqc_rework iqc_scrap stocktake stock_gain stock_loss)

failed=0
for biz_type in "${required[@]}"; do
  count="$("${MYSQL[@]}" -e "SELECT COUNT(*) FROM sys_config WHERE is_active=1 AND config_key='biz_no_rule.${biz_type}'")"
  if [[ "$count" != "1" ]]; then
    echo "MISSING biz_no_rule.${biz_type}"
    failed=1
  fi
done

echo "Capacity usage >= 80% (empty means healthy):"
"${MYSQL[@]}" -e "
SELECT s.sequence_key, s.period_key, s.current_value,
       CAST(JSON_UNQUOTE(JSON_EXTRACT(c.config_value, '$.digits')) AS UNSIGNED) AS configured_digits
FROM sys_number_sequence s
JOIN sys_config c ON c.config_key = CONCAT('biz_no_rule.', s.sequence_key)
WHERE s.current_value >= 0.8 * (POW(10, CAST(JSON_UNQUOTE(JSON_EXTRACT(c.config_value, '$.digits')) AS UNSIGNED)) - 1)
ORDER BY s.current_value DESC"

if rg -n 'yyyyMMddHHmmssSSS|CAPA.*currentTimeMillis' \
    "$REPO_ROOT/jjx-server/src/main/java/com/jjx/quality" \
    "$REPO_ROOT/jjx-server/src/main/java/com/jjx/inventory"; then
  echo "FAIL: found timestamp-based business number generation"
  failed=1
fi

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi
echo "Document number rule check passed."
