# 公共附件 V1-B 前端设计方案

- 任务码：dev-20261012-002（公共附件 V1-A/V1-B）
- 日期：2026-10-12
- 性质：**只读调研 + 方案设计**（本轮不改代码/数据库/配置，不执行迁移与重启，不 commit）
- 前置：V1-A 后端运行时验收 GO（271/272/273 已记账归档、P0 修复与代码侧 B 均运行时 PASS）
- 不在本方案范围：`quality_template_registry` 历史数据修复（已独立登记 `dev-20261012-011`，V1-B 不得夹带）

## 0. 修订记录（v2，2026-10-12 收敛版）

本版按"设计收敛"要求对 v1 做定向修订，**不重新全仓调研**；总体架构（`AttachmentCenter` 统一入口 + `useAttachmentRefs` + `attachmentRef` API）保持不变。修订点：

| # | 修订项 | v1 的写法 | v2 的处理 |
|---|---|---|---|
| 1 | 子组件拆分 | 预先固定 4 个新文件（`AttachmentCenter/AttachmentTempUploader/AttachmentMetaDialog/AttachmentDuplicateTip`） | 改为**按实际复杂度拆**：起步只落地容器 + 1 个上传件，元数据/重复提示先作为容器内区块，出现复用或超过约 300 行再拆（§2.2） |
| 2 | TEMP 待绑定状态 | 只描述"上传→绑定"两步，未定义异常路径 | 新增 §2.4 状态机：关闭弹窗 / 保存失败 / 部分绑定成功 / 重复提交 / 重新打开页面，并明确**哪些状态只在当前会话保留** |
| 3 | 历史附件合并读取 | "refs ∪ attachment/list 按 id 去重" | **修正**：refId 与 attachmentId 是两种主键，**禁止按 attachmentId 简单去重**（会丢引用元数据）；新增 §4.3 合并规则，并明确**不得把历史附件自动转 BOUND** |
| 4 | 重复检测通道 | 以上传后读 `X-Attachment-Duplicate-Count` 为主通道 | **修正**：`POST /refs/temp` **不返回**该响应头（只有旧 `upload`/`batch-upload`/`upload-product` 设置），新流程的重复提示改为"绑定后按范围查 `by-sha256`"，并把"temp 端点补头"列为**后端缺口（本轮不实施）**（§5.4、§6.3） |
| 5 | locked / readonly | 附带了逐单据锁定状态清单（拟实施） | 收敛为**组件能力**，不定义业务规则、不实施；需要后端补状态校验的缺口单列（§6.2/§6.3） |
| 6 | 专用业务组件 | 已保留 | 强化：`CustomerDocUpload` / `CustomerDocPanel` / `ProductFileLibrary` 的专用职责**不得**改成通用上传（§2.1） |
| 7 | 页面保存流程 | 未核查 | 新增 §4.4：五页"新建/编辑/详情"保存链路与附件收尾的实测核查，列出接入时不得破坏的点 |
| 8 | Work Package | 5 个 WP（WP3 一次做五页） | 改为**短 WP**：先 API + 公共组件，再**只选 1 个试点页**验证，通过后逐页推广（§7） |

## 1. 当前前端附件实现盘点

### 1.1 API 层

| 文件 | 现状 | 判定 |
|---|---|---|
| `jjx-web/src/api/system/attachment.ts`（197 行） | 只有 V1-A 之前的接口：`upload` / `batch?`（无）/ `bySha256` / `list` / `listByTrace` / `remove` / `customerDocs` / `setCurrent` / `release` / `downloadUrl` / `uploadProductFile` / `updateDrawingMetadata` / `productFiles` / `recycleList` / `restore` / `permanent` / `permanentExpired` | **需补**：V1-A 的 `refs/*` 全部接口（见 §4） |
| 全仓检索 | `attachment/refs`、`/refs/temp`、`cleanup-preview`、`document-types` 在前端**0 命中** | V1-A 能力**完全未接入**前端 |

### 1.2 组件层（共 2043 行）

| 组件 | 行数 | 现状职责 | 判定 |
|---|---|---|---|
| `components/AttachmentPanel/index.vue` | 268 | 只读列表：名称/大小/类别/版本/上传人/时间 + 下载 + 图片预览（含左右切换，走 `attachmentApi.list` + `listByTrace`） | **改造**为公共组件的只读视图（补：展示名、解绑、分类、LEGACY 标识） |
| `components/AttachmentUploader/index.vue` | 177 | 只做上传：有 `bizId` 立即 `POST /attachment/upload`；无 `bizId`（新建单据）把 `File` 暂存在浏览器，保存后 `flushPending()` 批量上传 | **改造**为 TEMP 上传（取消浏览器暂存 File 的做法，见 §6） |
| `components/AttachmentUploadDialog/index.vue` | 218 | 弹窗：`el-upload` 直传 + 列表 + 删除（`DELETE /attachment/{id}`） | **淘汰**（与公共组件重复；其 `action` 直传还会绕过 request 封装的错误处理） |
| `components/TraceAttachmentPanel/index.vue` | 292 | 按 traceId 聚合来源单据附件 | **疑似死组件**：全仓无 `.vue` 引用（建议随 WP5 复核后删除或改造为公共组件的 `trace-id` 模式） |
| `components/customer-doc/CustomerDocUpload.vue` | 158 | 客供资料专用：产品×资料类型矩阵、逐行状态、待上传确认、`customer-doc-upload` | **保留**（专用入口不可被通用组件替代），仅把读取侧接 refs |
| `components/product/customer-doc/CustomerDocPanel.vue` | 234 | 产品维度客供资料聚合（`customerDocs` + `productFiles`），权限 `product:edit`/`product:delete` | **保留**，改造其数据源为 refs（`/refs/product/{productId}`） |
| `components/product/ProductFileLibrary.vue` | 578 | 产品文件库：上传/删除/下发/现行、图纸元数据，权限由 `uploadPerm`/`deletePerm`/`releasePerm` 驱动 | **保留**（产品工程文件语义不同，走 `upload-product`）；**作为权限驱动组件的既有先例** |
| `components/product/ProductFilePreview.vue` + `productFilePreview.ts` | 56+62 | 工程文件预览封装 | 保留 |
| 其它引用点 | `views/system/file/index.vue`（附件管理页）、`views/engineering/drawing/components/EngineeringDrawingLibrary.vue`、`views/product/film/index.vue`（菲林）、`components/JJXIcon`、`TraceTimeline`、`OperationPreviewDialog` | — | 逐页判定，不纳入公共组件（各自语义独立） |

### 1.3 五类业务页面现状

| 页面 | 真实文件 | 附件相关用法 | 判定 |
|---|---|---|---|
| 询价单 | `views/sales/inquiry/index.vue:324,331`、`components/InquiryDetailDialog.vue:59` | `AttachmentPanel` + `CustomerDocUpload(biz-type=inquiry)` | 接入公共组件；客供资料入口保留 |
| 报价单 | `views/sales/quotation/components/QuotationFormDialog.vue:304,313`、`index.vue:173`、`index.back:815` | `CustomerDocUpload(quotation)` + `ProductFileLibrary` + 旧 `AttachmentUploadDialog` | 接入公共组件；**`index.back` 是备份文件，需复核删除** |
| 销售订单 | `views/sales/order/components/OrderForm.vue:289,295,305`、`OrderDetailDrawer.vue:293`、`ReviewDialog.vue:56`、`GeneratePlanDialog.vue:20`、`index.vue:251` | `AttachmentPanel` + `AttachmentUploader` + `CustomerDocUpload(sales_order)` + 旧 `AttachmentUploadDialog`；`ReviewDialog`/`GeneratePlanDialog` 仅复用上传器 | 接入公共组件；两处"仅上传"改为公共组件的 `mode="upload-only"` |
| 样品单 | `views/sales/sample-order/index.vue:369,376` | `AttachmentPanel` + `CustomerDocUpload(sample)` | 接入公共组件 |
| 产品 | `views/product/list/components/ProductDetail.vue:263,291`、`film/index.vue:344,438` | `ProductFileLibrary`（工程文件）+ 直接 `downloadUrl`；菲林走 `uploadProductFile` | 产品文件库保留；客供资料面板接 refs |

### 1.4 权限、锁定、重复检测、历史附件现状

| 维度 | 现状（证据） | 缺口 |
|---|---|---|
| 权限 | 组件级先例：`ProductFileLibrary` 的 `uploadPerm/deletePerm/releasePerm` → `userStore.hasPermission`（`:210/:213/:216`）；页面级用 `v-hasPermi`（`views/sales/inquiry/index.vue:68/79/90` 等） | 附件公共组件**没有**权限 props；上传/解绑按钮对所有有查看权的人可见 |
| 单据锁定 | 五类业务页的附件区**没有任何按单据状态的禁用**（检索 `附件 + status/lock/readonly` 空命中）；后端只校验来源 `:edit` 权限与 `protectedFile()` | 前端无锁定口径；"已审核/已下发单据不可改附件"未实现（需业务定义，见 §7） |
| 重复检测 | `attachmentApi.bySha256`（`:65`）**无任何调用方**；后端响应头 `X-Attachment-Duplicate-Count` 前端**0 命中** | 重复提示完全没做 |
| 历史附件 | 列表走 `GET /attachment/list?bizType&bizId`（按行内 `biz_type/biz_id`），LEGACY 行照原样展示；`upload_state` 前端从不读取 | 无 LEGACY 标识、无"未绑定引用"提示；不会误改历史状态（符合要求，但需要显式呈现） |

**结论**：现状是"5 个页面 × 3 套上传/列表实现"（`AttachmentUploader`、`AttachmentUploadDialog`、各页面自拼 `el-upload`），且完全没有用上 V1-A 的引用模型。V1-B 的价值就是把它们收敛到 1 个公共组件 + 1 套 refs 数据通道。

## 2. 推荐方案（唯一推荐，非备选罗列）

**一个公共组件 `AttachmentCenter` + 一个 composable `useAttachmentRefs` + 一个 API 模块 `attachmentRef.ts`**，业务页面只声明"来源类型/来源ID/权限/锁定状态"，不再自己写上传与列表。

```
jjx-web/src/
├── api/system/attachmentRef.ts            【新增】V1-A 引用接口封装（§3）
├── composables/useAttachmentRefs.ts       【新增】数据与流程编排 + TEMP 待绑定状态机（§2.3/§2.4）
└── components/attachment/
    ├── AttachmentCenter.vue               【新增】统一入口（panel / dialog / upload-only / readonly 四态）
    └── AttachmentTempUploader.vue         【新增】TEMP 上传（替代旧 AttachmentUploader 的浏览器暂存 File 做法）
```

**子组件不预先固定数量**（修订 1）：起步只落地上面 2 个 `.vue`。元数据编辑（分类/展示名/备注/排序）与重复提示**先作为 `AttachmentCenter` 内部区块**；满足以下任一条件再拆成独立文件（候选名 `AttachmentMetaDialog.vue` / `AttachmentDuplicateTip.vue`）：

1. 被 2 个以上页面独立引用（非经 `AttachmentCenter`）；
2. `AttachmentCenter.vue` 超过约 300 行或模板出现 3 层以上嵌套；
3. 需要独立权限/独立测试面（如元数据编辑要单独受限）。

拆分只动文件边界，不动 props/events 契约（§2.2 保持不变）。

### 2.1 职责边界（谁负责什么）

| 能力 | 归属 | 说明 |
|---|---|---|
| 上传（单/批量）、TEMP 建立、绑定、解绑、列表、预览、下载、分类、备注、展示名、排序、重复提示 | **公共组件** | 全部走 V1-A refs；业务页面零实现 |
| 来源类型 / 来源ID / 标题 / 是否只读 | **业务页面**（props 传入） | 页面知道自己是哪张单 |
| 来源权限码（view/edit） | **业务页面**（props）或组件按内置映射推导 | 映射表与后端 `AttachmentSourcePolicy` 保持一致（§4.3），**后端仍是最终裁决方** |
| 单据锁定判定 | **业务页面**（props `locked`） | 页面知道单据状态；组件只负责"锁定时禁用写操作" |
| 客供资料矩阵（产品 × 资料类型 + 整理名预览） | **业务页面 + `CustomerDocUpload`**（保留） | 语义特殊，不复用通用上传 |
| 产品工程文件（图纸版本/现行/下发） | **`ProductFileLibrary`**（保留） | 走 `upload-product` + 图纸元数据，属产品模块 |
| 附件引用数据的唯一真源 | **后端 `sys_attachment_ref`** | 前端不缓存第二份模型（不引入第二套数据模型） |

### 2.2 `AttachmentCenter.vue` Props / Events

Props：

| 名 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `sourceType` | `'inquiry'\|'quotation'\|'sales_order'\|'sample'\|'product'\|'purchase_order'\|'production_order'\|'material'` | 必填 | 与后端来源白名单一致 |
| `sourceId` | `number \| null` | 必填 | 新建单据未保存时为 `null`（只允许 TEMP 上传，不绑定） |
| `sourceNo` | `string` | `''` | 展示用（列表来源单号） |
| `mode` | `'panel' \| 'dialog' \| 'upload-only' \| 'readonly'` | `'panel'` | `upload-only` 供 `ReviewDialog`/`GeneratePlanDialog` 这类只需要上传的场景 |
| `readonly` | `boolean` | `false` | 纯展示（无上传/解绑/编辑） |
| `locked` | `boolean` | `false` | 单据锁定：隐藏/禁用全部写操作（由页面判定，见 §7） |
| `viewPerm` / `editPerm` | `string` | `''` | 权限码；为空表示不校验（仍受后端裁决） |
| `maxSizeMB` | `number` | `20` | 文件大小上限 |
| `accept` | `string[]` | 内置常用集 | 扩展名白名单 |
| `categories` | `Array<{value,label}>` | 从 `/refs/document-types` 拉取 | 分类字典（`attachment_document_type`） |
| `traceId` | `string` | `''` | 链路追溯（只读追加展示同链路来源文档） |
| `autoBind` | `boolean` | `true` | `sourceId` 有值时，TEMP 上传后立即绑定；`false` 时由父组件调 `bindPending()` |

Events：

| 事件 | 载荷 | 时机 |
|---|---|---|
| `uploaded` | `{ attachmentId, temp: boolean }` | TEMP 上传成功 |
| `bound` | `{ refId, attachmentId }` | 建立引用成功（TEMP→BOUND） |
| `unbound` | `{ refId, attachmentId }` | 解绑成功 |
| `changed` | `{ count }` | 列表数量变化（父组件刷新单据用） |
| `duplicate` | `{ attachmentId, sha256, matches: number }` | 检测到同内容（提示用，不阻断） |
| `error` | `{ action, message }` | 失败（组件已 toast，父组件可选上报） |

Exposed（`defineExpose`）：`refresh()`、`bindPending()`、`pendingCount()`、`clearPending()`（对齐旧 `AttachmentUploader` 的 `flushPending` 调用点，便于渐进替换）。

### 2.3 `useAttachmentRefs`（composable）职责

`list(sourceType, sourceId)`、`uploadTemp(file, requestKey)`、`bind({attachmentId, sourceType, sourceId, ...meta})`、`unbind(refId)`、`updateMeta(refId, meta)`、`checkDuplicate(sha256, scope)`、`documentTypes()`，并统一处理：错误 toast、加载态、`LEGACY`/`TEMP`/`ORPHAN` 状态标记、权限/锁定短路。**业务页面不直接拼 URL。**

### 2.4 TEMP 待绑定状态管理（修订 2）

核心事实：**TEMP 附件在服务端有真实归属**（`sys_attachment` 行，`upload_state=TEMP`、`upload_owner=当前用户`、`expires_at=+7天`），前端浏览器里**不再持有 `File` 对象**。因此待绑定队列只是一个 "attachmentId + 请求键 + 展示信息" 的清单，丢失可重建。

队列条目：`{ attachmentId, requestKey, fileName, fileSize, sha256?, state, error?, createdAt }`；`state ∈ pending | binding | bound | failed`。

| 场景 | 行为 | 状态保留范围 |
|---|---|---|
| 上传成功、`sourceId` 有值 | 立即绑定；成功即从队列移除 | — |
| 上传成功、`sourceId=null`（新建单据） | 入队 `pending`，界面显示「待绑定 N」 | **当前会话 + 本地持久化**（下条） |
| **关闭弹窗 / 切换页签** | 队列**不清空**（附件已在服务端，清空等于制造"看不见的 TEMP 孤儿"）；再次打开时按队列渲染「待绑定」区 | 已持久化则跨刷新保留 |
| **重新打开页面 / 浏览器刷新** | 从 `localStorage`（key：`attach-pending:{sourceType}:{sourceNo或空}`）读回 attachmentId 清单，只显示 id/文件名/时间，**不重传文件**、不展示缩略图 | 仅保留清单；`File` 与 `ObjectURL` 一律不保留 |
| **保存失败** | 队列保持 `pending`（不丢），单据表单保持原状；提示"单据未保存，N 个附件仍待绑定" | 同上 |
| **部分绑定成功** | 逐条独立处理：成功条目出队（`bound`），失败条目留在队列并标 `failed + error`；**不做整批回滚**（引用是独立的，回滚反而会删掉已成功的合法引用） | 同上 |
| **重复提交（连点保存/重复绑定）** | 前端按钮 loading + 队列条目置 `binding` 防重；`POST /refs` 对同来源同附件幂等（返回原 `refId`，V1-A 已实测）；同一 `requestKey` 重复上传同内容返回原 `attachmentId` | — |
| **附件已在别处被绑定/过期** | 绑定报错按其文案展示（如"附件不存在或已删除"）；条目标 `failed`，提供"移除待绑定"（仅出队，不删服务端附件） | — |
| 用户主动放弃 | 「移除待绑定」只出队；服务端 TEMP 行留到 7 天到期（V1-A 保留期规则），**前端不触发删除** | — |

持久化写入时机：入队、出队、绑定失败（各写一次 `localStorage`）；读取时机：组件挂载。**仅在当前会话/本地保留的东西**：待绑定 id 清单、列表滚动位置、上传中的进度；**不在本地保留**：`File`/`Blob`、附件内容、权限判断结果（权限每次向后端/`userStore` 取）。

## 3. V1-A API 对接清单

### 3.1 用到/新增的前端方法

| 前端方法（新增于 `attachmentRef.ts`） | 后端端点（真实存在，V1-A） | 用途 |
|---|---|---|
| `tempUpload(file, requestKey)` | `POST /system/attachment/refs/temp`（multipart: file, requestKey 16~100 位） | TEMP 上传（7 天保留） |
| `createRef(body)` | `POST /system/attachment/refs` | 绑定：`{attachmentId, sourceType, sourceId, productId?, documentType?, displayName?, remark?, sortOrder?}` |
| `listRefs(sourceType, sourceId)` | `GET /system/attachment/refs` | 列表（按来源过滤） |
| `refsByProduct(productId)` | `GET /system/attachment/refs/product/{productId}` | 产品维度聚合（含逐来源权限过滤） |
| `updateMeta(refId, body)` | `PUT /system/attachment/refs/{refId}` | 分类/展示名/备注/排序 |
| `unbind(refId)` | `DELETE /system/attachment/refs/{refId}` | 解绑（幂等，不动物理文件） |
| `downloadByRef(refId)` / `previewByRef(refId)` | `GET /system/attachment/refs/{refId}/download` `.../preview` | 按引用下载/预览（预览白名单：pdf/png/jpeg/gif/webp/bmp） |
| `refsBySha256(sha256, sourceType, sourceId)` | `GET /system/attachment/refs/by-sha256` | 范围内同内容检测（返回 `refId/attachmentId/file_name/file_size/sha256`） |
| `documentTypes()` | `GET /system/attachment/refs/document-types` | 分类字典（一级 `attachment_category` / 二级 `attachment_document_type`，二级带 `ext_data.parent/sources`） |
| `cleanupPreview(afterId, limit)` | `POST /system/attachment/refs/cleanup-preview`（admin） | 仅后台页使用，**不进业务页面** |

### 3.2 可复用的既有前端方法

| 方法 | 端点 | 何时仍用 |
|---|---|---|
| `attachmentApi.list`（`attachmentApi.list`） | `GET /system/attachment/list` | **兼容读取**：未回填引用的历史单据（LEGACY 行仍在 `biz_type/biz_id`） |
| `attachmentApi.downloadUrl(id)` | `GET /system/attachment/download/{id}` | 历史附件（无 refId 时）与图纸/工程文件 |
| `attachmentApi.bySha256` | `GET /system/attachment/by-sha256` | 已有单据范围内的重复检测（旧口径，需 `bizType+bizId`） |
| `attachmentApi.listByTrace` | `GET /system/attachment/by-trace/{traceId}` | 链路聚合（只读追加） |
| `attachmentApi.uploadProductFile` 等 | `/system/attachment/upload-product`、`/{id}/set-current`、`/{id}/release`、`/{id}/drawing-metadata`、`/product/{code}` | **产品文件库专用**，V1-B 不改语义 |

### 3.3 需要明确不能用的旧址

| 旧址 | 原因 |
|---|---|
| `POST /system/attachment/upload`、`/batch-upload`（通用上传） | 会写入 `biz_type/biz_id` 与 LEGACY/`BOUND` 状态，属 V1-A 之前的通道；新代码统一走 TEMP→refs。**保留接口用于兼容**，但公共组件不再调用 |
| `DELETE /system/attachment/{id}`（旧删除） | V1-A 中它=按业务上下文解绑；公共组件改用 `DELETE /refs/{refId}` 语义更明确。旧接口仅在"历史附件、尚无引用"时使用 |
| `DELETE /permanent/{id}`、`POST /permanent-expired` | V1-A 已明确禁用（直接抛错），前端入口需隐藏（`views/system/file/index.vue` 待复核） |

### 3.4 来源 → 权限码映射（与后端 `AttachmentSourcePolicy` 一致，前端只用于提前禁用）

| sourceType | 权限前缀 | 完整写权限 | 完整读权限 |
|---|---|---|---|
| `product` | `product` | `product:edit` | `product:view` |
| `inquiry` | `sales:inquiry` | `sales:inquiry:edit` | `sales:inquiry:view` |
| `quotation` | `sales:quotation` | `sales:quotation:edit` | `sales:quotation:view` |
| `sales_order` | `sales:order` | `sales:order:edit` | `sales:order:view` |
| `sample` | `sales:sample` | `sales:sample:edit` | `sales:sample:view` |
| `production_order` | `production:order` | `production:order:edit` | `production:order:view` |
| `purchase_order` | `purchase:order` | `purchase:order:edit` | `purchase:order:view` |
| `material` | `inventory:material` | `inventory:material:edit` | `inventory:material:view` |

旧别名前端需先归一（`order→sales_order`、`sample_order→sample`、`purchase→purchase_order`、`production→production_order`），与后端 `canonical()` 同表。

## 4. 五类业务页面接入方案

统一改法（每个页面只动附件区块，不动业务逻辑）：

```vue
<!-- 替换前（样例：OrderForm.vue:289-307） -->
<AttachmentPanel ... /> <AttachmentUploader ... /> <el-button>上传客供资料</el-button> <CustomerDocUpload ... />

<!-- 替换后 -->
<AttachmentCenter
  source-type="sales_order" :source-id="form.orderId || null" :source-no="form.orderNo"
  :trace-id="(form as any)?.traceId"
  view-perm="sales:order:view" edit-perm="sales:order:edit"
  :locked="isOrderLocked" :accept="['.pdf','.doc','.docx','.xls','.xlsx','.jpg','.jpeg','.png']"
  @bound="onAttachmentChanged" @unbound="onAttachmentChanged" />
<el-button type="primary" @click="customerUploadVisible = true">上传客供资料</el-button>   <!-- 保留 -->
<CustomerDocUpload ... />                                                                <!-- 保留 -->
```

| 页面 | 具体改造 | 保留 | 删除 |
|---|---|---|---|
| 询价单 `views/sales/inquiry/index.vue`、`components/InquiryDetailDialog.vue` | 两个 `AttachmentPanel` → `AttachmentCenter`（详情弹窗用 `mode="readonly"`） | `CustomerDocUpload` | — |
| 报价单 `views/sales/quotation/components/QuotationFormDialog.vue`、`index.vue` | `index.vue:173` 的旧 `AttachmentUploadDialog` → `AttachmentCenter`（`mode="dialog"`） | `CustomerDocUpload`、`ProductFileLibrary` | `index.back`（备份文件，WP5 复核删除） |
| 销售订单 `components/OrderForm.vue`、`OrderDetailDrawer.vue`、`ReviewDialog.vue`、`GeneratePlanDialog.vue`、`index.vue` | `OrderForm` 的三件（Panel+Uploader+按钮）→ `AttachmentCenter`；`ReviewDialog`/`GeneratePlanDialog` 的 `AttachmentUploader` → `AttachmentCenter mode="upload-only"`；`OrderDetailDrawer` 只读；`index.vue:251` 旧弹窗淘汰 | `CustomerDocUpload` | 旧 `AttachmentUploadDialog` 引用 |
| 样品单 `views/sales/sample-order/index.vue` | `AttachmentPanel` → `AttachmentCenter` | `CustomerDocUpload` | — |
| 产品 `views/product/list/components/ProductDetail.vue`、`film/index.vue` | `ProductFileLibrary` 保持；产品维度客供资料由 `CustomerDocPanel` 改用 `refsByProduct()` 取数 | `ProductFileLibrary`、菲林上传 | `ProductDetail.vue:291` 直接拼 `downloadUrl`（改走组件内封装） |

### 4.2 新建单据的接入（旧 `flushPending` 的替代）

`sourceId=null` 时公共组件只做 TEMP 上传（附件已在服务端有 7 天归属，不再是浏览器里的 `File`）；保存成功后父组件调 `attachmentRef.value.bindPending(savedId)` 一次性绑定全部 TEMP 附件（异常路径见 §2.4）。失败重试用 `requestKey`（同键同内容幂等），**不会产生错误的业务引用**——绑定失败时附件停在 TEMP，保留期由 V1-A 后端管理。

### 4.3 历史附件合并读取规则（修订 3，替换 v1 的"按 id 去重"）

v1 写的"refs ∪ `attachment/list` 按 id 去重"是错的：`refId` 与 `attachmentId` 是两种主键，按 `attachmentId` 去重会把**同一附件的多条引用**（不同来源/不同产品上下文/不同资料类型）压成一条，丢掉引用级元数据。正确规则：

1. **两段列表分开取、分开渲染**：
   - A 段（引用态）：`GET /refs?sourceType&sourceId` → 主键 `refId`，逐条展示 `documentType/displayName/remark/sortOrder/来源单号`，操作=下载/预览（`/refs/{refId}/...`）/编辑元数据/解绑；
   - B 段（历史态）：`GET /attachment/list?bizType&bizId` → 主键 `attachmentId`，只展示文件本身信息，标注「历史附件（未建立引用）」，操作=下载/预览（`/attachment/download/{id}`），**不出解绑按钮**。
2. **去重只在同一段内**：A 段内按 `refId` 去重；B 段内按 `attachmentId` 去重。B 段中 `attachmentId` 若已出现在 A 段，**不是"去重"而是"降级"**：该行标为「已建立引用」并只保留只读展示（避免同一条文件出现两个可操作入口）。
3. **同一 `attachmentId` 的多条引用全部保留**（例如同一份图纸分别挂在 inquiry:1 与 product:1）——它们是不同业务上下文，各自可独立解绑。
4. **禁止自动转换历史状态**：前端不得把 B 段行标成 `BOUND`、不得为它们自动建引用、不得触发任何写接口；`upload_state` 只读展示（`LEGACY` 显示为「历史附件」）。
5. 两段都失败时给统一错误态；只有 A 段失败（新单据尚未回填引用）时仍展示 B 段，反之同理。

### 4.4 五类页面的保存流程核查（修订 7）

实测（只读）各页"保存/提交 → 附件收尾"的耦合点，接入时**只替换附件区块，不动这些钩子**：

| 页面 | 保存链路（真实行号） | 与附件的耦合 | 接入注意 |
|---|---|---|---|
| 询价单 | `views/sales/inquiry/index.vue:969 submitForm()` | `:977`/`:984` 先 `customerDocUploadRef.flushPending(id)`，返回 `false` 直接 return；`:1003` 保存后 `reset()` | 客供资料钩子**保留**；公共组件的 `bindPending` 应在同一位置调用，且**失败即中断保存**（沿用现有语义） |
| 报价单 | `components/QuotationFormDialog.vue:617 handleSubmit()` | `:378 flushCustomerDocs(id, traceId)`；`:664 reset()` | 同上；`ProductFileLibrary` 不参与该链路 |
| 销售订单 | `components/OrderForm.vue` 保存流程 | `:910`/`:919`/`:964`/`:965` 三处收尾（旧 `uploaderRef.flushPending` + 客供 `flushPending`，后者 false 中断）；`:982 reset()` | 旧 `uploaderRef` 的 flush 由公共组件 `bindPending` 取代，**调用顺序与"false 中断"语义必须保持一致** |
| 样品单 | `views/sales/sample-order/index.vue:1027 submitCreate()`、`:1539 handleSubmitRequest()` | **未发现** `flushPending` 调用；`:376` 的 `CustomerDocUpload` 也没绑 `ref`（无 `customerDocUploadRef`） | 现存缺口：新建路径若确认"待上传"，组件内暂存行没有保存钩子收尾。接入公共组件时用 `bindPending` 一并补齐（属 V1-B 试点页整改项，不单独动该组件） |
| 产品 | `views/product/list/components/ProductDetail.vue` | 无保存耦合（纯 `ProductFileLibrary` 展示/管理） | 产品页只做只读/管理入口，不涉及单据保存 |

**结论**：统一组件只能替换"附件区块"，不得侵入表单校验与保存事务；`bindPending` 必须在原有 flush 位置调用并沿用"失败中断"语义。


## 5. 交互流程

### 5.1 上传（已有单据）

```
选文件 → 前端校验(大小/扩展名) → 生成 requestKey(sourceType+单据号+文件名+大小+时间戳的哈希, 16~100位)
→ POST /refs/temp → 拿 attachmentId(state=TEMP, 7 天)
→ POST /refs 绑定(sourceType, sourceId, documentType?, displayName?, remark?)
→ 成功：TEMP→BOUND、列表刷新、emit bound
        →（可选）按当前范围查 by-sha256，命中则提示"本单据内已有 N 份相同内容"（只提示不阻断）
→ 失败：附件停 TEMP（不产生引用）→ 入待绑定队列，toast + 可"重试绑定"（幂等，见 §2.4）
```

注：**不在上传后读 `X-Attachment-Duplicate-Count`**——`POST /refs/temp` 不返回该响应头（只有旧 `upload`/`batch-upload`/`upload-product` 设置），这是本轮收敛后的明确结论（修订 4）。

### 5.2 上传（新建单据，`autoBind=false`）

```
同上上传拿 attachmentId（TEMP）→ 页面显示"待绑定(N)"
→ 保存单据成功 → bindPending(savedId) → 逐个 POST /refs → 全部成功才清空待绑定列表
→ 单据保存失败：待绑定附件保留 TEMP（7 天）→ 用户可在弹窗里"移除待绑定"或再次保存后重试
```

### 5.3 绑定 / 解绑 / 元数据

| 动作 | 前端 | 后端（最终裁决） |
|---|---|---|
| 绑定 | `POST /refs`；重复绑定同来源同附件返回原 `refId`（幂等，已实测） | 校验来源 `:edit` 权限 + 来源单据存在 + 附件可读 + 分类字典有效 |
| 解绑 | `DELETE /refs/{refId}`，二次确认弹窗写明"仅解除本单据的引用，物理文件保留" | 校验 `:edit`；受控/历史硬引用 → 抛「禁止解除」（前端把该错误原样展示，不做兜底绕过） |
| 元数据 | `PUT /refs/{refId}`（分类下拉取 `/refs/document-types`，仅列出 `ext_data.sources` 含本 sourceType 的二级类型） | 二级类型必须存在/启用、父分类存在、来源在 `sources` 内、`sortOrder≥0` |

### 5.4 重复检测（修订 4：只有一条可用通道）

| 通道 | 可用性 | 结论 |
|---|---|---|
| `X-Attachment-Duplicate-Count` 响应头 | **仅**旧 `POST /attachment/upload`、`/batch-upload`、`/upload-product` 设置；`POST /refs/temp` **不设置** | 新流程（TEMP→绑定）**用不上**；历史/兼容路径仍可读 |
| `GET /refs/by-sha256?sha256&sourceType&sourceId` | 需要**业务范围**（已绑定/已保存的上下文） | **新流程的唯一可用通道**：绑定成功后查询并提示"本单据内已有 N 份相同内容" |
| `GET /attachment/by-sha256?sha256&bizType&bizId[&productId]` | 旧口径，同样需要范围 | 历史附件（B 段）路径可用 |

行为口径：
1. **只提示不阻断**（与后端一致：不做物理去重、不合并路径）；
2. 新建单据尚未绑定/未保存时**无法按范围检测**，此时不提示、也不猜测跨范围——这是能力边界，不是缺陷；
3. 前端不自行跨范围搜索（不做全库 sha256 扫描）。

**后端缺口（本轮只登记，不实施）**：若希望"上传即提示重复"，需后端在 `POST /refs/temp` 也设置 `X-Attachment-Duplicate-Count`（复用 `SysAttachmentController.addDuplicateHint` 的口径）。属后端改造，需单独立项拍板。

## 6. 权限与锁定状态处理

### 6.1 权限（三层，后端最终裁决）

| 层 | 做法 | 说明 |
|---|---|---|
| 页面可见 | 业务页已有的 `v-hasPermi` / 菜单权限 | 不变 |
| 组件提前禁用 | `AttachmentCenter` 按 `viewPerm`/`editPerm` 与 §3.4 映射，`hasPermission` 为假时隐藏上传/解绑/编辑按钮 | 只做体验优化（不让人点了才报错） |
| 后端裁决 | `AttachmentSourcePolicy.check()` 校验来源 `:view`/`:edit` + 来源行存在；`AttachmentAccessService.checkRead/checkUnlink` 校验附件级可见性与保护 | **任何前端绕过都会被拒**；前端必须原样展示后端错误文案（例：「受控、已发布或历史硬引用附件禁止解除」「业务来源不存在或不唯一」「无此权限：sales:inquiry:view」） |

### 6.2 locked / readonly：只做组件能力（修订 5）

本版**不定义业务锁定规则、不实施**。组件契约：

| prop | 语义 | 组件行为 |
|---|---|---|
| `readonly` | 纯展示 | 隐藏上传/解绑/编辑入口，保留列表/下载/预览/重复提示 |
| `locked` | 单据已锁定（由页面判定并传入） | 与 `readonly` 相同的写操作收敛，但**列表与元数据展示不变**；组件不解释"为什么锁定"、不显示锁定原因（由页面自己提示） |

**页面怎么算 `locked` 由业务决定**（本轮不实施）：候选口径是"单据进入已审核/已下发/已完成"，但**逐单据的状态取值未定**，且**后端目前没有对应的状态校验**——所以 V1-B 交付时 `locked` 默认 `false`，等于保持现状行为；等业务给出口径再逐页打开。

### 6.3 需要后端补充校验的缺口（只登记，不扩大改造）

前端 `locked`/权限只是体验层；以下缺口若要真正"锁住"，**必须后端补校验**，本轮只列出、不实施：

| # | 缺口 | 现状 | 建议（待拍板） |
|---|---|---|---|
| G1 | 已锁定单据仍可改附件 | 后端只校验来源 `:edit` 权限 + `protectedFile()`，**不读单据状态** | 在 `AttachmentRefService.create/unlink` 增加来源单据状态校验（需先定义"锁定状态"取值表） |
| G2 | `POST /refs/temp` 无重复提示头 | 只有旧上传端点设置 `X-Attachment-Duplicate-Count` | 复用 `addDuplicateHint` 口径补到 temp 端点 |
| G3 | TEMP 附件无法按用户列出 | 无"我的临时附件"查询端点（`cleanup-preview` 是 admin 全局预演） | 可选：`GET /refs/temp/mine`，或维持前端 `localStorage` 清单（本版采用后者，见 §2.4） |
| G4 | 元数据编辑不区分"锁定后是否允许" | `PUT /refs/{refId}` 只校验 `:edit` | 若锁定口径含"禁止改元数据"，需后端同步（建议**允许**改元数据，避免审核后连备注都改不了） |
| G5 | 来源别名归一仅后端有 | 前端 §3.4 需自己归一（`order`→`sales_order` 等） | 长期：把映射表下沉为后端返回（`/refs/document-types` 已是字典先例），本轮前端硬编码同表即可 |

**原则**：V1-B 不擅自扩大后端改造；上表任一项若要做，单独立项 + 拍板。

## 7. 短 Work Package（修订 8）

原则：每包**可在一次交付内完成并自验**；先"数据通道 + 组件"，再**只选 1 个试点页**验证，通过后才逐页推广。

| WP | 范围 | 产出（可验证） | 前置 |
|---|---|---|---|
| **WP1** | `api/system/attachmentRef.ts` + `composables/useAttachmentRefs.ts`（含 `requestKey` 生成、`localStorage` 待绑定队列、错误文案映射、状态标记） | 纯 TS，无 UI；用一次性调试入口（临时页或 `console` 脚本）跑通 temp→bind→unbind→by-sha256→document-types 五步 | 无（V1-A 已 GO） |
| **WP2** | `components/attachment/AttachmentCenter.vue` + `AttachmentTempUploader.vue`（四态 + 待绑定区 + 元数据区块 + 重复提示区块，**先不拆子文件**） | 组件可独立挂载；用一个临时页面挂 `sourceType="inquiry" :sourceId="1"` 人工点通全流程 | WP1 |
| **WP3（试点页）** | **只接 1 页：销售订单 `components/OrderForm.vue`**（含 `ReviewDialog`/`GeneratePlanDialog` 的 upload-only 替换） | 页面附件区全走公共组件；保存链路 `bindPending` 与"false 中断"语义与原实现一致（§4.4）；**数据库取证**：绑定后 `sys_attachment_ref` 新增行、附件 `upload_state` TEMP→BOUND | WP2 + §9 拍板 1/2 |
| **WP4** | 试点页验收通过后**逐页推广**：询价 → 报价 → 样品（顺带修 §4.4 的样品单收尾缺口）→ 产品/客供资料面板 | 每页一次小提交，逐页自验 | WP3 通过 |
| **WP5** | 收口：旧件退役（`AttachmentUploadDialog` 删除、`AttachmentUploader` 删除、`TraceAttachmentPanel` 死件复核）、`views/system/file/index.vue` 隐藏已禁用的物理删除入口、文档/`components.d.ts`/门禁 | `vue-tsc --noEmit` + `check:status-enums` + `check:docs` 通过 | WP4 |

选销售订单做试点的理由：它有**最全的附件组合**（通用上传 + 客供资料 + 旧弹窗 + 两处 upload-only 复用 + 新建/编辑/详情三态），且保存链路里已经有 `flushPending`/"false 中断"语义，最适合验证"不破坏原有保存逻辑"。

## 8. 推荐的第一批 Codex 实施范围（本轮之后）

**只做 WP1 + WP2，不碰任何业务页面**（页面试点留到 WP3，由人验收后再放行）。

| 项 | 内容 |
|---|---|
| 新增 | `jjx-web/src/api/system/attachmentRef.ts`（§3.1 十个方法，全部对齐真实端点，不新增后端接口） |
| 新增 | `jjx-web/src/composables/useAttachmentRefs.ts`（含 §2.4 待绑定队列与 `localStorage` 键约定） |
| 新增 | `jjx-web/src/components/attachment/AttachmentCenter.vue`、`AttachmentTempUploader.vue` |
| 允许 | 临时调试页（放在 `.tmp/` 或本地不入库；或复用现有页面加 `?attachDemo=1` 开关，**不得改动业务逻辑**） |
| 禁止 | 改任何业务页面/后端/数据库/迁移；改 `attachmentApi` 既有方法签名；引入新依赖；动 `dev-20261012-011` 涉及的数据 |
| 自验要求 | `vue-tsc --noEmit` 通过；`npm run check:status-enums` 通过（若新增状态字面量必须走枚举）；组件能对 `inquiry:1` 跑通 temp→bind→unbind；`grep` 确认无 `biz_type` 字面量兜底写入 |
| 交付形态 | 一个 commit（任务码 `dev-20261012-002`），只含上述 4 个新文件（+ 可选调试页） |

Codex 委托注意事项（沿用仓库既有经验）：**必须先给它这份文档路径**（避免自造接口）、**明确"不得修改业务页面"**、交付后由我用 `git show --name-only` + `vue-tsc` + 实测接口复核。


## 9. 风险、遗留项与验收标准

### 9.1 风险

| 风险 | 说明 | 应对 |
|---|---|---|
| 历史单据双通道读取 | 未回填引用的历史附件只在 `biz_type/biz_id` 上；若只读 refs，老单据附件会"消失" | 按 §4.3 的**两段式**读取（refId 段 / attachmentId 段），不按 `attachmentId` 跨段去重；LEGACY 行标注「历史附件」且不出解绑按钮 |
| TEMP 待绑定队列丢失 | 队列在浏览器内存/`localStorage`，清缓存会丢 | 附件本身在服务端有 7 天归属，且同 `requestKey` 重传幂等（返回原 id）→ 最坏情况用户重新选同一个文件即可找回同一 `attachmentId`（§2.4） |
| 重复检测在新建单据场景受限 | `by-sha256` 需要业务范围 | 只提示不阻断；作为能力边界写进验收（§5.4） |
| 解绑误伤 | 用户以为"删附件"其实只解绑 | 二次确认明确文案 + 解绑后附件仍可从其他单据/产品库看到 |
| 状态锁定未实施 | `locked` 只是能力，后端无状态校验（G1） | 交付时 `locked=false`（保持现状）；不宣称"已锁定单据不可改附件" |
| 客供资料与通用附件并存 | `CustomerDocUpload` 走 `customer-doc-upload`（带产品/资料类型/整理名） | **专用职责保留，不改造成通用上传**；两处列表都从 refs 读，避免两套展示口径 |
| 集成破坏保存逻辑 | 五页保存链路已有 `flushPending`/"false 中断"语义 | 按 §4.4：只替换附件区块，`bindPending` 在同一位置调用并沿用原语义 |
| 并发（多标签页/多人） | 绑定/解绑靠后端行锁 + 唯一键 | 前端失败即提示重试，不做本地乐观去重 |

### 9.2 遗留项（不在 V1-B）

1. `quality_template_registry` 19 条异常引用与 `protectedFile()` 职责边界 → **dev-20261012-011**（V1-B 不夹带）。
2. `sales_order` / `sample` 两类来源缺测试单据，后端正向验证未完成 → 影响 WP3 的联调样本，需造单据。
3. 测试附件（14~33）与孤儿文件未清理（dev-20261012-002 收口遗留）。
4. 并发绑定/解绑、双用户越权细项、TEMP 跨用户与过期边界未验证 → 建议随 WP3 联调补。
5. §6.3 的 G1~G5 后端缺口（单据状态校验、temp 端点重复头、我的 TEMP 列表、元数据锁定口径、别名映射下沉）。
6. §4.4 样品单"待上传无收尾"的现存缺口（随 WP4 修）。

### 9.3 验收标准（V1-B 完成判据）

1. 五类页面附件区全部由 `AttachmentCenter` 承载，页面上不再出现 `AttachmentUploader`/`AttachmentUploadDialog`/自拼 `el-upload` 的附件上传（§7 WP4/WP5）。
2. 上传走 TEMP→绑定；**数据库校验**：绑定后 `sys_attachment_ref` 新增行、附件 `upload_state` 由 TEMP 转 BOUND；历史行保持 LEGACY **不被前端改写**——用 SQL 取证，不只看界面。
3. 解绑调用 `DELETE /refs/{refId}`，二次确认文案含"物理文件保留"；受控/硬引用被拒时原样展示后端文案。
4. 列表按 §4.3 做两段式渲染：A 段以 `refId` 为键（可编辑/解绑），B 段以 `attachmentId` 为键（只读、「历史附件」标注、无解绑）；**同一 `attachmentId` 的多条引用都出现**。
5. TEMP 待绑定：关闭弹窗/保存失败/部分成功/重复提交/刷新重开 五个场景行为与 §2.4 一致；`localStorage` 只存 id 清单，不存 `File`。
6. 权限：无 `:edit` 权限的账号看不到上传/解绑入口；直接调接口被后端拒绝（前端不做越权兜底）。
7. `locked`/`readonly` 按 §6.2 只是能力位，**不宣称已实现业务锁定**；`locked` 打开时写操作隐藏、查看下载不受影响。
8. 重复提示：绑定后按范围提示同内容份数，**不阻断**上传；新建未绑定阶段不提示（能力边界，§5.4）。
9. 专用组件职责未变：`CustomerDocUpload`/`CustomerDocPanel`/`ProductFileLibrary` 仍是各自入口，未被改成通用上传。
10. 门禁：`vue-tsc --noEmit`、`npm run check:status-enums`、`npm run check:docs` 通过；无全量测试要求。
11. 保存链路未被破坏：§4.4 五页的 `flushPending`/"false 中断"语义保持（试点页需人工走一遍新建→保存→附件已绑定）。

### 9.4 需拍板项（开工前）

1. **试点页确认**：同意用销售订单 `OrderForm.vue` 做 WP3 试点（理由见 §7）。
2. **旧件处置**：`AttachmentUploader` / `AttachmentUploadDialog` 是否随 WP5 删除（推荐删除，避免两套上传）。
3. **后端缺口是否立项**：§6.3 的 G1（单据状态校验）与 G2（temp 端点重复头）是否在 V1-B 之后单独立项（本轮不实施）。



