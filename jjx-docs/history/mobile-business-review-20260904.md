# 移动端业务复查 · 第0步静态核查报告（任务 1420）

> 日期：2026-09-04 ｜ 范围：/m/* 移动端 7 页面 ↔ 后端接口 ↔ 权限矩阵
> 结论：接口全部存在；**权限缺口 3 处**是移动端走不通的主要嫌疑

---

## 一、接口存在性核查（全部 ✓）

| 链路 | 页面 | 后端接口 | 权限注解 | 存在 |
|---|---|---|---|---|
| 登录 | /m/login | POST /sessions/auth | 无（登录） | ✓ |
| 定位 | /m/scan→order | GET /production/order/code/{orderCode} | 无（登录即可） | ✓ |
| 定位 | /m/order | GET /production/tasks/my-executions | production:task:view | ✓ |
| 执行 | /m/order | PUT /production/operation-execution/{id}/start | 无 | ✓ |
| 执行 | /m/order | PUT .../pause | 无 | ✓ |
| 执行 | /m/order | PUT .../complete | 无 | ✓ |
| 报工入口 | /m/report | GET /production/tasks/mine | production:work-report:add | ✓ |
| 报工提交 | /m/report | POST /production/work-report | production:work-report:add | ✓ |
| 我的报工 | /m/reports | GET /production/work-report/mine | **production:work-report:view** | ✓ |
| 撤销报工 | /m/reports | POST /production/work-report/{id}/cancel | production:work-report:cancel | ✓ |
| 质检列表 | /m/quality | GET /production/quality/page | 无 | ✓ |
| 质检判定 | /m/quality | POST /production/quality/{id}/judge | **production:quality:judge** | ✓ |
| 领料预览 | /m/pick | GET /inventory/outbound/pick-preview/{workOrderId} | inventory:outbound:add | ✓ |
| 可领余量 | /m/pick | GET /inventory/outbound/pick-remaining/{workOrderId} | inventory:outbound:view | ✓ |
| 追加领料 | /m/pick | POST /inventory/outbound/create-production-pick/{workOrderId} | inventory:outbound:add | ✓ |

## 二、权限缺口（3 处，工人角色实操必 403）

相关角色：32=PRODUCTION 操作工、31=班组长、29=业务操作、28=全权限；22/23=INVENTORY

| # | 权限点 | 现状 | 影响 |
|---|---|---|---|
| 🔴 1 | `production:work-report:view` | 绑：1/28/29；**32 操作工没有** | 操作工打开 /m/reports「我的报工记录」→ 403（报工提交 add 有，看记录 view 无）|
| 🔴 2 | `production:quality:judge` | **未注册 sys_menu/任何角色都没有**（代码注解存在但菜单权限缺失）| 非超管调质检判定 judge → 403；移动端质检判定只有超管能用 |
| 🔴 3 | `inventory:outbound:add/view` | 绑：1/22/23/24（INVENTORY）；**生产角色 28-32 全无** | 工人扫码领料（pick-preview/remaining/create-production-pick）→ 403，领料移动化(1255)跑不通 |

## 三、其它发现（记录待办）
- execution 的 start/pause/complete/quality-check **无任何权限注解**（登录即可调）——工人能用，但任何角色都能操作执行，建议后续补细粒度权限（生产角色专用）
- 质检角色体系缺失：judge 该归谁（班组长/质检员）需业务定

## 四、下一步建议
1. 补权限缺口（方案见任务登记）→ 重启后做第 1 轮实跑（登录→扫码定位→执行→报工）
2. 造真实演示数据（进行中的工单+execution 分配给自己）
