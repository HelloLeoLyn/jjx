# 旧附件组件退役与代码清理（ATT-V1B-07）

- 状态：现行结论（收口）
- 任务：dev-20261012-032（ATT-V1B-07；依赖 dev-20261012-031）
- 被取代或取代：无（清理 V1-B 迁移后无现役依赖的旧组件）
- 什么情况看这篇：判断旧附件组件（AttachmentPanel / AttachmentUploader / AttachmentUploadDialog）是否已可退役
- 最后复核：2026-10-12

## 1. 目标与范围

公共附件 V1-B 迁移后，清理**无现役依赖**的旧附件组件；仍有独立职责的保留并说明原因。范围：全仓扫描 `AttachmentPanel` / `AttachmentUploader` / `AttachmentUploadDialog` 及调用点。完成标准：无误删、无失效引用、无不必要兼容层。

## 2. 扫描结果（2026-10-12）

| 组件 | 现役调用点 | 处置 |
|---|---|---|
| `components/AttachmentUploadDialog/index.vue` | **无**（仅 `components.d.ts` 自动生成条目 + 一句注释） | **删除** |
| `components/AttachmentPanel/index.vue` | `components/BizFlowDetail/index.vue`（文档流水 Tab）、`views/production/quality-archive/index.vue`（归档附件） | **保留** |
| `components/AttachmentUploader/index.vue` | `views/production/quality-archive/index.vue`（回传扫描件）、`views/production/quality-template/index.vue`（`biz-type=quality_template`） | **保留** |

## 3. 处置与理由

1）**删除 `AttachmentUploadDialog`**：全仓无任何 import/模板引用（`grep` 仅命中 `components.d.ts` 自动条目与 `AttachmentUploader` 的一句注释），已被公共组件 `AttachmentCenter`（dialog 模式）完整替代 → 退役。
2）**保留 `AttachmentUploader`**：`quality-template` 页用它上传 `biz-type="quality_template"`，而 `quality_template` **不在** `AttachmentSourcePolicy` 的 8 个来源白名单内 → `AttachmentCenter`（走 refs/来源策略）**无法承接**，故仍有独立职责。
3）**保留 `AttachmentPanel`**：`BizFlowDetail`（通用文档流水）与 `quality-archive`（归档附件）在用，其来源不在 refs 白名单 → 保留。

## 4. 清理动作

- 删除 `jjx-web/src/components/AttachmentUploadDialog/index.vue`（整目录）。
- `jjx-web/src/components.d.ts`：移除 `AttachmentUploadDialog` 自动生成条目。
- `jjx-web/src/components/AttachmentUploader/index.vue`：注释去掉对已删组件的引用（保留对 `AttachmentPanel` 的说明）。

## 5. 遗留发现（不属本卡，仅记录）

- `jjx-web/src/views/sales/quotation/index.back`：**git 已跟踪**的历史遗留备份文件（2026-08-29），其中引用了 `AttachmentUploadDialog`。该文件**非 `.vue`/`.ts`、不参与编译**，不构成失效引用；清理它属独立事项，且非本会话产生的文件，故**未动**，建议另立清理卡。

## 6. 验证与边界

- `npx vue-tsc --noEmit` → 0 error（删除后无失效引用）。
- 未做：浏览器/E2E、未重启服务（按仓库规则只做静态自查）。
- 边界：本卡只清"无现役依赖"者；`quality_*` 来源若要并入公共附件中心，需先扩展 `AttachmentSourcePolicy`，属独立设计，不在本卡。

## 7. 待确认点

1）`quality_template` / `quality_archive` 来源是否纳入公共附件 refs 策略（纳入后才能退役 `AttachmentPanel`/`AttachmentUploader`）。
2）`index.back` 等历史遗留文件是否清理（另立卡）。
