#!/usr/bin/env bash
# ============================================================================
# check-stock-summary.sh —— 库存三本账对账（只读）
#   任务：dev-20260922-019（起因：dev-20260922 的 WO-PL2609220001-01 汇总表漏刷 50）
#         2026-09-23 dev-20260923-017 追加「流水 vs 批次」两查（起因：看板任务 2199 库存台账看不懂）
#
# 背景：库存记在三处，口径以「流水」为唯一真源 ——
#   · inventory_stock_item     ：按批次的明细账（结存）
#   · inventory_stock          ：按物料的汇总账（页面/发货/预留/预警都读它）
#   · inventory_transaction    ：库存流水（只增不改，入库为正/出库为负，唯一审计依据）
#   系统里「改库存」只有一个入口（InventoryStockMutationService.applyDelta），必须同时「改明细 + 重算汇总 + 写流水」。
#   本脚本抓三类不一致：①汇总≠批次合计 ②有明细无汇总 ③有汇总无明细 ④批次结存≠流水派生 ⑤有流水无批次行。
#
# 用法：
#   bash scripts/check-stock-summary.sh            # 咨询模式：只报告，永远 exit 0
#   bash scripts/check-stock-summary.sh --strict    # 有不一致则 exit 1（已接进 npm run validate）
# 环境变量（与 db-backup.sh/db-migrate.sh 同口径）：DB_HOST DB_PORT DB_USER DB_PASS DB_NAME
# 只读：只跑 SELECT，不改任何数据。
# 手册：jjx-docs/guides/scripts-commands-20260914.md
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

echo "== 库存对账：$(date '+%F %T')  汇总表 inventory_stock  →  批次表 inventory_stock_item  →  流水 inventory_transaction =="

drift_rows=$(M "
SELECT COUNT(*) FROM (
  SELECT s.stock_id
    FROM inventory_stock s
    LEFT JOIN inventory_stock_item i
      ON i.inventory_item_id = s.inventory_item_id AND i.status = 1
   GROUP BY s.stock_id, s.total_quantity, s.total_reserved
  HAVING s.total_quantity <> IFNULL(SUM(i.quantity), 0)
      OR s.total_reserved <> IFNULL(SUM(i.reserved_quantity), 0)
) d;" || echo "ERR")

orphan_detail=$(M "
SELECT COUNT(*) FROM inventory_stock_item i
 WHERE i.status = 1 AND i.quantity <> 0
   AND NOT EXISTS (SELECT 1 FROM inventory_stock s WHERE s.inventory_item_id = i.inventory_item_id);" || echo "ERR")

orphan_summary=$(M "
SELECT COUNT(*) FROM inventory_stock s
 WHERE NOT EXISTS (SELECT 1 FROM inventory_stock_item i WHERE i.inventory_item_id = s.inventory_item_id AND i.status = 1);" || echo "ERR")

# 4) 先按批次汇总所有位置/卷，再比流水；逐卷另按 stock_item_id 严格对账。
# 不把历史无行ID流水分配给新卷，不回写历史。
batch_drift_sql="
  SELECT i.inventory_item_id, i.batch_no, i.stock_qty, IFNULL(t.flow_qty,0) AS flow_qty
    FROM (SELECT inventory_item_id,batch_no,SUM(quantity) AS stock_qty
            FROM inventory_stock_item GROUP BY inventory_item_id,batch_no) i
    LEFT JOIN (SELECT inventory_item_id,batch_no,SUM(quantity) AS flow_qty
                 FROM inventory_transaction GROUP BY inventory_item_id,batch_no) t
      ON t.inventory_item_id=i.inventory_item_id AND t.batch_no <=> i.batch_no
   WHERE i.stock_qty <> IFNULL(t.flow_qty,0)"
ledger_drift=$(M "SELECT COUNT(*) FROM ($batch_drift_sql) d;" || echo "ERR")

# 5) 原批次孤儿检查保留，再加新卷流水的行/卷/位置身份一致性。
orphan_batch_sql="
  SELECT t.inventory_item_id,t.batch_no
    FROM inventory_transaction t
   WHERE NOT EXISTS (SELECT 1 FROM inventory_stock_item i
     WHERE i.inventory_item_id=t.inventory_item_id AND i.batch_no <=> t.batch_no)
   GROUP BY t.inventory_item_id,t.batch_no"
orphan_tx=$(M "SELECT COUNT(*) FROM ($orphan_batch_sql) d;" || echo "ERR")

roll_columns=$(M "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
  AND ((table_name='inventory_stock_item' AND column_name='roll_no')
    OR (table_name='inventory_transaction' AND column_name IN ('stock_item_id','roll_no','source_item_id')));" || echo "ERR")
roll_drift=0
roll_orphan=0
if [ "$roll_columns" = "4" ]; then
  roll_drift_sql="SELECT i.item_id,i.roll_no,i.quantity,IFNULL(t.flow_qty,0) AS flow_qty
    FROM inventory_stock_item i LEFT JOIN
      (SELECT stock_item_id,roll_no,SUM(quantity) AS flow_qty FROM inventory_transaction
        WHERE stock_item_id IS NOT NULL AND roll_no IS NOT NULL GROUP BY stock_item_id,roll_no) t
      ON t.stock_item_id=i.item_id AND t.roll_no=i.roll_no
    WHERE i.roll_no IS NOT NULL AND i.quantity <> IFNULL(t.flow_qty,0)"
  roll_orphan_sql="SELECT t.transaction_id,t.stock_item_id,t.roll_no
    FROM inventory_transaction t LEFT JOIN inventory_stock_item i ON i.item_id=t.stock_item_id
    WHERE (t.roll_no IS NOT NULL OR t.stock_item_id IS NOT NULL) AND
      (i.item_id IS NULL OR (t.roll_no IS NOT NULL AND t.source_item_id IS NULL) OR NOT (t.inventory_item_id <=> i.inventory_item_id)
       OR NOT (t.batch_no <=> i.batch_no) OR NOT (t.warehouse_id <=> i.warehouse_id)
       OR NOT (t.location_id <=> i.location_id) OR NOT (t.roll_no <=> i.roll_no))"
  roll_drift=$(M "SELECT COUNT(*) FROM ($roll_drift_sql) d;" || echo "ERR")
  roll_orphan=$(M "SELECT COUNT(*) FROM ($roll_orphan_sql) d;" || echo "ERR")
  if [[ "$ledger_drift" =~ ^[0-9]+$ && "$roll_drift" =~ ^[0-9]+$ ]]; then ledger_drift=$((ledger_drift + roll_drift)); else ledger_drift=ERR; fi
  if [[ "$orphan_tx" =~ ^[0-9]+$ && "$roll_orphan" =~ ^[0-9]+$ ]]; then orphan_tx=$((orphan_tx + roll_orphan)); else orphan_tx=ERR; fi
elif [ "$roll_columns" = "0" ]; then
  echo "卷材迁移未应用：本次核对旧结构批次合计；未验证逐卷生命周期。"
else
  echo "卷材结构不完整，无法执行逐行身份校验：$roll_columns/4"
  ledger_drift=ERR
fi

if [ "$drift_rows" = "ERR" ] || [ "$orphan_detail" = "ERR" ] || [ "$orphan_summary" = "ERR" ] \
   || [ "$ledger_drift" = "ERR" ] || [ "$orphan_tx" = "ERR" ]; then
  echo "对账 SQL 执行失败（表结构变动？）"
  [ "$STRICT" = true ] && exit 1
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

total=$((drift_rows + orphan_detail + orphan_summary + ledger_drift + orphan_tx))
echo
echo "-- 4) 批次结存 ≠ 流水派生结存（流水是唯一真源）：$ledger_drift（期望 0）"
if [ "$ledger_drift" -gt 0 ]; then
  M "$batch_drift_sql;"
  [ "$roll_drift" -gt 0 ] && M "$roll_drift_sql;"
fi

echo
echo "-- 5) 有流水、无批次行（孤儿流水）：$orphan_tx（期望 0）"
if [ "$orphan_tx" -gt 0 ]; then
  M "$orphan_batch_sql;"
  [ "$roll_orphan" -gt 0 ] && M "$roll_orphan_sql;"
fi

echo
if [ "$total" -eq 0 ]; then
  echo "   ✅ 三本账一致（汇总表 = 批次明细合计 = 流水派生）"
else
  echo "   ❌ 发现 $total 处不一致 —— 修复口径：按批次明细重算汇总（stockMapper.refreshSummaryByInventoryItemId）"
  echo "      排查方向：①近期哪次「改库存」漏刷汇总/漏写流水（差额调整/特采加库存是已知漏点，见 dev-20260922-019）"
  echo "                ②第 4/5 类不一致说明有代码绕过了唯一入口 applyDelta（详见 CONVENTIONS 库存口径铁律）"
fi

if [ "$STRICT" = true ] && [ "$total" -gt 0 ]; then
  echo
  echo "✘ --strict：库存三本账不一致（$total 处）"
  exit 1
fi
echo
echo "== 对账完成：咨询模式，退出码 0（加 --strict 可当门禁）=="
exit 0
