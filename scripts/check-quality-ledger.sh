#!/usr/bin/env bash
# IQC/质量处置跨表一致性巡检（只读）
# 危险等级：🟢 只读；只执行 SELECT，不自动修复数据。
set -uo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"
DB_NAME="${DB_NAME:-jjx_erp_db}"
STRICT=false
for arg in "$@"; do
  case "$arg" in
    --strict) STRICT=true ;;
    -h|--help) sed -n '1,6p' "$0"; echo "用法：bash scripts/check-quality-ledger.sh [--strict]"; exit 0 ;;
    *) echo "未知参数：$arg（支持 --strict）" >&2; exit 2 ;;
  esac
done

command -v mysql >/dev/null 2>&1 || { echo "找不到 mysql 客户端" >&2; exit 1; }
export MYSQL_PWD="$DB_PASS"
M() { mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --connect-timeout=5 --default-character-set=utf8mb4 -N -B "$DB_NAME" -e "$1"; }
if ! M "SELECT 1" >/dev/null; then
  echo "无法连接 ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}，跳过质量账对账" >&2
  exit 0
fi

lot_ncr_drift=$(M "
SELECT COUNT(*) FROM (
  SELECT l.lot_id,
         l.fail_quantity,
         COALESCE(SUM(CASE WHEN n.status <> 'VOID' THEN n.defect_quantity ELSE 0 END), 0) ncr_fail
    FROM quality_lot l
    LEFT JOIN quality_ncr n ON n.lot_id = l.lot_id
   WHERE l.lot_type = 'IQC'
   GROUP BY l.lot_id, l.fail_quantity
  HAVING COALESCE(l.fail_quantity, 0) <> ncr_fail
) x;")

lot_action_drift=$(M "
SELECT COUNT(*) FROM (
  SELECT l.lot_id,
         COALESCE(l.disposed_quantity, 0) lot_disposed,
         COALESCE(SUM(CASE WHEN a.status IN ('DONE', 'PROCESSING') THEN a.quantity ELSE 0 END), 0) action_disposed
    FROM quality_lot l
    LEFT JOIN quality_ncr n ON n.lot_id = l.lot_id AND n.status <> 'VOID'
    LEFT JOIN quality_ncr_action a ON a.ncr_id = n.ncr_id
   WHERE l.lot_type = 'IQC'
   GROUP BY l.lot_id, l.disposed_quantity
  HAVING lot_disposed <> action_disposed
) x;")

rework_lot_drift=$(M "
SELECT COUNT(*) FROM inventory_iqc_rework_order r
LEFT JOIN quality_lot child ON child.lot_id = r.lot_id AND child.parent_lot_id IS NOT NULL
WHERE r.status IN ('PENDING_REINSPECTION', 'COMPLETED')
  AND (child.lot_id IS NULL OR child.lot_quantity <> r.quantity);")

echo "== 质量/IQC 跨表一致性巡检：$(date '+%F %T') =="
echo "1) IQC 批不良量 vs NCR 不良总量：$lot_ncr_drift（期望 0）"
echo "2) 检验批已处置量 vs 有效 NCR 处置动作：$lot_action_drift（期望 0）"
echo "3) 返工量 vs 复检子批数量：$rework_lot_drift（期望 0）"

stock_exit=0
if ! bash "$(dirname "$0")/check-stock-summary.sh" --strict; then
  stock_exit=1
fi

if [ "$STRICT" = true ] && { [ "$lot_ncr_drift" -gt 0 ] || [ "$lot_action_drift" -gt 0 ] || [ "$rework_lot_drift" -gt 0 ] || [ "$stock_exit" -ne 0 ]; }; then
  exit 1
fi
exit 0
