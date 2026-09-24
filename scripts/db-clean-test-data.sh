#!/usr/bin/env bash
# ============================================================================
# JJX 清理测试数据唯一入口（jjx-docs/sql/00_clean_test_data.sql）
#
#   bash scripts/db-clean-test-data.sh                # 只读体检（默认，不写库）
#   bash scripts/db-clean-test-data.sh --execute      # 真执行：须在终端手工输入库名确认
#
# 固定顺序（不可跳过）：只读体检 → 全库备份 → 人工确认 → 才执行。
# 人工确认 = 手工输入库名（jjx_erp_db）。非终端（agent/管道）一律拒绝执行——因为
# 00_clean_test_data.sql 是整表 TRUNCATE，且它自己会清空 sys_oper_log，库里留不下痕迹。
# 规范出处：jjx-docs/standards/CONVENTIONS.md §2（先备份再动库）/ §5。
# 危险等级：🟢 无参数=只读体检（不写库）／🔴 --execute 真清理（固定顺序：体检 → 全库备份 → 人工确认 → 执行）
# 前置：--execute 必须在终端手工执行（agent/管道一律拒绝）；确认方式=手输库名 jjx_erp_db；JJX_BACKUP_DIR 可写
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
BACKUP_DIR="${JJX_BACKUP_DIR:-$REPO_ROOT/jjx-docs/sql/backups}"
SQL_FILE="$REPO_ROOT/jjx-docs/sql/00_clean_test_data.sql"
MANIFEST="$REPO_ROOT/jjx-docs/sql/init/init-subset-tables.txt"
EXECUTE=0

c_red=$'\033[31m'; c_grn=$'\033[32m'; c_yel=$'\033[33m'; c_off=$'\033[0m'
say()  { printf '%s\n' "$*"; }
die()  { printf '%s✘ %s%s\n' "$c_red" "$*" "$c_off" >&2; exit 1; }
ok()   { printf '%s✓%s %s\n' "$c_grn" "$c_off" "$*"; }
warn() { printf '%s⚠%s %s\n' "$c_yel" "$c_off" "$*"; }

usage() {
  cat <<'EOF'
用途: 清理测试数据（jjx-docs/sql/00_clean_test_data.sql 的唯一入口；整表 TRUNCATE + 1 条 DELETE，表清单以脚本实际解析为准）
危险等级: 🟢 无参数=只读体检（不写库）／🔴 --execute 真清理（体检 → 全库备份 → 人工确认 → 执行）
前置: --execute 必须在终端手工执行（agent/管道一律拒绝）；确认方式=手工输入库名 jjx_erp_db；JJX_BACKUP_DIR（默认仓库内 jjx-docs/sql/backups/）可写
用法:
  bash scripts/db-clean-test-data.sh             只读体检：打印本次将删除多少行 + 顺带跑快照校验
  bash scripts/db-clean-test-data.sh --execute   真执行（须在终端手输库名确认）
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
    *) die "未知参数: $1（用法见 $0 --help）" ;;
  esac
done

# ── 0. 前置守卫 ────────────────────────────────────────────────────────────
# 2026-09-22 用户改口径：备份落仓库外 ~/jjx-backups/（仓库内只留索引 backup-index.tsv），原「必须在仓库外/内」的守卫不再需要
[ -f "$SQL_FILE" ] || die "清理脚本不存在: $SQL_FILE"
[ "$DB_NAME" = "jjx_erp_db" ] || die "目标库必须是 jjx_erp_db（当前: $DB_NAME）——防误连"
if [ "$EXECUTE" -eq 1 ] && [ ! -t 0 ]; then
  die "真执行需要人工确认：请在终端里手工运行本脚本（agent/管道调用一律拒绝）"
fi

export MYSQL_PWD="$DB_PASS"
MYSQL=(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 -N -B)
"${MYSQL[@]}" -e "SELECT 1" >/dev/null 2>&1 \
  || die "连不上数据库 $DB_NAME@$DB_HOST:$DB_PORT（检查服务与账号）"

# ── 1. 解析清理目标（只认整行语句，注释掉的忽略）──────────────────────────────
TRUNCATE_TABLES=()
DELETE_TABLES=()
DELETE_WHERES=()
while IFS= read -r line; do
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
done < "$SQL_FILE"
[ "${#TRUNCATE_TABLES[@]}" -gt 0 ] || die "未能从 $SQL_FILE 解析出 TRUNCATE 目标，请核对脚本格式"

# ── 2. 只读体检 ────────────────────────────────────────────────────────────
SQL_BYTES="$(stat -c%s "$SQL_FILE")"
say "══ 清理体检（只读，未写库） ══"
say "目标库: $DB_NAME@$DB_HOST:$DB_PORT"
say "脚本  : jjx-docs/sql/00_clean_test_data.sql（$SQL_BYTES 字节）"
say ""

say "① TRUNCATE 组：${#TRUNCATE_TABLES[@]} 张表将被清空"
TRUNC_TOTAL=0
TRUNC_NONZERO=0
for t in "${TRUNCATE_TABLES[@]}"; do
  n="$("${MYSQL[@]}" "$DB_NAME" -e "SELECT COUNT(*) FROM \`$t\`;" 2>/dev/null || echo "")"
  if ! [[ "$n" =~ ^[0-9]+$ ]]; then
    warn "   $t: 读取失败（表可能不存在，执行时会报错）"
    continue
  fi
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
say ""

if [ "${#DELETE_TABLES[@]}" -gt 0 ]; then
  say "② DELETE 组：${#DELETE_TABLES[@]} 张表按条件删除"
  DEL_TOTAL=0
  for i in "${!DELETE_TABLES[@]}"; do
    t="${DELETE_TABLES[$i]}"
    w="${DELETE_WHERES[$i]}"
    n="$("${MYSQL[@]}" "$DB_NAME" -e "SELECT COUNT(*) FROM \`$t\` WHERE $w;" 2>/dev/null || echo "")"
    all="$("${MYSQL[@]}" "$DB_NAME" -e "SELECT COUNT(*) FROM \`$t\`;" 2>/dev/null || echo "")"
    if ! [[ "$n" =~ ^[0-9]+$ ]]; then
      warn "   $t: 条件计数失败 → 执行时会由 MySQL 报错"
      continue
    fi
    if ! [[ "$all" =~ ^[0-9]+$ ]]; then
      warn "   $t: 总数读取失败，仅报将删 $n 条"
      say "   $t: 将删 $n 条"
      DEL_TOTAL=$((DEL_TOTAL + n))
      continue
    fi
    DEL_TOTAL=$((DEL_TOTAL + n))
    say "   $t: 将删 $n 条（保留 $((all - n)) 条）"
    say "      条件: WHERE $w"
  done
  say "   合计将删除 $DEL_TOTAL 条"
  say ""
fi

# 与初始化清单交叉（交付物范围被清理 = 高风险，必须显式提醒）
CROSS=()
if [ -f "$MANIFEST" ]; then
  while IFS= read -r l; do
    [[ "$l" =~ ^[[:space:]]*$ ]] && continue
    [[ "$l" =~ ^[[:space:]]*# ]] && continue
    l="${l//[[:space:]]/}"
    for t in "${TRUNCATE_TABLES[@]}" "${DELETE_TABLES[@]:-}"; do
      if [ "$t" = "$l" ]; then
        CROSS+=("$t")
      fi
    done
  done < "$MANIFEST"
fi
if [ "${#CROSS[@]}" -gt 0 ]; then
  warn "③ 与初始化清单交叉：${#CROSS[@]} 张（清理会影响到交付物范围）"
  for t in "${CROSS[@]}"; do say "   - $t"; done
else
  say "③ 与初始化清单交叉：0 张（本次清理不涉及交付物 19 表）"
fi

# ── 覆盖率校验：库里每张表都必须有归宿（TRUNCATE / DELETE / 保留白名单）────────
# 起因：inventory_iqc_batch 由迁移 136 新建后未同步清理清单 → 清理时批次行残留成孤儿，
# 明细 id 复用后又错挂到新单（2026-09-21，任务 dev-20260921-024）。
# 白名单对应 00_clean_test_data.sql 第 12 节「保留」：新增/下线保留表时两边同步。
RETAINED_TABLES=(
  sys_user sys_role sys_menu sys_role_menu sys_user_role sys_dept
  sys_config sys_dict sys_dict_item sys_event_config sys_event_config_bak_20260814
  sys_event_last_payload
  quality_template_registry quality_sampling_plan
  engineering_standard_process engineering_process_icon_sample
  sales_customer purchase_supplier inventory_material inventory_material_category
  inventory_warehouse inventory_item
  product product_category product_config_model product_config_option
  sys_tag sys_tag_rel hr_employee hr_dept_mapping
  engineering_die engineering_screen_frame engineering_screen_plate
)
DB_TABLES="$("${MYSQL[@]}" "$DB_NAME" -N -B -e \
  "SELECT table_name FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_type='BASE TABLE'" 2>/dev/null | sort)"
DB_COUNT="$(printf '%s\n' "$DB_TABLES" | grep -c . || true)"
UNCOVERED=()
while IFS= read -r t; do
  [ -n "$t" ] || continue
  hit=0
  for x in "${TRUNCATE_TABLES[@]}" "${DELETE_TABLES[@]:-}" "${RETAINED_TABLES[@]}"; do
    if [ "$t" = "$x" ]; then hit=1; break; fi
  done
  [ "$hit" -eq 0 ] && UNCOVERED+=("$t")
done <<< "$DB_TABLES"
if [ "${#UNCOVERED[@]}" -gt 0 ]; then
  warn "④ 覆盖率校验：${#UNCOVERED[@]} 张表既不在清理清单、也不在保留白名单（清理后会残留脏数据）"
  for t in "${UNCOVERED[@]}"; do say "   - $t"; done
  die "覆盖率校验未通过，已中止。处理：业务表→加到 00_clean_test_data.sql 对应模块段；基础档案/配置→补进 SQL 第 12 节保留清单与本脚本 RETAINED_TABLES"
else
  say "④ 覆盖率校验：库 ${DB_COUNT} 张表均有归宿（清理清单 ∪ 保留白名单）"
fi

# ── 3. 顺带跑快照最新性校验（只读；不过不拦，只提醒）────────────────────────
if [ -x "$REPO_ROOT/scripts/db-export-init-subset.sh" ]; then
  bash "$REPO_ROOT/scripts/db-export-init-subset.sh" --verify || warn "快照校验未通过（见上），不阻断清理"
else
  warn "未找到 scripts/db-export-init-subset.sh，跳过快照校验"
fi

if [ "$EXECUTE" -eq 0 ]; then
  say ""
  ok "体检完成，未写库、未执行清理"
  say "   要真执行: bash scripts/db-clean-test-data.sh --execute（在终端手工输入库名确认）"
  exit 0
fi

# ── 4. 全库备份（失败则绝不执行清理）─────────────────────────────────────────
mkdir -p "$BACKUP_DIR" || die "无法创建备份目录 $BACKUP_DIR"
TS="$(date +%Y%m%d-%H%M)"
BACKUP="$BACKUP_DIR/jjx_erp_db_backup_${TS}_before-clean-test-data.sql"
say ""
say "── 全库备份 ──"
if ! mysqldump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 \
      --single-transaction --set-gtid-purged=OFF --no-tablespaces "$DB_NAME" > "$BACKUP.raw" 2>"$BACKUP.err"; then
  rm -f "$BACKUP.raw"; sed 's/^/    /' "$BACKUP.err" >&2; rm -f "$BACKUP.err"
  die "备份失败——已中止，未执行任何清理"
fi
rm -f "$BACKUP.err"
{ printf -- '-- 备份人: %s\n-- 原因: 执行 00_clean_test_data.sql 清理测试数据前全库备份\n-- 脚本: scripts/db-clean-test-data.sh\n' \
    "${AI_AGENT:-Hermes Agent}"; cat "$BACKUP.raw"; } > "$BACKUP"
rm -f "$BACKUP.raw"
BK_SIZE="$(stat -c%s "$BACKUP" 2>/dev/null || echo 0)"
BK_TABLES="$(grep -c '^CREATE TABLE' "$BACKUP" 2>/dev/null || echo 0)"
BK_MD5="$(md5sum "$BACKUP" | awk '{print $1}')"
if [ "$BK_SIZE" -lt 1024 ] || [ "$BK_TABLES" -lt 1 ]; then
  die "备份产物异常（${BK_SIZE}B / ${BK_TABLES} 张表）——已中止，未执行清理"
fi
ok "备份 $BACKUP"
say "    $BK_SIZE 字节 / $BK_TABLES 张表 / md5 $BK_MD5"

# ── 5. 人工确认（手输库名）───────────────────────────────────────────────────
say ""
printf '人工确认：请手工输入库名以执行清理 [%s]（其他任何输入=中止）: ' "$DB_NAME"
read -r ANSWER || ANSWER=""
if [ "$ANSWER" != "$DB_NAME" ]; then
  die "确认失败（输入与库名不一致）——已中止，未执行清理。备份在 $BACKUP（md5 $BK_MD5）"
fi

# ── 6. 执行清理 ────────────────────────────────────────────────────────────
say ""
say "── 执行清理 ──"
START="$(date +%s)"
if ! "${MYSQL[@]}" "$DB_NAME" < "$SQL_FILE" 2> "$BACKUP.cleanerr"; then
  sed 's/^/    /' "$BACKUP.cleanerr" >&2; rm -f "$BACKUP.cleanerr"
  die "清理执行失败。回滚参考: mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME < $BACKUP
    （备份 md5 $BK_MD5）"
fi
rm -f "$BACKUP.cleanerr"
ok "清理执行完成（$(($(date +%s) - START))s）"

# ── 7. 库外留痕（脚本会清空 sys_oper_log，库里留不下痕迹）───────────────────
LOG="$BACKUP_DIR/clean-test-data-log.txt"
printf '%s 清理测试数据 由 %s 执行 | 库 %s | 脚本 00_clean_test_data.sql | TRUNCATE %s 张(%s 行) + DELETE %s 条 | 备份 %s md5=%s\n' \
  "$(date '+%Y-%m-%d %H:%M')" "${AI_AGENT:-Hermes Agent}" "$DB_NAME" \
  "${#TRUNCATE_TABLES[@]}" "$TRUNC_TOTAL" "${DEL_TOTAL:-0}" "$(basename "$BACKUP")" "$BK_MD5" >> "$LOG"
ok "留痕: $LOG"

say ""
say "提示：清理后若初始化快照出现差异，按滚动机制重出（登记任务 → 确认 → bash scripts/db-export-init-subset.sh --task dev-YYYYMMDD-NNN）"
