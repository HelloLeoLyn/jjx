#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模块 2 · 产品 / 工程 —— 场景测试数据运行器（dev-20261008-023）

本阶段：造一个**已发布**产品（含一个**已审批 + 设为默认**的 BOM）。
原因：产品发布要求“当前BOM已审批通过”；且询价选产品要求“属于该客户 + 已发布”。
工程侧其余（工艺路线 / 菲林 / 资源）待续。

口径：复用现有客户（不新建）；单号走系统规则；[TST] 备注标记；幂等。
用法：env JJX_BASE_URL/JJX_USER/JJX_PASS/JJX_TENANT；可选 JJX_TST_CUSTOMER_ID、JJX_TST_MATERIAL_ID
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import call, get, find_first, get_customer, log, summary, mask, TST  # noqa: E402

PRODUCT_NAME = mask("产品")
MATERIAL_ID = int(os.environ.get("JJX_TST_MATERIAL_ID", "1"))
PROCESS_ID = int(os.environ.get("JJX_TST_PROCESS_ID", "17"))
PROCESS_NAME = os.environ.get("JJX_TST_PROCESS_NAME", "面板")


def get_or_create_product(customer_id, customer_name):
    row = find_first("/product/page", {"pageNum": 1, "pageSize": 50},
                     lambda r: str(r.get("productName", "")).startswith(TST))
    if row:
        pid = row.get("productId")
        log(f"复用产品 id={pid} code={row.get('productCode')} status={row.get('productStatus')}")
        return pid, row.get("productCode"), str(row.get("productStatus", "")).upper()
    pid = call("POST", "/product", {
        "productName": PRODUCT_NAME,
        "customerId": customer_id, "customerName": customer_name, "unit": "PCS",
        # 产品编码构成（缺段会报“请完整选择面板结构/特征”）
        "panelType": "M", "panelFeature": "O", "circuitType": "O", "circuitFeature": "O",
        "remark": f"{TST} 自动化造数",
    }, "新增产品")
    log(f"新增产品 id={pid}")
    if not pid:
        return None, None, None
    p = get(f"/product/{pid}")
    code = p.get("productCode") if isinstance(p, dict) else None
    return pid, code, "DRAFT"


def ensure_bom(product_id, product_code):
    row = find_first("/engineering/bom/page", {"pageNum": 1, "pageSize": 100},
                     lambda r: r.get("productId") == product_id
                     and str(r.get("bomCode", "")).startswith(TST))
    if row:
        bom_id = row.get("bomId")
        st = row.get("approveStatus")
        log(f"复用BOM id={bom_id} approveStatus={st}")
    else:
        mat = get(f"/inventory/material/{MATERIAL_ID}")
        mcode = mat.get("materialCode") if isinstance(mat, dict) else None
        mname = mat.get("materialName") if isinstance(mat, dict) else None
        munit = (mat.get("unit") if isinstance(mat, dict) else None) or "M"
        bom_id = call("POST", "/engineering/bom", {
            "bomCode": mask("BOM"), "bomName": mask("BOM"), "bomVersion": "V1",
            "productId": product_id, "productCode": product_code,
            "items": [{"materialId": MATERIAL_ID, "materialCode": mcode, "materialName": mname,
                       "unit": munit, "quantity": 1, "moduleQty": 1, "baseQty": 1, "itemOrder": 1}],
        }, "新增BOM")
        log(f"新增BOM id={bom_id}")
        if not bom_id:
            return None
        st = 1
    if st == 1:  # DRAFT → 提交
        call("PUT", f"/engineering/bom/submit/{bom_id}", None, "BOM提交审核")
        st = 2
    if st != 3:  # 非已批准 → 批准（需请求体）
        call("PUT", f"/engineering/bom/approve/{bom_id}",
             {"bomId": bom_id, "current": 2, "target": 3, "remark": f"{TST} 审核通过"}, "BOM审核通过")
    call("PUT", f"/engineering/bom/setDefault/{bom_id}", None, "BOM设为默认")
    return bom_id


def ensure_route(product_id, product_code):
    row = find_first("/engineering/routings/page", {"pageNum": 1, "pageSize": 100},
                     lambda r: r.get("productId") == product_id
                     and str(r.get("routingCode", "")).startswith(TST))
    if row:
        rid = row.get("routingId")
        st = row.get("approveStatus")
        log(f"复用工艺路线 id={rid} approveStatus={st}")
    else:
        p = get(f"/product/{product_id}")
        pname = p.get("productName") if isinstance(p, dict) else None
        data = call("POST", "/engineering/routings", {
            "routingCode": mask("ROUTE"), "routingName": mask("ROUTE"), "routingVersion": "V1",
            "productId": product_id, "productCode": product_code, "productName": pname,
            "items": [{"processId": PROCESS_ID, "processName": PROCESS_NAME, "processOrder": 1,
                       "processCategory": "PANEL", "majorCategory": "PANEL"}],
        }, "新增工艺路线")
        rid = (data.get("routingId") if isinstance(data, dict) else data)
        log(f"新增工艺路线 id={rid}")
        if not rid:
            return None
        st = 1
    if st == 1:
        call("POST", f"/engineering/routings/{rid}/submit", None, "工艺路线提交审批")
        st = 2
    if st != 3:
        call("PUT", f"/engineering/routings/{rid}/approve", None, "工艺路线审批通过")
    call("PUT", f"/engineering/routings/{rid}/set-current", None, "工艺路线设为当前")
    return rid


def release_product(pid):
    p = get(f"/product/{pid}")
    s = str((p or {}).get("productStatus"))
    if s == "1":  # 开发中 → 提交
        call("PUT", f"/product/submit/{pid}", None, "产品提交审核")
        s = "2"
    if s in ("2", "3"):  # 待审核/审核中 → 通过
        call("PUT", f"/product/approve/{pid}", None, "产品审核通过")
    call("PUT", f"/product/release/{pid}", None, "产品发布")


def main():
    log("=== 模块2 产品/工程 造数（产品 → BOM审批 → 发布）===")
    cid, cname = get_customer()
    if not cid:
        summary()
        return
    pid, code, status = get_or_create_product(cid, cname)
    if not pid:
        log("⚠ 未取到产品 ID")
        summary()
        return
    ensure_bom(pid, code)
    ensure_route(pid, code)
    if str(status).upper() not in ("RELEASED", "6"):
        release_product(pid)
    log(f">>> 模块1 用它：export JJX_TST_PRODUCT_ID={pid}")
    summary()


if __name__ == "__main__":
    main()
