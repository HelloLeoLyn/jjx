#!/usr/bin/env bash
# task-register.sh —— 开发任务登记唯一入口（sys_task）
#
# 用途: 取号 + 撞唯一约束自动重试 + 登记后回查格式/排序（**默认不再做 guard 备份**，2026-10-09 起；需备份用 --backup）
# 危险等级: 🔴 改数据库（INSERT sys_task）+ 🟡 写备份文件/索引
# 前置: mysql 可连（默认 -uroot -p123456，可用 JJX_DB_ARGS 覆盖）；redis 模式需 redis-cli（默认 127.0.0.1:6379 db0）
# 背景（2026-09-24 立）: 多 agent（OpenClaw/Hermes/Codex）并行登记，各自 MAX+1 取号必然撞车；
#   且手写 SQL 出现过两类事故：① LPAD(@base+n,3,'0') 被当小数 → 畸形码 dev-YYYYMMDD-14.
#   ② INSERT...SELECT...FROM sys_task 少聚合 → 插多行 → 整条回滚。本脚本把这两类坑封死。
# 取码模式（2026-10-14 增）:
#   redis（默认）: Redis 计数器 `jjx:task:seq:<module>:<YYYYMMDD>` INCR 取号；首次使用/库被回滚时
#                  先用 max(库里当天最大号, git 历史当天最大号) 播种（SETNX），因此**清库/回滚也不会重发号**。
#   db          : 只看库里当天最大号 +1（旧行为，且已加 git 历史当天最大号下限）。
#   Fallback    : redis 模式在 Redis 不可达时自动回退 db 模式（不阻塞登记）。
set -euo pipefail

DB_ARGS="${JJX_DB_ARGS:--uroot -p123456}"
DB_NAME="${JJX_DB_NAME:-jjx_erp_db}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$REPO_ROOT/jjx-docs/sql/backups"
MAX_ATTEMPT=3

# Redis 连接（与 jjx-server application-dev.yml 默认一致：127.0.0.1:6379 / db0 / 空密码）
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_DB="${REDIS_DB:-0}"
REDIS_ARGS=(-h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DB")
[[ -n "${REDIS_PASSWORD:-}" ]] && REDIS_ARGS+=(-a "$REDIS_PASSWORD")

usage() {
  cat <<'EOF'
用途: 登记开发任务到 sys_task（唯一入口：取号 + 撞号重试 + 回查 + 可选 guard 备份）
危险等级: 🔴 改数据库（INSERT sys_task）+ 🟡 写备份文件/索引
前置: mysql 可连（默认 -uroot -p123456，JJX_DB_ARGS 可覆盖）；redis 模式需 redis-cli；建议在仓库根执行

用法:
  bash scripts/task-register.sh --title "任务标题" [选项]

选项:
  --title <文本>       必填，任务标题（会做单引号转义）
  --seq-mode <m>       取码模式：redis（默认）| db（只看库里当天最大号）
  --priority <P0..P3>  优先级，默认 P2
  --module <mod>       看板模块，默认 dev
  --type <TYPE>        任务类型，默认 DEV
  --by <agent>         登记人，默认 dahuang
  --desc <文本>        描述（可选）
  --desc-file <文件>   从文件读描述（可选，优先于 --desc）
  --backup             额外做 sys_task 表级 guard 备份（默认关闭：按 CONVENTIONS §2 任务登记免备份）
  --no-backup          兼容旧参数（等价默认：不备份）
  --dry-run            只打印取码依据/将执行的 SQL 与备份路径，不落库不写文件（不 INCR Redis）
  -h, --help           本帮助

行为:
  1) 取号：redis 模式 = SETNX 播种 max(库当天最大, git 当天最大) → INCR；db 模式 = 库当天最大 +1（同样卡 git 下限）
  2) 插入：单条 INSERT 真码；撞唯一约束（Duplicate entry / 1062）→ 重新取号重试，最多 3 次
  3) 回查：task_code 必须匹配 ^<module>-YYYYMMDD-NNN$ 且是当日最大号；否则报错退出
  4) 备份（可选，--backup）：mysqldump 表级 guard 备份 → jjx-docs/sql/backups/sys_task_register_<时间>_guard.sql
     并在拿到真码后追加 backup-index.tsv

退出码: 0=登记成功（stdout 打印 task_code）；1=参数/前置/登记失败
EOF
}

TITLE=""; PRIORITY="P2"; MODULE="dev"; TYPE="DEV"; BY="dahuang"; DESC=""
SEQ_MODE="redis"; DO_BACKUP=0; DRY=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --title) TITLE="${2:-}"; shift 2 ;;
    --seq-mode) SEQ_MODE="${2:-}"; shift 2 ;;
    --priority) PRIORITY="${2:-}"; shift 2 ;;
    --module) MODULE="${2:-}"; shift 2 ;;
    --type) TYPE="${2:-}"; shift 2 ;;
    --by) BY="${2:-}"; shift 2 ;;
    --desc) DESC="${2:-}"; shift 2 ;;
    --desc-file) DESC="$(cat "${2:-/dev/null}")"; shift 2 ;;
    --backup) DO_BACKUP=1; shift ;;
    --no-backup) shift ;; # 兼容旧参数（默认即不备份）
    --dry-run) DRY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage; exit 1 ;;
  esac
done
[[ -n "$TITLE" ]] || { echo "必须提供 --title" >&2; usage; exit 1; }
case "$SEQ_MODE" in redis|db) ;; *) echo "未知 --seq-mode: $SEQ_MODE（支持 redis|db）" >&2; exit 1 ;; esac

esc() { printf '%s' "$1" | sed "s/'/''/g"; }
DAY="$(date '+%Y%m%d')"
PREFIX="${MODULE}-${DAY}-"
SEQ_KEY="jjx:task:seq:${MODULE}:${DAY}"

mysql_run() { mysql $DB_ARGS --batch --skip-column-names "$DB_NAME" -e "$1"; }

# 库里当天（已存在行）的最大序号
db_max_today() {
  mysql_run "SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(task_code,'-',-1) AS UNSIGNED)),0) FROM sys_task WHERE task_code LIKE '${PREFIX}%';"
}

# git 历史当天已用过的最大序号（防"行被清/库被回滚 → 号重发"）
git_max_today() {
  local out
  out="$(cd "$REPO_ROOT" && git log --all --oneline --grep="${PREFIX}" 2>/dev/null \
        | grep -oE "${PREFIX}[0-9]{3}" | sed 's/.*-//' | sort -n | tail -1 || true)"
  printf '%s' "${out:-0}"
}

# 当天已用号下限 = max(库里最大, git 历史最大)
seq_floor() {
  local d g
  d="$(db_max_today)"; g="$(git_max_today)"
  [[ "$d" =~ ^[0-9]+$ ]] || d=0
  [[ "$g" =~ ^[0-9]+$ ]] || g=0
  if (( d > g )); then printf '%s' "$d"; else printf '%s' "$g"; fi
}

redis_alive() { redis-cli "${REDIS_ARGS[@]}" PING >/dev/null 2>&1; }

# redis 取号：播种（不回退已有值，但库被回滚后下限变高时会抬升）→ INCR
redis_next() {
  local floor cur
  floor="$(seq_floor)"
  redis-cli "${REDIS_ARGS[@]}" SET "$SEQ_KEY" "$floor" NX >/dev/null 2>&1 || true
  cur="$(redis-cli "${REDIS_ARGS[@]}" GET "$SEQ_KEY" 2>/dev/null | tr -d '\r')"
  [[ "$cur" =~ ^[0-9]+$ ]] || cur=0
  if (( cur < floor )); then redis-cli "${REDIS_ARGS[@]}" SET "$SEQ_KEY" "$floor" >/dev/null 2>&1 || true; fi
  redis-cli "${REDIS_ARGS[@]}" INCR "$SEQ_KEY" | tr -d '\r'
}

insert_sql_for() { # $1 = 完整 task_code
  printf "INSERT INTO sys_task (task_code,task_type,kanban_module,title,status,priority,create_by,description,create_time) VALUES ('%s','%s','%s','%s',0,'%s','%s','%s',NOW());" \
    "$(esc "$1")" "$(esc "$TYPE")" "$(esc "$MODULE")" "$(esc "$TITLE")" "$(esc "$PRIORITY")" "$(esc "$BY")" "$(esc "$DESC")"
}

code_of() { printf '%s%03d' "$PREFIX" "$1"; }

if [[ $DRY -eq 1 ]]; then
  echo "[dry-run] 取码模式: $SEQ_MODE ｜ Redis 键: $SEQ_KEY ｜ 当天下限(库/git 取大): $(seq_floor)"
  if [[ "$SEQ_MODE" == redis ]]; then
    if redis_alive; then
      echo "[dry-run] Redis 当前值: $(redis-cli "${REDIS_ARGS[@]}" GET "$SEQ_KEY" 2>/dev/null | tr -d '\r')（+1 即本次号；dry-run 不 INCR）"
    else
      echo "[dry-run] Redis 不可达（$REDIS_HOST:$REDIS_PORT db$REDIS_DB）→ 实际执行会回退 db 模式"
    fi
  fi
  echo "[dry-run] guard 备份: 默认不做（加 --backup 才写 $BACKUP_DIR/sys_task_register_${DAY}-<HHMM>_guard.sql）"
  echo "[dry-run] 将执行: $(insert_sql_for "${PREFIX}NNN")"
  exit 0
fi

# 1) 改库前 guard 备份（默认关闭，2026-10-09 起；按 CONVENTIONS §2 任务登记免备份）
if [[ $DO_BACKUP -eq 1 ]]; then
  mkdir -p "$BACKUP_DIR"
  TS_FULL="$(date '+%Y-%m-%d %H:%M:%S')"; TS_MIN="$(date '+%Y%m%d-%H%M')"
  GF="$BACKUP_DIR/sys_task_register_${DAY}-${TS_MIN}_guard.sql"
  {
    printf -- '-- 备份人: %s\n' "$BY"
    printf -- '-- 原因: 登记任务前的 sys_task 表级 guard（scripts/task-register.sh）\n'
    printf -- '-- 时间: %s\n' "$TS_FULL"
    printf -- '-- 涉表: sys_task\n'
    printf -- '-- 任务码: 见 backup-index.tsv（登记成功后追加）\n'
    mysqldump $DB_ARGS --no-tablespaces "$DB_NAME" sys_task
  } > "$GF" 2>/dev/null
  GMD="$(md5sum "$GF" | cut -d' ' -f1)"; GSZ="$(stat -c%s "$GF")"; GTC="$(grep -c 'CREATE TABLE' "$GF" || true)"
  echo "guard 备份: $(basename "$GF")  md5=$GMD  bytes=$GSZ"
fi

# 2) 取号 + 插入（撞号自动重试）
ATTEMPT=1; OK=0; CODE=""
while [[ $ATTEMPT -le $MAX_ATTEMPT ]]; do
  if [[ "$SEQ_MODE" == redis ]] && redis_alive; then
    NUM="$(redis_next)"
  else
    [[ "$SEQ_MODE" == redis ]] && echo "Redis 不可达（$REDIS_HOST:$REDIS_PORT db$REDIS_DB），回退 db 模式取号" >&2
    NUM=$(( $(seq_floor) + 1 ))
  fi
  [[ "$NUM" =~ ^[0-9]+$ ]] || { echo "取号失败：得到非数字序号 '$NUM'" >&2; exit 1; }
  CODE="$(code_of "$NUM")"
  if OUT="$(mysql_run "$(insert_sql_for "$CODE")" 2>&1)"; then OK=1; break; fi
  if grep -qE 'Duplicate entry|1062' <<<"$OUT"; then
    echo "第 $ATTEMPT 次取号撞车（$CODE），重试…" >&2
    [[ "$SEQ_MODE" == redis ]] && redis_alive && redis-cli "${REDIS_ARGS[@]}" INCR "$SEQ_KEY" >/dev/null 2>&1 || true
    ATTEMPT=$((ATTEMPT+1)); sleep 1; continue
  fi
  echo "登记失败: $OUT" >&2; exit 1
done
[[ $OK -eq 1 ]] || { echo "取号连续 $MAX_ATTEMPT 次撞车，放弃（请稍后重试）" >&2; exit 1; }

# 3) 回查：码格式 + 当日最大号
CODE="$(mysql_run "SELECT task_code FROM sys_task WHERE title='$(esc "$TITLE")' ORDER BY task_id DESC LIMIT 1;")"
if [[ ! "$CODE" =~ ^${MODULE}-[0-9]{8}-[0-9]{3}$ ]]; then
  echo "回查失败：task_code 格式异常 → '$CODE'（期望 ^${MODULE}-YYYYMMDD-NNN$）" >&2; exit 1
fi
MAXCODE="$(mysql_run "SELECT task_code FROM sys_task WHERE task_code LIKE '${PREFIX}%' ORDER BY CAST(SUBSTRING_INDEX(task_code,'-',-1) AS UNSIGNED) DESC LIMIT 1;")"
FLOORCODE="$(code_of "$(seq_floor)")"
if [[ "$CODE" != "$MAXCODE" ]]; then
  echo "回查失败：'$CODE' 不是当日最大号（当前最大 '$MAXCODE'）—— 可能被并行会话抢先，请核对" >&2; exit 1
fi
if [[ "$CODE" < "$FLOORCODE" ]]; then
  echo "回查失败：'$CODE' 小于当天已用号下限 '$FLOORCODE'（库被回滚过？）请核对" >&2; exit 1
fi

# 4) 追加索引（拿到真码后才写，保证索引准确）
if [[ $DO_BACKUP -eq 1 && -n "${GF:-}" ]]; then
  printf '%s\tguard\t%s\t%s\t%s\t%s\t%s\t%s\n' "$TS_FULL" "$(basename "$GF")" "$GMD" "$GSZ" "$GTC" "$BY" "$CODE" >> "$BACKUP_DIR/backup-index.tsv"
fi

echo "登记成功: $CODE"
echo "取码模式: $SEQ_MODE ｜ 优先级: $PRIORITY ｜ 模块: $MODULE ｜ 登记人: $BY"
echo "提示: 若并发登记，请以本脚本回查结果为准（唯一约束是最后一道保险）"
