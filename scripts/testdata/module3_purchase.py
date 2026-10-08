#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模块 3 · 采购 —— 场景测试数据运行器（dev-20261008-023）

流程：复用供应商 + 物料 → 采购订单（生成单号→建→提交→审批）→ 收货（含检验，走采购侧 receive）。
口径：复用现有供应商/物料（不新建）；单号走系统规则（GET /purchase/order/generate-order-no）；[TST] 备注；幂等。
用法：env JJX_BASE_URL/JJX_USER/JJX_PASS/JJX_TENANT；可选 JJX_TST_SUPPLIER_ID、JJX_TST_MATERIAL_ID
"""
import os
import sys
import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import call, get, get_list, find_first, try_call, log, summary, TST  # noqa: E402

TODAY = datetime.date.today().isoformat()
DUE = (datetime.date.today() + datetime.timedelta(days=7)).isoformat()
SUPPLIER_ID = int(os.environ.get("JJX_TST_SUPPLIER_ID", "1"))
MATERIAL_ID = int(os.environ.get("JJX_TST_MATERIAL_ID", "1"))
QTY = 100
PRICE = 10


def get_supplier():
    row = get(f"/purchase/supplier/{SUPPLIER_ID}")
    name = row.get("supplierName") if isinstance(row, dict) else None
    log(f"使用供应商 id={SUPPLIER_ID} {name}")
    return SUPPLIER_ID, name


def get_material():
    m = get(f"/inventory/material/{MATERIAL_ID}")
    return m if isinstance(m, dict) else {}


def ensure_po(sid, sname, mat):
    row = find_first("/purchase/order/list", {"pageNum": 1, "pageSize": 50, "supplierId": sid},
                     lambda r: r.get("supplierId") == sid)
    if row:
        oid = row.get("orderId")
        log(f"复用采购订单 id={oid} approvalStatus={row.get('approvalStatus')}")
        return oid, row.get("approvalStatus")
    order_no = get("/purchase/order/generate-order-no")
    mcode, mname = mat.get("materialCode"), mat.get("materialName")
    munit = mat.get("unit") or "M"
    amount = QTY * PRICE
    body = {
        "orderNo": order_no, "supplierId": sid, "supplierName": sname,
        "orderDate": TODAY, "expectedDeliveryDate": DUE,
        "orderAmount": amount, "orderTax": 0, "orderTotalAmount": amount, "currency": "CNY",
        "items": [{"materialId": MATERIAL_ID, "materialCode": mcode, "materialName": mname,
                   "unit": munit, "quantity": QTY, "unitPrice": PRICE, "amount": amount}],
    }
    call("POST", "/purchase/order", body, "新增采购订单")
    r2 = find_first("/purchase/order/list", {"pageNum": 1, "pageSize": 50, "orderNo": order_no},
                    lambda r: r.get("orderNo") == order_no)
    oid = r2.get("orderId") if r2 else None
    log(f"新增采购订单 id={oid} no={order_no}")
    return oid, 1


def approve_po(oid):
    try_call("PUT", f"/purchase/order/submit/{oid}", None, "采购订单提交审批")
    try_call("PUT", "/purchase/order/approve",
             {"orderId": oid, "approverId": 1, "approverName": "系统管理员",
              "approvalComment": f"{TST} 审批通过", "approvalStatus": 3}, "采购订单审批通过")


def receive_po(oid):
    items = get_list(f"/purchase/order/{oid}/items")
    recv = [{"itemId": it.get("itemId"), "receivedQuantity": it.get("quantity")}
            for it in items if it.get("itemId")]
    if recv:
        try_call("POST", f"/purchase/order/{oid}/receive", {"items": recv}, "采购收货（含检验）")
    else:
        log("（无明细可收货）")


def main():
    log("=== 模块3 采购 造数（订单建/审批 → 收货）===")
    sid, sname = get_supplier()
    if not sid:
        summary()
        return
    mat = get_material()
    oid, st = ensure_po(sid, sname, mat)
    if not oid:
        log("⚠ 未取到采购订单 ID")
        summary()
        return
    if st != 3:
        approve_po(oid)
    receive_po(oid)
    log("=== 完成 ===")
    summary()


if __name__ == "__main__":
    main()
