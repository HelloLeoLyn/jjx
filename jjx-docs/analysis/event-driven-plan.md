# 事件驱动联动方案

> 基于 2026-07-24 讨论
> 目标：业务事件→消息通知→看板任务 配置化联动

---

## 一、设计原则

**业务逻辑只关心"发生了什么"，不关心"发生后要做什么"**

```java
// ✅ 正确：业务代码只做一件事
eventService.fire("order.confirmed", orderId);

// ❌ 错误：业务代码又发通知又创看板
notificationService.create("工程部", "...");
kanbanService.createTask("设计任务", "...");
```

像日志一样——代码里只管 `log.info()`，输出到哪由配置决定。

---

## 二、架构

```
业务层                         事件层                         联动层
┌──────────────┐       ┌──────────────────┐       ┌────────────────┐
│ startProduction│──────→│  EventPublisher  │──────→│ 查 sys_event_   │
│ completeOrder │       │  (接口, 可替换)   │       │ config/         │
│ createFrom... │       │                  │       │ notification/   │
│ 预警检查      │       │ 本地实现:        │       │ kanban 配置表    │
└──────────────┘       │ Spring事件       │       ├────────────────┤
                       │ 同进程同步执行    │       │→ 发站内信       │
                       │ 事务内执行       │       │→ 创看板任务      │
                       │ 失败回滚        │       │→ (未来)发MQ     │
                       └──────────────────┘       └────────────────┘
```

**MQ：** 当前单体架构不需要。Spring ApplicationEventPublisher 够用。
**接口设计：** `EventPublisher` 接口先本地实现，日后换 MQ 只需换实现类。

---

## 三、配置表设计

### 3.1 sys_event_config — 事件定义

| 字段 | 类型 | 说明 |
|------|------|------|
| event_id | bigint PK | 主键 |
| event_code | varchar(50) UNIQUE | 事件编码，如 `order.confirmed` |
| event_name | varchar(100) | 事件名称 |
| biz_module | varchar(50) | 所属模块：sales/production/inventory |
| is_enabled | tinyint(1) | 是否启用 |
| remark | varchar(500) | 备注 |

### 3.2 sys_event_notification — 事件→通知映射

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | 主键 |
| event_id | bigint FK | 关联事件 |
| role_id | bigint | 接收角色（可多角色） |
| template_id | bigint FK | 通知模板 sys_notification_template |
| priority | tinyint | 优先级 |
| is_enabled | tinyint(1) | 是否启用 |

### 3.3 sys_event_kanban — 事件→看板映射

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | 主键 |
| event_id | bigint FK | 关联事件 |
| kanban_type | varchar(50) | 看板类型：engineering/production/purchase |
| target_column | varchar(50) | 目标列：todo/doing/done |
| card_title_template | varchar(200) | 卡片标题模板，支持 ${orderNo} |
| card_desc_template | varchar(500) | 卡片描述模板 |
| assign_role_id | bigint | 指派角色 |
| is_enabled | tinyint(1) | 是否启用 |

---

## 四、定义的事件清单

| 事件编码 | 触发点 | 通知角色 | 看板动作 |
|----------|--------|----------|----------|
| `order.confirmed` | `startProduction()` | 工程部 | 创建设计任务→工程看板 |
| `order.bom_missing` | BOM检查 | 产品工程师 | 标记任务缺BOM |
| `production.completed` | `completeOrder()` | 销售部 | 更新看板→待发货 |
| `purchase.arrived` | `createFromPurchase()` | 仓库/质检 | 创建入库任务→仓库看板 |
| `qc.failed` | 质检提交 | 生产主管 | 创建返工任务 |
| `stock.low` | 预警检查 | 采购部 | 创建补货任务→采购看板 |

---

## 五、实现步骤（共5步）

| 步骤 | 内容 | 工作量 | 状态 |
|------|------|--------|------|
| **1** | 建3张配置表 sys_event_config/notification/kanban | 1h | 📋 待办 |
| **2** | 实现 EventPublisher 接口 + 本地事件联动器 | 2h | 📋 待办 |
| **3** | 在业务代码插入 eventService.fire() 调用点 | 1h | 📋 待办 |
| **4** | jjx-kanban 开放 REST API 接收外部创建任务 | 2h | 📋 待办 |
| **5** | 前端通知栏+看板入口整合 | 1h | 📋 待办 |
| **合计** | | **~7h** | |

---

## 六、关键设计决策

| 决策 | 结论 |
|------|------|
| 硬编码 vs 配置化 | ✅ **配置化**（像日志一样） |
| 是否上MQ | ❌ **不需要**，单体够用，留接口将来换 |
| EventPublisher 设计 | ✅ **接口+实现分离**，可替换 |
| 通知机制 | ✅ 站内信（现有 NotificationService） |
| 看板联动 | ✅ jjx-kanban REST API |
