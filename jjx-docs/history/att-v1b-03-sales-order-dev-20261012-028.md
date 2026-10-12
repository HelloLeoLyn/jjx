# 销售订单通用附件入口接入 AttachmentCenter（dev-20261012-028 / ATT-V1B-03）—— 实施记录

> 任务码：dev-20261012-028（ATT-V1B-03，P1）｜ 日期：2026-10-12 ｜ 状态：**已实施（代码就绪；运行时验收另计）**
> 依赖：027（已完成）｜参照：`views/sales/inquiry/index.vue` + `composables/useAttachmentRefs.ts`

---

## 1. 改动清单（5 文件，`git diff --stat` 98+/42−）

### 1.1 `views/sales/order/components/OrderForm.vue`
- 模板：`AttachmentPanel`(289) + `AttachmentUploader`(295) → **一个 `AttachmentCenter`**（`source-type="sales_order"`、`:source-id="form.orderId || null"`、`:draft-id="draftId"`、`:trace-id`、`:accept`、`view-perm/edit-perm`）；:304 客供按钮与 :305 `CustomerDocUpload` 保留。
- 导入：删 `AttachmentPanel`/`AttachmentUploader`；加 `AttachmentCenter` + `useUserStore`；vue 导入加 `nextTick`。
- refs：删 `uploaderRef`；加 `attachmentCenterRef`、`draftId`、`ensureDraftId/clearDraftId/newDraftId`（照询价单策略，sessionStorage `attach-draft:v1:<uid>:sales_order`，**只在为空时生成**）。
- `onMounted`：`if (!props.isEdit) ensureDraftId()`。
- 保存链路（新增模式）：`uploaderRef.flushPending(...)` → **`form.orderId=newOrderId` → `await nextTick()` → `try{ await attachmentCenterRef.value?.bindPending() }catch{ 仅在附件区提示，不冒泡 }`**；客供 `flushPending` 的 **false 中断语义不变**；保存成功后 `clearDraftId()`。

### 1.2 `ReviewDialog.vue:56`
`AttachmentUploader(biz-type=order)` → `AttachmentCenter mode="upload-only"`；`@uploaded="p=>onUploadSuccess(p.attachmentId)"` **保留** `attachmentIds` 收集（审批 payload 口径不变）。

### 1.3 `GeneratePlanDialog.vue:20`
`AttachmentUploader(biz-type=sales_order_confirmation)` → `AttachmentCenter mode="upload-only"`。

### 1.4 `OrderDetailDrawer.vue:293`（**CRLF 文件，行尾保持**）
`AttachmentPanel(biz-type=order)` → `AttachmentCenter mode="readonly"`（`v-if="props.orderId"`）。

### 1.5 `views/sales/order/index.vue:251/295`
旧 `AttachmentUploadDialog`（确认凭证弹窗）→ **`el-dialog` 包 `AttachmentCenter mode="panel"`**；导入替换。

## 2. 约束落实

- **保存链路语义不变**：客供 `flushPending` 的 `false → return false`（:910/:919）保留；`bindPending()` **不改 bindPending 语义**（document-not-saved/partial/all-failed 由组件内部处理）；异常 `try/catch` 隔离，**附件失败不冒泡成「整单保存失败」**。
- **draftId 照询价单**：只在为空时生成、sessionStorage 跨刷新复用、保存成功后清空；`:source-id="form.orderId || null"`。
- **权限**：仅 `view-perm="sales:order:view"` / `edit-perm="sales:order:edit"`；`locked` 保持默认 false（未自行发明）。
- **不动**审批/业务日志/其它表单逻辑；**不动** `CustomerDocUpload` 本体；**不删**旧组件（退役属 032）；**未回退** `useAttachmentRefs.ts`（沿用其最终形态）。

## 3. 验证（真实输出）

- `npx vue-tsc --noEmit` → **EXIT=0**
- `npm run check:status-enums` → `状态魔法值门禁通过（存量基线 122 处，新增 0 处）` **EXIT=0**
- `npm run check:docs` → 通过
- `OrderDetailDrawer.vue` 行尾：**CRLF 保持**（`git diff` 仅 10 行小改，非整文件）
- `grep AttachmentPanel/AttachmentUploader/AttachmentUploadDialog` 于 `views/sales/order/` → **无残留**

## 4. 验收（运行时，由 Hermes/人工）

新建订单上传 2 个 TEMP → 保存后 SQL 取证 `sys_attachment_ref` 新增行 + 附件 TEMP→BOUND；编辑态可直接上传绑定；详情只读；ReviewDialog/GeneratePlanDialog 上传正常；列表页附件弹窗替换后可用；历史附件 B 段只读不重复；无 `sales:order:edit` 账号看不到上传/解绑入口。**（未跑，需运行环境）**

## 5. 未执行声明

未改后端/数据库、**未 commit/push**。

## 6. 遗留

- 运行验收（§4）待执行；
- `ReviewDialog` 现由 AttachmentCenter 绑定为 refs，`approveOrder` 仍收到 `attachmentIds`（经 `@uploaded`）——与旧口径一致，但「引用 + 审批 payload」并存的双写语义可在验收时确认；
- 旧组件（AttachmentPanel/Uploader/UploadDialog）未删除，退役属 032。
