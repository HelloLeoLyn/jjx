#!/usr/bin/env bash
set -euo pipefail

# JJX 项目专用 OCR 启动脚本：不读取其他项目的虚拟环境、模型缓存或上传目录。
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ ! -x "$SCRIPT_DIR/.venv/bin/uvicorn" ]]; then
  echo "未找到本项目 OCR 虚拟环境：$SCRIPT_DIR/.venv" >&2
  echo "请先执行：python3.11 -m venv .venv && .venv/bin/pip install -r requirements.txt" >&2
  exit 1
fi

OCR_HOST="${JJX_OCR_HOST:-127.0.0.1}"
OCR_PORT="${JJX_OCR_PORT:-8866}"
exec "$SCRIPT_DIR/.venv/bin/uvicorn" app:app --host "$OCR_HOST" --port "$OCR_PORT"
