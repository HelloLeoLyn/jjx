#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模块 1 · 销售管理 —— 场景测试数据运行器（状态机版，dev-20261008-023）

认领：复用现有客户；本脚本造的询价/报价/样品单/销售订单用**记住的 id**（.tst-state.json）认领，
      不用"第一条匹配"。每步"探测→决策→执行"，四态记结果。
停止点 A：销售订单"客户确认"为止。

用法：env JJX_BASE_URL/JJX_USER/JJX_PASS/JJX_TENANT；可选 JJX_TST_CUSTOMER_ID、JJX_TST_PRODUCT_ID/CODE
"""
import os
import sys
import datetime
from urllib.parse import quote

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import (call, get, get_list, find_first, get_customer, step, remember,  # noqa: E402
                     remembered, rec, report, log, TST)

TODAY = datetime.date.today().isoformat()
DUE = (datetime.date.today() + datetime.timedelta(days=30)).isoformat()


def resolve_product_id():
    pid = os.environ.get("JJX_TST_PRODUCT_ID") or remembered("m2.productId")
    if pid:
        return int(pid)
    code = os.environ.get("JJX_TST_PRODUCT_CODE")
    if code:
        r = find_first("/product/page", {"pageNum": 1, "pageSize": 50},
                       lambda x: x.get("productCode") == code)
        return r.get("productId") if r else None
    return None


def by_id(path, key):
    """按记住的 id 认领（GET 校验仍存在）。"""
    rid = remembered(key)
    if not rid:
        return None
    try:
        r = get(f"{path}/{int(rid)}")
    except Exception:
        return None
    return r if isinstance(r, dict) and r.get(key.split('.')[-1].replace('Id', 'Id')) is not None or isinstance(r, dict) else None


def main():
    log("=== 模块1 销售 造数（状态机，停止点A）===")
    cid, cname = get_customer()
    if not cid:
        rec("blocked", "取客户", "库里无客户（不新建）")
        report()
        return
    pid = resolve_product_id()
    if not pid:
        rec("blocked", "取产品", "先跑模块2（或设 JJX_TST_PRODUCT_ID）")
        report()
        return

    # ① 询价
    def create_inquiry():
        d = call("POST", "/sales/inquiry", {
            "customerId": cid, "customerName": cname, "inquiryType": 1, "productId": pid,
            "inquiryDate": TODAY, "expectedQuantity": 1000, "unitPrice": 25, "unit": "PCS",
            "remark": f"{TST} 自动化造数"}, "新增询价单")
        iid = d.get("inquiryId") if isinstance(d, dict) else None
        if iid:
            remember("m1.inquiryId", iid)
        return iid
    iid = step("询价单", existing=lambda: remembered("m1.inquiryId"), create=create_inquiry)

    # ② 报价（询价转报价 或 复用）
    def create_quotation():
        d = call("POST", f"/sales/inquiry/convert/{iid}", None, "询价转报价")
        qid = d.get("quotationId") if isinstance(d, dict) else None
        if qid:
            remember("m1.quotationId", qid)
        return qid
    qid = step("报价单", existing=lambda: remembered("m1.quotationId"), create=create_quotation)

    # ③ 报价链：提交审核 → 审核 → 发送 → 客户确认（按状态补缺口）
    if qid:
        st = (get(f"/sales/quotation/{qid}") or {}).get("quotationStatus")
        step("报价提交审核", pre=lambda: (str(st) == "0", f"报价状态={st}"), create=lambda: call("PUT", f"/sales/quotation/submit-review/{qid}", None, "提交报价审核"))
        st = (get(f"/sales/quotation/{qid}") or {}).get("quotationStatus")
        step("报价审核通过", pre=lambda: (str(st) == "5", f"报价状态={st}"), create=lambda: call("PUT", f"/sales/quotation/review/{qid}?approved=true", None, "报价审核通过"))
        st = (get(f"/sales/quotation/{qid}") or {}).get("quotationStatus")
        step("报价发送给客户", pre=lambda: (str(st) == "6", f"报价状态={st}"), create=lambda: call("PUT", f"/sales/quotation/send/{qid}", None, "发送报价单"))
        st = (get(f"/sales/quotation/{qid}") or {}).get("quotationStatus")
        step("客户确认报价", pre=lambda: (str(st) in ("1", "6"), f"报价状态={st}"), create=lambda: call("PUT", f"/sales/quotation/confirm/{qid}", None, "客户确认报价"))

    # ④ 样品单（客户确认后转）
    def create_sample():
        d = call("POST", f"/sales/sample-order/create-from-quotation/{qid}"
                 f"?sampleQty=10&remark={quote(TST + '自动化')}", None, "报价转样品单")
        sid = d.get("orderId") if isinstance(d, dict) else d
        if sid:
            remember("m1.sampleOrderId", sid)
        return sid
    sid = step("样品单", existing=lambda: remembered("m1.sampleOrderId"), create=create_sample)

    # ⑤ 销售订单（直接建；按记住的 id 认领）
    def create_order():
        p = get(f"/product/{pid}") or {}
        body = {
            "customerId": cid, "customerName": cname, "orderDate": TODAY, "deliveryDate": DUE, "orderType": 1,
            "currency": "CNY", "exchangeRate": 1.0, "totalQuantity": 1000, "totalAmount": 25000,
            "salesManagerId": 1, "salesManagerName": "系统管理员",
            "items": [{"productId": pid, "productCode": p.get("productCode"), "productName": p.get("productName"),
                       "quantity": 1000, "unit": "PCS", "unitPrice": 25, "amount": 25000}],
            "remark": f"{TST} 自动化造数",
        }
        d = call("POST", "/sales/orders", body, "新增销售订单")
        oid = d.get("orderId") if isinstance(d, dict) else None
        if oid:
            remember("m1.salesOrderId", oid)
        return oid
    oid = step("销售订单", existing=lambda: remembered("m1.salesOrderId"), create=create_order)

    # ⑥ 订单：提交审核 → 开始 → 通过 → 客户确认（按状态补缺口）
    if oid:
        so = get(f"/sales/orders/{oid}") or {}
        st = str(so.get("orderStatus"))
        step("订单提交审核", pre=lambda: (st == "1", f"订单状态={st}"), create=lambda: call("PUT", f"/sales/orders/{oid}/status/submissions", None, "提交订单审核"))
        so = get(f"/sales/orders/{oid}") or {}
        st = str(so.get("orderStatus"))
        step("订单开始审核", pre=lambda: (st == "2", f"订单状态={st}"), create=lambda: call("PUT", f"/sales/orders/{oid}/status/review", None, "开始审核"))
        so = get(f"/sales/orders/{oid}") or {}
        st = str(so.get("orderStatus"))
        step("订单审核通过", pre=lambda: (st == "3", f"订单状态={st}"), create=lambda: call("PUT", f"/sales/orders/{oid}/status/approval", {"orderId": oid, "remark": f"{TST} 审核通过"}, "审核通过"))
        so = get(f"/sales/orders/{oid}") or {}
        st = str(so.get("orderStatus"))
        step("客户确认订单", pre=lambda: (st == "4", f"订单状态={st}"), create=lambda: call("PUT", f"/sales/orders/{oid}/confirm?confirmedBy={quote('系统管理员')}", None, "客户确认订单"))
    report()


if __name__ == "__main__":
    main()
