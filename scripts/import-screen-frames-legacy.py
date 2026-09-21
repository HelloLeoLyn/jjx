#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
网版老台账导入脚本（一次性）—— dev-20260921-050

用途：把《2026_所有网版内容登记表.xlsx》（6 个 sheet：G框/H框/B框/C框/A框/F框）导入
      `engineering_screen_frame`（网框）+ `engineering_screen_plate`（当前版面）。

规则（与后端 EngineeringResourceService.importScreenFrames 一致）：
  · 一行 = 一个网框：编号（G0001）→ frame_no；sheet 名首字母 → frame_type（G/H/B/C/A/F）
  · 有「网版内容记录」→ 网框 status=PLATED（已制版）+ 生成/更新该网框的**当前版面**（status=ACTIVE，content=原文）
  · 内容为空 → 网框 status=EMPTY（空框，不建版面）
  · 内容摘要（前 200 字）写入 frame.remark 便于搜索
  · 幂等：网框按唯一索引 uk_screen_frame_no upsert；版面按 frame_id + status=ACTIVE 判存在

用法：
  python3 scripts/import-screen-frames-legacy.py <xlsx>              # 试运行
  python3 scripts/import-screen-frames-legacy.py <xlsx> --apply      # 落库
"""
import argparse
import os
import re
import subprocess
import sys

try:
    import openpyxl
except ImportError:
    sys.exit("需要 openpyxl：pip install openpyxl")

SHEETS = {"G框": "G", "H框": "H", "B框": "B", "C框": "C", "A框": "A", "F框": "F"}
NO = re.compile(r"^([A-Za-z])(\d{3,4})$")
REMARK_ABSTRACT = 200
TMP_TABLE = "tmp_imp_screen_frames"


def parse_sheet(ws, frame_type):
    """返回 [{frame_no, content}]：编号 = 匹配 G0001 的单元格；内容 = 该行最长的其他文本"""
    out = []
    for row in ws.iter_rows(values_only=True):
        if not row:
            continue
        no, content = None, ""
        for c in row:
            if c is None:
                continue
            s = " ".join(str(c).split())
            if not s:
                continue
            if no is None and NO.match(s):
                no = s.upper()
                continue
            if len(s) > len(content) and not NO.match(s):
                content = s
        if no:
            out.append({"frame_no": no, "frame_type": frame_type, "content": content})
    # 同表内按编号去重（保留首个非空内容）
    dedup = {}
    for r in out:
        if r["frame_no"] not in dedup:
            dedup[r["frame_no"]] = r
        elif not dedup[r["frame_no"]]["content"] and r["content"]:
            dedup[r["frame_no"]]["content"] = r["content"]
    return list(dedup.values())


def sql_escape(v):
    if v is None:
        return "NULL"
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def run_sql(sql, db, user, password, host="127.0.0.1", port="3306"):
    env = dict(os.environ, MYSQL_PWD=password)
    res = subprocess.run(["mysql", "-h", host, "-P", port, "-u", user, db],
                         input=sql.encode("utf-8"), capture_output=True, env=env)
    if res.returncode != 0:
        sys.exit("SQL 失败：" + res.stderr.decode("utf-8", "ignore")[:600])
    return res.stdout.decode("utf-8", "ignore")


def apply_rows(rows, db, user, password, chunk=500):
    run_sql(f"DROP TABLE IF EXISTS {TMP_TABLE};", db, user, password)
    run_sql(f"""CREATE TABLE {TMP_TABLE}(
        frame_no varchar(50) NOT NULL PRIMARY KEY,
        frame_type varchar(30) NULL,
        status varchar(20) NOT NULL,
        remark_txt varchar(500) NULL,
        content varchar(1000) NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;""", db, user, password)

    for i in range(0, len(rows), chunk):
        part = rows[i:i + chunk]
        values = ",\n".join("(" + ",".join(sql_escape(r[c]) for c in
                                           ("frame_no", "frame_type", "status", "remark", "content")) + ")" for r in part)
        run_sql(f"INSERT INTO {TMP_TABLE}(frame_no,frame_type,status,remark_txt,content) VALUES\n{values}\n"
                "ON DUPLICATE KEY UPDATE frame_type=VALUES(frame_type),status=VALUES(status),"
                "remark_txt=VALUES(remark_txt),content=VALUES(content);", db, user, password)
        print(f"  staging {min(i + chunk, len(rows))}/{len(rows)}")

    # 1) 网框 upsert（唯一索引 uk_screen_frame_no）
    run_sql(f"""INSERT INTO engineering_screen_frame(frame_no,frame_type,status,remark,create_by)
                SELECT t.frame_no,t.frame_type,t.status,t.remark_txt,'import-legacy'
                  FROM {TMP_TABLE} t
                ON DUPLICATE KEY UPDATE frame_type=VALUES(frame_type),status=VALUES(status),
                    remark=COALESCE(VALUES(remark),remark),update_by='import-legacy',update_time=NOW();""",
            db, user, password)
    # 2) 当前版面：无 ACTIVE 版面则新建
    out = run_sql(f"""INSERT INTO engineering_screen_plate(frame_id,plate_no,content,status,create_by)
                      SELECT f.frame_id,f.frame_no,t.content,'ACTIVE','import-legacy'
                        FROM {TMP_TABLE} t
                        JOIN engineering_screen_frame f ON f.frame_no=t.frame_no AND f.del_flag='0'
                       WHERE t.content IS NOT NULL AND t.content<>''
                         AND NOT EXISTS (SELECT 1 FROM engineering_screen_plate p
                                          WHERE p.frame_id=f.frame_id AND p.status='ACTIVE');""",
                  db, user, password)
    # 3) 已有 ACTIVE 版面 → 更新内容
    run_sql(f"""UPDATE engineering_screen_plate p
                  JOIN engineering_screen_frame f ON p.frame_id=f.frame_id
                  JOIN {TMP_TABLE} t ON t.frame_no=f.frame_no
                   SET p.content=t.content,p.update_by='import-legacy'
                 WHERE p.status='ACTIVE' AND t.content IS NOT NULL AND t.content<>'';""",
            db, user, password)
    run_sql(f"DROP TABLE IF EXISTS {TMP_TABLE};", db, user, password)
    print("  落库完成")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xlsx")
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--report", default="/tmp/frame-legacy-import-report.md")
    ap.add_argument("--db", default="jjx_erp_db")
    ap.add_argument("--user", default="root")
    ap.add_argument("--password", default="123456")
    args = ap.parse_args()

    wb = openpyxl.load_workbook(args.xlsx, read_only=True, data_only=True)
    rows, per_sheet = [], {}
    for ws in wb.worksheets:
        ft = SHEETS.get(ws.title)
        if not ft:
            continue
        rs = parse_sheet(ws, ft)
        per_sheet[ws.title] = (len(rs), sum(1 for r in rs if not r["content"]))
        rows.extend(rs)
    wb.close()

    # 全局去重
    dedup = {}
    for r in rows:
        k = r["frame_no"]
        if k not in dedup:
            dedup[k] = r
        elif not dedup[k]["content"] and r["content"]:
            dedup[k]["content"] = r["content"]
    rows = list(dedup.values())
    for r in rows:
        c = r["content"]
        r["status"] = "PLATED" if c else "EMPTY"
        r["remark"] = (c[:REMARK_ABSTRACT] if c else None)
        r["content"] = (c[:1000] if c else None)

    plated = [r for r in rows if r["status"] == "PLATED"]
    empty = [r for r in rows if r["status"] == "EMPTY"]
    lines = ["# 网版老台账导入 · 试运行报告\n",
             f"- 源文件：`{os.path.basename(args.xlsx)}`",
             f"- 读取网框行：**{len(rows)}**（去重后；跨表重复 {len(rows) - len(dedup) if False else 0}）",
             f"- 已制版（有内容）：**{len(plated)}** → 生成/更新当前版面",
             f"- 空框（无内容）：**{len(empty)}** → 建空框，不建版面",
             "\n## 各表\n"]
    for k, v in per_sheet.items():
        lines.append(f"- {k}: 读取 {v[0]}（其中空内容 {v[1]}）")
    lines.append("\n## 抽样（前 8 条）\n")
    lines.append("| 网框编号 | 框型 | 状态 | 版面内容 |")
    lines.append("|---|---|---|---|")
    for r in rows[:8]:
        lines.append(f"| {r['frame_no']} | {r['frame_type']} | {r['status']} | {(r['content'] or '')[:60]} |")
    report = "\n".join(lines) + "\n"
    with open(args.report, "w", encoding="utf-8") as f:
        f.write("\ufeff" + report)
    print(report)
    print(f"报告：{args.report}")

    if args.apply:
        print("开始落库…")
        apply_rows(rows, args.db, args.user, args.password)
    else:
        print("（试运行，未写库；加 --apply 才落库）")


if __name__ == "__main__":
    main()
