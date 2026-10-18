#!/usr/bin/env bash
# 危险等级：🟡 启动后端进程（应用可能写日志、连接数据库）；--help 为只读。
# 前置：Bash、Java、已构建的后端 JAR；仅在明确需要启动后端时执行。
set -euo pipefail

usage() {
  cat <<'EOF'
用途：启动 JJX 后端，支持 Windows Git Bash 和 Linux。
危险等级：🟡 启动后端进程（应用可能写日志、连接数据库）；--help 为只读。
前置：Bash、Java、已构建的后端 JAR；确认需要启动后端后再执行。
用法：bash start-jjx.sh
      bash start-jjx.sh --help
环境变量：
  JJX_ENV_FILE  可选配置文件（Bash 语法）；显式指定时必须可读。
                未指定时尝试 /etc/jjx-server/jjx.env，缺失则使用现有环境变量。
  JJX_JAR_FILE  可选 JAR 路径，默认使用脚本所在仓库的
                jjx-server/target/jjx-server-1.0.0.jar。
路径含空格时请加引号；相对覆盖路径以当前工作目录为基准。
AI 环境变量：JJX_ARCHIVE_AI_URL、JJX_ARCHIVE_AI_API_KEY、
JJX_ARCHIVE_AI_MODEL、JJX_ARCHIVE_AI_PROXY；不使用 AI 时可留空。
EOF
}

if [[ $# -gt 0 ]]; then
  case "$1" in
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数：$1" >&2; usage >&2; exit 1 ;;
  esac
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${JJX_ENV_FILE-/etc/jjx-server/jjx.env}"

if [[ -n "${JJX_ENV_FILE+x}" || -e "$ENV_FILE" || -L "$ENV_FILE" ]]; then
  if [[ ! -f "$ENV_FILE" || ! -r "$ENV_FILE" ]]; then
    echo "配置文件不存在或不可读：$ENV_FILE" >&2
    exit 1
  fi
  set -a
  source "$ENV_FILE"
  set +a
else
  echo "未找到默认配置文件：$ENV_FILE；使用当前环境变量，AI 可暂不配置。" >&2
fi

JAR_FILE="${JJX_JAR_FILE:-$SCRIPT_DIR/jjx-server/target/jjx-server-1.0.0.jar}"
if [[ ! -f "$JAR_FILE" ]]; then
  echo "找不到后端 JAR：$JAR_FILE" >&2
  exit 1
fi

exec java -jar "$JAR_FILE"
