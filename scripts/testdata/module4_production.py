#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模块 4 · 生产 —— 场景测试数据运行器（状态机版，dev-20261008-023）

认领链：我的销售订单（模块1 记住的 id）→ 我的生产计划 → 我的工单 → 领料 → 工序/报工 → 完工入库。
每步"探测→决策→执行"：已存在则跳过（exists），前置不满足则被挡（blocked 带原因），否则执行（done/failed）。
绝不按"第一条匹配"去认领别人的数据。

用法：env JJX_BASE_URL/JJX_USER/JJX_PASS/JJX_TENANT；可选 JJX_TST_SALES_ORDER_ID（不设则用模块1 记住的）
"""
import os
import sys
import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _common import (call, get, get_list, find_first, step, remember, remembered,  # noqa: E402
                     rec, report, log, TST)

TODAY = datetime.date.today().isoformat()
DUE = (datetime.date.today() + datetime.timedelta(days=20)).isoformat()


def resolve_order():
    oid = os.environ.get("JJX_TST_SALES_ORDER_ID") or remembered("m1.salesOrderId")
    if not oid:
        return None
    so = get(f"/sales/orders/{int(oid)}")
    return int(oid) if isinstance(so, dict) and so.get("orderId") else None


def find_plan(so_id):
    return find_first("/production/order/page", {"pageNum": 1, "pageSize": 100, "orderType": "plan"},
                      lambda r: str(r.get("salesOrderId")) == str(so_id) and r.get("orderType") == "PLAN")


def find_wo(plan_id):
    return find_first("/production/order/page", {"pageNum": 1, "pageSize": 100, "orderType": "work_order"},
                      lambda r: str(r.get("parentOrderId")) == str(plan_id)
                      and r.get("orderType") == "WORK_ORDER")


def gen_plan(so_id):
    call("PUT", f"/sales/orders/{so_id}/status/generate-plan", None, "生成生产计划")
    return find_plan(so_id)


def convert(plan):
    qty = plan.get("remainingQuantity") or plan.get("plannedQuantity") or 1
    call("POST", "/production/order/convert-plan-to-work-orders", {
        "planId": plan.get("orderId"),
        "workOrders": [{
            "productId": plan.get("productId"), "productCode": plan.get("productCode"),
            "productName": plan.get("productName"), "plannedQuantity": qty,
            "planStartDate": plan.get("planStartDate") or TODAY,
            "planEndDate": plan.get("planEndDate") or DUE, "priority": "MEDIUM",
        }],
    }, "计划转工单")
    return find_wo(plan.get("orderId"))


def run_executions(wo_id, planned_qty):
    exs = get_list("/production/operation-execution/list", {"orderId": wo_id})
    exs = exs if isinstance(exs, list) else []
    for ex in exs:
        eid = ex.get("executionId")
        if not eid:
            continue
        qty = ex.get("plannedQuantity") or ex.get("outputQuantity") or planned_qty
        step(f"工序{eid} 开始", pre=lambda e=ex: (str(e.get("executionStatus")) in ("0", "1"),
                                                  f"工序状态={e.get('executionStatus')}（非法待执行态）"),
             create=lambda eid=eid: call("PUT", f"/production/operation-execution/{eid}/start", None, "开始工序"))
        t = get(f"/production/tasks/execution/{eid}/root")
        tid = t.get("taskId") if isinstance(t, dict) else None
        if tid:
            r = step(f"工序{eid} 报工",
                     create=lambda eid=eid, tid=tid, qty=qty: call("POST", "/production/work-report",
                                                                    {"executionId": eid, "taskId": tid, "reporterId": 1,
                                                                     "qualifiedQuantity": qty, "defectiveQuantity": 0,
                                                                     "remark": f"{TST} 自动化报工"}, "报工"))
            rid = r.get("reportId") if isinstance(r, dict) else None
            if rid:
                step(f"报工{rid} 审批", create=lambda rid=rid: call("POST", f"/production/work-report/{rid}/approve", None, "报工审批"))
        step(f"工序{eid} 完成", create=lambda eid=eid: call("PUT", f"/production/operation-execution/{eid}/complete", None, "完成工序"))


def judge_fqc(wo_id, qty):
    lots = get_list("/quality/lot/page", {"pageNum": 1, "pageSize": 20, "orderId": wo_id, "lotType": "FQC"})
    for lot in (lots if isinstance(lots, list) else []):
        if str(lot.get("status", "")).upper() in ("PENDING", "0"):
            lid = lot.get("lotId")
            lq = lot.get("lotQuantity") or qty
            step(f"完工检验FQC判定(lot={lid})",
                 create=lambda lid=lid, lq=lq: call("POST", f"/quality/lot/{lid}/judge",
                     {"inspectedQuantity": lq, "passQuantity": lq, "failQuantity": 0, "result": "pass"},
                     "FQC判定合格"))


def main():
    log("=== 模块4 生产 造数（状态机）===")
    so_id = resolve_order()
    if not so_id:
        rec("blocked", "取销售订单", "未找到（先跑模块1，或设 JJX_TST_SALES_ORDER_ID）")
        report()
        return

    plan = step("生成生产计划", existing=lambda: find_plan(so_id), create=lambda: gen_plan(so_id))
    if not plan:
        report()
        return
    remember("m4.planId", plan.get("orderId"))

    # 生产计划需先审批通过(2)才能转工单
    if str(plan.get("orderStatus")) == "1":
        step("生产计划审批",
             create=lambda: call("PUT",
                                 f"/production/order/status?orderId={plan.get('orderId')}&orderStatus=2",
                                 None, "生产计划审批通过"))
        plan = find_plan(so_id) or plan

    plan_closed = str(plan.get("orderStatus")) == "10" or float(plan.get("remainingQuantity") or 0) <= 0
    if plan_closed:
        wo = find_wo(plan.get("orderId"))
        rec("exists" if wo else "blocked", "计划转工单",
            "计划已全部下达，复用已有工单" if wo else "计划已关闭且找不到工单")
    else:
        wo = step("计划转工单", create=lambda: convert(plan))
    if not wo:
        report()
        return
    wo_id = wo.get("orderId")
    remember("m4.workOrderId", wo_id)

    step("工单启动", pre=lambda: (str(wo.get("orderStatus")) == "4", f"工单状态={wo.get('orderStatus')}（非已计划）"),
         create=lambda: call("PUT", f"/production/order/{wo_id}/start", None, "工单启动"))
    wo = get(f"/production/order/{wo_id}") or wo or {}  # 刷新状态
    step("领料出库", existing=lambda: find_first(
            "/inventory/outbound/list", {"pageNum": 1, "pageSize": 50},
            lambda r: r.get("sourceType") == "work_order" and str(r.get("sourceId")) == str(wo_id)),
         create=lambda: call("POST", f"/inventory/outbound/create-from-production/{wo_id}", None, "从工单创建领料出库"))
    run_executions(wo_id, wo.get("plannedQuantity"))
    judge_fqc(wo_id, wo.get("plannedQuantity"))
    step("工单完成（触发完工入库）", pre=lambda: (str(wo.get("orderStatus")) in ("6",),
                                            f"工单状态={wo.get('orderStatus')}（非进行中）"),
         create=lambda: call("PUT", f"/production/order/{wo_id}/complete", None, "完成生产工单"))
    report()


if __name__ == "__main__":
    main()
