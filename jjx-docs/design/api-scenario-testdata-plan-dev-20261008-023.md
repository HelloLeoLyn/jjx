# API 优先 · 全模块场景测试数据方案（dev-20261008-023）

> 性质：**设计方案**。只读盘点 + 设计；本次**不实现运行器、不调用写入型 API、不生成业务数据**。
> 依据：`jjx-docs/design/01-业务流程.md`（薄膜开关定制制造：先接单 → 产品定义(BOM/工艺) → 采购备料 → 生产执行 → 质检入库）。
> 目标库：**当前开发库**。测试数据带可识别前缀；**清库脚本执行后可整段重跑**（幂等）。

## 0 总则（口径）

- **顺序＝真实业务流程**（不是按菜单/模块字母序）。逐模块推进，每步先确认：
  1. 销售管理 → 2. 产品 / 工程 → 3. 采购 → 4. 生产 → 5. 质量 → 6. 库存。
  不碰**系统管理**模块（用户/角色/菜单/字典/参数）。人事 / 文档 / 业务 默认不动。
- **写入隔离（轻）与可重复（2026-10-08 口径修订）**：目标库是开发库、无真实业务数据。**不依赖 `db-clean-test-data`**。做法：
  1. **复用现有主数据**（客户/供应商等**不新建**）；本次造出的单据带 `[TST]` 备注标记，便于识别与按标记清理；
  2. **单据号一律走系统业务规则**（由后端号段生成，脚本**不自行编号**）；
  3. 运行器**自身幂等且可增量**：按“标记 + 业务键”检测，已存在则复用/跳过，不产生重复单；
  4. 提供**按标记清理**的入口（不靠全库清库脚本）。
- **数据规模目标（2026-10-08）**：**不是最小单链**——数据要**尽量多、覆盖大部分测试案例**（多客户 / 多产品 / 多状态 / 边界 / 异常分支），作为人工与回归测试的底料。每个模块交“**覆盖矩阵（状态 × 分支）**”，而不是一条 happy path。
- **每个模块交付**：可调写入型 API（造数据）/ 只读型 API（校验）/ 前置依赖 / 场景数据 / 停止点 / 清理与可重复 / 校验。路径以 `@RequestMapping` 现行代码为准（本文件为设计快照，实施前复核）。

---

## 模块 1 · 销售管理

真实子流程：**客户 → 询价 → 报价 →（审核）→ 样品单（打样→客户确认）→ 转量产 → 销售订单（提交审核→审核通过→客户确认）→ 生成生产计划 → 发货（待发货单→OQC→确认发货）→ 签收/拒收 → 收款 / 发票**。

### 1.1 写入型 API（按子流程顺序，用于造数据）

| 序 | 步骤 | 方法 + 路径 | 用途 |
|---|---|---|---|
| 1 | 客户 | `POST /sales/customers` | 建测试客户（编码可先 `GET /sales/customers/generate-code`） |
| 2 | 询价 | `POST /sales/inquiry` | 建询价单（客户 + 产品明细） |
| 3 | 询价 | `PUT /sales/inquiry` / `PUT /send/{id}` / `PUT /accept/{id}` | 改 / 发送 / 客户确认 |
| 4 | 转报价 | `POST /sales/inquiry/convert/{inquiryId}` | 询价 → 报价 |
| 5 | 报价审核 | `PUT /sales/quotation/submit-review/{id}` → `PUT /review/{id}` | 提交审核 → 审核通过 |
| 6 | 报价确认 | `PUT /sales/quotation/confirm/{id}` | 客户确认报价 |
| 7 | 转样品 | `POST /sales/sample-order/create-from-quotation/{quotationId}`（或 `POST /sales/sample-order`） | 报价 → 样品单 |
| 8 | 打样 | `PUT /sales/sample-order/submit-request/{id}` → `approve/{id}` → `start-engineering/{id}`（+打样工序/送样/客户确认） | 提交打样 → 审核 → 工程接单 → 打样推进 |
| 9 | 转量产 | `POST /sample/transfer/confirm` | 打样转标准（产品/BOM/工艺复核） |
| 10 | 销售订单 | `POST /sales/orders` | 建销售订单（客户 + 产品明细） |
| 11 | 订单审核 | `PUT /sales/orders/{id}/status/submissions` → `review` → `approval` | 提交审核 → 开始审核 → 审核通过 |
| 12 | 订单确认 | `PUT /sales/orders/{id}/confirm` | 客户确认订单 |
| 13 | 生成生产计划 | `PUT /sales/orders/{id}/status/generate-plan` | 订单 → 生产计划（**跨到生产模块，停止点的判据**） |
| 14 | 发货建单 | `PUT /sales/orders/{id}/status/ship` 或 `POST /sales/deliveries` | 创建待发货单 + OQC |
| 15 | 确认发货 | `POST /sales/deliveries/{deliveryId}/confirm-shipment`（OQC 放行后） | 安排仓库出库 |
| 16 | 签收 / 拒收 | `PUT /sales/deliveries/{id}/receive` / `POST /sales/deliveries/{id}/reject` | 客户签收 / 拒收回冲 |
| 17 | 收款 | `POST /sales/receipt` | 登记收款（回写订单付款状态） |
| 18 | 发票 | `POST /sales/invoice` | 登记发票 |
| (选) | 退货 | `POST /sales/returns` → `PUT /approve` → `PUT /receive` → `PUT /refund` | 退货链路 |

### 1.2 只读型 API（用于校验，勿调用写接口）

| 校验对象 | 方法 + 路径 |
|---|---|
| 客户 | `GET /sales/customers`、`GET /sales/customers/{id}` |
| 询价 | `GET /sales/inquiry/list`、`GET /sales/inquiry/{id}` |
| 报价 | `GET /sales/quotation/list`、`GET /sales/quotation/{id}`、`GET /sales/quotation/flow/{id}` |
| 样品单 | `GET /sales/sample-order/page`、`GET /sales/sample-order/{id}`、`GET /sales/sample-order/products/{id}`、`GET /sales/sample-order/convert-check/{id}` |
| 订单 | `GET /sales/orders`、`GET /sales/orders/{id}`、`GET /sales/order/review/records/{id}` |
| 发货/签收 | `GET /sales/deliveries`、`GET /sales/deliveries/{id}` |
| 收款 / 发票 | `GET /sales/receipt/page`、`GET /sales/invoice/page` |
| 对账 / 流水 | `GET /sales/reconciliation`、`GET /sales/logs/order/{id}` |

### 1.3 前置依赖

- **客户**：本模块第 1 步自建。
- **产品档案 / BOM / 工艺路线**：报价/订单/样品明细需要**产品编码**；属模块 2（产品/工程）。→ 销售造数前，需模块 2 已能产出测试产品；否则销售只能停在"报价"阶段。
- **账号（审核人/操作人）**：属系统模块，**不新建**，使用现有测试账号。
- **生产计划生成**会创建 `production_order`，**跨入模块 4**：这是模块 1 的天然停止点。

### 1.4 覆盖矩阵（状态 × 分支）＋ 停止点

停止点＝**A**：模块 1 造数到**销售订单“客户确认”为止**（不调 `generate-plan`，避免跨入生产模块）。发货 / 签收 / 收款链路按需再跑。

**覆盖矩阵（目标：覆盖大部分测试案例，不是单链）**

| 对象 | 覆盖的分支 / 状态 |
|---|---|
| 客户 | 正常、信用额度边界、停用（各若干） |
| 询价 | 草稿、已发送、客户已确认、客户已拒绝 |
| 报价 | 待审核、审核通过、审核驳回、客户已确认、客户已拒绝、已转订单 |
| 样品单 | 待打样、打样中、待送样、客户已确认、审核驳回、已转量产 |
| 销售订单 | 草稿、待审核、已审核、已确认、已驳回、已取消；明细 1 款 / 多款 |
| （可选）发货 | 待发货、OQC 未过、已发货、已签收、已拒收→退货 |
| （可选）收款 / 发票 | 未收、部分收、已收；有票 / 无票 |

建议量级：客户 3~5、产品 5~10、询价 3+、报价 3+、样品单 2+、订单 4+（覆盖上表各状态）。具体条数可调。

- **停止点 A（模块 1 边界·默认）**：订单“客户确认”为止（`confirm`）。**不**调用 `generate-plan`，避免跨入生产模块。
- **停止点 B（冒烟级一单到底·可选）**：继续 `generate-plan → ship → OQC → confirm-shipment → receive`；`generate-plan` 之后的生产侧数据由模块 4 负责。

### 1.5 清理与可重复（不依赖 db-clean-test-data）

- 标识：测试客户 / 单据均带 `[TST]` 标记，可按标记定位与清理。
- 幂等：按“标记 + 业务键”（如 `[TST]客户名 + 产品编码`）检测，已存在则复用 / 跳过，重复执行不产生重复单。
- 清理：提供**按 `[TST]` 标记删除**的入口；不依赖全库清库脚本。
- 可重复：在“已有部分测试数据”或“空库”两种起点下都能跑通（增量补齐缺失数据）。

### 1.6 校验（跑完怎么算对）

- 逐单据 `GET` 详情：状态与期望流转一致（询价已确认 / 报价已审核+客户已确认 / 样品已转量产 / 订单已确认）。
- 销售订单 `GET /sales/orders/{id}`：`total_quantity` 与明细一致、`order_status` 正确。
- 若走到发货：`GET /sales/deliveries/{id}` 状态、数量与订单一致；订单 `shipped_quantity` 与发货量一致。
- 收款/发票：订单付款状态回写正确。

---

## 模块 2 · 产品 / 工程

真实子流程：**产品分类 → 产品档案 → 标准工序 → 工艺路线(+明细) → BOM(+明细) → 菲林/图纸 → 资源(刀模/网版/网框)关联产品 → 产品发布**。
本模块产出"**已发布产品 + 已审批 BOM/工艺路线 + 当前版本**"，是**模块 1(销售) / 3(采购) / 4(生产) 的共同前置**。

### 2.1 写入型 API（按子流程顺序）

| 序 | 步骤 | 方法 + 路径 |
|---|---|---|
| 1 | 产品分类 | `POST` / `PUT` / `DELETE /product/category` |
| 2 | 产品档案 | `POST /product`（新增）、`PUT /product`（改）；`PUT /product/submit/{id}` → `approve/{id}` → `release/{id}`（提交/审核/发布）；`obsolete/{id}`、`cancel/{id}` |
| 3 | 标准工序 | `POST /engineering/standard-processes`、`PUT /{processId}`、`/{processId}/enable`、`/{processId}/disable`、`POST /import` |
| 4 | 工艺路线 | `POST /engineering/routings`、`PUT /{routingId}`、`/{routingId}/submit` → `approve` → `reject`、`/{routingId}/set-current`、`/{routingId}/copy` |
| 5 | 路线明细 | `POST /engineering/routing-items`、`/batch`、`PUT /reorder` |
| 6 | BOM | `POST /engineering/bom`、`PUT`、`/submit/{bomId}` → `approve` → `reject`、`/setDefault/{bomId}`、`/{bomId}/copy`、`/calculateCost/{bomId}` |
| 7 | 菲林 | `POST /engineering/films`、`/{filmId}/submit` → `approve` → `reject`、`/{filmId}/new-version`、`/{filmId}/set-current`、`/{filmId}/release` |
| 8 | 资源（刀模/网版/网框） | `POST /engineering/resources/dies`、`/screen-frames`、`/screen-plates`、`/import`；`PUT /engineering/resources/{type}/{id}/products`（关联产品） |
| 9 | 产品配置（快捷） | `POST /engineering/config/bom`、`/engineering/config/route` |
| (选) | 产品实例 | `POST /product/instance`（+ `/batch`）、`/startProduction`、`/completeProduction`、`/deliver` |

### 2.2 只读型 API（校验）

`GET /product`、`/product/{id}`、`/product/category`；`GET /engineering/standard-processes`；`GET /engineering/routings`、`/routings/{id}`、产品版本列表；`GET /engineering/bom`、`/bom/{id}`；`GET /engineering/films`；`GET /engineering/resources/...`（含 `by-product/{productId}`）。

### 2.3 前置依赖

- **产品分类**：产品需归属分类（本模块第 1 步自建）。
- **物料档案**：BOM 明细引用物料（`material_id`）。**物料主数据归属待模块 6 核对**（是库存管理下的物料档案，还是独立基础数据）——本模块 BOM 造数依赖它。
- 无其它强依赖；本模块是模块 1 / 3 / 4 的共同前置。

### 2.4 覆盖矩阵（状态 × 分支）＋ 停止点

| 对象 | 覆盖的分支 / 状态 |
|---|---|
| 产品 | 草稿、待审核、已审核、已发布、停产、取消；单/多规格 |
| 标准工序 | 启用、禁用；含印刷/凹凸等类别 |
| 工艺路线 | 草稿、待审、通过、驳回；多版本 + 当前版本 |
| BOM | 草稿、待审、通过、驳回；多版本 + 默认；含损耗率 / 替代料 |
| 菲林 | 各审批状态 + 多版本 + 当前版本 |
| 资源 | 刀模 / 网版 / 网框 关联产品（1↔n） |

建议量级：产品 5~10、每产品 ≥1 条 BOM + ≥1 条工艺路线、标准工序复用现有、菲林 / 刀模 / 网版 各若干。

**停止点**：**产品已发布 + BOM / 工艺路线已审批且设为当前版本**（够模块 1 报价/订单/样品、模块 4 生产使用）。

### 2.5 清理与可重复（同口径）

`[TST]` 标记（产品名 / 编码可识别）、按标记清理、幂等增量（按标记 + 产品编码检测，已存在复用/跳过）。

### 2.6 校验

- 产品 `GET`：`status=已发布`、`current_bom_id` / `current_route_id` 指向正确。
- BOM / 工艺路线 `GET`：明细与当前版本一致、默认版本正确。
- 资源关联 `GET .../by-product/{productId}`：返回正确的刀模/网版。

---

> 待办：模块 3（采购）、4（生产）、5（质量）、6（库存）逐个补；收尾统一补「断点恢复 / 验证报告 / 实施验收」。
