#!/usr/bin/env bash
# dev-20261112-007：从已存只读快照重建评审样本；不连接数据库、不启动服务。
set -euo pipefail
review_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
review_output=${1:-"$review_root/jjx-web/public/demos/bom-entry-review/review-data.json"}
review_temp=$(mktemp -d /tmp/jjx-bom-007-XXXXXX)
trap 'rm -rf -- "$review_temp"' EXIT
review_cp="$review_root/jjx-server/target/classes"
for review_library in jackson-databind jackson-core jackson-annotations; do
  review_jar="$HOME/.m2/repository/com/fasterxml/jackson/core/$review_library/2.18.5/$review_library-2.18.5.jar"
  if [[ ! -f "$review_jar" ]]; then
    echo "缺少已缓存依赖：$review_jar" >&2
    exit 1
  fi
  review_cp="$review_cp:$review_jar"
done
javac -cp "$review_cp" -d "$review_temp" "$review_root/scripts/bom-entry-review/GenerateReviewData.java"
java -Xmx256m -cp "$review_cp:$review_temp" GenerateReviewData \
  "$review_root/jjx-web/public/demos/bom-entry-review/review-data.json" "$review_output"
