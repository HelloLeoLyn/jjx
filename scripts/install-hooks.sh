#!/usr/bin/env bash
# 安装本仓库的 git 闸门（每台机器 / 每个 clone 跑一次）
#   bash scripts/install-hooks.sh
# 卸载：git config --unset core.hooksPath
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

chmod +x scripts/hooks/* 2>/dev/null || true
git config core.hooksPath scripts/hooks

echo "✓ core.hooksPath = $(git config --get core.hooksPath)"
echo "  已启用钩子:"
for f in scripts/hooks/*; do
  printf '    - %s\n' "$(basename "$f")"
done
echo
echo "闸门内容（详见 AGENTS.md / CONVENTIONS.md §5）:"
echo "  pre-commit  拦：jjx-docs/sql|standards 下的删除/移动、状态魔法值基线新增或放大"
echo "  commit-msg  拦：① 必须带任务码 dev-YYYYMMDD-NNN ② 该码必须真实存在于 sys_task（用只读账号 jjx_ro 校验；库不可达时只提醒不阻塞）"
echo "                  关闭：git config jjx.requireTaskCode false ／ jjx.verifyTaskCode false"
echo
echo "临时跳过单次提交：git commit --no-verify"
echo "关闭任务码硬拦截（本 clone）：git config jjx.requireTaskCode false"
