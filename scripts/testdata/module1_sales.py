#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模块 1 · 销售管理 —— 场景测试数据运行器（dev-20261008-023）

覆盖（真实子流程，停止点 A = 订单“客户确认”为止）：
  客户 → 询价 → 转报价 → 报价审核 → 客户确认 → 转样品单 → 提交打样 → 审核 → 工程接单
       → 转量产(打样转标准) → 销售订单 → 提交/开始/审核通过 → 客户确认
口径：[TST] 标记 + 幂等增量（已存在复用/跳过）+ 不依赖 db-clean-test-data；--dry-run 只打印不落库。
发货/签收/收款为可选段，用 --ship 打开（默认关，避免跨入生产/出库模块）。

用法：
  export JJX_BASE_URL=http://127.0.0.1:8080
  export JJX_USER=admin  JJX_PASS=******  JJX_TENANT=1
  python3 scripts/testdata/module1_sales.py [--dry-run]
"""
import os
import sys
import datetime
from urllib.parse import quote

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import call, get, get_list, find_first, get_customer, try_call, log, summary, mask, TST  # noqa: E402

TODAY = datetime.date.today().isoformat()
DUE = (datetime.date.today() + datetime.timedelta(days=30)).isoformat()

# 一个测试产品编码（模块 2 的产物；无则报价/订单明细只能空）——由 env 覆盖
PRODUCT_CODE = os.environ.get("JJX_TST_PRODUCT_CODE", "")


def resolve_product_id():
    """取一个用于询价/报价/订单的测试产品：env JJX_TST_PRODUCT_ID 优先，否则按 JJX_TST_PRODUCT_CODE 查。"""
    pid = os.environ.get("JJX_TST_PRODUCT_ID")
    if pid:
        return int(pid)
    code = os.environ.get("JJX_TST_PRODUCT_CODE", PRODUCT_CODE)
    if not code:
        return None
    row = find_first("/product/page", {"pageNum": 1, "pageSize": 50, "productCode": code},
                     lambda r: r.get("productCode") == code)
    return row.get("productId") if row else None


def ensure_inquiry(customer_id, customer_name):
    row = find_first("/sales/inquiry/list", {"pageNum": 1, "pageSize": 100},
                     lambda r: r.get("customerId") == customer_id)
    if row:
        log(f"复用询价单 id={row.get('inquiryId')}")
        return row.get("inquiryId")
    pid = resolve_product_id()
    if not pid:
        log("⚠ 询价需要产品（标准品必须选 productId），但未找到测试产品 → 先跑模块 2 或设 JJX_TST_PRODUCT_ID/CODE")
        return None
    data = call("POST", "/sales/inquiry", {
        "customerId": customer_id, "customerName": customer_name,
        "inquiryType": 1, "productId": pid,
        "inquiryDate": TODAY, "expectedQuantity": 1000, "unitPrice": 25, "unit": "PCS",
        "remark": f"{TST} 自动化造数",
    }, "新增询价单")
    iid = (data or {}).get("inquiryId") if isinstance(data, dict) else None
    log(f"新增询价单 id={iid}")
    return iid


def inquiry_to_quotation(inquiry_id):
    data = call("POST", f"/sales/inquiry/convert/{inquiry_id}", None, "询价转报价")
    return data


def review_quotation(quotation_id):
    try_call("PUT", f"/sales/quotation/submit-review/{quotation_id}", None, "提交报价审核")
    try_call("PUT", f"/sales/quotation/review/{quotation_id}?approved=true", None, "报价审核通过")
    try_call("PUT", f"/sales/quotation/send/{quotation_id}", None, "发送报价单给客户")
    try_call("PUT", f"/sales/quotation/confirm/{quotation_id}", None, "客户确认报价")


def quotation_to_sample(quotation_id):
    data = try_call("POST",
                    f"/sales/sample-order/create-from-quotation/{quotation_id}"
                    f"?sampleQty=10&remark={quote(TST + '自动化')}",
                    None, "报价转样品单")
    return data.get("orderId") if isinstance(data, dict) else data


def run_sample_flow(order_id):
    try_call("PUT", f"/sales/sample-order/submit-request/{order_id}", None, "样品单申请打样")
    try_call("PUT", f"/sales/sample-order/approve/{order_id}", None, "样品单审核通过")
    try_call("PUT", f"/sales/sample-order/start-engineering/{order_id}", None, "工程接单")
    try_call("PUT", f"/sales/sample-order/mark-ready/{order_id}", None, "工程标记样品完成")
    try_call("PUT", f"/sales/sample-order/send-sample/{order_id}", None, "销售送样登记")
    try_call("PUT", f"/sales/sample-order/confirm/{order_id}", None, "客户确认样品OK")


def transfer_sample(order_id):
    try_call("POST", "/sample/transfer/confirm", {"orderId": order_id}, "打样转标准（资料转移）")


def create_order(customer_id, customer_name):
    row = find_first("/sales/orders", {"pageNum": 1, "pageSize": 50},
                     lambda r: r.get("customerId") == customer_id)
    if row:
        oid = row.get("orderId")
        log(f"复用销售订单 id={oid} status={row.get('orderStatus')}")
        return oid
    pid = resolve_product_id()
    p = get(f"/product/{pid}") if pid else {}
    pcode = p.get("productCode") if isinstance(p, dict) else None
    pname = (p.get("productName") if isinstance(p, dict) else None) or "TST测试产品"
    body = {
        "customerId": customer_id, "customerName": customer_name,
        "orderDate": TODAY, "deliveryDate": DUE, "orderType": 1,
        "currency": "CNY", "exchangeRate": 1.0,
        "totalQuantity": 1000, "totalAmount": 25000,
        "salesManagerId": 1, "salesManagerName": "系统管理员",
        "items": [{
            "productId": pid, "productCode": pcode, "productName": pname,
            "quantity": 1000, "unit": "PCS", "unitPrice": 25, "amount": 25000,
        }],
    }
    data = call("POST", "/sales/orders", body, "新增销售订单")
    oid = (data or {}).get("orderId") if isinstance(data, dict) else None
    log(f"新增销售订单 id={oid}")
    return oid


def review_order(order_id):
    try_call("PUT", f"/sales/orders/{order_id}/status/submissions", None, "提交订单审核")
    try_call("PUT", f"/sales/orders/{order_id}/status/review", None, "开始审核")
    try_call("PUT", f"/sales/orders/{order_id}/status/approval",
             {"orderId": order_id, "remark": f"{TST} 审核通过"}, "审核通过")
    try_call("PUT", f"/sales/orders/{order_id}/confirm?confirmedBy={quote('系统管理员')}",
             None, "客户确认订单（停止点A）")


def main():
    log("=== 模块1 销售管理 造数（停止点A：到订单客户确认）===")
    cid, cname = get_customer()
    if not cid:
        summary()
        return
    iid = ensure_inquiry(cid, cname)
    if not iid:
        log("（询价未建立，停止——请先跑模块2造产品）")
        summary()
        return
    qrow = find_first("/sales/quotation/list", {"pageNum": 1, "pageSize": 50},
                      lambda r: r.get("customerId") == cid)
    if qrow:
        qid = qrow.get("quotationId")
        log(f"复用报价单 id={qid}")
    else:
        q = inquiry_to_quotation(iid)
        log(f"询价转报价返回：{q}")
        qid = q.get("quotationId") if isinstance(q, dict) else None
    if not qid:
        log("⚠ 未取到报价单 ID，后续步骤跳过")
        summary()
        return
    review_quotation(qid)
    sid = quotation_to_sample(qid)
    log(f"报价转样品返回样品单 id={sid}")
    if not sid:
        srow = find_first("/sales/sample-order/page", {"pageNum": 1, "pageSize": 50},
                          lambda r: r.get("customerId") == cid)
        sid = srow.get("orderId") if srow else None
    if sid:
        run_sample_flow(sid)
        transfer_sample(sid)
    else:
        log("⚠ 未取到样品单 ID")
    oid = create_order(cid, cname)
    if oid:
        review_order(oid)
    log("=== 完成 ===")
    summary()


if __name__ == "__main__":
    main()
