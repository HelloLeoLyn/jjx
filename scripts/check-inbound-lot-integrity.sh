#!/usr/bin/env bash
# ============================================================================
# check-inbound-lot-integrity.sh —— 入库单/检验批维度巡检（只读）
#   任务：dev-20260923-010（起因：022「一验批一单 + 红冲」落地后，
#         check-stock-summary.sh 只覆盖「汇总表 vs 批次明细 vs 流水」，
#         缺入库单与检验批维度的校验）
#
# 与 check-stock-summary.sh 同口径（只读 / fail-open / --strict）。原有三查 + 2026-09-23（dev-20260923-023）补「数量守恒」五查：
#   ① 入库明细 lot_id 覆盖率：生产来源、未取消单据的明细里 lot_id 为空的数量（期望 0）
#   ② 同一 lot 被多张单重复计账：按 lot_id 汇总「未取消单据明细数量」应 ≤ 该批上限
#      上限按来源分叉（dev-20260924-027 修采购含不良误报）：
#        · 生产来源(PRODUCTION)：≤ pass_quantity（成品重复入库检测，保留强度）
#        · 其它来源(如 PURCHASE)：≤ lot_quantity（采购明细含不良，收货量必然 > 合格量）
#      反例说明：若统一用 lot_quantity，会漏掉「合格量<批量时两单合计不超批量却超合格量」的重复计账。
#      （红冲单数量为负、作废单 order_status=9 排除，故正常换代后净额为 0）
#   ③ 已过账生产入库明细的 posted_quantity 合计（按 inventory_item_id + batch_no）
#      应等于该批「入库侧流水」合计（INBOUND + ADJUST；红冲走 ADJUST 负额）
#      —— 出库方向（OUTBOUND/TRANSFER_OUT）不计，故成品出库不会误报；
#         若该批另有盘点/其它 ADJUST，需人工核对（脚本会列出明细行）。
#
# 2026-09-23（dev-20260923-023 数量守恒五查；业内口径见 CONVENTIONS §13）：
#   ④ 判定数量守恒：已判定批 pass + fail = inspected ≤ lot_quantity
#   ⑤ 不良台账守恒：批 fail = Σ NCR defect_quantity；未作废处置量 ≤ NCR 不良量
#   ⑥ 有效批合格量 ≤ 可判上限（批量 − 该批自身已报废未回收 − 让步未确认）—— 与判定护栏（dev-20260923-021）同口径
#      FQC整批复检子批取代父批；返工局部复检子批是增量，与原批同时有效（dev-20260926-005）。
#      处置仍按有效批自身聚合，避免整批复检的历史处置重复扣减。
#   ⑦ 工单完工数 = 有效批合格累计（整批复检替代、返工局部复检累加；防"复检换代不重算"复发）
#   ⑧ 入库/放行累计 stored_quantity ≤ pass_quantity
#   ⑨（2026-09-23 补，看板 2250）有效 FQC 批必有入库单：pass>0 且无后继版本的检验批，
#      必须存在挂在其 lot_id 上、未取消(order_status<>9)的生产入库明细 —— 兜住「FI 序号定长导致静默不出单」类问题，
#      以及任何"该出的单没出"的口径缺口（无单时 ① 查不出来）
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

# Effective FQC lineage, kept in sync with QualityLotLineagePolicy:
# whole-lot children replace their parent, while a partial rework-inspection child
# is additive. If a later whole-lot revision is created from the same parent, it
# supersedes both that parent and any earlier partial rework child.
EFFECTIVE_FQC_FILTER="
   AND NOT EXISTS (
       SELECT 1 FROM quality_lot c
       LEFT JOIN production_operation_execution ce ON ce.execution_id = c.execution_id
        WHERE c.parent_lot_id = l.lot_id AND c.del_flag = 0
          AND NOT (c.lot_type = 'FQC' AND IFNULL(ce.execution_type, '') = 'REWORK'))
   AND NOT (
       l.parent_lot_id IS NOT NULL
       AND EXISTS (SELECT 1 FROM production_operation_execution le
                    WHERE le.execution_id = l.execution_id AND le.execution_type = 'REWORK')
       AND EXISTS (
           SELECT 1 FROM quality_lot r
           LEFT JOIN production_operation_execution re ON re.execution_id = r.execution_id
            WHERE r.parent_lot_id = l.parent_lot_id AND r.lot_id > l.lot_id AND r.del_flag = 0
              AND NOT (r.lot_type = 'FQC' AND IFNULL(re.execution_type, '') = 'REWORK')))
"

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

# ② 同一 lot 被重复计账（未取消单据明细数量合计 > 该批上限；上限按来源分叉，见文件头说明）
double_lot=$(M "
SELECT COUNT(*) FROM (
  SELECT ii.lot_id
    FROM inventory_inbound_item ii
    JOIN inventory_inbound_order o ON o.inbound_id = ii.inbound_id
    JOIN quality_lot l ON l.lot_id = ii.lot_id
   WHERE o.order_status <> 9
     AND ii.lot_id IS NOT NULL
   GROUP BY ii.lot_id, o.source_type, l.pass_quantity, l.lot_quantity
  HAVING SUM(ii.quantity) > IF(UPPER(IFNULL(o.source_type, '')) = 'PRODUCTION',
                               l.pass_quantity, l.lot_quantity)
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

judge_conservation=$(M "
SELECT COUNT(*) FROM quality_lot
 WHERE del_flag = 0 AND inspected_quantity IS NOT NULL AND inspected_quantity > 0
   AND (pass_quantity + fail_quantity <> inspected_quantity OR inspected_quantity > lot_quantity);" 2>/dev/null || echo "ERR")

ncr_conservation=$(M "
SELECT
  (SELECT COUNT(*) FROM (
      SELECT l.lot_id FROM quality_lot l
       LEFT JOIN (SELECT lot_id, SUM(defect_quantity) q FROM quality_ncr WHERE del_flag = 0 GROUP BY lot_id) n ON n.lot_id = l.lot_id
       WHERE l.del_flag = 0 AND l.inspected_quantity > 0 AND IFNULL(l.fail_quantity, 0) > 0
         AND IFNULL(l.fail_quantity, 0) <> IFNULL(n.q, 0)) a)
+ (SELECT COUNT(*) FROM (
      SELECT n.ncr_id FROM quality_ncr n
       LEFT JOIN (SELECT ncr_id, SUM(quantity) q FROM quality_ncr_action WHERE del_flag = 0 AND status <> 'VOID' GROUP BY ncr_id) x ON x.ncr_id = n.ncr_id
       WHERE n.del_flag = 0 AND IFNULL(x.q, 0) > IFNULL(n.defect_quantity, 0)) b);" 2>/dev/null || echo "ERR")

upper_bound_drift=$(M "
SELECT COUNT(*) FROM quality_lot l
 LEFT JOIN (SELECT n.lot_id,
        IFNULL(SUM(CASE WHEN a.action_type = 'SCRAP' AND a.status = 'DONE' THEN a.quantity ELSE 0 END), 0) scrap,
        IFNULL(SUM(CASE WHEN a.action_type = 'CONCESSION' AND a.status = 'DONE' AND IFNULL(a.customer_confirmed, 0) <> 1 THEN a.quantity ELSE 0 END), 0) conc
      FROM quality_ncr n LEFT JOIN quality_ncr_action a ON a.ncr_id = n.ncr_id AND a.del_flag = 0
     WHERE n.del_flag = 0 GROUP BY n.lot_id) s ON s.lot_id = l.lot_id
 WHERE l.del_flag = 0 AND l.inspected_quantity > 0
   AND ((l.lot_type = 'FQC' ${EFFECTIVE_FQC_FILTER})
        OR (l.lot_type <> 'FQC' AND NOT EXISTS (
              SELECT 1 FROM quality_lot c WHERE c.parent_lot_id = l.lot_id AND c.del_flag = 0)))
   AND IFNULL(l.pass_quantity, 0) > GREATEST(0, IFNULL(l.lot_quantity, 0) - IFNULL(s.scrap, 0) - IFNULL(s.conc, 0));" 2>/dev/null || echo "ERR")

finish_recalc=$(M "
SELECT COUNT(*) FROM production_order o
 WHERE o.order_type = 'WORK_ORDER'
   AND EXISTS (SELECT 1 FROM quality_lot l WHERE l.order_id = o.order_id AND l.lot_type = 'FQC' AND l.del_flag = 0)
  AND IFNULL(o.completed_quantity, 0) <> (
        SELECT IFNULL(SUM(l.pass_quantity), 0) FROM quality_lot l
         WHERE l.order_id = o.order_id AND l.lot_type = 'FQC' AND l.del_flag = 0
           ${EFFECTIVE_FQC_FILTER});" 2>/dev/null || echo "ERR")

stored_over_pass=$(M "
SELECT COUNT(*) FROM quality_lot
 WHERE del_flag = 0 AND IFNULL(stored_quantity, 0) > IFNULL(pass_quantity, 0);" 2>/dev/null || echo "ERR")

has_inbound_doc=$(M "
SELECT COUNT(*) FROM quality_lot l
 WHERE l.del_flag = 0 AND l.lot_type = 'FQC' AND IFNULL(l.pass_quantity, 0) > 0
   ${EFFECTIVE_FQC_FILTER}
   AND NOT EXISTS (SELECT 1 FROM inventory_inbound_item ii
                     JOIN inventory_inbound_order o ON o.inbound_id = ii.inbound_id
                    WHERE ii.lot_id = l.lot_id AND o.order_status <> 9);" 2>/dev/null || echo "ERR")

if [ "$missing_lot" = "ERR" ] || [ "$double_lot" = "ERR" ] || [ "$posted_drift" = "ERR" ] \
   || [ "$judge_conservation" = "ERR" ] || [ "$ncr_conservation" = "ERR" ] || [ "$upper_bound_drift" = "ERR" ] \
   || [ "$finish_recalc" = "ERR" ] || [ "$stored_over_pass" = "ERR" ] || [ "$has_inbound_doc" = "ERR" ]; then
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
echo "-- ② 同一 lot 被多张单重复计账（明细合计 > 该批上限：生产比合格量/采购比批量）：$double_lot（期望 0）"
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

echo
echo "-- ④ 判定数量守恒（合格+不良≠检验数量，或 检验数量>批量）：$judge_conservation（期望 0）"
[ "$judge_conservation" -gt 0 ] && M "
  SELECT lot_id, lot_no, status, lot_quantity, inspected_quantity, pass_quantity, fail_quantity
    FROM quality_lot
   WHERE del_flag = 0 AND inspected_quantity IS NOT NULL AND inspected_quantity > 0
     AND (pass_quantity + fail_quantity <> inspected_quantity OR inspected_quantity > lot_quantity);"

echo
echo "-- ⑤ 不良台账守恒（批不良≠台账不良，或 未作废处置>不良）：$ncr_conservation（期望 0）"
[ "$ncr_conservation" -gt 0 ] && M "
  SELECT l.lot_no, l.fail_quantity AS 批不良, IFNULL(n.q, 0) AS 台账不良, '批/台账不一致' AS 类型
    FROM quality_lot l
    LEFT JOIN (SELECT lot_id, SUM(defect_quantity) q FROM quality_ncr WHERE del_flag = 0 GROUP BY lot_id) n ON n.lot_id = l.lot_id
   WHERE l.del_flag = 0 AND l.inspected_quantity > 0 AND IFNULL(l.fail_quantity, 0) > 0
     AND IFNULL(l.fail_quantity, 0) <> IFNULL(n.q, 0)
  UNION ALL
  SELECT n.ncr_no, n.defect_quantity, IFNULL(x.q, 0), '处置>不良' FROM quality_ncr n
    LEFT JOIN (SELECT ncr_id, SUM(quantity) q FROM quality_ncr_action WHERE del_flag = 0 AND status <> 'VOID' GROUP BY ncr_id) x ON x.ncr_id = n.ncr_id
   WHERE n.del_flag = 0 AND IFNULL(x.q, 0) > IFNULL(n.defect_quantity, 0);"

echo
echo "-- ⑥ 有效批合格量 > 可判上限（批量−已报废−让步未确认）：$upper_bound_drift（期望 0）"
[ "$upper_bound_drift" -gt 0 ] && M "
  SELECT l.lot_no, l.status, l.lot_quantity AS 批量, l.pass_quantity AS 合格, IFNULL(s.scrap, 0) AS 已报废,
         IFNULL(s.conc, 0) AS 让步未确认,
         GREATEST(0, IFNULL(l.lot_quantity, 0) - IFNULL(s.scrap, 0) - IFNULL(s.conc, 0)) AS 可判上限
    FROM quality_lot l
    LEFT JOIN (SELECT n.lot_id,
          IFNULL(SUM(CASE WHEN a.action_type = 'SCRAP' AND a.status = 'DONE' THEN a.quantity ELSE 0 END), 0) scrap,
          IFNULL(SUM(CASE WHEN a.action_type = 'CONCESSION' AND a.status = 'DONE' AND IFNULL(a.customer_confirmed, 0) <> 1 THEN a.quantity ELSE 0 END), 0) conc
        FROM quality_ncr n LEFT JOIN quality_ncr_action a ON a.ncr_id = n.ncr_id AND a.del_flag = 0
       WHERE n.del_flag = 0 GROUP BY n.lot_id) s ON s.lot_id = l.lot_id
   WHERE l.del_flag = 0 AND l.inspected_quantity > 0
     AND ((l.lot_type = 'FQC' ${EFFECTIVE_FQC_FILTER})
          OR (l.lot_type <> 'FQC' AND NOT EXISTS (
                SELECT 1 FROM quality_lot c WHERE c.parent_lot_id = l.lot_id AND c.del_flag = 0)))
     AND IFNULL(l.pass_quantity, 0) > GREATEST(0, IFNULL(l.lot_quantity, 0) - IFNULL(s.scrap, 0) - IFNULL(s.conc, 0));"

echo
echo "-- ⑦ 工单完工 ≠ 有效批合格累计（换代不重算会命中）：$finish_recalc（期望 0）"
[ "$finish_recalc" -gt 0 ] && M "
  SELECT o.order_no, o.planned_quantity AS 计划, o.completed_quantity AS 工单完工,
         (SELECT IFNULL(SUM(l.pass_quantity), 0) FROM quality_lot l
           WHERE l.order_id = o.order_id AND l.lot_type = 'FQC' AND l.del_flag = 0
             ${EFFECTIVE_FQC_FILTER}) AS 有效批合格累计
    FROM production_order o
   WHERE o.order_type = 'WORK_ORDER'
     AND EXISTS (SELECT 1 FROM quality_lot l WHERE l.order_id = o.order_id AND l.lot_type = 'FQC' AND l.del_flag = 0)
     AND IFNULL(o.completed_quantity, 0) <> (
          SELECT IFNULL(SUM(l.pass_quantity), 0) FROM quality_lot l
           WHERE l.order_id = o.order_id AND l.lot_type = 'FQC' AND l.del_flag = 0
             ${EFFECTIVE_FQC_FILTER});"

echo
echo "-- ⑧ 入库/放行累计 > 合格量：$stored_over_pass（期望 0）"
[ "$stored_over_pass" -gt 0 ] && M "
  SELECT lot_no, status, lot_quantity, pass_quantity, stored_quantity
    FROM quality_lot WHERE del_flag = 0 AND IFNULL(stored_quantity, 0) > IFNULL(pass_quantity, 0);"

echo
echo "-- ⑨ 有效 FQC 批缺入库单（pass>0 且无后继版本，却查不到未取消的生产入库明细）：$has_inbound_doc（期望 0）"
[ "$has_inbound_doc" -gt 0 ] && M "
  SELECT l.lot_id, l.lot_no, l.status, l.pass_quantity, l.stored_quantity, l.order_id
    FROM quality_lot l
   WHERE l.del_flag = 0 AND l.lot_type = 'FQC' AND IFNULL(l.pass_quantity, 0) > 0
     ${EFFECTIVE_FQC_FILTER}
     AND NOT EXISTS (SELECT 1 FROM inventory_inbound_item ii
                       JOIN inventory_inbound_order o ON o.inbound_id = ii.inbound_id
                      WHERE ii.lot_id = l.lot_id AND o.order_status <> 9);"

# ⑩ 不良件级三查（dev-20260924-004，**提示模式**：只报不拦，跑 5 个工作日后转 strict）
#   ⑩-1 件数守恒：有件的不良单，件数必须等于不良数量（向下取整）
#   ⑩-2 缺陷归因守恒：非作废件每件至少 1 条缺陷记录，且恰有 1 条主缺陷（is_main=1）
#   ⑩-3 处置对账：处置单 piece_count 必须等于该单挂的件数
piece_count_drift=$(M "
SELECT COUNT(*) FROM (
  SELECT n.ncr_id, COUNT(p.piece_id) AS pieces
    FROM quality_ncr n
    JOIN quality_ncr_piece p ON p.ncr_id = n.ncr_id AND p.del_flag = 0
   WHERE n.del_flag = 0
   GROUP BY n.ncr_id, n.defect_quantity
  HAVING COUNT(p.piece_id) <> FLOOR(n.defect_quantity)
) d;" 2>/dev/null || echo "ERR")

piece_attribution_gap=$(M "
SELECT COUNT(*) FROM (
  SELECT p.piece_id
    FROM quality_ncr_piece p
    LEFT JOIN quality_ncr_piece_defect d ON d.piece_id = p.piece_id
   WHERE p.del_flag = 0 AND p.status <> 'VOID'
   GROUP BY p.piece_id
  HAVING COUNT(d.defect_id) = 0 OR SUM(CASE WHEN d.is_main = 1 THEN 1 ELSE 0 END) <> 1
) d;" 2>/dev/null || echo "ERR")

action_piece_mismatch=$(M "
SELECT COUNT(*) FROM quality_ncr_action a
 WHERE a.del_flag = 0 AND a.piece_count IS NOT NULL
   AND a.piece_count <> (SELECT COUNT(*) FROM quality_ncr_piece p WHERE p.action_id = a.action_id AND p.del_flag = 0);" 2>/dev/null || echo "ERR")

echo
echo "-- ⑩ 不良件级三查【提示模式】件数守恒：$piece_count_drift / 缺陷归因：$piece_attribution_gap / 处置对账：$action_piece_mismatch（期望各 0）"
[ "$piece_count_drift" -gt 0 ] && M "
  SELECT n.ncr_no, n.defect_quantity AS 不良数量, COUNT(p.piece_id) AS 已发件数
    FROM quality_ncr n
    JOIN quality_ncr_piece p ON p.ncr_id = n.ncr_id AND p.del_flag = 0
   WHERE n.del_flag = 0
   GROUP BY n.ncr_id, n.ncr_no, n.defect_quantity
  HAVING COUNT(p.piece_id) <> FLOOR(n.defect_quantity);"
[ "$piece_attribution_gap" -gt 0 ] && M "
  SELECT p.piece_no, p.status, COUNT(d.defect_id) AS 缺陷条数,
         SUM(CASE WHEN d.is_main = 1 THEN 1 ELSE 0 END) AS 主缺陷条数
    FROM quality_ncr_piece p
    LEFT JOIN quality_ncr_piece_defect d ON d.piece_id = p.piece_id
   WHERE p.del_flag = 0 AND p.status <> 'VOID'
   GROUP BY p.piece_id, p.piece_no, p.status
  HAVING COUNT(d.defect_id) = 0 OR SUM(CASE WHEN d.is_main = 1 THEN 1 ELSE 0 END) <> 1;"
[ "$action_piece_mismatch" -gt 0 ] && M "
  SELECT a.action_id, a.action_type, a.quantity AS 处置数量, a.piece_count AS 记录件数,
         (SELECT COUNT(*) FROM quality_ncr_piece p WHERE p.action_id = a.action_id AND p.del_flag = 0) AS 实际件数
    FROM quality_ncr_action a
   WHERE a.del_flag = 0 AND a.piece_count IS NOT NULL
     AND a.piece_count <> (SELECT COUNT(*) FROM quality_ncr_piece p WHERE p.action_id = a.action_id AND p.del_flag = 0);"

# 注：⑩ 三查为提示模式，**不计入 total**（不拦 --strict）；稳定后再并入 total
total=$((missing_lot + double_lot + posted_drift + judge_conservation + ncr_conservation + upper_bound_drift + finish_recalc + stored_over_pass + has_inbound_doc))
echo
if [ "$total" -eq 0 ]; then
  echo "   ✅ 入库单/检验批维度一致（lot_id 齐全 · 无重复计账 · 已过账=流水）+ 数量守恒（判定/不良/上界/工单口径/放行/批必有单）全 0"
else
  echo "   ❌ 发现 $total 处不一致"
  echo "      排查方向：① lot_id 缺失 = 入库明细未挂检验批（022 口径要求明细挂 lot_id）"
  echo "                ② 重复计账 = 同一批被多张未取消单据收两次（红冲单未开或未过账）"
  echo "                ③ 已过账量 ≠ 入库侧流水 = 过账时漏写/多写流水，或该批另有盘点调整（人工核对）"
  echo "                ④~⑨ 数量守恒 = 判定/不良台账/可判上限/工单完工口径/放行进库量/批必有入库单 六处必须守恒"
  echo "      口径参考：jjx-docs/standards/CONVENTIONS.md §13；设计稿 jjx-docs/design/fqc-judgement-upper-bound-dev-20260923-021.md"
fi

if [ "$STRICT" = true ] && [ "$total" -gt 0 ]; then
  echo
  echo "✘ --strict：入库单/检验批维度巡检不一致（$total 处）"
  exit 1
fi
echo
if [ "$STRICT" = true ]; then
  echo "== 巡检完成：严格模式通过，退出码 0 =="
else
  echo "== 巡检完成：咨询模式，退出码 0（加 --strict 可当门禁）=="
fi
exit 0
