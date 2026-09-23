# 质量管理 · 现行真相

> 状态：✅ 已实施 | 最后复核：**2026-09-23**（补 2026-09-22~23 定案口径）

## 一句话

质量管理是独立一级模块，当前正式入口覆盖 IQC 来料检验、来料不合格品处置、FQC 成品检验、统一不良台账和抽样方案。来料页面 URL 沿用历史路径，菜单归属和权限已纳入质量域。

## 入口（质量管理 menu_id=338）

| 菜单 | menu_id | path | component | perms |
|---|---:|---|---|---|
| 来料检验 | 330 | `/inventory/iqc` | `views/inventory/iqc/index.vue` | `quality:lot:view` |
| 不合格品处置 | 333 | `/inventory/iqc-quarantine` | `views/inventory/iqc-quarantine/index.vue` | `quality:ncr:view` |
| 成品检验 | 377 | `/quality/lot/fqc` | `views/quality/lot/fqc-lot.vue` | `quality:lot:view` |
| 不良台账 | 378 | `/quality/ncr` | `views/quality/ncr/index.vue` | `quality:ncr:view` |
| 抽样方案 | 379 | `/quality/sampling-plan` | `views/quality/sampling-plan/index.vue` | `quality:plan:config` |
| CAPA 台账 | 391 | `/quality/capa` | `views/quality/capa/index.vue` | `quality:capa:view` |

迁移 129 已删除旧的“生产质检”(264)、“质检报告”(265) 和试运行的“来料检验（检验批）”(376) 及其按钮菜单。不要再把这些入口写入导航、文档或验收清单。

## 业务流程

```text
采购收货 → 来料检验 → 合格入库
                    └→ IQC 隔离台账 → 放行 / 退货 / 返工 / 报废

生产报工 → FQC 检验批 → 合格入库
                     └→ 不良台账 → 返工 / 让步接收 / 报废
```

## 当前生效规则

- `quality_lot` / `quality_lot_item` 是 IQC、IPQC、FQC 的统一检验批模型；类型使用 `IQC`、`IPQC`、`FQC`，状态和结果统一复用 `src/enums/quality/InspectionEnum.ts`。
- 正式来料检验仍由 `/inventory/iqc` 承载收货行检验与审核，并与 `quality_lot` 关联；来料不合格品进入 `inventory_iqc_quarantine`，在独立处置页闭环。
- FQC 页面使用 `/quality/lot/fqc`，按检验批展示待检、已检、合格、不良、已入库和已处置数量；复检以同批新版本表达。
- 不良台账 `/quality/ncr` 统一承载返工、让步接收、报废等处置；处置权限为 `quality:ncr:dispose`。
- **CAPA 台账** `/quality/capa`（表 `quality_capa`，号段 `CAPA+yyMMdd+3`，菜单 391）：四态 `待分析 → 措施执行中 → 待验证 → 已关闭`；已结案的不良单挂 CAPA 会自动回退 `DISPOSING`；**结案门禁：处置量覆盖不良量且无未关闭 CAPA 才能把不良单置 CLOSED** —— 三条结案路径（处置完成 / 复检更正同步台账 / 返工完成）共用同一判据（dev-20260923-046 收口；此前只有处置完成路径查 CAPA，另两条会绕过）。
- 来料检验员只负责查看和录入；检验判定、隔离处置由品质主管权限承担。权限职责以迁移 132 为准。
- IQC 提交必须逐条判定；前后端均应使用现有质量/库存枚举，不在页面内新建状态映射。

## 近期变更

- 迁移 117/122 建立检验批模型、质量菜单和按钮权限；迁移 127 保留原来料检验为正式入口。
- 迁移 129 删除旧生产质检、质检报告及试运行来料检验批入口，并把 377 收敛为“成品检验”。
- 迁移 132 将来料检验与不合格品处置权限纳入质量域，并分离检验员、品质主管职责。
- dev-20260921-022 修正不合格品处置页：处置建议、批次类型、处置单类型使用枚举中文展示；危险处置不再作为默认值；无处置权限时显示明确提示。
- dev-20260918-026 起，IQC 隔离、处置、返工和批次谱系通过 `lot_id` / `iqc_batch_id` 归一关联。

## 关键代码

- 后端：`quality/controller/QualityLotController`、`quality/service/impl/QualityLotServiceImpl`、`quality/service/impl/QualityNcrServiceImpl`、`inventory/controller/InventoryInboundController`、`inventory/service/impl/InventoryInboundServiceImpl`
- 前端：`views/inventory/iqc/`、`views/inventory/iqc-quarantine/`、`views/quality/lot/fqc-lot.vue`、`views/quality/ncr/`、`views/quality/sampling-plan/`
- 枚举：`src/enums/quality/InspectionEnum.ts`、`src/enums/inventory/IqcQuarantineEnum.ts`、`src/enums/inventory/IqcBatchEnum.ts`、`src/enums/inventory/IqcReworkEnum.ts`
- 迁移：`117_quality_lot_model.sql`、`122_quality_menu.sql`、`127_revert_iqc_to_legacy.sql`、`129_rename_and_remove_legacy_menus.sql`、`132_iqc_permission_separation.sql`

## 口径（2026-09-22 ~ 23 定案，勿再走回头路）

### 1. 成品入库口径 B：判定/复检只维护「应入数量」，一律不动库存

- FQC 判定、复检（换代）**只写检验批的应入数量与状态**，不调任何库存接口；
- 加/减都必须在库存侧由仓库点「**确认入库**」才过账（含复检减量的**冲减** `reducePostedStock`）；
- 反例（已废弃）：判定即自动调库存、复检直接改库存余额。

### 2. 单据粒度：一个检验批一张入库单

- 单号从属式 `<工单号>-FI<2位>`（如 `WO-PL260923001-01-FI03`）；
- 成品批次号 `BATCH-<检验批号>`；入库单/明细必须挂 `lot_id`（可追溯到检验批）；
- 红冲单为 `-R` 后缀；同批被多张未取消单据重复计账会被门禁拦（见 `scripts/check-inbound-lot-integrity.sh` ②）。

### 3. 复检换代 A：未过账作废、已过账红冲

- 原入库单**未过账** → 作废（`order_status=9`）；
- 已过账 → 生成**红冲单**（`-R`、负数量、待仓库确认），原单备注「已被 <红冲单号> 冲销」；
- 换代后自动**重算工单完工口径**（`syncFinishInbound`），不再出现「工单显示 200、有效合格只剩 100」。

### 4. 判定可合格上界（dev-20260923-021）

- 上界 = `批批量 − Σ(本链 SCRAP 且 DONE 且未回收) − Σ(让步接收未客户确认)`；**REWORK 视为可回收，不扣减**；
- 判定时合格量超上界 → **拒绝**（fail-closed）；上界计算异常 → 降级放行不阻塞（打 WARN）；
- 仅供预填/校验：`GET /quality/lot/{lotId}/judgement-guard`；前端判定弹窗按上界预填并拦截提交。

### 5. 不良处置固化 + 显式撤销流（dev-20260923-022）

- **失效批禁处置**：已被新版本取代的批、已作废批不允许再登记/执行处置；
- **随批作废**：复检换代时，该批上未完成的 NCR + 处置单一律置 `VOID` 并留痕；
- **撤销流**：`POST /quality/ncr/action/{actionId}/revoke?reason=`（权限点 `quality:ncr:revoke`，菜单 396）——原因必填、仅允许撤 `DONE`、**本期仅 SCRAP**；副作用 = 处置单→VOID + 台账/检验批已处置量回落 → **判定上界自动恢复**；
- **未做**：让步（CONCESSION）与返工（REWORK）的撤销需反向库存调整/返工冲销，按 SCRAP → CONCESSION → REWORK 分批推进。

### 6. 批关闭与重开

- 关闭判据：`stored ≥ pass` 且 `disposed ≥ fail` → `CLOSED`（`closeIfSettled` 在处置登记后 + `markOrderFinishedStored` 在入库过账后，双触发）；
- `CLOSED` 后仍要更正 → 走显式「**重开**」`reopenLot(lotId, reason)`（权限 `quality:lot:judge`，原因必填、必须是最新版本、CLOSED→JUDGED），再复检；
- **不再**用「直接改判定」绕过。

### 7. 数量守恒巡检门禁（dev-20260923-023）

- `scripts/check-inbound-lot-integrity.sh` 八查中 ④~⑧ 属质检口径：判定守恒（`pass+fail=inspected≤批量`）、不良台账守恒、有效批合格 ≤ 可判上限、工单完工 = 有效批合格累计、`stored ≤ pass`；
- 与 `check:stock:strict` / `check:doc-no` 一起挂在 `npm run validate`；
- **待议**：链级/件级数量守恒需「不良件序列号」级追溯（现模型为整批重判(差额)，链级聚合会重复计入）。

### 8. 单号

- 业务单号一律走 `sys_number_sequence`，**默认 3 位流水 + 溢出告警**（规则总表见 `design/doc-no-rules-dev-20260922-023.md`）；
- 巡检：`scripts/check-doc-no.sh`（规则键缺失 / 用量达 80% / 时间戳式编号回归），已接进 `npm run validate`。

> ⚠️ 生效状态：第 1~7 条中 021/022/023 的代码于 2026-09-23 入库，**需重启后端后生效**；口径本身即当前定案。

## 历史资料

需要追溯设计原因时再看 `jjx-docs/analysis/` 中的质量重构、FQC 闭环和采购到 IQC 测试报告；日常开发与验收以本文和现行迁移为准。
