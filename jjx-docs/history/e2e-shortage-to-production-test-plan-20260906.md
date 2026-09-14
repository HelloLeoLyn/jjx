# E2E 全链路测试计划：生产缺料 → 采购 → 收货 → 检验 → 入库 → 生产领料 → 生产

> 日期：2026-09-06
> 依据：代码实测（controller/menu/router/sys_role 权限点）+ jjx-docs/flows 流程文档 + DB 现状快照
> 关联：昨日收货 400 bug（b62a308 修复）回归；1504/1505/1514/1506/1508 等近期改动
> 原则：本计划所有「角色 / 路径 / 权限点 / 状态」均来自代码与 DB 核实；未核实处显式标注【待确认】，不猜测

---

## 一、目标与范围

### 1.1 目标
1. 打通一条真实业务链：**缺料（预警/建议）→ 采购下单审批 → 收货 → 来料检验 → 入库过账 → 生产领料 → 工单开工/执行/报工**
2. 每个环节验证三件事：页面操作成功、数据落库正确、跨模块联动生效（符合 docs/test/测试标准.md）
3. 回归昨日 Bug：收货请求体契约 = `{itemId, receivedQuantity}`，多余字段（inspectionResult 等）必须 400
4. 全程截图留证，产出可下载报告

### 1.2 范围与边界
- 覆盖：库存预警 → 采购计划工作台 → 采购订单 → 采购收货 → 自动入库 → 入库确认（内嵌来料检验）→ 生产领料（含发料确认）→ 工单开工 → 工序执行 → 报工
- 不覆盖：销售下单/齐套、工程 BOM 编辑、发票付款、调拨盘点、成本核算（列为后续阶段）
- 【待确认】你描述中的「检测物流」：系统内采购链路**没有独立"物流"模块**（"物流"仅存在于销售送货 delivery/tracking）。本计划将中间段按系统现状映射为「收货 → 来料检验（入库确认内嵌，1505）→ 入库过账」。若你指别的环节，请指出，我改映射。

---

## 二、链路总览（业务环节 → 系统模块映射）

| # | 业务环节 | 系统模块 | 主页面（URL/菜单） | 主要角色账号 |
|---|---|---|---|---|
| 0 | 缺料产生 | 库存预警 | /inventory/alert（库存管理>预警管理, 菜单27） | admin / warehouse_keeper(93) |
| 1 | 采购建议→生成订单 | 采购计划工作台 | /purchase/plan（采购管理>采购计划, 菜单245） | buyer_clerk(91) |
| 2 | 采购订单提交/审核 | 采购订单 | /purchase/order（菜单38） | buyer_clerk(91) 提交、buyer_reviewer(92) 审核 |
| 3 | 收货 | 采购订单收货弹窗 | /purchase/order 行操作 OrderReceiveDialog | 有 purchase:order:edit 者（buyer_clerk/91） |
| 4 | 自动入库单 | 入库管理（自动生成） | /inventory/inbound（菜单28/242） | 自动，无人工 |
| 5 | 来料检验+入库确认 | 入库确认（内嵌检验 1505） | /inventory/inbound 确认/检验弹窗 InboundInspectionDialog | 检验录入：quality_inspector(135)/inventory ops；复核：quality_manager(136) 或具备 inbound:approve |
| 6 | 生产领料 | 生产工单领料 + 出库发料 | PC：/production/order（菜单45）领料弹窗；发料：/inventory/outbound（菜单33）PickIssueDialog；移动端：/m/pick | 领料：prod 操作角色/移动端；发料确认：inventory:outbound:edit（warehouse_keeper/93） |
| 7 | 工单开工 | 生产工单 | /production/order 开工 | prod_manager(94)/admin |
| 8 | 工序执行 | 工序执行 | /production/execution（菜单48）；移动端 /m/order /m/report | 执行/报工：生产角色（操作工 104+ 等） |
| 9 | 报工审批 | 报工审批 | 移动端 /m/report-approvals；PC 报工查询/审批 | 负责人/管理角色 |

---

## 三、角色与账号矩阵（已核实 sys_user × sys_role × 权限点）

| 账号 | 用户ID | 角色（role_id） | 本链路关键权限点（实测） |
|---|---|---|---|
| admin | 1 | 超级管理员 | 全部（链路兜底执行者） |
| buyer_clerk | 91 | PURCHASE 业务操作(26) | purchase:order:add/edit/delete/view、purchase:plan:view/confirm、purchase:receipt:add/edit/view |
| buyer_reviewer | 92 | PURCHASE 审核员(27) | purchase:order:approve、purchase:plan:confirm |
| warehouse_keeper | 93 | INVENTORY 业务操作(23) | inventory:inbound:add/create/edit/view、outbound:add/edit/view、alert:view/edit、stock:view、material:* |
| quality_inspector | 135 | QUALITY 来料检验员(33) | quality:judge/report/view、inventory:inbound:view、purchase:receipt:view |
| quality_manager | 136 | QUALITY 品质主管(34) | quality:* + **inventory:inbound:approve**（复核过账人） |
| prod_manager | 94 | PRODUCTION 全权限(28)+派工主管 | production:order:*、execution:*、work-report:approve、**inventory:outbound:add** |
| print/punch/assembly_mgr | 95-97 | PRODUCTION 业务操作(29)+派工主管 | 同上（分车间） |
| print/punch/assembly_op1… | 104-121 | PRODUCTION 操作工(32) | operation-execution:edit/view、work-report:add/cancel/view（移动端报工） |

> ⚠️ **密码待确认**：以上非 admin 账号密码未知（我不改密码库）。执行方式二选一：① 你提供演示账号密码；② 全链路用 admin 跑通，角色权限用「切账号看按钮可见性」抽测（需密码）。

---

## 四、页面路径矩阵（前端路由/菜单实测）

| 页面 | 菜单路径 | 组件 |
|---|---|---|
| 预警管理 | /inventory/alert | views/inventory/alert/index.vue |
| 采购计划工作台 | /purchase/plan | views/purchase/plan/index.vue（含 print 子页 /purchase/plan/print） |
| 采购订单 | /purchase/order | views/purchase/order/index.vue（+print.vue） |
| 采购收货（独立收货单体系） | /purchase/receipt（菜单180） | views/purchase/receipt/index.vue |
| 入库管理 | /inventory/inbound | views/inventory/inbound/index.vue（含 InboundInspectionDialog 组件） |
| 出库管理 | /inventory/outbound | views/inventory/outbound/index.vue（+PickIssueDialog） |
| 库存列表 | /inventory/stock | views/inventory/stock/index.vue |
| 生产订单 | /production/order | views/production/order/index.vue（PickPreviewDialog 等组件） |
| 工序执行 | /production/execution | views/production/execution/index.vue |
| 质检管理/检验判定 | /production/quality | views/production/quality/index.vue |
| 移动端-生产领料 | /m/pick | views/mobile/pick.vue |
| 移动端-工单任务/报工/质检 | /m/order /m/report /m/quality /m/report-approvals | views/mobile/* |

> 移动端路由守卫：/m/* 需登录，未登录跳 /m/login（router 实测）。

---

## 五、数据现状（DB 快照 2026-09-06）

| 数据 | 现状 | 用途 |
|---|---|---|
| purchase_order | 1 张：PO-1788614769255（罗杰斯泡棉 supplier_id=54，receipt_status=1 部分收货，明细 item1/2 各收 100/2000） | 收货回归用（方案一文档已覆盖 A/B） |
| inventory_alert_log | 4 条（order_shortage，status 2/3） | 缺料环节可用/再造 |
| engineering_bom / _item | 1 / 2 | 领料需求来源 |
| engineering_routing / _item | 1 / 5 | 工单工序来源 |
| production_order | 4 张（PLAN PL2609050004 → WORK_ORDER WO-PL2609050004-01 等，sales_order SO2609050007，product JST001MOOO） | 生产段主数据 |
| production_operation_execution | 9 | 执行/报工段 |
| production_work_report | 5 | 报工段 |
| inventory_inbound_order | 2 | 入库段 |
| inventory_outbound_order | 0 | 领料/发料需新建 |
| inventory_stock / _item | 3 / 3 | 库存断言基准 |

> 结论：现库已具备一套「SO→PLAN→WO→执行→报工」演示链 + 可收货采购单 + 预警记录，适合直接做 E2E 主场景；领料出库与"缺料→采购计划→新订单"段需动态造数（见第七节）。

---

## 六、关键接口与状态机（代码实测）

### 6.1 缺料/预警（InventoryAlertController，前缀 /inventory/alert）
- GET /list、/unprocessed、/purchase-suggestions、/dashboard
- POST /execute-check、/check-safe-stock、/check-order-shortage/{orderId}、/check-global-shortage
- 预警类型（AlertTypeEnum）：safe_stock / max_stock / expiry / obsolete / order_shortage
- 职责链（2026-08-18 前端注释）：仓库只上报（status 0→1 mark-read），采购侧在工作台生成采购单后回写处理状态

### 6.2 采购计划工作台 → 生成采购订单
- 前端按钮：加载建议(loadSuggestions → getPlanSuggestions) / 添加物料 / 生成采购订单(confirmPlan)
- 后端：GET /purchase/order/plan-suggestions（purchase:plan:view）——安全库存 + 订单缺料建议
- 旧的 create-plan-from-suggestions / create-plan-from-alerts 已 @Deprecated（2026-08-18 弃用，前端无调用）

### 6.3 采购订单（PurchaseOrderController，前缀 /purchase/order）
- 审批状态：1草稿 → 3待审批(submit) → 4已批准 / 5已拒绝；可取消 2（ApprovalStatusEnum）
- 收货状态：0未收 → 1部分 → 2完成（ReceiptStatusEnum）
- POST /{orderId}/receive（purchase:order:edit，批量，DTO 仅 items[{itemId, receivedQuantity}]）→ 服务内自动调 inboundService.createInboundRecordFromPurchase(orderId) 生成入库单（1504）
- ⚠️ 昨日 Bug 回归点：请求体只允许 itemId + receivedQuantity；带 inspectionResult/inspectionRemark → 400

### 6.4 入库确认 + 来料检验（InventoryInboundController，前缀 /inventory/inbound）
- 状态机：草稿 → 提交审批(PENDING) → 审批通过(APPROVED) → 确认入库(COMPLETED，加库存)；驳回 REJECTED；取消 CANCELLED
- POST /submit-approve/{inboundId}（权限：inventory:inbound:edit **或** quality:inspector，OR）——检验员逐行录入+判定提交，body 为 InboundInspectionSubmitDTO（inspectionResult/inspectionRemark/items[sampled/qualified/rejected/rejectReason]）
- POST /approve/{inboundId}（inventory:inbound:approve）——主管复核过账；/reject 拒收留痕
- POST /confirm/{inboundId}（inventory:inbound:edit）——确认入库（加库存路径之一，前端"确认入库"按钮）
- 前端 canInspect = hasAnyPermission(['inventory:inbound:edit','quality:inspector'])（实测）
- 结论：**采购收货后检验在"入库确认"环节**（1505），收货弹窗本身已无检验字段

### 6.5 生产领料/发料（InventoryOutboundController，前缀 /inventory/outbound）
- 出库类型 OutboundTypeEnum：production(生产领料) / sales / return / scrap / transfer / adjust
- GET /pick-preview/{workOrderId}、/pick-remaining/{workOrderId}（inventory:outbound:view）
- POST /create-production-pick/{workOrderId}、/create-from-production/{workOrderId}（inventory:outbound:add）→ 生成生产领料出库单
- POST /confirm/{outboundId}（inventory:outbound:edit）→ 发料确认扣库存；/approve（outbound:approve）
- 移动端 /m/pick：扫码/输入工单号(getProductionOrderByCode) → 显示剩余可领量 → 提交领料（调 outboundApi）

### 6.6 工单开工 → 执行 → 报工
- ProductionOrder：order_status 0草稿/1待排产/2已排产/3生产中/4完成/5取消/6暂停/7关闭；material_status 0未领/1部分/2已领（1514 开工门槛：material_status=0 禁止开工，先领料后开工）
- 开工：POST /production/order startOrder（production:order:edit 类）
- 工序执行：ProductionOperationExecutionController —— POST 建执行、PUT /{id}/start、/pause、/quality-check、/complete
- 报工：WorkReportController POST /（work-report:add）、POST /{id}/approve|reject|cancel（approve 权限 production:work-report:approve，仅负责人/管理角色，1513）

---

## 七、E2E 用例设计

> 记号：前置 P、步骤 S、断言 A（操作✅ / 数据✅ / 联动✅）；截图统一存 tests/screenshots/e2e-shortage-*

### 主场景：E2E-ALL 全链路（一条龙，admin 兜底执行）

**P**：admin 登录；记录 DB 基线（inventory_stock/inventory_stock_item 各物料数量、alert_log、purchase/inbound/outbound 计数）

1. **缺料造数**（库存预警）：选 1 个原料（如 order1 明细对应物料 material_id=2）将库存扣至低于安全库存/需求 → 调 POST /inventory/alert/execute-check 或 check-safe-stock → GET /inventory/alert/list 断言出现该物料预警
2. **采购计划工作台**：/purchase/plan → 点「加载建议」→ 断言建议行含该物料 → 选供应商（罗杰斯泡棉 supplier_id=54）→ 「生成采购订单」
3. **采购订单**：/purchase/order → 断言新单出现（状态草稿）→ 提交(submit → 3 待审批) → 审核通过(→ 4 已批准)
4. **收货**：打开该单 → 收货弹窗（OrderReceiveDialog）→ 断言无「检验结果/检验备注」控件 → 明细填本次收货 → 提交 → 成功提示
   - **A（回归）**：抓包/断言请求体 JSON 仅含 items[{itemId,receivedQuantity}]；构造带 inspectionResult 的请求 → 400
5. **自动入库**：断言 /inventory/inbound 列表出现新入库单（来源=采购订单，单号 PO-…，明细=本次收货物料/数量）
6. **入库确认+来料检验**：入库管理 → 该单「确认入库/检验」→ InboundInspectionDialog 逐行录入（合格数量=收货量）→ 判定 PASS 提交（submit-approve）→ 复核过账（approve，需 quality_manager 或 admin）
   - **A**：inventory_stock 对应物料 +数量；inventory_transaction 增流水；入库单状态 COMPLETED；purchase_order_item.received_quantity 同步
   - 负向（可选）：再造一批判定 FAIL → 断言拒收留痕、不加库存
7. **生产领料**：/production/order 选 WO（WO-PL2609050004-01 等，material_status=0 未领）→ 领料弹窗（PickPreviewDialog，BOM buy 类明细）→ 填本次领料 → 提交 → 出库管理 /inventory/outbound 出现 production 类型领料单（PICK-…）→ 确认发料（PickIssueDialog confirm）
   - **A**：stock 扣减 + outbound 流水 + 工单 material_status 0→1/2
   - 移动端抽查（可选）：/m/pick 输工单号 → 剩余可领量正确 → 提交领料
8. **工单开工**：/production/order → 开工（material_status≥1 才允许，1514）→ order_status 2→3 生产中
9. **工序执行/报工**：/production/execution 或移动端 → 执行 start → 报工提交（合格数）→ 审批（负责人）→ 断言 work_report 状态、工单 completed/remaining 更新
10. **收尾**：全链路 DB 断言汇总 + 截图打包 + 报告

### 分环节用例（抽取重点，编号 TC-xx）

| TC | 环节 | 关键断言 |
|---|---|---|
| TC-01 | 预警生成 | alert_log 新增 order_shortage/safe_stock，类型/物料正确 |
| TC-02 | 计划建议 | plan-suggestions 含缺料物料与建议量=缺口 |
| TC-03 | 订单审批流 | 草稿→待审批→已批准；驳回分支 5 可改重提 |
| TC-04 | 收货契约（Bug 回归） | 弹窗无检验控件；请求体仅两字段；旧字段 400 |
| TC-05 | 自动入库 | receive 后 inbound_order 自动生成（1504），来源正确 |
| TC-06 | 来料检验 PASS | submit-approve+approve 后加库存、状态 COMPLETED |
| TC-07 | 来料检验 FAIL | 拒收留痕、不加库存、可复检重提（1505） |
| TC-08 | 领料单生成 | create-production-pick → outbound production 单，明细=BOM buy 类 |
| TC-09 | 发料确认 | confirm 后 FIFO 扣库存、流水、工单 material_status 更新 |
| TC-10 | 开工门槛 | material_status=0 禁开工；部分领料(1)允许（1514） |
| TC-11 | 执行+报工闭环 | execution complete → work_report → 审批 → 工单数量回写（1517） |
| TC-12 | 角色可见性抽测【待确认密码】 | 用 buyer_clerk/buyer_reviewer/warehouse_keeper/quality_inspector 登录，验证各自菜单/按钮按权限点出现（v-hasPermi 实测） |

### 数据准备与清理
- 主链路尽量**复用现有演示链**（SO2609050007 → PL2609050004 → WO…，bom 1/routing 1）减少污染；采购段用 order1 或新建小单
- 每次执行前对 6.1 涉及表做快照（mysqldump 或 SELECT 存 json），跑完可回滚
- 新增测试单建议命名前缀 TEST-，便于清理

---

## 八、执行方式与产物

- 基建：/mnt/d/openclaw-workspace/tests（playwright.config.js：headless chromium、baseURL http://localhost:3000、超时 60s）；或按 erp-browser-test skill 连 Windows Chrome CDP（用户可见操作）
- 新增 spec：tests/purchase-e2e.spec.js / tests/production-e2e.spec.js / tests/chain-shortage-e2e.spec.js；接口断言可加 python/curl 脚本
- 截图：tests/screenshots/e2e-shortage-*.png；每步关键点 ss()
- 产物：测试报告 md（UTF-8 BOM，MEDIA 发送），含逐 TC 通过/失败、DB 前后值、截图引用
- 服务：后端 :8080、前端 :3000 需在线（当前在线）

---

## 九、风险与【待确认】清单（不猜测，逐项等你拍板）

1. **「检测物流」映射**：系统采购侧无独立物流模块（物流只在销售送货）。本计划按「收货→来料检验(入库确认)→过账」执行——对不对？还是你要加销售送货段？
2. **角色账号密码**：buyer_clerk/buyer_reviewer/warehouse_keeper/quality_inspector/quality_manager 等密码未知——提供密码，或授权我用 admin 全链路 + 权限点静态断言代替？
3. **入库确认执行人**：1505 是检验员录入+主管复核，但 role 24(INVENTORY 审核员) 无绑定用户；复核过账实际由 quality_manager(136, 有 inbound:approve) 还是 admin 执行？【建议按你实际分工定】
4. **是否真扣库存**：E2E 会真实增减库存与流水（测试物料）。允许在测试物料/测试单上真实过账，跑完回滚吗？
5. **采购审批**：order1 当前 approval_status=3 与"待审批"语义存在出入（已有 approval_time/approver），执行时以代码枚举与页面行为实测为准——OK？
6. **执行范围**：先跑主场景 E2E-ALL 还是先分环节 TC-01~04（采购段，承接昨日方案）？

---

## 附：参考文件
- 采购测试方案：jjx-docs/analysis/purchase-receive-test-plan-20260906.md
- 流程文档：jjx-docs/flows/{purchase,production,inventory}-flow-analysis.md、production-inventory-purchase-detail.md、order-to-production-target-flow.md
- 测试标准：/mnt/d/openclaw-workspace/docs/test/测试标准.md
