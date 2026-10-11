# 客供资料受控库 P0：现状核查（4 清单）

- 日期：2026-10-11
- 任务：dev-20261011-025
- 前置设计：`jjx-docs/history/product-spec-layout-and-customer-docs-dev-20261010-032.md`（客供资料 V2.0）
- 范围：**只读**核查源码与本地 dev 库结构；不改业务代码、不建表、不迁移、不启停服务。
- 产出：现状差异 / DB 最终表结构建议 / 接口清单 / 页面清单，每条带源码或表结构证据。

---

## 一、清单 1：现状差异（设计 V2.0 → 现状）

| # | 设计条目 | 现状证据（源码 / 表结构） | 差异结论 |
|---|---|---|---|
| 1 | 产品归属 `productId` | `product.product_id` bigint **PRI**；`product.product_code` varchar **UNI**（information_schema）。 | 归属键成立。注意 product 已存在 `customer_id`/`customer_name`（均**可空**），与决策 D-02「不新增 customerId」不冲突，但客户维度已存在，需确认是否沿用。 |
| 2 | CustomerDoc / DocRevision / DocRevisionFile / DocSourceRelation | 全库无对应表：`%doc%`/`%customer%` 仅命中 `purchase_document`、`sales_customer`、`sales_customer_address`（information_schema.tables）。 | 受控模型**完全缺失**，属 P1 新建对象。 |
| 3 | 受控标识 `is_controlled` | `sys_attachment.is_controlled` tinyint **NOT NULL default 0**；仅经 `POST /system/attachment/{id}/release`（权限 `engineering:drawing:release`）置 1（`SysAttachmentServiceImpl.java:664-680`）。 | 有该列且默认不受控。语义偏差：**撤回（released=false）也把 is_controlled 置 1**（:678），「撤回」≠「解除受控」。 |
| 4 | 文件 SHA-256 | `sys_attachment.sha256` char(64) NULL（`jjx-docs/sql/migrations/applied/264_attachment_sha256.sql`）；落库时计算：`uploadAttachment` `SysAttachmentServiceImpl.java:112`、`uploadProductFile` :388。 | 哈希列已就绪；但**无 sha256 唯一索引、无内容去重**，也无「按产品范围查同哈希」索引（现有 `idx_biz(biz_type,biz_id)`、`idx_attachment_drawing(biz_type,biz_id,drawing_no,version,deleted)`）。 |
| 5 | 文件不可变 / 删除留存 | 软删进回收站：`deleteAttachment` :415-431 → `logicalDelete`；物理删：`permanentDelete` :471-488 → 删物理文件 + `physicalDelete`。受控附件禁软删(:420-422)、禁物删(:477-478)、禁过期清理(:497-498)；`product` 图纸「现行或已下发」禁删(:425-427)。 | 留存保护**只覆盖 `is_controlled=1` 与产品图纸**；inquiry/quotation/order/sample 的普通附件可被删除并在回收站到期后**物理清除** → 不足以支撑「引用不可变文件」。 |
| 6 | 来源：询价/报价/订单/样品 | `customerDocs(productId)` 归集 SQL（`SysAttachmentServiceImpl.java:518-536`）：仅 `biz_type='inquiry'`（`sales_inquiry.product_id`，**单头**）与 `biz_type='quotation'`（`sales_quotation_item.product_id` + 报价**单头**附件 `biz_id=quotation_id`）。 | ① **不含 sales_order / sample**（设计含 5 类，现状 2 类）；② 报价单头附件按明细产品归集 → 多产品报价会**互相串附件**（与 `product-work-spec-p0-audit-dev-20261011-003.md` §二一致）；③ 样品单 `biz_type='sample'` 但 `bizId=orderId`（`views/sales/sample-order/index.vue:372`），非 `sample_order_id`。 |
| 7 | 来源类型 `sales_order` | 销售订单附件 `biz_type` **三种并存**：`OrderForm.vue:291`=`sales_order`；`ReviewDialog.vue:57`、`OrderDetailDrawer.vue:295`、`review-print.vue:117`=`order`；确认凭证 `sales_order_confirmation`（`views/sales/order/index.vue:253`、`GeneratePlanDialog.vue:21`）。 | 同一单据 biz_type 不统一；设计只列 `sales_order`，无法直接映射。 |
| 8 | 复用上传/预览/下载 | 上传 `AttachmentUploader/index.vue`、`AttachmentUploadDialog/index.vue`、页面 `el-upload`；预览 `OperationPreviewDialog/*` + `attachmentApi.downloadUrl(id)`（内联 image/pdf，`SysAttachmentController.java:182-195`）；下载 `GET /system/attachment/download/{id}`。 | 组件齐备、可复用；但**下载接口无鉴权**（见清单 3）。 |
| 9 | 作业规范页「客供资料」页签 | `ProductSpecDrawer.vue:21-23` customer tab 渲染 `CustomerDocPanel`（:117 import）；`CustomerDocPanel.vue` 汇总 `customerDocs()`+`productFiles('客供稿')` 只读列表，可上传/管理（`product:edit`/`product:delete`），顶部 `el-alert`「受控入库、确认生效与修订历史将在后续接入」。 | 现状=只读聚合 + 未受控上传；与 V2.0「受控闭环」差距=全部受控能力（0）。 |
| 10 | 岗位/权限 | `SysAttachmentController` 仅 2 处 `@SaCheckPermission`：`drawing-metadata`=`product:edit`(:96)、`release`=`engineering:drawing:release`(:152)；其余 upload/batch-upload/list/by-trace/product/recycle/restore/permanent/delete-by-biz/set-current/customer-docs/download **仅登录态**。前端 `CustomerDocPanel` canUpload=`product:edit`、canManage=`product:edit|product:delete`。 | **无独立受控资料权限点**；未授权下载/删除仅靠登录，前端隐藏≠后端拦截。 |
| 11 | 审计 | `SysAttachmentController` 全文**无 `@Log`/OperLog**；附件增删改查不写操作日志。 | 设计 §11.2 要求的审计事件**无落地**（P3）。 |
| 12 | 数据库/唯一索引能力 | MySQL（information_schema）；`sys_attachment` 现有 `idx_biz`、`idx_attachment_drawing`、`idx_create_time`；无 sha256 唯一索引。 | 支持条件唯一索引（生成列）实现「同 doc 最多一个 active」。 |

---

## 二、清单 2：DB 最终表结构建议（P0 不建表，仅建议）

**复用而非新建文件表**：`sys_attachment` 已具备 `biz_type/biz_id/category/version/drawing_no/drawing_name/file_role/is_current/is_controlled/released/released_at/released_by/sha256/file_path/sort_order/deleted`，可直接作为物理文件与来源附件的**唯一真源**；受控层只做「引用 + 状态机」，不复制文件元数据。

建议新增 4 表（逻辑模型 → 物理草案）：

1. `customer_doc`：`id` PK；`product_id` bigint NOT NULL（引用 `product.product_id`）；`doc_no` varchar NULL；`doc_type` varchar NOT NULL；`title` varchar NOT NULL；`active_revision_id` bigint NULL；`create_by/create_time/update_by/update_time`；`deleted` tinyint default 0。
2. `customer_doc_revision`：`id` PK；`doc_id` NOT NULL；`version_no` int NOT NULL（服务端序号）；`revision_no` varchar NULL（客户原值，如 A/0）；`state` varchar NOT NULL（`received/active/superseded/obsolete`）；`change_reason`/`ecn_ref` varchar NULL；`received_by/received_at`、`confirmed_by/confirmed_at`、`effective_at` NULL；审计列。
3. `customer_doc_revision_file`：`id` PK；`revision_id` NOT NULL；`attachment_id` bigint NOT NULL（引用 `sys_attachment.id`）；`sha256` char(64)（入库存快照值）；`file_role`（`primary/supporting`）；`sort_order` int；`storage_mode`（`reference/snapshot`）。
4. `customer_doc_source_relation`：`id` PK；`revision_id` NOT NULL；`source_type`；`source_ref_id`；`source_ref_line_id` NULL；`source_attachment_id` NULL；`linked_by/linked_at`。

约束建议（对齐设计 §6.1）：
- UK `customer_doc_revision(doc_id, version_no)`；「同一 doc 最多一个 active」用生成列 `active_doc_id = IF(state='active', doc_id, NULL)` + 唯一索引。
- 来源去重 UK `(revision_id, source_type, source_ref_id, source_ref_line_id, source_attachment_id)`；MySQL NULL 唯一性需生成列兜底。
- 引用完整性：`revision_file.attachment_id → sys_attachment.id`；物理删除前查引用（可复用 `is_controlled` 作「已被受控库引用」标记）。
- `active_revision_id` 必须指向本 doc 且 `state='active'` 的 revision；无有效版允许 NULL。

待用户确认（不替用户拍）：① `doc_type` 字典落 `sys_dict` 还是沿用 `sys_attachment.category`；② `storage_mode` 默认策略——因现状普通附件**可被物理删除**，建议源附件一律强制 `snapshot`。

**门槛**：任何新表须按 `CONVENTIONS.md` §15 走「提案→拍板→`model-baseline.json` 登记→迁移号」；P0 不执行。

---

## 三、清单 3：接口清单

### 3.1 现有接口（`SysAttachmentController`，前缀 `/system/attachment`）

| 方法 路径 | 权限守卫 | 用途 |
|---|---|---|
| POST `/upload` | 无（仅登录） | 通用上传（询价/报价/订单/样品等写入） |
| POST `/batch-upload` | 无 | 批量上传 |
| GET `/list` | 无 | 按 bizType+bizId 列表 |
| GET `/by-trace/{traceId}` | 无 | 按链路取附件（含来源单据文档） |
| POST `/upload-product` | 无 | 产品文件库上传（`biz_type=product`），落 `sha256` |
| PUT `/{id}/drawing-metadata` | `product:edit` | 补录图纸编号/版本/用途 |
| GET `/product/{productCode}` | 无 | 产品文件库（按产品编码） |
| DELETE `/{id}` | 无 | 逻辑删（受控/现行拦截） |
| GET `/recycle-list` | 无 | 回收站列表 |
| POST `/restore/{id}` | 无 | 回收站还原（不恢复现行/下发） |
| DELETE `/permanent/{id}` | 无 | 物理删（受控拦截） |
| POST `/permanent-expired` | 无 | 清理回收站过期（默认 30 天；受控跳过） |
| DELETE `/delete-by-biz` | 无 | 按业务关联删除 |
| POST `/{id}/set-current` | 无 | 图纸版本设现行（同版本原稿+打印件同步） |
| POST `/{id}/release` | `engineering:drawing:release` | 下发/撤回（置 `released`，并置 `is_controlled=1`） |
| GET `/customer-docs/{productId}` | 无 | 客供资料只读归集（inquiry + quotation） |
| GET `/download/{id}` | 无 | 内联预览/下载（image/pdf inline，其余 octet-stream） |

### 3.2 缺口接口（设计 §10，均未实现）

`GET /api/customer-docs`、`GET /api/customer-docs/{id}`、`GET /{id}/revisions`、`POST /intake/check`、`POST /intake/confirm`、`POST /{id}/revisions/{revisionId}/activate`、`POST /{id}/revisions/{revisionId}/obsolete`、`GET /{id}/audit-logs`、`GET /files/{fileId}/download`（受控下载）。

证据：`SysAttachmentServiceImpl.customerDocs()` :518-536；控制器全文 :35-196。

---

## 四、清单 4：页面清单

### 4.1 现有组件（可复用）

| 文件 | 角色 | 证据/要点 |
|---|---|---|
| `jjx-web/src/components/product/customer-doc/CustomerDocPanel.vue` | 「客供资料」页签主体 | 汇总 `customerDocs()`+`productFiles('客供稿')`；上传/管理受 `product:edit`/`product:delete`；只读列表 + 未受控上传 |
| `jjx-web/src/components/product/ProductSpecDrawer.vue` | 作业规范抽屉 + tab 注册 | :21-23 customer tab；:117 import；另有 样品/领料单/产品作业规范/印刷规范 等 tab |
| `jjx-web/src/components/product/ProductFileLibrary.vue` | 产品文件库（类别过滤） | `categories=['客供稿']`；上传 `product:edit`/删除 `product:delete`；`isControlled` 标签 :107 |
| `jjx-web/src/components/AttachmentUploader/index.vue` | 内嵌上传 | 有 bizId 立即传，无 bizId 暂存 flush（DEV-733） |
| `jjx-web/src/components/AttachmentUploadDialog/index.vue` | 附件管理弹窗 | `el-upload` + `el-image-viewer` 预览 |
| `jjx-web/src/components/AttachmentPanel/index.vue` | 附件列表 | props bizType/bizId/traceId |
| `jjx-web/src/components/OperationPreviewDialog/*` | 业务操作证据上传/预览 | `registry.ts` 按 bizType（quotation/sample_order…）配置 |
| `jjx-web/src/components/TraceAttachmentPanel/index.vue` | 按链路聚合附件 | `listByTrace` + `list` |
| `jjx-web/src/views/engineering/product-spec/index.vue` + `components/docset.ts:170` | 打印文档集「客供资料」段 | 调 `customerDocs(productId)` 追加文件 |

### 4.2 缺口组件（设计 §9，均未建）

`components/customer-doc/` 下：`CustomerDocEditor.vue`（新建/来源关联/新版本）、`CustomerDocDuplicateDialog.vue`（重复候选）、`CustomerDocHistory.vue`（版本历史）、`CustomerDocDetail.vue`（详情/预览）、`types.ts`、`api.ts`。

---

## 五、结论、风险与门槛

- **可直接复用**：`sys_attachment`（含 `sha256`/`is_controlled`/`released`）+ 上传/预览/下载组件 + 抽屉页签 + 产品文件库；`product.customer_id/customer_name` 客户维度已存在。
- **必须新建**：4 张受控表（清单 2）+ 独立权限点 + 附件审计（P1/P3）。
- **主要风险**：① 报价单头附件按明细产品归集 → 串附件；② 销售订单 `biz_type` 三种并存；③ 下载接口无鉴权；④ 普通附件可物理删除，不满足「引用不可变文件」；⑤ `is_controlled` 撤回也置 1，语义需收紧。
- **实施门槛**：新表按 §15 提案→拍板→baseline 登记→迁移；P0 只读，不改代码/不建表/不迁移。
