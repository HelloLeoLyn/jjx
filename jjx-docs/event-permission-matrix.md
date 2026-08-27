# 🗺️ 事件 / 权限矩阵

> 版本: v1.0 | 最后更新: 2026-08-01
> 数据来源: sys_event_config（120条事件）+ sys_role_menu（362条权限点）
> 用途: 一张表说清"每个操作→通知谁→派任务给谁→谁有权限操作"

---

## 一、角色体系

| 角色ID | 角色 | 权限范围 | 职责 |
|---|---|---|---|
| 1 | 超级管理员 | 全部 97 菜单 | 全权 |
| 5 | 测试 | 45 菜单 | 测试专用 |
| 6 | 系统用户 | 6 菜单 | 系统维护 |
| 7 | 销售人员 | 41 权限点（全销售模块）| 客户/询价/报价/样品/订单 |
| 8 | 订单审核员 | 11 权限点 | 报价/订单/样品/采购审核 |
| 9 | 工程管理 | 33 权限点（工程12+产品10+生产6+销售样品10）| BOM/路线/菲林/样品打样/生产 |
| 10 | 销售管理all | 41 权限点 | 销售+审核管理 |
| 11 | 仓管 | 45 权限点（全库存模块）| 物料/库存/出入库/调拨/盘点 |

### 角色权限矩阵（按模块）

| 角色 | sales | purchase | inventory | production | engineering/product | system |
|---|---|---|---|---|---|---|
| 销售人员(7) | 41 | 0 | 0 | 0 | 0 | 0 |
| 订单审核员(8) | 8 | 3 | 0 | 0 | 0 | 0 |
| 工程管理(9) | 10 | 0 | 0 | 6 | 12 | 0 |
| 仓管(11) | 0 | 0 | 45 | 0 | 0 | 0 |

---

## 二、事件→角色映射（核心矩阵）

> event_type: both=通知+派任务, notification=仅通知

### 销售模块（32 事件）→ 销售[7]/审核[8]/工程[9]

| 操作 | 事件 | 类型 | 通知/派任务给 |
|---|---|---|---|
| 询价转报价 | inquiry.converted | both | 销售[7] |
| 报价提交审核 | quotation.submitted | both | 审核[8] |
| 报价审核通过/驳回 | quotation.reviewed | notification | 销售[7] |
| 报价发送客户 | quotation.sent | notification | 销售[7] |
| 报价客户确认/拒绝 | quotation.confirmed/rejected | notification | 销售[7] |
| 报价转订单 | quotation.converted | notification | 销售[7] |
| 订单提交 | order.submitted | notification | 审核[8] |
| 订单开始审核 | order.review_started | both | 审核[8] |
| 订单审核通过 | order.approved | notification | 销售[7,10] + 超管[1] |
| **订单提交生产** | order.production_started | both | 工程[9] + 超管[1] |
| 报价转样品 | sample.created | both | 工程[9] |
| 样品提交审核 | sample.submitted | notification | 审核[8] |
| 样品审核通过 | sample.approved | both | 工程[9] |
| 样品制作完成 | sample.ready | both | 销售[7] + 工程[9] |
| 样品送样 | sample.sent | notification | 销售[7] |
| 样品客户确认OK | sample.confirmed | notification | 销售[7] |
| 样品转量产 | sample.converted | both | 销售[7] |
| 样品客户退回 | sample.rejected_by_customer | notification | 工程[9] |
| 样品重新打样 | sample.restarted | both | 工程[9] |

### 采购模块（20 事件）→ 审核[8]

| 操作 | 事件 | 类型 | 通知/派任务给 |
|---|---|---|---|
| 采购单提交 | purchase.submitted | both | 审核[8] |
| 采购单审批通过 | purchase.approved | both | 审核[8] |
| 采购到货 | purchase.received | notification | 审核[8] + 工程[9] |
| 采购收货登记 | purchase.item_received | both | 审核[8] |
| 付款创建/审批/确认 | purchase.payment.* | both | 审核[8] |

### 产品工程模块（25 事件）→ 工程[9]

| 操作 | 事件 | 类型 | 通知/派任务给 |
|---|---|---|---|
| 产品提审 | product.submitted | both | 工程[9] |
| 产品审核通过 | product.approved | notification | 工程[9] |
| BOM提审/通过 | bom.submitted/approved | both/notification | 工程[9] |
| 路线提审/通过/驳回 | routing.* | both/notification | 工程[9] |
| 菲林提审/通过/发布 | film.* | both | 工程[9] |
| 产品实例生命周期 | instance.* | both | 工程[9] |

### 库存模块（44 事件）→ 仓管[11]

| 操作 | 事件 | 类型 | 通知/派任务给 |
|---|---|---|---|
| 入库单 创建/提交/通过/确认 | inbound.* | both | 仓管[11] |
| 出库单 创建/提交/通过/确认 | outbound.* | both | 仓管[11] |
| 调拨单 创建/提交/确认入/确认出 | transfer.* | both | 仓管[11] |
| 盘点单 全流程 | stocktake.* | both | 仓管[11] |
| 物料/仓库/库位/分类 增删改 | material.*/warehouse.* | notification | 仓管[11] |
| 库存不足/超上限预警 | stock.low/over | notification | 销售[7] + 审核[8]（紧急看板）|

### 生产模块（1 事件）⚠️ 严重不足

| 操作 | 事件 | 类型 | 通知/派任务给 |
|---|---|---|---|
| 工单完工 | production.completed | notification | 工程[9] |

---

## 三、操作→权限点对照（关键）

| 操作 | 权限点 | 角色 |
|---|---|---|
| 报价审核 | sales:quotation:approve | 审核[8] |
| 订单审核 | sales:order:approve | 审核[8] |
| 样品审核 | sales:sample:approve | 审核[8] |
| 样品工程操作 | sales:sample:engineering | 工程[9] |
| 采购审批 | purchase:order:approve | 审核[8] |
| 入库/出库操作 | inventory:inbound:* / outbound:* | 仓管[11] |
| BOM审核 | engineering:bom:approve | 工程[9] |

---

## 四、已知问题

1. **生产事件严重不足**（仅 production.completed 1 条）：工单排产/开工/工序执行/领料/质检无事件
2. **部分事件 target_role 为 NULL**：order.cancelled/rejected/resubmitted/sent_to_customer 不通知任何人
3. **审核员权限点少**（11 个）：只有审核类权限，无查看业务明细权限（如看不到报价明细）
4. **销售管理(10) 与销售(7) 权限点相同**（41 个），未体现管理差异
5. **工程角色(9) 无库存权限**：生产领料需仓管配合，工程无法直接看库存

---

## 五、使用规范

1. **新操作接入**：先查本矩阵 → 确认事件类型（单据=both，主数据=notification）→ 补 sys_event_config → 补权限点
2. **新角色**：对照矩阵分配模块权限，避免"有操作无权限"或"有权限无操作"
3. **事件与权限对齐检查**：事件通知的角色必须有对应权限点（否则通知了也做不了）
