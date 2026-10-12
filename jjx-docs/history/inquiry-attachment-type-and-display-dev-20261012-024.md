# 询价单附件「可选类型 / 看类型 / 入口口径」（dev-20261012-024）—— 实施记录

> 任务码：dev-20261012-024（P2）｜ 日期：2026-10-12 ｜ 状态：**已实施（运行时验收另计）**
> 说明：早前一轮因目标文件（AttachmentCenter.vue / useAttachmentRefs.ts）**未跟踪无法隔离**停在只读（见本文件历史版本）；**其后 043/034/027 已提交入 HEAD**，重做方清除了隔离障碍，本轮完成代码。

---

## 1. 改动清单（3 文件，`git diff --stat` 55+/12−）

### 1.1 `composables/useAttachmentRefs.ts`
- `PendingEntry` 加 `documentType?: string | null` / `displayName?: string | null`（**仅存编码/名，不存 File/Blob**）。
- `uploadTemp` 入队带 `documentType: null, displayName: null`；`restore()` 回读两字段。
- `bindPending()` 批量绑定改为**逐条优先 entry 自身类型**（`meta?.documentType ?? entry.documentType ?? null`，displayName 同）→ 保存时各行类型随 `document_type` 落库。

### 1.2 `components/attachment/AttachmentCenter.vue`
- 待绑定行新增**二级类型下拉**（选项 = `selectableTypes`；`@change` 即 `persist()` 持久化；clearable）。
- `docTypeLabel(code)` → **「一级｜二级」**：用 `/refs/document-types` 真实字典经 `documentType → 二级 item_key → ext_data.parent → 一级 item_key` 解析；**不硬编码中文**；未知二级回退原编码；空值 **「未分类」**（引用行 tag 由「有值才显示」改为**始终显示**，无值则「未分类」）。
- `displayNameOf(row)` → 展示层拼 **「二级类型 · 原名」**（如「客户图纸 · 客供稿.jpg」），**不写库**。
- A 段「分类/命名」入口：`EditPen` **图标按钮 → 文字按钮**「分类/命名」；移除未用的 `EditPen` 导入。
- 上传按钮文案：`mode==='upload-only' ? '上传附件' : '上传（通用附件）'`。
- `retryBind(entry)` 传 `entry` 的 `documentType/displayName`（手动重试也带类型）。

### 1.3 `views/sales/inquiry/index.vue:334`
客供按钮文案 `上传客供资料` → **`上传客供资料（需选产品）`**。

## 2. 口径落实（对照任务卡 3）

| 项 | 落实 |
|---|---|
| a) 待绑定行选类型→绑定时提交 | ✅ 逐条 entry.documentType → `bindPendingOne(meta)` → `sys_attachment_ref.document_type` |
| b) 「一级｜二级」用真实字典、停用项不出现、未知回退、空值未分类 | ✅ `docTypeLabel` + `selectableTypes`（**含 043 的 `is_active!==1` 过滤，未重复改**） |
| c) 展示名前缀「二级类型 · 原名」仅展示层 | ✅ `displayNameOf` |
| d) 双入口文案区分 | ✅ 通用「上传（通用附件）」/ 询价客供「上传客供资料（需选产品）」 |

## 3. 约束

- **043 的 `:241 is_active !== 1` 过滤保持原样**（未改、未回退）。
- **未回退** `useAttachmentRefs.ts`（在其最终形态上增量）；**不拆组件**；**不动** `bindPending` 语义（document-not-saved/partial/all-failed）；不动后端/字典 275/保存链路；`CustomerDocUpload` 本体不改。

## 4. 验证（真实输出）

- `npx vue-tsc --noEmit` → **EXIT=0**
- `npm run check:status-enums` → `状态魔法值门禁通过（存量基线 122 处，新增 0 处）` **EXIT=0**
- `npm run check:docs` → 通过

## 5. 验收（运行时，另计）

新建询价单上传 2 → 每行可下拉选类型（选项=按 sourceType 过滤的 active 二级）→ 保存后 SQL 取证 `document_type` 落值；重开/详情显示「一级｜二级」且展示名带类型前缀；未选类型历史行「未分类」不报错；**刷新后待绑定行的类型不丢**（restore 回读）；两上传按钮文案已区分。**（未跑，需运行环境）**

## 6. 未执行声明

未改后端/数据库；**未 commit/push**。
