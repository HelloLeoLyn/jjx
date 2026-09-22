#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="/etc/jjx-server/jjx.env"
JAR_FILE="/home/administrator/jjx/jjx-server/target/jjx-server-1.0.0.jar"

if [[ ! -r "$ENV_FILE" ]]; then
  echo "缺少 AI 配置文件：$ENV_FILE" >&2
  exit 1
fi

if [[ ! -f "$JAR_FILE" ]]; then
  echo "找不到后端 JAR：$JAR_FILE" >&2
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

exec java -jar "$JAR_FILE"
