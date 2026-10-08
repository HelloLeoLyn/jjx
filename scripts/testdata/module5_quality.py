#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模块 5 · 质量 —— 场景测试数据运行器（状态机版 v1，dev-20261008-023）

本阶段（v1）：① 检验批台账盘点（各类型/状态计数）② 待判定批→判定合格（幂等，只判 PENDING）
            ③ 不良台账 / 隔离台账盘点。
留续：OQC 出货检验、不良处置（返工/让步/报废）、隔离处置、复检。
用法：env JJX_BASE_URL/JJX_USER/JJX_PASS/JJX_TENANT
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import call, get, get_list, find_first, step, rec, report, log, TST  # noqa: E402


def main():
    log("=== 模块5 质量 造数（状态机 v1：台账+判定）===")
    lots = get_list("/quality/lot/page", {"pageNum": 1, "pageSize": 100})
    lots = lots if isinstance(lots, list) else []
    by_type, by_status = {}, {}
    for l in lots:
        by_type[l.get("lotType")] = by_type.get(l.get("lotType"), 0) + 1
        by_status[l.get("status")] = by_status.get(l.get("status"), 0) + 1
    rec("done", "检验批盘点",
        "类型 " + (",".join(f"{k}:{v}" for k, v in by_type.items()) or "无")
        + " | 状态 " + (",".join(f"{k}:{v}" for k, v in by_status.items()) or "无"))

    # 待判定批 → 判定合格（幂等；只处理 PENDING）
    judged = 0
    for l in lots:
        if str(l.get("status", "")).upper() in ("PENDING", "0", "1"):
            lid, lq = l.get("lotId"), (l.get("lotQuantity") or 0)
            if lid and lq:
                r = step(f"判定检验批(lot={lid},{l.get('lotType')})",
                         create=lambda lid=lid, lq=lq: call("POST", f"/quality/lot/{lid}/judge",
                             {"inspectedQuantity": lq, "passQuantity": lq, "failQuantity": 0, "result": "pass"},
                             "判定合格"))
                if r is not None:
                    judged += 1
    if not judged:
        rec("exists", "待判定批判定", "无待判定批")

    # 不良 / 隔离 台账
    ncr = get_list("/quality/ncr/page", {"pageNum": 1, "pageSize": 100})
    rec("done", "不良台账盘点", f"{len(ncr) if isinstance(ncr, list) else 0} 条")
    report()


if __name__ == "__main__":
    main()
