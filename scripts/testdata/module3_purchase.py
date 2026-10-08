#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模块 3 · 采购 —— 场景测试数据运行器（状态机版，dev-20261008-023）

认领：复用现有供应商/物料；本脚本造的采购订单用**记住的 id** 认领（不用"第一条匹配"）。
每步"探测→决策→执行"，四态记结果。收货→IQC逐项检验→确认入库（原料过账）。

用法：env JJX_BASE_URL/JJX_USER/JJX_PASS/JJX_TENANT；可选 JJX_TST_SUPPLIER_ID、JJX_TST_MATERIAL_ID
"""
import os
import sys
import datetime
from urllib.parse import quote

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import (call, get, get_list, find_first, step, remember, remembered,  # noqa: E402
                     rec, report, log, TST)

TODAY = datetime.date.today().isoformat()
DUE = (datetime.date.today() + datetime.timedelta(days=7)).isoformat()
SUPPLIER_ID = int(os.environ.get("JJX_TST_SUPPLIER_ID", "1"))
MATERIAL_ID = int(os.environ.get("JJX_TST_MATERIAL_ID", "1"))
QTY, PRICE = 2000, 10


def get_my_po():
    pid = remembered("m3.purchaseOrderId")
    if not pid:
        return None
    try:
        r = get(f"/purchase/order/{int(pid)}")
    except Exception:
        return None
    return r if isinstance(r, dict) and r.get("orderId") else None


def create_po(sup_id, sup_name):
    mat = get(f"/inventory/material/{MATERIAL_ID}") or {}
    order_no = get("/purchase/order/generate-order-no")
    amount = QTY * PRICE
    call("POST", "/purchase/order", {
        "orderNo": order_no, "supplierId": sup_id, "supplierName": sup_name,
        "orderDate": TODAY, "expectedDeliveryDate": DUE,
        "orderAmount": amount, "orderTax": 0, "orderTotalAmount": amount, "currency": "CNY",
        "remark": f"{TST} 自动化造数",
        "items": [{"materialId": MATERIAL_ID, "materialCode": mat.get("materialCode"),
                   "materialName": mat.get("materialName"), "unit": mat.get("unit") or "M",
                   "quantity": QTY, "unitPrice": PRICE, "amount": amount}],
    }, "新增采购订单")
    row = find_first("/purchase/order/list", {"pageNum": 1, "pageSize": 50, "orderNo": order_no},
                     lambda r: r.get("orderNo") == order_no)
    if row:
        remember("m3.purchaseOrderId", row.get("orderId"))
    return row


def main():
    log("=== 模块3 采购 造数（状态机）===")
    sup = get(f"/purchase/supplier/{SUPPLIER_ID}")
    sup_name = sup.get("supplierName") if isinstance(sup, dict) else None
    po = step("采购订单", existing=get_my_po, create=lambda: create_po(SUPPLIER_ID, sup_name))
    if not po:
        report()
        return
    po_id = po.get("orderId")

    st = po.get("approvalStatus")
    if str(st) != "3":
        # 提交 + 审批
        if str(st) == "1":
            step("采购订单提交", create=lambda: call("PUT", f"/purchase/order/submit/{po_id}", None, "采购订单提交"))
        step("采购订单审批通过", create=lambda: call("PUT", "/purchase/order/approve", {
            "orderId": po_id, "approverId": 1, "approverName": "系统管理员",
            "approvalComment": f"{TST} 审批通过", "approvalStatus": 3}, "采购订单审批通过"))

    po = get(f"/purchase/order/{po_id}") or po
    if str(po.get("receiptStatus")) != "2":
        items = get_list(f"/purchase/order/{po_id}/items")
        recv = [{"itemId": it.get("itemId"), "receivedQuantity": it.get("quantity")}
                for it in items if it.get("itemId")]
        if recv:
            step("采购收货", create=lambda: call("POST", f"/purchase/order/{po_id}/receive", {"items": recv}, "采购收货"))

    # 入库（IQC：一次提交本单全部明细 + 逐项检测项 → 确认入库）
    inb = find_first("/inventory/inbound/list", {"pageNum": 1, "pageSize": 50},
                     lambda r: r.get("sourceNo") == po.get("orderNo"))
    if inb and str(inb.get("orderStatus")) != "10":
        iid = inb.get("inboundId")
        detail = get(f"/inventory/inbound/{iid}")
        items = detail.get("items") if isinstance(detail, dict) else None
        items = items if isinstance(items, list) else []
        iqc_items = [{
            "itemId": it.get("inboundItemId"), "sampledQuantity": it.get("quantity"),
            "inspectionResult": "pass", "qualifiedQuantity": it.get("quantity"),
            "acceptedQuantity": it.get("quantity"), "rejectedQuantity": 0, "disposition": "ACCEPT",
            "inspectionItems": [{"checkItem": "外观", "standard": "无缺陷", "result": "pass", "actualValue": "OK"}],
        } for it in items if it.get("inboundItemId")]
        step("IQC提交检验+审批(item数=%d)" % len(iqc_items), create=lambda: call(
            "POST", f"/inventory/inbound/submit-approve/{iid}", {"items": iqc_items}, "IQC提交检验+审批"))
        # 品质主管逐项审核 → 单据状态推进到已审批(2) → 才能确认过账
        for it in items:
            item_id = it.get("inboundItemId")
            if item_id:
                step(f"IQC项审核(item={item_id})", create=lambda item_id=item_id: call(
                    "POST", f"/inventory/inbound/inspection-item/{item_id}/approve",
                    {"approverId": 1, "approverName": "系统管理员", "remark": f"{TST} 合格"}, "IQC项审核"))
        step("确认入库（过账）", create=lambda: call(
            "POST", f"/inventory/inbound/confirm/{iid}?operatorId=1&operatorName={quote('系统管理员')}",
            None, "确认入库"))
    elif inb:
        rec("exists", "确认入库", "入库单已完成")
    else:
        rec("blocked", "确认入库", "未找到该采购单的入库单")
    report()


if __name__ == "__main__":
    main()
