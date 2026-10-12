# 打样工作台「复制打样方案」新增「成品方案」来源 —— 设计方案

> 任务码：dev-20261012-010 ｜ 日期：2026-10-12 ｜ 状态：设计稿（待评审，未实现）
> 范围：工程打样工作台（`jjx-web/src/views/engineering/sample-workbench/`）+ 只读带出接口
> 前置结论：`dev-20261011-044`「待拍板 B：打样是否继承已有 BOM/路线」→ 本轮拍为「要，但走复制方案带出，不自动落库」

---

## 1. 背景与问题

1）销售单发起打样**不继承**产品的 BOM/工艺路线，且这是设计缺失而非缺陷：
   A) `SampleOrderServiceImpl.createSample`（`jjx-server/src/main/java/com/jjx/sales/service/impl/SampleOrderServiceImpl.java:652-831`）只建样品单头 + 产品明细（:772、:795），全篇不读产品 BOM/路线。
   B) 打样工序计划由前端传入并在 `saveProcessPlan`（:1427-1517）覆盖式写入当前轮。
   C) 打样 BOM 由前端从**打样工序的 materials JSON** 聚合：`useSampleWorkbench.ts:1453-1491`。
   D) 工序选择数据源是全局标准工序库（`GET /engineering/standard-processes/enabled`，`api/product/routing.ts:133-136`）。
   E) sales 侧对 engineering BOM/路线**只有写**（资料转移 `:1848-1984`、`:2492-2653`）与就绪检查（`:3068`/`:3103`），没有"读回打样"。
2）库内实况：`product=3`、`engineering_bom=1`(5 条 item)、`engineering_routing=1`(11 条 item) 均为 `is_current=1`；`sales_sample_bom=0`、`sales_sample_process=0`。
3）口径（用户 2026-10-12 拍板）：**不自动落库**；复用打样台现有「📋 复制打样方案」弹窗，新增第三个来源 **「成品方案」**，带出可编辑参考行，追加到当前轮、保存后生效。

---

## 2. 目标 / 非目标

1）目标
   A) 打样台「复制打样方案」弹窗新增 tab「成品方案」，从该样品单产品的**当前已批准且现行** BOM + 工艺路线带出工序行（含参数、索引）与材料。
   B) 追加语义（不覆盖现有卡片），保存后生效；带出的行可自由编辑/删除。
   C) 带出可追溯：能看出"来源 = 成品方案 + 版本号"，且产品后续升版不自动回灌。
2）非目标
   A) 不改 `createSample` 的建单行为（不自动落库）。
   B) 不改资料转移（`transferMaterials`）逻辑。
   C) 不改标准工序库；不新增第二真源；不改 BOM/路线的版本与审批规则。

---

## 3. 现状机制（本方案复用的流水线）

1）弹窗与入口：`workbench.vue:378-500`（title「📋 复制打样方案」），tabs 现为「本单历史轮」/「其他样品单」；入口按钮在 `SampleInfoCard.vue:12`。
2）复制主流程 `useSampleWorkbench.ts:203-271` `confirmHistoryCopy()`：
   A) 取源工序列表 → 按 `processOrder` 排序（:210-214）
   B) 分流：`majorCategory === 'PRINT'` → 印刷表；其余 → 卡片（:220-221）
   C) ASSEMBLY 行按 `processOrder` 分组为卡片（:222-228）
   D) enrich：用 `allProcesses` 按 `stdProcessId` 回填 `processType/processCategory/icon`（:233-242）
   E) `makeCard(...)` / `makePrintRow(...)` 追加（:243-261）；状态重置为 0、`uid` 重生成
3）结论：**新增来源只需产出一个"打样工序行形状"的列表**，其余全部复用。

---

## 4. 方案

### 4.1 交互

1）弹窗新增第三个 tab「成品方案」（仅只读展示，不落库）：
   A) 展示：产品编码/名称、BOM 版本 + 审批状态、路线版本 + 审批状态、工序条数、材料条数。
   B) 单选该产品的成品方案（样品单强制一单一产品，`validateSingleSampleProduct` :833-839，故无需产品选择器）。
   C) 按钮「复制方案到当前轮」；不满足条件时**禁用 + 原因提示**（见 4.3）。
2）复制结果提示沿用现有文案风格："已带出 N 张卡片、M 道印刷工序（追加到现有后，保存后生效）"。
3）语义提示明确写："带出的是**可编辑参考行**，保存后生效；产品后续升版不会自动同步"。

### 4.2 字段传承对照表（核心，按 `jjx-docs/specs/data-inheritance-spec.md` §三 模板）

| 传承项 | 产品侧来源 | 打样侧目标 | 转换规则 | 状态 |
|---|---|---|---|---|
| 工序 | `engineering_routing_item.process_id` | `sales_sample_process.stdProcessId` | 直接映射（两边均指标准工序；映射后现有 enrich 逻辑可用） | 可直传 |
| 工序名 | `engineering_routing_item.process_name` | `processName` | 直接 | 可直传 |
| 一级大类 | `engineering_routing_item.major_category` | `majorCategory` | 直接（决定进印刷表还是卡片） | 可直传 |
| 工序顺序 | `engineering_routing_item.process_order` | `processOrder` | 直接（决定卡片分组） | 可直传 |
| 下标 | `engineering_routing_item.index_number` | `indexNumber` | 直接 | 可直传 |
| 定制参数 | `engineering_routing_item.custom_process_params` | `customProcessParams` | 直接（印刷色号/油墨/网板） | 可直传 |
| 说明 | `engineering_routing_item.description` / `work_instruction` | `processNote` | 拼接或二选一 | 需定口径 |
| 分组 | `routing_item.group_id/group_name/group_order` | `process_category` / `processOrder` | 打样按卡片分组，语义不同 | 需定口径 |
| 卡片备注 | `routing_item.remark` | `remark` | 追加来源标记（见 4.4） | 可直传 |
| 材料 | `engineering_bom_item`（`bom_id` 组织） | 工序 `materials` JSON | 按 `bom_item.process_id` 挂到对应工序后序列化 | 需适配 |
| 材料字段 | `material_name/specification/quantity/unit` | JSON `{name, spec, qty, unit}` | 键名必须与前端解析一致（`useSampleWorkbench.ts:1474-1481`、`SampleOrderServiceImpl:1893-1902`） | 可直传 |
| 材料编码 | `material_code` / `material_id` | JSON 新增 `code` | 打样侧现有 JSON 无 code | **需拍板**（见 4.5） |
| 损耗/尺寸 | `loss_rate / width_mm / length_mm / module_qty …` | — | 打样侧无对应列 | 不带，留人工 |
| 层结构 | `bom_item.layer` | `layer_name` | 两侧口径不同 | 不硬塞 |
| 版本 | `bom_version` / `routing_version` | 仅 remark 或只读来源列 | 快照语义，不参与计算 | 见 4.4 |

### 4.3 只带「已批准且现行」

1）过滤条件：`is_current = 1` **且** 审批已通过；两者缺一即禁用按钮。
2）禁止情形与提示：
   A) 产品无 BOM → "该产品无 BOM，不能带出"
   B) 产品无路线 → "该产品无工艺路线，不能带出"
   C) 仅有草稿版 → "成品方案尚未批准，不能带出"
3）判断口径与转量产就绪检查一致（`SampleOrderServiceImpl:3068` 查 BOM、`:3103` 查路线），避免两套标准。

### 4.4 追溯与快照

1）带出的每一行写来源标记，例如 `remark` 追加 `[成品方案 BOM V1.0 / ROUTING V1.0 @2026-10-12]`；卡片级备注同源。
2）产品后续升版**不回灌**已带出的打样（打样是当时快照），这与资料转移的版本化方向一致（资料转移才是把打样结果提升为新版本，`:1862-1877` / `:1977-1980`）。
3）不加标记则无法区分"带的/工程师排的/别的单复制的"——必须加。

### 4.5 材料编码口径（待拍板）

1）现状：`sales_sample_bom` 只有 `material_name`（无 code）；工序 `materials` JSON 亦无 code。
2）选项：
   A) **推荐**：工序列 `materials` JSON 增加 `code` 键（不动表结构，能被带出、能被资料转移读取）；
   B) 扩 `sales_sample_bom` 加 `material_code` 列（动表结构，需迁移）。
3）不解决即"带出来缺编码"，工程师需手补——必须在提示里说明，不能静默。

### 4.6 权限

1）成品 BOM/路线属工程数据，打样台只做**只读带出**；不因本功能授予打样台用户 BOM/路线编辑权。
2）推荐由后端出只读带出接口（下节），前端不直连 engineering 模块接口，避免权限扩散。

### 4.7 影响面（实现时的建议落点，本稿不实现）

1）后端：新增只读接口（建议 `GET /sales/sample-order/{orderId}/product-plan`），在服务端完成 4.2 的 join/映射/过滤，直接返回**打样工序行形状**的临时列表（不落库），并带 `sourceVersion` 标记。
   A) 复用现有 `SalesSampleProcess` 字段名，使前端 `confirmHistoryCopy` 的 makeCard/makePrintRow 可原样复用。
2）前端：
   A) `workbench.vue`：弹窗加第三 tab + 状态/禁用逻辑。
   B) `useSampleWorkbench.ts`：新增 `confirmProductPlanCopy()`，与 `confirmHistoryCopy` 共用分组/分流/追加代码（建议抽公共函数，避免第三份拷贝）。
3）数据：无表结构变更（若 4.5 选 B 则另立迁移）。

---

## 5. 待拍板项（含本稿默认建议）

| # | 待拍板 | 本稿建议 |
|---|---|---|
| 1 | 一个 tab 还是两个（只要路线 / 只要 BOM） | **一个 tab「成品方案」**，一次带出路线 + 挂在工序上的材料 |
| 2 | 带出是追加还是覆盖 | **追加**（与现有复制语义一致，避免误覆盖已排工序） |
| 3 | 材料编码怎么带 | 工序列 JSON 加 `code`（不动表结构） |
| 4 | 工序说明字段怎么带 | `description` 优先，`work_instruction` 并入备注 |
| 5 | 是否需要"只带已批准" | **必须**（4.3），无例外 |

---

## 6. 验收用例

1）产品有已批准现行 BOM+路线：打开弹窗 → 第三 tab 可见且可复制 → 复制后卡片数/印刷行数与源一致，且每行可编辑。
2）产品仅草稿版：按钮禁用 + 原因提示，不可复制。
3）产品无路线（仅有 BOM）：禁用 + 原因提示。
4）路线含 PRINT 工序：带出后印刷行进印刷表、其余进卡片（不串表）。
5）带出后不保存直接刷新：不落库（追加仅存在于前端编辑区）。
6）带出后保存：`saveProcessPlan` 落库，行数与内容正确；`materials` JSON 键名被前端 BOM 面板正确解析。
7）产品后续升版：已带出的打样内容不变（快照）。
8）追溯：带出的行能看出来源与版本号。

---

## 7. 风险

1）适配层字段口径差异（材料/层结构）最易**静默丢数据** → 第一版对无法对应的字段留空并在提示里明确说明。
2）PRINT 工序未按现有 `:220-221` 分流会塞错表。
3）与"自动继承"混淆：`dev-20261011-044` 的待拍板 B 已按本稿拍为"带出"，需在任务卡写明，避免日后有人按自动落库实现一遍。
4）一个 tab 内同时带路线+材料，若材料挂载（`bom_item.process_id`）在存量数据中为空，会退化为"只带工序" → 需在提示里说明。

---

## 8. 规范更新建议（`jjx-docs/specs/data-inheritance-spec.md`，本稿不改，待评审后执行）

1）§二 转换点清单新增一条：「订单产品 → 打样（成品方案带出，复制方案第三来源）」。
2）§三 按 4.2 的对照表登记（规范要求"新单据转换必须填写字段传承对照表"）。
3）§五 更正过期条目：现写"样品→量产不传承工艺路线"，但代码 `SampleOrderServiceImpl:1965-1984` 已建路线，应更新为"已传承（版本化 + source_sample_id）"。
4）新增约束条款："凡新增带出/继承入口，必须在 §二 登记 + §三 填表 + 补充验收用例"，防止再次出现"做了不登记、登记了不同步"。

---

## 9. 不做什么

1）不自动落库；不改资料转移；不改标准工序库与图标字典。
2）不动 BOM/路线的版本、审批与 `is_current` 规则。
3）不改 `sales_sample_bom` 表结构（除非 4.5 选 B）。
4）不实现本稿（本任务只交付设计稿）。

---

## 10. 后续

1）评审 §5 五个待拍板项 → 拍板后按 4.7 落点实现（另立实现任务）。
2）实现时同步执行 §8 的规范更新。
3）与 `dev-20261011-044` 的"待拍板 B"关联销账。
