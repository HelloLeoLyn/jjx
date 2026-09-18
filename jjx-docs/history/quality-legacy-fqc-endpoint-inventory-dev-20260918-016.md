# 旧质检（/production/quality）端点·页面·菜单 使用方盘点（dev-20260918-016）

> 目的：为「删除旧 FQC」提供**安全前提** —— 逐条确认每个端点/页面/菜单的使用者，判定 可删 / 需迁移 / IQC 在用。
> **一句话结论：旧 `/production/quality` 是 IQC 与 FQC 共用的一套接口，不能整体删除**；必须按调用方拆分后，再删 FQC 部分。
> 性质：历史快照（盘点记录），不保证反映当前实现。

## 1. 后端端点（QualityInspectionController @ /production/quality）

| 端点 | 主要用途 | 已知使用方 | 判定 |
|---|---|---|---|
| GET /page | 质检单分页 | 移动端质检页、工序执行页(按 executionId 查 FQC)、工单卡 ProductionWorkCard | 共用 → 保留（FQC 读口需迁 quality_lot） |
| GET /{id} | 质检单详情 | IQC：InboundInspectionDialog / IqcReviewDialog / IqcPrintSelectDialog / inventory/iqc | **IQC 在用 → 必须保留** |
| GET /{id}/fqc-report-print | 旧 FQC 报告打印 | quality-print/fqc-report.vue | **FQC 专属 → 可删** |
| POST / | 建质检单 | 旧 FQC 页 | 待确认（IQC 走 /inspection） |
| PUT / | 改单 | 旧 FQC 页 | 待确认 |
| DELETE /{id} | 删单 | 旧 FQC 页 | 待确认 |
| POST /{id}/judge | 判定 | 旧 FQC 页、移动端质检页 | 共用 → 保留（FQC 判定需迁） |
| POST /{id}/reinspect | 复检 | 旧 FQC 页（后端 IQC 走 service） | 共用 → 保留 |
| POST /{id}/disposition | FQC 不良处置 | 旧 FQC 页 | **FQC 专属 → 可删**（新模型走 quality_ncr） |
| POST /inspection | 建质检单(P3-C) | 旧 FQC 页 | 待确认 |
| GET /statistics、GET /export-excel/{id} | 统计/导出 | 旧 FQC 页 report.vue | 待确认 |

## 2. 前端 API（api/production/quality.ts · qualityApi）

| 方法 | 使用方 | 判定 |
|---|---|---|
| getById | IQC 4 页 + 旧FQC打印/返工单/IQC报告 | **必须保留** |
| page | 移动端质检、工序执行页、工单卡 | 共用 |
| judge | 旧FQC页、移动端质检 | 共用 |
| createInspection / reinspect / disposeFailure / getStatistics / exportExcel | 旧FQC页 index/report | FQC 侧 |
| getFqcReportPrint | quality-print/fqc-report.vue | **FQC 专属 → 可删** |

## 3. 前端页面与路由

- **FQC 旧页（可删）**：`src/views/production/quality/index.vue|print.vue|report.vue`；`quality-print/fqc-report.vue`、`FqcReportQr039Print.vue`；router `/production/quality-print/fqc-report`、`/rework-form`（旧 FQC 操作）
- **IQC 在用（保留）**：`quality-print/iqc-report.vue`、路由 `/production/quality-print/iqc-report`；`inventory/inbound/components/{InboundInspectionDialog,IqcReviewDialog,IqcPrintSelectDialog}`；`views/inventory/iqc/index.vue`
- **需迁移**：`views/production/execution/index.vue`（按 executionId 查 FQC → 应改读 quality_lot）；`views/mobile/quality.vue`（通用检验判定，含 FQC 类型 → 按 lot_type 分流）；`ProductionWorkCard.vue`

## 4. 菜单与权限

- 旧「生产质检」菜单 **264 已不存在**（质量域重建时已下）→ 无需删菜单
- 待核：按钮权限 `production:quality:view / :judge / :report` 等是否仍被 IQC 复用；IQC 菜单 330/333 用的是 `quality:lot:view` / `quality:ncr:view`
- 主路由 `/production/quality` 已不在 router（仅剩 `quality-print/*`）→ 旧页已是"无入口但可按 URL 打开"的半下线态

## 5. 删除前置结论（供 017/018/019 执行）

1. **不能整体删** `/production/quality`：先把 IQC 用到的能力拆出来（IQC 专用 Controller/Service），再删 FQC。
2. 后端 FQC 专属可直接删：`/{id}/fqc-report-print`、`/{id}/disposition`、`QualityActionServiceImpl.createFqcForExecution / handleFqcPass / handleFqcFail / disposeFqcFailure`。
3. 共用端点（page/{id}/judge/reinspect/statistics/export/inspection）：拆出 IQC 专用后，FQC 侧**停写并显式拒绝**（inspection_type=FQC → 报错）。
4. 前端：旧 FQC 页/打印直接删；IQC 页保持；execution/index.vue 与 mobile/quality.vue 按新模型改读后删除旧读口。
5. 验证口径：旧 FQC 页可删；IQC 全链路（收货→判定→部分接收→隔离→退货→返工→报废→复检）不受影响。
