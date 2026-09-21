#!/usr/bin/env bash
set -euo pipefail

# 历史档案 OCR 测试数据清理工具（dev-20260912-013）。
# 默认只预览；只有显式传入 --yes 才备份并删除。

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"
DB_NAME="${DB_NAME:-jjx_erp_db}"
BACKUP_DIR="${JJX_BACKUP_DIR:-$REPO_ROOT/jjx-docs/sql/backups}"
TASK_CODE="dev-20260912-013"
CONFIRM=false

usage() {
  echo "用法：bash scripts/clean-archive-ocr-data.sh [--yes] [--task dev-YYYYMMDD-NNN]"
  echo "不带 --yes 时只显示待清理范围。"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --yes) CONFIRM=true; shift ;;
    --task) TASK_CODE="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数：$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "$TASK_CODE" =~ ^dev-[0-9]{8}-[0-9]{3}$ ]] || { echo "任务码格式错误：$TASK_CODE" >&2; exit 2; }
# 2026-09-21 用户改口径：备份统一落仓库内 jjx-docs/sql/backups/，原「必须在仓库外」守卫已移除

export MYSQL_PWD="$DB_PASS"
MYSQL=(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 "$DB_NAME")
DUMP=(mysqldump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 --single-transaction --skip-lock-tables --no-tablespaces)
q() { "${MYSQL[@]}" -N -B -e "$1"; }

q "SELECT 1" >/dev/null
archive_count="$(q "SELECT COUNT(*) FROM engineering_archive_import")"
icon_count="$(q "SELECT COUNT(*) FROM engineering_process_icon_sample")"
attachment_count="$(q "SELECT COUNT(*) FROM sys_attachment WHERE biz_type='engineering_archive'")"

echo "════ 历史档案 OCR 清理预览 ════"
echo "数据库：$DB_NAME@$DB_HOST:$DB_PORT"
echo "档案记录：$archive_count"
echo "图标样本：$icon_count"
echo "档案附件：$attachment_count"
q "SELECT archive_id,file_name,recognize_status,COALESCE(product_id,'-'),COALESCE(bom_id,'-'),COALESCE(routing_id,'-') FROM engineering_archive_import ORDER BY archive_id" \
  | awk -F '\t' 'BEGIN{print "archive_id\t文件\t状态\t产品\tBOM\t路线"}{print}'

if [[ "$archive_count" == "0" && "$icon_count" == "0" && "$attachment_count" == "0" ]]; then
  echo "没有需要清理的数据。"
  exit 0
fi

# 只允许删除历史档案生成的开发中产品、草稿 BOM 和草稿路线。
unsafe="$(q "
SELECT COUNT(*) FROM engineering_archive_import a
LEFT JOIN product p ON p.product_id=a.product_id
LEFT JOIN engineering_bom b ON b.bom_id=a.bom_id
LEFT JOIN engineering_routing r ON r.routing_id=a.routing_id
WHERE (a.product_id IS NOT NULL AND (p.product_id IS NULL OR p.from_source<>'history_archive' OR p.product_status<>1))
   OR (a.bom_id IS NOT NULL AND (b.bom_id IS NULL OR b.approve_status<>1))
   OR (a.routing_id IS NOT NULL AND (r.routing_id IS NULL OR r.approve_status<>1));")"
[[ "$unsafe" == "0" ]] || { echo "发现非历史档案草稿或非草稿状态的数据，拒绝清理。" >&2; exit 3; }

# 核对历史档案产品/BOM/路线没有被下游业务引用。
external_refs="$(q "
SELECT
 (SELECT COUNT(*) FROM production_order o JOIN engineering_archive_import a ON o.product_id=a.product_id OR o.bom_id=a.bom_id OR o.routing_id=a.routing_id) +
 (SELECT COUNT(*) FROM product_instance i JOIN engineering_archive_import a ON i.product_id=a.product_id OR i.bom_id=a.bom_id) +
 (SELECT COUNT(*) FROM sales_order_product s JOIN engineering_archive_import a ON s.product_id=a.product_id) +
 (SELECT COUNT(*) FROM sales_sample_order s JOIN engineering_archive_import a ON s.product_id=a.product_id) +
 (SELECT COUNT(*) FROM sales_quotation_item s JOIN engineering_archive_import a ON s.product_id=a.product_id) +
 (SELECT COUNT(*) FROM inventory_material m JOIN engineering_archive_import a ON m.product_id=a.product_id) +
 (SELECT COUNT(*) FROM engineering_film f JOIN engineering_archive_import a ON f.product_id=a.product_id) +
 (SELECT COUNT(*) FROM engineering_resource_product_rel r JOIN engineering_archive_import a ON r.product_id=a.product_id);")"
[[ "$external_refs" == "0" ]] || { echo "发现 $external_refs 条下游业务引用，拒绝清理。" >&2; exit 3; }

if [[ "$CONFIRM" != true ]]; then
  echo "当前为预览模式；确认后执行：bash scripts/clean-archive-ocr-data.sh --yes --task $TASK_CODE"
  exit 0
fi

mkdir -p "$BACKUP_DIR"
stamp="$(date +%Y%m%d-%H%M%S)"
full_backup="$BACKUP_DIR/jjx_erp_db_backup_${stamp}_before-archive-ocr-cleanup.sql"
guard_backup="$BACKUP_DIR/archive_ocr_cleanup_${stamp}.sql"
file_backup="$BACKUP_DIR/archive_ocr_files_cleanup_${stamp}.tar.gz"

echo "1/4 全库备份：$full_backup"
"${DUMP[@]}" "$DB_NAME" --result-file="$full_backup"
echo "2/4 guard 备份：$guard_backup"
"${DUMP[@]}" "$DB_NAME" engineering_archive_import engineering_process_icon_sample sys_attachment product engineering_bom engineering_bom_item engineering_routing engineering_routing_item --result-file="$guard_backup"
md5sum "$full_backup" "$guard_backup"
echo "全库表数：$(grep -c '^CREATE TABLE' "$full_backup")"

mapfile -t archive_ids < <(q "SELECT archive_id FROM engineering_archive_import ORDER BY archive_id")
mapfile -t stored_files < <(q "SELECT file_path FROM engineering_archive_import UNION SELECT file_path FROM sys_attachment WHERE biz_type='engineering_archive'")
backup_paths=()
for root in "$REPO_ROOT/upload" "$REPO_ROOT/jjx-server/upload"; do
  for relative in "${stored_files[@]}"; do
    [[ "$relative" == engineering-archive/* && "$relative" != *..* ]] || continue
    [[ -f "$root/$relative" ]] && backup_paths+=("${root#"$REPO_ROOT/"}/$relative")
  done
  for id in "${archive_ids[@]}"; do
    [[ "$id" =~ ^[0-9]+$ ]] || continue
    [[ -d "$root/engineering-archive/crops/$id" ]] && backup_paths+=("${root#"$REPO_ROOT/"}/engineering-archive/crops/$id")
    [[ -d "$root/engineering-archive/icons/$id" ]] && backup_paths+=("${root#"$REPO_ROOT/"}/engineering-archive/icons/$id")
  done
done
if [[ ${#backup_paths[@]} -gt 0 ]]; then
  echo "3/4 文件备份：$file_backup"
  tar -czf "$file_backup" -C "$REPO_ROOT" -- "${backup_paths[@]}"
  md5sum "$file_backup"
else
  echo "3/4 没有本地文件需要备份"
fi

echo "4/4 事务清理"
"${MYSQL[@]}" <<'SQL'
START TRANSACTION;
CREATE TEMPORARY TABLE tmp_archive_cleanup AS
SELECT archive_id,product_id,bom_id,routing_id FROM engineering_archive_import;
DELETE FROM engineering_process_icon_sample;
DELETE FROM sys_attachment WHERE biz_type='engineering_archive';
DELETE i FROM engineering_routing_item i JOIN tmp_archive_cleanup t ON i.routing_id=t.routing_id;
DELETE i FROM engineering_bom_item i JOIN tmp_archive_cleanup t ON i.bom_id=t.bom_id;
DELETE FROM engineering_archive_import;
DELETE r FROM engineering_routing r JOIN tmp_archive_cleanup t ON r.routing_id=t.routing_id WHERE r.approve_status=1;
DELETE b FROM engineering_bom b JOIN tmp_archive_cleanup t ON b.bom_id=t.bom_id WHERE b.approve_status=1;
DELETE p FROM product p JOIN tmp_archive_cleanup t ON p.product_id=t.product_id WHERE p.product_status=1 AND p.from_source='history_archive';
COMMIT;
SQL

for root in "$REPO_ROOT/upload" "$REPO_ROOT/jjx-server/upload"; do
  for relative in "${stored_files[@]}"; do
    [[ "$relative" == engineering-archive/* && "$relative" != *..* ]] || continue
    rm -f -- "$root/$relative"
  done
  for id in "${archive_ids[@]}"; do
    [[ "$id" =~ ^[0-9]+$ ]] || continue
    rm -rf -- "$root/engineering-archive/crops/$id" "$root/engineering-archive/icons/$id"
  done
done

remaining="$(q "SELECT (SELECT COUNT(*) FROM engineering_archive_import)+(SELECT COUNT(*) FROM engineering_process_icon_sample)+(SELECT COUNT(*) FROM sys_attachment WHERE biz_type='engineering_archive')")"
[[ "$remaining" == "0" ]] || { echo "清理后仍有 $remaining 条记录，请从备份恢复并排查。" >&2; exit 4; }
echo "清理完成；任务：$TASK_CODE；数据库残留：0"
