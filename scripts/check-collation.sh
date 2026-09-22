#!/usr/bin/env bash
# ============================================================================
# check-collation.sh —— 跨表 collation 体检（只读）
#   任务：dev-20260921-046（任务 2108）  目标口径：全库字符串列统一 utf8mb4_unicode_ci
#
# 背景：库内曾同时存在 utf8mb4_unicode_ci 与 utf8mb4_0900_ai_ci 两套字符串列，
#       跨表字符串 JOIN 会报 ERROR 1267 Illegal mix of collations（实例见迁移 193 文件头）。
#       修复迁移：jjx-docs/sql/migrations/193_collation_unify_utf8mb4_unicode_ci.sql
#
# 用法：
#   bash scripts/check-collation.sh              # 咨询模式：只报告，永远 exit 0
#   bash scripts/check-collation.sh --strict      # 有漂移则 exit 1（迁移 193 执行后改用这个）
#   bash scripts/check-collation.sh --preflight   # 追加执行前预检：串列外键 + 唯一索引撞键
# 环境变量（与 scripts/db-backup.sh / db-migrate.sh 同口径）：
#   DB_HOST DB_PORT DB_USER DB_PASS DB_NAME
# 只读：本脚本只跑 SELECT / information_schema 查询，不改任何库内数据。
# ============================================================================
set -uo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"
DB_NAME="${DB_NAME:-jjx_erp_db}"
TARGET_COLLATION="utf8mb4_unicode_ci"

STRICT=false
PREFLIGHT=false
for arg in "$@"; do
  case "$arg" in
    --strict) STRICT=true ;;
    --preflight) PREFLIGHT=true ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "未知参数：$arg（支持 --strict / --preflight）"; exit 2 ;;
  esac
done

command -v mysql >/dev/null 2>&1 || { echo "找不到 mysql 客户端（apt install mysql-client）"; exit 2; }
export MYSQL_PWD="$DB_PASS"
if ! mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --connect-timeout=5 -N -B "$DB_NAME" -e "SELECT 1" >/dev/null 2>&1; then
  echo "连不上库 ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME} —— 跳过体检（不阻塞）"
  exit 0
fi

M() { mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" --default-character-set=utf8mb4 -N -B "$DB_NAME" -e "$1"; }

echo "== collation 体检：$(date '+%F %T')  目标=$TARGET_COLLATION =="

db_default=$(M "SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$DB_NAME';")
echo
echo "-- 1) 库默认：$db_default $([ "$db_default" = "$TARGET_COLLATION" ] && echo '✅' || echo "❌ 期望 $TARGET_COLLATION")"

echo
echo "-- 2) 表级分布（BASE TABLE / TABLE_COLLATION）"
M "SELECT TABLE_COLLATION, COUNT(*) AS tbls FROM information_schema.TABLES
   WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_TYPE='BASE TABLE' GROUP BY TABLE_COLLATION ORDER BY tbls DESC;"

cols_drift=$(M "SELECT COUNT(*) FROM information_schema.COLUMNS c JOIN information_schema.TABLES t
   ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME
   WHERE c.TABLE_SCHEMA='$DB_NAME' AND t.TABLE_TYPE='BASE TABLE'
     AND c.COLLATION_NAME IS NOT NULL AND c.COLLATION_NAME<>'$TARGET_COLLATION';")
tbls_drift=$(M "SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA='$DB_NAME' AND TABLE_TYPE='BASE TABLE' AND TABLE_COLLATION<>'$TARGET_COLLATION';")
echo
echo "-- 3) 漂移汇总：基表列 $cols_drift 个 / 基表默认 $tbls_drift 张（期望都是 0）"

if [ "$cols_drift" -gt 0 ] || [ "$tbls_drift" -gt 0 ]; then
  echo
  echo "-- 4) 非目标 collation 的列分布"
  M "SELECT COLLATION_NAME, COUNT(*) AS cols, COUNT(DISTINCT TABLE_NAME) AS tbls
     FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME'
       AND COLLATION_NAME IS NOT NULL AND COLLATION_NAME<>'$TARGET_COLLATION'
     GROUP BY COLLATION_NAME ORDER BY cols DESC;"
  echo
  echo "-- 5) 危险面：同名列跨两种 collation（最易触发 1267 的 JOIN 键）"
  M "SELECT COLUMN_NAME,
        SUM(COLLATION_NAME='$TARGET_COLLATION') AS unicode_ci,
        SUM(COLLATION_NAME<>'$TARGET_COLLATION') AS other
     FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB_NAME'
       AND COLLATION_NAME IS NOT NULL
       AND COLUMN_NAME IN ('material_code','batch_no','unit','product_code','product_name','customer_name','trace_id','order_no')
     GROUP BY COLUMN_NAME HAVING unicode_ci>0 AND other>0 ORDER BY (unicode_ci+other) DESC;"
  echo
  echo "-- 修复：bash scripts/db-migrate.sh 193_collation_unify_utf8mb4_unicode_ci.sql --yes --task dev-20260921-046"
else
  echo "   ✅ 全库字符串列已统一到 $TARGET_COLLATION（1267 跨表字符串 JOIN 风险已消除）"
fi

if [ "$PREFLIGHT" = true ]; then
  echo
  echo "-- 6) 预检：字符串列上的外键（CONVERT TO 会被 FK 阻挡）"
  M "SELECT COUNT(*) AS str_fk FROM information_schema.KEY_COLUMN_USAGE k
     JOIN information_schema.COLUMNS c ON c.TABLE_SCHEMA=k.TABLE_SCHEMA AND c.TABLE_NAME=k.TABLE_NAME AND c.COLUMN_NAME=k.COLUMN_NAME
     WHERE k.TABLE_SCHEMA='$DB_NAME' AND k.REFERENCED_TABLE_NAME IS NOT NULL AND c.COLLATION_NAME IS NOT NULL;" \
     | sed 's/^/   串列外键数: /'
  echo "   期望 0；非 0 时须先删除/重建该外键再 CONVERT。"

  echo
  echo "-- 7) 预检：含串列的 UNIQUE 索引在 $TARGET_COLLATION 口径下是否会撞键"
  echo "   （方法：按索引全部列 GROUP BY + 对串列加 COLLATE；必须排除 NULL 与复合键漏列，否则假阳性）"
  M "SELECT CONCAT('SELECT ''', s.TABLE_NAME, '.', s.INDEX_NAME, ''' AS uk, ''',
        GROUP_CONCAT(s.COLUMN_NAME ORDER BY s.SEQ_IN_INDEX SEPARATOR '+'), ''' AS cols, COUNT(*) AS dup_groups FROM \`',
        s.TABLE_NAME, '\` WHERE ',
        GROUP_CONCAT(CONCAT('\`', s.COLUMN_NAME, '\` IS NOT NULL') ORDER BY s.SEQ_IN_INDEX SEPARATOR ' AND '),
        ' GROUP BY ',
        GROUP_CONCAT(CONCAT('\`', s.COLUMN_NAME, '\`',
            IF(c.COLLATION_NAME IS NOT NULL, ' COLLATE $TARGET_COLLATION', '')) ORDER BY s.SEQ_IN_INDEX SEPARATOR ', '),
        ' HAVING dup_groups > 1')
     FROM information_schema.STATISTICS s
     LEFT JOIN information_schema.COLUMNS c
       ON c.TABLE_SCHEMA=s.TABLE_SCHEMA AND c.TABLE_NAME=s.TABLE_NAME AND c.COLUMN_NAME=s.COLUMN_NAME
     WHERE s.TABLE_SCHEMA='$DB_NAME' AND s.NON_UNIQUE=0
       AND EXISTS (SELECT 1 FROM information_schema.COLUMNS c2
                   WHERE c2.TABLE_SCHEMA=s.TABLE_SCHEMA AND c2.TABLE_NAME=s.TABLE_NAME AND c2.COLLATION_NAME IS NOT NULL)
     GROUP BY s.TABLE_NAME, s.INDEX_NAME;" > /tmp/.jjx_uk_stmts.$$ 2>/dev/null
  hit=0
  while IFS= read -r stmt; do
    [ -z "$stmt" ] && continue
    res=$(M "$stmt;" 2>/dev/null | tr '\t' ' ' | tr '\n' ';')
    [ -n "$res" ] && { echo "   COLLISION: $res"; hit=$((hit+1)); }
  done < /tmp/.jjx_uk_stmts.$$
  rm -f /tmp/.jjx_uk_stmts.$$
  echo "   撞键索引数: $hit（期望 0）"
fi

if [ "$STRICT" = true ] && { [ "$cols_drift" -gt 0 ] || [ "$tbls_drift" -gt 0 ] || [ "$db_default" != "$TARGET_COLLATION" ]; }; then
  echo
  echo "✘ --strict：仍有 collation 漂移（列 $cols_drift / 表 $tbls_drift / 库默认 $db_default）"
  exit 1
fi
echo
echo "== 体检完成：咨询模式，退出码 0（加 --strict 可当门禁）=="
exit 0
