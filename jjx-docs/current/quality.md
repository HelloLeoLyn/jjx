# 质量管理 · 现行真相

> 状态：✅已实施（dev-20260909-005 方案 / 011 实施 / 010 建域） | 本文是"现在是什么样"
> 最后复核：2026-09-10

## 一句话

质量管理是**独立一级模块**，统一承载 IQC 来料检验、IPQC 过程检验、FQC 完工检验与不合格品处置；页面 URL 沿用历史值（`/inventory/iqc`、`/production/quality`），只改了菜单归属。

## 入口（菜单 338 → /quality，M，icon CircleCheck）

| 菜单 | menu_id | 原 menu_id | path | perms |
|---|---|---|---|---|
| 来料检验 | 330 | 330 | /inventory/iqc | inventory:inbound:view |
| 生产质检 | 264 | 264 | /production/quality | production:quality:view |
| 不合格品处置 | 333 | 333 | /inventory/iqc-quarantine | inventory:inbound:view |
| 质检报告 | 265 | 265 | /production/quality/report | production:quality:report |

> 注意：菜单归属变了，**URL 没变**（保持书签与代码跳转有效）。父目录授权由子菜单现有角色反向补齐。

## 业务流程

```
采购订单 → 采购收货 → IQC → 合格入库
生产执行 → IPQC → 完工 → FQC → 成品入库 / 返工 / 报废
```

## 当前生效的规则

- 质检单据：`production_quality_inspection` + `production_quality_inspection_item`
  - `inspection_type`：IQC / IPQC / FQC / OQC
  - `result`：pending / pass / fail（枚举见 `enums/quality/InspectionEnum.ts`）
  - `review_status`：DRAFT / PENDING / APPROVED / REJECTED
- **FQC 闭环（dev-20260909-011，0 新表）**：
  - `remaining_fail_qty` 待处置不良余量：首次判定按不合格数量初始化，返工/报废处置时**原子递减**
  - 返工不新建业务表：`production_operation_execution.execution_type=NORMAL|REWORK` + `source_inspection_id` 指向来源 FQC
  - 返工执行完成后**自动创建关联上次检验的 FQC**；合格数量累计回写生产订单并按质检单幂等触发成品入库
- **完工门禁**：订单完工前检查全部 FQC —— 不得存在待检记录、不得有未处置不良余量、累计合格成品数须达计划数量
- 无不合格品台账表，不良余量即台账（靠 `remaining_fail_qty` 表达）

## 已知约束（踩过的坑，别重复）

- **IQC 提交必须逐条判定**：后端 `InventoryInboundServiceImpl.saveInspection` 要求每条可编辑明细的判定是 `PASS`/`FAIL`，未判定会被硬拒（`BusinessException`，**不落 sys_error_log**）。前端提交前会弹"还有 N 行未判定…是否继续"，点继续仍会被拒 —— 这是前后端契约不一致处，尚未收口。

## 关键代码

- 后端：`production/controller/QualityInspectionController`（`/production/quality/page`、`/{id}`、`/{id}/judge`、`/{id}/reinspect`、`/{id}/disposition`、`/inspection`、`/statistics`）、`QualityActionService(Impl)`、`QualityInspectionServiceImpl`；`inventory/InventoryInboundServiceImpl.saveInspection`（IQC 落库与判定）
- 前端：`views/inventory/iqc/**`（含 `components/MaterialChecksDialog.vue`）、`views/production/quality/**`、`api/production/quality.ts`、`enums/quality/InspectionEnum.ts`、`enums/inventory/InboundEnum.ts`

## 近期变更

- 迁移 `76_fqc_rework_close_loop.sql`（FQC 闭环；迁移前备份 `jjx_erp_db_backup_20260909-2347_before-fqc-close-loop.sql`）
- 迁移 `77_reorganize_inventory_quality_menus.sql` → `78_create_quality_domain_and_flatten_inventory_menu.sql`（建质量域；78 幂等可复跑；迁移前备份 `...20260910-0825_before-quality-menu-domain.sql`；空环境快照 `...20260910-0835_final-handoff.sql`）
- 注意：数据库需执行 75/77/78 后才与代码一致（2026-09-10 曾出现"代码到 78、库停在 74"）

## 历史（"当年为什么这样"才看）

`analysis/fqc-close-loop-dev-20260909-005.md`（方案定稿，含待拍板项）、`analysis/fqc-rework-close-loop-dev-20260909-011.md`（实施记录）、`analysis/purchase-to-iqc-test-report-20260907.md`（链路实测）、`analysis/inventory-menu-reorg-dev-20260909-010.md`（建域与职责边界）
