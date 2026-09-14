#!/usr/bin/env bash
# 按固定清单导出 JJX 初始化数据子集（只读数据库）
#   bash scripts/db-export-init-subset.sh --task dev-YYYYMMDD-NNN [--dry-run]
#   bash scripts/db-export-init-subset.sh --verify        # 只读校验：最新快照 vs 现库
#   bash scripts/db-export-init-subset.sh --help
# 危险等级：🟡 只读库 + 写仓库文件（产出新快照，可回退）；--verify 为 🟢 纯只读
# 前置：清单 jjx-docs/sql/init/init-subset-tables.txt 存在且表都在库里；--task 必须是真实任务码
# 手册：jjx-docs/guides/scripts-commands-20260914.md
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  printf '✘ 无法定位 Git 仓库根目录\n' >&2
  exit 1
}
cd "$REPO_ROOT"

DB_HOST="${JJX_DB_HOST:-127.0.0.1}"
DB_PORT="${JJX_DB_PORT:-3306}"
DB_NAME="${JJX_DB_NAME:-jjx_erp_db}"
DB_USER="${JJX_DB_USER:-root}"
DB_PASSWORD="${JJX_DB_PASSWORD:-123456}"
TABLES_FILE="${JJX_INIT_SUBSET_TABLES_FILE:-$REPO_ROOT/jjx-docs/sql/init/init-subset-tables.txt}"
OUT_DIR="$REPO_ROOT/jjx-docs/sql/init"
TASK=""
DRY_RUN=0
VERIFY=0
VERIFY_RC=0

c_red=$'\033[31m'; c_grn=$'\033[32m'; c_yel=$'\033[33m'; c_off=$'\033[0m'
say()  { printf '%s\n' "$*"; }
die()  { printf '%s✘ %s%s\n' "$c_red" "$*" "$c_off" >&2; exit 1; }
ok()   { printf '%s✓%s %s\n' "$c_grn" "$c_off" "$*"; }
warn() { printf '%s⚠%s %s\n' "$c_yel" "$c_off" "$*"; }

usage() {
  cat <<'EOF'
用途: 按清单导出 JJX 初始化数据子集快照（给新环境开账用；不是全库备份），或只读校验快照是否最新
危险等级: 🟡 只读库 + 写仓库文件（产出新快照，可回退）；--verify 为 🟢 纯只读
前置: 清单 jjx-docs/sql/init/init-subset-tables.txt 存在，且清单里的表都在库里（有已下线的表会直接中止）；--task 必须是真实任务码
用法:
  bash scripts/db-export-init-subset.sh --task dev-YYYYMMDD-NNN            重出一份新快照
  bash scripts/db-export-init-subset.sh --task dev-YYYYMMDD-NNN --dry-run  只体检，不落文件
  bash scripts/db-export-init-subset.sh --verify                          只读校验：✓最新 / ⚠行数有差异 / ✘缺表
可选: --out-dir <目录>（默认 jjx-docs/sql/init）  -h|--help
退出码: 0=成功或快照一致  1=失败或有差异
手册: jjx-docs/guides/scripts-commands-20260914.md
EOF
}

# ── 快照行数解析：按 INSERT 段数元组（1 表 1 行输出「表名<TAB>行数」）────────
snapshot_rows() {
  python3 - "$1" <<'PY'
import re, sys
txt = open(sys.argv[1], encoding='utf-8', errors='replace').read()
counts = {}
for m in re.finditer(r'INSERT INTO `([A-Za-z0-9_]+)`(?: \([^)]*\))?\s*VALUES(.*?);\n', txt, re.S):
    vals = m.group(2)
    depth = 0; n = 0; instr = False; i = 0
    while i < len(vals):
        c = vals[i]
        if instr:
            if c == '\\':
                i += 2; continue
            if c == "'":
                instr = False
        else:
            if c == "'":
                instr = True
            elif c == '(':
                if depth == 0:
                    n += 1
                depth += 1
            elif c == ')':
                depth -= 1
        i += 1
    counts[m.group(1)] = counts.get(m.group(1), 0) + n
for t in counts:
    print('%s\t%d' % (t, counts[t]))
PY
}

# ── --verify：只读校验「最新快照 vs 现库」，不落任何文件 ────────────────────
# 三态：✓ 最新（0 退出）／⚠ 行数有差异（快照过期，1 退出）／✘ 缺表或表集合不一致（1 退出）
# 只印汇总：一致时一行结论；有差异才逐表列出。
verify_snapshot() {
  local snap snap_md5 snap_tables manifest_tables t n now delta
  local snap_total=0 now_total=0 diff_tables=0
  local -A SNAP_ROWS=()

  say ""
  say "── 快照校验（只读） ──"

  snap="$(find "$OUT_DIR" -maxdepth 1 -type f -name 'jjx_erp_db_backup_*_init-data-subset.sql' -print | sort | tail -1)"
  if [ -z "$snap" ]; then
    warn "未找到 init-data-subset 快照（$OUT_DIR 下为空），无法校验"
    VERIFY_RC=1
    return 0
  fi
  snap_md5="$(md5sum "$snap" | awk '{print $1}')"
  say "最新快照: $(basename "$snap")"
  say "          md5 $snap_md5"

  # ① 清单里的表是否都在库里
  if [ "${#MISSING[@]}" -gt 0 ]; then
    warn "清单里有 ${#MISSING[@]} 张表在库中不存在："
    for t in "${MISSING[@]}"; do say "   - $t"; done
    VERIFY_RC=1
    return 0
  fi

  # ② 快照的表集合 vs 清单表集合（按 CREATE TABLE 取，空表也算）
  snap_tables="$(grep -o '^CREATE TABLE `[A-Za-z0-9_]*`' "$snap" | sed -E 's/^CREATE TABLE `([A-Za-z0-9_]*)`/\1/' | sort)"
  manifest_tables="$(printf '%s\n' "${TABLES[@]}" | sort)"
  if [ -z "$snap_tables" ]; then
    warn "无法从快照解析出表结构（文件异常？）"
    VERIFY_RC=1
    return 0
  fi
  if [ "$snap_tables" != "$manifest_tables" ]; then
    warn "快照的表集合与清单不一致："
    for t in $(comm -23 <(printf '%s\n' "$snap_tables") <(printf '%s\n' "$manifest_tables")); do
      say "   只在快照里: $t"
    done
    for t in $(comm -13 <(printf '%s\n' "$snap_tables") <(printf '%s\n' "$manifest_tables")); do
      say "   只在清单里: $t"
    done
    VERIFY_RC=1
    return 0
  fi

  # ③ 逐表行数 diff（现库 vs 快照）
  while IFS=$'\t' read -r t n; do
    [ -n "$t" ] || continue
    SNAP_ROWS["$t"]="$n"
  done < <(snapshot_rows "$snap")

  for t in "${TABLES[@]}"; do
    n="${SNAP_ROWS[$t]:-0}"
    now="$("${MYSQL[@]}" "$DB_NAME" -e "SELECT COUNT(*) FROM \`$t\`;")"
    [[ "$now" =~ ^[0-9]+$ ]] || die "无法读取表 $t 的当前行数"
    snap_total=$((snap_total + n))
    now_total=$((now_total + now))
    if [ "$n" != "$now" ]; then
      delta=$((now - n))
      if [ "$delta" -gt 0 ]; then delta="+$delta"; fi
      say "   ⚠ $t  快照 $n → 现库 $now ($delta)"
      diff_tables=$((diff_tables + 1))
    fi
  done

  if [ "$diff_tables" -eq 0 ]; then
    ok "快照是最新的（${#TABLES[@]} 表 / $now_total 行，与现库一致）"
  else
    warn "快照已过期：$diff_tables 张表行数不一致（快照 $snap_total → 现库 $now_total）"
    say "   需要最新数据时按滚动机制重出: bash scripts/db-export-init-subset.sh --task dev-YYYYMMDD-NNN"
    VERIFY_RC=1
  fi
  return 0
}

while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --task)
      [ $# -ge 2 ] || die "--task 缺少值"
      TASK="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --verify)
      VERIFY=1
      shift
      ;;
    --out-dir)
      [ $# -ge 2 ] || die "--out-dir 缺少值"
      OUT_DIR="$2"
      shift 2
      ;;
    *) die "未知参数: $1" ;;
  esac
done

if [ "$VERIFY" -eq 0 ]; then
  [[ "$TASK" =~ ^dev-[0-9]{8}-[0-9]{3}$ ]] \
    || die "--task 必填，格式必须为 dev-YYYYMMDD-NNN"
fi
[ -f "$TABLES_FILE" ] || die "表清单不存在: $TABLES_FILE"
[ -d "$OUT_DIR" ] || die "输出目录不存在: $OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

TABLES=()
while IFS= read -r line || [ -n "$line" ]; do
  line="${line%$'\r'}"
  [[ "$line" =~ ^[[:space:]]*$ ]] && continue
  [[ "$line" =~ ^[[:space:]]*# ]] && continue
  line="${line#"${line%%[![:space:]]*}"}"
  line="${line%"${line##*[![:space:]]}"}"
  [[ "$line" =~ ^[A-Za-z0-9_]+$ ]] || die "清单含非法表名: $line"
  TABLES+=("$line")
done < "$TABLES_FILE"
[ "${#TABLES[@]}" -gt 0 ] || die "表清单为空: $TABLES_FILE"

export MYSQL_PWD="$DB_PASSWORD"
MYSQL=(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 -N -B)
"${MYSQL[@]}" -e "SELECT 1" >/dev/null 2>&1 \
  || die "连不上数据库 $DB_NAME@$DB_HOST:$DB_PORT（检查服务与账号）"

sql_string() { printf '%s' "$1" | sed "s/'/''/g"; }
DB_SQL="$(sql_string "$DB_NAME")"
MISSING=()
for table in "${TABLES[@]}"; do
  present="$("${MYSQL[@]}" information_schema -e \
    "SELECT COUNT(*) FROM tables WHERE table_schema='$DB_SQL' AND table_name='$(sql_string "$table")';")"
  [ "$present" = "1" ] || MISSING+=("$table")
done

if [ "$VERIFY" -eq 1 ]; then
  verify_snapshot
  exit "$VERIFY_RC"
fi

if [ "${#MISSING[@]}" -gt 0 ]; then
  say "清单中的以下表不存在：" >&2
  for table in "${MISSING[@]}"; do say "  - $table" >&2; done
  die "存在 ${#MISSING[@]} 张缺失表，已中止；未生成导出文件"
fi

say "清单表数: ${#TABLES[@]}"
TOTAL_ROWS=0
for table in "${TABLES[@]}"; do
  rows="$("${MYSQL[@]}" "$DB_NAME" -e "SELECT COUNT(*) FROM \`$table\`;")"
  [[ "$rows" =~ ^[0-9]+$ ]] || die "无法读取表 $table 的准确行数"
  say "  $table: $rows"
  TOTAL_ROWS=$((TOTAL_ROWS + rows))
done
say "合计行数: $TOTAL_ROWS"

if [ "$DRY_RUN" -eq 1 ]; then
  ok "dry-run 体检通过，未生成任何文件"
  exit 0
fi

TS="$(date +%Y%m%d-%H%M)"
OUTPUT="$OUT_DIR/jjx_erp_db_backup_${TS}_init-data-subset.sql"
[ ! -e "$OUTPUT" ] || die "输出文件已存在，拒绝覆盖: $OUTPUT"
TABLE_LIST="$(printf '%s ' "${TABLES[@]}")"
TABLE_LIST="${TABLE_LIST% }"

{
  printf -- '-- 备份人: Hermes Agent\n'
  printf -- '-- 原因: 数据初始化（业务数据迁移）—— 用户已确认进入初始化脚本的表数据子集备份\n'
  printf -- '-- 依据: jjx-docs/sql/init/DECISIONS.md + 清单 jjx-docs/sql/init/init-subset-tables.txt\n'
  printf -- '-- 任务码: %s\n' "$TASK"
  printf -- '-- 表清单(%s 表): %s\n' "${#TABLES[@]}" "$TABLE_LIST"
  mysqldump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" \
    --single-transaction --skip-lock-tables --default-character-set=utf8mb4 \
    --no-tablespaces --skip-add-locks "$DB_NAME" "${TABLES[@]}"
} > "$OUTPUT"

CREATE_COUNT="$(grep -c '^CREATE TABLE' "$OUTPUT" || true)"
if [ "$CREATE_COUNT" -ne "${#TABLES[@]}" ]; then
  die "导出自检失败：CREATE TABLE 数为 $CREATE_COUNT，清单表数为 ${#TABLES[@]}（文件保留供排查: $OUTPUT）"
fi

BYTES="$(stat -c%s "$OUTPUT")"
MD5="$(md5sum "$OUTPUT" | awk '{print $1}')"
say ""
say "════ 导出完成 ════"
say "文件绝对路径: $OUTPUT"
say "字节数: $BYTES"
say "md5: $MD5"
say "表数: ${#TABLES[@]}"
say "总行数: $TOTAL_ROWS"
say "CREATE TABLE 自检: $CREATE_COUNT"
say "旧份文件："
old_count=0
while IFS= read -r old_file; do
  [ "$old_file" = "$OUTPUT" ] && continue
  say "  $(basename "$old_file")"
  old_count=$((old_count + 1))
done < <(find "$OUT_DIR" -maxdepth 1 -type f \
  -name 'jjx_erp_db_backup_*_init-data-subset.sql' -print | sort)
[ "$old_count" -gt 0 ] || say "  （无）"
warn "旧份保留未删除，确认后自行删除（删除落在 pre-commit 闸门保护范围，提交需 git commit --no-verify）"
