# 来料检验（IQC）不合格原因口径：检验侧只登记原因 + 原因由检验项目派生 + 处置词表统一（dev-20260924-013）

> 任务：dev-20260924-013（P1）｜起草：大黄(OpenClaw)｜2026-09-24
> 状态：**方案已定（用户逐条拍板）；实施待排 —— 等 Hermes 当前任务收口后再动**
> 适用域：来料检验（IQC）→ 来料不合格处置 → NCR
> 上游口径：`dev-20260918-026`（IQC 归一落 `quality_lot`）、`dev-20260924-004`（原因 = 检验项目 + CR/MA/MI）
> 相关：`design/fqc-disposition-freeze-void-recalc-dev-20260923-022.md`（同域，勿混淆）

---

## 1. 背景（用户 2026-09-24 指出）

来料检验页（`/inventory/iqc`，「材料检验处理」表格）里那一栏列名 **「处置/原因」**，一笔账三个问题：

1. **两个职责挤在一个格子**：不合格原因 = 检验员的事实登记；处置方式 = 品质主管的决策（`quality:ncr:dispose`，2026-09-21 口径 B）。放一起，等于让检验员替主管拍处置。
2. **两套词表打架**：检验侧 `IqcDispositionEnum` **8 项** vs 处置端 `IqcQuarantineActionEnum` **4 项**（让步接收/退货/返工/报废）。检验端**没有"返工"这个词**（拆成"供应商来厂重工""内部挑选/返工"），让步接收两边码还不同（`CONCESSION` vs `RELEASE`），另有 5 个处置端根本不存在的值（含"待复检""待定/隔离"——那其实是**状态**不是处置方式）。
3. **原因是自由文本**，与 `-004` 已定的"**原因 = 检验项目 + CR/MA/MI**"口径不一致，无法统计。

---

## 2. 定案（用户逐条拍板，2026-09-24 11:59–12:09）

| # | 决定 |
|---|---|
| 1 | 检验侧**只登记不合格原因**，**不留处置**（处置统一在「来料不合格处置」页做） |
| 2 | 「不合格原因」与「处置建议」**必须分开**（不是一个格子/一列） |
| 3 | 原因**由检验项目派生**，**生成时点 = 审核通过时** |
| 4 | 派生串**带实测值**：`外观：CR 2（实测「有划痕」）；尺寸：MA 1` |
| 5 | 保留**可选「补充说明」**（兜底"不在检验项目里"的原因：包装破损/混料/发错料/资料缺失） |
| 6 | 超长（字段 `varchar(500)`）**截断 + 末尾标「等 N 项」** |
| 7 | 边界 **A**：判定不合格但**一个不合格项都没有**时，**必须有补充说明**，否则驳回提交 |
| 8 | 处置词表**统一**：**删掉 8 项枚举**，全系统只用处置端那 4 项 |

---

## 3. 现状证据（代码 / 数据）

**数据链路（已存在的"原因"通道，不用新开字段）**

```
检验提交  → inbound_item.reject_reason
          → syncIqcLot(..., defectReason = rejectReason, ...)  → quality_lot.defect_reason
审核判 fail → ncrService.syncFromLot(lot, ..., item.getRejectReason(), ...) → quality_ncr.defect_reason
隔离台账显示 → COALESCE(lot.defect_reason, lot.remark) AS defect_reason（列名「缺陷原因」）
```

| 证据 | 位置 |
|---|---|
| 检验侧处置下拉（8 项） | `jjx-web/src/views/inventory/iqc/index.vue:213-233` |
| 入库管理的检验弹窗**也有同一个下拉** | `jjx-web/src/views/inventory/inbound/components/InboundInspectionDialog.vue:170` |
| 检验侧"必须选处置方式"硬校验 | `InventoryInboundServiceImpl:1084` |
| 检验侧"不合格必须填原因"硬校验 | `InventoryInboundServiceImpl:1088-1089` |
| 处置端 4 项动作 | `jjx-web/src/enums/inventory/IqcQuarantineEnum.ts`（`IqcQuarantineActionEnum`） |
| 隔离台账读原因 | `InventoryIqcQuarantineMapper:22`（`COALESCE(NULLIF(lot.defect_reason,''), lot.remark)`） |
| 隔离台账「IQC处置建议」列 | `iqc-quarantine/index.vue:99-100` |
| 行规则唯一出处 | `views/inventory/iqc/iqcRowRules.ts` |

**数据现状（决定清理是否安全）**

- `inventory_inbound_item.disposition`：**21 行全为 NULL**
- `inventory_iqc_quarantine`：**0 行**
- 8 项里多出的 5 个值（`SUPPLIER_REWORK` / `INTERNAL_SORT` / `PARTIAL_ACCEPT` / `REINSPECT` / `HOLD`）：**代码中无任何引用**
- `quality_lot_item`（检验项目明细）为 **IQC / FQC / OQC 三类共用**，且已有 `result` + `cr/ma/mi_quantity` + `actual_value`

→ 结论：**清理安全；且派生所需的原始数据全部已在库，无需新增字段。**

---

## 4. 改动清单

### 4.1 前端

| # | 文件 | 改动 |
|---|---|---|
| 1 | `views/inventory/iqc/index.vue` | 列改名「处置/原因」→ **「不合格原因」**；**去掉编辑态处置下拉**；原因改为**只读展示**（派生串 + 补充）；加 `show-overflow-tooltip` |
| 2 | `views/inventory/iqc/components/MaterialChecksDialog.vue` | 加 **「补充说明（可选）」** 输入；不做处置 |
| 3 | `views/inventory/inbound/components/InboundInspectionDialog.vue` | **同一处处置下拉一并去掉**（原因同样走派生） |
| 4 | `views/inventory/iqc/iqcRowRules.ts` | 去掉处置联动（仅保留"接收数量 = 合格数量"）；校验改为：判定不合格 → **派生非空 或 补充说明非空**（边界 A） |
| 5 | `views/inventory/iqc-quarantine/index.vue` | **删除「IQC处置建议」列** + 相关 `dispositionLabel`（处置页状态列已显示实际结果） |
| 6 | `enums/inventory/InboundEnum.ts` | **删除 `IqcDispositionEnum`**；其余引用处改用 `IqcQuarantineActionEnum`（`IqcPrintSelectDialog` / `IqcReviewDialog` / `IqcPostingDialog` 等只读展示同步） |

### 4.2 后端

| # | 文件 | 改动 |
|---|---|---|
| 7 | `InventoryInboundServiceImpl:1084` | 删掉「不合格必须选择处置方式」硬校验 |
| 8 | `InventoryInboundServiceImpl:1088` | 「原因必填」→ 改为"**审核通过时派生串非空**"（提交阶段只校验补充说明那一条，见边界 A） |
| 9 | `InventoryInboundServiceImpl`（审核通过 / `applyJudgement` 之后） | **生成 `lot.defect_reason`** = 派生串 + `补充：xxx`；超 500 截断并标「等 N 项」 |

### 4.3 明确不动

- `inventory_inbound_item.disposition`：**保留字段但不再写入**（历史数据/单据不动）
- **不新增字段**；**不动 `quality_lot_item`**（三类共用，动它 = 三个模块一起验收）
- 隔离台账 / NCR / 检验报告：**自动受益**（读的就是 `lot.defect_reason`）

---

## 5. 派生规则（实施依据）

**取项**：`result = 不合格`（或 `cr/ma/mi_quantity > 0`）的检验项目。

**格式**（多项以「；」连接）：

```
外观：CR 2（实测「有划痕」）；尺寸：MA 1（实测「0.8mm」）；补充：外箱有压痕
```

- 缺实测值时省略括号部分
- 只有 MI 时写 `MI 1`；同时有多个等级写 `CR 2/MA 1/MI 3`

**补充说明**：用户手填，追加为 `；补充：xxx`；**仅当派生串为空时必填**（边界 A）。

**超长**：整体截断到 **500** 字，末尾追加 `等 N 项`（N = 未展开的不合格项数）。

**时点**：**审核通过时**生成（`applyJudgement` 之后），保证"提交 → 审核"之间检验项被改也不会不一致。

---

## 6. 验证步骤（实施后）

| # | 用例 | 期望 |
|---|---|---|
| 1 | 某材料判不合格、检测项目里 2 项不合格（CR 2 / MA 1，含实测值）→ 审核通过 | 隔离台账「缺陷原因」= `外观：CR 2（实测「x」）；尺寸：MA 1（实测「y」）`；NCR 缺陷原因同值 |
| 2 | 同上但**不填任何不合格项**、也不填补充说明 → 提交 | **被驳回**（边界 A） |
| 3 | 同上、无不合格项但填了补充说明「外箱压痕」→ 审核通过 | `defect_reason` = `补充：外箱压痕` |
| 4 | 不合格项很多致派生串超 500 | 截断 500 且末尾有 `等 N 项` |
| 5 | 检验页不再出现处置下拉；隔离台账无「IQC处置建议」列 | 页面无残留 |
| 6 | 合格批 | 不受影响（无原因、不进台账） |
| 7 | 处置页正常处置（让步接收/退货/返工/报废） | 4 项动作照旧；显示文案来自 `IqcQuarantineActionEnum` |

**自查**：`cd jjx-web && npm run validate`（含 `vue-tsc --noEmit`）；后端 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -o compile`。**不打包、不重启**（服务生命周期归用户）。

---

## 7. 待确认 / 风险

1. **实施排期**：等 Hermes 当前任务收口（用户 2026-09-24 12:09 指示）——其正在改 `QualityNcrServiceImpl` / `WorkReportActionServiceImpl`，同属质量域，避免并行改同一处。
2. **历史数据**：`disposition` 全 NULL，无迁移；`IqcDispositionEnum` 删除后若历史值出现，显示会退化为原文——现状无数据，可接受。
3. **口径外延**：FQC / OQC 的"不合格原因"是否也走同一派生？本方案只覆盖 IQC，**未包含**（若要推广需另开卡）。
4. **统计**：派生后原因可聚合（按不合格项统计），这是本次改造的副产品收益。

---

## 8. 一句话

**检验侧只登记事实（原因），原因是"算出来的"；处置只有一处（来料不合格处置页）、只有一套词表（4 项）。**
