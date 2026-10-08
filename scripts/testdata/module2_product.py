#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模块 2 · 产品 / 工程 —— 场景测试数据运行器（状态机版，dev-20261008-023）

认领：本脚本造的产品/BOM/工艺路线用**记住的 id**（.tst-state.json）+ `[TST]` 标签认领。
状态机：产品(建→提交→审核→发布) ；BOM(建→提交→审核→设默认) ；工艺路线(建→提交→审批→设当前)。
产品发布前置：当前 BOM 已审批 + 工艺路线已审批。
用法：env JJX_BASE_URL/JJX_USER/JJX_PASS/JJX_TENANT；可选 JJX_TST_CUSTOMER_ID、JJX_TST_MATERIAL_ID、JJX_TST_PROCESS_ID
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import (call, get, get_list, find_first, get_customer, step, remember,  # noqa: E402
                     remembered, rec, report, log, mask, TST)

PRODUCT_NAME = mask("产品")
MATERIAL_ID = int(os.environ.get("JJX_TST_MATERIAL_ID", "1"))
PROCESS_ID = int(os.environ.get("JJX_TST_PROCESS_ID", "17"))
PROCESS_NAME = os.environ.get("JJX_TST_PROCESS_NAME", "面板")


def my_product():
    pid = remembered("m2.productId")
    if pid:
        p = get(f"/product/{int(pid)}")
        if isinstance(p, dict) and p.get("productId"):
            return p
    r = find_first("/product/page", {"pageNum": 1, "pageSize": 50},
                   lambda x: str(x.get("productName", "")).startswith(TST))
    if r:
        remember("m2.productId", r.get("productId"))
        return get(f"/product/{r.get('productId')}")
    return None


def my_bom(pid):
    bid = remembered("m2.bomId")
    if bid:
        b = get(f"/engineering/bom/{int(bid)}")
        if isinstance(b, dict) and b.get("bomId"):
            return b
    return find_first("/engineering/bom/page", {"pageNum": 1, "pageSize": 100},
                      lambda r: r.get("productId") == pid and str(r.get("bomCode", "")).startswith(TST))


def my_route(pid):
    rid = remembered("m2.routeId")
    if rid:
        r = get(f"/engineering/routings/{int(rid)}")
        if isinstance(r, dict) and r.get("routingId"):
            return r
    return find_first("/engineering/routings/page", {"pageNum": 1, "pageSize": 100},
                      lambda r: r.get("productId") == pid and str(r.get("routingCode", "")).startswith(TST))


def create_product(cid, cname):
    pid = call("POST", "/product", {
        "productName": PRODUCT_NAME, "customerId": cid, "customerName": cname, "unit": "PCS",
        "panelType": "M", "panelFeature": "O", "circuitType": "O", "circuitFeature": "O",
        "remark": f"{TST} 自动化造数"}, "新增产品")
    if pid:
        remember("m2.productId", pid)
    return get(f"/product/{pid}") if pid else None


def ensure_bom(p):
    pid, pcode = p.get("productId"), p.get("productCode")

    def create():
        mat = get(f"/inventory/material/{MATERIAL_ID}") or {}
        bid = call("POST", "/engineering/bom", {
            "bomCode": mask("BOM"), "bomName": mask("BOM"), "bomVersion": "V1",
            "productId": pid, "productCode": pcode,
            "items": [{"materialId": MATERIAL_ID, "materialCode": mat.get("materialCode"),
                       "materialName": mat.get("materialName"), "unit": mat.get("unit") or "M",
                       "quantity": 1, "moduleQty": 1, "baseQty": 1, "itemOrder": 1}]}, "新增BOM")
        if bid:
            remember("m2.bomId", bid)
        return get(f"/engineering/bom/{bid}") if bid else None

    bom = step("BOM", existing=lambda: my_bom(pid), create=create)
    if not bom:
        return None
    bid = bom.get("bomId")
    st = bom.get("approveStatus")
    if str(st) == "1":
        step("BOM提交审核", create=lambda: call("PUT", f"/engineering/bom/submit/{bid}", None, "BOM提交审核"))
        st = 2
    if str(st) != "3":
        step("BOM审核通过", create=lambda: call("PUT", f"/engineering/bom/approve/{bid}",
                                            {"bomId": bid, "current": 2, "target": 3, "remark": f"{TST} 通过"}, "BOM审核通过"))
    step("BOM设为默认", create=lambda: call("PUT", f"/engineering/bom/setDefault/{bid}", None, "BOM设为默认"))
    return bid


def ensure_route(p):
    pid, pcode, pname = p.get("productId"), p.get("productCode"), p.get("productName")

    def create():
        rid = call("POST", "/engineering/routings", {
            "routingCode": mask("ROUTE"), "routingName": mask("ROUTE"), "routingVersion": "V1",
            "productId": pid, "productCode": pcode, "productName": pname,
            "items": [{"processId": PROCESS_ID, "processName": PROCESS_NAME, "processOrder": 1,
                       "processCategory": "PANEL", "majorCategory": "PANEL"}]}, "新增工艺路线")
        rid = rid.get("routingId") if isinstance(rid, dict) else rid
        if rid:
            remember("m2.routeId", rid)
        return get(f"/engineering/routings/{rid}") if rid else None

    r = step("工艺路线", existing=lambda: my_route(pid), create=create)
    if not r:
        return None
    rid = r.get("routingId")
    st = r.get("approveStatus")
    if str(st) == "1":
        step("工艺路线提交", create=lambda: call("POST", f"/engineering/routings/{rid}/submit", None, "工艺路线提交"))
        st = 2
    if str(st) != "3":
        step("工艺路线审批通过", create=lambda: call("PUT", f"/engineering/routings/{rid}/approve", None, "工艺路线审批通过"))
    step("工艺路线设当前", create=lambda: call("PUT", f"/engineering/routings/{rid}/set-current", None, "工艺路线设为当前"))
    return rid


def main():
    log("=== 模块2 产品/工程 造数（状态机）===")
    cid, cname = get_customer()
    if not cid:
        rec("blocked", "取客户", "库里无客户（不新建）")
        report()
        return
    p = step("产品", existing=my_product, create=lambda: create_product(cid, cname))
    if not p:
        report()
        return
    ensure_bom(p)
    ensure_route(p)

    # 产品：提交→审核→发布（发布前置=BOM+路线已审批）
    p = get(f"/product/{p.get('productId')}") or p
    st = str(p.get("productStatus"))
    if st == "1":
        step("产品提交审核", create=lambda: call("PUT", f"/product/submit/{p.get('productId')}", None, "产品提交审核"))
        st = "2"
    if st in ("2", "3"):
        step("产品审核通过", create=lambda: call("PUT", f"/product/approve/{p.get('productId')}", None, "产品审核通过"))
    if st == "6":
        rec("exists", "产品发布", "已发布")
    else:
        step("产品发布", pre=lambda: (st in ("4", "2", "3", "1"), f"产品状态={st}（非已通过，无法发布）"),
             create=lambda: call("PUT", f"/product/release/{p.get('productId')}", None, "产品发布"))
    log(f">>> 模块1 用它：JJX_TST_PRODUCT_ID={p.get('productId')}")
    report()


if __name__ == "__main__":
    main()
