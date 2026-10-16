# 附件保存处理器（saveHandler）注入契约

- 任务码：dev-20261016-069
- 日期：2026-10-16
- 上游：dev-20261016-068（已固化 5 条保存契约语义 + 201 行测试）
- 下游：dev-20261016-071（报价页一次替换 + 落定，直接照抄本文 §4 示例）
- 代码真源：`jjx-web/src/composables/useAttachmentSession.ts`（本文行号以本卡提交为准；语义真源是 068 的测试用例）

---

## 0. 一句话

**调用方只做一件事：给 `useAttachmentSession` 传一个 `saveHandler`。**
传了 → 保存入口开放（运行时 `canSave=true`，类型上 `AttachmentSession<true>`）；
不传 → 不开放保存入口（运行时 `canSave=false` 且 `saveChanges`/`commit` 抛错，类型上 `AttachmentSession<false>`）。
会话只负责「攒差异 → 校验 → 调处理器 → 按『被接受』回填」，后端请求全部由 handler 负责。

---

## 1. 注入点（调用方唯一要写的东西）

注入面是一个对象字段，定义在 `AttachmentSaveInjection`（useAttachmentSession.ts:117）：

| 符号 | 位置 | 作用 |
| --- | --- | --- |
| `AttachmentSaveInjection` | :117 | options 的保存注入面；本卡定型的接口 |
| `saveHandler?: AttachmentSaveHandler` | :127 | **唯一注入点**；不传 = 不开放保存入口 |
| `UseAttachmentSessionOptions extends AttachmentSaveInjection` | :130 | 建会话的 options 类型 |
| `AttachmentChanges` | :68 | 交出去的变更集 `{ uploads, updates, total }` |
| `AttachmentSaveResult` | :75 | 收回来「已落定」清单 `{ acceptedUploads?, acceptedUpdates?, failed? }` |
| `AttachmentSaveHandler` | :84 | 处理器签名 `(changes) => Promise<AttachmentSaveResult> | AttachmentSaveResult` |
| `AttachmentSaveScope` | :98 | 保存范围 `{kind:'all'} | {kind:'row',refId} | {kind:'rows',refIds}` |
| `AttachmentValidation` | :92 | `validate()` 返回 `{ ok, issues }` |
| `AttachmentOwnership` / `orderOwnership` / `productOwnership` | :27 / :38 / :43 | 上传归属（单据级 / 产品级） |

处理器签名（照此实现即可，无需自定义其它形状）：

```ts
export type AttachmentSaveHandler = (
  changes: AttachmentChanges,
) => Promise<AttachmentSaveResult> | AttachmentSaveResult

export interface AttachmentChanges {
  uploads: PendingEntry[]        // 仅 scope.kind === 'all' 时非空
  updates: AttachmentMetaEdit[]  // { refId, documentType?, remark?, sortOrder? }
  total: number
}

export interface AttachmentSaveResult {
  acceptedUploads?: { attachmentId: number; refId: number }[] // 成功落定的上传：本地 id → 服务端 refId
  acceptedUpdates?: number[]                                  // 成功保存的元数据改动 refId
  failed?: { key: number; message: string }[]                 // 逐行失败（key = attachmentId 或 refId）
}
```

---

## 2. 类型层闸门（本卡重点：不只靠运行时布尔）

`useAttachmentSession`（useAttachmentSession.ts:488）是泛型函数，返回类型按**是否注入 `saveHandler`** 推导：

```ts
export type AttachmentSaveGate<O> = O extends { saveHandler: AttachmentSaveHandler } ? true : false  // :148

export type AttachmentSession<CanSave extends boolean = boolean> = ReturnType<typeof createAttachmentSession> & {
  canSave: ComputedRef<CanSave>   // :157
}
export type AttachmentSessionWithSaver = AttachmentSession<true>     // :165
export type AttachmentSessionWithoutSaver = AttachmentSession<false> // :167
```

结论（可以直接依赖）：

1. 传了 handler：`const session = useAttachmentSession({ ..., saveHandler })` → `session` 类型是 `AttachmentSession<true>`，`session.canSave.value` 类型即 `true`。
2. 没传 handler：→ `AttachmentSession<false>`，`session.canSave.value` 类型即 `false`（编译期就能看出「没有保存入口」）。
3. 公共组件（`AttachmentManager.vue` / `AttachmentUpload.vue`）继续用不带参数的 `AttachmentSession`（= `AttachmentSession<boolean>`），行为与类型都不变。
4. 若调用方把 options 声明成宽松的 `UseAttachmentSessionOptions`（`saveHandler?` 可选），闸门退化为 `boolean`（未知），此时以运行时 `canSave.value` 为准 —— 类型闸门只在 options 是**字面量/推断类型**时精确。

---

## 3. 行为边界（068 已固化的 5 条 + 互斥，本卡不改语义）

1. **范围三档**：`{kind:'row',refId}` = 当前行；`{kind:'rows',refIds}` = 勾选行的改动；`{kind:'all'}` = 完整会话（含被筛选隐藏、未加载进 `refs` 的行）。
   - **`uploads` 只在 `all` 出现**；`row`/`rows` 的 `uploads` 恒为空数组。
   - **空勾选（`rows:[]`）≠ 全量**：`total=0`，不提交任何改动。
2. **入口闸**：无 `saveHandler` ⇒ `canSave=false`，且 `saveChanges`/`commit` **抛错**（`未配置保存处理器…`），不静默成功；实现见 :354 / :375。
3. **校验先行**：`commit(scope)` 先 `getChanges`（:301）再 `validate`（:313）；校验不过 ⇒ **不调用处理器**、`result=null`，队列与差异原样保留。
4. **失败不假成功**：处理器抛错 → 向上抛（调用方提示），差异全保留；处理器返回但某项**不在 `acceptedUploads`/`acceptedUpdates` 里** → 该项照样保留（`acceptSaved` :338）。只有被接受项才清差异并回填。
5. **提交期间锁定**：`saving=true`，被提交的 `updates` 行进 `savingRefIds`；结束（正常/抛错）必须复位。
6. **与移除操作互斥（071 需在 UI 层落实）**：会话运行时不主动拦截 `removePending`（:407）/`unbind`（:417）。所以：
   - 保存进行中（`session.saving.value === true`）禁止移除；
   - 某行在保存中（`session.savingRefIds.value.has(refId)`）该行的移除按钮禁用（`AttachmentManager` 的表格就是用它来 `disabled` 的）。

---

## 4. 071 照抄示例（报价页：建会话 + 注入 handler + 保存按钮 + 互斥）

> 说明：`quotationApi.saveAttachments` 是**占位**，请替换成 071 的真实落定接口/字段名；除这一行外其余可原样照抄。

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  useAttachmentSession,
  productOwnership,
  type AttachmentChanges,
  type AttachmentSaveResult,
} from '@/composables/useAttachmentSession'
import { quotationApi } from '@/api/sales/quotation' // 占位：换成真实落定接口
import AttachmentManager from '@/components/attachment/AttachmentManager.vue'
import AttachmentUpload from '@/components/attachment/AttachmentUpload.vue'

const props = defineProps<{
  quotationId: number | null
  quotationNo?: string
  draftId: string
  canEdit: boolean
}>()

// 1) 建会话：每个单据实例一次；注入 saveHandler ⇒ 类型为 AttachmentSession<true>，保存入口开放
const session = useAttachmentSession({
  sourceType: 'quotation',
  sourceId: computed(() => props.quotationId),
  editPerm: 'sales:quotation:attachment',
  draftId: computed(() => props.draftId),
  saveHandler: async (changes: AttachmentChanges): Promise<AttachmentSaveResult> => {
    // 2) 唯一落定点：交给后端，返回「已被接受」的项；不能只 emit 不返回结果
    const res = await quotationApi.saveAttachments({
      quotationId: props.quotationId,
      uploads: changes.uploads.map((u) => ({ attachmentId: u.attachmentId, documentType: u.documentType, ownershipKey: u.ownershipKey })),
      updates: changes.updates,
    })
    return {
      acceptedUploads: res.acceptedUploads ?? [],
      acceptedUpdates: res.acceptedUpdates ?? [],
      failed: res.failed ?? [],
    }
  },
})

// 3) 保存全部：commit 校验先行；校验不过 result=null（不调处理器），失败抛错则差异保留
async function onSaveAll() {
  if (!session.canSave.value) return // 无 handler 时这里恒 false（类型上也判得出来）
  const out = await session.commit({ kind: 'all' })
  if (out.result === null) {
    ElMessage.warning(out.validation.issues[0]?.message || '存在未完成的改动')
    return
  }
  const failed = out.result.failed?.length ?? 0
  if (failed) ElMessage.warning(`已保存 ${out.changes.total - failed} 条，${failed} 条失败（保留待重试）`)
  else ElMessage.success('已保存')
}

// 4) 行内保存（当前行；不带待保存附件）
async function onRowSave(refId: number) {
  const out = await session.commit({ kind: 'row', refId })
  if (out.result === null) return ElMessage.warning(out.validation.issues[0]?.message || '该行暂不可保存')
  ElMessage.success('已保存')
}

// 5) 与移除互斥：保存中/该行保存中，移除按钮禁用（会话不代拦，调用方必须自己挡）
const canRemove = computed(() => !session.saving.value)
function rowRemovable(refId: number) {
  return !session.saving.value && !session.savingRefIds.value.has(refId)
}
</script>

<template>
  <!-- 上传：整单级 + 行级，共用同一个 session（归属在上传发起时固定） -->
  <AttachmentUpload :session="session" />
  <AttachmentUpload
    v-for="row in refRows"
    :key="row.id"
    :session="session"
    :ownership="productOwnership({ rowKey: String(row.id), productId: row.productId, productCode: row.productCode })"
  />

  <!-- 管理区：状态/差异/待保存都在 session 里，不保留第二套可变状态 -->
  <AttachmentManager :session="session" row-save batch-save @changed="onSessionChanged" />

  <el-button type="primary" :disabled="!session.canSave.value || !session.dirtyCount.value" @click="onSaveAll">
    保存附件
  </el-button>

  <!-- 移除入口必须与保存互斥 -->
  <el-button link :disabled="!canRemove" @click="onRemoveAll">移除选中</el-button>
</template>
```

---

## 5. 失败 / 部分成功处理约定

| 情形 | 会话行为 | 调用方要做的 |
| --- | --- | --- |
| 无 handler | `canSave=false`；`saveChanges`/`commit` 抛错 | 不要渲染保存按钮（`v-if="session.canSave.value"` 或类型闸门） |
| 校验不过 | 不调处理器，`result=null`，差异保留 | 提示 `result.validation.issues[0].message` |
| 处理器抛错 | 向上抛，差异全保留，`saving`/`savingRefIds` 复位 | `try/catch` + `ElMessage.error` |
| 部分接受 | 只清 `acceptedUploads`/`acceptedUpdates` 里的项，其余保留 | 用 `failed` 提示「已保存 N，M 条失败（保留待重试）」 |
| 返回 `{}` | 什么都不清（全部保留） | 视为失败处理，别当成功 |
| 保存中要移除 | 会话不代拦 | 用 `saving` / `savingRefIds` 禁用移除入口 |

---

## 6. 反例（明确禁止）

1. **文档 / 示例与实现不一致**：本文所有符号都以 `useAttachmentSession.ts` 为准；实现改动后必须回写本文。
2. **处理器只 emit / 不返回结果**：必须返回 `AttachmentSaveResult`；只 emit 会让会话无法区分「已落定 / 没落定」→ 假成功。
3. **为了省事改掉 068 的契约语义**（范围三档、无 handler 抛错、校验先行、失败不假成功、saving 锁定）——本卡只定型注入接口，不改语义。
4. **靠 UI 隐藏代替入口闸**：类型闸门 + 运行时 `canSave` 都要，不能只在模板里 `v-if`。
5. **保存与移除并发**：保存中必须禁用移除，避免「保存中的行被 unbind 掉」后再回填。

---

## 7. 验证命令（笼子内跑，勿裸跑）

```bash
# 类型检查（需 4G；2G 会被 OOM 杀）
cd jjx-web && systemd-run --user --scope -p MemoryMax=4G -p MemorySwapMax=0 -- timeout 600 npx vue-tsc --noEmit

# 068 既有测试（1G）
systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- timeout 120 \
  node --test src/composables/__tests__/useAttachmentSession.test.cjs
```
