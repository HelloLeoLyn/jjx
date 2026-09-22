#!/usr/bin/env bash
# ============================================================================
# JJX 全库备份（独立入口）—— 不跑迁移、不清数据，只想"随手拍一份全库快照"时用
#
#   bash scripts/db-backup.sh                          # 例行全库备份
#   bash scripts/db-backup.sh --tag before-xxx          # 带原因标签
#   bash scripts/db-backup.sh --task dev-YYYYMMDD-NNN   # 记录关联任务码到文件头
#   bash scripts/db-backup.sh --dry-run                 # 只看路径/将清理什么，不落盘
#   bash scripts/db-backup.sh --no-clean                # 备份但不清理过期
#   bash scripts/db-backup.sh --exclude-table hr_employee  # 导出时排除指定表（可重复）
#   bash scripts/db-backup.sh --help
#
# 危险等级：🟡 只读数据库 + 写备份文件（不写库、不改库内数据）；--dry-run 为 🟢 纯预览
# 前置：mysqldump 可用；数据库可达；JJX_BACKUP_DIR 可写（默认仓库外 ~/jjx-backups/，2026-09-22 改口径）
# 手册：jjx-docs/guides/scripts-commands-20260914.md
#
# 定位（与其它脚本的关系，别用错）：
#   - 迁移             → scripts/db-migrate.sh（内部自带全库备份，本脚本不替代它）
#   - 清测试数据       → scripts/db-clean-test-data.sh（内部自带全库备份）
#   - 初始化数据子集   → scripts/db-export-init-subset.sh（那不是备份，是开账交付物）
#   - 只想立刻拿一份全库快照（不触发任何库内变更）→ 本脚本
#
# 命名/留痕（CONVENTIONS §2）：jjx_erp_db_backup_YYYYMMDD-HHmm[_tag].sql
#   文件头 1~3 行写明 备份人 / 原因 / 任务码；执行后校验 md5 + 表数 + 字节数；
#   保留策略：超过 --keep-days（默认 14 天）删除；并自动做“每日只留最新一份”去重。
#   备份落仓库外 ~/jjx-backups/；仓库内只追加索引 jjx-docs/sql/backups/backup-index.tsv。
# 环境覆盖：DB_HOST DB_PORT DB_USER DB_PASS DB_NAME / JJX_BACKUP_DIR / AI_AGENT
# ============================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"
DB_NAME="${DB_NAME:-jjx_erp_db}"
BACKUP_DIR="${JJX_BACKUP_DIR:-$HOME/jjx-backups}"
AGENT="${AI_AGENT:-dahuang}"
INDEX_FILE="${JJX_BACKUP_INDEX:-$REPO_ROOT/jjx-docs/sql/backups/backup-index.tsv}"

TAG=""
TASK=""
REASON=""
EXCLUDES=()
KEEP_DAYS=14
DO_CLEAN=1
DRY_RUN=0

c_red=$'\033[31m'; c_grn=$'\033[32m'; c_yel=$'\033[33m'; c_off=$'\033[0m'
say()  { printf '%s\n' "$*"; }
die()  { printf '%s✘ %s%s\n' "$c_red" "$*" "$c_off" >&2; exit 1; }
ok()   { printf '%s✓%s %s\n' "$c_grn" "$c_off" "$*"; }
warn() { printf '%s⚠%s %s\n' "$c_yel" "$c_off" "$*"; }

usage() {
  cat <<'EOF'
用途: 立刻做一份全库备份（只读库，不动任何库内数据），产物落在 JJX_BACKUP_DIR
危险等级: 🟡 只读数据库 + 写备份文件；--dry-run 为 🟢 纯预览（不落盘）
前置: mysqldump 可用；数据库可达；JJX_BACKUP_DIR（默认仓库外 ~/jjx-backups/）可写
用法:
  bash scripts/db-backup.sh [选项]

选项:
  --tag <tag>        原因标签（简短英文，[A-Za-z0-9._-]，如 before-xxx、daily）；缺省=例行备份
  --task <code>      关联任务码 dev-YYYYMMDD-NNN（写进文件头，便于回溯）
  --reason <text>    自定义"原因"文案（默认按 tag 自动生成）
  --exclude-table <表名>  导出时排除该表（可重复；如 hr_employee），内部转 mysqldump --ignore-table
  --out-dir <dir>    指定输出目录（覆盖 JJX_BACKUP_DIR；默认 ~/jjx-backups/）
  --keep-days <N>    过期清理阈值，默认 14 天
  --no-clean         本次不清理过期备份
  --dry-run          只打印将写入的路径与将清理的文件，不真正备份
  -h, --help         本帮助

产物: jjx_erp_db_backup_YYYYMMDD-HHmm[_tag].sql（同名冲突自动加 -2/-3 后缀，不覆盖）
注意: --exclude-table 导出的**不是**可完整恢复的全库备份，只当剪裁快照用
留痕: 追加一行到 <备份目录>/db-backup-log.txt（时间/备份人/文件/md5/字节/表数/任务码）
清理: 仅清理 <备份目录> 下 jjx_erp_db_backup_*.sql 且超过 --keep-days 的文件；
      其它类型的备份（guard 表级备份、tar.gz 等）只提示、不删。
退出码: 0=备份成功；1=前置不满足/备份失败/产物异常
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --tag)       TAG="${2:-}"; shift 2 ;;
    --task)      TASK="${2:-}"; shift 2 ;;
    --reason)    REASON="${2:-}"; shift 2 ;;
    --exclude-table) EXCLUDES+=("${2:-}"); shift 2 ;;
    --out-dir)   BACKUP_DIR="${2:-}"; shift 2 ;;
    --keep-days) KEEP_DAYS="${2:-}"; shift 2 ;;
    --no-clean)  DO_CLEAN=0; shift ;;
    --dry-run)   DRY_RUN=1; shift ;;
    -h|--help)   usage; exit 0 ;;
    *) die "未知参数：$1（用 --help 看用法）" ;;
  esac
done

# ── 前置检查 ───────────────────────────────────────────────────────────────
# 2026-09-21 用户改口径：备份统一落仓库内 jjx-docs/sql/backups/，原「必须在仓库外」守卫已移除

if [ -n "$TAG" ]; then
  printf '%s' "$TAG" | grep -Eq '^[A-Za-z0-9._-]+$' \
    || die "--tag 只允许 [A-Za-z0-9._-]（当前：$TAG）"
fi
if [ -n "$TASK" ]; then
  printf '%s' "$TASK" | grep -Eq '^dev-[0-9]{8}-[0-9]{3,}$' \
    || die "--task 必须是任务码 dev-YYYYMMDD-NNN（当前：$TASK）"
fi
case "$KEEP_DAYS" in
  ''|*[!0-9]*) die "--keep-days 必须是正整数（当前：$KEEP_DAYS）" ;;
esac

# 排除表：允许写 table 或 db.table，统一归一到表名
IGNORE_ARGS=()
for t in ${EXCLUDES[@]+${EXCLUDES[@]}}; do
  t="${t##*.}"
  printf '%s' "$t" | grep -Eq '^[A-Za-z0-9_]+$' || die "--exclude-table 表名不合法：$t"
  IGNORE_ARGS+=(--ignore-table="${DB_NAME}.${t}")
done

command -v mysqldump >/dev/null 2>&1 || die "找不到 mysqldump（Debian/Ubuntu: apt install mysql-client）"

export MYSQL_PWD="$DB_PASS"
if ! mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --connect-timeout=5 -N -B \
      -e "SELECT 1" "$DB_NAME" >/dev/null 2>&1; then
  die "连不上数据库 ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}（库没起/账号密码不对？）"
fi
# MYSQL_PWD 保持导出：下面的 mysqldump 也靠它免密码（与 db-migrate.sh 同口径）

if [ ! -d "$BACKUP_DIR" ]; then
  if [ "$DRY_RUN" -eq 1 ]; then
    warn "备份目录不存在，真跑时会创建：$BACKUP_DIR"
  else
    mkdir -p "$BACKUP_DIR" || die "无法创建备份目录：$BACKUP_DIR"
  fi
elif [ ! -w "$BACKUP_DIR" ]; then
  die "备份目录不可写：$BACKUP_DIR"
fi

# ── 目标文件名（不覆盖已有文件）────────────────────────────────────────────
TS="$(date +%Y%m%d-%H%M)"
TAGSUF=""; [ -n "$TAG" ] && TAGSUF="_${TAG}"
BASE="jjx_erp_db_backup_${TS}${TAGSUF}"
TARGET="$BACKUP_DIR/${BASE}.sql"
if [ -e "$TARGET" ]; then
  n=2
  while [ -e "$BACKUP_DIR/${BASE}-${n}.sql" ]; do n=$((n + 1)); done
  TARGET="$BACKUP_DIR/${BASE}-${n}.sql"
  warn "同名备份已存在，本次写入：$(basename "$TARGET")"
fi

if [ -z "$REASON" ]; then
  if [ -n "$TAG" ]; then REASON="全库备份（${TAG}）"; else REASON="例行全库备份"; fi
fi

say "── 全库备份 ──"
say "  库:     ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
say "  目录:   ${BACKUP_DIR}"
say "  文件:   $(basename "$TARGET")"
say "  原因:   ${REASON}"
say "  任务码: ${TASK:-无}"
if [ "${#IGNORE_ARGS[@]}" -gt 0 ]; then
  warn "排除表: $(printf '%s ' "${EXCLUDES[@]}")（导出结果不是可完整恢复的全库备份）"
fi
say "  清理:   $([ "$DO_CLEAN" -eq 1 ] && printf '删除超过 %s 天的全库备份' "$KEEP_DAYS" || printf '本次不清理')"
say ""

if [ "$DRY_RUN" -eq 1 ]; then
  warn "--dry-run：未写任何文件，未动数据库"
else
  RAW="${TARGET}.raw"
  if ! mysqldump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 \
        --single-transaction --set-gtid-purged=OFF --no-tablespaces \
        ${IGNORE_ARGS[@]+"${IGNORE_ARGS[@]}"} "$DB_NAME" \
        > "$RAW" 2>"${RAW}.err"; then
    sed 's/^/    /' "${RAW}.err" >&2; rm -f "$RAW" "${RAW}.err"
    die "备份失败——未产出文件"
  fi
  rm -f "${RAW}.err"
  { printf -- "-- 备份人: %s\n-- 原因: %s\n-- 任务码: %s\n-- 生成: %s | 库 %s@%s:%s/%s | 脚本 scripts/db-backup.sh\n" \
      "$AGENT" "$REASON" "${TASK:-无}" "$(date '+%Y-%m-%d %H:%M:%S %z')" \
      "$DB_USER" "$DB_HOST" "$DB_PORT" "$DB_NAME"
    cat "$RAW"
  } > "$TARGET" || { rm -f "$RAW"; die "写入备份文件失败：$TARGET"; }
  rm -f "$RAW"

  BK_SIZE="$(stat -c%s "$TARGET" 2>/dev/null || echo 0)"
  BK_TABLES="$(grep -c '^CREATE TABLE' "$TARGET" 2>/dev/null || echo 0)"
  if [ "$BK_SIZE" -lt 1024 ] || [ "$BK_TABLES" -lt 1 ]; then
    die "备份产物异常（${BK_SIZE}B / ${BK_TABLES} 张表）——请检查 mysqldump 输出：$TARGET"
  fi
  BK_MD5="$(md5sum "$TARGET" | cut -d' ' -f1)"
  ok "备份完成：$TARGET"
  say "    ${BK_SIZE} 字节 / ${BK_TABLES} 张表 / md5 ${BK_MD5}"
  say "    恢复参考: mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME < $TARGET"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$(date '+%Y-%m-%d %H:%M:%S')" "$AGENT" "$(basename "$TARGET")" "$BK_MD5" "$BK_SIZE" "$BK_TABLES" "${TASK:-无}" \
    >> "$BACKUP_DIR/db-backup-log.txt" 2>/dev/null || warn "写留痕日志失败：$BACKUP_DIR/db-backup-log.txt"
  mkdir -p "$(dirname "$INDEX_FILE")" 2>/dev/null || true
  [ -f "$INDEX_FILE" ] || printf 'time\tkind\tfile\tmd5\tbytes\ttables\tagent\ttask\n' >> "$INDEX_FILE" 2>/dev/null || true
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$(date '+%Y-%m-%d %H:%M:%S')" "full" "$(basename "$TARGET")" "$BK_MD5" "$BK_SIZE" "$BK_TABLES" "$AGENT" "${TASK:-无}" \
    >> "$INDEX_FILE" 2>/dev/null || warn "写仓库索引失败：$INDEX_FILE"
fi

# ── 过期清理（只动本脚本产物；其它类型只提示）──────────────────────────────
if [ "$DO_CLEAN" -eq 1 ]; then
  say ""
  say "── 过期清理（> ${KEEP_DAYS} 天）──"
  target_name="$(basename "$TARGET")"
  deleted=0
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    [ "$(basename "$f")" = "$target_name" ] && continue
    if [ "$DRY_RUN" -eq 1 ]; then
      say "    [dry-run] 将删除 $(basename "$f") ($(du -h "$f" | cut -f1))"
    else
      rm -f "$f" && { say "    已删除 $(basename "$f")"; deleted=$((deleted + 1)); }
    fi
  done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'jjx_erp_db_backup_*.sql' -mtime +"$KEEP_DAYS" 2>/dev/null | sort)
  [ "$deleted" -eq 0 ] && [ "$DRY_RUN" -eq 0 ] && say "    无过期全库备份"

  # 每日只留最新一份（按文件名日期去重，保留 mtime 最新的一份；不碰本次产物）
  say ""
  say "── 每日去重（全库快照只留当天最新一份）──"
  cur="$(basename "$TARGET")"; seen=" "; dedup=0
  case "$cur" in
    jjx_erp_db_backup_*)
      d0="$(printf '%s' "$cur" | sed -E 's/^jjx_erp_db_backup_([0-9]{8})-.*/\1/')"
      seen=" $d0 "   # 本次产物当天的席位先占住
      ;;
  esac
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    b="$(basename "$f")"; [ "$b" = "$cur" ] && continue
    d="$(printf '%s' "$b" | sed -E 's/^jjx_erp_db_backup_([0-9]{8})-.*/\1/')"
    case "$seen" in
      *" $d "*)
        if [ "$DRY_RUN" -eq 1 ]; then say "    [dry-run] 将删除同日旧快照 $b"
        else rm -f "$f" && { say "    已删同日旧快照 $b"; dedup=$((dedup + 1)); }; fi ;;
      *) seen="$seen$d " ;;
    esac
  done < <(ls -1 "$BACKUP_DIR"/jjx_erp_db_backup_*.sql 2>/dev/null | sort -r)
  [ "$dedup" -eq 0 ] && [ "$DRY_RUN" -eq 0 ] && say "    无同日重复快照"

  others="$(find "$BACKUP_DIR" -maxdepth 1 -type f ! -name 'jjx_erp_db_backup_*.sql' -mtime +"$KEEP_DAYS" 2>/dev/null | wc -l)"
  [ "${others:-0}" -gt 0 ] && warn "目录里还有 ${others} 个非本脚本产物（guard 表级备份/tar.gz 等）已超 ${KEEP_DAYS} 天，本脚本不删，需自行处理"
fi

say ""
ok "全部完成"
