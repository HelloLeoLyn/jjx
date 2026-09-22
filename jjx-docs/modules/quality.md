# 质量管理 · 现行真相

> 状态：✅ 已实施 | 最后复核：2026-09-21

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

## 历史资料

需要追溯设计原因时再看 `jjx-docs/analysis/` 中的质量重构、FQC 闭环和采购到 IQC 测试报告；日常开发与验收以本文和现行迁移为准。
