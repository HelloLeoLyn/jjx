#!/usr/bin/env bash
# Read-only guard for IQC source rows and child reinspection lots.
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
    -h|--help) echo "Usage: bash scripts/check-iqc-lineage.sh [--strict]"; exit 0 ;;
    *) echo "Unknown argument: $arg"; exit 2 ;;
  esac
done

command -v mysql >/dev/null 2>&1 || { echo "mysql client not found"; exit 1; }
export MYSQL_PWD="$DB_PASS"
M() { mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 -N -B "$DB_NAME" -e "$1"; }
if ! M "SELECT 1" >/dev/null; then
  echo "Cannot connect to ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}" >&2
  exit 1
fi

# An inbound item owns the original IQC lot and ORIGINAL batch. Reinspection
# children are referenced by inventory_iqc_rework_order, never by the source row.
source_drift=$(M "
SELECT COUNT(*) FROM inventory_inbound_item ii
LEFT JOIN quality_lot original_lot
  ON original_lot.source_item_id = ii.item_id
 AND original_lot.lot_type = 'IQC' AND original_lot.parent_lot_id IS NULL
LEFT JOIN inventory_iqc_batch original_batch
  ON original_batch.source_inbound_item_id = ii.item_id
 AND original_batch.batch_type = 'ORIGINAL'
WHERE (EXISTS (SELECT 1 FROM inventory_iqc_rework_order r WHERE r.inbound_item_id = ii.item_id)
    OR EXISTS (SELECT 1 FROM quality_lot child_lot
                WHERE child_lot.source_item_id = ii.item_id
                  AND child_lot.lot_type = 'IQC' AND child_lot.parent_lot_id IS NOT NULL)
    OR EXISTS (SELECT 1 FROM inventory_iqc_batch child_batch
                WHERE child_batch.source_inbound_item_id = ii.item_id
                  AND child_batch.parent_batch_id IS NOT NULL))
  AND (original_lot.lot_id IS NULL OR original_batch.batch_id IS NULL
    OR NOT (ii.lot_id <=> original_lot.lot_id)
    OR NOT (ii.batch_no <=> original_batch.batch_no)
    OR NOT (ii.iqc_batch_id <=> original_batch.batch_id));")

# Batch judgement quantities must agree with the source quality lot. Pending
# child lots carry their own batch number and zero processed quantity.
batch_drift=$(M "
SELECT COUNT(*) FROM inventory_iqc_batch b
JOIN quality_lot l ON l.batch_no = b.batch_no AND l.lot_type = 'IQC'
WHERE (b.processed_quantity <> b.accepted_quantity + b.rejected_quantity
    OR b.remaining_quantity <> GREATEST(0, b.quantity - b.processed_quantity)
    OR b.processed_quantity <> l.inspected_quantity
    OR b.accepted_quantity <> l.pass_quantity
    OR b.rejected_quantity <> l.fail_quantity);")

echo "IQC source lineage mismatches: $source_drift (expected 0)"
echo "IQC batch judgement mismatches: $batch_drift (expected 0)"
total=$((source_drift + batch_drift))
if [ "$total" -gt 0 ] && [ "$STRICT" = true ]; then
  exit 1
fi
exit 0
