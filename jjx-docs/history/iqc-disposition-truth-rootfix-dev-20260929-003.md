# IQC 处置真源收敛：关批判据 / 隔离状态派生 / 报废审批回写 核查与根治方案（dev-20260929-003）

> 日期：2026-09-29 ｜ 任务码：dev-20260929-003（P1，登记人 hermes）
> 类型：**核查报告 + 根治方案**（未改代码、未改数据；只读核查）
> 实测对象：`IN260929005`（PO260929001，行1 RM001572 50 / 行2 RM001592 63）
> 用户 2026-09-29 拍板口径：① 隔离单状态**删列 + 派生**（不加"处置中"过渡）；② 让步件**并入原批次**，不新建批次，只在文档写死口径
> 2026-09-29 追加拍板：③ `dev-20260928-010` **并入本卡**（010 置 status=4 核销，已交付 `a6191c71` 保留）；④ 024 §2.2/§7 决策1 **以本次为准**（列表+独立子页 → 单页上列表下明细）；⑤ 让步单行判定字段口径按本文 §4.6 定稿
> 关联：`quality-issues-disposition-proposal-20260928.md`（总处置建议，M1~M5）｜`quality-taskcards-review-20260928.md`（任务卡 dev-20260928-010~016 评审）｜`design/iqc-pages-industry-alignment-dev-20260924-024.md`（页面结构；其 §2.2/§7 决策1 已由本方案 §6 取代，用户 2026-09-29 确认）

---

## 1. 结论先行

`IN260929005` 的**账是对的**（数量、库存、流水、PO 收货量全部对得上，没有多入/少入），问题集中在**状态与口径**：

```text
同一「已处置」事实 → 3 套计数（lot / iqc_batch / ncr_action 派生）
                  → 4 种写入时机（申请即计 | 审批才计 | 审批被跳过 | 返工在途也算）
                  → 1 套判据（closeIfSettled 只比 2 个数字，不看在途单据）
                  → 结果：关批靠"数字凑巧"，状态靠"谁最后写谁生效"
```

- 6 条核查全部成立（§2），其中 2 条与既有卡 **dev-20260928-010** 同源，4 条为本次新增发现。
- 根治不是补丁：**关闭判据改为"待处置=0 且在途=0"**，**删掉 2 处第二真源计数**，**隔离状态改派生并删 4 个终态枚举**（§4）。修完 ②④ 两条会自然消失，不需要"IQC 特判 + 审批时补累加"这类过渡分支。

---

## 2. 核查结论（6 条，代码 + 数据库双向取证）

实测快照（只读账号 jjx_ro，2026-09-29）：

| 对象 | 实测值 |
|---|---|
| lot1 QL260929004 | lot 50 / pass 40 / fail 10 / **stored 45** / **disposed 10** / status **CLOSED** |
| lot2 QL260929005 | lot 63 / pass 60 / fail 3 / stored 60 / **disposed 0** / status **JUDGED** |
| quarantine 1（RM001572） | quantity 10 / **remaining 0** / status **RELEASED** |
| quarantine 2（RM001592） | quantity 3 / **remaining 0** / status **PENDING** |
| 处置单 | IQD260929005 RELEASE 5 COMPLETED ｜ IQD260929006 REWORK 2 **CREATED** ｜ IQD260929007 RETURN 3 COMPLETED ｜ IQD260929008 SCRAP 3 **PENDING_APPROVAL** |
| quality_ncr_action | 1 CONCESSION 5 DONE ｜ 2 REWORK 2 **PROCESSING** ｜ 3 RETURN 3 DONE ｜ 4 SCRAP 3 PENDING_APPROVAL |
| NCR | NCR260929003（lot1）10/10 CLOSED ｜ NCR260929004（lot2）3/**0** PENDING |
| iqc_batch | b1 rejected 10 / **disposed 10** / DISPOSED ｜ b2 rejected 3 / **disposed 0** / FAILED |

### 1️⃣ 关批过早（系统性）—— 成立

- 现象：lot1 已 CLOSED，但返工单 `IQD260929006` 仍 `CREATED`、复检批未建。
- 证据：`closeIfSettled`（`QualityLotServiceImpl.java:702-710`）只判 `stored ≥ pass` 且 `disposed ≥ fail`，**不看是否还有未终结的处置单**；全仓库仅 3 处调用（`:667`、`:690`、定义处）。
- 根因：返工在**登记时**即计入已处置（`QualityNcrServiceImpl.java:487` + 工件状态 `PROCESSING`，`:490`，派生 SQL 认 `DONE/PROCESSING`，`QualityNcrActionMapper.java:19-24`），而 `dev-20260928-047` 把返工改成两段式（登记停 `CREATED` → `completeIqcRework` 才建复检子批，注释见 `InventoryInboundServiceImpl.java:529-530`）后，**关闭判据没有跟着加"无在途"条件**。
- 结论：这是 047 两段式改造的遗留，不是全新缺陷。

### 2️⃣ 报废审批通过后会卡 JUDGED（系统性，尚未触发）—— 成立

- 证据：IQC 报废登记走**提前 return 的待审批分支**（`QualityNcrServiceImpl.java:466-478`，所有累加都不做）；而审批分支对 IQC **跳过** `addDisposedQuantity`（`:779-781`），其注释理由"IQC 在登记时已同步写入"对报废不成立。
- 审批侧 `approveIqcScrap`（`InventoryInboundServiceImpl.java:810-857`）只更新 IQC 批次（`approveBatchScrap`），**从不碰 quality_lot，也不触发关批判定** → lot2 的 disposed 会永远停在 0 < fail 3。
- 与 dev-20260928-010 的"报废不同步 NCR"同源；但**修法不同**：010 的字面实现是"审批时补记"，本方案主张"改为派生后不需要任何补记"（见 §4.2）。

### 3️⃣ 隔离单状态被"最后写入者"覆盖 —— 成立（且比报告多 2 处）

- 证据（数据）：quarantine 1 = `RELEASED`，实际是 5 让步 + 2 返工 + 3 退货；quarantine 2 = `PENDING` 而 `remaining=0`。
- 证据（代码）：
  - 状态取自"本次动作"，且**排除 SCRAP/RELEASE**、只在剩余归零时才写：`InventoryInboundServiceImpl.java:505-511`、`:567-568`；
  - 让步单确认入库时**无条件覆盖**为 `RELEASED`：`completeIqcReleaseDisposition`（`:1128-1142`）；
  - 报废通过时只在"剩余=0 且状态仍为 PENDING"才置 `SCRAPPED`：`:843-846`（混合处置下不生效）；
  - 补一处：`inventory_iqc_batch.disposed_quantity` 在**申请时**累加（`:583`），而报废是**审批时**才累加（`approveBatchScrap:863`）→ 同一列两种时机。
- 根因：一张隔离记录只有一个状态字段，装不下"混合处置"这一事实。

### 4️⃣ 关闭判据用错口径（不是"双计"）—— 成立，表述需修正

- 数字没被多算：库存、流水、PO 全部自洽。
- 真问题：`stored(45)` 含让步放行件，`pass(40)` 只含合格判定件，`closeIfSettled` 拿 `stored ≥ pass` 判"合格量已全部入库" → **让步件可以替合格件凑数**，所以关不关是偶然（上一单 `IN260929003` 没凑够就没关）。
- 结论：这条与 1️⃣ 是同一判据的两个侧面，一起修。

### 5️⃣ 报废 remark 文案与数量矛盾 —— 成立

- 实测：`quality_ncr_action#4.result_remark = "待审批：报废 3 件 超过阈值 5 件，需品质主管审批（dev-20260924-005）"`（3 < 5）。
- 根因：只有一句文案（`QualityNcrServiceImpl.java:470-473`），但送审有两种触发——超阈值（`scrapGovernance && qty > threshold`）或强制送审（IQC 由 `syncIqcDisposition` 传 `forceScrapApproval=true`，`:405`）；IQC 走后者（`scrapGovernanceApplies` 对 IQC 返回 false，`:703-708`），阈值根本没参与。

### 6️⃣ 让步件批次号复用 —— 成立，属口径确认而非缺陷

- 实测：让步单 `IN260929006` 行 `batch_no = IN260929005-1`、`lot_id=1`、`iqc_batch_id=1`，库存把 5 件并入原批次 → RM001572 结存 45（40 合格 + 5 让步）。
- 判定：让步接收是"原批那几件货被批准放行"，不产生新货，**不新建批次是正确的**（用户已拍板维持）。
- 附带观察（待核，非结论）：该让步单行 `accepted_quantity=0` 而 `posted_quantity=5`、`qualified/rejected` 为 NULL；lot1 的 stored 由 `syncQualityLotStored`（`:2081-2127`）按未取消单据的 posted 净额回写，故 45 正确。该行字段语义是否要补齐，列入待定项。

---

## 3. 根因总纲（决定"能不能合在一起做"）

```text
计数（3 套）  quality_lot.disposed_quantity
              inventory_iqc_batch.disposed_quantity
              quality_ncr_action 派生（DONE+PROCESSING）
时机（4 种）  申请即计：让步 / 退货 / 返工
              审批才计：非 IQC 报废
              审批被跳过：IQC 报废（:779-781）
              在途也计：返工 PROCESSING
判据（1 套）  closeIfSettled：只看 lot 的两个数字
```

1️⃣2️⃣4️⃣ 是这一条根因的四个面；3️⃣ 是同一个"多来源拼状态"的毛病落在隔离表上；5️⃣6️⃣ 是文案与口径。

---

## 4. 根治方案（不留过渡、不留第二真源）

判据（三条，用于筛掉一切补丁式修法）：

1. 同一事实只允许一个真源；**派生值不存列**（仓库 AGENTS.md §13 对批次表已是此口径，IQC 侧未遵守）。
2. 状态不允许靠"比数字"猜，必须看**链路上是否还有未终结的单**。
3. 一次改到真源，不留过渡字段/兼容分支；用完即死的代码同步删除。

### 4.1 计费口径与关闭判据（治 1️⃣4️⃣）

- 唯一真源：`quality_ncr_action`，按状态分两档 —— **已终结 = DONE**、**在途 = PENDING_APPROVAL / PROCESSING**（`QualityNcrActionMapper.java:19-24` 的 SQL 需按此语义拆开）。
- `quality_lot.disposed_quantity`、`inventory_iqc_batch.disposed_quantity` 退出权威地位（降为可重算缓存或直接停用；最终形态与 `quality-issues-disposition-proposal-20260928.md` §4.2 一致）。
- `closeIfSettled` 判据改为：**待处置量 = 0 且无在途处置单 且 合格件入库完成**；不再用 `stored ≥ pass` 这种跨桶比较。
- 影响面：字段名对外不变（前端仍读 `disposedQuantity`，后端由"存"改"算"）；全仓库用量 后端 10 个文件 / 前端 8 个文件，均为展示，无写入口。

### 4.2 报废审批与 IQC 特判（治 2️⃣）

- 改造为派生后，报废登记 = 在途（不计），审批通过 = DONE（自动计入）→ **删除 `QualityNcrServiceImpl.java:779-781` 的 IQC 特判**，不再需要"审批时补累加"。
- `approveIqcScrap` 只需保证审批与关批判定在同一事务内被触发一次。

### 4.3 隔离单状态：删列 + 派生（治 3️⃣，用户已拍板）

- 删除那三处"谁最后写谁生效"的写入：`InventoryInboundServiceImpl.java:505-511` + `:567-568`、`:1128-1142`、`:843-846`。
- 删除 `IqcQuarantineStatusEnum` 的 4 个终态值（`RELEASED/RETURNED/REWORKED/SCRAPPED`）；前端仅 3 个文件引用，改动面小。
- 显示口径：**剩余量 > 0 → 待处置；剩余量 = 0 → 已处置**，构成从处置单列出（例："已处置 · 让步5 / 返工2 / 退货3"）。
- 列表筛选从"状态"改为"待处置 / 已处置"（按剩余量/派生），不再依赖该列。
- 注：`remaining_quantity` 本身也应最终降为可重算缓存（与 §4.1 同口径），届时"待处置"直接由动作表派生。

### 4.4 报废送审文案按原因分支（治 5️⃣）

- `:470-473` 拆两支：超阈值（打印阈值）｜强制送审（不出现阈值，改说明"IQC 报废按单送审"）。

### 4.5 让步件批次口径写死（治 6️⃣，用户已拍板）

> 让步接收件并入原批次，不新建批次；让步身份由处置单（`IQC_RELEASE` / `IQC_REWORK`）与让步入库单承载，追溯走 `lot_id` / `iqc_batch_id`。

> 不改代码，只在本文与后续 `modules/` 文档中固化，避免后人重复解读为缺陷。

### 4.6 让步行（IQC_RELEASE / IQC_REWORK）判定字段口径（2026-09-29 定稿）

- 规则：让步/返工放行入库行**不写判定三字段** —— `qualified_quantity` / `rejected_quantity` / `accepted_quantity` 一律 **NULL**（不再写 0）。该行的业务事实只有 `quantity`（本行放行量）与 `posted_quantity`（已过账量）；"这是放行、不是质量判定"由 `inbound_type` / `source_type` 表达。
- 理由：写 0 会被任何 SUM / 报表读成"合格 0、允收 0"的**假事实**（第二真源那一族）；写放行量又会造出 `accepted > pass` 的跨桶混口径（正是 4️⃣ 那个病）。NULL + 类型区分是唯一不产生假事实的表达。
- 同步门禁（写进巡检脚本，作为可自动发现项）：`inbound_type IN (IQC_RELEASE, IQC_REWORK)` 的行断言三字段为 NULL；`PURCHASE` 行断言 `qualified + rejected = quantity`。
- 实施前清点消费者（入库明细读侧集中在）：`InboundDetail.vue`、`IqcPostingDialog.vue`、`InboundInspectionDialog.vue`、`inbound/print.vue`、`iqc/detail.vue`、`IqcMaterialTable.vue`、`IqcReviewDialog.vue`、`types/inventory/inbound.ts`；后端仅 `InventoryInboundServiceImpl`（7 处）。需确认前端把 NULL 渲染为"—"而不是 0。

---

## 5. 与既有任务卡/文档的关系（避免重复开工）

| 既有 | 关系 | 处理 |
|---|---|---|
| `dev-20260928-010`（四套「已处置」账分叉 + 巡检脚本，status=2 待审核） | 与本方案 §4.1/§4.2 **同一根因**，白名单文件重叠（QualityNcrServiceImpl / InventoryInboundServiceImpl） | **已并入本卡**（用户 2026-09-29 拍板）：010 置 status=4（核销·被覆盖），其已交付 `a6191c71` 保留有效；本卡为唯一实施卡 |
| `dev-20260928-011`（报废审批前端入口） | 已实现于 `iqc-quarantine` 页 | 不重复；本卡只改后端口径 |
| `dev-20260928-016`（前端口径与术语） | 与本方案 §6 工作台整合重叠（列名/数量口径/命名） | §6 作为 016 的结构化延伸，实施时以一张卡做（避免同一批前端文件两卡同改） |
| `quality-issues-disposition-proposal-20260928.md`（M1~M5） | 本文 = 其 M1 的**细化 + 提前可执行部分**（关闭判据与真源） | 迁移路径（M2~M3 统一处置表）仍按该文推进，本文不与其冲突 |

---

## 6. 工作台整合（用户 2026-09-29 定：单页，上列表下明细）

- 形态与仓库既有的派工/工序执行同构（`design/.../dev-20260923-035`：`dispatch/index.vue:11` 注释"上工单、下派工（与工序执行页同构）"；`execution/index.vue:23-56` 上 `WorkOrderPanel` + 下 `TaskTreePanel`）。
- 目标：`/inventory/iqc` 一页 = 上批次列表（分页保留）+ 下明细工作区；`/inventory/iqc-detail/:id` 改重定向（旧链接不断：`purchase/order/index.vue:629`、`purchase/receipt/index.vue:235`、`iqc-quarantine/index.vue:421`）。
- 分区组件化（区块 = 组件 = 文案同名，术语表对齐 024 §4.3）：
  `IqcDocumentHeader` / `IqcMaterialTable`（已有）/ `DispositionPendingTable` / `DispositionOrderTable` / `BatchLineageDrawer` / `QualityHistoryTimeline`。
- 位置感与分页（照抄既有解）：明细区锁定批次后不分页；切批次用 `:key` 重建 + 常驻"当前批次"提示（`execution/index.vue:42`、`dispatch/index.vue:20-27`）。
- 动作分配规则：多行决策 → 行内区块；单行填写+确认 → 弹窗（检验录入已有 `MaterialChecksDialog` 先例）。**处置弹窗必须带完整上下文**（不合格原因、检验项、供应商/采购单号/检验批号、已处置/剩余、该品历史），否则重演"信息太少判不了"。
- 数据出口收敛：启用已存在但无人调用的 `getIqcWorkbench`（`jjx-web/src/api/inventory/inbound.ts:54`、后端 `InventoryInboundServiceImpl.java:265-271`）作为唯一读口；停用/删除瘦接口 `listQuarantine` 与 `IqcQuarantineDialog` 瘦表；禁 `listQuarantine` 直接返回主表实体。
- **与 024 的冲突（已确认，2026-09-29）**：024 §2.2/§7 决策1 原定"列表页 → 独立子页详情"，用户已确认**以本次为准**；024 文首与 §7 已加注指向本文件 §6，本方案据此实施。

---

## 7. 实施顺序与验收

1. **包 A（后端根治，本文 §4）** —— 一次做完 4.1~4.4，不留过渡；4.5 仅文档。
2. **包 C（工作台整合 + §6）** —— 吃包 A 的结果，前端只做一次。
3. 顺序理由：包 A 改的是"批能不能关 / 状态是什么"的真源，工作台所有状态与待办显示都依赖它；先做 C 会返工一次。

验收：

- 清库后跑 IQC 全链路：混合处置（让步/返工/退货/报废）过程中**批不能关**；返工完成、报废审批通过后才关；隔离列表显示"已处置 · 让步5/返工2/退货3"而不是单一终态词。
- `cd jjx-web && npm run validate`（含 `vue-tsc`、`check:status-enums`、`check:docs`）。
- `bash scripts/check-stock-summary.sh --strict`（五项全 0）、`check-quality-ledger.sh --strict`、`check-iqc-lineage.sh --strict`、`check-inbound-lot-integrity.sh --strict`。
- 存量数据：用户已明确**上线前均为测试数据，不考虑存量**（不核对、不重开历史批）；清库属需备份场景，按 AGENTS.md 由用户手工执行。

---

## 8. 拍板结果与遗留

已拍板（用户 2026-09-29）：

1. `dev-20260928-010` **并入本卡**：010 置 status=4（核销·被覆盖），已交付 `a6191c71` 保留；本卡为唯一实施卡。
2. 024 §2.2/§7 决策1 **以本次为准**（独立子页 → 单页上列表下明细）。
3. 让步单行判定字段：按 §4.6 定稿（NULL + 类型区分 + 聚合门禁）。

遗留：

4. 同 PO 的另一张入库单 `IN260929007`（RM001572 × 100，`lot_id=3`，`iqc_batch_id` 未建）在途，回归时一并覆盖。
5. 实施前消费者清点（§4.6 末）：确认前端 NULL 渲染与报表 SUM 过滤。

---

## 9. 附：本次核查的只读取证方式

```bash
mysql -h127.0.0.1 -u jjx_ro -pjjx_ro_2026 jjx_erp_db   # SELECT only
# 查质量批/台账/处置单
SELECT lot_id,lot_no,lot_quantity,pass_quantity,fail_quantity,stored_quantity,disposed_quantity,status FROM quality_lot WHERE lot_id IN (1,2);
SELECT action_id,ncr_id,action_type,quantity,status,result_remark FROM quality_ncr_action ORDER BY action_id;
SELECT quarantine_id,material_code,quantity,remaining_quantity,status FROM inventory_iqc_quarantine;
SELECT disposition_id,disposition_no,action,quantity,status FROM inventory_iqc_disposition_order;
```

代码取证位置（file:line）见 §2 各行括号内标注。
