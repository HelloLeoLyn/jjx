#!/usr/bin/env bash
# ============================================================================
# check-inbound-lot-integrity.sh —— 入库单/检验批维度巡检（只读）
#   任务：dev-20260923-010（起因：022「一验批一单 + 红冲」落地后，
#         check-stock-summary.sh 只覆盖「汇总表 vs 批次明细 vs 流水」，
#         缺入库单与检验批维度的校验）
#
# 与 check-stock-summary.sh 同口径（只读 / fail-open / --strict），补三查：
#   ① 入库明细 lot_id 覆盖率：生产来源、未取消单据的明细里 lot_id 为空的数量（期望 0）
#   ② 同一 lot 被多张单重复计账：按 lot_id 汇总「未取消单据明细数量」应 ≤ 该批 pass_quantity
#      （红冲单数量为负、作废单 order_status=9 排除，故正常换代后净额为 0）
#   ③ 已过账生产入库明细的 posted_quantity 合计（按 inventory_item_id + batch_no）
#      应等于该批「入库侧流水」合计（INBOUND + ADJUST；红冲走 ADJUST 负额）
#      —— 出库方向（OUTBOUND/TRANSFER_OUT）不计，故成品出库不会误报；
#         若该批另有盘点/其它 ADJUST，需人工核对（脚本会列出明细行）。
#
# 用法：
#   bash scripts/check-inbound-lot-integrity.sh            # 咨询模式：只报告，永远 exit 0
#   bash scripts/check-inbound-lot-integrity.sh --strict    # 有不一致则 exit 1
# 环境变量（与 check-stock-summary.sh / db-backup.sh 同口径）：
#   DB_HOST DB_PORT DB_USER DB_PASS DB_NAME
#   JJX_LOTID_CHECK_SINCE （可选，格式 YYYY-MM-DD）只巡检该时间之后创建的入库单；
#                          不设 = 全量（存量历史数据可用它设基线）
# 只读：只跑 SELECT，不改任何数据。
# ============================================================================
set -uo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"
DB_NAME="${DB_NAME:-jjx_erp_db}"
SINCE="${JJX_LOTID_CHECK_SINCE:-}"

STRICT=false
for arg in "$@"; do
  case "$arg" in
    --strict) STRICT=true ;;
    -h|--help) sed -n '2,27p' "$0"; exit 0 ;;
    *) echo "未知参数：$arg（支持 --strict）"; exit 2 ;;
  esac
done

command -v mysql >/dev/null 2>&1 || { echo "找不到 mysql 客户端（apt install mysql-client）"; exit 0; }
export MYSQL_PWD="$DB_PASS"
if ! mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --connect-timeout=5 -N -B "$DB_NAME" -e "SELECT 1" >/dev/null 2>&1; then
  echo "连不上库 ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME} —— 跳过入库/检验批巡检（不阻塞）"
  exit 0
fi

M() { mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 -N -B "$DB_NAME" -e "$1"; }

# 可选时间基线（只影响 ①；② ③ 按批聚合，不设时间窗）
SINCE_FILTER=""
if [ -n "$SINCE" ]; then
  SINCE_FILTER=" AND o.create_time >= '${SINCE} 00:00:00'"
fi

echo "== 入库单/检验批巡检：$(date '+%F %T')  入库明细 lot_id  →  同 lot 重复计账  →  已过账 vs 入库侧流水 =="
[ -n "$SINCE" ] && echo "   ① 只巡检 ${SINCE} 之后创建的入库单（JJX_LOTID_CHECK_SINCE）"

# ① 生产来源、未取消单据的明细 lot_id 覆盖率
missing_lot=$(M "
SELECT COUNT(*)
  FROM inventory_inbound_item ii
  JOIN inventory_inbound_order o ON o.inbound_id = ii.inbound_id
 WHERE o.order_status <> 9
   AND UPPER(IFNULL(o.source_type,'')) = 'PRODUCTION'
   AND ii.lot_id IS NULL${SINCE_FILTER};" 2>/dev/null || echo "ERR")

# ② 同一 lot 被重复计账（未取消单据明细数量合计 > 该批合格量）
double_lot=$(M "
SELECT COUNT(*) FROM (
  SELECT ii.lot_id
    FROM inventory_inbound_item ii
    JOIN inventory_inbound_order o ON o.inbound_id = ii.inbound_id
    JOIN quality_lot l ON l.lot_id = ii.lot_id
   WHERE o.order_status <> 9
     AND ii.lot_id IS NOT NULL
   GROUP BY ii.lot_id, l.pass_quantity
  HAVING SUM(ii.quantity) > l.pass_quantity
) d;" 2>/dev/null || echo "ERR")

# ③ 已过账明细 posted_quantity 合计 vs 入库侧流水合计
posted_drift=$(M "
SELECT COUNT(*) FROM (
  SELECT ii.inventory_item_id, ii.batch_no,
         SUM(ii.posted_quantity) AS posted_sum,
         IFNULL((SELECT SUM(t.quantity) FROM inventory_transaction t
                  WHERE t.inventory_item_id = ii.inventory_item_id
                    AND t.batch_no = ii.batch_no
                    AND t.transaction_type IN ('INBOUND', 'ADJUST')), 0) AS flow_in
    FROM inventory_inbound_item ii
    JOIN inventory_inbound_order o ON o.inbound_id = ii.inbound_id
   WHERE o.order_status <> 9
     AND UPPER(IFNULL(o.source_type,'')) = 'PRODUCTION'
   GROUP BY ii.inventory_item_id, ii.batch_no
) d
WHERE d.posted_sum <> d.flow_in;" 2>/dev/null || echo "ERR")

if [ "$missing_lot" = "ERR" ] || [ "$double_lot" = "ERR" ] || [ "$posted_drift" = "ERR" ]; then
  echo "巡检 SQL 执行失败（表结构变动？）—— 不阻塞"
  exit 0
fi

echo
echo "-- ① 生产来源入库明细 lot_id 为空：$missing_lot（期望 0）"
[ "$missing_lot" -gt 0 ] && M "
  SELECT o.inbound_no, o.order_status, ii.item_id, ii.material_code, ii.batch_no, ii.quantity
    FROM inventory_inbound_item ii
    JOIN inventory_inbound_order o ON o.inbound_id = ii.inbound_id
   WHERE o.order_status <> 9
     AND UPPER(IFNULL(o.source_type,'')) = 'PRODUCTION'
     AND ii.lot_id IS NULL${SINCE_FILTER}
   ORDER BY o.inbound_id DESC, ii.item_id;"

echo
echo "-- ② 同一 lot 被多张单重复计账（明细合计 > 批合格量）：$double_lot（期望 0）"
[ "$double_lot" -gt 0 ] && M "
  SELECT ii.lot_id, l.lot_no, SUM(ii.quantity) AS 单据明细合计, l.pass_quantity AS 批合格量,
         SUM(ii.quantity) - l.pass_quantity AS 超出
    FROM inventory_inbound_item ii
    JOIN inventory_inbound_order o ON o.inbound_id = ii.inbound_id
    JOIN quality_lot l ON l.lot_id = ii.lot_id
   WHERE o.order_status <> 9
     AND ii.lot_id IS NOT NULL
   GROUP BY ii.lot_id, l.lot_no, l.pass_quantity
  HAVING SUM(ii.quantity) > l.pass_quantity;"

echo
echo "-- ③ 已过账明细 ≠ 入库侧流水（按 inventory_item_id + batch_no）：$posted_drift（期望 0）"
[ "$posted_drift" -gt 0 ] && M "
  SELECT d.inventory_item_id, d.batch_no, d.posted_sum AS 明细已过账合计, d.flow_in AS 入库侧流水
    FROM (
      SELECT ii.inventory_item_id, ii.batch_no,
             SUM(ii.posted_quantity) AS posted_sum,
             IFNULL((SELECT SUM(t.quantity) FROM inventory_transaction t
                      WHERE t.inventory_item_id = ii.inventory_item_id
                        AND t.batch_no = ii.batch_no
                        AND t.transaction_type IN ('INBOUND', 'ADJUST')), 0) AS flow_in
        FROM inventory_inbound_item ii
        JOIN inventory_inbound_order o ON o.inbound_id = ii.inbound_id
       WHERE o.order_status <> 9
         AND UPPER(IFNULL(o.source_type,'')) = 'PRODUCTION'
       GROUP BY ii.inventory_item_id, ii.batch_no
    ) d
   WHERE d.posted_sum <> d.flow_in;"

total=$((missing_lot + double_lot + posted_drift))
echo
if [ "$total" -eq 0 ]; then
  echo "   ✅ 入库明细/检验批/流水一致（lot_id 齐全、无重复计账、已过账量 = 入库侧流水）"
else
  echo "   ❌ 发现 $total 处不一致"
  echo "      排查方向：① lot_id 缺失 = 入库明细未挂检验批（022 口径要求明细挂 lot_id）"
  echo "                ② 重复计账 = 同一批被多张未取消单据收两次（红冲单未开或未过账）"
  echo "                ③ 已过账量 ≠ 入库侧流水 = 过账时漏写/多写流水，或该批另有盘点调整（人工核对）"
  echo "      口径参考：jjx-docs/standards/CONVENTIONS.md §13（流水是唯一真源）"
fi

if [ "$STRICT" = true ] && [ "$total" -gt 0 ]; then
  echo
  echo "✘ --strict：入库单/检验批维度巡检不一致（$total 处）"
  exit 1
fi
echo
echo "== 巡检完成：咨询模式，退出码 0（加 --strict 可当门禁）=="
exit 0
