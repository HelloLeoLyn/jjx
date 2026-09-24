#!/usr/bin/env bash
# task-register.sh —— 开发任务登记唯一入口（sys_task）
#
# 用途: 原子取号 + 撞唯一约束自动重试 + 登记后回查格式/排序 + 改库前 sys_task 表级 guard 备份
# 危险等级: 🔴 改数据库（INSERT sys_task） + 🟡 写备份文件/索引
# 前置: mysql 可连（默认 -uroot -p123456，可用 JJX_DB_ARGS 覆盖）；在仓库任意目录运行
#
# 背景（2026-09-24 立）: 多 agent（OpenClaw/Hermes/Codex）并行登记，各自 MAX+1 取号必然撞车；
#   且手写 SQL 出现过两类事故：① LPAD(@base+n,3,'0') 被当小数 → 畸形码 dev-YYYYMMDD-14.
#   ② INSERT...SELECT...FROM sys_task 少聚合 → 插多行 → 整条回滚。本脚本把这两类坑封死。
set -euo pipefail

DB_ARGS="${JJX_DB_ARGS:--uroot -p123456}"
DB_NAME="${JJX_DB_NAME:-jjx_erp_db}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$REPO_ROOT/jjx-docs/sql/backups"
MAX_ATTEMPT=3

usage() {
  cat <<'EOF'
用途: 登记开发任务到 sys_task（唯一入口：原子取号 + 撞号重试 + 回查 + guard 备份）
危险等级: 🔴 改数据库（INSERT sys_task）+ 🟡 写备份文件/索引
前置: mysql 可连（默认 -uroot -p123456，JJX_DB_ARGS 可覆盖）；建议在仓库根执行以正确落地备份

用法:
  bash scripts/task-register.sh --title "任务标题" [选项]

选项:
  --title <文本>       必填，任务标题（会做单引号转义）
  --priority <P0..P3>  优先级，默认 P2
  --module <mod>       看板模块，默认 dev
  --type <TYPE>        任务类型，默认 DEV
  --by <agent>         登记人，默认 dahuang
  --desc <文本>        描述（可选）
  --desc-file <文件>   从文件读描述（可选，优先于 --desc）
  --no-backup          跳过 guard 备份（不推荐）
  --dry-run            只打印将执行的 SQL 与备份路径，不落库不写文件
  -h, --help           本帮助

行为:
  1) 改库前：mysqldump 表级 guard 备份 → jjx-docs/sql/backups/sys_task_register_<时间>_guard.sql
  2) 取号+插入用**单条聚合 SQL**（MAX(...)+1，CAST 成 UNSIGNED 再 LPAD）
  3) 撞唯一约束（Duplicate entry / 1062）→ 自动重试，最多 3 次
  4) 登记后回查：task_code 必须匹配 ^<module>-YYYYMMDD-NNN$ 且是当日最大号；否则报错退出
  5) 备份与索引（backup-index.tsv）在拿到真码后追加，保证索引里的任务码准确

退出码: 0=登记成功（stdout 打印 task_code）；1=参数/前置/登记失败
EOF
}

TITLE=""; PRIORITY="P2"; MODULE="dev"; TYPE="DEV"; BY="dahuang"; DESC=""; NO_BACKUP=0; DRY=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --title) TITLE="${2:-}"; shift 2 ;;
    --priority) PRIORITY="${2:-}"; shift 2 ;;
    --module) MODULE="${2:-}"; shift 2 ;;
    --type) TYPE="${2:-}"; shift 2 ;;
    --by) BY="${2:-}"; shift 2 ;;
    --desc) DESC="${2:-}"; shift 2 ;;
    --desc-file) DESC="$(cat "${2:-/dev/null}")"; shift 2 ;;
    --no-backup) NO_BACKUP=1; shift ;;
    --dry-run) DRY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage; exit 1 ;;
  esac
done
[[ -n "$TITLE" ]] || { echo "必须提供 --title" >&2; usage; exit 1; }

esc() { printf '%s' "$1" | sed "s/'/''/g"; }
DAY="$(date '+%Y%m%d')"
PREFIX="${MODULE}-${DAY}-"

SQL="INSERT INTO sys_task (task_code,task_type,kanban_module,title,status,priority,create_by,description,create_time)
SELECT CONCAT('${PREFIX}', LPAD(CAST(COALESCE(MAX(CAST(SUBSTRING_INDEX(task_code,'-',-1) AS UNSIGNED)),0)+1 AS UNSIGNED),3,'0')),
       '$(esc "$TYPE")','$(esc "$MODULE")','$(esc "$TITLE")',0,'$(esc "$PRIORITY")','$(esc "$BY")','$(esc "$DESC")',NOW()
FROM sys_task WHERE task_code LIKE '${PREFIX}%';"

if [[ $DRY -eq 1 ]]; then
  echo "[dry-run] 备份路径: $BACKUP_DIR/sys_task_register_${DAY}-<HHMM>_guard.sql"
  echo "[dry-run] 将执行:"; echo "$SQL"
  exit 0
fi

mysql_run() { mysql $DB_ARGS --batch --skip-column-names "$DB_NAME" -e "$1"; }

# 1) 改库前 guard 备份
if [[ $NO_BACKUP -eq 0 ]]; then
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

# 2) 插入（撞号自动重试）
ATTEMPT=1; OK=0
while [[ $ATTEMPT -le $MAX_ATTEMPT ]]; do
  if OUT="$(mysql_run "$SQL" 2>&1)"; then OK=1; break; fi
  if grep -qE 'Duplicate entry|1062' <<<"$OUT"; then
    echo "第 $ATTEMPT 次取号撞车，重试…" >&2; ATTEMPT=$((ATTEMPT+1)); sleep 1; continue
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
if [[ "$CODE" != "$MAXCODE" ]]; then
  echo "回查失败：'$CODE' 不是当日最大号（当前最大 '$MAXCODE'）—— 可能被并行会话抢先，请核对" >&2; exit 1
fi

# 4) 追加索引（拿到真码后才写，保证索引准确）
if [[ $NO_BACKUP -eq 0 && -n "${GF:-}" ]]; then
  printf '%s\tguard\t%s\t%s\t%s\t%s\t%s\t%s\n' "$TS_FULL" "$(basename "$GF")" "$GMD" "$GSZ" "$GTC" "$BY" "$CODE" >> "$BACKUP_DIR/backup-index.tsv"
fi

echo "登记成功: $CODE"
echo "标题: $TITLE ｜ 优先级: $PRIORITY ｜ 模块: $MODULE ｜ 登记人: $BY"
echo "提示: 若并发登记，请以本脚本回查结果为准（唯一约束是最后一道保险）"
