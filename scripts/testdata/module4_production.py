#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模块 4 · 生产 —— 场景测试数据运行器（dev-20261008-023）

流程：从**已确认销售订单**生成生产计划 → 计划转工单 → 工单启动 → 领料（生产出库）
      → 工序执行 开始/完成 → 报工(提交/审批) → 工单完成 → 完工入库。
本版先跑通：生成计划 → 转工单 → 启动 → 领料；工序/报工/完工逐步补。

口径：复用已有订单/产品；单号走系统规则；幂等（已存在则复用/跳过）。
用法：env JJX_BASE_URL/JJX_USER/JJX_PASS/JJX_TENANT；可选 JJX_TST_SALES_ORDER_ID
"""
import os
import sys
import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import call, get, get_list, find_first, try_call, log, summary, TST  # noqa: E402

TODAY = datetime.date.today().isoformat()
DUE = (datetime.date.today() + datetime.timedelta(days=20)).isoformat()
SALES_ORDER_ID = os.environ.get("JJX_TST_SALES_ORDER_ID")


def find_confirmed_sales_order():
    """取一张已确认(6)的销售订单（模块1 的产物）。"""
    if SALES_ORDER_ID:
        return int(SALES_ORDER_ID)
    row = find_first("/sales/orders", {"pageNum": 1, "pageSize": 50},
                     lambda r: str(r.get("orderStatus")) in ("6", "7", "8"))
    return row.get("orderId") if row else None


def generate_plan(so_id):
    try_call("PUT", f"/sales/orders/{so_id}/status/generate-plan", None, "生成生产计划")


def find_plan(so_id):
    row = find_first("/production/order/page", {"pageNum": 1, "pageSize": 100, "orderType": "plan"},
                     lambda r: str(r.get("salesOrderId")) == str(so_id) and r.get("orderType") == "PLAN")
    return row


def find_wo(plan_id):
    return find_first("/production/order/page", {"pageNum": 1, "pageSize": 100, "orderType": "work_order"},
                      lambda r: str(r.get("parentOrderId")) == str(plan_id)
                      and r.get("orderType") == "WORK_ORDER")


def convert_plan(plan):
    if not plan:
        return None
    pid, pcode = plan.get("productId"), plan.get("productCode")
    pname = plan.get("productName")
    call("POST", "/production/order/convert-plan-to-work-orders", {
        "planId": plan.get("orderId"),
        "workOrders": [{
            "productId": pid, "productCode": pcode, "productName": pname,
            "plannedQuantity": plan.get("remainingQuantity") or plan.get("plannedQuantity") or 1000,
            "planStartDate": plan.get("planStartDate") or TODAY,
            "planEndDate": plan.get("planEndDate") or DUE,
            "priority": "MEDIUM",
        }],
    }, "计划转工单")
    row = find_first("/production/order/page", {"pageNum": 1, "pageSize": 100, "orderType": "work_order"},
                     lambda r: str(r.get("parentOrderId")) == str(plan.get("orderId"))
                     and r.get("orderType") == "WORK_ORDER")
    return row


def start_wo(wo_id):
    try_call("PUT", f"/production/order/{wo_id}/start", None, "工单启动")


def create_pick(wo_id):
    try_call("POST", f"/inventory/outbound/create-from-production/{wo_id}", None, "从工单创建领料出库")


def main():
    log("=== 模块4 生产 造数（计划→工单→启动→领料）===")
    so_id = find_confirmed_sales_order()
    if not so_id:
        log("⚠ 没有已确认的销售订单（先跑模块1）")
        summary()
        return
    log(f"用销售订单 id={so_id}")
    generate_plan(so_id)
    plan = find_plan(so_id)
    if not plan:
        log("⚠ 未找到生产计划")
        summary()
        return
    log(f"生产计划 id={plan.get('orderId')} no={plan.get('orderNo')} status={plan.get('orderStatus')}")
    if str(plan.get("orderStatus")) == "10" or float(plan.get("remainingQuantity") or 0) <= 0:
        wo = find_wo(plan.get("orderId"))
        log("计划已全部下达，复用已有工单")
    else:
        wo = convert_plan(plan)
    if not wo:
        log("⚠ 已转工单或未取到工单")
        summary()
        return
    wo_id = wo.get("orderId")
    log(f"工单 id={wo_id} no={wo.get('orderNo')} status={wo.get('orderStatus')}")
    start_wo(wo_id)
    create_pick(wo_id)
    log("=== 完成 ===")
    summary()


if __name__ == "__main__":
    main()
