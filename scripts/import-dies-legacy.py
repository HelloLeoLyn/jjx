#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
刀模老台账导入脚本（一次性）—— dev-20260921-047

用途：把《刀模存放登记表1.xlsx》（33 个 sheet：刀模总览 + 28 个客户分表 + 无关表）导入
      `engineering_die` 表（与后端「刀模导入」功能同一套字段口径）。

规则（与后端 EngineeringResourceService.importDies 一致）：
  · 刀模编号 = 客户列（如 JST-412）；XY 表用「客户 + 流水号」→ XY-001；编号统一大写、数字补足 3 位
  · 状态：报废→SCRAPPED；无刀/空档/只印刷/打样/暂不用→STOPPED；委外/送修→MAINTENANCE；
          刀模共用 X→AVAILABLE；其余→AVAILABLE
  · 数量→quantity；入库日期→stock_in_date；存放位置→location（多库位「；」分隔）；备注→remark
  · 刀模祥情/详情：非纯状态词的描述文字进 remark（保留原文）；「共用 X」进 remark 前缀
  · 同一刀模号出现在多张表 → 按字段合并（谁有值用谁的），冲突值记录进报告

用法：
  python3 scripts/import-dies-legacy.py <xlsx>                 # 试运行，只出报告
  python3 scripts/import-dies-legacy.py <xlsx> --apply         # 试运行后写入数据库
  python3 scripts/import-dies-legacy.py <xlsx> --report out.md # 报告输出路径
"""
import argparse
import os
import re
import subprocess
import sys
import datetime

try:
    import openpyxl
except ImportError:
    sys.exit("需要 openpyxl：pip install openpyxl")

SKIP_SHEETS = {"刀模使用记录表", "垫片及加强片", "钢孔库存", "测试治具", "Sheet1", "Sheet2"}
NO = re.compile(r"^([A-Z]{2,4})-(\d{1,5})$")
DIE_NO_HEADERS = ("客户", "刀模号", "编号")
DETAIL_HEADERS = ("刀模祥情", "刀模详情", "祥情", "详情")
QTY_HEADERS = ("数量",)
DATE_HEADERS = ("入库日期", "日期")
LOC_HEADERS = ("存放位置", "位置", "库位")
REMARK_HEADERS = ("备注",)
PURE_STATUS_WORDS = ("报废", "无刀", "空档", "只印刷", "暂不用", "暂不用箱一")


def norm_no(code, seq=None):
    """规范化刀模编号"""
    if code is None:
        return None
    s = str(code).strip().upper().replace(" ", "")
    if not s:
        return None
    m = NO.match(s)
    if m:
        return f"{m.group(1)}-{int(m.group(2)):03d}"
    if seq is not None and re.match(r"^[A-Z]{2,4}$", s):
        try:
            return f"{s}-{int(seq):03d}"
        except (TypeError, ValueError):
            return None
    return None


def status_of(detail):
    """刀模祥情 → engineering_die.status"""
    d = (detail or "").replace(" ", "")
    if "报废" in d:
        return "SCRAPPED"
    if "共用" in d or "共" == d[:1]:
        return "AVAILABLE"
    if any(k in d for k in ("无刀", "空档", "只印刷", "打样", "暂不用")):
        return "STOPPED"
    if any(k in d for k in ("委外", "送修", "维修", "维护")):
        return "MAINTENANCE"
    if "重做" in d:
        return "REPLACED"
    return "AVAILABLE"


LOC_TOKEN = re.compile(r"[A-Za-z]{1,3}-F[abcABC]\d+")


def norm_location(raw):
    """库位规范化（2026-09-21 修正）：
    老台账里 `B-Fb1   003`（架位 + 盒号，2~3 个空格）是**一个**库位，
    不能按空格拆开；只有同一格出现 **2 个以上库位码**（如 `C-Fc2   006   C-Fc2   003`）才拆成多库位。
    规则：空白折叠成单空格；按 、,;； 显式分隔；再把「库位码 + 盒号」聚成一段，多段用「；」连接。
    """
    if raw is None:
        return None
    s = " ".join(str(raw).split())
    if not s:
        return None
    parts, seen = [], set()
    for seg0 in re.split(r"、|,|;|；", s):
        seg0 = seg0.strip()
        if not seg0:
            continue
        hits = list(LOC_TOKEN.finditer(seg0))
        if len(hits) >= 2:
            for i, m in enumerate(hits):
                end = hits[i + 1].start() if i + 1 < len(hits) else len(seg0)
                seg = seg0[m.start():end].strip()
                if seg and seg not in seen:
                    seen.add(seg)
                    parts.append(seg)
        elif seg0 not in seen:
            seen.add(seg0)
            parts.append(seg0)
    return "；".join(parts) if parts else None


def norm_date(raw):
    if raw is None or raw == "":
        return None
    if isinstance(raw, (datetime.datetime, datetime.date)):
        return raw.strftime("%Y-%m-%d")
    s = str(raw).strip().replace("/", "-").replace(".", "-")[:10]
    try:
        return datetime.date.fromisoformat(s).strftime("%Y-%m-%d")
    except ValueError:
        return None


def header_index(header, names):
    for i, h in enumerate(header):
        if h and any(n in str(h) for n in names):
            return i
    return None


def read_sheet(ws):
    """读取单张刀模表 → [{die_no, ...}]"""
    rows = list(ws.iter_rows(values_only=True))
    if not rows:
        return []
    header = [str(c).strip() if c is not None else "" for c in rows[0]]
    has_header = header and any(str(h) == "客户" for h in header[:1])
    if has_header:
        body = rows[1:]
        i_no = 0
        i_seq = header_index(header, ("流水号", "序号"))
        i_det = header_index(header, DETAIL_HEADERS)
        i_qty = header_index(header, QTY_HEADERS)
        i_date = header_index(header, DATE_HEADERS)
        i_loc = header_index(header, LOC_HEADERS)
        if i_loc is None and len(header) >= 5 and ws.title in ("LJ",):
            i_loc = 4  # LJ 表位置列表头被写成「刀模架顶层盒1」
        i_rem = header_index(header, REMARK_HEADERS)
    else:
        body = rows
        i_no, i_seq, i_det, i_qty, i_date, i_loc, i_rem = 0, None, 1, 2, 3, 4, 5

    out = []
    for row in body:
        if not row:
            continue
        code = row[i_no] if i_no < len(row) else None
        seq = row[i_seq] if (i_seq is not None and i_seq < len(row)) else None
        no = norm_no(code, seq)
        if not no:
            continue
        def cell(i):
            return row[i] if (i is not None and i < len(row)) else None
        out.append({
            "die_no": no,
            "sheet": ws.title,
            "detail": (str(cell(i_det)).strip() if cell(i_det) not in (None, "") else ""),
            "quantity": cell(i_qty),
            "date": norm_date(cell(i_date)),
            "location": norm_location(cell(i_loc)),
            "remark": (str(cell(i_rem)).strip() if cell(i_rem) not in (None, "") else ""),
        })
    return out


def merge(records):
    """同一刀模号跨表合并（字段级取非空；冲突记录）"""
    merged, conflicts = {}, []
    for r in records:
        no = r["die_no"]
        if no not in merged:
            merged[no] = dict(r)
            continue
        tgt = merged[no]
        for f in ("detail", "location", "remark", "date"):
            v, old = r.get(f), tgt.get(f)
            if v and old and v != old and f in ("location",):
                conflicts.append((no, f, old, v))
            elif v and not old:
                tgt[f] = v
            elif v and old and f == "remark" and v not in old:
                tgt[f] = f"{old}｜{v}"
        q = r.get("quantity")
        if q not in (None, "") and tgt.get("quantity") in (None, ""):
            tgt["quantity"] = q
    return merged, conflicts


def to_row(rec):
    """合并记录 → 入库字段"""
    detail = (rec.get("detail") or "").strip()
    status = status_of(detail)
    remark_parts = []
    if "共用" in detail:
        m = re.search(r"共用\s*([A-Za-z]{2,4}-?\d{1,5})", detail)
        if m:
            ref = norm_no(m.group(1)) or m.group(1).upper()
            remark_parts.append(f"共用 {ref}")
        else:
            remark_parts.append(detail)
    elif detail and detail not in PURE_STATUS_WORDS and not any(detail == w for w in PURE_STATUS_WORDS):
        remark_parts.append(detail)
    if rec.get("remark"):
        remark_parts.append(rec["remark"])
    remark = "；".join(dict.fromkeys(remark_parts))
    if len(remark) > 500:
        remark = remark[:500]
    qty = rec.get("quantity")
    try:
        qty = int(float(qty)) if qty not in (None, "") else None
    except (TypeError, ValueError):
        qty = None
    return {
        "die_no": rec["die_no"],
        "die_name": rec["die_no"],
        "purpose": None,
        "specification": None,
        "version": None,
        "quantity": qty,
        "location": rec.get("location"),
        "stock_in_date": rec.get("date"),
        "status": status,
        "remark": remark or None,
    }


def sql_escape(v):
    if v is None:
        return "NULL"
    if isinstance(v, int):
        return str(v)
    s = str(v).replace("\\", "\\\\").replace("'", "''")
    return f"'{s}'"


def apply_rows(rows, chunk=500, db="jjx_erp_db", host="127.0.0.1", port="3306", user="root", password="123456"):
    total = 0
    for i in range(0, len(rows), chunk):
        part = rows[i:i + chunk]
        values = ",\n".join(
            "(" + ",".join(sql_escape(r[c]) for c in
                            ("die_no", "die_name", "purpose", "specification", "version",
                             "quantity", "location", "stock_in_date", "status", "remark")) + ",'import-legacy')"
            for r in part)
        sql = ("INSERT INTO engineering_die(die_no,die_name,purpose,specification,version,quantity,"
               "location,stock_in_date,status,remark,create_by) VALUES\n" + values +
               "\nON DUPLICATE KEY UPDATE die_name=VALUES(die_name),purpose=VALUES(purpose),"
               "specification=VALUES(specification),version=VALUES(version),quantity=VALUES(quantity),"
               "location=VALUES(location),stock_in_date=VALUES(stock_in_date),status=VALUES(status),"
               "remark=VALUES(remark),update_by='import-legacy',update_time=NOW();")
        env = dict(os.environ, MYSQL_PWD=password)
        res = subprocess.run(["mysql", "-h", host, "-P", port, "-u", user, db],
                             input=sql.encode("utf-8"), capture_output=True, env=env)
        if res.returncode != 0:
            sys.exit("导入失败：" + res.stderr.decode("utf-8", "ignore")[:500])
        total += len(part)
        print(f"  已写入 {total}/{len(rows)}")
    return total


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xlsx")
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--report", default=None)
    ap.add_argument("--db", default="jjx_erp_db")
    ap.add_argument("--user", default="root")
    ap.add_argument("--password", default="123456")
    args = ap.parse_args()

    wb = openpyxl.load_workbook(args.xlsx, read_only=True, data_only=True)
    records, per_sheet = [], {}
    for ws in wb.worksheets:
        if ws.title in SKIP_SHEETS:
            continue
        rs = read_sheet(ws)
        if rs:
            per_sheet[ws.title] = len(rs)
            records.extend(rs)
    wb.close()

    merged, conflicts = merge(records)
    rows = [to_row(r) for r in merged.values()]
    status_cnt, no_loc, no_qty, no_date = {}, 0, 0, 0
    for r in rows:
        status_cnt[r["status"]] = status_cnt.get(r["status"], 0) + 1
        no_loc += 0 if r["location"] else 1
        no_qty += 0 if r["quantity"] else 1
        no_date += 0 if r["stock_in_date"] else 1

    lines = []
    lines.append("# 刀模老台账导入 · 试运行报告\n")
    lines.append(f"- 源文件：`{os.path.basename(args.xlsx)}`")
    lines.append(f"- 扫描 sheet：{len(per_sheet)} 张（跳过 {len(SKIP_SHEETS)} 张无关表）")
    lines.append(f"- 读取刀模行：**{len(records)}**")
    lines.append(f"- 去重后刀模数量：**{len(rows)}**（跨表重复 {len(records) - len(rows)} 行已按字段合并）")
    lines.append("\n## 状态分布（映射后）\n")
    for k, v in sorted(status_cnt.items(), key=lambda x: -x[1]):
        lines.append(f"- {k}: {v}")
    lines.append("\n## 字段覆盖率\n")
    lines.append(f"- 有存放位置: {len(rows) - no_loc} / {len(rows)}（缺 {no_loc}）")
    lines.append(f"- 有数量: {len(rows) - no_qty} / {len(rows)}（缺 {no_qty}）")
    lines.append(f"- 有入库日期: {len(rows) - no_date} / {len(rows)}（缺 {no_date}）")
    lines.append("\n## 各表读取行数\n")
    for k, v in sorted(per_sheet.items(), key=lambda x: -x[1]):
        lines.append(f"- {k}: {v}")
    if conflicts:
        lines.append(f"\n## 冲突（同刀模号多表位置不一致，取先出现值）：{len(conflicts)} 条\n")
        for c in conflicts[:20]:
            lines.append(f"- {c[0]} {c[1]}：`{c[2]}` vs `{c[3]}`")
    lines.append("\n## 抽样（前 10 条）\n")
    lines.append("| 刀模编号 | 数量 | 位置 | 入库日期 | 状态 | 备注 |")
    lines.append("|---|---|---|---|---|---|")
    for r in rows[:10]:
        lines.append("| " + " | ".join(str(r[c] or "") for c in
                                       ("die_no", "quantity", "location", "stock_in_date", "status", "remark")) + " |")
    report = "\n".join(lines) + "\n"

    out = args.report or "/tmp/die-legacy-import-report.md"
    with open(out, "w", encoding="utf-8") as f:
        f.write("\ufeff" + report)
    print(report)
    print(f"\n报告已写出：{out}")

    if args.apply:
        print("开始写入数据库…")
        n = apply_rows(rows, db=args.db, user=args.user, password=args.password)
        print(f"写入完成：{n} 条（按 die_no upsert）")
    else:
        print("（试运行，未写库；加 --apply 才落库）")


if __name__ == "__main__":
    main()
