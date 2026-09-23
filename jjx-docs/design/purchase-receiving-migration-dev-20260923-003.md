# 采购收货 —— 现状操作口径 + 「采购订单收货」迁移/复制可行性分析

> 任务码：`dev-20260923-003`　｜　日期：2026-09-23　｜　性质：**只读核查 + 方案建议（未改任何代码/数据）**
> 核查范围：`jjx-web/src/views/purchase/receipt/index.vue`、`jjx-web/src/views/purchase/order/**`、`jjx-server/.../purchase/**`、`jjx-server/.../inventory/service/impl/InventoryInboundServiceImpl.java`

---

## 0. 结论速览

1. **「采购收货」页面已经存在**（菜单 `采购管理 → 采购收货`，menu_id=180），但它现在是**薄壳**：只能"看进度 + 单条收货 + 检验"，且**检验按钮是坏的（404）**。
2. **采购订单页的「收货」**才是真正在用的入口（多明细一次收 + 票据图片），但它**权限标注错位**（前端 `purchase:receipt:add` / 后端 `purchase:order:edit`），且**上传的票据不入库**。
3. **两条入口的后端其实是同一套 service**（`receiveOrderItem` / `batchReceiveOrderItems` → 都调 `createInboundRecordFromPurchase` 生成入库单）→ **迁移/复制在技术上完全可行，属于前端重组 + 权限收口，不需要动数据模型。**
4. 推荐 **方案 A：抽公共组件 + 收货权限收口**（详见第 6 节），不建议"复制一份"（会变成双份维护）。
5. 顺带查出 **6 个真实缺陷**（第 7 节），其中「检验 404」「票据不入库」「权限错位」建议随本次一起修。

---

## 1. 现状：两条收货入口对比

| 维度 | ① 采购管理 → 采购收货（menu 180） | ② 采购订单页 → 收货（弹窗） |
|---|---|---|
| 页面 | `views/purchase/receipt/index.vue`（221 行） | `views/purchase/order/components/OrderReceiveDialog.vue`（279 行，1200px） |
| 列表内容 | **采购订单**列表（展开看明细行） | 订单列表页 + 选中一行点「收货」 |
| 列表接口 | `GET /purchase/receipt/list`（`purchase:receipt:view`） | `GET /purchase/order/page`（`purchase:order:view`） |
| 列表口径 | `receipt_status IN (0,1)` 且 `approval_status IN (3,4)`（未收/部分收 且 待审批/已批准），**无分页**，前端 `computed` 过滤 | 全量订单，有分页/筛选 |
| 收货动作 | 明细行内「收货」→ 弹窗**单条**填数量 | 弹窗**多明细一次收**（只提交 `本次收货 > 0` 的行） |
| 收货接口 | `PUT /purchase/receipt/confirm/{itemId}` | `POST /purchase/order/{orderId}/receive` |
| 接口权限 | `purchase:receipt:edit` ✅一致 | 后端 `purchase:order:edit`，前端按钮写 `purchase:receipt:add` ❌**错位** |
| 检验 | 明细行内「检验」→ `PUT /purchase/receipt/inspect/{itemId}` | 无 |
| 票据图片 | 无 | 有（picture-card 上传，JPG/PNG/GIF/BMP/WebP ≤10MB） |
| 后端落地 | `receiveOrderItem` → 更新明细已收/收货状态 → `createInboundRecordFromPurchase` → 事件 `purchase.item_received` | `batchReceiveOrderItems` → 同上批量 → `createInboundRecordFromPurchase` → 事件 `purchase.received`（手写 payload 带 orderNo/supplierName） |
| 菜单按钮权限 | add/edit/delete/export/import（181~185） | 借用订单页按钮 |

> 关键点：**两条路最终都落到 `PurchaseOrderServiceImpl#receiveOrderItem / batchReceiveOrderItems`**，二者末尾都调用 `inboundService.createInboundRecordFromPurchase(orderId)`（DEV-624，注释明示"收货≠入库：生成待仓库确认的入库单，仓库确认后才加库存"，业务 2026-08-11 定稿）。

---

## 2. 「采购收货」页现在怎么操作（逐步）

1. 登录 → 左侧 **采购管理 → 采购收货**（需要 `purchase:receipt:view`）。
2. 列表列出**待收货订单**（`receipt_status ∈ {0 未收货, 1 部分收货}` 且 `approval_status ∈ {3 待审批, 4 已批准}`），按 `expected_delivery_date` 升序。
   - 页面顶部的"订单号/供应商"是**前端过滤**（`computed filteredRows`），不是后端查询；**没有分页、没有日期/物料筛选**。
3. 点订单行**展开箭头** → 明细表：物料编码/名称/规格/单位、订购数量、已收数量、**收货状态**、**检验结果**，行尾操作列 `收货` / `检验`。
4. 点 **收货** → 弹窗（480px）：物料、订购数量、已收数量、**本次收货（必填）**、收货人、收货日期、备注 → 「确认收货」。
   - 实际提交：`PUT /purchase/receipt/confirm/{itemId}?receivedQuantity=&receiverName=&receiptDate=&remark=`。
   - ⚠️ 后端把 `receiptId` 当 **明细 id** 用（`orderItemMapper.selectById(receiptId)`），语义是"按明细收货"，不是"按收货单收货"。
5. 点 **检验** → 弹窗填检验结果（合格/不合格/待检）、检验人、检验日期、备注 → 「保存检验结果」。
   - ⚠️ **后端没有 `PUT /purchase/receipt/inspect/{id}` 这个端点**（见第 7 节缺陷①）→ 必然 404。
6. 收货完成后：**库存不会立刻增加**。系统自动生成一张 `PURCHASE` 来源的入库单（状态"待审批"，`inspection_result` 为空表示待检验），要再到 **库存管理 → 入库作业** 里「确认入库」才真正加库存。

---

## 3. 「采购订单」页收货怎么操作（逐步）

1. 打开 **采购管理 → 采购订单**，找到目标订单（`approval_status=4 已批准`（3 待审批也可收），`receipt_status ∈ {0,1}` 时行内出现绿色「收货」）。
   - 也可勾选一行后点右上角「收货」按钮。
2. 弹窗（1200px）「收货 - {订单号}」：
   - **收货明细**表：物料编码/名称/规格/单位、订购数量、已收数量、**本次收货**（每行一个输入框，默认 0）；
   - **票据图片**：picture-card 上传，可预览、可删除（上传即写磁盘临时目录，**不插库**）。
3. 填好要收的行 → 「确定」→ `POST /purchase/order/{orderId}/receive`，body `{items:[{itemId, receivedQuantity}]}`。
4. 后端逐项校验（数量 > 0、**累计不超过订单数量**，防超收）、更新明细收货状态、更新订单整体收货状态，然后**自动生成入库单**（同上，待仓库确认）。
5. 同样要再去 **库存管理 → 入库作业 → 确认入库** 才加库存。

---

## 4. 能力矩阵（"能不能迁移/复制"的事实基础）

| 能力 | 采购收货页 | 采购订单收货弹窗 | 迁移难度 |
|---|---|---|---|
| 多明细一次收 | ❌（单条） | ✅ | 低（复用组件） |
| 票据图片 | ❌ | ✅（但未入库） | 中（权限在 `purchase:invoice:*`） |
| 检验结果录入 | ⚠️按钮在但接口缺失 | ❌（死组件 `ReceiveDialog.vue` 里有下拉） | 中（需补端点） |
| 收货进度可视化 | ✅（按订单展开） | ❌ | — |
| 分页/筛选 | ❌（前端过滤） | ✅ | 低 |

---

## 5. 能不能迁移/复制？—— 能，但有 3 条路

### 方案 A（推荐）：抽公共组件 + 收货权限收口
- **前端**：把 `OrderReceiveDialog.vue` 提升为公共组件（如 `views/purchase/components/PurchaseReceiveDialog.vue`），**采购订单页与采购收货页共用同一个组件**（单来源，改一处两处生效）。
- **后端**：把"按订单批量收货"的**权限判定收口到收货域**——二选一：
  - A1：`POST /purchase/order/{orderId}/receive` 改 `@SaCheckPermission(value={"purchase:order:edit","purchase:receipt:add"}, mode=OR)`；
  - A2（更干净）：新增 `POST /purchase/receipt/confirm-batch`（`purchase:receipt:add`），内部委托 `batchReceiveOrderItems`，把旧端点标注为兼容入口。
- **票据**：上传/确认接口权限目前挂在 `purchase:invoice:*`，收货员通常没有 → 建议把"收货票据"独立成一套 `purchase:receipt:doc:*` 权限，或至少允许 `purchase:receipt:edit` 调用 batch-confirm。
- **收益**：两入口行为完全一致；收货权限自洽；不需要改数据模型。

### 方案 B：把收货弹窗"复制"到采购收货页
- 最省事（把一个组件 import 进去即可），但**前端出现两份收货实现**，日后改一处漏一处；且默认仍会撞上第 7 节的权限/票据问题。
- 只在"临时验证"场景可接受。

### 方案 C：采购订单页的「收货」按钮改成跳转
- 点「收货」→ 跳到 `采购收货` 页并带 `orderId` 自动打开收货弹窗。
- 好处：入口唯一、菜单语义清楚；坏处：**采购收货页当前能力（单条收货 + 检验 404）撑不起唯一入口**，必须先补 A 的能力，否则体验倒退。

> **我的建议**：A（含 A2 权限收口）+ 顺带修第 7 节的①③④；C 作为后续可选收敛。

---

## 6. 若走方案 A，需要处理的 6 个技术点

1. **权限**：收货写入必须是"收货域"权限（`purchase:receipt:*`），不能要求 `purchase:order:edit`（否则仓库/收货员干不了活）。
2. **票据归属**：收货票据与"采购发票"共用 `purchase_document` + `/purchase/invoice/*` 端点 → 要么独立权限，要么明确"收货上传票据"由 `purchase:invoice:edit` 兜底（并在 UI 上隐藏无权限的上传区）。
3. **检验**：`inspectionResult` 字段在 `purchase_order_item` 上；要"收货时录检验结果"，必须先补 `PUT /purchase/receipt/inspect/{itemId}`（或并入收货提交体）。
4. **入库单粒度**：`createInboundRecordFromPurchase` **每次调用生成一张新入库单** → 批量收货若逐条调用会生成 N 张（见缺陷⑤）→ 批量端点必须**只调一次**。
5. **双入口一致**：两处共用组件 + 共用 service；采购订单页保留入口但内部打开同一组件。
6. **菜单按钮**：`purchase:receipt:export`（后端返回"导出功能待实现"）、`purchase:receipt:import`（有端点但前端未接）——要么补齐、要么从菜单撤掉，避免"看得见点不动"。

---

## 7. 顺带查出的 6 个缺陷（只读核查，未修改）

| # | 缺陷 | 证据 | 影响 |
|---|---|---|---|
| ① | 采购收货页「检验」必然 404 | 前端 `api/purchase/receipt.ts` 调 `PUT /purchase/receipt/inspect/{id}`；`PurchaseReceiptController` 全部端点里**没有** `inspect`/`batch-inspect` | 检验功能完全不可用；`batchInspect` 同属死 API |
| ② | 收货弹窗「已收数量」与「本次收货」**绑同一字段** | `receipt/index.vue`：`<el-form-item label="已收数量">{{ confirmForm.receivedQuantity }}</el-form-item>` 与下方 `v-model="confirmForm.receivedQuantity"` 同源；且 `openConfirm` 用 `item.receivedQuantity` 预填 | 展示值会随输入变化；**默认预填"已收数量"，不改就提交会重复计数**（后端有超收校验兜底，但会报错或翻倍） |
| ③ | 收货按钮**权限前后端不一致** | 前端 `order/index.vue` 收货动作 `permission: 'purchase:receipt:add'`；后端 `PurchaseOrderController#receive` `@SaCheckPermission("purchase:order:edit")` | 只有收货权限的账号：**看得见按钮、点下去 403** |
| ④ | 收货上传的票据**不入库** | `OrderReceiveDialog.vue` 上传走 `POST /purchase/invoice/upload-temp/{orderId}`（只写磁盘）；同文件 `import { confirmReceiptDocuments }` **引入但 `handleSubmit` 从未调用**（全仓只有 `OrderPaymentDialog.vue:282` 调用） | 收货票据永远是临时文件，不进 `purchase_document`，后续查不到/会被清理 |
| ⑤ | 批量收货接口**逐条调用**导致多张入库单 | `PurchaseReceiptController#batchReceive` 循环 `receiveOrderItem`，而后者每次都调 `createInboundRecordFromPurchase` | 一次批量收货 3 个物料 → 3 张入库单（应为 1 张） |
| ⑥ | 死代码/死 API | `order/components/ReceiveDialog.vue`（225 行，含检验下拉）**无任何引用**；`api/purchase/receipt.ts` 里 `statistics/today/week/month/pending-inspection/inspected` 等页面均未使用 | 维护噪音；`ReceiveDialog` 里的"检验结果"字段说明原设计就打算在收货时录检验 |

> 另：`jjx-docs/design/purchase-flow-analysis.md`（2026-08-01）第 227~228 行写"**收货不自动建入库单**（TC-64）：需手动调 create-from-purchase"——**已被 DEV-624 推翻**，该文档口径过期，建议加一条过期提示或归入 `history/`。

---

## 8. 待拍板

1. **走哪个方案**：A（推荐，抽公共组件 + 权限收口）／ B（复制）／ C（跳转）？
2. **权限收口方式**：A1（旧端点改 OR 权限）还是 A2（新增 `purchase/receipt/confirm-batch`）？
3. **检验结果**是否并入收货弹窗（要补 `inspect` 端点）？还是维持独立的"检验"步骤（仍要补端点）？
4. **票据**归属：独立 `purchase:receipt:doc:*` 权限，还是沿用 `purchase:invoice:*`？
5. 缺陷 ①②④⑤ 是否**随本次一起修**（我建议一起，成本低、都是硬伤）？

拍板后我按方案落地（前端 + 后端 + 必要迁移），并按既定口径做 guard 备份与门禁验证。


---

## 9. 实施记录（2026-09-23 当日落地，任务码 `dev-20260923-004`）

**用户拍板**：①方案 A ②权限收口用 A2 ③检验走 IQC 引导（不新建 inspect 端点、不在收货弹窗录检验结论）④票据权限独立 ⑤缺陷①②④⑤一起修。

### 已改（后端 `jjx-server`）
| 改动 | 位置 |
|---|---|
| **A2 收货域主入口** `POST /purchase/receipt/confirm-batch?orderId=`（`purchase:receipt:add`，日志归"采购收货管理"） | `PurchaseReceiptController` |
| 修⑤：`POST /purchase/receipt/batch` 与 `/import` 改为**按 orderId 分组、每单只提交一次**（原逐条调 `receiveOrderItem`，每次都会建一张入库单） | `PurchaseReceiptController#groupReceiptRowsByOrder` |
| 新增收货域票据端点 `POST /doc/upload-temp/{orderId}`、`GET /doc/disk-files/{orderId}`、`DELETE /doc/temp-file`、`POST /doc/batch-confirm`；权限 `purchase:receipt:doc:view/add/delete`；`supplierId` 由服务端从订单解析（原来前端传 0）、`documentType=receipt` | `PurchaseReceiptController` + `IPurchaseDocumentService`（复用） |
| 新增只读 `GET /purchase/receipt/inbound-orders/{orderId}`（本订单收货生成的入库单，供提示与 IQC 跳转） | `PurchaseReceiptInboundMapper`（新增，只读） |
| 新增日志动作 `PUR_RECEIPT_CONFIRM_BATCH` / `PUR_RECEIPT_DOC_*` | `LogActions` |

### 已改（前端 `jjx-web`）
- **新增共享组件** `views/purchase/components/PurchaseReceiveDialog.vue`（由 `order/components/OrderReceiveDialog.vue` 提升；原文件移出仓库到 `~/jjx-backups/removed-inrepo_20260923/`）：
  - 修②：**已收数量只读**、本次收货独立字段（原来两者共用 `confirmForm.receivedQuantity`，且默认预填已收数量，直接提交会重复计数）；新增「未收」列与逐行 `:max=未收`；
  - 明细改走 `GET /purchase/receipt/order/{orderId}`（`purchase:receipt:view`），不再依赖订单域读权限；
  - 提交走 `POST /purchase/receipt/confirm-batch`（权限与收货按钮统一为 `purchase:receipt:add`，修③）；
  - 修④：确认收货后把磁盘临时票据 `batch-confirm` 落库（原来 `confirmReceiptDocuments` 引入未调用）；
  - 收货成功给出**入库单号**并（有 `quality:lot:view` 时）提供「去来料检验」。
- `views/purchase/receipt/index.vue`：删除旧的单条收货弹窗与检验弹窗；行级「收货」打开共享组件；「来料检验」按钮改为跳 `质量管理→来料检验`（带 `inboundNo`，按 `quality:lot:view` 显示）；**修⑦**（新发现）：`/purchase/receipt/list` 返回的订单不含明细（后端 VO `@Mapping(target="items", ignore=true)`）导致展开行永远是空的 → 明细改为展开时懒加载 `GET /purchase/receipt/order/{orderId}`，并展示该订单的收货入库单。
- `views/purchase/order/index.vue`：接入共享组件 + `@goto-iqc`。
- `api/purchase/receipt.ts`：新增 `confirmBatchReceive` / `getReceiptInboundOrders` / `uploadReceiptDocTemp` / `getReceiptDocDiskFiles` / `deleteReceiptDocTemp` / `confirmReceiptDocs`。

### 迁移
- `jjx-docs/sql/migrations/204_purchase_receipt_doc_perms.sql`（幂等）：新增权限点 `purchase:receipt:doc:view/add/delete`（menu_id 393/394/395，挂菜单 180，icon 显式 NULL）+ 按已有收货权限对齐授权（view→角色 1/25/26/27/33；add/delete→1/25/26）。

### 验证
- `mvn -o compile` ✅（JDK 21）｜`npx vue-tsc --noEmit` ✅
- 门禁：`check:status-enums` / `check:docs` / `check:collation:strict` / `check:stock:strict` 全绿
- **未打包、未重启** → 后端新端点（confirm-batch / doc/* / inbound-orders）**需重启后生效**；前端 vite 热更即生效
- 运行时回归清单见下方「回归清单」

### 新发现（第 7 节之外）
- **⑦ 采购收货列表不含明细**：`selectPendingReceiptOrders` → `PurchaseConverter.toVOList` 对 `items` 是 `@Mapping(ignore=true)` → 页面展开行永远为空，行内「收货/检验」实际点不到（本页主功能此前不可用）。本次改为展开时懒加载修复。

### 遗留（未动）
- 旧接口保留为兼容：`PUT /purchase/receipt/confirm/{itemId}`、`POST /purchase/receipt/batch`、订单域 `POST /purchase/order/{orderId}/receive`（`purchase:order:edit`）。
- 菜单按钮 `purchase:receipt:export`（后端"待实现"）、`purchase:receipt:import`（后端有、前端未接）、`/purchase/receipt/inspect` 类旧 API（`api/purchase/receipt.ts` 里 `inspectReceipt`/`batchInspect`/`statistics` 等）仍未清理；`order/components/ReceiveDialog.vue` 仍是死代码（本次未删）。
- `purchase_order_item.inspection_result` 历史字段不再写入（检验以 IQC 检验批为准）。

### 回归清单（需重启后端）
1. 采购收货页：列表 → 行「收货」→ 共享弹窗（已收只读、本次收货独立、逐行上限=未收）→ 提交；
2. 提交后：提示"已生成入库单 PO…-N（待来料检验）"；一次收多行**只生成一张**入库单；
3. 上传票据 → 提交后可在「采购发票/收货票据」域查到 `purchase_document`（`document_type='receipt'`，supplier_id 正确）；
4. 「来料检验」→ 跳 IQC 页并带出 `inboundNo`；无入库单时提示先收货；
5. 权限：仅 `purchase:receipt:add` 的账号可收货（不再需要 `purchase:order:edit`）；无 `purchase:receipt:doc:add` 时票据区只读；无 `quality:lot:view` 时看不到「来料检验」按钮；
6. 采购订单页收货按钮 → 同一弹窗，行为一致。
