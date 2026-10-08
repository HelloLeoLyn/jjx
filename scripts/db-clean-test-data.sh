#!/usr/bin/env bash
# ============================================================================
# JJX 清理测试数据唯一入口（jjx-docs/sql/00_clean_test_data.sql）
#
#   bash scripts/db-clean-test-data.sh                # 只读体检（默认，不写库）
#   bash scripts/db-clean-test-data.sh --include-products # 只读预览全量清理，含全部产品资料
#   bash scripts/db-clean-test-data.sh --domains purchase,inventory,quality        # 只体检三域
#   bash scripts/db-clean-test-data.sh --execute      # 真执行：须在终端手工输入库名确认
#   bash scripts/db-clean-test-data.sh --execute --backup <file>   # 指定本次校验的手工全库备份
#   bash scripts/db-clean-test-data.sh --domains purchase,inventory,quality --execute  # 真清理三域（仍须终端手输库名）
#   MYSQL_BIN_DIR=/path/to/mysql/bin                  # 可选：指定 MySQL 客户端目录
#
# 固定顺序（不可跳过）：只读体检 → 校验手工备份存在 → 人工确认 → 才执行。
# 备份由用户手工做（CONVENTIONS §2，2026-09-28 口径）：脚本不再自动备份，只校验
# 「已存在今天的全库备份」才放行，找不到即中止。导出必须排除人事档案表 hr_employee
# （仓库为公开，入库即永久留在 git 历史）。
# 人工确认 = 手工输入库名（jjx_erp_db）。非终端（agent/管道）一律拒绝执行——因为
# 00_clean_test_data.sql 是整表 TRUNCATE，且它自己会清空 sys_oper_log，库里留不下痕迹。
# 规范出处：jjx-docs/standards/CONVENTIONS.md §2（先备份再动库）/ §5。
# 危险等级：🟢 无参数/--domains=只读体检（不写库）／🔴 --execute 真清理（固定顺序：体检 → 校验手工备份 → 人工确认 → 执行）
# 前置：--execute 必须在终端手工执行（agent/管道一律拒绝）；确认方式=手输库名 jjx_erp_db；BACKUP_DIR（默认仓内 jjx-docs/sql/backups/）里要有今天的全库备份，或用 --backup 指定；--domains 仅可取 purchase/inventory/quality
# 手册：jjx-docs/guides/scripts-commands-20260914.md
# ============================================================================
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  printf '✘ 无法定位 Git 仓库根目录\n' >&2
  exit 1
}
cd "$REPO_ROOT"

DB_HOST="${DB_HOST:-${JJX_DB_HOST:-127.0.0.1}}"
DB_PORT="${DB_PORT:-${JJX_DB_PORT:-3306}}"
DB_USER="${DB_USER:-${JJX_DB_USER:-root}}"
DB_PASS="${DB_PASS:-${JJX_DB_PASSWORD:-123456}}"
DB_NAME="${DB_NAME:-${JJX_DB_NAME:-jjx_erp_db}}"
MYSQL_BIN_DIR="${MYSQL_BIN_DIR:-}"
BACKUP_DIR="${JJX_BACKUP_DIR:-$REPO_ROOT/jjx-docs/sql/backups}"
SQL_FILE="$REPO_ROOT/jjx-docs/sql/00_clean_test_data.sql"
EXECUTE=0
BACKUP_ARG="${JJX_CLEAN_BACKUP:-}"
DOMAIN_MODE=0
DOMAINS=()
DOMAIN_LABEL=""
INCLUDE_PRODUCTS=0
PRODUCT_TABLES=(product_config_option product_config_model product product_category)
PLAN_SQL=""
cleanup_plan() { [ -z "$PLAN_SQL" ] || rm -f "$PLAN_SQL"; }
trap cleanup_plan EXIT

c_red=$'\033[31m'; c_grn=$'\033[32m'; c_yel=$'\033[33m'; c_off=$'\033[0m'
say()  { printf '%s\n' "$*"; }
die()  { printf '%s✘ %s%s\n' "$c_red" "$*" "$c_off" >&2; exit 1; }
ok()   { printf '%s✓%s %s\n' "$c_grn" "$c_off" "$*"; }
warn() { printf '%s⚠%s %s\n' "$c_yel" "$c_off" "$*"; }

usage() {
  cat <<'EOF'
用途: 清理测试数据（jjx-docs/sql/00_clean_test_data.sql 的唯一入口；整表 TRUNCATE + 条件 DELETE，表清单以脚本实际解析为准）
危险等级: 🟢 无参数/--domains=只读体检（不写库）／🔴 --execute 真清理（体检 → 校验手工备份 → 人工确认 → 执行）
前置: --execute 必须在终端手工执行（agent/管道一律拒绝）；确认方式=手工输入库名 jjx_erp_db；BACKUP_DIR（默认仓库内 jjx-docs/sql/backups/）里要有今天的全库备份（或用 --backup 指定）；备份必须排除 hr_employee
域参数: --domains <逗号分隔>，可选 purchase / inventory / quality；可组合，域模式不清理 sys_task
产品参数: --include-products，仅限全量模式；同时清空所有产品、分类、配置模型和选项，清理无引用的产品库存身份。不是自动识别测试产品。
用法:
  bash scripts/db-clean-test-data.sh             只读体检：打印本次将删除多少行
  bash scripts/db-clean-test-data.sh --include-products  只读预览：全量清理包含全部产品资料
  bash scripts/db-clean-test-data.sh --include-products --execute  含全部产品的全量清理（手工确认）
  bash scripts/db-clean-test-data.sh --domains purchase,inventory,quality        只体检三域
  bash scripts/db-clean-test-data.sh --execute   真执行（须在终端手输库名确认；用今天的全库备份）
  bash scripts/db-clean-test-data.sh --execute --backup <file>   指定本次校验的手工全库备份
  bash scripts/db-clean-test-data.sh --domains purchase,inventory,quality --execute  真清理三域（仍须终端手输库名）
退出码: 0=体检通过或清理成功  1=拒绝执行/中止/失败
手册: jjx-docs/guides/scripts-commands-20260914.md
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --execute) EXECUTE=1; shift ;;
    --include-products) INCLUDE_PRODUCTS=1; shift ;;
    --backup)
      [ $# -ge 2 ] || die "--backup 缺少参数（手工全库备份文件路径）"
      BACKUP_ARG="$2"; shift 2 ;;
    --domains)
      [ $# -ge 2 ] || die "--domains 缺少参数（可选值: purchase, inventory, quality）"
      DOMAIN_MODE=1
      IFS=',' read -r -a DOMAINS <<< "$2"
      [ "${#DOMAINS[@]}" -gt 0 ] || die "--domains 不能为空（可选值: purchase, inventory, quality）"
      for domain in "${DOMAINS[@]}"; do
        case "$domain" in
          purchase|inventory|quality) ;;
          *) die "非法域: $domain（可选值: purchase, inventory, quality）" ;;
        esac
      done
      DOMAIN_LABEL="$(IFS=,; printf '%s' "${DOMAINS[*]}")"
      shift 2
      ;;
    *) die "未知参数: $1（用法见 $0 --help）" ;;
  esac
done
[ "$INCLUDE_PRODUCTS" -eq 0 ] || [ "$DOMAIN_MODE" -eq 0 ] || die "--include-products 不能与 --domains 混用；产品清理需要完整的业务关联清理范围"

domain_selected() {
  local table="$1" domain
  for domain in "${DOMAINS[@]}"; do
    case "$domain" in
      # purchase_ 是采购单据及其明细，采购供应商是保留主数据，不在清理清单中。
      purchase) [[ "$table" == purchase_* ]] && return 0 ;;
      # inventory_iqc_ 是来料检验台账，业务归属质量域而非库存账务域。
      quality) [[ "$table" == quality_* || "$table" == inventory_iqc_* ]] && return 0 ;;
  # inventory_ 是库存业务及流水；inventory_iqc_ 已归质量，主数据表由白名单保留。
      inventory) [[ "$table" == inventory_* && "$table" != inventory_iqc_* ]] && return 0 ;;
    esac
  done
  return 1
}

# ── 0. 前置守卫 ────────────────────────────────────────────────────────────
# 2026-09-28 用户口径（CONVENTIONS §2）：备份放仓内 jjx-docs/sql/backups/、由用户手工做、
# 只留最新一份；脚本不再自动备份、不写索引 —— --execute 只校验「今天的全库备份存在」才放行。
[ -f "$SQL_FILE" ] || die "清理脚本不存在: $SQL_FILE"
[ "$DB_NAME" = "jjx_erp_db" ] || die "目标库必须是 jjx_erp_db（当前: $DB_NAME）——防误连"
if [ "$EXECUTE" -eq 1 ] && [ ! -t 0 ]; then
  die "真执行需要人工确认：请在终端里手工运行本脚本（agent/管道调用一律拒绝）"
fi

# ── 0b. 输入 SQL 预检：MySQL 只认 '-- '（连字符后必须跟空格），
#     '---- 变更说明' 这种 3 个以上连字符开头的行会被当作 SQL → ERROR 1064 并中断整轮清理
#     （2026-09-28 实测：00_clean_test_data.sql 里一行 '----' 让 --execute 直接失败）。
BAD_COMMENT="$(grep -n '^-\{3,\}' "$SQL_FILE" || true)"
if [ -n "$BAD_COMMENT" ]; then
  printf '%s\n' "$BAD_COMMENT" >&2
  die "清理 SQL 存在非法注释行（见上，3 个以上连字符开头，MySQL 会报 1064）：修正后再执行（本次未备份、未写库）"
fi

if [ -n "$MYSQL_BIN_DIR" ]; then
  if command -v cygpath >/dev/null 2>&1; then
    MYSQL_BIN_DIR="$(cygpath -u "$MYSQL_BIN_DIR" 2>/dev/null || printf '%s' "$MYSQL_BIN_DIR")"
  fi
  [ -d "$MYSQL_BIN_DIR" ] && PATH="$MYSQL_BIN_DIR:$PATH"
fi

case "$(uname -s 2>/dev/null || true)" in
  MINGW*|MSYS*|CYGWIN*)
    for pf in "${PROGRAMFILES:-}" "$(printenv 'PROGRAMFILES(X86)' 2>/dev/null || true)" "/c/Program Files" "/c/Program Files (x86)"; do
      [ -n "$pf" ] || continue
      if command -v cygpath >/dev/null 2>&1; then
        pf="$(cygpath -u "$pf" 2>/dev/null || printf '%s' "$pf")"
      fi
      for bin_dir in "$pf"/MySQL/MySQL\ Server\ */bin "$pf"/MariaDB\ */bin; do
        [ -d "$bin_dir" ] && PATH="$bin_dir:$PATH"
      done
    done
    [ -d /c/xampp/mysql/bin ] && PATH="/c/xampp/mysql/bin:$PATH"
    ;;
esac
export PATH

MYSQL_BIN="$(command -v mysql 2>/dev/null || true)"
[ -n "$MYSQL_BIN" ] || die "找不到 mysql 客户端；请安装 MySQL Client 并加入 PATH，或设置 MYSQL_BIN_DIR（Windows Git Bash 示例：C:/Program Files/MySQL/MySQL Server 8.0/bin）"

export MYSQL_PWD="$DB_PASS"
MYSQL=("$MYSQL_BIN" -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 -N -B)
CONNECTION_ERROR="$("${MYSQL[@]}" --connect-timeout=5 -e "SELECT 1" "$DB_NAME" 2>&1)" || \
  die "数据库连接失败 ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}：$CONNECTION_ERROR"

# 先生成本次实际 SQL，预览与执行使用同一清单。直接运行原 SQL 仍保留产品。
PLAN_SQL="$(mktemp)"
awk -v include_products="$INCLUDE_PRODUCTS" '
  /^-- INCLUDE_PRODUCTS: / {
    if (include_products == 1) sub(/^-- INCLUDE_PRODUCTS: /, ""); else next
  }
  { print }
' "$SQL_FILE" > "$PLAN_SQL"

db_count() {
  local count
  count="$("${MYSQL[@]}" "$DB_NAME" -e "$1")" || die "数据库计数失败，流程已中止"
  [[ "$count" =~ ^[0-9]+$ ]] || die "数据库计数返回异常，流程已中止：$count"
  printf '%s' "$count"
}

# ── 1. 解析清理目标（只认整行语句，注释掉的忽略）──────────────────────────────
TRUNCATE_TABLES=()
DELETE_TABLES=()
DELETE_WHERES=()
while IFS= read -r line; do
  line="${line%$'\r'}"
  case "$line" in
    TRUNCATE*';')
      t="${line#TRUNCATE }"; t="${t%%;*}"; t="${t//[[:space:]]/}"
      [ -n "$t" ] && TRUNCATE_TABLES+=("$t")
      ;;
    DELETE\ FROM*';')
      rest="${line#DELETE FROM }"
      t="${rest%% WHERE *}"; t="${t//[[:space:]]/}"
      w="${rest#* WHERE }"; w="${w%;*}"
      [ -n "$t" ] && { DELETE_TABLES+=("$t"); DELETE_WHERES+=("$w"); }
      ;;
  esac
done < "$PLAN_SQL"
[ "${#TRUNCATE_TABLES[@]}" -gt 0 ] || die "未能从 $SQL_FILE 解析出 TRUNCATE 目标，请核对脚本格式"

ALL_TRUNCATE_TABLES=("${TRUNCATE_TABLES[@]}")
ALL_DELETE_TABLES=("${DELETE_TABLES[@]}")
ALL_DELETE_WHERES=("${DELETE_WHERES[@]}")
if [ "$DOMAIN_MODE" -eq 1 ]; then
  FILTERED_TRUNCATE_TABLES=()
  for t in "${ALL_TRUNCATE_TABLES[@]}"; do
    domain_selected "$t" && FILTERED_TRUNCATE_TABLES+=("$t")
  done
  TRUNCATE_TABLES=("${FILTERED_TRUNCATE_TABLES[@]}")
  FILTERED_DELETE_TABLES=()
  FILTERED_DELETE_WHERES=()
  for i in "${!ALL_DELETE_TABLES[@]}"; do
    t="${ALL_DELETE_TABLES[$i]}"
    if domain_selected "$t"; then
      FILTERED_DELETE_TABLES+=("$t")
      FILTERED_DELETE_WHERES+=("${ALL_DELETE_WHERES[$i]}")
    fi
  done
  DELETE_TABLES=("${FILTERED_DELETE_TABLES[@]}")
  DELETE_WHERES=("${FILTERED_DELETE_WHERES[@]}")
fi

# ── 2. 只读体检 ────────────────────────────────────────────────────────────
SQL_BYTES="$(stat -c%s "$SQL_FILE")"
say "══ 清理体检（只读，未写库） ══"
say "目标库: $DB_NAME@$DB_HOST:$DB_PORT"
say "脚本  : jjx-docs/sql/00_clean_test_data.sql（$SQL_BYTES 字节）"
if [ "$DOMAIN_MODE" -eq 1 ]; then
  say "模式  : 仅清理域 $DOMAIN_LABEL"
else
  say "模式  : 全量清理"
fi
if [ "$INCLUDE_PRODUCTS" -eq 1 ]; then
  warn "产品  : 同时清空全部产品资料（含产品分类、配置模型和选项）；不是仅删除测试产品"
else
  say "产品  : 保留产品、分类、配置模型和选项"
fi
say ""

say "① TRUNCATE 组：${#TRUNCATE_TABLES[@]} 张表将被清空"
TRUNC_TOTAL=0
TRUNC_NONZERO=0
for t in "${TRUNCATE_TABLES[@]}"; do
  n="$(db_count "SELECT COUNT(*) FROM \`$t\`;")" || exit 1
  TRUNC_TOTAL=$((TRUNC_TOTAL + n))
  if [ "$n" -gt 0 ]; then
    say "   $t: $n 行"
    TRUNC_NONZERO=$((TRUNC_NONZERO + 1))
  fi
done
if [ "$TRUNC_NONZERO" -eq 0 ]; then
  say "   （有数据的 0 张，其余 ${#TRUNCATE_TABLES[@]} 张均为 0 行）"
else
  say "   —— 有数据的 $TRUNC_NONZERO 张，其余 $((${#TRUNCATE_TABLES[@]} - TRUNC_NONZERO)) 张为 0 行"
fi
say "   合计将清空 $TRUNC_TOTAL 行"
if [ "$INCLUDE_PRODUCTS" -eq 1 ]; then
  say "   产品专项范围：${PRODUCT_TABLES[*]}（含零行表）"
fi
say ""

if [ "${#DELETE_TABLES[@]}" -gt 0 ]; then
  say "② DELETE 组：${#DELETE_TABLES[@]} 张表按条件删除"
  DEL_TOTAL=0
  for i in "${!DELETE_TABLES[@]}"; do
    t="${DELETE_TABLES[$i]}"
    w="${DELETE_WHERES[$i]}"
    if [ "$INCLUDE_PRODUCTS" -eq 1 ] && [ "$t" = "inventory_item" ]; then
      # 产品及库存业务表稍后都将清空，按完整计划计算，不只统计当前孤儿。
      n="$(db_count "SELECT COUNT(*) FROM inventory_item WHERE item_type = 'PRODUCT';")" || exit 1
      say "   inventory_item 产品身份：按关联业务和产品清空后的计划计数；执行仍保留全部引用保护"
    else
      n="$(db_count "SELECT COUNT(*) FROM \`$t\` WHERE $w;")" || exit 1
    fi
    all="$(db_count "SELECT COUNT(*) FROM \`$t\`;")" || exit 1
    DEL_TOTAL=$((DEL_TOTAL + n))
    say "   $t: 将删 $n 条（保留 $((all - n)) 条）"
    say "      条件: WHERE $w"
  done
  say "   合计将删除 $DEL_TOTAL 条"
  say ""
elif [ "$DOMAIN_MODE" -eq 1 ]; then
  say "② DELETE 组：按域模式跳过 sys_task 清理"
  say "   （按域模式跳过 sys_task 清理）"
  say ""
fi

# ── 覆盖率校验：库里每张表都必须有归宿（TRUNCATE / DELETE / 保留白名单）────────
# 起因：inventory_iqc_batch 由迁移 136 新建后未同步清理清单 → 清理时批次行残留成孤儿，
# 明细 id 复用后又错挂到新单（2026-09-21，任务 dev-20260921-024）。
# 白名单对应 00_clean_test_data.sql 第 12 节「保留」：新增/下线保留表时两边同步。
RETAINED_TABLES=(
  sys_user sys_role sys_menu sys_role_menu sys_user_role sys_dept
  sys_config sys_dict sys_dict_item sys_event_config
  sys_event_last_payload sys_event_var
  quality_template_registry
  engineering_standard_process engineering_process_icon_sample
  sales_customer purchase_supplier inventory_material inventory_material_category
  inventory_warehouse inventory_item
  product product_category product_config_model product_config_option
  sys_tag sys_tag_rel hr_employee hr_dept_mapping
  engineering_die engineering_screen_frame engineering_screen_plate
  # Legacy tables still present in this database; preserve their rows/schema until separately retired.
  archive_production_quality_inspection archive_production_quality_inspection_item
  quality_sampling_plan sales_order_review
)
DB_TABLES="$("${MYSQL[@]}" "$DB_NAME" -N -B -e \
  "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_type='BASE TABLE'" | tr -d '\r' | sort)"
DB_COUNT="$(printf '%s\n' "$DB_TABLES" | grep -c . || true)"
UNCOVERED=()
if [ "$DOMAIN_MODE" -eq 1 ]; then
  say "③ 按域排除 $((${#ALL_TRUNCATE_TABLES[@]} - ${#TRUNCATE_TABLES[@]})) 张（不清理）"
fi
while IFS= read -r t; do
  [ -n "$t" ] || continue
  hit=0
  COVERAGE_TABLES=("${TRUNCATE_TABLES[@]}" "${DELETE_TABLES[@]:-}" "${RETAINED_TABLES[@]}")
  if [ "$DOMAIN_MODE" -eq 1 ]; then
    COVERAGE_TABLES+=("${ALL_TRUNCATE_TABLES[@]}" "${ALL_DELETE_TABLES[@]}")
  fi
  for x in "${COVERAGE_TABLES[@]}"; do
    if [ "$t" = "$x" ]; then hit=1; break; fi
  done
  [ "$hit" -eq 0 ] && UNCOVERED+=("$t")
done <<< "$DB_TABLES"
if [ "${#UNCOVERED[@]}" -gt 0 ]; then
  warn "③ 覆盖率校验：${#UNCOVERED[@]} 张表既不在清理清单、也不在保留白名单（清理后会残留脏数据）"
  for t in "${UNCOVERED[@]}"; do say "   - $t"; done
  die "覆盖率校验未通过，已中止。处理：业务表→加到 00_clean_test_data.sql 对应模块段；基础档案/配置→补进 SQL 第 12 节保留清单与本脚本 RETAINED_TABLES"
else
  say "③ 覆盖率校验：库 ${DB_COUNT} 张表均有归宿（清理清单 ∪ 保留白名单）"
fi

check_retained_product_refs() {
  [ "$INCLUDE_PRODUCTS" -eq 1 ] || return 0
  local refs t column n clear x blocked=0
  refs="$("${MYSQL[@]}" "$DB_NAME" -e "SELECT c.table_name,c.column_name FROM information_schema.columns c JOIN information_schema.tables t ON t.table_schema=c.table_schema AND t.table_name=c.table_name WHERE c.table_schema='$DB_NAME' AND t.table_type='BASE TABLE' AND c.column_name IN ('product_id','product_code') ORDER BY c.table_name,c.column_name;")" || die "读取产品引用结构失败，未执行清理"
  while IFS=$'\t' read -r t column; do
    [ -n "$t" ] || continue
    [[ "$t" =~ ^[a-zA-Z0-9_]+$ && "$column" =~ ^[a-zA-Z0-9_]+$ ]] || die "产品引用字段名异常"
    clear=0
    for x in "${TRUNCATE_TABLES[@]}"; do
      [ "$t" != "$x" ] || { clear=1; break; }
    done
    [ "$clear" -eq 0 ] || continue
    if [ "$column" = "product_id" ]; then
      n="$(db_count "SELECT COUNT(*) FROM \`$t\` WHERE product_id IS NOT NULL AND product_id <> 0;")" || exit 1
    else
      n="$(db_count "SELECT COUNT(*) FROM \`$t\` WHERE product_code IS NOT NULL AND TRIM(product_code) <> '';")" || exit 1
    fi
    if [ "$n" -gt 0 ]; then
      warn "   保留表 $t.$column 有 $n 条产品引用（包含已有悬空引用），不能清空产品"
      blocked=1
    fi
  done <<< "$refs"
  # 通用标签关系没有 product_id 字段；保留标签配置，不悄悄删除关系。
  n="$(db_count "SELECT COUNT(*) FROM sys_tag_rel WHERE biz_type IN ('product','product_config_model','product_config_option','product_category');")" || exit 1
  if [ "$n" -gt 0 ]; then
    warn "   保留表 sys_tag_rel 有 $n 条产品相关标签关系，需先处理关联"
    blocked=1
  fi
  [ "$blocked" -eq 0 ] || die "保留表的产品引用检查未通过，未执行清理；请先核对以上关联"
  ok "产品引用检查通过：保留表无产品引用"
}
check_retained_product_refs

if [ "$EXECUTE" -eq 0 ]; then
  say ""
  ok "体检完成，未写库、未执行清理"
  if [ "$DOMAIN_MODE" -eq 1 ]; then
    say "   要真执行: bash scripts/db-clean-test-data.sh --domains $DOMAIN_LABEL --execute（在终端手工输入库名确认）"
  elif [ "$INCLUDE_PRODUCTS" -eq 1 ]; then
    say "   要真执行: bash scripts/db-clean-test-data.sh --include-products --execute（清空所有产品，须终端手工确认）"
  else
    say "   要真执行: bash scripts/db-clean-test-data.sh --execute（在终端手工输入库名确认）"
  fi
  exit 0
fi

# ── 3. 校验手工备份（CONVENTIONS §2：脚本不再自动备份，失败则绝不执行清理）────
# 顺序：优先 --backup/JJX_CLEAN_BACKUP 指定；否则取 BACKUP_DIR 里「今天」的全库备份（最新一份）。
mkdir -p "$BACKUP_DIR" || die "无法创建备份目录 $BACKUP_DIR"
say ""
say "── 校验手工备份 ──"
if [ -n "$BACKUP_ARG" ]; then
  [ -f "$BACKUP_ARG" ] || die "指定的备份文件不存在: $BACKUP_ARG"
  BK_FILE="$BACKUP_ARG"
else
  BK_FILE="$(ls -1t "$BACKUP_DIR"/jjx_erp_db_backup_"$(date +%Y%m%d)"-*.sql 2>/dev/null | head -1 || true)"
fi
[ -n "$BK_FILE" ] || die "未找到今天的全库备份。请先手工备份（只留最新一份；导出必须排除 hr_employee）：
  mysqldump -h127.0.0.1 -P3306 -uroot --single-transaction --set-gtid-purged=OFF --no-tablespaces --ignore-table=jjx_erp_db.hr_employee jjx_erp_db > jjx-docs/sql/backups/jjx_erp_db_backup_$(date +%Y%m%d-%H%M)_before-clean-test-data.sql"
BK_SIZE="$(stat -c%s "$BK_FILE" 2>/dev/null || echo 0)"
BK_TABLES="$(grep -c '^CREATE TABLE' "$BK_FILE" 2>/dev/null || true)"
[ "$BK_SIZE" -gt 1024 ] && [ "$BK_TABLES" -ge 1 ] || die "手工备份文件异常（${BK_SIZE}B / ${BK_TABLES} 张表）——已中止，未执行清理"
BK_MD5="$(md5sum "$BK_FILE" | awk '{print $1}')"
ok "手工全库备份 $BK_FILE"
say "    $BK_SIZE 字节 / $BK_TABLES 张表 / md5 $BK_MD5"
if grep -q '^CREATE TABLE `hr_employee`' "$BK_FILE"; then
  warn "备份含人事档案表 hr_employee（身份证密文/住址/电话）——仓库为公开，入库即永久留在 git 历史，请勿提交该文件（CONVENTIONS §2）"
fi

# ── 4. 人工确认（手输库名）───────────────────────────────────────────────────
say ""
printf '人工确认：请手工输入库名以执行清理 [%s]（其他任何输入=中止）: ' "$DB_NAME"
read -r ANSWER || ANSWER=""
if [ "$ANSWER" != "$DB_NAME" ]; then
  die "确认失败（输入与库名不一致）——已中止，未执行清理。备份在 $BK_FILE（md5 $BK_MD5）"
fi
check_retained_product_refs

# ── 5. 执行清理 ────────────────────────────────────────────────────────────
say ""
say "── 执行清理 ──"
START="$(date +%s)"
if [ "$DOMAIN_MODE" -eq 0 ]; then
  CLEAN_SQL_INPUT="$PLAN_SQL"
else
  DOMAIN_TABLE_CSV="$(IFS=,; printf '%s' "${TRUNCATE_TABLES[*]}")"
  CLEAN_SQL_INPUT="$(mktemp)"
  awk -v allowed="$DOMAIN_TABLE_CSV" '
    BEGIN { n=split(allowed, a, ","); for (i=1; i<=n; i++) keep[a[i]]=1 }
    /^TRUNCATE[[:space:]]/ { t=$2; sub(/;$/, "", t); if (!keep[t]) next }
    /^DELETE[[:space:]]+FROM[[:space:]]/ { next }
    { print }
  ' "$PLAN_SQL" > "$CLEAN_SQL_INPUT"
fi
CLEAN_ERR="$(mktemp)"
if ! "${MYSQL[@]}" "$DB_NAME" < "$CLEAN_SQL_INPUT" 2> "$CLEAN_ERR"; then
  sed 's/^/    /' "$CLEAN_ERR" >&2; rm -f "$CLEAN_ERR"
  [ "$DOMAIN_MODE" -eq 1 ] && rm -f "$CLEAN_SQL_INPUT"
  die "清理执行失败。回滚参考: mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME < $BK_FILE
    （备份 md5 $BK_MD5）"
fi
[ "$DOMAIN_MODE" -eq 1 ] && rm -f "$CLEAN_SQL_INPUT"
rm -f "$CLEAN_ERR"
ok "清理执行完成（$(($(date +%s) - START))s）"

# ── 6. 库外留痕（脚本会清空 sys_oper_log，库里留不下痕迹）───────────────────
LOG="$BACKUP_DIR/clean-test-data-log.txt"
printf '%s 清理测试数据 由 %s 执行 | 库 %s | 脚本 00_clean_test_data.sql | TRUNCATE %s 张(%s 行) + DELETE %s 条 | 备份 %s md5=%s | include_products=%s domains=%s\n' \
  "$(date '+%Y-%m-%d %H:%M')" "${AI_AGENT:-Hermes Agent}" "$DB_NAME" \
  "${#TRUNCATE_TABLES[@]}" "$TRUNC_TOTAL" "${DEL_TOTAL:-0}" "$(basename "$BK_FILE")" "$BK_MD5" "$INCLUDE_PRODUCTS" "${DOMAIN_LABEL:-all}" >> "$LOG"
ok "留痕: $LOG"

if [ "$INCLUDE_PRODUCTS" -eq 1 ]; then
  REMAINING=0
  for t in "${PRODUCT_TABLES[@]}"; do
    n="$(db_count "SELECT COUNT(*) FROM \`$t\`;")" || exit 1
    say "产品清理核验 $t: $n 行（期望 0）"
    REMAINING=$((REMAINING + n))
  done
  n="$(db_count "SELECT COUNT(*) FROM inventory_item WHERE item_type = 'PRODUCT';")" || exit 1
  say "产品清理核验 inventory_item/PRODUCT: $n 行（期望 0）"
  REMAINING=$((REMAINING + n))
  [ "$REMAINING" -eq 0 ] || die "清理已执行，但产品残留核验失败；请检查引用保护保留的记录。执行记录见 $LOG"
  ok "产品清理核验通过：产品资料及产品库存身份均为零"
fi
