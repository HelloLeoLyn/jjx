#!/usr/bin/env bash
# ============================================================================
# 开工自检（agent 动手前跑一条就够；CONVENTIONS §10）
#   bash scripts/agent-preflight.sh
# 只读、快速（不跑 maven/npm install/vue-tsc）。任一项失败 → exit 1。
# ============================================================================
set -uo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"

c_red=$'\033[31m'; c_grn=$'\033[32m'; c_yel=$'\033[33m'; c_off=$'\033[0m'
ok()  { printf '%s  ✓%s %s\n' "$c_grn" "$c_off" "$*"; }
bad() { printf '%s  ✘%s %s\n' "$c_red" "$c_off" "$*"; }
warn(){ printf '%s  ⚠%s %s\n' "$c_yel" "$c_off" "$*"; }
FAIL=0

echo "──────── 开工自检 ────────"

# 1. git 闸门
echo "[1/5] git 闸门"
if [ "$(git config --get core.hooksPath 2>/dev/null)" = "scripts/hooks" ]; then
  missing=0
  for h in pre-commit commit-msg; do
    [ -x "scripts/hooks/$h" ] || { missing=1; bad "scripts/hooks/$h 不可执行"; }
  done
  [ "$missing" -eq 0 ] && ok "core.hooksPath=scripts/hooks（pre-commit + commit-msg 就位）"
else
  bad "未安装闸门 → 跑：bash scripts/install-hooks.sh"; FAIL=1
fi

# 2. 数据库版本
echo "[2/5] 数据库迁移版本"
if out=$(bash scripts/db-migrate.sh --status 2>&1); then
  rec=$(printf '%s' "$out" | sed -n 's/^已应用版本: //p')
  mx=$(printf '%s' "$out" | sed -n 's/^目录最大号: //p')
  if [ -z "$rec" ]; then
    warn "库中未记录已应用版本（只能人工比对）→ 接管登记：bash scripts/db-migrate.sh --record <NN> --yes"; FAIL=1
  elif [ "$rec" = "$mx" ]; then
    ok "库与代码一致（版本 $rec / 目录最大 $mx）"
  else
    bad "有待执行迁移：库 $rec / 目录 $mx"
    printf '%s' "$out" | grep -E '^    - ' | sed 's/^/     /'
    warn "执行：bash scripts/db-migrate.sh <文件名> --yes --task dev-YYYYMMDD-NNN"; FAIL=1
  fi
else
  bad "连不上数据库或脚本报错"; FAIL=1
fi

# 3. 只读账号（commit-msg 真实性校验依赖它）
echo "[3/5] 只读账号 jjx_ro"
ro=$(MYSQL_PWD="${JJX_RO_PASS:-jjx_ro_2026}" mysql -h127.0.0.1 -u"${JJX_RO_USER:-jjx_ro}" \
      --connect-timeout=3 -N -B jjx_erp_db -e "SELECT 1;" 2>/dev/null)
if [ "$ro" = "1" ]; then
  ok "jjx_ro 可读（commit-msg 的任务码真实性校验生效）"
else
  warn "jjx_ro 不可用 → commit-msg 会跳过任务码真实性校验（只查格式）；重建见 CONVENTIONS §10"; FAIL=1
fi

# 4. 文档门禁
echo "[4/5] 文档门禁"
if dc=$(node scripts/check-docs.mjs 2>&1); then
  ok "$(printf '%s' "$dc" | tail -1)"
else
  printf '%s\n' "$dc" | sed 's/^/     /'; FAIL=1
fi

# 4. 工作区
echo "[5/5] 工作区状态"
dirty=$(git status --short | grep -vE '^\?\? (\.tmp/|node_modules/)' || true)
if [ -z "$dirty" ]; then
  ok "工作区干净"
else
  n=$(printf '%s\n' "$dirty" | grep -c .)
  warn "有 $n 项未提交改动——提交时只 add 自己动过的路径，别把别人的 WIP 混进去："
  printf '%s\n' "$dirty" | head -12 | sed 's/^/     /'
  [ "$n" -gt 12 ] && printf '     … 其余 %s 项\n' "$((n - 12))"
fi

echo "──────────────────────────"
if [ "$FAIL" -eq 0 ]; then
  printf '%s自检通过，可以动手%s\n' "$c_grn" "$c_off"
else
  printf '%s自检未通过：先处理上面标 ✘ 的项%s\n' "$c_red" "$c_off"
fi
exit "$FAIL"
