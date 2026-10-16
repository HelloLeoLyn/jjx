#!/usr/bin/env bash
# ============================================================================
# check-attachment-trace.sh —— 附件痕迹检查（只读）
#   任务：dev-20261016-075
#
# 用途：给「单据单号」或「来源类型,来源ID」或「草稿键」，打印该对象在
#       sys_attachment_ref 上的痕迹判定（每项一行 PASS/FAIL + 明细），
#       供附件接入类任务（dev-20261016-071 / 072）做端到端验收。
#
# 判定口径（判定只看 sys_attachment_ref；sys_attachment 只作参考信息展示）：
#   ·「文件存在」（sys_attachment 有行）不等于「引用已落定」——落定看的是引用行。
#   · 空结果（没有任何引用行）= FAIL，不许判 PASS。
#   · ref_state：draft=未落定（source_id 空、单据侧/产品侧都不可见）；active=已落定。
#   · draft_key：草稿行必须有（缺 -> FAIL）；active 行转正后保留，可空。
#   · document_type：非删行必须齐全（缺 -> FAIL）。
#   · 归属产品：只允许用 product_id（归属模型 §2-3）；有 product_code 无 product_id -> FAIL；
#     「整单级无产品」（product_id / product_code 都空）合法。
#   · 单号反查沿用后端 AttachmentSourcePolicy.idByNoSql（含 deleted=0 过滤）：
#     已删单据按单号查不到 -> 请改用 <来源类型>,<来源ID>。
#
# 用法：
#   bash scripts/check-attachment-trace.sh <单号>                  # 按单据单号（自动识别来源）
#   bash scripts/check-attachment-trace.sh <来源类型>,<来源ID>      # 按 来源类型,来源ID
#   bash scripts/check-attachment-trace.sh <来源类型>,<单号>        # 按 来源类型,单据单号
#   bash scripts/check-attachment-trace.sh product,<产品编码>       # 按产品（查 product_id 侧痕迹）
#   bash scripts/check-attachment-trace.sh --draft <草稿键>         # 按草稿键（未保存单据）
#   bash scripts/check-attachment-trace.sh quotation,3 --expect-attachment 116 --expect-count 4
#
#   可选断言（给了才判 PASS/FAIL，不给只报告）：
#     --expect-attachment <id>   期望存在该附件（attachment_id）的非删引用行
#     --expect-product <id>      期望存在该产品（product_id）的非删引用行
#     --expect-count <n>         期望非删引用行数 = n
#
# 危险等级：🟢 只读（只跑 SELECT；不改数据、不写文件）
# 环境变量（与 db-backup.sh / db-migrate.sh / check-stock-summary.sh 同口径）：
#   DB_HOST DB_PORT DB_USER DB_PASS DB_NAME
# 输出：判定行 = <PASS|FAIL><TAB><检查项><TAB><明细>；明细表 = TSV（行首有表头）
# 退出码：0=全部 PASS / 1=有 FAIL / 2=用法错误 / 3=连不上库
# 手册：jjx-docs/guides/scripts-commands-20260914.md
# ============================================================================
set -uo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"
DB_NAME="${DB_NAME:-jjx_erp_db}"

usage() {
  sed -n '3,40p' "$0"
}

MODE=""
TARGET=""
DRAFT_KEY=""
EXPECT_ATTACHMENT=""
EXPECT_PRODUCT=""
EXPECT_COUNT=""

while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --draft)
      MODE="draft"; DRAFT_KEY="${2:-}"; shift 2 ;;
    --expect-attachment)
      EXPECT_ATTACHMENT="${2:-}"; shift 2 ;;
    --expect-product)
      EXPECT_PRODUCT="${2:-}"; shift 2 ;;
    --expect-count)
      EXPECT_COUNT="${2:-}"; shift 2 ;;
    --)
      shift; continue ;;
    -*) echo "未知参数：$1（-h 看用法）" >&2; exit 2 ;;
    *)
      if [ -z "$TARGET" ]; then TARGET="$1"; else echo "只接受一个目标参数（-h 看用法）" >&2; exit 2; fi
      shift ;;
  esac
done

if [ -z "$TARGET" ] && [ -z "$DRAFT_KEY" ]; then
  echo "缺少目标：给单号 / 来源类型,来源ID / --draft 草稿键（-h 看用法）" >&2
  exit 2
fi

# 断言参数必须是正整数，避免拼进 SQL
for v in "$EXPECT_ATTACHMENT" "$EXPECT_PRODUCT" "$EXPECT_COUNT"; do
  [ -n "$v" ] && case "$v" in (*[!0-9]*) echo "断言参数必须是非负整数：$v" >&2; exit 2 ;; esac
done

command -v mysql >/dev/null 2>&1 || { echo "找不到 mysql 客户端（apt install mysql-client）"; exit 3; }
export MYSQL_PWD="$DB_PASS"
if ! mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --connect-timeout=5 -N -B "$DB_NAME" -e "SELECT 1" >/dev/null 2>&1; then
  echo "连不上库 $DB_USER@$DB_HOST:$DB_PORT/$DB_NAME —— 无法检查附件痕迹"
  exit 3
fi

M() { mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 -N -B "$DB_NAME" -e "$1"; }

# 单引号转义：参数一律当字面量，杜绝注入
esc() { printf '%s' "$1" | sed "s/'/''/g"; }

# 按业务单号反查来源（SQL 与后端 AttachmentSourcePolicy.SOURCES[].idByNoSql 一致）
resolve_by_no() {
  local no_esc="$1"
  while IFS='|' read -r t sql; do
    [ -z "$t" ] && continue
    local q id
    q="${sql//@NO@/$no_esc}"
    id=$(M "$q" 2>/dev/null | head -1)
    [ -n "$id" ] && printf '%s|%s
' "$t" "$id"
  done <<'SRC_EOF'
inquiry|SELECT inquiry_id FROM sales_inquiry WHERE inquiry_no='@NO@' AND deleted=0
quotation|SELECT quotation_id FROM sales_quotation WHERE quotation_no='@NO@' AND deleted=0
sales_order|SELECT order_id FROM sales_order WHERE order_no='@NO@' AND deleted=0 AND NOT EXISTS (SELECT 1 FROM sales_sample_order s WHERE s.order_id=sales_order.order_id AND s.deleted=0)
sample|SELECT s.order_id FROM sales_sample_order s JOIN sales_order o ON o.order_id=s.order_id WHERE o.order_no='@NO@' AND s.deleted=0 AND o.deleted=0
production_order|SELECT order_id FROM production_order WHERE order_no='@NO@'
purchase_order|SELECT order_id FROM purchase_order WHERE order_no='@NO@'
purchase_payment|SELECT payment_id FROM purchase_payment WHERE payment_no='@NO@'
purchase_receipt|SELECT inbound_id FROM inventory_inbound_order WHERE inbound_no='@NO@' AND source_type='PURCHASE'
purchase_invoice|SELECT document_id FROM purchase_document WHERE document_no='@NO@' AND document_type='invoice'
sales_delivery|SELECT delivery_id FROM sales_delivery WHERE delivery_no='@NO@' AND deleted=0
quality_archive|SELECT l.id FROM quality_template_print_log l LEFT JOIN quality_template_registry t ON t.id=l.template_id WHERE COALESCE(t.record_no, l.record_no)='@NO@'
quality_template|SELECT id FROM quality_template_registry WHERE record_no='@NO@'
task|SELECT task_id FROM sys_task WHERE task_code='@NO@'
material|SELECT material_id FROM inventory_material WHERE material_code='@NO@'
product|SELECT product_id FROM product WHERE product_code='@NO@'
SRC_EOF
}

canonical() {
  case "$1" in
    order) echo sales_order ;;
    sample_order) echo sample ;;
    purchase) echo purchase_order ;;
    production) echo production_order ;;
    *) echo "$1" ;;
  esac
}

SCOPE=""
WHERE=""

if [ -n "$DRAFT_KEY" ]; then
  MODE="draft"
elif case "$TARGET" in draft:*) true ;; *) false ;; esac; then
  MODE="draft"; DRAFT_KEY="${TARGET#draft:}"
elif case "$TARGET" in *,*) true ;; *) false ;; esac; then
  MODE="pair"
else
  MODE="auto"
fi

case "$MODE" in
  draft)
    [ -z "$DRAFT_KEY" ] && { echo "草稿键为空" >&2; exit 2; }
    KEY_ESC=$(esc "$DRAFT_KEY")
    WHERE="r.draft_key='$KEY_ESC'"
    SCOPE="mode=draft	draft_key=$DRAFT_KEY"
    ;;
  pair)
    TYPE=$(canonical "${TARGET%%,*}")
    REST="${TARGET#*,}"
    [ -z "$TYPE" ] && { echo "来源类型为空（-h 看用法）" >&2; exit 2; }
    if case "$REST" in *[!0-9]*|'') true ;; *) false ;; esac; then
      # 逗号后不是纯数字 -> 当成该来源下的业务单号
      REST_ESC=$(esc "$REST")
      SID=$(resolve_by_no "$REST_ESC" | awk -F'|' -v t="$TYPE" '$1==t{print $2; exit}')
      if [ -z "$SID" ]; then
        echo "按来源类型 $TYPE 反查不到单号 $REST（该来源不存在此单号）" >&2
        SID=""
      fi
    else
      SID="$REST"
    fi
    if [ -z "$SID" ]; then
      echo "FAIL	00_resolve	来源类型=$TYPE 单号=$REST 解析不到来源ID" 
      echo "FAIL	结论	目标解析失败（无法定位引用行）"
      exit 1
    fi
    if [ "$TYPE" = "product" ]; then
      WHERE="(r.product_id=$SID OR (r.source_type='product' AND r.source_id=$SID))"
    else
      WHERE="r.source_type='$TYPE' AND r.source_id=$SID"
    fi
    SCOPE="mode=来源	source_type=$TYPE	source_id=$SID"
    ;;
  auto)
    NO_ESC=$(esc "$TARGET")
    # 先看是不是草稿键（草稿行 source_id 为空，只能按 draft_key 找）
    DK_HIT=$(M "SELECT COUNT(*) FROM sys_attachment_ref WHERE draft_key='$NO_ESC'" 2>/dev/null)
    if [ -n "$DK_HIT" ] && [ "$DK_HIT" -gt 0 ]; then
      MODE="draft"; DRAFT_KEY="$TARGET"
      WHERE="r.draft_key='$NO_ESC'"
      SCOPE="mode=draft(按草稿键命中)	draft_key=$TARGET"
    else
      MATCHES=$(resolve_by_no "$NO_ESC" 2>/dev/null)
      N=$(printf '%s
' "$MATCHES" | grep -c .)
      if [ -z "$MATCHES" ] || [ "$N" -eq 0 ]; then
        echo "FAIL	00_resolve	单号=$TARGET 在已登记来源里查不到（inquiry/quotation/sales_order/sample/.../product）"
        echo "FAIL	结论	目标解析失败（无法定位引用行）"
        exit 1
      elif [ "$N" -gt 1 ]; then
        echo "FAIL	00_resolve	单号=$TARGET 命中多个来源：$(printf '%s' "$MATCHES" | tr '
' ' ')"
        echo "FAIL	结论	目标解析不唯一，请改用 <来源类型>,<来源ID>"
        exit 1
      fi
      TYPE=$(printf '%s' "$MATCHES" | awk -F'|' '{print $1}')
      SID=$(printf '%s' "$MATCHES" | awk -F'|' '{print $2}')
      if [ "$TYPE" = "product" ]; then
        WHERE="(r.product_id=$SID OR (r.source_type='product' AND r.source_id=$SID))"
      else
        WHERE="r.source_type='$TYPE' AND r.source_id=$SID"
      fi
      SCOPE="mode=单号	source_type=$TYPE	source_id=$SID"
    fi
    ;;
esac

ROWS_SQL="SELECT r.id, r.attachment_id, r.source_type, IFNULL(r.source_id,''), r.ref_state,
       IFNULL(r.draft_key,''), IFNULL(r.product_id,''), IFNULL(r.product_code,''),
       IFNULL(r.document_type,''), r.deleted, IFNULL(r.source_no,''),
       IFNULL(a.upload_state,''), IFNULL(r.display_name,'')
  FROM sys_attachment_ref r
  LEFT JOIN sys_attachment a ON a.id = r.attachment_id
 WHERE $WHERE
 ORDER BY r.id"

ROWS=$(M "$ROWS_SQL" 2>/dev/null)
STATS=$(printf '%s
' "$ROWS" | awk -F'\t' '
  NF>0 {
    total++
    if ($10=="0") {
      alive++
      if ($5=="active") active++
      if ($5=="draft") { draft++; if ($6=="") missdk++ }
      if ($9=="") missdt++
      if ($8!="" && $7=="") codeonly++
    } else { del++ }
  }
  END { printf "%d %d %d %d %d %d %d %d", alive+0, total+0, draft+0, active+0, missdk+0, missdt+0, codeonly+0, del+0 }')
read -r ALIVE TOTAL DRAFTN ACTIVEN MISSDK MISSDT CODEONLY DELN <<< "$STATS"

FAILS=0; CHECKS=0
emit() { printf '%s\t%s\t%s\n' "$1" "$2" "$3"; [ "$1" = "FAIL" ] && FAILS=$((FAILS+1)); CHECKS=$((CHECKS+1)); }

echo "== 附件痕迹检查 check-attachment-trace · 任务 dev-20261016-075 · $(date '+%F %T') =="
printf 'SCOPE\t%s\trows=%s\n' "$SCOPE" "$TOTAL"
echo "-- 判定（依据 sys_attachment_ref）--"

if [ "$ALIVE" -gt 0 ]; then
  emit PASS 01_ref_exists "非删引用行=$ALIVE（总行=$TOTAL 软删=$DELN）"
else
  emit FAIL 01_ref_exists "非删引用行=0（空结果=FAIL：该单据在 sys_attachment_ref 上没有痕迹；文件存在不算落定）"
fi
if [ "$ACTIVEN" -gt 0 ]; then
  emit PASS 02_settled "已落定 active=$ACTIVEN draft=$DRAFTN"
else
  emit FAIL 02_settled "active=0 draft=$DRAFTN（仍是草稿态：未落定，单据侧/产品侧都不可见）"
fi
if [ "$MISSDK" -eq 0 ]; then
  emit PASS 03_draft_key "草稿行缺 draft_key=0"
else
  emit FAIL 03_draft_key "草稿行缺 draft_key=$MISSDK（草稿键是草稿行的唯一线索）"
fi
if [ "$MISSDT" -eq 0 ]; then
  emit PASS 04_document_type "缺 document_type 的非删行=0"
else
  emit FAIL 04_document_type "缺 document_type 的非删行=$MISSDT"
fi
if [ "$CODEONLY" -eq 0 ]; then
  emit PASS 05_product_id "有 product_code 无 product_id 的非删行=0"
else
  emit FAIL 05_product_id "有 product_code 无 product_id 的非删行=$CODEONLY（归属模型 §2-3：关联一律用 product_id）"
fi

if [ -n "$EXPECT_ATTACHMENT" ]; then
  n=$(M "SELECT COUNT(*) FROM sys_attachment_ref WHERE attachment_id=$EXPECT_ATTACHMENT AND deleted=0" 2>/dev/null)
  if [ "${n:-0}" -gt 0 ]; then emit PASS 06_expect_attachment "attachment_id=$EXPECT_ATTACHMENT 有非删引用行（$n）"; else emit FAIL 06_expect_attachment "attachment_id=$EXPECT_ATTACHMENT 无非删引用行（预期新行未落）"; fi
fi
if [ -n "$EXPECT_PRODUCT" ]; then
  n=$(M "SELECT COUNT(*) FROM sys_attachment_ref WHERE product_id=$EXPECT_PRODUCT AND deleted=0" 2>/dev/null)
  if [ "${n:-0}" -gt 0 ]; then emit PASS 07_expect_product "product_id=$EXPECT_PRODUCT 有非删引用行（$n）"; else emit FAIL 07_expect_product "product_id=$EXPECT_PRODUCT 无非删引用行"; fi
fi
if [ -n "$EXPECT_COUNT" ]; then
  if [ "$ALIVE" -eq "$EXPECT_COUNT" ]; then emit PASS 08_expect_count "非删引用行=$ALIVE（预期 $EXPECT_COUNT）"; else emit FAIL 08_expect_count "非删引用行=$ALIVE（预期 $EXPECT_COUNT）"; fi
fi

echo "-- 明细（sys_attachment_ref）--"
printf 'id\tattachment_id\tsource_type\tsource_id\tref_state\tdraft_key\tproduct_id\tproduct_code\tdocument_type\tdeleted\tsource_no\tupload_state\tdisplay_name\n'
[ -n "$ROWS" ] && printf '%s\n' "$ROWS"
echo "-- 结论 --"
if [ "$FAILS" -eq 0 ]; then
  printf 'PASS\t结论\t%d 项全 PASS（引用痕迹符合口径）\n' "$CHECKS"
  exit 0
else
  printf 'FAIL\t结论\t%d 项中 %d 项 FAIL\n' "$CHECKS" "$FAILS"
  exit 1
fi
