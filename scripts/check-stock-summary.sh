#!/usr/bin/env bash
# ============================================================================
# check-stock-summary.sh —— 库存两本账对账（只读）
#   任务：dev-20260922-019（起因：dev-20260922 的 WO-PL2609220001-01 汇总表漏刷 50）
#
# 背景：成品/物料库存记在两处 ——
#   · inventory_stock_item：按批次的明细账（真源）
#   · inventory_stock     ：按物料的汇总账（页面/发货/预留/预警都读它）
#   系统里「改库存」有多个入口，每个都必须「改明细 + 重算汇总 + 写流水」。少一步就出现
#   「明细 98 / 汇总 48」这种对不上（本脚本就是抓这类）。
#
# 用法：
#   bash scripts/check-stock-summary.sh            # 咨询模式：只报告，永远 exit 0
#   bash scripts/check-stock-summary.sh --strict    # 有漂移则 exit 1（已接进 npm run validate）
# 环境变量（与 db-backup.sh/db-migrate.sh 同口径）：DB_HOST DB_PORT DB_USER DB_PASS DB_NAME
# 只读：只跑 SELECT，不改任何数据。
# ============================================================================
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
    -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
    *) echo "未知参数：$arg（支持 --strict）"; exit 2 ;;
  esac
done

command -v mysql >/dev/null 2>&1 || { echo "找不到 mysql 客户端（apt install mysql-client）"; exit 0; }
export MYSQL_PWD="$DB_PASS"
if ! mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --connect-timeout=5 -N -B "$DB_NAME" -e "SELECT 1" >/dev/null 2>&1; then
  echo "连不上库 ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME} —— 跳过库存对账（不阻塞）"
  exit 0
fi

M() { mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 -N -B "$DB_NAME" -e "$1"; }

echo "== 库存对账：$(date '+%F %T')  汇总表 inventory_stock  vs  批次表 inventory_stock_item =="

drift_rows=$(M "
SELECT COUNT(*) FROM (
  SELECT s.stock_id
    FROM inventory_stock s
    LEFT JOIN inventory_stock_item i
      ON i.inventory_item_id = s.inventory_item_id AND i.status = 1
   GROUP BY s.stock_id, s.total_quantity, s.total_reserved
  HAVING s.total_quantity <> IFNULL(SUM(i.quantity), 0)
      OR s.total_reserved <> IFNULL(SUM(i.reserved_quantity), 0)
) d;" 2>/dev/null || echo "ERR")

orphan_detail=$(M "
SELECT COUNT(*) FROM inventory_stock_item i
 WHERE i.status = 1 AND i.quantity <> 0
   AND NOT EXISTS (SELECT 1 FROM inventory_stock s WHERE s.inventory_item_id = i.inventory_item_id);" 2>/dev/null || echo "ERR")

orphan_summary=$(M "
SELECT COUNT(*) FROM inventory_stock s
 WHERE NOT EXISTS (SELECT 1 FROM inventory_stock_item i WHERE i.inventory_item_id = s.inventory_item_id AND i.status = 1);" 2>/dev/null || echo "ERR")

if [ "$drift_rows" = "ERR" ] || [ "$orphan_detail" = "ERR" ] || [ "$orphan_summary" = "ERR" ]; then
  echo "对账 SQL 执行失败（表结构变动？）—— 不阻塞"
  exit 0
fi

echo
echo "-- 1) 数量漂移行数（汇总 ≠ 明细合计）：$drift_rows（期望 0）"
if [ "$drift_rows" -gt 0 ]; then
  M "
  SELECT s.material_code, s.total_quantity AS 汇总表, IFNULL(SUM(i.quantity),0) AS 批次合计,
         s.total_quantity - IFNULL(SUM(i.quantity),0) AS 差额, s.total_reserved, s.last_update_time
    FROM inventory_stock s
    LEFT JOIN inventory_stock_item i
      ON i.inventory_item_id = s.inventory_item_id AND i.status = 1
   GROUP BY s.stock_id, s.material_code, s.total_quantity, s.total_reserved, s.last_update_time
  HAVING s.total_quantity <> IFNULL(SUM(i.quantity),0)
      OR s.total_reserved <> IFNULL(SUM(i.reserved_quantity),0);"
fi

echo
echo "-- 2) 有明细、无汇总行（该重算没重算）：$orphan_detail（期望 0）"
[ "$orphan_detail" -gt 0 ] && M "
  SELECT i.inventory_item_id, i.material_code, SUM(i.quantity) AS 明细合计
    FROM inventory_stock_item i
   WHERE i.status = 1 AND i.quantity <> 0
     AND NOT EXISTS (SELECT 1 FROM inventory_stock s WHERE s.inventory_item_id = i.inventory_item_id)
   GROUP BY i.inventory_item_id, i.material_code;"

echo
echo "-- 3) 有汇总、无明细（脏行）：$orphan_summary（期望 0）"
[ "$orphan_summary" -gt 0 ] && M "
  SELECT s.stock_id, s.inventory_item_id, s.material_code, s.total_quantity
    FROM inventory_stock s
   WHERE NOT EXISTS (SELECT 1 FROM inventory_stock_item i WHERE i.inventory_item_id = s.inventory_item_id AND i.status = 1);"

total=$((drift_rows + orphan_detail + orphan_summary))
echo
if [ "$total" -eq 0 ]; then
  echo "   ✅ 两本账一致（汇总表 = 批次明细合计）"
else
  echo "   ❌ 发现 $total 处不一致 —— 修复口径：按批次明细重算汇总（stockMapper.refreshSummaryByInventoryItemId）"
  echo "      排查方向：近期哪次「改库存」漏刷汇总（差额调整/特采加库存是已知漏点，见任务 dev-20260922-019）"
fi

if [ "$STRICT" = true ] && [ "$total" -gt 0 ]; then
  echo
  echo "✘ --strict：库存两本账不一致（$total 处）"
  exit 1
fi
echo
echo "== 对账完成：咨询模式，退出码 0（加 --strict 可当门禁）=="
exit 0
