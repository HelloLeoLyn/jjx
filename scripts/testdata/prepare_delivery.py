#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
为「多张销售单合并一张送货单」准备数据（dev-20261008-023）

在同一客户下造 N 张订单，并推进到「生产中(7)」——这是发货管理「待安排」池的准入状态
（DeliveryArrangeMapper: WHERE o.order_status=#{production}=7）。
幂等：已造的订单 id 记在 .tst-state.json（prep.orderIds）。

用法：env JJX_BASE_URL/JJX_USER/JJX_PASS/JJX_TENANT；JJX_TST_SALES_ORDER_COUNT（默认 3）
"""
import os
import sys
import datetime
from urllib.parse import quote

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import (call, get, get_list, find_first, get_customer, remember,  # noqa: E402
                     remembered, rec, report, log, TST)

TODAY = datetime.date.today().isoformat()
DUE = (datetime.date.today() + datetime.timedelta(days=30)).isoformat()
N = int(os.environ.get("JJX_TST_SALES_ORDER_COUNT", "3"))


def resolve_product_id():
    pid = os.environ.get("JJX_TST_PRODUCT_ID") or remembered("m2.productId")
    if pid:
        return int(pid)
    r = find_first("/product/page", {"pageNum": 1, "pageSize": 50},
                   lambda x: str(x.get("productName", "")).startswith(TST))
    return r.get("productId") if r else None


def make_order(cid, cname, pid, seq):
    p = get(f"/product/{pid}") or {}
    qty = 100 * seq
    d = call("POST", "/sales/orders", {
        "customerId": cid, "customerName": cname, "orderDate": TODAY, "deliveryDate": DUE, "orderType": 1,
        "currency": "CNY", "exchangeRate": 1.0, "totalQuantity": qty, "totalAmount": qty * 25,
        "salesManagerId": 1, "salesManagerName": "系统管理员",
        "items": [{"productId": pid, "productCode": p.get("productCode"), "productName": p.get("productName"),
                   "quantity": qty, "unit": "PCS", "unitPrice": 25, "amount": qty * 25}],
        "remark": f"{TST} 合并发货测试",
    }, f"新增销售订单#{seq}")
    oid = d.get("orderId") if isinstance(d, dict) else None
    if not oid:
        return None
    call("PUT", f"/sales/orders/{oid}/status/submissions", None, "提交审核")
    call("PUT", f"/sales/orders/{oid}/status/review", None, "开始审核")
    call("PUT", f"/sales/orders/{oid}/status/approval", {"orderId": oid, "remark": f"{TST} 通过"}, "审核通过")
    call("PUT", f"/sales/orders/{oid}/confirm?confirmedBy={quote('系统管理员')}", None, "客户确认")
    # 置「生产中(7)」 → 进入发货管理的「待安排」池
    call("PUT", f"/sales/orders/{oid}/status/generate-plan", None, "生成生产计划(→生产中)")
    return oid


def main():
    log(f"=== 备货：为合并发货造 {N} 张「生产中」订单 ===")
    cid, cname = get_customer()
    if not cid:
        rec("blocked", "取客户", "库里无客户")
        report()
        return
    pid = resolve_product_id()
    if not pid:
        rec("blocked", "取产品", "先跑模块2")
        report()
        return
    ids = remembered("prep.orderIds", []) or []
    # 校验已记住的订单仍在「生产中」
    ids = [i for i in ids if str((get(f"/sales/orders/{i}") or {}).get("orderStatus")) in ("7", "8")]
    while len(ids) < N:
        rec("done", f"造第 {len(ids) + 1} 张订单", f"客户={cname}")
        oid = make_order(cid, cname, pid, len(ids) + 1)
        if not oid:
            rec("failed", f"造第 {len(ids) + 1} 张订单", "未取到 orderId")
            break
        ids.append(oid)
    remember("prep.orderIds", ids)
    if len(ids) >= N:
        rec("exists", "订单张数", f"已有 {len(ids)} 张（≥{N}）")
    # 校验：待安排池
    av = get_list("/sales/deliveries/available-lines", {"pageNum": 1, "pageSize": 50})
    rec("done", "发货管理·待安排行数", f"{len(av) if isinstance(av, list) else 0}")
    report()


if __name__ == "__main__":
    main()
