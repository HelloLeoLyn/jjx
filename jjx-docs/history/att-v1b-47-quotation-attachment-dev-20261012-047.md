# 报价单附件闭环（dev-20261012-047 / ATT-V1B-47）—— 实施记录

> 任务码：dev-20261012-047（P1）｜ 日期：2026-10-12 ｜ 状态：**已实施（运行时验收另计）**
> 依赖：027（已提交 d8b5bbc2，仅换列表页旧弹窗）

---

## 0. 关键发现（卡第 6 点：先报告再改）

**报价单「保存链路」不止 `QuotationFormDialog.handleSubmit` 一处收尾：**
- `QuotationFormDialog.handleSubmit`（:617-660）**只做校验后 `emit('submit')`**，本身不保存；
- 真正保存 + 收尾在 **`views/sales/quotation/index.vue` `submitForm`**（`@submit`）：
  - 改单：`quotationApi.edit`(:690) → `getInfo` → **`flushCustomerDocs`(:693)**；
  - 新增：`quotationApi.add`(:699) → 取 `newId` → **`flushCustomerDocs`(:705)**。

→ 故 `bindPending()` **不能放 handleSubmit**（那里拿不到「保存成功」时点）。本轮按卡「先报告再改」，把它放到**正确位置：`index.vue` `submitForm` 保存成功之后**；`AttachmentCenter` 实例在子组件 `QuotationFormDialog`，通过 `defineExpose` 暴露 `bindPendingAttachments()` 供父调用。

## 1. 改动清单（3 文件，+98/−1）

### 1.1 `components/QuotationFormDialog.vue`
- 模板：在「报价明细」(divider :86) 与「金额汇总」(divider :194) **之间**新增 `附件` 分节 + `AttachmentCenter`（`source-type="quotation"`、`:source-id="props.formData.quotationId || null"`、`:draft-id="props.draftId || ''"`、`:trace-id`、`view-perm/edit-perm`）。
- `props` 加 `draftId?: string`（父下发）。
- 新增 `attachmentCenterRef` + **`bindPendingAttachments()`**（照询价单：`await nextTick()` → `try{ bindPending() }` → `document-not-saved/partial/all-failed` 提示 → `catch` 异常隔离），并 `defineExpose` 暴露。

### 1.2 `components/QuotationDetailDialog.vue`
- 新增只读 `AttachmentCenter`（`mode="readonly"`、`v-if="props.quotationId"`、`view-perm`）。

### 1.3 `views/sales/quotation/index.vue`
- 加 `draftId` + `ensureDraftId/clearDraftId/newDraftId`（照询价单：**只在为空时生成**、sessionStorage `attach-draft:v1:<uid>:quotation` 跨刷新、保存成功后清空）；`:draft-id="draftId"` 下发；`handleAdd` 调 `ensureDraftId()`。
- `submitForm`：改单/新增**保存成功**后 → `await quotationFormDialogRef.value?.bindPendingAttachments()`；新增成功 `clearDraftId()`。

## 2. 约束落实

- 保留 `CustomerDocUpload`(:304)、`ProductFileLibrary`(:313)、`flushCustomerDocs`(:378) 与审批审计流程（**未动**）。
- `source-type="quotation"`、`view-perm="sales:quotation:view"`、`edit-perm="sales:quotation:edit"`、`locked=false`。
- **未回退** `useAttachmentRefs.ts`（含 033/034 的 sha256 键与草稿策略）；**不删**旧组件（退役属 032）；不动后端/库。

## 3. 验证（真实输出）

- `npx vue-tsc --noEmit` → **EXIT=0**
- `npm run check:status-enums` → 通过（存量 122，新增 0）**EXIT=0**
- `npm run check:docs` → 通过
- `git diff --stat`：仅上述 3 文件（+98/−1）

## 4. 验收（运行时，另计）

新建报价单上传 2 个 TEMP → 保存后 SQL 取证 `sys_attachment_ref` 新增行 + 附件 TEMP→BOUND + `expires_at` 清空；绑定部分失败提示且队列可重试；编辑态上传即绑定；详情只读（无可操作按钮）；历史附件 B 段只读不重复。**（未跑，需运行环境）**

## 5. 未执行声明

未改后端/数据库；**未 commit/push**。

## 6. 遗留

- 运行验收（§4）待执行；
- 「保存链路在 index.vue 而非 handleSubmit」的偏差已按卡「先报告再改」处理并记录（见 §0）。
