#!/usr/bin/env bash
# ============================================================================
# JJX 迁移唯一执行通道
#
#   bash scripts/db-migrate.sh --status                                 查看已应用版本 / 待执行迁移
#   bash scripts/db-migrate.sh <NN_desc.sql> --yes [--task dev-...] [--tag xxx]
#
# 设计原则（CONVENTIONS §2/§3）：把"改库先备份"从"要求 agent 自觉"变成
# "不备份这条路根本走不通"——本脚本是执行迁移的唯一入口，内部固定顺序：
#   前置检查 → 全库备份(记 md5/表数) → 执行 → 记录已应用版本 → 输出摘要
# 任何一步失败即中止，且**失败时不会执行/不会记录版本**。
#
# 环境覆盖（默认值即本机开发库，见 CONVENTIONS §2）：
#   DB_HOST DB_PORT DB_USER DB_PASS DB_NAME
#   JJX_BACKUP_DIR（默认 jjx-docs/sql/backups）
#   JJX_MIGRATIONS_DIR（默认 jjx-docs/sql/migrations；仅用于自测）
# ============================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"
DB_NAME="${DB_NAME:-jjx_erp_db}"
BACKUP_DIR="${JJX_BACKUP_DIR:-$REPO_ROOT/jjx-docs/sql/backups}"
MIG_DIR="${JJX_MIGRATIONS_DIR:-$REPO_ROOT/jjx-docs/sql/migrations}"
VERSION_KEY="ops.schema.version"

export MYSQL_PWD="$DB_PASS"
MYSQL=(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4)
q() { "${MYSQL[@]}" -N -B "$DB_NAME" -e "$1" 2>/dev/null; }

c_red=$'\033[31m'; c_grn=$'\033[32m'; c_yel=$'\033[33m'; c_off=$'\033[0m'
say()  { printf '%s\n' "$*"; }
die()  { printf '%s✘ %s%s\n' "$c_red" "$*" "$c_off" >&2; exit 1; }
ok()   { printf '%s✓%s %s\n' "$c_grn" "$c_off" "$*"; }
warn() { printf '%s⚠%s %s\n' "$c_yel" "$c_off" "$*"; }

# ── --status ───────────────────────────────────────────────────────────────
max_migration() {
  ls -1 "$MIG_DIR" 2>/dev/null | grep -E '^[0-9]+_' | sed -E 's/^([0-9]+)_.*/\1/' \
    | sort -n | tail -1
}
recorded_version() { q "SELECT config_value FROM sys_config WHERE config_key='$VERSION_KEY';" | head -1; }

if [ "${1:-}" = "--status" ] || [ $# -eq 0 ]; then
  rec="$(recorded_version)"; max="$(max_migration)"
  say "迁移目录: ${MIG_DIR#$REPO_ROOT/}"
  say "库        : $DB_NAME@$DB_HOST:$DB_PORT"
  say ""
  if [ -z "$rec" ]; then
    warn "库中未记录已应用版本（sys_config.$VERSION_KEY 为空）——只能按迁移目录人工比对"
  else
    say "已应用版本: $rec"
  fi
  say "目录最大号: ${max:-无}"
  if [ -n "$rec" ] && [ -n "$max" ]; then
    pending=$(ls -1 "$MIG_DIR" | grep -E '^[0-9]+_' \
              | awk -v r="$rec" -F_ '{ if ($1+0 > r+0) print }' | sort -n)
    if [ -z "$pending" ]; then
      ok "无待执行迁移（库与代码一致）"
    else
      n=$(printf '%s\n' "$pending" | grep -c .)
      warn "待执行迁移 ${n} 个："
      while IFS= read -r f; do [ -n "$f" ] && say "    - $f"; done <<< "$pending"
      say "  执行：bash scripts/db-migrate.sh <文件名> --yes --task dev-YYYYMMDD-NNN"
    fi
  fi
  exit 0
fi

# ── 参数解析 ───────────────────────────────────────────────────────────────
MIG_FILE=""
TASK=""
TAG=""
CONFIRMED=0
RECORD=""
while [ $# -gt 0 ]; do
  case "$1" in
    --yes|-y) CONFIRMED=1 ;;
    --record) RECORD="${2:-}"; shift ;;
    --task) TASK="${2:-}"; shift ;;
    --tag)  TAG="${2:-}"; shift ;;
    -h|--help) sed -n '2,14p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) die "未知参数: $1" ;;
    *)  [ -z "$MIG_FILE" ] || die "只接受一个迁移文件参数" ; MIG_FILE="$1" ;;
  esac
  shift
done

# ── --record <NN>：接管已存在的库，只登记版本、不执行迁移 ────────────────────
if [ -n "$RECORD" ]; then
  case "$RECORD" in ''|*[!0-9]*) die "--record 需要数字序号，如 --record 78" ;; esac
  "${MYSQL[@]}" -e "SELECT 1" >/dev/null 2>&1 || die "连不上数据库 $DB_NAME@$DB_HOST:$DB_PORT"
  mkdir -p "$BACKUP_DIR" || die "无法创建备份目录 $BACKUP_DIR"
  TS="$(date +%Y%m%d-%H%M)"
  GUARD="$BACKUP_DIR/sys_config_record-version_${TS}.sql"
  { printf -- "-- 备份人: %s\n-- 原因: 登记 %s（%s）前的 sys_config guard 备份\n-- 任务码: %s\n" \
      "${AI_AGENT:-agent}" "$VERSION_KEY" "$RECORD" "${TASK:-无}"
    mysqldump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 \
      --single-transaction --set-gtid-purged=OFF --no-tablespaces "$DB_NAME" sys_config 2>/dev/null; } > "$GUARD"
  G_MD5=$(md5sum "$GUARD" | cut -d' ' -f1); G_ROWS=$(grep -c '^INSERT INTO' "$GUARD" || true)
  [ -s "$GUARD" ] || die "guard 备份为空，已中止"
  old="$(recorded_version)"
  say "── 登记已应用版本 ──"
  say "  guard 备份: ${GUARD#$REPO_ROOT/}（$(stat -c%s "$GUARD")B / INSERT ${G_ROWS} 段 / md5 ${G_MD5}）"
  say "  版本: ${old:-（空）} → $RECORD"
  [ "$CONFIRMED" -eq 1 ] || { say "  （未加 --yes：未写入）"; exit 0; }
  "${MYSQL[@]}" "$DB_NAME" -e "
    INSERT INTO sys_config (config_key, config_value, config_name, config_group, remark, sort_order, is_active)
    VALUES ('$VERSION_KEY', '$RECORD', '已应用迁移版本', 'ops',
            '接管登记：$(date +%Y-%m-%d\ %H:%M) 由 ${AI_AGENT:-agent} 登记为 $RECORD${TASK:+；任务 $TASK}', 0, 1)
    ON DUPLICATE KEY UPDATE config_value=VALUES(config_value), remark=VALUES(remark), update_time=NOW();" 2>/dev/null \
    || die "写入失败（未改动任何业务数据）"
  ok "sys_config.$VERSION_KEY = $(recorded_version)"
  exit 0
fi

[ -n "$MIG_FILE" ] || die "缺少迁移文件参数（或使用 --status）"

# ── 前置检查 ───────────────────────────────────────────────────────────────
BASE="$(basename "$MIG_FILE")"
case "$BASE" in
  [0-9]*_[A-Za-z0-9._-]*.sql) ;;
  *) die "迁移文件名必须是 NN_<描述>.sql，当前: $BASE（CONVENTIONS §3）" ;;
esac
NN="${BASE%%_*}"
# 裸文件名 → 在迁移目录下解析；带路径 → 按给定路径解析（随后仍校验是否在迁移目录内）
if [[ "$MIG_FILE" == */* ]]; then
  SRC="$(cd "$(dirname "$MIG_FILE")" 2>/dev/null && pwd)/$BASE"
else
  SRC="$MIG_DIR/$BASE"
fi
[ -f "$SRC" ] || die "迁移文件不存在: $MIG_FILE（在 ${MIG_DIR#$REPO_ROOT/} 下也没找到）"
if [ "$(cd "$(dirname "$SRC")" && pwd)" != "$(cd "$MIG_DIR" && pwd)" ]; then
  die "迁移必须放在 ${MIG_DIR#$REPO_ROOT/} 下（CONVENTIONS §3）；当前: $SRC"
fi
[ -s "$SRC" ] || die "迁移文件为空: $SRC"
"${MYSQL[@]}" -e "SELECT 1" >/dev/null 2>&1 || die "连不上数据库 $DB_NAME@$DB_HOST:$DB_PORT（检查服务与账号）"
create_cnt=$(grep -c '^CREATE TABLE' "$SRC" 2>/dev/null || true)
destructive=$(grep -icE '^\s*(DROP|TRUNCATE|DELETE)' "$SRC" 2>/dev/null || true)

rec="$(recorded_version)"
say ""
say "════ 迁移计划 ════"
say "  文件    : $BASE"
say "  目标库  : $DB_NAME@$DB_HOST:$DB_PORT"
say "  已记录版本: ${rec:-（未记录）}    本次序号: $NN"
say "  语句特征: CREATE TABLE ${create_cnt} 处 / 破坏性语句(DROP|TRUNCATE|DELETE) ${destructive} 处"
[ -n "$TASK" ] && say "  任务码  : $TASK"
[ "$destructive" -gt 0 ] && warn "含破坏性语句——请确认已审阅（CONVENTIONS §3 要求显式注释原因）"
if [ -n "$rec" ] && [ "$NN" -le "$rec" ] 2>/dev/null; then
  warn "本次序号($NN) <= 已记录版本($rec)：属于重复执行，仅在脚本幂等时安全"
fi

if [ "$CONFIRMED" -ne 1 ]; then
  say ""
  say "（未加 --yes：以上仅为计划，未备份、未执行）"
  say "  确认后执行：bash scripts/db-migrate.sh $BASE --yes --task dev-YYYYMMDD-NNN"
  exit 0
fi

# ── 1. 备份（失败则绝不执行迁移）────────────────────────────────────────────
mkdir -p "$BACKUP_DIR" || die "无法创建备份目录 $BACKUP_DIR"
TS="$(date +%Y%m%d-%H%M)"
TAG="${TAG:-before-${NN}}"
BACKUP="$BACKUP_DIR/jjx_erp_db_backup_${TS}_${TAG}.sql"
say ""
say "── 1/3 备份 ──"
if ! mysqldump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 \
      --single-transaction --set-gtid-purged=OFF --no-tablespaces "$DB_NAME" > "$BACKUP.raw" 2>"$BACKUP.err"; then
  rm -f "$BACKUP.raw"; sed 's/^/    /' "$BACKUP.err" >&2; rm -f "$BACKUP.err"
  die "备份失败——已中止，未执行任何迁移"
fi
rm -f "$BACKUP.err"
{ printf -- "-- 备份人: %s\n-- 原因: 执行迁移 %s 前全库备份（脚本 %s）\n-- 任务码: %s\n" \
    "${AI_AGENT:-agent}" "$BASE" "scripts/db-migrate.sh" "${TASK:-无}"; cat "$BACKUP.raw"; } > "$BACKUP"
rm -f "$BACKUP.raw"
BK_SIZE=$(stat -c%s "$BACKUP" 2>/dev/null || echo 0)
BK_TABLES=$(grep -c '^CREATE TABLE' "$BACKUP" 2>/dev/null || echo 0)
BK_MD5=$(md5sum "$BACKUP" | cut -d' ' -f1)
if [ "$BK_SIZE" -lt 1024 ] || [ "$BK_TABLES" -lt 1 ]; then
  die "备份产物异常（${BK_SIZE}B / ${BK_TABLES} 张表）——已中止，未执行迁移"
fi
ok "备份 ${BACKUP#$REPO_ROOT/}"
say "    ${BK_SIZE} 字节 / ${BK_TABLES} 张表 / md5 ${BK_MD5}"

# ── 2. 执行迁移 ────────────────────────────────────────────────────────────
say ""
say "── 2/3 执行迁移 ──"
START=$(date +%s)
if ! "${MYSQL[@]}" "$DB_NAME" < "$SRC" 2>"$BACKUP.migerr"; then
  sed 's/^/    /' "$BACKUP.migerr" >&2; rm -f "$BACKUP.migerr"
  say ""
  die "迁移执行失败。回滚参考：
    mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME < ${BACKUP#$REPO_ROOT/}
  （版本号未记录，sys_config.$VERSION_KEY 保持原值 ${rec:-空}）"
fi
rm -f "$BACKUP.migerr"
ok "迁移执行完成（$(($(date +%s) - START))s）"

# ── 3. 记录已应用版本 ──────────────────────────────────────────────────────
say ""
say "── 3/3 记录已应用版本 ──"
REMARK="迁移 ${BASE} 于 $(date '+%Y-%m-%d %H:%M') 由 ${AI_AGENT:-agent} 执行；备份 $(basename "$BACKUP") md5=$BK_MD5${TASK:+；任务 $TASK}"
if q "SELECT 1 FROM sys_config LIMIT 1" >/dev/null && [ -n "$(q "SELECT 1 FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name='sys_config'")" ]; then
  "${MYSQL[@]}" "$DB_NAME" -e "
    INSERT INTO sys_config (config_key, config_value, config_name, config_group, remark, sort_order, is_active)
    VALUES ('$VERSION_KEY', '$NN', '已应用迁移版本', 'ops', '${REMARK//\'/}', 0, 1)
    ON DUPLICATE KEY UPDATE config_value=VALUES(config_value), remark=VALUES(remark), update_time=NOW();" 2>/dev/null \
    && ok "sys_config.$VERSION_KEY = $NN" \
    || warn "版本记录写入失败（迁移已执行，请手工登记）"
else
  warn "本库没有 sys_config 表，跳过版本记录"
fi

say ""
say "════ 完成 ════"
say "  迁移 : $BASE"
say "  备份 : ${BACKUP#$REPO_ROOT/}  (md5 $BK_MD5)"
say "  版本 : $VERSION_KEY = $NN"
say ""
say "  收尾提醒："
say "   - 备份按规范随仓库提交（CONVENTIONS §2）"
say "   - 在 sys_task 登记执行记录（时间/执行人/备份 md5）"
say "   - 业务侧验证后再通知用户验收"
