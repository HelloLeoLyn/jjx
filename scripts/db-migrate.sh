#!/usr/bin/env bash
# JJX 迁移唯一执行通道：预览 → 手工备份校验 → 逐条执行/记账 → 同名归档。
# 危险等级：🟢 --status/不带--yes；🔴 --all/单文件/--retry执行；🟡 --record记账及归档。
# 前置：bash、python3、mysql、flock；写操作须真实任务码。备份由用户手工完成，排除hr_employee。
# 手册：jjx-docs/guides/scripts-commands-20260914.md；dev-20261011-014。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_HOST="${DB_HOST:-127.0.0.1}"; DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"; DB_NAME="${DB_NAME:-jjx_erp_db}"
export MYSQL_PWD="${DB_PASS:-123456}"
MIG_DIR="${JJX_MIGRATIONS_DIR:-$REPO_ROOT/jjx-docs/sql/migrations}"
BACKUP_DIR="${JJX_BACKUP_DIR:-$REPO_ROOT/jjx-docs/sql/backups}"
BACKUP="${JJX_MIGRATE_BACKUP:-}"
MYSQL=(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --connect-timeout=5 --default-character-set=utf8mb4 -N -B "$DB_NAME")
MODE=status; YES=0; TASK=''; FILE=''; NUMBER=''; TAG=''; MODE_SET=0
say() { printf '%s\n' "$*"; }
die() { say "✘ $*" >&2; exit 1; }
q() { "${MYSQL[@]}" -e "$1"; } # 不吞数据库错误；每个调用必须检查退出码。
usage() {
  cat <<'EOF'
用途：查看并补齐迁移；成功记账、回查后归档到 migrations/applied/。
危险等级：🟢 默认预览/--status；🔴 SQL执行；🟡 --record记账及归档。
前置：bash、python3、mysql、flock；写操作须真实任务码；SQL执行须手工全库备份（排除hr_employee）。
用法：
  bash scripts/db-migrate.sh --status
  bash scripts/db-migrate.sh --all                         # 只预览
  bash scripts/db-migrate.sh --all --yes --task dev-YYYYMMDD-NNN --backup <全库备份.sql>
  bash scripts/db-migrate.sh <NN_x.sql> [--yes --task dev-... --backup <备份.sql>]
  bash scripts/db-migrate.sh --record <NN> --yes --task dev-...  # 人工核实SQL已完整生效后补记账/归档，不执行SQL
  bash scripts/db-migrate.sh --retry <NN> --yes --task dev-... --backup <备份.sql>
选项：--tag <说明>（兼容）；--help；不带--yes不写文件、不写库。
--retry仅用于人工核查失败/中断后的显式重试；SQL成功但未记账必须用--record。
以目标库ops.schema.applied为准，扫描待办目录及applied/；低号遗漏仍执行，另一数据库也会执行未记账的归档文件。
同号多文件、内容变更、连接/执行/记账/归档失败均中止；DDL可能部分生效，不自动回滚。
执行锁覆盖同机同目标库及同一迁移目录；不支持不同主机同时迁移同一库。
恢复状态保存在仓库.tmp/db-migrate/（按目标库隔离），未恢复前不要清除此目录。
脚本不自动备份、不启停服务、不自动提交Git；用户执行后提交内容不变的同名归档移动。
EOF
}
mode() { [[ $MODE_SET -eq 0 ]] || die '只能选一种模式：--status/--all/单文件/--record/--retry'; MODE="$1"; MODE_SET=1; }
while (($#)); do
  case "$1" in
    --status) mode status ;;
    --all) mode all ;;
    --record|--retry) (($# >= 2)) || die "$1 缺少编号"; mode "${1#--}"; NUMBER="$2"; shift ;;
    --task|--backup|--tag)
      (($# >= 2)) && [[ -n $2 && $2 != --* ]] || die "$1 缺少参数"
      case "$1" in --task) TASK="$2" ;; --backup) BACKUP="$2" ;; --tag) TAG="$2" ;; esac; shift ;;
    --yes|-y) YES=1 ;;
    --help|-h) usage; exit 0 ;;
    -*) die "未知参数：$1" ;;
    *) mode file; FILE="$1" ;;
  esac
  shift
done
[[ $DB_NAME =~ ^[A-Za-z0-9_]+$ ]] || die '数据库名称只能包含字母、数字、下划线'
[[ $MODE != status || $YES -eq 0 ]] || die '--status 不接受 --yes'
if [[ $MODE == record || $MODE == retry ]]; then
  [[ $NUMBER =~ ^[0-9]+$ ]] || die '迁移编号必须是数字'
  NUMBER="$(python3 -c 'import sys; print(int(sys.argv[1]))' "$NUMBER")"
fi
[[ -d $MIG_DIR && ! -L $MIG_DIR ]] || die "迁移目录不存在或是软链接：$MIG_DIR"
MIG_DIR="$(cd "$MIG_DIR" && pwd -P)"
ARCHIVE="$MIG_DIR/applied"
[[ ! -L $ARCHIVE && (! -e $ARCHIVE || -d $ARCHIVE) ]] || die 'applied必须是普通目录'
TARGET_KEY="$(printf '%s\0%s\0%s' "$DB_HOST" "$DB_PORT" "$DB_NAME" | sha256sum | cut -d' ' -f1)"
STATE_DIR="$REPO_ROOT/.tmp/db-migrate/$TARGET_KEY"
JOURNAL="$STATE_DIR/inflight.json"
DONE=(); SKIPPED=(); PLAN=(); CURRENT=''; FAILED=''; SUMMARY=0
declare -A FINISHED=()
summary() {
  local rc=$? entry
  if [[ $SUMMARY -eq 1 ]]; then
    say '════ 执行摘要 ════'
    say "目标库：$DB_NAME@$DB_HOST:$DB_PORT"
    for entry in "${DONE[@]}"; do say "成功：$entry"; done
    for entry in "${SKIPPED[@]}"; do say "已记账，未重复执行：$entry"; done
    [[ -z $FAILED ]] || say "失败 / 待恢复：$FAILED"
    for entry in "${PLAN[@]}"; do
      [[ ${FINISHED[$entry]:-0} == 1 || $entry == "$CURRENT" ]] || say "未执行：$entry"
    done
    [[ $rc -eq 0 ]] || say '已成功的迁移保留；失败SQL可能部分生效。核查后使用--record或--retry，禁止自动重跑。'
  fi
}
trap summary EXIT

# 写模式取得两把同机锁：同库跨checkout互斥、同目录跨数据库归档互斥。
if [[ $YES -eq 1 ]]; then
  command -v flock >/dev/null || die '缺少flock'
  mkdir -p "$REPO_ROOT/.tmp/db-migrate"
  DB_LOCK="${TMPDIR:-/tmp}/jjx-db-migrate-$TARGET_KEY.lock"
  [[ ! -L $DB_LOCK ]] || die '数据库锁文件不能是软链接'
  exec 9>"$DB_LOCK"
  flock -n 9 || die '同机另一个会话正在迁移此数据库'
  DIR_KEY="$(printf '%s' "$MIG_DIR" | sha256sum | cut -d' ' -f1)"
  exec 8>"$REPO_ROOT/.tmp/db-migrate/directory-$DIR_KEY.lock"
  flock -n 8 || die '另一个会话正在使用此迁移目录'
fi

RAW_APPLIED=''; APPLIED=''; VERSION=''
load_ledger() {
  RAW_APPLIED="$(q "SELECT config_value FROM sys_config WHERE config_key='ops.schema.applied';")" || die '读取已应用集合失败'
  VERSION="$(q "SELECT config_value FROM sys_config WHERE config_key='ops.schema.version';")" || die '读取迁移版本失败'
  APPLIED="$(python3 - "$RAW_APPLIED" <<'PY'
import sys
raw=sys.argv[1]
try:
    parts=raw.split(',') if raw else []
    if any(not x.isascii() or not x.isdigit() for x in parts): raise ValueError('编号必须是逗号分隔的整数')
    print(','.join(map(str, sorted({int(x) for x in parts}))))
except ValueError as e:
    print(f'已应用集合格式错误：{e}', file=sys.stderr); sys.exit(1)
PY
)" || die '账本内容异常'
  [[ -z $VERSION || $VERSION =~ ^[0-9]+$ ]] || die '最大迁移版本格式错误'
}
is_applied() { [[ ",$APPLIED," == *",$1,"* ]]; }
load_ledger

# 不按最大号推断，已归档目录也纳入扫描；重复编号直接拒绝。
INVENTORY="$(python3 - "$MIG_DIR" <<'PY'
import hashlib, pathlib, re, sys
root=pathlib.Path(sys.argv[1]); rows=[]; seen={}
for folder in (root, root/'applied'):
    if not folder.exists(): continue
    for p in folder.glob('*.sql'):
        if p.is_symlink() or not p.is_file() or not re.fullmatch(r'[0-9]+_[A-Za-z0-9][A-Za-z0-9._-]*\.sql', p.name):
            sys.exit(f'非法迁移文件：{p}')
        n=int(p.name.split('_',1)[0])
        if n in seen: sys.exit(f'迁移编号重复：{seen[n]} / {p}；每个文件必须独占编号')
        if not p.stat().st_size: sys.exit(f'迁移文件为空：{p}')
        seen[n]=p
        rows.append((n,p,hashlib.sha256(p.read_bytes()).hexdigest()))
for n,p,h in sorted(rows): print(f'{n}\t{p}\t{h}')
PY
)" || die '迁移目录检查失败'
NUMBERS=(); PATHS=(); HASHES=()
while IFS=$'\t' read -r n p h; do
  [[ -n $n ]] || continue
  NUMBERS+=("$n"); PATHS+=("$p"); HASHES+=("$h")
done <<< "$INVENTORY"
STATE_NN=''; STATE_FILE=''; STATE_HASH=''; STATE_STAGE=''
read_journal() {
  local state
  [[ -f $JOURNAL ]] || return 0
  state="$(python3 - "$JOURNAL" <<'PY'
import json,re,sys
try:
    s=json.load(open(sys.argv[1]))
    assert isinstance(s['number'],int) and s['number']>=0
    assert re.fullmatch(r'[0-9]+_[A-Za-z0-9][A-Za-z0-9._-]*\.sql',s['file'])
    assert re.fullmatch(r'[a-f0-9]{64}',s['sha256'])
    assert s['stage'] in ('running','failed','executed','recorded')
    print(f"{s['number']}\t{s['file']}\t{s['sha256']}\t{s['stage']}")
except (OSError,ValueError,KeyError,AssertionError,TypeError) as e:
    print(f'恢复记录不可读：{e}',file=sys.stderr); sys.exit(1)
PY
)" || die '恢复记录异常，不得自动重跑'
  IFS=$'\t' read -r STATE_NN STATE_FILE STATE_HASH STATE_STAGE <<< "$state"
}
write_journal() {
  mkdir -p "$STATE_DIR"
  python3 - "$JOURNAL" "$1" "$2" "$3" "$4" "$TASK" <<'PY'
import json,os,sys,tempfile
path,n,name,h,stage,task=sys.argv[1:]
fd,tmp=tempfile.mkstemp(dir=os.path.dirname(path),prefix='.inflight-')
try:
    with os.fdopen(fd,'w') as f:
        json.dump(dict(number=int(n),file=name,sha256=h,stage=stage,task=task),f)
        f.flush(); os.fsync(f.fileno())
    os.replace(tmp,path)
    d=os.open(os.path.dirname(path),os.O_RDONLY); os.fsync(d); os.close(d)
finally:
    if os.path.exists(tmp): os.unlink(tmp)
PY
  STATE_NN="$1"; STATE_FILE="$2"; STATE_HASH="$3"; STATE_STAGE="$4"
}
read_journal
say "迁移目录: ${MIG_DIR#$REPO_ROOT/}（含 applied/）"
say "库        : $DB_NAME@$DB_HOST:$DB_PORT"
say "已应用: ${APPLIED:-无}"
if ((${#NUMBERS[@]})); then say "目录最大号: ${NUMBERS[-1]}"; else say '目录最大号: 无'; fi
PENDING=(); TO_ARCHIVE=()
for i in "${!NUMBERS[@]}"; do
  if is_applied "${NUMBERS[i]}"; then
    [[ ${PATHS[i]%/*} != "$MIG_DIR" ]] || TO_ARCHIVE+=("${PATHS[i]##*/}")
  else PENDING+=("${PATHS[i]##*/}"); fi
done
say "待执行迁移 ${#PENDING[@]} 个："
for f in "${PENDING[@]}"; do say "    - $f"; done
say "已记账待归档 ${#TO_ARCHIVE[@]} 个："
for f in "${TO_ARCHIVE[@]}"; do say "    - $f"; done
[[ -z $STATE_NN ]] || say "恢复记录: $STATE_NN $STATE_FILE $STATE_STAGE（未恢复前禁止重跑SQL）"
[[ -n $RAW_APPLIED || -z $VERSION ]] || say '⚠ 只有最大号、缺少显式集合；必须人工核对逐个--record，不能把低号都当成已执行'
[[ $MODE != status ]] || exit 0
SELECTED=()
for i in "${!NUMBERS[@]}"; do
  case "$MODE" in
    all) if ! is_applied "${NUMBERS[i]}" || [[ ${PATHS[i]%/*} == "$MIG_DIR" ]]; then SELECTED+=("$i"); fi ;;
    file) [[ ${PATHS[i]##*/} != "${FILE##*/}" ]] || SELECTED+=("$i") ;;
    record|retry) [[ ${NUMBERS[i]} != "$NUMBER" ]] || SELECTED+=("$i") ;;
  esac
done
if [[ $MODE == file ]]; then
  ((${#SELECTED[@]} == 1)) || die "找不到迁移：$FILE"
  if [[ $FILE == */* ]]; then
    resolved="$(realpath -e "$FILE")" || die "迁移路径不存在：$FILE"
    [[ $resolved == "${PATHS[SELECTED[0]]}" ]] || die '文件必须位于待办或applied目录'
  fi
fi
[[ $MODE != retry || ${#SELECTED[@]} == 1 ]] || die '找不到要重试的迁移'
if [[ $YES -eq 0 ]]; then
  say '仅预览：未执行SQL、未记账、未归档。执行时加 --yes --task dev-YYYYMMDD-NNN --backup <手工备份.sql>。'
  [[ $MODE != record ]] || say "计划人工补登记编号：$NUMBER（须先核实SQL完整生效）"
  exit 0
fi
[[ $TASK =~ ^dev-[0-9]{8}-[0-9]{3}$ ]] || die '写操作必须带真实任务码 --task dev-YYYYMMDD-NNN'
found="$(q "SELECT 1 FROM sys_task WHERE task_code='$TASK' LIMIT 1;")" || die '任务查询失败'
[[ $found == 1 ]] || die "任务不存在：$TASK"
[[ -n $RAW_APPLIED || -z $VERSION || $MODE == record ]] || die '缺少显式已应用集合，禁止自动按最大号推断'
if [[ -n $STATE_NN ]]; then
  if [[ $MODE == retry ]]; then
    [[ $STATE_NN == "$NUMBER" && ($STATE_STAGE == running || $STATE_STAGE == failed) ]] || die '仅失败或中断记录可显式重试；SQL已成功时请--record'
  elif [[ $MODE == record ]]; then
    [[ $STATE_NN == "$NUMBER" ]] || die "先恢复迁移 $STATE_NN"
  elif ! is_applied "$STATE_NN"; then
    die "迁移 $STATE_NN 有未解决记录（$STATE_STAGE）；人工核查后--record或--retry"
  else
    # 已记账但未归档：必须选中该文件才能清除恢复记录。
    matched=0
    for i in "${SELECTED[@]}"; do [[ ${NUMBERS[i]} != "$STATE_NN" ]] || matched=1; done
    [[ $matched -eq 1 ]] || die "先补归档已记账迁移 $STATE_NN"
  fi
elif [[ $MODE == retry ]]; then die '没有失败/中断记录，不允许--retry'; fi
# 先收尾已经记账的中断文件，避免被后续新迁移覆盖恢复记录。
if [[ -n $STATE_NN ]] && is_applied "$STATE_NN" && [[ $MODE != record ]]; then
  recovery=(); rest=()
  for i in "${SELECTED[@]}"; do
    if [[ ${NUMBERS[i]} == "$STATE_NN" ]]; then recovery+=("$i"); else rest+=("$i"); fi
  done
  SELECTED=("${recovery[@]}" "${rest[@]}")
fi

# 新执行写入文件指纹，旧编号无指纹仅提示，不伪造旧执行证据。
check_fingerprint() {
  local meta expected
  meta="$(q "SELECT config_value FROM sys_config WHERE config_key='ops.schema.file.$1';")" || die '读取迁移指纹失败'
  expected="$(printf '{"file":"%s","sha256":"%s"}' "$2" "$3")"
  [[ -z $meta || $meta == "$expected" ]] || die "迁移 $1 内容/文件名与执行时指纹不同，禁止执行或归档"
}
record_applied() {
  local n="$1" name="${2:-}" hash="${3:-}" old="$APPLIED" meta_sql='' got value
  if [[ -n $name ]]; then
    value="$(printf '{"file":"%s","sha256":"%s"}' "$name" "$hash")"
    meta_sql="INSERT INTO sys_config (config_key,config_value,config_name,config_group,remark,sort_order,is_active)
      VALUES ('ops.schema.file.$n','$value','迁移文件指纹','ops','任务 $TASK',0,1)
      ON DUPLICATE KEY UPDATE config_value=config_value;"
  fi
  q "START TRANSACTION;
    INSERT INTO sys_config (config_key,config_value,config_name,config_group,remark,sort_order,is_active)
    VALUES ('ops.schema.applied','$n','已应用迁移集合','ops','由db-migrate.sh维护',0,1)
    ON DUPLICATE KEY UPDATE config_value=IF(FIND_IN_SET('$n',config_value)>0,config_value,CONCAT_WS(',',NULLIF(config_value,''),'$n')),update_time=NOW();
    INSERT INTO sys_config (config_key,config_value,config_name,config_group,remark,sort_order,is_active)
    VALUES ('ops.schema.version','$n','已应用迁移版本(最大)','ops','由db-migrate.sh维护',0,1)
    ON DUPLICATE KEY UPDATE config_value=GREATEST(CAST(config_value AS UNSIGNED),$n),update_time=NOW();
    $meta_sql
    COMMIT;" >/dev/null || die "迁移 $n 记账失败；SQL可能已执行，核查后使用--record，禁止自动重跑"
  load_ledger
  is_applied "$n" || die "迁移 $n 记账回查失败，禁止归档/重跑"
  for got in ${old//,/ }; do is_applied "$got" || die "历史记账编号 $got 丢失"; done
  [[ -z $name ]] || check_fingerprint "$n" "$name" "$hash"
}
archive_file() {
  local p="$1" n="$2" hash="$3" now
  now="$(sha256sum "$p" | cut -d' ' -f1)" || die '归档前读取文件失败'
  [[ $now == "$hash" ]] || die '执行过程中SQL文件发生修改，禁止归档'
  if [[ ${p%/*} == "$MIG_DIR" ]]; then
    mkdir -p "$ARCHIVE"
    [[ ! -e "$ARCHIVE/${p##*/}" && ! -L "$ARCHIVE/${p##*/}" ]] || die "归档目标已存在，禁止覆盖：${p##*/}"
    mv -- "$p" "$ARCHIVE/${p##*/}" || die "迁移 $n 已记账但归档失败，下次仅补归档"
    say "已归档：applied/${p##*/}"
  else say "已在归档目录：${p##*/}"; fi
  if [[ $STATE_NN == "$n" ]]; then
    # 此处只会处理已通过编号匹配的恢复记录或本次当前迁移。
    rm -f -- "$JOURNAL"
    STATE_NN=''; STATE_STAGE=''
  fi
}
validate_backup() {
  if [[ -z $BACKUP ]]; then
    BACKUP="$(python3 - "$BACKUP_DIR" <<'PY'
import datetime,pathlib,sys
p=pathlib.Path(sys.argv[1]); files=list(p.glob('jjx_erp_db_backup_'+datetime.date.today().strftime('%Y%m%d')+'-*.sql'))
if files: print(max(files,key=lambda f:f.stat().st_mtime))
PY
)" || die '查找手工备份失败'
  fi
  [[ -n $BACKUP && -f $BACKUP && ! -L $BACKUP ]] || die '缺少手工全库备份，请通过--backup指定；脚本不会自动生成备份'
  python3 - "$BACKUP" <<'PY'
import pathlib,re,sys
p=pathlib.Path(sys.argv[1]); count=0
if p.stat().st_size<=1024: sys.exit('手工备份过小或为空')
with p.open(errors='replace') as f:
    for line in f:
        if re.match(r'\s*CREATE TABLE\b',line,re.I): count+=1
        if re.match(r'\s*(?:CREATE TABLE(?: IF NOT EXISTS)?|INSERT INTO|REPLACE INTO)\s+(?:`?\w+`?\.)?`?hr_employee`?(?:\s|\()',line,re.I):
            sys.exit('备份包含hr_employee，按用户规则拒绝使用')
if not count: sys.exit('手工备份未包含建表语句')
print(f'手工备份：{p} / {p.stat().st_size}B / {count}张表')
PY
  say "备份SHA256：$(sha256sum "$BACKUP" | cut -d' ' -f1)"
}

# 全批次预检，确认所有选中文件/历史指纹后才动库。
NEEDS_SQL=0
for i in "${SELECTED[@]}"; do
  n="${NUMBERS[i]}"; p="${PATHS[i]}"; h="${HASHES[i]}"
  check_fingerprint "$n" "${p##*/}" "$h"
  if [[ $STATE_NN == "$n" && $MODE != retry ]]; then
    [[ $STATE_FILE == "${p##*/}" && $STATE_HASH == "$h" ]] || die '恢复记录与当前文件不一致，先恢复原SQL文件再处理'
  fi
  if [[ $MODE != record ]] && ! is_applied "$n"; then NEEDS_SQL=1; fi
  PLAN+=("${p##*/}")
done
[[ $NEEDS_SQL -eq 0 ]] || validate_backup
SUMMARY=1
if [[ $MODE == record && ${#SELECTED[@]} -eq 0 ]]; then
  [[ -z $STATE_NN ]] || die '恢复记录对应的SQL文件丢失，禁止盲目补记账'
  record_applied "$NUMBER"
  DONE+=("编号 $NUMBER（人工补登记，无本地文件）")
fi
for i in "${SELECTED[@]}"; do
  n="${NUMBERS[i]}"; p="${PATHS[i]}"; h="${HASHES[i]}"; CURRENT="${p##*/}"; FAILED="$CURRENT"
  [[ -f $p && ! -L $p && $(sha256sum "$p" | cut -d' ' -f1) == "$h" ]] || die '预检后SQL文件发生修改/丢失'
  if [[ $MODE == record ]]; then
    write_journal "$n" "$CURRENT" "$h" executed
    record_applied "$n" "$CURRENT" "$h"
    write_journal "$n" "$CURRENT" "$h" recorded
    archive_file "$p" "$n" "$h"
    DONE+=("$CURRENT"); FINISHED["$CURRENT"]=1
  elif is_applied "$n"; then
    archive_file "$p" "$n" "$h"
    SKIPPED+=("$CURRENT"); FINISHED["$CURRENT"]=1
  else
    say "执行：$CURRENT${TAG:+（$TAG）}"
    write_journal "$n" "$CURRENT" "$h" running
    if ! "${MYSQL[@]}" < "$p"; then
      write_journal "$n" "$CURRENT" "$h" failed
      die "迁移 $n 执行失败；文件保留，后续不执行；SQL可能已部分生效"
    fi
    write_journal "$n" "$CURRENT" "$h" executed
    record_applied "$n" "$CURRENT" "$h"
    write_journal "$n" "$CURRENT" "$h" recorded
    archive_file "$p" "$n" "$h"
    DONE+=("$CURRENT"); FINISHED["$CURRENT"]=1
  fi
  FAILED=''; CURRENT=''
done
# 没有执行SQL时无需建表闸（只补归档/补登记）。不改变其现有只告警契约。
if [[ $NEEDS_SQL -eq 1 && -f $REPO_ROOT/scripts/check-model-baseline.sh ]]; then
  if bash "$REPO_ROOT/scripts/check-model-baseline.sh"; then say '建表闸复核通过';
  else say '⚠ 建表闸复核未通过；迁移已执行/记账，需按§15核查'; fi
fi
say '完成：成功记账的迁移已归档；未自动提交Git或启停服务。'
