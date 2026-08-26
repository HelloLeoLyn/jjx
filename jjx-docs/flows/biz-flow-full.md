# JJX ERP 业务流程全图（含前端操作）

> 读者：开发 | 粒度：菜单→页面→按钮→弹窗→表单字段
> 版本：v0.2（阶段一重点：打样→转销售）
> 编写：2026-08-12

---

# 总览

## 核心业务主线（薄膜开关定制制造）

```
客户需求 → 询价 → 报价 → 样品打样 → 客户确认 → 转量产订单
  → 审核 → 生成生产计划 → 转工单 → 工序执行 → 质检 → 完工入库
  → 发货 → 开票 → 收款
```

## 六大模块与菜单树

| 模块 | 顶级菜单 | 子菜单 |
|---|---|---|
| 销售管理 | 销售管理 | 客户管理 / 询价管理 / 报价管理 / 样品单管理 / 销售订单 / 订单跟踪 / 发货管理 / 销售报表 |
| 产品管理 | 产品管理 | 产品列表 / 产品分类 / 产品实例 / 产品配置模型 |
| 工程管理 | 工程管理 | 标准工序 / BOM管理 / 工艺路线 / 打样平台 / 薄膜管理 / 作业项目 |
| 采购管理 | 采购管理 | 供应商管理 / 采购计划 / 采购订单 / 采购发票 / 采购付款 / 采购收货 |
| 生产管理 | 生产管理 | 生产订单 / 工序执行 / 操作记录 / 派工管理 / 生产追溯 / 设备管理 / 工装模具档案 / 成本核算 / 生产报表 |
| 库存管理 | 库存管理 | 物料管理 / 仓库管理 / 库存列表 / 预警管理 / 入库管理 / 出库管理 / 盘点管理 / 调拨管理 |
| 系统管理 | 系统管理 | 用户 / 角色 / 菜单 / 部门 / 字典 / 事件配置 / 单据模板配置 / 系统配置 / 文件管理 |
| 日志管理 | 日志管理 | 操作日志 / 登录日志 / 异常日志 |

## 流水 / 任务 / 通知 归属说明（重要）

**这三者是系统级机制，不属于任何单一业务阶段**，业务阶段只负责"触发"：

| 机制 | 实现 | 归属 |
|---|---|---|
| **流水（链路追踪）** | 每个单据带 `traceId`，操作写 `sys_oper_log`，前端 TraceTimeline 按 traceId 查全链路 | 系统级（日志/链路），所有业务页面都有"查看流水"按钮 |
| **任务（sys_task）** | 业务操作发 `@Event` → `LocalEventPublisher` 查 `sys_event_config` 配置 → 按角色创建看板任务（dev/office 模块） | 系统级（事件配置），触发点在业务节点 |
| **通知（sys_notification）** | 同一事件机制，按 `target_role` 展开用户逐个发通知（可排除触发者 exclude_trigger） | 系统级（事件配置） |

**事件配置**：系统管理 → 事件配置（`sys_event_config`），可开关、配角色/标题/内容模板。
**看板**：任务看板模块（kanban），任务按 kanban_module 分组（dev/office/production）。

> 业务阶段文档只标注"触发事件：xxx"，机制细节在阶段六（系统管理）详述。

---

# 阶段一：销售管理

> 代码：`jjx-server/src/main/java/com/jjx/sales/` + `jjx-web/src/views/sales/`
> 全链路必经：**客户 → 询价 → 报价 → 样品打样 → 转量产订单**
> ⭐ 本阶段重点：**打样 → 转销售**

## 1.1 客户管理（简单）

**菜单**：销售管理 → 客户管理 | **页面**：`src/views/sales/customer/index.vue`

- 操作：新增 / 编辑 / 删除 / 审核(启停) / 导入 / 导出 / 查看
- 关键字段：客户名称、简称、类型(终端/代理/经销)、等级(A/B/C)、联系人电话、税号、开户行账号、信用额度
- 作用：销售链路的客户主数据，询价/报价/订单都引用

## 1.2 询价管理（简单）

**菜单**：销售管理 → 询价管理 | **页面**：`src/views/sales/inquiry/index.vue`

- 状态机：`草稿 → 待处理 → 已发送 → 已确认/已拒绝`，`草稿 → 已转报价`（终态）
- 操作：新增 / 编辑 / 删除 / 发送 / 接受 / 拒绝 / **转报价** / 导出
- 关键字段：客户、产品描述、预估数量、预估单价（转报价继承）、特殊要求、有无图纸
- **转报价**：`convertToQuotation` → 生成报价单（承继客户/联系人/销售员/单价），回写询价单 converted 状态
- 触发事件：`inquiry.converted`（通知：已转为报价单）

## 1.3 报价管理（中等，因为通向样品）

**菜单**：销售管理 → 报价管理 | **页面**：`src/views/sales/quotation/index.vue` + `print.vue`

- 状态机：`草稿 → 待审核 → 已审核 → 已发送 → 已确认`，`已驳回 → 草稿`，`草稿 → 已转样品/订单`
- 操作：新增 / 编辑 / 复制 / 提交审核 / 审核通过/驳回 / 发送 / **转样品单** / 转订单 / 重新报价 / 附件 / 导出Excel/PDF
- 主表字段：报价单号、客户、类型、日期、有效期、币种汇率、交货天数、付款条件、销售员
- 明细行：产品、数量、单位、单价、金额、特殊要求（增删行 addItem/removeItem）
- **转样品单**：`createFromQuotation` → 生成样品单（承继报价明细+客户）
- 触发事件：`quotation.submitted / reviewed / sent / converted`

---

## 1.4 样品单管理（⭐ 本阶段重点：打样 → 转销售）

**菜单**：销售管理 → 样品单管理 | **页面**：`src/views/sales/sample-order/index.vue` + `print.vue` + `transfer-edit.vue`(资料转移)
**API**：`src/api/sales/sampleOrder.ts`

### 状态机（10 态）
```
1 样品需求已创建 → 2 待审核 → 3 工程打样中 → 4 样品待送样 → 5 已送样待确认
  → 6 样品确认 → 7 已转量产（终态）
  ↘ 9 客户退回 → 回 3 重新打样（可多轮）
  2 → 10 已取消（终态）；8 已关闭（终态）
```

### 操作按钮（按状态）
| 状态 | 按钮 |
|---|---|
| 1 已创建 | 提交审核 / 编辑 / 删除 |
| 2 待审核 | 审核通过 / 审核驳回 |
| 3 工程打样中 | **打样平台**（跳工程）/ 打样完成标记（打样平台联动） |
| 4 样品待送样 | 送样（填快递单号） |
| 5 已送样待确认 | 客户确认(OK) / 客户退回 |
| 6 样品确认 | **转量产** / 重新打样 |
| 全部 | 详情 / 流水 / 打印 / 资料转移 |

### 打样环节（核心：样品单 ↔ 工程打样平台联动）
```
审核通过(2→3) → 工程打样中
  → 工程侧「打样平台」(views/engineering/sample-workbench) 录工序/BOM/图纸
  → 打样完成 → 样品单状态 4 样品待送样
  → 送样(5) → 客户确认(6) 或 退回(9→回3重新打样)
```
- 图纸/文档：打样平台录图纸、BOM、工艺参数，样品单与产品/BOM/工艺路线资料转移衔接
- 触发事件：`sample.created / submitted / approved / ready / sent / confirmed / rejected_by_customer / restarted`

### 转量产（⭐ 核心链路：打样成功 → 销售订单）
```
convertToProduction(sampleOrderId)
  ① 状态校验：必须 6 样品确认
  ② 标准化（可选）：前端传 items 则按明细替换为正式产品档案（编码/名称/规格/单位）
  ③ 就绪检查（SampleConvertCheckVO）：
     - 产品建档 + 已发布（required）
     - BOM 已批准（required）
     - 工艺路线已批准（required）
     - 菲林档案（suggest，不阻塞）
     任一 required 不通过 → 抛"转量产资料未就绪：xxx，请先补全"
  ④ 生成标准销售订单（SO 号，草稿状态，承继客户/报价/明细）
  ⑤ 回写样品单：status=7 已转量产 + convertedOrderId
```
- 触发事件：`sample.converted`

---

## 1.5 销售订单（转量产产物 + 独立下单）

**菜单**：销售管理 → 销售订单 | **页面**：`src/views/sales/order/index.vue` + `add.vue`/`edit.vue`

### 状态机（10 态，2026-08-12 定稿：去掉客户确认）
```
1草稿 → 2待审核 → 3审核中 → 4已审核/5已驳回 → (生成生产计划) → 7生产中 → 8已发货 → 9已完成
  5已驳回 → 1草稿(重新提交)；任意 → 10已取消
  状态6已确认：保留枚举不流转
```

### 操作按钮（按状态）
| 状态 | 按钮 |
|---|---|
| 1 草稿 | 提交审核 / 编辑 / 删除 / 复制 |
| 2 待审核 | 开始审核 |
| 3 审核中 | 审核通过（可上传确认书，选填）/ 审核驳回 |
| 4 已审核 | **生成生产计划**（唯一操作） |
| 5 已驳回 | 重新提交 |
| 7 生产中 | 发货（自动创建销售出库单扣库存）/ 齐套检查 |
| 8 已发货 | 完成订单 |
| 全部 | 查看 / 流水 / 导出 |

### 审核通过（approveOrder）联动
```
校验 3 → 置 4，随后（异常不阻断主流程）：
  ① 齐套检查 checkOrderShortage → order_shortage 预警
  ② 全局缺料检查 checkGlobalShortage → demand_shortage 预警
  ③ 成品库存预留 reserveForOrder
  ④ 原料占用 confirmReserve
  附件：审核时可上传确认书（AttachmentUploader，bizType=order，选填）
```

### 生成生产计划
```
校验 4 → 每产品生成一张 PLAN → 自动进入待审批 → 工单启动时才置 7
（生产细节见阶段四）
```

---

## 1.6 发票 / 收款（无前端入口，走流程即可）

> ⚠️ 现状：后端接口存在（`/sales/invoice`、`/sales/receipt`），**前端暂无独立页面**（菜单未挂），走后端流程：

```
销售订单（发货后）→ 发票登记 POST /sales/invoice（开票信息+金额）
                 → 收款登记 POST /sales/receipt（收款金额，actualAmount 默认=receiptAmount）
                 → 导出/查询
```

---

_阶段一完。下一步：阶段二（产品工程）——标准工序/BOM/工艺路线/打样平台/薄膜管理/作业项目，重点写"图纸设计 + 打样文档"（用户指定突出项）。_

---

# 阶段二：产品工程

> 代码：`jjx-server/src/main/java/com/jjx/product/` + `com/jjx/engineering/` + `jjx-web/src/views/product/` + `src/views/engineering/`
> 核心职责：**把样品打样成果固化成产品档案（BOM/工艺路线/图纸），为量产提供工程依据**
> ⭐ 本阶段重点：**图纸设计 + 打样文档**（打样平台）

## 2.1 产品列表（产品档案主数据）

**菜单**：产品管理 → 产品列表 | **页面**：`src/views/product/list/index.vue` + `add.vue`/`edit.vue`

### 状态机（8 态，前端 ProductStatusEnum）
```
1开发中 → 2待审核 → 3审核中 → 4已通过 → 6已发布 → 7停产
                    ↘ 5已驳回 → 回1
任何状态可 → 8取消
```

### 操作按钮
| 按钮 | 动作 | 状态限制 |
|---|---|---|
| 新增/编辑 | 表单弹窗 | 开发中/已驳回/取消可编辑 |
| 提交 | handleSubmit → 待审核 | 开发中/已驳回 |
| 审核通过/驳回 | handleApprove/handleReject | 待审核 |
| 发布 | handlePublish → 已发布 | 已通过 |
| 停产 | handleObsolete | 已发布 |
| 配置BOM | handleConfigBom → 跳 BOM 编辑 | 全部 |
| 配置路线 | handleConfigRoute → 跳工艺路线 | 全部 |
| 查看BOM | handleViewBom | 全部 |
| 取消/删除/导出 | 常规 | — |

### 关键字段
`productCode`(自动流水)、`productName`、`specJson`(规格)、`unit`、`categoryId`、`productStatus`、`版本/发布信息`

> 发布状态(6) 是转量产就绪检查的硬性条件（见阶段一 1.4）。

## 2.2 产品分类 / 产品实例 / 产品配置模型

| 菜单 | 页面 | 说明 |
|---|---|---|
| 产品分类 | `src/views/product/category/index.vue` | 分类树，产品挂分类 |
| 产品实例 | `src/views/product/instance/index.vue` | 产品配置实例（客户化变体） |
| 产品配置模型 | `src/views/engineering/config/index.vue` | 配置选项（addOption/设默认/启停），驱动实例化 |

## 2.3 标准工序（工序主数据）

**菜单**：工程管理 → 标准工序 | **页面**：`src/views/product/standard-process/index.vue`

- 操作：新增 / 编辑 / 删除 / 启用停用(handleToggleEnabled) / 导入(模板+结果) / 导出
- 字段：工序编码、工序名称、工序类型、所属大类（冲型组装/印刷）
- 作用：工艺路线的可选工序库（印刷类工序走自定义录入，不走库）

## 2.4 BOM 管理（物料清单）

**菜单**：工程管理 → BOM 管理 | **页面**：`src/views/product/bom/index.vue`
**API**：`src/api/product/bom.ts`

### 状态机
```
draft(草稿) → reviewing(审核中) → approved(已审核) → active(生效中) → inactive(已失效)
                     ↘ 驳回 → 回 draft
```

### 操作按钮
| 按钮 | 动作 |
|---|---|
| 新增 / 编辑 | 表单弹窗（明细行） |
| 复制BOM | handleCopyBom → 新版本草稿 |
| 提交审核 | handleSubmitApprove |
| 审核通过/驳回 | handleApprove（ApproveDialog，结果+意见） |
| 删除 / 导出 / 查看 | 常规 |

### 关键字段
`bomCode`、`bomName`、`bomVersion`、`productCode/Name`、`effectiveDate/expiryDate`、`approveStatus`
明细：物料编码/名称/用量/单位（材料从工序聚合或手工维护）

> BOM 已批准(approved+) 是转量产就绪检查的硬性条件。

## 2.5 工艺路线（工序路线）

**菜单**：工程管理 → 工艺路线 | **页面**：`src/views/product/route/index.vue`

### 状态机（RouteStatusEnum）
```
1草稿 → 2审核中 → 3已批准
              ↘ 4已驳回 → 回草稿
```

### 操作按钮
| 按钮 | 动作 |
|---|---|
| 新增 / 编辑 | 表单（工序列表：标准工序 + 自定义印刷行） |
| 复制 | handleCopy → 新版本 |
| 提交审核 | handleSubmitApprove |
| 审核通过/驳回 | handleApprove |
| 版本对比 | handleVersionCompare（多版本 diff） |
| 详情 | handleDetail |

### 关键字段
`routingCode`、`routingName`、`routingVersion`、`productCode/Name`、`isCurrent`(当前生效版本)、`processCount`、`totalLaborHours/totalMachineHours`
工序行：工序顺序、标准工序/自定义印刷（major_category: ASSEMBLY/PRINT）、工艺参数、材料、工时

> 路线已批准是转量产就绪检查的硬性条件；印刷工序行承载打样阶段的印刷参数（印刷名称/色号/油墨/网框）。

## 2.6 打样平台（⭐ 图纸设计 + 打样文档，本阶段重点）

**菜单**：工程管理 → 打样平台 | **页面**：`src/views/engineering/sample-workbench/index.vue`（列表）+ `workbench.vue`（工作台）
**组件**：SampleInfoCard / ProcessCard / PrintProcessPanel / BomPanel / NoteFilesPanel / ExecutionTimeline / PlanBoard / BatchToolbar / FrequentMaterialsBar

### 入口
- 从样品单列表"打样平台"按钮进入（阶段一 1.4 联动）
- 打样平台列表：样品单号（点击进工作台）、客户、轮次、状态、当前工序、工序数、工时

### 工作台结构（workbench.vue）
```
① 轮次 Tabs（Round 1/2/…，多轮打样）
② 大类 Tabs：🛠 冲型组装(ASSEMBLY) | 🖨️ 印刷(PRINT)
   - 冲型组装：工序卡片（ProcessCard，标准工序流程）
   - 印刷：PrintProcessPanel（自定义工序表格：印刷名称/色号/油墨编号/网框编号…）
③ 样品信息卡（SampleInfoCard）：客户/轮次 + 接单/拒单 + 📤 上传图纸/文件（样品单级）
④ BOM 面板（BomPanel）：各工序材料自动聚合 → 📦 资料转移
⑤ 工艺参数/备注（NoteFilesPanel）
⑥ 执行时间线（ExecutionTimeline）：工序完成进度
```

### 打样流程（开发视角）
```
进工作台 → 录/改工序（冲型组装 + 印刷）→ 挂材料（FrequentMaterialsBar 常用料）
→ 保存工序计划 → 上传图纸/文件 → 执行工序（标记完成，时间线推进）
→ 打样完成 → 回写样品单状态 4 样品待送样
→ 客户确认(6) → 📦 资料转移：把本轮工序+材料建档为 产品/BOM/工艺路线
```

### 资料转移（关键动作）
```
transfer(样品单)
  预览匹配：工序/材料 → 产品/BOM/工艺路线 映射（可人工调整）
  确认后：建档产品（状态待审核）→ 建 BOM → 建工艺路线
  回写样品单：transferred 标记
  触发事件：sample.transferred
```

## 2.7 图纸管理 / 薄膜管理 / 作业项目

| 菜单 | 页面 | 说明 |
|---|---|---|
| 图纸管理 | `src/views/product/drawing/index.vue` | 图纸上传/预览/下载/版本；字段：drawingNo/Name/Type/File/Format/Size/Version |
| 薄膜管理 | `src/views/product/film/index.vue` | 菲林档案（打样网版）；字段：filmCode/Name/Type/Size/Version/设计人；操作：新增/审核/发布/新版本 |
| 作业项目 | `src/views/engineering/work-project/index.vue` | 项目式作业记录；字段：processCode/Name/Type/Category/Icon/Description；操作：新增/编辑/提交 |

> 菲林档案：转量产就绪检查的**建议项**（不阻塞）。

---

_阶段二完。下一步：阶段三（采购管理）——供应商/采购计划/采购订单/收货/发票/付款，重点"预警转采购计划"。_

---

# 阶段三：采购管理

> 代码：`jjx-server/src/main/java/com/jjx/purchase/` + `jjx-web/src/views/purchase/`
> 核心链路：**缺料预警 → 采购计划 → 采购订单 → 收货 → 发票 → 付款**
> ⭐ 本阶段重点：**预警转采购计划**（缺料驱动采购）

## 3.0 触发源头：库存预警（在库存模块，但采购由此驱动）

**菜单**：库存管理 → 预警管理 | **页面**：`src/views/inventory/alert/index.vue`

- 预警来源：订单齐套检查（order_shortage）/ 全局缺料检查（demand_shortage）——见阶段一 1.5 审核通过联动
- 操作：标记已读 / 批量已读 / 处理（handleProcess）/ 查看详情 / 导出 / **去采购计划**(goPurchasePlan) / **去采购订单**(goPurchaseOrders)
- 预警状态：0 未处理 / 1 已读 / 2 已处理（终态）

## 3.1 供应商管理

**菜单**：采购管理 → 供应商管理 | **页面**：`src/views/purchase/supplier/index.vue`

- 操作：新增 / 编辑 / 删除 / 查看 / 导入(模板) / 导出 / **评估**(handleEvaluation，供应商评分)
- 关键字段：供应商编码/名称/简称、类型、行业、联系人/电话/邮箱、税号、开户行账号、资质、评估记录

## 3.2 采购计划（⭐ 预警转计划）

**菜单**：采购管理 → 采购计划 | **页面**：`src/views/purchase/plan/index.vue`

### 入口
- 预警页"去采购计划"跳转（带 materialId/alertId，可预填——预警闭环4 目标）
- 手动新增（showAddMaterial）

### 操作
| 按钮 | 动作 |
|---|---|
| 添加物料 | showAddMaterial → confirmAddMaterial（编码/名称/建议数量/当前库存/单位/原因） |
| 移除行 | removeRow |
| 生成建议 | loadSuggestions（按缺料建议补数量） |
| 清空 | clearPlan |
| **确认计划** | showConfirmDialog → doConfirmPlan → 生成采购订单（草稿） |
| 导出 | handleExport |

### 计划行字段
`materialCode/materialName`、`suggestQuantity`、`currentStock`、`unit`、`reason`

### 确认计划链路
```
doConfirmPlan(计划行)
  校验物料/数量
  生成采购订单（草稿，单号 PO-时间戳）
  回写预警：batchProcess(alertIds, relatedOrderNo) → 预警状态=2 已处理 + 关联采购单号
```

## 3.3 采购订单

**菜单**：采购管理 → 采购订单 | **页面**：`src/views/purchase/order/index.vue`

### 状态机（9 态，PurchaseOrderStatusEnum）
```
0草稿 → 1询价中 → 2比价中 → 3已提交 → 4已批准 → 5执行中 → 6已完成
                    ↘ 8已取消          ↘ 驳回
7已关闭
```

### 操作按钮
| 按钮 | 动作 |
|---|---|
| 新增 | 表单弹窗（明细行） |
| 提交审核 | handleSubmitReview → 3已提交 |
| 审核通过/驳回 | openPreview('purchase.approve'/'purchase.reject') |
| 取消 | openPreview('purchase.cancel') |
| **收货** | receiveDialog（按明细行收货：收货数量/检验结果/备注） |
| **付款** | paymentDialog（付款金额/状态，isOrderPayable 控制显示） |
| 打印 / 导出 PDF / 导出 | handlePrint / handleExportPdf / handleExport |
| 流水 | showTrace |

### 主表字段
`orderNo`(PO-时间戳)、`supplierId/Name`、`orderType`、`orderDate`、`expectedDeliveryDate`、`currency`、`orderAmount/orderTax/orderTotalAmount`、`approvalStatus`、`receiptStatus`、`paymentStatus`、`urgentFlag`
明细：`materialId/Code/Name`、`quantity`、`unitPrice`、`amount`、`receivedQuantity`

## 3.4 收货 / 发票 / 付款（单据级推进）

| 环节 | 实现 | 说明 |
|---|---|---|
| 收货 | `receiveOrderItem` / `/purchase/order/{id}/receive` | 按明细收货数量 + 检验结果（合格/不合格）+ 备注；影响 receiptStatus |
| 发票 | `/purchase/invoice`（upload-temp 临时上传 / batch-confirm 批量确认） | 采购发票登记（临时文件 + 确认） |
| 付款 | `updatePaymentInfo` → `/purchase/order/payment/{id}` | 付款金额 + 状态；未付款订单列表 `/purchase/order/pending-payment` |

> 采购发票/付款前端入口：菜单挂载但组件待确认（同销售侧），单据级推进为主。

---

_阶段三完。下一步：阶段四（生产管理）——生产订单/派工/工序执行/质检/追溯/设备/工装模具/成本/报表。_

---

_阶段三完。下一步：阶段四（生产管理）——生产订单/派工/工序执行/质检/追溯/设备/工装模具/成本/报表。_


---

# 阶段四：生产管理

> 代码：`jjx-server/src/main/java/com/jjx/production/` + `jjx-web/src/views/production/`
> 核心链路：**销售订单 → 生成生产计划(PLAN) → 转工单 → 派工 → 工序执行(报工) → 完工质检 → 完工入库 → 发货**
> ⭐ 本阶段重点：**工单执行闭环（派工/工序执行/质检/追溯）**

## 4.1 生产订单（计划 + 工单合并）

**菜单**：生产管理 → 生产订单 | **页面**：`src/views/production/order/index.vue`

### 状态机（12 态，ProductionOrderStatusEnum）
```
0草稿 → 1待审核 → 2已审核 → 3已驳回 → 4已计划 → 5待开始 → 6进行中 → 8已完成
                                                      ↘ 7已暂停 → 6
                                                      ↘ 9已取消 / 11已超期
10已关闭（终态）
```

### 入口
- 销售订单"生成生产计划"（阶段一 1.5）→ 生成 PLAN（order_type=PLAN）
- 计划审批 → **转工单**（convertPlanToWorkOrders：按产品拆 WORK_ORDER，编号 WO-{planNo}-{seq}）
- 直接转工单（销售订单 startProduction 快捷模式，现已不在前端展示）

### 操作
| 按钮 | 动作 |
|---|---|
| 计划转工单 | convertDialogVisible → addConvertRow/submitConvert（按产品行拆工单） |
| 工单开始/暂停/完成 | 状态推进（联动工序执行） |
| 打印随工单 | 随工单打印页（含工单号二维码，扫码A-1） |
| 派工 | 跳派工管理（带 orderNo） |

### 工单关键字段
`orderNo`(WPO 或 WO-)、`orderType`(PLAN计划/WORK_ORDER工单)、`productId/Code/Name`、`plannedQuantity/completedQuantity/remainingQuantity`、`planStartDate/planEndDate`、`priority`(LOW/MEDIUM/HIGH/URGENT)、`dispatchTeam/Leader`、`materialStatus`(0未领料/1待发料/2已领料)、`orderStatus`

> 生成工单时按工艺路线自动生成工序执行记录（generateOperationExecutions，含印刷大类信息）。

## 4.2 派工管理

**菜单**：生产管理 → 派工管理 | **页面**：`src/views/production/dispatch/index.vue`
**方案**：`jjx-docs/specs/dispatch-spec.md`

### 状态机（派工单）
```
0待派工 → 1已派工 → 2执行中 → 3已完成
                  ↘ 4已退回（原因必填）→ 可改派
```

### 操作
| 按钮 | 动作 |
|---|---|
| 派工 | openAssign → assignVisible → handleAssign（选班组/设备/操作员 operators JSON） |
| 批量派工 | openBatchDialog → batchVisible → handleBatchAssign（多工单一次派） |
| 开始 | handleStart → 执行中（联动工序执行） |
| 完成 | handleComplete → 已完成 |
| 退回 | openReject → rejectVisible → handleReject（原因必填，可改派） |
| 派工记录 | openLogs（派工操作流水） |
| 搜索/重置 | handleSearch / handleReset |

### 派工字段
`orderId/orderNo`、`executionId`(工序执行)、`processName/processOrder`、`teamId/teamName`、`equipmentId/Name`、`operators`(JSON 操作员列表)、`assignedBy/assignTime`、`status`、`rejectReason`、`reDispatchCount`

## 4.3 工序执行（报工核心）

**菜单**：生产管理 → 工序执行 | **页面**：`src/views/production/execution/index.vue`
**后端**：`ProductionOperationExecutionController`（/production/operation-execution）

### 状态机（10 态，ExecutionStatusEnum）
```
0待执行 → 1准备中 → 2执行中 → 4已完成
                    ↘ 3已暂停 → 2
                    ↘ 6已取消 / 5已跳过 / 7已超期 / 8异常中 / 9待确认
```

### 操作按钮
| 按钮 | 动作 | 说明 |
|---|---|---|
| 开始 | handleStart → 2执行中 | 记录实际开始时间 |
| 暂停 | handlePause → 3已暂停 | 记录暂停 |
| 完成 | handleComplete → 4已完成 | 校验投入/产出/合格/不良数量 |
| 质检 | handleQualityCheck → qcVisible → submitQc | 首检/巡检，记录质量数据 |
| 报工记录 | handleRecord → recordOpen → submitRecord | 写 production_operation_record（START/PAUSE/COMPLETE/QUALITY/ISSUE/PARAM） |
| 详情 | detailOpen / handleView | 执行详情 |

### 关键字段
`orderId/processId/processOrder`、`plannedStart/EndTime`、`actualStart/EndTime`、`actualLaborHours/actualMachineHours`、`equipmentId/Code/Name`、`operatorId/Name`、`inputQuantity/outputQuantity`、`qualifiedQuantity/defectiveQuantity/defectiveReason`、`actualProcessParams`(JSON)、`qualityCheckResult`(JSON)、`executionStatus`
印刷行：`majorCategory/processName/customProcessParams`（印刷工序参数，打样→路线→工单透传）

> 完成数量校验：工单已完工(8)后冻结，禁止再报工/改数量（053）。

## 4.4 完工质检

**菜单**：生产管理 →（质检页面）| **页面**：`src/views/production/quality/index.vue`

- 操作：创建质检单(handleCreate) / 检验报告(handleReport) / 设置(showSettings) / 全部检验(viewAllInspections) / 详情(viewInspectionDetail)
- 质检结果：通过/不通过（FAIL 待返工，rework_flag）
- 联动：完工质检通过 → 允许完工入库（createFromProduction 校验工单已完成）

## 4.5 生产追溯

**菜单**：生产管理 → 生产追溯 | **页面**：`src/views/production/trace/index.vue`

- 操作：正向追溯(doTraceForward) / 反向追溯(doTraceBackward) / 查询 / 重置
- 维度：工单 → 工序执行 → 操作记录 → 物料 → 批次，双向追溯

## 4.6 设备管理 / 工装模具档案

| 菜单 | 页面 | 说明 |
|---|---|---|
| 设备管理 | `src/views/production/equipment/index.vue` | 设备档案 + 维修(handleMaintenance)；字段：设备编码/名称/状态/车间 |
| 工装模具档案 | `src/views/production/tooling/index.vue` | 网框/刀模等工艺装备档案；新增/编辑/删除/导入/导出；编号规则可配置 |

## 4.7 操作记录 / 成本核算 / 生产报表

| 菜单 | 页面 | 说明 |
|---|---|---|
| 操作记录 | `src/views/production/production-operation/index.vue` | production_operation_record 全量（START/PAUSE/COMPLETE/QUALITY/ISSUE…） |
| 成本核算 | `src/views/production/cost/index.vue` | 材料/人工/机器成本汇总 |
| 生产报表 | `src/views/production/report/index.vue` | 生产统计报表 |

## 4.8 完工入库（生产 → 库存衔接）

```
工单完成(8) → POST /inventory/inbound/create-from-production/{workOrderId}
  校验：工单必须已完成（未完工禁止入库，DEV-936/053）
  生成完工入库单（默认取启用仓库）
  成品物料(F类型)入库 → 库存入账 → 扣减工单 finishedQuantity
```

---

_阶段四完。下一步：阶段五（库存管理）——物料/仓库/库存/出入库/盘点/调拨/预警。_


---

# 阶段五：库存管理

> 代码：`jjx-server/src/main/java/com/jjx/inventory/` + `jjx-web/src/views/inventory/`
> 核心链路：**物料档案 → 仓库/库位 → 出入库（单据级）→ 库存/预警 → 盘点/调拨**
> ⭐ 本阶段重点：**出入库单据流 + 缺料预警驱动采购**（预警见阶段三 3.0）
> ⚠️ 概念约定：库存里只有**物料**（R原料/F成品/S半成品），**产品永远不入库**；物料档案的 product_id = 该物料被哪个产品专用

## 5.1 物料管理（物料主数据）

**菜单**：库存管理 → 材料管理（物料列表） | **页面**：`src/views/inventory/material/index.vue` + `detail.vue` + `category.vue`

### 物料类型（MaterialEnum）
```
R 原材料 | S 半成品 | F 成品（完工入库的落点）
```

### 操作
| 按钮 | 动作 |
|---|---|
| 新增/编辑 | 表单弹窗 |
| 删除 | 物理删除 |
| 导入 | 模板下载 + 导入 + 失败结果（handleDownloadTemplate / handleImport / handleDownloadFail） |
| 查看 | 详情页（库存/价格/关联） |

### 关键字段
`materialCode/Name`、`materialType`(R/S/F)、`categoryId`、`spec`、`unit`、`safetyStock/maxStock`、`productId`(专用物料，非等同产品)、`price`、`status`

> 成品物料(F)在产品**发布**时自动创建（转量产链路），完工入库入的是 F 物料。

## 5.2 仓库 / 库位

**菜单**：库存管理 → 仓库管理 | **页面**：`src/views/inventory/warehouse/index.vue` + `location.vue`

- 仓库：新增/编辑/删除 + **库位管理**(handleLocation/handleLocationManage)
- 字段：仓库编码/名称/类型/状态(启用1)、库位编码/名称
- 作用：出入库单的 warehouse_id 落点（销售/生产出入库默认取启用仓库）

## 5.3 库存列表

**菜单**：库存管理 → 库存列表 | **页面**：`src/views/inventory/stock/index.vue`

- 操作：查看详情(handleViewDetail) / 调整(handleAdjust，盘盈盘亏) / 导出 / 刷新 / 筛选(showFilter)
- 视图：按物料汇总库存（quantity/availableQuantity/锁定量），产品维度通过专用 F 物料间接看

## 5.4 入库管理

**菜单**：库存管理 → 入库管理 | **页面**：`src/views/inventory/inbound/index.vue` + `create.vue`

### 入库类型
```
purchase(采购入库) / production(生产完工入库) / return(退货入库) / transfer(调拨入库) / adjust(盘盈入库)
```

### 状态机
```
draft(草稿) → pending(待审批) → approved(已批准) → confirmed(已确认) → closed(已关闭)
                    ↘ rejected(已驳回)                    ↘ cancelled(已取消)
```

### 操作按钮
| 按钮 | 动作 |
|---|---|
| 新建 | handleCreate（含来源：采购单/生产工单 createFromProduction） |
| 提交 | handleSubmit → 待审批 |
| 审批 | handleApprove → 已批准 |
| 取消 | handleCancel |
| 打印 / PDF / 导出 | handlePrint / handleExportPdf / handleExport |
| 详情 | detailDialogVisible |

### 生产完工入库链路
```
工单完成(8) → createFromProduction（校验工单已完成，DEV-936）
  → 成品物料(F)入库 → 库存入账 → 扣减工单 finishedQuantity
```

## 5.5 出库管理（含销售发货、生产领料）

**菜单**：库存管理 → 出库管理 | **页面**：`src/views/inventory/outbound/index.vue` + `create.vue`

### 出库类型
```
production(生产领料) / sales(销售出库) / return(退货出库) / scrap(报废出库) / transfer(调拨出库) / adjust(盘亏出库)
```

### 状态机
```
draft(草稿) → confirmed(已确认) → closed(已关闭)  ↘ cancelled(已取消)
```

### 操作按钮
| 按钮 | 动作 |
|---|---|
| 新建 | handleCreate（含来源：销售订单 createFromSales / 生产工单） |
| 确认 | handleConfirm → 扣库存 |
| 打印 / PDF / 导出 | handlePrint / handleExportPdf / handleExport |
| 流水 | showTrace |

### 销售发货链路（阶段一 1.5 状态7"发货"触发）
```
订单发货(shipOrder) → createFromSales（自动建销售出库单，取默认启用仓库，DEV-932）
  → 成品F物料出库扣库存 → 订单 7→8 已发货
```

## 5.6 盘点管理

**菜单**：库存管理 → 盘点管理 | **页面**：`src/views/inventory/stocktake/index.vue`

### 盘点类型
```
full(全盘) / partial(抽盘) / cycle(循环盘点)
```

### 状态机
```
draft(草稿) → processing(盘点中) → closed(已关闭) ↘ cancelled(已取消)
差异行：pending(待处理) → processed(已处理) / skipped(已跳过)
```

### 操作按钮
| 按钮 | 动作 |
|---|---|
| 创建 | handleCreate → handleCreateSubmit |
| 开始盘点 | handleStart |
| 录入盘点数 | handleInput → handleInputSubmit |
| 确认结果 | handleConfirmResult |
| 差异处理 | handleProcessDiff（盘盈→入库/盘亏→出库） |
| 打印 | handlePrint |

## 5.7 调拨管理

**菜单**：库存管理 → 调拨管理 | **页面**：`src/views/inventory/transfer/index.vue`

### 调拨类型
```
normal(普通) / urgent(紧急)
```

### 状态机
```
draft(草稿) → approved(已批准) → out_confirm(已出库) → in_confirm(已入库) → closed(已关闭)
                                                  ↘ cancelled(已取消)
```

### 操作按钮
| 按钮 | 动作 |
|---|---|
| 创建 | handleCreate → handleCreateSubmit（addMaterialRow 明细） |
| 审批 | handleApprove |
| 出库确认 | handleConfirmOut |
| 入库确认 | handleConfirmIn |
| 取消 | handleCancel |

## 5.8 预警管理（衔接采购）

**菜单**：库存管理 → 预警管理 | **页面**：`src/views/inventory/alert/index.vue`
**详见阶段三 3.0**：订单缺料(order_shortage)/安全库存(safe_stock)/最高库存(max_stock)/保质期(expiry)/呆滞料(obsolete)
状态：0 未处理 / 1 已读 / 2 已处理（终态）

---

_阶段五完。下一步：阶段六（系统管理/日志）——用户/角色/菜单/部门/字典/事件配置/配置/文件 + 日志三件套 + 任务看板。_


---

# 阶段六：系统管理 / 日志 / 看板

> 代码：`jjx-server/src/main/java/com/jjx/system/` + `com/jjx/event/` + `com/jjx/notification/` + `jjx-web/src/views/system/` + `src/views/log/` + `src/views/kanban/`
> 职责：**权限底座 + 事件驱动（任务/通知）+ 配置 + 日志审计 + 任务看板**
> ⭐ 本阶段重点：**事件配置（驱动任务/通知）+ 任务看板**（全系统联动中枢）

## 6.1 用户 / 角色 / 菜单 / 部门（权限底座）

### 用户管理
**菜单**：系统管理 → 用户管理 | **页面**：`src/views/system/user/index.vue`
- 操作：新增/编辑/删除/批量删除/重置密码(handleResetPwd)/分配角色(handleAssignRole)
- 字段：账号、姓名、部门、角色、状态、手机/邮箱

### 角色管理
**菜单**：系统管理 → 角色管理 | **页面**：`src/views/system/role/index.vue`
- 操作：新增/编辑/删除/分配用户(handleAuthUser)/**菜单按钮授权**(handleAuthMenuButton → menuButtonDialog → saveMenuButtonPermissions)
- 权限模型：角色 → 菜单 + 按钮权限（perms 如 sales:order:view），前端 v-hasPermi 控制按钮显隐

### 菜单管理
**菜单**：系统管理 → 菜单管理 | **页面**：`src/views/system/menu/index.vue`
- 菜单树：M目录/C菜单/F按钮，path/component/perms/icon/order
- 新增菜单后需给角色授权才可见

### 部门管理
**菜单**：系统管理 → 部门管理 | **页面**：`src/views/system/dept/index.vue`
- 树形组织；生产班组复用部门（派工 team_id 指向部门）

## 6.2 字典 / 系统配置 / 单据模板 / 文件管理

| 菜单 | 页面 | 说明 |
|---|---|---|
| 字典管理 | `src/views/system/dict/index.vue` | 字典类型 + 字典项（启用/停用/批量删），前端 useDict 加载 |
| 系统配置 | `src/views/system/config/index.vue` | 配置分组(activeGroup) + 键值保存；如 pdf_template 公司抬头、tooling_no_rule 编号规则 |
| 单据模板配置 | `src/views/system/pdfConfig/index.vue` | 打印模板配置 |
| 文件管理 | `src/views/system/file/index.vue` | 附件存储管理：删除/恢复/永久删除 + 后台任务(runDaily/runCheck/runMigrate/runCleanExpired) |

## 6.3 事件配置（⭐ 任务/通知中枢）

**菜单**：系统管理 → 事件配置 | **页面**：`src/views/system/eventConfig/index.vue`
**实现**：`LocalEventPublisher`（@Event 注解 → 查 sys_event_config → 发通知 + 创任务）

### 事件配置字段
`eventCode`(如 sample.converted)、`eventName`、`bizModule`(sales/purchase...)、`eventType`(notification/task/both)、`kanbanModule`(dev/office/production)、`priority`、`targetRole`(JSON 角色数组)、`title/content`(模板，{bizId} 占位符)、`isEnabled`、`excludeTrigger`(排除触发者)

### 触发机制
```
业务方法 @Event(value="sample.converted") → LocalEventPublisher.fire
  → 查 sys_event_config（enabled=1）
  → notification 类型：按 targetRole 展开用户 → 逐人建 sys_notification
  → task 类型：按角色建 sys_task（看板任务，kanban_module 分组）
```

### 已配置事件示例（销售域）
`inquiry.converted` / `quotation.submitted/reviewed/sent/converted` / `sample.created/submitted/approved/ready/sent/confirmed/rejected_by_customer/converted` / `order.submitted/review_started/approved/rejected/resubmitted/cancelled/sent_to_customer`

## 6.4 任务看板（开发/办公/生产）

**菜单**：任务看板 | **页面**：`src/views/kanban/index.vue`
**配置**：`src/views/kanban/config/board.ts`

### 模板（switchTemplate）
```
production（生产管理看板：工单/派工/工序）
office（办公任务：事件生成的通知类任务）
dev（开发任务：dev-YYYYMMDD-NNN 编码，sys_task kanban_module='dev'）
```

### 任务状态流转
```
0待处理 → 1进行中 → 2待审核 → 10已完成
                ↘ 3阻塞(blocked)
```

### 任务字段（sys_task）
`taskCode`、`taskType`(design/review/production/sample)、`kanbanModule`、`title/description`、`bizType/bizId`、`assigneeId/Name`、`assignRole`、`priority`(urgent/high/normal/low)、`sourceEvent/sourceId`、`status`、`deadline`

> 开发任务流程：看板登记 → 认领 → 完成置 2 待审核 → 审核后拖 10 已完成。

## 6.5 日志三件套（审计）

**菜单**：日志管理

| 菜单 | 页面 | 说明 |
|---|---|---|
| 操作日志 | `src/views/log/operation/index.vue` | sys_oper_log：操作人/模块/URL/参数/IP/状态/耗时；链路追踪（traceId）查询 |
| 登录日志 | `src/views/log/login/index.vue` | sys_login_log：登录/登出、IP、结果 |
| 异常日志 | `src/views/log/exception/index.vue` | sys_error_log：异常堆栈 |

> 操作日志即"流水"数据源：`@Log` 注解切面写入（OperLogAspect），按 traceId 组织链路（TraceTimeline）。`detail` 支持通用文本/JSON；引用 `#attachmentIds` 时由切面组装附件详情。

## 6.6 通知中心

- 数据表：`sys_notification`（标题/内容/类型/接收人/优先级/bizType）
- 来源：事件配置（见 6.3），业务触发点自动生成
- 前端：通知铃铛/通知列表（`src/views/notification/`）

---

_阶段六完。全文完成：总览 + 阶段一~六（销售→产品工程→采购→生产→库存→系统）。_
