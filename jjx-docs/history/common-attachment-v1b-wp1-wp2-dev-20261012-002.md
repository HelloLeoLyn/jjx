# 公共附件 V1-B · WP1+WP2 实施报告

- 任务码：dev-20261012-002
- 日期：2026-10-12
- 设计依据：`jjx-docs/history/common-attachment-v1b-frontend-design-dev-20261012-002.md`（v2 收敛版）§2/§3/§5/§7
- 性质：**只新增前端文件**；未改业务页面、未改后端/数据库/迁移、未删旧组件、未引入依赖、未 commit
- 交付边界：WP1（API + composable）+ WP2（公共组件）已完成；**WP3 试点未开始**

## 1. 修改文件清单

| 文件 | 行数 | 类型 | 说明 |
|---|---|---|---|
| `jjx-web/src/api/system/attachmentRef.ts` | 162 | 新增 | V1-A refs 接口封装（10 个方法，全部对应真实端点） |
| `jjx-web/src/composables/useAttachmentRefs.ts` | 345 | 新增 | 数据流 + 待绑定队列 + 部分成功结果 + 合并规则 |
| `jjx-web/src/components/attachment/AttachmentCenter.vue` | 477 | 新增 | 统一附件入口（panel/dialog/upload-only/readonly） |
| `jjx-web/src/components/attachment/AttachmentTempUploader.vue` | 95 | 新增 | 选文件 + 校验 + 向上抛 `File`（不自行上传） |
| `jjx-web/src/enums/system/AttachmentEnum.ts` | 62 | 新增 | 上传状态/来源类型枚举 + 来源→权限码映射 + 别名归一 |
| `jjx-web/src/enums/system/index.ts` | +6 行 | 修改 | 把上面两个枚举纳入统一导出（按仓库"状态必须用枚举成员"规则） |

**未提交**（按约束）：以上改动全部留在工作区。旧组件（`AttachmentUploader`/`AttachmentUploadDialog`）保持原样，未被引用改动、未被删除。

## 2. 关键流程说明

### 2.1 上传（TEMP → 绑定）

```
选文件(AttachmentTempUploader: 校验大小/扩展名) → emit files
→ AttachmentCenter.onFiles: useAttachmentRefs.uploadTemp(file)
   a) 生成本地稳定 requestKey（同草稿+同名+同大小+同 lastModified → 恒定，64 位十六进制，满足 16~100 位 [A-Za-z0-9_-]）
   b) 浏览器算 SHA-256（crypto.subtle；不可用则置 null，不阻断上传）
   c) POST /refs/temp → attachmentId（服务端 upload_state=TEMP，7 天）
   d) 入待绑定队列（localStorage 持久化）
→ sourceId 有值且 autoBind=true：立即 POST /refs 绑定 → TEMP 转 BOUND → 出队 → emit bound
→ 绑定成功后按范围 GET /refs/by-sha256（用步骤 b 的 sha256）→ 命中则提示"本单据内已有 N 份相同内容"
```
**不使用** `X-Attachment-Duplicate-Count`（`/refs/temp` 不返回该头，见设计方案 §5.4）。

### 2.2 待绑定队列与 localStorage 键（修正 1）

键：`attach-pending:v2:{userId}:{sourceType}:{draftId}`，其中 `draftId` 由调用方传入；未传时退化为 `id-{sourceId}` 或 `unsaved`。
三要素隔离：**userId**（不同用户不串）、**sourceType**（不同模块不串）、**draftId**（同一用户的多个新建单据不串）。
条目：`{attachmentId, requestKey, fileName, fileSize, sha256, state, error?, createdAt}`。
恢复：组件挂载时读回 id 清单（只做展示与重试，**不重传文件、不存 File/Blob**）；写入时机＝入队/出队/绑定失败。
「移除」只出队，不删服务端 TEMP 行（7 天保留期由后端管理）。

### 2.3 列表合并规则（修正 2，refId ≠ attachmentId）

- A 段（引用）：`GET /refs` → 主键 `refId`，**全部保留**（同一附件的多条引用是不同业务上下文）。
- B 段（历史）：`GET /attachment/list` → 主键 `attachmentId`；**仅隐藏**"在本单据已有有效引用"的那些 `attachmentId`（避免同一条文件出现两个入口）；其余历史行保留并标「历史附件」标签，只读（下载/预览），**无解绑按钮、不自动建引用、不改写 `upload_state`**。
- 前端从不把 B 段行标成 BOUND、不调用任何写接口。

### 2.4 部分成功结果（修正 3）

`bindPending()` 返回：

```ts
{ status: 'document-not-saved' | 'all-ok' | 'partial' | 'all-failed',
  bound:  { attachmentId, refId }[],
  failed: { attachmentId, fileName, message }[] }
```

- **单据未保存**（无 `sourceId`）→ `document-not-saved`，与"绑定失败"严格区分（调用方据此提示"请先保存单据"）；
- 逐条独立绑定：成功的出队、失败的留队并记 `error`；**不做整批回滚**（引用相互独立，回滚会删掉已成功的合法引用）；
- 组件 `AttachmentCenter` 暴露 `bindPending()` 供业务页在保存成功后调用，并在自身 `retryBind()` 上复用同一逻辑。

### 2.5 权限与锁定（修正 6）

`canView`/`canEdit` = 显式权限码（`viewPerm`/`editPerm`）优先，否则用来源类型映射（`product:view|edit`、`sales:inquiry:*` …）；`readonly`/`locked` 只收敛写操作（上传/解绑/编辑），**不定义业务规则、不显示锁定原因**；后端始终是最终裁决方，错误文案原样展示。

## 3. 七条必须修正的落实情况

| # | 修正要求 | 落实位置 | 状态 |
|---|---|---|---|
| 1 | localStorage 键含 userId+sourceType+draftId | `useAttachmentRefs.ts` `storageKey`（`attach-pending:v2:{userId}:{sourceType}:{draftId}`） | 已落实 |
| 2 | 同上下文已有 refId 的不再重复展示历史副本；不同 refId 全保留 | `refRows`（不过滤）/`legacyOnlyRows`（按 A 段 attachmentId 隐藏） | 已落实 |
| 3 | 区分"单据保存失败"与"绑定失败"，返回部分成功 | `BindResult.status = document-not-saved / all-ok / partial / all-failed` | 已落实 |
| 4 | 不假设重复响应头；绑定后按范围查 SHA-256 | `uploadTemp` 只读 `res.data`；`checkDuplicate` 走 `/refs/by-sha256` | 已落实 |
| 5 | 保留 LEGACY 兼容读取、不自动建引用、不改写历史状态 | B 段只读渲染 + `upload_state` 未在前端出现任何写路径 | 已落实 |
| 6 | locked/readonly 仅组件能力 | `readonly`/`locked` → `canEdit` 短路 | 已落实 |
| 7 | 保留 CustomerDocUpload / CustomerDocPanel / ProductFileLibrary 专用职责 | 未触碰这三个组件，也未在其他页面替换它们 | 已落实 |

## 4. 验证结果

| 验证项 | 命令 | 结果 |
|---|---|---|
| TypeScript 类型检查 | `npx vue-tsc --noEmit` | **通过（exit 0）** |
| API 与真实端点一致性 | 逐条比对 `SysAttachmentRefController` 的 mapping 与 `attachmentRef.ts` 的 url | **10/10 一致**（`/refs`、`/refs/product/{productId}`、`/refs/{refId}` PUT/DELETE、`/refs/{refId}/download`、`/refs/{refId}/preview`、`/refs/temp`、`/refs/by-sha256`、`/refs/document-types`、`/refs/cleanup-preview`） |
| 运行时接口形状复验（只读 + 1 次幂等探针） | 对 `127.0.0.1:8080` 实调 | ① `GET /refs?sourceType=sales_order&sourceId=1` → 200 `data:[]`；② `GET /refs/document-types` → 200 字典项；③ `GET /refs/by-sha256`（64 位）→ 200 返回 `refId/attachmentId/file_name/file_size/sha256`；④ `GET /refs/product/1` → 200；⑤ `POST /refs {attachmentId:24,sourceType:sales_order,sourceId:1}` → 200 `data:12`（幂等返回 refId，**探针已回滚**：`DELETE /refs/12` → deleted=1） |
| requestKey 约束 | 以同一算法在 node 复算 | 64 字符、`^[A-Za-z0-9_-]{16,100}$` 通过、同输入恒定（重传幂等）、内容变化即变键 |
| 状态枚举门禁 | `npm run check:status-enums` | **本次新增文件 0 违规**（门禁输出中无 `attachment/`、`attachmentRef`、`useAttachmentRefs` 命中）；**门禁整体 exit=1，原因见下（存量，不由本次引入）** |
| 组件运行时联调（浏览器点通） | — | **未验证**（本环境无浏览器自动化；不伪造 PASS）。见 §5-1 的替代证据与限制说明 |

**状态枚举门禁红的原因（已取证，非本次引入）**：门禁报 7 条"新增违规"集中在 `src/composables/usePrintFieldSuggest.ts`（2 条）与 `src/views/production/order/components/OrderBatchActions.vue`（5 条），这两个文件在 `git status --short` 下**均无改动**（＝内容等于 HEAD），且不在我本次新增文件之列；同时报"8 条存量违规已消失"，即这些行文本在某次提交里被改过而基线未同步。按 AGENTS.md「基线只许收窄、不许扩增」，修它属于改这两个文件的会话，本任务不动基线文件。

## 5. 已知问题

1. **组件运行时点击流未验证**：本环境无浏览器自动化，`AttachmentCenter` 的实际交互（el-upload 选文件、el-image-viewer 预览、localStorage 恢复）**未取到运行时证据**。已取得的替代证据：`vue-tsc --noEmit` 通过（模板/类型/属性层面可编译）、API 层 5 条实调通过、requestKey 算法离线复算通过。WP3 试点时由人工点通并回填证据；在此之前不得宣称"组件已联调通过"。
2. **`GET /refs` 不返回文件元数据**（无 `file_name`/`file_size`/`file_type`）：A 段只能靠本单据的历史列表回填文件名/大小，取不到时显示「附件 #id」。属设计方案 §6.3 的 G 类后端缺口（建议 refs 列表带文件元数据），本轮不实施。
3. **`AttachmentCenter.vue` 477 行 > 设计 §2.2 拆分阈值（约 300 行）**：本次任务明确"按实际复杂度实现 AttachmentTempUploader，不预先增加其他子组件"，故未拆。触发拆分条件（满足其一即拆出 `AttachmentMetaDialog.vue` / `AttachmentDuplicateTip.vue`）：被 ≥2 个页面独立引用、超过约 400 行、或元数据区需要独立权限。
4. **`npm run check:status-enums` 整体 exit=1（存量，非本次）**：会让 `npm run validate` 整体红，需对应会话把基线收窄；与 WP1/WP2 无关（已取证）。
5. **待绑定条目在 7 天后仍会显示**：服务端 TEMP 到期后条目不会自动消失（前端不做定时校验）；用户点"重试绑定"会拿到「附件不存在或已删除」，此时可"移除"。若后续要更干净，可在 WP3 加一次"挂载时按 id 校验"（需要后端提供按 id 查 TEMP 的能力，属 G 类缺口）。
6. 未做（非必须）：`components.d.ts` 由 IDE/工具生成，本次未手工改动。

## 6. WP3 试点建议（销售订单）

试点页：`jjx-web/src/views/sales/order/components/OrderForm.vue`（理由见设计方案 §7）。

| 步骤 | 动作 | 关键约束 |
|---|---|---|
| 1 | `:289-307` 的三件（`AttachmentPanel` + `AttachmentUploader` + 独立按钮）替换为一个 `AttachmentCenter`，传 `source-type="sales_order"`、`:source-id="form.orderId || null"`、`view-perm="sales:order:view"`、`edit-perm="sales:order:edit"`、`:draft-id="draftId"`、`:locked="false"`（锁定口径未拍板前保持 false） | **不动表单校验与保存事务** |
| 2 | 保存链路 `:910/:919/:964/:965` 的收尾改为调用 `attachmentCenterRef.value.bindPending()`；保留原"false 中断"语义：`document-not-saved` → 不中断但提示"请先保存单据"；`partial`/`all-failed` → 提示失败条目数并保留队列（不再静默丢弃） | 旧的 `uploaderRef.flushPending` 调用点删除前先确认已无其他引用 |
| 3 | `new` 路径：打开新建弹窗时生成一次 `draftId`（模块级 `ref` + 随机串），保存成功后清空；同一弹窗内多次保存复用同一 `draftId` | 避免不同新建单据共用队列 |
| 4 | `ReviewDialog.vue:56`、`GeneratePlanDialog.vue:20` 改为 `AttachmentCenter mode="upload-only"`；`OrderDetailDrawer.vue:293` 改为 `mode="readonly"` | 只换组件，不改各自业务动作 |
| 5 | 客供资料入口（`CustomerDocUpload` / `:305`）保持原样 | 专用职责不动 |

人工验收步骤（试点页通过判据）：

1. 新建订单 → 选 2 个文件 → 界面出现「待绑定 2」（此时不得出现任何 `sys_attachment_ref` 行）；
2. 保存订单成功 → 期望：引用新增 2 行、附件 `upload_state` 由 `TEMP` 转 `BOUND`、`expires_at` 清空；待绑定队列清空；
3. 重新打开该订单 → A 段出现 2 条（主键 refId）；历史附件（若有）在 B 段且无解绑按钮；
4. 解绑其中一条 → 该 ref `deleted=1`、`deleted_at` 落值；若是新上传件则附件转 `ORPHAN`、`expires_at=+30天`（历史件保持 `LEGACY`）；
5. 再上传一个与已有附件相同内容的文件 → 绑定后出现"本单据内已有 N 份相同内容"提示，且**未阻断**上传；
6. 无 `sales:order:edit` 的账号登录 → 看不到上传/解绑入口；直接调 `POST /refs` 被后端拒绝。

数据库取证 SQL（可直接复跑）：

```sql
SELECT id,attachment_id,source_type,source_id,deleted,deleted_at FROM sys_attachment_ref ORDER BY id DESC LIMIT 5;
SELECT id,upload_state,expires_at,biz_type FROM sys_attachment WHERE id = <新上传的 attachmentId>;
```

## 7. 遗留与下一步

1. 等待人工验收 WP1/WP2（尤其 §5-1 的组件点通）；通过后再按 §6 做 WP3 试点。
2. `sales_order`/`sample` 缺测试单据（影响 §6 步骤 1-6 的样本），需造一张销售订单。
3. 后端缺口（设计方案 §6.3 G1~G5）与 `dev-20261012-011`（质量模板历史引用）均不在本任务范围。
4. 本任务改动未提交（按约束）；提交时只含 §1 的 6 个文件。

## 8. 补缺轮（WP1+WP2 收尾，2026-10-12）

### 8.1 本轮修改文件

| 文件 | 类型 | 改动 |
|---|---|---|
| `jjx-server/.../system/domain/entity/SysAttachmentRef.java` | 后端 | 新增 3 个 `@TableField(exist=false)` 只读字段：`fileName`/`fileSize`/`fileType`（非落库列） |
| `jjx-server/.../system/service/AttachmentRefService.java` | 后端 | `list()`/`product()` 返回前调用新增私有方法 `fillFileMetadata()`：一条 `IN` 查询批量回填上述三字段（含软删附件）；其余逻辑不变 |
| `jjx-web/src/api/system/attachmentRef.ts` | 前端 | `AttachmentRefItem` 增加 `fileName/fileSize/fileType` |
| `jjx-web/src/composables/useAttachmentRefs.ts` | 前端 | ① `draftId` 强制（去掉 `unsaved` 退化，缺失即拒绝上传并给明确文案）；② `storageKey` 缺标识时为空、`persist/restore` 直接跳过；③ 队列按 `attachmentId` 去重（幂等重传不重复入队）；④ 暴露 `draftReady` |
| `jjx-web/src/components/attachment/AttachmentCenter.vue` | 前端 | A 段改为**直接消费 refs 元数据**（删除原先靠 legacy 列表回填的 `fileIndex/fileInfo`）；`sourceId=null` 且无 `draftId` 时禁用上传按钮并显示告警 |

未改：五类业务页面、B 段历史兼容逻辑、`CustomerDocUpload`/`CustomerDocPanel`/`ProductFileLibrary`、数据库结构、迁移、`dev-20261012-011`。

### 8.2 修正 1：`sourceId=null` 必须有唯一 `draftId`

- 键：`attach-pending:v2:{userId}:{sourceType}:{draftKey}`，其中 `draftKey = sourceId ? 'id-{sourceId}' : draftId`；
- `draftReady = !!sourceId || !!draftId`；**缺失时**：上传按钮禁用 + 组件内告警 + `uploadTemp()` 抛
  「缺少草稿标识（draftId）：新建单据必须提供唯一 draftId 才能上传附件，避免多个单据共用待绑定队列」；
- 无标识时**完全跳过 localStorage 读写**（不产生 `…:unsaved` 这类共享键）。

### 8.3 修正 2/3：refs 返回文件元数据（后端补链 → 前端直读）

- 现状取证（改前实调）：`GET /refs` 返回体只有引用字段，无文件名/大小/类型；
- 后端补链：`fillFileMetadata()` 只读一条 `SELECT id,file_name,file_size,file_type FROM sys_attachment WHERE id IN (…)`，**不改引用模型、不改权限判定、不写数据**；`product()` 同样回填；
- 前端：A 段文件名 = `displayName || fileName || 附件 #id`，大小来自 `fileSize`，图片/预览判定改用 `fileType`+`fileName`；B 段仍按 `attachment/list` 的历史兼容规则读取（未变）。

### 8.4 修正 4：requestKey 幂等语义复核（实证）

| 场景 | 实测 | 结论 |
|---|---|---|
| 同键 + 同内容（重试） | `POST /refs/temp` 两次 → 均返回 **34** | 重试复用同一附件，不产生重复 TEMP 行 ✅ |
| 同键 + 异内容 | → `同一requestKey不能上传不同文件`（拒绝） | 键与内容绑定，不会张冠李戴 ✅ |
| 异键 + 同内容 | → 新附件 **35** | 不同上传意图 = 不同附件 ✅ |
| 同草稿 + 同文件（离线复算） | 键恒定（`e6fa7c08…`） | 保证重试幂等 ✅ |
| 异草稿 + 同文件 | 键不同（`1a96f2e4…`） | 两个新建单据不共用键 ✅ |
| 同草稿 + 内容变化 | 键不同（`fac65104…`） | 内容变化即换键 ✅ |

前端配套：服务端因幂等返回原 id 时，队列按 `attachmentId` 去重（不再出现两条同 id 条目）。
说明：同一草稿内用户"故意再传同一文件"会拿回同一 `attachmentId`（服务端语义如此），表现为"仍是那一份附件"，这是期望行为而非缺陷。

### 8.5 本轮验证结果

| 验证项 | 命令/方式 | 结果 |
|---|---|---|
| 后端离线编译 | `mvn -f jjx-server/pom.xml -o -Dmaven.test.skip=true compile -q` | 通过（exit 0） |
| 前端类型检查 | `npx vue-tsc --noEmit` | 通过（exit 0） |
| 回填 SQL 等价复现（只读） | 对 refs 指向的附件执行同一 `IN` 查询 | 4/4 命中（`客供稿.jpg` 186991B image/jpeg 等）；软删件（id=21）同样可回填 |
| 接口形状 | 探针同 §4 | `/refs/temp` 幂等与拒绝语义见 §8.4 |
| **运行时新字段** | 需重新打包 + 重启 | **未验证 —— 阻塞见 §8.6** |

### 8.6 剩余阻塞

1. **需人工重新打包并重启后端**（本会话不启停服务）：`mvn -f jjx-server/pom.xml -o -Dmaven.test.skip=true package -DskipTests` → 重启 jar。重启后 A 段才会收到 `fileName/fileSize/fileType`；重启前的旧进程会让 A 段回退成「附件 #id」。
2. 本轮探针产生 2 个 TEMP 测试附件（**34、35**，`upload_owner=1`），随既有测试数据一并待清理。
3. `npm run check:status-enums` 存量红（§5-4）仍会挡住 `npm run validate`，与本任务无关。
4. 组件浏览器点击流仍未验证（§5-1），需 WP3 人工点通。
5. 未提交：本轮 5 个文件改动仍在工作区。

## 9. 最终运行时验收（人工重启后，只读）

| # | 验收项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 运行中的 jar 含最新 `AttachmentRefService` 修改 | **PASS** | 进程启动 `15:57:29` > jar `15:57:28` > 源码 `15:51:59`；从运行 jar 解出的 class 含 `SELECT id,file_name,file_size,file_type FROM sys_attachment` 与字段名 `fileName/fileSize/fileType` |
| 2 | `GET /refs` 返回 `fileName/fileSize/fileType` 且与库一致 | **PASS** | `GET /refs?sourceType=inquiry&sourceId=1` → 2 条：ref1/att12 `客供稿.jpg`·186991·`image/jpeg`；ref10/att32 `v1a-A.png`·70·`image/png`；`SELECT … FROM sys_attachment WHERE id IN (12,32)` 逐字段一致 |
| 3 | `GET /refs/product/{productId}` 同样返回元数据 | **PASS** | `GET /refs/product/1` → 1 条：ref6/att24 `v1a-A.png`·70·`image/png`；与库中 id=24 一致 |
| 4 | 无 sourceId 且无 draftId 时禁止 TEMP 上传、不用共享 unsaved 键 | **PASS（静态）** | `useAttachmentRefs.ts:109/110/112/137/146/230`：`draftReady=false` → `storageKey=''`、`persist/restore` 直接 return、`uploadTemp` 抛「缺少草稿标识（draftId）…」；`AttachmentCenter.vue:14/18/25`：上传按钮 `:disabled="!draftReady"` + 告警 + 提示语；全仓 grep 仅剩常量 `attach-pending:v2`，**无 `unsaved` 键字面量**。浏览器点击流仍未验证（无自动化环境） |
| 5 | requestKey 碰撞风险分析（只分析，未重构） | **结论：低风险且 fail-closed**（详见下） | 见 §9.1 |
| 6 | 未破坏旧附件 API / 历史兼容展示 / 权限逻辑 | **PASS** | 见 §9.2 |

### 9.1 第 5 项：requestKey 同形不同内容的碰撞分析

- **算法事实**：键 = `hash(draftKey | fileName | fileSize | lastModified)`，**不含内容**。离线复算：同名（`报价单.pdf`）、同大小（102400）、同 `lastModified` 的两个不同内容文件 → 键完全相同（`8f93614c…`）。
- **后果（上轮已实证）**：同键 + 异内容会被服务端**拒绝**（`同一requestKey不能上传不同文件`），即"第二次上传报错"，**不会**把 B 文件错当成 A 文件、不会产生错误引用或错误覆盖 → 属 fail-closed，**数据安全**。
- **触发条件**：同一草稿内，两个文件**名称、字节数、修改时间三者全同而内容不同**（例如把文件覆盖成同大小的另一版、或从别处拷贝保留原 mtime）。概率低，但 UX 上会莫名被拒。
- **改进选项（本轮不实施，仅登记）**：
  1. **推荐**：键改为 `hash(draftKey | sha256)`（sha256 已在上传前算好）→ 同内容重试仍幂等、同形不同内容自然分开；代价是键不再包含文件名（可读性下降，但键本就是哈希）。
  2. 保留现算法，仅把错误文案补成可行动指引（"该文件与此前上传的同名同大小文件内容不同，请重命名后重试"）。
  3. 不推荐：加入随机 nonce —— 会破坏"同文件重试复用同一键"的幂等语义。

### 9.2 第 6 项：改动范围与回归（只读证据）

- **本轮实际写入仅 5 个文件**（mtime `15:51:23`~`15:53:48`）：`SysAttachmentRef.java`、`AttachmentRefService.java`、`attachmentRef.ts`、`useAttachmentRefs.ts`、`AttachmentCenter.vue`（+ `components.d.ts` 由工具自动登记）。
- **未触碰**：`SysAttachmentController`(10:02)、`AttachmentSourcePolicy`(09:58)、`AttachmentAccessService`(12:31，属此前 B 修复)、`api/system/attachment.ts`(09:49) —— 时间戳均早于本轮，且 `git status` 中它们的改动来自 V1-A 会话的在制品。
- **旧附件 API（只读复验）**：`/attachment/list?bizType=inquiry&bizId=1` → 200、14 条（首条 id=12 `客供稿.jpg` 186991 `uploadState=LEGACY`）；`/attachment/by-sha256`（旧口径）→ 200、11 条；`/attachment/download/12` → `http=200`、`bytes=186991`（与 `fileSize` 一致）。
- **历史兼容展示**：旧列表仍按 `biz_type/biz_id` 返回 `upload_state=LEGACY` 历史行，未被本次改动影响（前端 B 段仍走该接口）。
- **权限逻辑**：非管理员 `yuanhui` 读 `GET /refs?sourceType=inquiry&sourceId=1` → `无此权限：sales:inquiry:view`；`GET /refs/product/1` → `无此权限：product:view`；说明 refs 回填只加展示字段，**权限判定链路未变**。
- **回归口径说明**：本次未做写接口探针（提交/绑定/解绑），故"写入路径未受回填影响"由静态分析给出（回填仅 `list()/product()` 的返回前一步只读查询，不在 `create/unlink/update` 路径上）。

### 9.3 结论

1—4 项 **PASS**（第 4 项为静态通过，标注未验证的运行时部分）；第 5 项为**分析结论**（低风险、fail-closed，附改进建议未实施）；第 6 项 **PASS**。无 FAIL。剩余未验证项：组件浏览器点击流、`npm run check:status-enums` 存量红、测试数据（附件 14~35 等）清理。




