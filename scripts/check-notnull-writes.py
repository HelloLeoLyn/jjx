#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
JJX 巡检：NOT NULL 无默认值列 vs 后端写入路径（「Field 'xxx' doesn't have a default value」防复发）

用法：
    python3 scripts/check-notnull-writes.py            # 咨询模式（只报告，退出码 0）
    python3 scripts/check-notnull-writes.py --strict   # 门禁模式（① 命中且不在基线 → 退出码 1）
    python3 scripts/check-notnull-writes.py --write-baseline   # 把当前 ① 命中写进基线（收窄用）

背景（2026-09-24，dev-20260924-022 / -023）：
    MyBatis-Plus 默认 insertStrategy=NOT_NULL → 实体属性为 null 时该列不进 INSERT 列清单；
    若该列在库里是 NOT NULL 且无默认值（且 sql_mode 含 STRICT_TRANS_TABLES），插入必报
    "Field 'xxx' doesn't have a default value"。
    真实事故：IQC 归一（dev-20260918-026/-027）把检验事实切到 quality_lot、旧表归档删除，
    但 5 张 IQC 单的 inspection_id 仍为 NOT NULL，代码却写恒为 NULL 的 quarantine.getInspectionId()
    → 采购入库审核、四个处置动作（放行/退货/返工/报废）先后全部报错（已修：迁移 220/221）。

判据（按精确度）：
    ① MISSING   实体没有该属性（或被 @TableField(exist=false) 排除）→ 该表任何 insert 必漏列【门禁】
    ② NEVER_SET 属性存在但全仓从未出现赋值写法 → 恒 null【咨询；DTO/JSON/BeanUtils 注入会产生误报】
    ③ NULL_SRC  出现 setX(A.getX()) 且 A 的该列可空（接收者与来源类型都已解析）→ 空值沿对象链传染【咨询；
                运行期 BusinessException 守卫无法静态识别，需人工确认】

环境变量（与 db-migrate.sh / check-stock-summary.sh 同口径）：DB_HOST DB_PORT DB_USER DB_PASS DB_NAME
连不上库时 fail-open（只提醒，不阻塞）。
"""

import os
import re
import json
import subprocess
import sys
from collections import defaultdict

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
SRC = os.path.join(REPO, "jjx-server/src/main/java")
BASELINE = os.path.join(REPO, "scripts/notnull-baseline.json")

DB_HOST = os.environ.get("DB_HOST", "127.0.0.1")
DB_PORT = os.environ.get("DB_PORT", "3306")
DB_USER = os.environ.get("DB_USER", "root")
DB_PASS = os.environ.get("DB_PASS", "123456")
DB_NAME = os.environ.get("DB_NAME", "jjx_erp_db")

STRICT = "--strict" in sys.argv
WRITE_BASELINE = "--write-baseline" in sys.argv


def q(sql):
    env = dict(os.environ, MYSQL_PWD=DB_PASS)
    p = subprocess.run(
        ["mysql", f"-h{DB_HOST}", f"-P{DB_PORT}", f"-u{DB_USER}", "-N", DB_NAME, "-e", sql],
        capture_output=True, text=True, env=env)
    if p.returncode != 0:
        raise RuntimeError(p.stderr.strip())
    return [l for l in p.stdout.splitlines() if l.strip()]


def camel2snake(s):
    return re.sub(r"(?<!^)(?=[A-Z])", "_", s).lower()


def main():
    try:
        q("SELECT 1")
    except Exception as e:  # noqa: BLE001 —— 与其它巡检一致：连不上库不阻塞
        print(f"⚠ 连不上数据库 {DB_NAME}@{DB_HOST}:{DB_PORT}（{e}）——巡检跳过（fail-open，退出码 0）")
        return 0

    # 候选列：NOT NULL、无默认值、非自增/非生成列、非主键
    cands = defaultdict(list)
    for line in q("""
        SELECT t.table_name, c.column_name FROM information_schema.columns c
        JOIN information_schema.tables t ON t.table_schema=c.table_schema AND t.table_name=c.table_name
          AND t.table_type='BASE TABLE'
        WHERE c.table_schema='%s' AND c.is_nullable='NO' AND c.column_default IS NULL
          AND c.extra NOT LIKE '%%auto_increment%%' AND c.extra NOT LIKE '%%GENERATED%%'
          AND NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage k
            WHERE k.table_schema=c.table_schema AND k.table_name=c.table_name
              AND k.column_name=c.column_name AND k.constraint_name='PRIMARY')
        ORDER BY t.table_name, c.ordinal_position""" % DB_NAME):
        t, c = line.split("\t")[:2]
        cands[t].append(c)

    nullable = set()
    for line in q("SELECT table_name, column_name FROM information_schema.columns "
                  f"WHERE table_schema='{DB_NAME}' AND is_nullable='YES'"):
        t, c = line.split("\t")[:2]
        nullable.add((t, c))

    # 源码单次遍历
    entity_of, fields_of, excluded_of = {}, {}, {}
    mentions = defaultdict(list)
    files = [os.path.join(r, f) for r, _d, fs in os.walk(SRC) for f in fs if f.endswith(".java")]
    decl_re = re.compile(r"(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?[\w<>\[\], .]+\s+(\w+)\s*[;=]")
    for p in files:
        txt = open(p, encoding="utf-8", errors="replace").read()
        m = re.search(r'@TableName\(\s*"([^"]+)"', txt)
        if m:
            entity_of.setdefault(m.group(1), p)
            fields, excluded = {}, set()
            pend_exist_false, pend_col = False, None
            for ln in txt.splitlines():
                s = ln.strip()
                if s.startswith("@TableField"):
                    pend_exist_false = re.search(r"exist\s*=\s*false", s) is not None
                    mc = re.search(r'"([^"]+)"', s)
                    pend_col = mc.group(1) if mc and not pend_exist_false else None
                    continue
                dm = decl_re.match(s)
                if dm:
                    fields[dm.group(1)] = pend_col
                    if pend_exist_false:
                        excluded.add(dm.group(1))
                    pend_exist_false, pend_col = False, None
                elif s and not s.startswith("@"):
                    pend_exist_false, pend_col = False, None
            fields_of[p], excluded_of[p] = fields, excluded
        for i, ln in enumerate(txt.splitlines(), 1):
            for mm in re.finditer(r"\.(set[A-Z]\w*)\s*\(", ln):
                key = mm.group(1)[3:][:1].lower() + mm.group(1)[4:]
                mentions[key].append((p, i, ln.strip()))
            for mm in re.finditer(r"\.([a-z]\w*)\s*\(", ln):
                mentions[mm.group(1)].append((p, i, ln.strip()))

    getter_src = defaultdict(list)
    for table, ep in entity_of.items():
        for f, col in fields_of.get(ep, {}).items():
            getter_src["get" + f[0].upper() + f[1:]].append((table, col or camel2snake(f)))

    missing, never_set, null_src = [], [], []
    for table, cols in sorted(cands.items()):
        if re.search(r"(_bak_|_backup_|^product_backup_)", table):
            continue
        ep = entity_of.get(table)
        if not ep:
            continue
        fields, excluded = fields_of.get(ep, {}), excluded_of.get(ep, set())
        by_col = {(c if c else camel2snake(f)): f for f, c in fields.items()}
        for col in cols:
            f = by_col.get(col)
            if f is None or f in excluded:
                missing.append((table, col, ep, f))
                continue
            if not mentions.get(f):
                never_set.append((table, col, ep, f))
                continue
            # ③ 链式传染（接收者/来源类型都解析，排除同名方法碰撞）
            chain, cache = [], {}
            setter = "set" + f[0].upper() + f[1:]
            ent_cls = os.path.basename(ep)[:-5]
            call_re = re.compile(r"(\w+)\.(set[A-Z]\w*|[a-z]\w*)\s*\(([^()]*(?:\([^()]*\)[^()]*)*)\)")
            getter_re = re.compile(r"(\w+)\.(get\w+)\(\)")

            def vtype(fp, var):
                if fp not in cache:
                    cache[fp] = open(fp, encoding="utf-8", errors="replace").read()
                body = cache[fp]
                m2 = re.search(r"\bvar\s+" + re.escape(var) + r"\s*=\s*new\s+(?:com\.jjx\.[\w.]+\.)?(\w+)\s*\(", body)
                if m2:
                    return m2.group(1)
                m1 = re.search(r"([\w.]+)\s+" + re.escape(var) + r"\s*[=;,)]", body)
                return m1.group(1).split(".")[-1] if m1 else None

            for (fp, lineno, text) in mentions.get(f, []):
                for mc in call_re.finditer(text):
                    recv, method, args = mc.group(1), mc.group(2), mc.group(3)
                    if method not in (f, setter) or vtype(fp, recv) != ent_cls:
                        continue
                    for mg in getter_re.finditer(args):
                        srcvar, getter = mg.group(1), mg.group(2)
                        stype = vtype(fp, srcvar)
                        if not stype:
                            continue
                        for (st, sc) in getter_src.get(getter, []):
                            if os.path.basename(entity_of.get(st, ""))[:-5] == stype and (st, sc) in nullable:
                                chain.append((fp.replace(REPO + "/", ""), lineno, text, st, sc))
            if chain:
                null_src.append((table, col, ep, f, chain))

    base = {"version": 1, "note": "", "missing": []}
    if os.path.exists(BASELINE):
        base.update(json.load(open(BASELINE, encoding="utf-8")))
    allowed = set(base.get("missing", []))

    print(f"== 巡检 NOT NULL 写入路径：{DB_NAME}@{DB_HOST}  候选列 {sum(len(v) for v in cands.values())}"
          f" / {len(cands)} 表；有 @TableName 实体的表 {len([t for t in cands if t in entity_of])} ==")

    print(f"\n-- ① 实体无该列（insert 必漏列）：{len(missing)}（期望 0；基线内 {len(allowed)}）")
    for table, col, ep, f in missing:
        mark = "（基线内）" if f"{table}.{col}" in allowed else "❌"
        print(f"  {mark} {table}.{col}  实体 {ep.replace(REPO + '/', '')}")
        print("      修法：实体补属性，或（若该列确为历史/废弃）迁移里放开为可空")

    print(f"\n-- ② 属性全仓未被赋值（恒 null；DTO/JSON 注入会误报）：{len(never_set)}")
    for table, col, ep, f in never_set:
        print(f"  ? {table}.{col}  属性 {f}  实体 {ep.replace(REPO + '/', '')}")

    print(f"\n-- ③ 从可空列链式取值（本次事故同因；需人工确认是否有守卫）：{len(null_src)}")
    for table, col, ep, f, chain in null_src:
        print(f"  ? {table}.{col}  实体 {ep.replace(REPO + '/', '')}")
        for (fp, lineno, text, st, sc) in chain[:3]:
            print(f"      ← {fp}:{lineno}  {text[:100]}   来源 {st}.{sc}（可空）")

    if WRITE_BASELINE:
        base["missing"] = sorted({f"{t}.{c}" for t, c, _e, _f in missing})
        json.dump(base, open(BASELINE, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
        print(f"\n已写入基线 {BASELINE.replace(REPO + '/', '')}（{len(base['missing'])} 条；只许缩小）")
        return 0

    outside = [f"{t}.{c}" for t, c, _e, _f in missing if f"{t}.{c}" not in allowed]
    if STRICT and outside:
        print(f"\n✘ --strict：① 有 {len(outside)} 处不在基线内：{'、'.join(outside)}")
        print("   口径：CONVENTIONS §13（写入路径不得漏必填列）；基线只许缩小（--write-baseline 收窄）")
        return 1
    if STRICT:
        print("\n✅ --strict：① 无新增（②③ 仅提示，不作门禁）")
        return 0
    print("\n== 完成：咨询模式，退出码 0（加 --strict 可当门禁）==")
    return 0


if __name__ == "__main__":
    sys.exit(main())
