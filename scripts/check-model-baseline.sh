#!/usr/bin/env bash
set -uo pipefail

DB_NAME="jjx_erp_db"
MYSQL=(mysql -h127.0.0.1 -uroot -p123456 --default-character-set=utf8mb4 -N -B)
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BASELINE="$SCRIPT_DIR/model-baseline.json"
WRITE=0

usage() {
  cat <<'EOF'
用途：只读检查 jjx_erp_db 的表名集合、批准例外和类型后缀基线。
危险等级：🟢 只读；--write-baseline 会写入基线文件并备份原文件。
用法：check-model-baseline.sh [--write-baseline] [--baseline <path>] [--help]
退出码：0=通过；1=检查失败、参数错误或数据库/文件读取失败。
EOF
}

while (($#)); do
  case "$1" in
    --help) usage; exit 0 ;;
    --write-baseline) WRITE=1 ;;
    --baseline)
      shift
      if (($# == 0)); then echo "✘ --baseline 缺少路径" >&2; exit 1; fi
      BASELINE="$1"
      ;;
    *) echo "✘ 未知参数：$1" >&2; usage >&2; exit 1 ;;
  esac
  shift
done

if [[ ! -f "$BASELINE" ]]; then
  echo "✘ 基线文件不存在：$BASELINE"
  exit 1
fi

if ! current_tables="$(${MYSQL[@]} -e "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB_NAME}' ORDER BY TABLE_NAME" 2>/tmp/check-model-baseline.mysql.err)"; then
  echo "✘ 无法读取数据库 ${DB_NAME}：$(< /tmp/check-model-baseline.mysql.err)"
  exit 1
fi

export BASELINE CURRENT_TABLES="$current_tables" WRITE DB_NAME
python3 - <<'PY'
import datetime
import json
import os
import re
import shutil
import sys

baseline_path = os.environ["BASELINE"]
try:
    with open(baseline_path, encoding="utf-8") as fh:
        baseline = json.load(fh)
except Exception as exc:
    print(f"✘ 基线 JSON 无法读取：{exc}")
    sys.exit(1)

current = sorted({line for line in os.environ.get("CURRENT_TABLES", "").splitlines() if line})
baseline_tables = set(baseline.get("tables", []))
approved = baseline.get("approvedNewTables", [])
approved_names = {item.get("table") for item in approved if isinstance(item, dict)}
allowlist = baseline.get("typeSuffixAllowlist", [])
allow_names = {item.get("table") for item in allowlist if isinstance(item, dict)}
failed = False

new_tables = sorted(set(current) - baseline_tables - approved_names)
if new_tables:
    failed = True
    print("✘ 未批准新表：")
    for name in new_tables:
        print(f"  - {name}")
    print("  新增表须先提案拍板并写入 approvedNewTables")
else:
    print("✓ 未批准新表：0")

missing_approved = sorted(name for name in approved_names if name not in current)
for name in missing_approved:
    print(f"⚠ 已批准未迁移/已删除：{name}")
if not missing_approved:
    print("✓ 批准例外在库中：无缺失")

missing_baseline = sorted(baseline_tables - set(current))
for name in missing_baseline:
    print(f"⚠ 表已删除/改名，下次 --write-baseline 收缩基线：{name}")
if not missing_baseline:
    print("✓ 基线表均存在：无缺失")

# 类型后缀闸：匹配「类型词」结尾，允许再带一段（_order/_item 等）。
# 旧正则 `_(scrap|rework|return|release)$` 漏抓真凶——inventory_iqc_scrap_order / _rework_order /
# _return_order 结尾是 _order，一个都不命中（全库只 sales_return 命中），等于闸门空转。
suffix_re = re.compile(r"_(scrap|rework|return|release)(_order|_item)?$")
suffix_bad = sorted(name for name in current if suffix_re.search(name) and name not in allow_names)
if suffix_bad:
    failed = True
    print("✘ 未列入白名单的类型后缀表：")
    for name in suffix_bad:
        print(f"  - {name}")
else:
    print("✓ 类型后缀闸：通过")

quality_bad = []
for index, item in enumerate(allowlist, 1):
    if not isinstance(item, dict) or not item.get("kind"):
        quality_bad.append(f"第 {index} 条缺 kind")
        continue
    kind = item["kind"]
    if kind == "debt" and (not item.get("retireTask") or not item.get("target")):
        quality_bad.append(f"第 {index} 条 debt 缺 retireTask/target")
    if kind == "false-positive" and not item.get("reason"):
        quality_bad.append(f"第 {index} 条 false-positive 缺 reason")
if quality_bad:
    failed = True
    print("✘ 白名单质量不合格：")
    for message in quality_bad:
        print(f"  - {message}")
else:
    print("✓ 白名单质量：通过")

# 批准例外字段校验（CONVENTIONS §15.7）：每条必须带 任务码/日期/提案链接，否则例外机制形同虚设
approved_bad = []
for index, item in enumerate(approved, 1):
    if not isinstance(item, dict):
        approved_bad.append(f"第 {index} 条不是对象")
        continue
    for field in ("table", "taskCode", "date", "proposalLink"):
        if not item.get(field):
            approved_bad.append(f"第 {index} 条缺 {field}")
if approved_bad:
    failed = True
    print("✘ 批准例外不合格（§15.7 要求带 任务码/日期/提案链接）：")
    for message in approved_bad:
        print(f"  - {message}")
else:
    print("✓ 批准例外：通过")

groups = {}
for name in current:
    parts = name.split("_")
    prefix = "_".join(parts[:2]) if len(parts) >= 2 else name
    groups.setdefault(prefix, []).append(name)
homogeneous = {prefix: names for prefix, names in groups.items() if len(names) >= 3}
if homogeneous:
    print("⚠ 同构表告警（同一前缀至少 3 张；需人工评审）：")
    for prefix in sorted(homogeneous):
        print(f"  - {prefix}: {', '.join(homogeneous[prefix])}")
else:
    print("✓ 同构表告警：无")

if os.environ.get("WRITE") == "1":
    if new_tables:
        print("✘ 拒绝写入基线：请先补 approvedNewTables")
        sys.exit(1)
    backup_path = baseline_path + ".bak"
    shutil.copy2(baseline_path, backup_path)
    new_baseline = dict(baseline)
    new_baseline["generatedAt"] = datetime.date.today().isoformat()
    new_baseline["tableCountBaseline"] = len(current)
    new_baseline["tables"] = current
    # 例外台账长期保留（CONVENTIONS §15.7 留痕：任务码/日期/提案链接），不随入库而清除；
    # 已入库的表会同时出现在 tables 与 approvedNewTables，属预期（可追溯谁批的）。
    new_baseline["approvedNewTables"] = approved
    with open(baseline_path, "w", encoding="utf-8") as fh:
        json.dump(new_baseline, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print(f"✓ 已受控更新基线：{baseline_path}（备份：{backup_path}）")

if failed:
    print("✘ 表数基线门禁失败")
    sys.exit(1)
print(f"✓ 表数基线门禁通过（当前 {len(current)} 张表，基线 {len(baseline_tables)} 张表）")
PY
