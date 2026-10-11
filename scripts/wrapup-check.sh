#!/usr/bin/env bash
# JJX 收尾自查（迁移 + 新表治理）——阶段收尾时跑一次，确认「有没有管」。
#   ✓ 迁移台账：待执行 0 / 已记账待归档 0（scripts/db-migrate.sh --status）
#   ✓ 新表清理归属：所有表都有归宿（scripts/db-clean-test-data.sh 只读体检的覆盖率校验）
#   ✓ 建表闸：无未批准新表（scripts/check-model-baseline.sh §15）
# 危险等级：🟢 只读（只调用上述三个只读入口，不写库、不落文件、不启停服务）。
# 前置：仓库内执行；数据库可连（连不上对应项报 ✘）。
# 退出码：0=全部通过；1=有缺口（用于阶段收尾判断）。
# 任务：dev-20261011-018；手册：jjx-docs/guides/scripts-commands-20260914.md
set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)" || exit 1
fail=0
say() { printf '%s\n' "$*"; }

# ① 迁移台账：待执行 / 已记账待归档（不按最大号，看显式集合）
mout="$(bash scripts/db-migrate.sh --status 2>&1)" || true
pending="$(printf '%s\n' "$mout" | grep -oE '待执行迁移 [0-9]+ 个' | grep -oE '[0-9]+' || echo 0)"
unfiled="$(printf '%s\n' "$mout" | grep -oE '已记账待归档 [0-9]+ 个' | grep -oE '[0-9]+' || echo 0)"
if printf '%s\n' "$mout" | grep -q '待执行迁移'; then
  if [ "${pending:-0}" -eq 0 ] && [ "${unfiled:-0}" -eq 0 ]; then
    say "✓ 迁移台账：待执行 0 / 待归档 0"
  else
    say "✘ 迁移台账：待执行 ${pending:-?} / 待归档 ${unfiled:-?}"
    printf '%s\n' "$mout" | grep -E '^    - ' | sed 's/^/    /'
    fail=1
  fi
else
  say "✘ 迁移台账：无法读取（数据库不可达？）"
  fail=1
fi

# ② 新表清理归属（覆盖率校验）
cout="$(bash scripts/db-clean-test-data.sh 2>&1)" || true
if printf '%s\n' "$cout" | grep -q '均有归宿'; then
  say "✓ 清理归属：所有表均有归宿"
elif printf '%s\n' "$cout" | grep -q '覆盖率校验未通过'; then
  say "✘ 清理归属：有表「无归宿」"
  printf '%s\n' "$cout" | grep -E '^   - ' | sed 's/^/    /'
  fail=1
else
  say "✘ 清理归属：无法判定（数据库不可达或脚本报错？）"
  fail=1
fi

# ③ 建表闸（§15 未批准新表）
if mbout="$(bash scripts/check-model-baseline.sh 2>&1)"; then
  say "✓ 建表闸：无未批准新表"
else
  say "✘ 建表闸：有未批准新表（详见 scripts/check-model-baseline.sh）"
  printf '%s\n' "$mbout" | grep -E '^  - ' | sed 's/^/    /'
  fail=1
fi

say ''
if [ "$fail" -eq 0 ]; then
  say '收尾自查通过：迁移 + 新表治理无缺口'
else
  say '收尾自查未通过：见上 ✘（逐项处理后重跑）'
fi
exit "$fail"
