#!/usr/bin/env bash
# 印刷资源 v2 只读一致性检查（dev-20261125-008 P02）
# 用途：在迁移后的目标库上核对 5 张新表、字符集、唯一性与开放段排他；不改数据。
# 退出码：0=全部通过；1=有失败项或无法连接。
set -uo pipefail

DB_NAME="${DB_NAME:-jjx_erp_db}"
MYSQL=(mysql -h127.0.0.1 -uroot -p123456 --default-character-set=utf8mb4 -N -B "$DB_NAME")
fail=0

q() { "${MYSQL[@]}" -e "$1" 2>/dev/null; }
chk() {
  local name="$1" expect="$2" sql="$3" v
  v="$(q "$sql")"
  if [[ "${v:-}" == "$expect" ]]; then
    printf '✓ %s：%s\n' "$name" "${v:-0}"
  else
    printf '✘ %s：期望 %s，实际 %s\n' "$name" "$expect" "${v:-<空>}"
    fail=1
  fi
}

TABLES="('print_artwork','print_artwork_version','print_film_copy','print_frame_stretch','print_use_segment')"

chk "5 张新表存在" 5 "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME IN $TABLES"
chk "字符集不是 utf8mb4_unicode_ci 的表数" 0 "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_NAME IN $TABLES AND TABLE_COLLATION<>'utf8mb4_unicode_ci'"

chk "重复图案编号" 0 "SELECT COUNT(*) FROM (SELECT artwork_no FROM print_artwork GROUP BY artwork_no HAVING COUNT(*)>1) t"
chk "同图案重复版本号" 0 "SELECT COUNT(*) FROM (SELECT artwork_id,version_no FROM print_artwork_version GROUP BY artwork_id,version_no HAVING COUNT(*)>1) t"
chk "重复底片编号" 0 "SELECT COUNT(*) FROM (SELECT film_no FROM print_film_copy GROUP BY film_no HAVING COUNT(*)>1) t"
chk "重复用版请求号" 0 "SELECT COUNT(*) FROM (SELECT request_id FROM print_use_segment GROUP BY request_id HAVING COUNT(*)>1) t"

chk "同框多个开放绷网段" 0 "SELECT COUNT(*) FROM (SELECT frame_id FROM print_frame_stretch WHERE end_time IS NULL GROUP BY frame_id HAVING COUNT(*)>1) t"
chk "同执行多个开放用版段" 0 "SELECT COUNT(*) FROM (SELECT execution_id FROM print_use_segment WHERE end_time IS NULL GROUP BY execution_id HAVING COUNT(*)>1) t"
chk "同网版多个开放用版段" 0 "SELECT COUNT(*) FROM (SELECT plate_id FROM print_use_segment WHERE end_time IS NULL GROUP BY plate_id HAVING COUNT(*)>1) t"

chk "版本孤儿（图案）" 0 "SELECT COUNT(*) FROM print_artwork_version v LEFT JOIN print_artwork a ON a.artwork_id=v.artwork_id WHERE a.artwork_id IS NULL"
chk "底片孤儿（版本）" 0 "SELECT COUNT(*) FROM print_film_copy f LEFT JOIN print_artwork_version v ON v.version_id=f.version_id WHERE v.version_id IS NULL"
chk "绷网孤儿（网框）" 0 "SELECT COUNT(*) FROM print_frame_stretch s LEFT JOIN engineering_screen_frame f ON f.frame_id=s.frame_id WHERE f.frame_id IS NULL"
chk "用版孤儿（版本）" 0 "SELECT COUNT(*) FROM print_use_segment s LEFT JOIN print_artwork_version v ON v.version_id=s.version_id WHERE v.version_id IS NULL"
chk "用版孤儿（网版）" 0 "SELECT COUNT(*) FROM print_use_segment s LEFT JOIN engineering_screen_plate p ON p.plate_id=s.plate_id WHERE p.plate_id IS NULL"

chk "默认版本跨图案" 0 "SELECT COUNT(*) FROM print_artwork a JOIN print_artwork_version v ON v.version_id=a.default_version_id WHERE a.default_version_id IS NOT NULL AND v.artwork_id<>a.artwork_id"

if [[ "$fail" -eq 0 ]]; then
  echo "✓ 印刷资源 v2 一致性检查通过（库：$DB_NAME）"
else
  echo "✘ 印刷资源 v2 一致性检查失败（库：$DB_NAME）"
fi
exit "$fail"
