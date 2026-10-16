import { computed, reactive, ref, type Ref } from 'vue'
import {
  useAttachmentRefs,
  type AttachmentUploadContext,
  type PendingEntry,
} from '@/composables/useAttachmentRefs'
import type { AttachmentDocumentTypeItem, AttachmentRefItem } from '@/api/system/attachmentRef'
import { attachmentRefApi } from '@/api/system/attachmentRef'

/**
 * 附件共享会话（dev-20261016-048 第一阶段 A 公共能力）
 *
 * 定位：由**单据编辑容器**每实例创建一次，显式共享给多个上传入口 + 一个管理区（AttachmentManager）。
 * 原则（见 jjx-docs/reference/attachment-refactor-decisions-dev-20261016-001.md §1）：
 *   - 公共能力只做增删改查，不判断业务（报价/订单状态由调用方提供）；
 *   - 会话是**唯一可变状态**：待保存附件、已绑定引用、编辑差异、上传/保存状态都在这一个实例里；
 *   - 不用全局单例；不靠 localStorage 拼多份内存态（localStorage 只做离线兜底，与既有实现一致）。
 *
 * 保存契约（本卡固定并测试，完整落定由 dev-20261016-049 接通）：
 *   - 保存范围固定：行内=当前行；批量=勾选行的改动；调用方全量=完整会话（含筛选隐藏行）。
 *   - 入口：getChanges(scope) → validate(scope) → saveChanges(changes) → acceptSaved(result)。
 *   - 无 saveHandler 时不开放保存入口（canSave=false），不会出现点击无效/假成功的按钮；
 *     可见保存必须真实可用：等异步成功才回填，失败保留改动。
 */

/** 归属上下文：单据级（订单级）/ 产品级。产品级身份必须稳定（正式 product_id 或稳定行键），产品编码仅展示。 */
export interface AttachmentOwnership {
  /** 稳定唯一键（筛选/分组用），如 `order` / `product:123` / `row:<稳定行键>` */
  key: string
  kind: 'order' | 'product'
  productId?: number | null
  /** 仅展示/透传，不作唯一身份 */
  productCode?: string | null
  label?: string
}

/** 单据级归属（订单级，无产品） */
export function orderOwnership(label = '订单级'): AttachmentOwnership {
  return { key: 'order', kind: 'order', productId: null, productCode: null, label }
}

/** 产品级归属：正式 product_id 优先，否则使用调用方提供的**稳定行键**（禁止数组下标/可变编码作唯一身份）。 */
export function productOwnership(input: {
  rowKey: string
  productId?: number | null
  productCode?: string | null
  label?: string
}): AttachmentOwnership {
  const key = input.productId != null ? `product:${input.productId}` : `row:${input.rowKey}`
  return {
    key,
    kind: 'product',
    productId: input.productId ?? null,
    productCode: input.productCode ?? null,
    label: input.label,
  }
}

/** 一条「引用元数据」编辑差异（保存前攒着，不即时提交） */
export interface AttachmentMetaEdit {
  refId: number
  documentType?: string | null
  remark?: string | null
  sortOrder?: number | null
}

/** 变更集：待保存附件 + 已绑定引用的信息类改动 */
export interface AttachmentChanges {
  uploads: PendingEntry[]
  updates: AttachmentMetaEdit[]
  total: number
}

/** 保存结果：成功回填 / 逐行失败（批量允许部分成功） */
export interface AttachmentSaveResult {
  /** 成功落定的上传：本地 attachmentId → 服务端 refId */
  acceptedUploads?: { attachmentId: number; refId: number }[]
  /** 成功保存的元数据改动 refId */
  acceptedUpdates?: number[]
  /** 逐行失败（key：attachmentId 或 refId） */
  failed?: { key: number; message: string }[]
}

export type AttachmentSaveHandler = (
  changes: AttachmentChanges,
) => Promise<AttachmentSaveResult> | AttachmentSaveResult

export interface AttachmentValidationIssue {
  key: number
  message: string
}
export interface AttachmentValidation {
  ok: boolean
  issues: AttachmentValidationIssue[]
}

/** 保存范围：all=完整会话（含筛选隐藏行）；row=当前行；rows=勾选行的改动 */
export type AttachmentSaveScope =
  | { kind: 'all' }
  | { kind: 'row'; refId: number }
  | { kind: 'rows'; refIds: number[] }

type MaybeRef<T> = Ref<T> | T

export interface UseAttachmentSessionOptions {
  sourceType: MaybeRef<string>
  sourceId?: MaybeRef<number | null | undefined>
  draftId?: MaybeRef<string>
  traceId?: MaybeRef<string>
  readonly?: MaybeRef<boolean>
  locked?: MaybeRef<boolean>
  editPerm?: MaybeRef<string>
  viewPerm?: MaybeRef<string>
  autoLoad?: boolean
  /** 保存处理器；不传 = 不开放保存入口（无处理器不开放按钮） */
  saveHandler?: AttachmentSaveHandler
}

export type AttachmentSession = ReturnType<typeof useAttachmentSession>

/** 引用行的归属键：优先正式 productId，其次 productCode 分组，最后单据级 */
export function ownershipKeyOfRef(row: Pick<AttachmentRefItem, 'productId' | 'productCode'>): string {
  if (row.productId != null) return `product:${row.productId}`
  if (row.productCode) return `pc:${row.productCode}`
  return 'order'
}

/** 待保存附件行的归属键：以上传时固定的 ownershipKey 为准，退化用 productCode */
export function ownershipKeyOfPending(entry: Pick<PendingEntry, 'ownershipKey' | 'productCode'>): string {
  if (entry.ownershipKey) return entry.ownershipKey
  if (entry.productCode) return `pc:${entry.productCode}`
  return 'order'
}

function unwrap<T>(value: MaybeRef<T> | undefined, fallback: T): T {
  if (value === undefined) return fallback
  return typeof value === 'object' && value !== null && 'value' in (value as Ref<T>)
    ? ((value as Ref<T>).value as T)
    : (value as T)
}

export function useAttachmentSession(options: UseAttachmentSessionOptions) {
  const saveHandler = options.saveHandler

  // 文档级共享：productCode 传 null → 一次加载整单全部引用；上传归属由每次 upload 的上下文固定。
  const store = useAttachmentRefs({
    sourceType: options.sourceType,
    sourceId: options.sourceId,
    draftId: options.draftId,
    traceId: options.traceId,
    readonly: options.readonly,
    locked: options.locked,
    editPerm: options.editPerm,
    viewPerm: options.viewPerm,
    productCode: null,
    autoLoad: options.autoLoad,
  })

  /** 编辑差异：refId → 未提交的元数据补丁（保存前攒着，绝不即时提交） */
  const edits = reactive<Record<number, Omit<AttachmentMetaEdit, 'refId'>>>({})
  const uploadingCount = ref(0)
  const saving = ref(false)
  /** 正在提交保存的行（refId）：用于提交期间锁定对应行，防重复提交/并发编辑（dev-20261016-048） */
  const savingRefIds = ref<Set<number>>(new Set())
  /** 已移除但可能仍有迟到回包的 attachmentId：回包不得重新加回 */
  const suppressed = ref<Set<number>>(new Set())

  const canSave = computed(() => !!saveHandler)
  const hasEdits = computed(() => Object.keys(edits).length > 0)
  /** 未保存改动数 = 待保存附件数 + 已绑定引用的信息改动数 */
  const dirtyCount = computed(() => store.pending.value.length + Object.keys(edits).length)
  const uploading = computed(() => uploadingCount.value > 0)

  /** 归属上下文清单（用于 Manager 筛选）：单据级恒在，产品级按现存行/队列去重 */
  const ownerships = computed<AttachmentOwnership[]>(() => {
    const map = new Map<string, AttachmentOwnership>()
    map.set('order', orderOwnership())
    for (const row of store.refs.value) {
      if (row.deleted) continue
      const key = ownershipKeyOfRef(row)
      if (key === 'order' || map.has(key)) continue
      map.set(key, {
        key,
        kind: 'product',
        productId: row.productId ?? null,
        productCode: row.productCode ?? null,
        label: row.productCode ? `产品 ${row.productCode}` : key,
      })
    }
    for (const entry of store.pending.value) {
      const key = ownershipKeyOfPending(entry)
      if (key === 'order' || map.has(key)) continue
      map.set(key, {
        key,
        kind: 'product',
        productId: null,
        productCode: entry.productCode ?? null,
        label: entry.productCode ? `产品 ${entry.productCode}` : key,
      })
    }
    return [...map.values()]
  })

  // ==================== 编辑差异 ====================
  /** 记录一条引用行的信息改动（与原始值一致的字段自动丢弃，避免假差异） */
  function setEdit(refId: number, patch: Omit<AttachmentMetaEdit, 'refId'>) {
    const merged: Omit<AttachmentMetaEdit, 'refId'> = { ...(edits[refId] ?? {}), ...patch }
    const row = store.refs.value.find((r) => r.id === refId)
    if (row) {
      if ('documentType' in merged && (merged.documentType ?? null) === (row.documentType ?? null)) delete merged.documentType
      if ('remark' in merged && (merged.remark ?? null) === (row.remark ?? null)) delete merged.remark
      if ('sortOrder' in merged && (merged.sortOrder ?? 0) === (row.sortOrder ?? 0)) delete merged.sortOrder
    }
    if (Object.keys(merged).length) edits[refId] = merged
    else delete edits[refId]
  }
  function clearEdit(refId: number) {
    delete edits[refId]
  }
  function editOf(refId: number): Omit<AttachmentMetaEdit, 'refId'> | undefined {
    return edits[refId]
  }
  function hasEdit(refId: number): boolean {
    return !!edits[refId]
  }
  /** 显示用：某引用行当前生效的二级类型（优先未提交草稿） */
  function effectiveDocumentType(refId: number, fallback: string | null | undefined): string {
    const draft = edits[refId]
    if (draft && 'documentType' in draft) return draft.documentType ?? ''
    return fallback ?? ''
  }

  // ==================== 变更集 / 校验 / 保存 ====================
  function inScope(refId: number, scope: AttachmentSaveScope): boolean {
    if (scope.kind === 'all') return true
    if (scope.kind === 'row') return refId === scope.refId
    return scope.refIds.includes(refId)
  }

  /** 按范围取变更集：uploads 仅"完整会话"包含；updates 按行过滤 */
  function getChanges(scope: AttachmentSaveScope = { kind: 'all' }): AttachmentChanges {
    const updates: AttachmentMetaEdit[] = []
    for (const key of Object.keys(edits)) {
      const refId = Number(key)
      if (!inScope(refId, scope)) continue
      updates.push({ refId, ...edits[refId] })
    }
    const uploads = scope.kind === 'all' ? [...store.pending.value] : []
    return { uploads, updates, total: uploads.length + updates.length }
  }

  /** 保存前校验：资料类型缺失之外，还覆盖上传未完成、无权限/锁定、正在处理中的待保存行 */
  function validate(scope: AttachmentSaveScope = { kind: 'all' }): AttachmentValidation {
    const issues: AttachmentValidationIssue[] = []
    // 权限/锁定：无编辑能力时一律不得进入保存（不只靠 UI 隐藏）
    if (!store.canEdit.value) {
      issues.push({ key: -1, message: '当前无附件编辑权限或已被业务锁定，不能保存附件改动' })
    }
    // 上传未完成：有在途上传时不允许提交，避免与未落定队列竞争
    if (uploadingCount.value > 0) {
      issues.push({ key: -2, message: '有附件正在上传，请等待上传完成后再保存' })
    }
    if (scope.kind === 'all') {
      for (const entry of store.pending.value) {
        if (entry.state === 'binding') {
          issues.push({ key: entry.attachmentId, message: `附件「${entry.fileName || entry.attachmentId}」正在处理中，请稍后` })
          continue
        }
        if (!entry.documentType) {
          issues.push({ key: entry.attachmentId, message: `待保存附件「${entry.fileName || entry.attachmentId}」未选择资料类型（二级）` })
        }
      }
    }
    return { ok: issues.length === 0, issues }
  }

  /** 应用服务端保存结果：成功项回填并清理本地差异；失败项保留（不假成功） */
  function acceptSaved(result: AttachmentSaveResult) {
    const acceptedUploads = result.acceptedUploads ?? []
    if (acceptedUploads.length) {
      const acceptedIds = new Set(acceptedUploads.map((u) => u.attachmentId))
      store.pending.value = store.pending.value.filter((e) => !acceptedIds.has(e.attachmentId))
      for (const id of acceptedIds) suppressed.value.delete(id)
      store.persist()
    }
    for (const refId of result.acceptedUpdates ?? []) {
      if (edits[refId]) delete edits[refId]
    }
    void store.load()
  }

  /** 执行保存：调用注入的处理器，成功才回填（失败保留改动、向上抛由调用方提示） */
  async function saveChanges(changes: AttachmentChanges): Promise<AttachmentSaveResult> {
    if (!saveHandler) throw new Error('未配置保存处理器，不能保存附件改动')
    saving.value = true
    const lockedIds = changes.updates.map((u) => u.refId)
    if (lockedIds.length) savingRefIds.value = new Set([...savingRefIds.value, ...lockedIds])
    try {
      const result = await saveHandler(changes)
      acceptSaved(result ?? {})
      return result ?? {}
    } finally {
      if (lockedIds.length) {
        const next = new Set(savingRefIds.value)
        for (const id of lockedIds) next.delete(id)
        savingRefIds.value = next
      }
      saving.value = false
    }
  }

  /** 便捷方法：按范围取差异 → 调用处理器 → 回填；返回变更与结果 */
  async function commit(scope: AttachmentSaveScope = { kind: 'all' }) {
    const changes = getChanges(scope)
    const check = validate(scope)
    if (!check.ok) return { changes, validation: check, result: null as AttachmentSaveResult | null }
    const result = await saveChanges(changes)
    return { changes, validation: check, result }
  }

  // ==================== 上传 / 即时移除 ====================
  /** 通过共享会话上传：归属在发起时固定，切换产品/单据不改变进行中上传的归属 */
  async function upload(file: File, ownership?: AttachmentOwnership, onProgress?: (percent: number) => void): Promise<number> {
    const context: AttachmentUploadContext | null = ownership
      ? { productId: ownership.productId ?? null, productCode: ownership.productCode ?? null, ownershipKey: ownership.key }
      : null
    uploadingCount.value++
    try {
      const attachmentId = await store.uploadTemp(file, onProgress, context)
      if (suppressed.value.has(attachmentId)) {
        // 迟到回包：该附件已被移除，不得重新加回队列
        store.removePending(attachmentId)
        return attachmentId
      }
      const entry = store.pending.value.find((e) => e.attachmentId === attachmentId)
      if (entry && ownership) entry.ownershipKey = ownership.key
      return attachmentId
    } finally {
      uploadingCount.value--
    }
  }

  /** 即时移除待保存附件（草稿移除草稿/队列项，不删文件）：
   *  先删草稿行，成功才清队列项并登记抑制（迟到回包不复活）；失败抛错，由调用方保留原行。 */
  async function removePending(attachmentId: number) {
    const entry = store.pending.value.find((e) => e.attachmentId === attachmentId)
    if (entry?.draftRefId) {
      await attachmentRefApi.removeDraft(entry.draftRefId) // 失败向上抛：失败保持原行
    }
    suppressed.value = new Set([...suppressed.value, attachmentId])
    store.removePendingLocal(attachmentId)
  }

  /** 即时移除已绑定引用（解除引用，不删文件）；成功清该行编辑差异 */
  async function unbind(refId: number) {
    await store.unbind(refId)
    if (edits[refId]) delete edits[refId]
  }

  function refresh() {
    return store.load()
  }

  return {
    // 数据
    refs: store.refs,
    refRows: store.refRows,
    pending: store.pending,
    pendingCount: store.pendingCount,
    documentTypes: store.documentTypes as Ref<AttachmentDocumentTypeItem[]>,
    loading: store.loading,
    error: store.error,
    canView: store.canView,
    canEdit: store.canEdit,
    draftReady: store.draftReady,
    storageKey: store.storageKey,
    sourceType: store.sourceType,
    sourceId: store.sourceId,
    // 状态/差异
    edits,
    dirtyCount,
    hasEdits,
    uploadingCount,
    uploading,
    saving,
    savingRefIds,
    canSave,
    ownerships,
    // 编辑差异
    setEdit,
    clearEdit,
    editOf,
    hasEdit,
    effectiveDocumentType,
    // 契约
    getChanges,
    validate,
    saveChanges,
    acceptSaved,
    commit,
    // 上传/移除
    upload,
    removePending,
    unbind,
    persist: store.persist,
    refresh,
    load: store.load,
    loadDocumentTypes: store.loadDocumentTypes,
    // 归属键工具（供 Manager 筛选）
    ownershipKeyOfRef,
    ownershipKeyOfPending,
  }
}
