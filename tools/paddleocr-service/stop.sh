#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MATCH="${SCRIPT_DIR}/.venv/bin/uvicorn app:app"
PIDS="$(pgrep -f "$MATCH" || true)"

if [[ -z "$PIDS" ]]; then
  echo "JJX PaddleOCR 未运行"
  exit 0
fi

echo "停止 JJX PaddleOCR：$PIDS"
kill $PIDS
for _ in {1..20}; do
  sleep 0.25
  pgrep -f "$MATCH" >/dev/null 2>&1 || { echo "已停止"; exit 0; }
done
echo "服务未在 5 秒内退出，请检查进程" >&2
exit 1
