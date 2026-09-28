#!/usr/bin/env bash
# ============================================================================
# JJX 迁移唯一执行通道
#
#   bash scripts/db-migrate.sh --status                                 查看已应用迁移 / 待执行清单
#   bash scripts/db-migrate.sh <NN_desc.sql> --yes [--task dev-...] [--tag xxx] [--backup <path>]
#   bash scripts/db-migrate.sh --record <NN> --yes                      接管已有库，登记某号已应用
#   bash scripts/db-migrate.sh --help
#
# 迁移前必须有手工备份（`--backup` 指定或放今天的备份在 jjx-docs/sql/backups/）；
#   脚本不代做备份、不写索引。
# 危险等级：🔴 改数据库（执行迁移：备份→执行→记版本；备份失败即中止）／🟡 只写版本记录（--record）／🟢 只读（--status）
# 前置：迁移文件在 jjx-docs/sql/migrations/；JJX_BACKUP_DIR 可写；动库必须带真实任务码
# 手册：jjx-docs/guides/scripts-commands-20260914.md
#
# 版本记账用「已应用集合」sys_config.ops.schema.applied（逗号分隔的号），
# 同时维护 ops.schema.version = 集合最大值（兼容旧读法）。
# 用集合而非"最大号"是为了乱序执行安全：若跳过 79 先跑 81，--status 仍会报 79 待执行。
#
# 设计原则（CONVENTIONS §2/§3）：把"改库先备份"从"要求 agent 自觉"变成
# "不备份这条路根本走不通"——本脚本是执行迁移的唯一入口，内部固定顺序：
#   前置检查 → 校验手工备份(记 md5/表数) → 执行 → 记录已应用版本 → 建表闸复核(提示) → 输出摘要
# 任何一步失败即中止，且**失败时不会执行/不会记录版本**。
#
# 环境覆盖（默认值即本机开发库，见 CONVENTIONS §2）：
#   DB_HOST DB_PORT DB_USER DB_PASS DB_NAME
#   JJX_BACKUP_DIR（默认仓库内 jjx-docs/sql/backups/）
#   JJX_MIGRATE_BACKUP（可替代 --backup，指定手工备份文件）
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
APPLIED_KEY="ops.schema.applied"

export MYSQL_PWD="$DB_PASS"
MYSQL=(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4)
q() { "${MYSQL[@]}" -N -B "$DB_NAME" -e "$1" 2>/dev/null; }

c_red=$'\033[31m'; c_grn=$'\033[32m'; c_yel=$'\033[33m'; c_off=$'\033[0m'
say()  { printf '%s\n' "$*"; }
die()  { printf '%s✘ %s%s\n' "$c_red" "$*" "$c_off" >&2; exit 1; }
ok()   { printf '%s✓%s %s\n' "$c_grn" "$c_off" "$*"; }
warn() { printf '%s⚠%s %s\n' "$c_yel" "$c_off" "$*"; }

# ── 风险分级（仅用于迁移计划展示）───────────────────────────────────────────
# 高风险 → 全库快照；低风险 → 只备份本次涉及的库表；涉及表都不存在 → 全库结构快照兜底。
LOW_RISK_TABLES="sys_config sys_dict sys_dict_type sys_dict_item"
RISK=""; RISK_SRC=""; RISK_TABLES=""
PRUNE=1

collect_tables() {  # $1=迁移文件 → 涉及的库表名（含动态 SQL 字符串里的），去重
  grep -oiE '(INSERT[[:space:]]+INTO|CREATE[[:space:]]+TABLE([[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS)?|ALTER[[:space:]]+TABLE|UPDATE|DELETE[[:space:]]+FROM|TRUNCATE([[:space:]]+TABLE)?|DROP[[:space:]]+TABLE([[:space:]]+IF[[:space:]]+EXISTS)?|JOIN)[[:space:]]+`?[A-Za-z0-9_]+`?' "$1" \
    | awk '{print $NF}' | tr -d '`' | grep -E '^[A-Za-z0-9_]+$' \
    | grep -viE '^(IF|NOT|EXISTS|ON|AS|SELECT|WHERE|VALUES)$' | sort -u
}

classify_risk() {  # $1=迁移文件 → RISK / RISK_SRC / RISK_TABLES
  local f="$1" ov t bad=0
  RISK_TABLES="$(collect_tables "$f" | paste -sd' ')"
  ov="$(grep -m1 -ioE '^--[[:space:]]*risk:[[:space:]]*(high|low)' "$f" 2>/dev/null | grep -ioE '(high|low)' | tr 'A-Z' 'a-z')"
  if [ "$ov" = "high" ]; then RISK=high; RISK_SRC="文件头 -- risk: high"; return; fi
  if [ "$ov" = "low" ]; then RISK=low;  RISK_SRC="文件头 -- risk: low（显式降级）"; return; fi
  RISK=high
  if grep -qiE '(DROP[[:space:]]+(TABLE|COLUMN|INDEX|KEY|FOREIGN)|TRUNCATE|DELETE[[:space:]]+FROM|MODIFY[[:space:]]+COLUMN|CHANGE[[:space:]]+COLUMN|RENAME[[:space:]]+TABLE|ALTER[[:space:]]+TABLE[^;]*DROP)' "$f"; then
    RISK_SRC="自动判定（破坏性语句）"; return
  fi
  if grep -qiE 'sys_menu|sys_role_menu' "$f"; then
    RISK_SRC="自动判定（菜单/权限变更）"; return
  fi
  if grep -qiE '(^|[^A-Za-z_])(UPDATE|DELETE)[[:space:]]' "$f"; then
    bad=0
    for t in $RISK_TABLES; do
      case " $LOW_RISK_TABLES " in *" $t "*) ;; *) bad=1 ;; esac
    done
    if [ "$bad" -eq 1 ]; then RISK_SRC="自动判定（数据订正类 UPDATE/DELETE）"; return; fi
  fi
  if [ -z "$RISK_TABLES" ]; then RISK_SRC="自动判定（无法识别影响面，保守按高风险）"; return; fi
  RISK=low; RISK_SRC="自动判定（仅建表/加列/加索引/配置新增）"
}

table_exists() { [ -n "$(q "SELECT 1 FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name='$1' LIMIT 1")" ]; }

# ── --status ───────────────────────────────────────────────────────────────
max_migration() {
  ls -1 "$MIG_DIR" 2>/dev/null | grep -E '^[0-9]+_' | sed -E 's/^([0-9]+)_.*/\1/' \
    | sort -n | tail -1
}
recorded_version() { q "SELECT config_value FROM sys_config WHERE config_key='$VERSION_KEY';" | head -1; }

# ── 已应用集合（乱序执行安全：记录"哪些号已应用"，而不是"最大号"）────────────
# 背景：2026-09-10 发现若跑 81 而 79/80 未跑，用"最大号"记录会让 --status 再也看不见 79/80 缺失。
all_dir_nn() {
  ls -1 "$MIG_DIR" 2>/dev/null | grep -E '^[0-9]+_' \
    | sed -E 's/^([0-9]+)_.*/\1/' | sort -n | uniq
}
# 同号多文件检测（2026-09-10 实际撞过：82 被两个 agent 同时使用）
dup_nn() {
  ls -1 "$MIG_DIR" 2>/dev/null | grep -E '^[0-9]+_' \
    | sed -E 's/^([0-9]+)_.*/\1/' | sort -n | uniq -d
}
files_of_nn() { ls -1 "$MIG_DIR" 2>/dev/null | grep -E "^${1}_" | paste -sd' '; }
applied_set() {
  local a v
  a=$(q "SELECT config_value FROM sys_config WHERE config_key='$APPLIED_KEY';" | head -1)
  if [ -n "$a" ]; then
    printf '%s' "$a" | tr ',' '\n' | grep -E '^[0-9]+$' | sort -n | uniq | paste -sd,
    return
  fi
  # 无集合记录时按 version 推导：目录里所有 <= version 的号（接管老库用）
  v="$(recorded_version)"
  [ -z "$v" ] && return
  all_dir_nn | awk -v v="$v" '$1+0 <= v+0' | paste -sd,
}
is_applied() { case ",$(applied_set)," in *",$1,"*) return 0 ;; *) return 1 ;; esac; }
record_applied() {  # $1=NN
  # 2026-09-22 修 fail-open：原先 `... 2>/dev/null` 既不查退出码也不回读，写失败仍报成功
  #（实测 sys_config.config_value varchar(500) 写满 498 字符 → ERROR 1406 被吞掉，账本静默丢账）。
  # 现在：写库失败 → stderr 打印原因 + 返回非 0；再用回读校验「号已落库」且「历史号没被覆盖丢失」。
  local set new maxv got err lost
  set="$(applied_set)"
  new=$( { [ -n "$set" ] && printf '%s\n' "$set" | tr ',' '\n'; printf '%s\n' "$1"; } \
         | grep -E '^[0-9]+$' | sort -n | uniq | paste -sd, )
  maxv=$(printf '%s' "$new" | tr ',' '\n' | sort -n | tail -1)
  if ! err="$("${MYSQL[@]}" "$DB_NAME" -e "
    INSERT INTO sys_config (config_key, config_value, config_name, config_group, remark, sort_order, is_active)
    VALUES ('$APPLIED_KEY', '$new', '已应用迁移集合', 'ops', '由 scripts/db-migrate.sh 维护（逗号分隔）', 0, 1)
    ON DUPLICATE KEY UPDATE config_value=VALUES(config_value), update_time=NOW();
    INSERT INTO sys_config (config_key, config_value, config_name, config_group, remark, sort_order, is_active)
    VALUES ('$VERSION_KEY', '$maxv', '已应用迁移版本(最大)', 'ops', '由 scripts/db-migrate.sh 维护', 0, 1)
    ON DUPLICATE KEY UPDATE config_value=VALUES(config_value), update_time=NOW();" 2>&1 >/dev/null)"; then
    printf '%s\n' "写库失败：${err:-未知错误}" >&2
    return 1
  fi
  got="$(q "SELECT config_value FROM sys_config WHERE config_key='$APPLIED_KEY';" | head -1)"
  if ! printf '%s' ",$got," | grep -q ",$1,"; then
    printf '%s\n' "回读校验失败：号 $1 未落库（当前值: ${got:-空}）" >&2
    return 1
  fi
  lost="$(printf '%s' "$set" | tr ',' '\n' | awk -v got=",$got," 'NF && index(got, "," $0 ",") == 0 { printf "%s ", $0 }')"
  if [ -n "$lost" ]; then
    printf '%s\n' "回读校验失败：历史号被覆盖丢失（$lost）" >&2
    return 1
  fi
  printf '%s' "$(printf '%s' "$got" | tr ',' '\n' | grep -E '^[0-9]+$' | sort -n | uniq | paste -sd,)"
}

if [ "${1:-}" = "--status" ] || [ $# -eq 0 ]; then
  rec="$(recorded_version)"; max="$(max_migration)"; set="$(applied_set)"
  say "迁移目录: ${MIG_DIR#$REPO_ROOT/}"
  say "库        : $DB_NAME@$DB_HOST:$DB_PORT"
  say ""
  if [ -z "$set" ]; then
    warn "库中未记录已应用迁移（sys_config.$APPLIED_KEY / $VERSION_KEY 均为空）——只能按目录人工比对"
  else
    say "已应用: $set"
    [ -z "$(q "SELECT config_value FROM sys_config WHERE config_key='$APPLIED_KEY';" | head -1)" ] \
      && say "        （按 $VERSION_KEY 推导，首次执行迁移时会落成显式集合）"
  fi
  say "目录最大号: ${max:-无}"
  dups="$(dup_nn)"
  if [ -n "$dups" ]; then
    warn "迁移号重复（已应用集合只记号，同号会导致对方的迁移被误判为已应用）："
    for n in $dups; do say "    - 号 ${n}: $(files_of_nn "$n")"; done
    say "      处理：后建者改号，并同步 sys_config.$APPLIED_KEY 里的旧号"
  fi
  if [ -n "$max" ]; then
    pending=$(while IFS= read -r f; do
                nn="${f%%_*}"
                case ",$set," in *",$nn,"*) ;; *) printf '%s\n' "$f" ;; esac
              done < <(ls -1 "$MIG_DIR" | grep -E '^[0-9]+_' | sort -n))
    if [ -z "$pending" ]; then
      ok "无待执行迁移（目录里的迁移都已应用）"
    else
      n=$(printf '%s\n' "$pending" | grep -c .)
      warn "待执行迁移 ${n} 个："
      while IFS= read -r f; do [ -n "$f" ] && say "    - $f"; done <<< "$pending"
      if [ -n "$set" ]; then
        maxapp=$(printf '%s' "$set" | tr ',' '\n' | sort -n | tail -1)
        low=$(printf '%s\n' "$pending" | awk -F_ -v m="$maxapp" '$1+0 < m+0' | grep -c . || true)
        [ "${low:-0}" -gt 0 ] && warn "注意乱序：已应用更高号（最大 $maxapp），但有 ${low} 个低号迁移缺失，建议逐个补齐"
      fi
      say "  执行：bash scripts/db-migrate.sh <文件名> --yes --task dev-YYYYMMDD-NNN"
    fi
  fi
  exit 0
fi

# ── 参数解析 ───────────────────────────────────────────────────────────────
MIG_FILE=""
TASK=""
TAG=""
BACKUP_ARG="${JJX_MIGRATE_BACKUP:-}"
CONFIRMED=0
RECORD=""
while [ $# -gt 0 ]; do
  case "$1" in
    --yes|-y) CONFIRMED=1 ;;
    --record) RECORD="${2:-}"; shift ;;
    --task) TASK="${2:-}"; shift ;;
    --tag)  TAG="${2:-}"; shift ;;
    --backup) BACKUP_ARG="${2:-}"; shift ;;
    -h|--help) sed -n '2,18p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) die "未知参数: $1" ;;
    *)  [ -z "$MIG_FILE" ] || die "只接受一个迁移文件参数" ; MIG_FILE="$1" ;;
  esac
  shift
done

# ── --record <NN>：接管已存在的库，只登记版本、不执行迁移 ────────────────────
if [ -n "$RECORD" ]; then
  case "$RECORD" in ''|*[!0-9]*) die "--record 需要数字序号，如 --record 78" ;; esac
  "${MYSQL[@]}" -e "SELECT 1" >/dev/null 2>&1 || die "连不上数据库 $DB_NAME@$DB_HOST:$DB_PORT"
  old="$(recorded_version)"
  say "── 登记已应用版本 ──"
  say "  已应用集合: $(applied_set)（追加 $RECORD）"
  [ "$CONFIRMED" -eq 1 ] || { say "  （未加 --yes：未写入）"; exit 0; }
  newset=$(record_applied "$RECORD") || die "登记失败（原因见上方；库中集合未改动）"
  [ -n "$newset" ] || die "登记失败（回读为空；库中集合未改动）"
  ok "sys_config.$APPLIED_KEY = $newset"
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
if printf '%s\n' "$(dup_nn)" | grep -qw "$NN"; then
  warn "号 ${NN} 存在多个文件：$(files_of_nn "$NN") —— 已应用集合只记号，请确认谁是本次目标（必要时先改号）"
fi

rec="$(recorded_version)"
say ""
say "════ 迁移计划 ════"
say "  文件    : $BASE"
say "  目标库  : $DB_NAME@$DB_HOST:$DB_PORT"
say "  已记录版本: ${rec:-（未记录）}    本次序号: $NN"
say "  语句特征: CREATE TABLE ${create_cnt} 处 / 破坏性语句(DROP|TRUNCATE|DELETE) ${destructive} 处"
classify_risk "$SRC"
if [ "$RISK" = high ]; then say "  备份策略: 全库快照（风险=high；${RISK_SRC}）"; else say "  备份策略: 表级备份（风险=low；${RISK_SRC}）"; fi
say "  影响表  : ${RISK_TABLES:-（未识别到）}"
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

# ── 1. 校验手工备份（失败则绝不执行迁移）────────────────────────────────────
BK_KIND="full"; BK_FILE=""; BK_SIZE=0; BK_TABLES=0; BK_MD5=""
say ""
say "── 1/4 校验手工备份 ──"
if [ -n "$BACKUP_ARG" ]; then
  [ -f "$BACKUP_ARG" ] || die "指定的备份文件不存在: $BACKUP_ARG"
  BK_FILE="$BACKUP_ARG"
else
  BK_FILE="$(ls -1t "$BACKUP_DIR"/jjx_erp_db_backup_"$(date +%Y%m%d)"-*.sql 2>/dev/null | head -1)"
fi
[ -n "$BK_FILE" ] || die "未找到今天的备份。请先手工备份（只留最新一份）：mysqldump -h127.0.0.1 -P3306 -uroot --single-transaction --set-gtid-purged=OFF --no-tablespaces --ignore-table=jjx_erp_db.hr_employee jjx_erp_db > jjx-docs/sql/backups/jjx_erp_db_backup_$(date +%Y%m%d-%H%M)_before-<NN>.sql"
BK_TABLES=$(grep -c '^CREATE TABLE' "$BK_FILE" 2>/dev/null || true)
BK_SIZE=$(stat -c%s "$BK_FILE" 2>/dev/null || echo 0)
[ "$BK_SIZE" -gt 1024 ] && [ "$BK_TABLES" -ge 1 ] || die "手工备份文件异常（${BK_SIZE}B / ${BK_TABLES} 张表）——已中止，未执行迁移"
BK_MD5=$(md5sum "$BK_FILE" | cut -d' ' -f1)
ok "手工全库备份 $BK_FILE"
say "    ${BK_SIZE} 字节 / ${BK_TABLES} 张表 / md5 ${BK_MD5}"

# ── 2. 执行迁移 ────────────────────────────────────────────────────────────
say ""
say "── 2/4 执行迁移 ──"
START=$(date +%s)
if ! "${MYSQL[@]}" "$DB_NAME" < "$SRC" 2>"$BK_FILE.migerr"; then
  sed 's/^/    /' "$BK_FILE.migerr" >&2; rm -f "$BK_FILE.migerr"
  say ""
  die "迁移执行失败。回滚参考：
    mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME < $BK_FILE
  （版本号未记录，sys_config.$VERSION_KEY 保持原值 ${rec:-空}）"
fi
rm -f "$BK_FILE.migerr"
ok "迁移执行完成（$(($(date +%s) - START))s）"

# ── 3. 记录已应用版本 ──────────────────────────────────────────────────────
say ""
say "── 3/4 记录已应用版本 ──"
REMARK="迁移 ${BASE} 于 $(date '+%Y-%m-%d %H:%M') 由 ${AI_AGENT:-agent} 执行；备份 $(basename "$BK_FILE") md5=$BK_MD5${TASK:+；任务 $TASK}"
if [ -n "$(q "SELECT 1 FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name='sys_config'")" ]; then
  if ! newset=$(record_applied "$NN"); then
    say ""
    die "版本记账失败（原因见上方）——**迁移已执行但账本没记上**，下次会被误判为未应用。
    - 撤回到迁移前: mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME < $BK_FILE
    - 或修好原因后补登记（不要重复执行迁移）: bash scripts/db-migrate.sh --record $NN --yes --task ${TASK:-dev-YYYYMMDD-NNN}"
  fi
  ok "sys_config.$APPLIED_KEY = $newset"
else
  warn "本库没有 sys_config 表，跳过版本记录"
  newset=""
fi

# ── 4/4 建表闸复核（CONVENTIONS §15；只告警不阻断）───────────────────────────
say ""
say "── 4/4 建表闸复核 ──"
GATE="$REPO_ROOT/scripts/check-model-baseline.sh"
if [ -f "$GATE" ]; then
  if gate_out="$(bash "$GATE" 2>&1)"; then
    ok "建表闸通过（表数基线一致）"
  else
    warn "建表闸未通过——新表要先把登记进 scripts/model-baseline.json 的 approvedNewTables（§15.3/§15.7）"
    printf '%s\n' "$gate_out" | grep -E '^(✘|  - )' | sed 's/^/    /'
    warn "（本次迁移已执行并记账；此闸门仅提示，不阻断）"
  fi
else
  warn "未找到 $(basename "$GATE")，跳过建表闸复核"
fi

say ""
say ""
say "════ 完成 ════"
say "  迁移 : $BASE"
say "  备份 : ${BK_KIND} → $BK_FILE  (md5 $BK_MD5)"
say "  已应用: $APPLIED_KEY = ${newset:-$NN}"
say ""
say "  收尾提醒："
say "   - 迁移前必须有手工备份（--backup 指定或放今天的备份在 ${BACKUP_DIR#$REPO_ROOT/}）"
say "   - 在 sys_task 登记执行记录（时间/执行人/备份 md5）"
say "   - 业务侧验证后再通知用户验收"
