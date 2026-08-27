# ⚡ 事件驱动体系完整分析

> 基于实际代码 + sys_event_config 表逐条梳理。
>
> 代码路径: `jjx-server/src/main/java/com/jjx/event/` + `com.jjx/notification/` + `com.jjx/system/annotation/`
>
> 最后更新: 2026-08-01

---

## 一、体系架构

```
业务操作（@Event 注解） → EventAspect 切面 → EventPublisher
                                              │
                       ┌──────────────────────┼──────────────────────┐
                       ▼                      ▼                      ▼
                 sys_event_config      sys_notification        sys_task(看板)
                 查配置(启用/类型)      (通知消息)              (派任务)
                       │                      │                      │
                       ▼                      ▼                      ▼
                  target_role          receiver_id=角色用户      看板模块+任务
                  (目标角色)             (通知谁)                (派任务给谁)
```

**核心组件**：
- `@Event(value, bizId, bizType)` 注解：标注业务操作
- `EventAspect`：AOP 拦截，触发事件
- `EventPublisher` / `LocalEventPublisher`：事件分发
- `sys_event_config` 表：事件配置（启用/类型/目标角色）

---

## 二、事件类型（event_type）

| 类型 | 含义 | 动作 |
|---|---|---|
| **both** | 通知 + 派任务 | 发 sys_notification + 创建 sys_task 看板任务 |
| **notification** | 仅通知 | 只发 sys_notification |

**设计原则**（2026-08-01 定）：
- 单据创建类新增 = both（通知+派任务）
- 主数据增删改 = notification（仅通知）

---

## 三、事件配置统计（120 条，is_enabled=1）

| 模块 | 事件数 | both | notification | 目标角色 |
|---|---|---|---|---|
| **inventory** 库存 | 44 | 24 | 20 | 仓管[11] |
| **sales** 销售 | 32 | 10 | 22 | 销售[7]/审核[8]/工程[9]/管理[10] |
| **product** 产品工程 | 25 | 12 | 13 | 工程[9] |
| **purchase** 采购 | 20 | 8 | 12 | 审核[8] |
| **production** 生产 | 1 | 0 | 1 | 工程[9] ⚠️ |

⚠️ **生产模块事件严重不足**（只有 production.completed 1 条）——工单排产/开工/工序/完工都应有事件

---

## 四、各模块事件清单

### 销售（32 条）→ 主要目标销售[7]/审核[8]

| 事件 | 名称 | 类型 | 目标 |
|---|---|---|---|
| inquiry.converted | 询价转报价 | both | [7] |
| quotation.submitted | 报价单提交审核 | both | [8] |
| quotation.confirmed/rejected/sent/reviewed/converted | 报价确认/拒绝/发送/审核/转订单 | notification | [7] |
| order.submitted | 订单提交 | notification | [8] |
| order.review_started | 订单开始审核 | both | [8] |
| order.approved | 订单审核通过 | notification | [7,10,1] |
| order.production_started | 订单提交生产 | both | [9,1] |
| sample.created/approved | 报价转样品/样品审核通过 | both | [9] |
| sample.submitted | 样品单提交审核 | notification | [8] |
| sample.ready | 样品制作完成 | both | [7,9] |
| sample.sent | 样品已送样 | notification | [7] |
| sample.confirmed | 样品客户确认OK | notification | [7] |
| sample.converted | 样品转量产 | both | [7] |
| sample.rejected_by_customer | 样品客户退回 | notification | [9] |
| sample.restarted | 样品重新打样 | both | [9] |

### 产品工程（25 条）→ 目标工程[9]

| 事件 | 名称 | 类型 |
|---|---|---|
| product.submitted | 产品提审 | both |
| product.approved | 产品审核通过 | notification |
| bom.submitted | BOM提审 | both |
| bom.approved | BOM审核通过 | notification |
| routing.submitted/approved/rejected/version_changed | 路线提审/通过/驳回/版本 | both/notification |
| film.submitted/approved/rejected/released | 菲林提审/通过/驳回/发布 | both |
| instance.created/started/completed/delivered | 产品实例生命周期 | both |

### 采购（20 条）→ 目标审核[8]

| 事件 | 名称 | 类型 |
|---|---|---|
| purchase.submitted | 采购单提审 | both |
| purchase.approved | 采购订单审批通过 | both |
| purchase.received | 采购到货 | notification [8,9] |
| purchase.item_received | 采购收货登记 | both |
| purchase.payment.* | 付款创建/审批/确认 | both |

### 库存（44 条）→ 目标仓管[11]

| 事件 | 名称 | 类型 |
|---|---|---|
| inbound.created/submitted/approved/rejected/confirmed | 入库单全生命周期 | both(创建/提交/通过/确认) |
| outbound.created/submitted/approved/rejected/confirmed | 出库单全生命周期 | both |
| transfer.created/submitted/approved/confirmed_in/out | 调拨单生命周期 | both |
| stocktake.created/started/submitted/approved/closed | 盘点单生命周期 | both |
| material.*/warehouse.*/category.*/location.* | 主数据增删改 | notification |
| stock.low / stock.over | 库存不足/超上限预警 | notification [7,8] 紧急看板 |

### 生产（1 条）⚠️

| 事件 | 名称 | 类型 |
|---|---|---|
| production.completed | 工单完工 | notification [9] |

---

## 五、目标角色定义

| 角色ID | 角色 | 接收事件 |
|---|---|---|
| 1 | 超级管理员 | 订单审核通过/提交生产 |
| 7 | 销售人员 | 销售/样品/库存预警 |
| 8 | 订单审核员 | 报价/订单/样品/采购审核 |
| 9 | 工程管理 | 产品/样品/生产 |
| 10 | 销售管理 | 订单审核通过 |
| 11 | 仓管 | 库存全部 |

---

## 六、看板联动（kanban_module）

事件派任务时的看板模块：
- 大部分事件 → `office`（办公任务看板）
- `order.production_started` → `production`（生产看板）⚠️ 需确认生产看板支持
- `stock.low/over` → `emergency`（紧急看板）

---

## 七、已知问题

1. **生产事件覆盖不足**（仅 1 条）：工单排产/开工/工序执行/领料/质检都无事件
2. **部分 target_role 为 NULL**：order.cancelled/rejected/resubmitted/sent_to_customer 无目标角色（不通知）
3. **BOM/路线审核是空壳**：bom.approved 事件存在但 approve 接口不做事（工程文档已记录）
4. **事件触发完整性**：需 E2E 验证每个 @Event 注解的操作是否真触发（DEV-436 待验证）
