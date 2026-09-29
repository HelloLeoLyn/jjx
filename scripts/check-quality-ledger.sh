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
         COALESCE((
           SELECT SUM(a.quantity) FROM quality_ncr n
             JOIN quality_ncr_action a ON a.ncr_id = n.ncr_id AND a.del_flag = 0
            WHERE n.lot_id = l.lot_id AND n.del_flag = 0
              AND a.status IN ('DONE', 'PROCESSING')
         ), 0) derived_disposed
    FROM quality_lot l
   WHERE l.lot_type = 'IQC'
   GROUP BY l.lot_id, l.disposed_quantity
  HAVING lot_disposed <> derived_disposed
) x;")

# dev-20260929-004 新增：① 放行类行判定字段必须为 NULL；② 采购行判定守恒；③ 隔离台账行恒等式
release_field_drift=$(M "
SELECT COUNT(*) FROM inventory_inbound_item i
JOIN inventory_inbound_order o ON o.inbound_id = i.inbound_id
WHERE (o.inbound_type IN ('IQC_RELEASE', 'IQC_REWORK') OR o.source_type IN ('IQC_RELEASE', 'IQC_REWORK'))
  AND (i.qualified_quantity IS NOT NULL OR i.rejected_quantity IS NOT NULL OR i.accepted_quantity IS NOT NULL);")

purchase_judgement_drift=$(M "
SELECT COUNT(*) FROM inventory_inbound_item i
JOIN inventory_inbound_order o ON o.inbound_id = i.inbound_id
WHERE o.source_type = 'PURCHASE' AND i.qualified_quantity IS NOT NULL
  AND COALESCE(i.qualified_quantity, 0) + COALESCE(i.rejected_quantity, 0) <> i.quantity;")

quarantine_balance_drift=$(M "
SELECT COUNT(*) FROM (
  SELECT q.quarantine_id, q.quantity, q.remaining_quantity,
         COALESCE((
           SELECT SUM(d.quantity) FROM inventory_iqc_disposition_order d
            WHERE d.quarantine_id = q.quarantine_id
              AND d.status NOT IN ('REJECTED', 'CANCELLED')
         ), 0) disposed
    FROM inventory_iqc_quarantine q
  HAVING remaining_quantity < 0 OR disposed <> quantity - remaining_quantity
) x;")

rework_lot_drift=$(M "
SELECT COUNT(*) FROM inventory_iqc_disposition_order r
LEFT JOIN quality_lot child ON child.lot_id = r.lot_id AND child.parent_lot_id IS NOT NULL
WHERE r.action = 'REWORK'
  AND r.status IN ('PENDING_REINSPECTION', 'COMPLETED')
  AND (child.lot_id IS NULL OR child.lot_quantity <> r.quantity);")

disposition_ncr_drift=$(M "
SELECT COUNT(*) FROM inventory_iqc_disposition_order d
LEFT JOIN quality_ncr_action a ON a.action_id = d.quality_action_id
WHERE d.quality_action_id IS NULL
   OR a.action_id IS NULL
   OR a.quantity <> d.quantity
   OR a.action_type <> CASE d.action
       WHEN 'RELEASE' THEN 'CONCESSION'
       WHEN 'RETURN' THEN 'RETURN'
       WHEN 'REWORK' THEN 'REWORK'
       WHEN 'SCRAP' THEN 'SCRAP'
     END;")

lot_balance_drift=$(M "
SELECT COUNT(*) FROM (
  SELECT l.lot_id,
         COALESCE(l.fail_quantity, 0) fail_quantity,
         COALESCE((
           SELECT SUM(d.quantity)
             FROM inventory_iqc_disposition_order d
            WHERE d.lot_id = l.lot_id
              AND d.status NOT IN ('REJECTED', 'CANCELLED')
         ), 0) action_disposed,
         COALESCE((
           SELECT SUM(q.remaining_quantity)
             FROM inventory_iqc_quarantine q
            WHERE q.lot_id = l.lot_id
         ), 0) quarantine_remaining
    FROM quality_lot l
   WHERE l.lot_type = 'IQC'
  HAVING fail_quantity <> action_disposed + quarantine_remaining
) x;")

echo "== 质量/IQC 跨表一致性巡检：$(date '+%F %T') =="
echo "1) IQC 批不良量 vs NCR 不良总量：$lot_ncr_drift（期望 0）"
echo "2) 检验批已处置量缓存 vs 派生(DONE+PROCESSING)：$lot_action_drift（期望 0）"
echo "3) 返工量 vs 复检子批数量：$rework_lot_drift（期望 0）"
echo "4) 不良量 vs 有效处置 + 隔离剩余：$lot_balance_drift（期望 0）"
echo "5) 统一处置事实 vs NCR 动作关联：$disposition_ncr_drift（期望 0）"
echo "6) 放行类行判定字段必须为 NULL：$release_field_drift（期望 0；迁移 229 未执行时存量行会报）"
echo "7) 采购行判定守恒(合格+不良=收货)：$purchase_judgement_drift（期望 0）"
echo "8) 隔离台账行恒等式(隔离量-已处置=剩余)：$quarantine_balance_drift（期望 0）"

if [ "$lot_balance_drift" -gt 0 ]; then
  echo "-- 4) 不良量恒等式差异明细（lot_id / fail / 有效处置 / 隔离剩余）"
  M "
  SELECT l.lot_id,
         COALESCE(l.fail_quantity, 0) AS fail_quantity,
         COALESCE((
           SELECT SUM(a.quantity)
             FROM quality_ncr n
             JOIN quality_ncr_action a ON a.ncr_id = n.ncr_id
            WHERE n.lot_id = l.lot_id
              AND n.status <> 'VOID'
              AND a.status IN ('DONE', 'PROCESSING')
         ), 0) AS action_disposed,
         COALESCE((
           SELECT SUM(q.remaining_quantity)
             FROM inventory_iqc_quarantine q
            WHERE q.lot_id = l.lot_id
         ), 0) AS quarantine_remaining
    FROM quality_lot l
   WHERE l.lot_type = 'IQC'
  HAVING fail_quantity <> action_disposed + quarantine_remaining;"
fi

stock_exit=0
if ! bash "$(dirname "$0")/check-stock-summary.sh" --strict; then
  stock_exit=1
fi

if [ "$STRICT" = true ] && { [ "$lot_ncr_drift" -gt 0 ] || [ "$lot_action_drift" -gt 0 ] || [ "$rework_lot_drift" -gt 0 ] || [ "$lot_balance_drift" -gt 0 ] || [ "$disposition_ncr_drift" -gt 0 ] || [ "$release_field_drift" -gt 0 ] || [ "$purchase_judgement_drift" -gt 0 ] || [ "$quarantine_balance_drift" -gt 0 ] || [ "$stock_exit" -ne 0 ]; }; then
  exit 1
fi
exit 0
