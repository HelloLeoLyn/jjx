import { computed, ref, watch, type Ref } from 'vue'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import type {
  AttachmentDocumentTypeItem,
  AttachmentRefCreateBody,
  AttachmentRefItem,
  AttachmentRefMetadata,
} from '@/api/system/attachmentRef'
import { attachmentApi } from '@/api/system/attachment'
import { usePermission } from '@/composables/usePermission'
import { useUserStore } from '@/store/modules/user'
import { ATTACHMENT_SOURCE_PERMISSION, canonicalAttachmentSource } from '@/enums/system/AttachmentEnum'

/**
 * 公共附件引用编排（V1-B WP1）
 * 只封装两件事：① V1-A refs 接口的数据流；② 待绑定（TEMP）队列及其本地恢复。
 * 不做业务判断：单据是否锁定、是否只读由调用方通过 readonly/locked 传入。
 */

export interface PendingEntry {
  attachmentId: number
  requestKey: string
  fileName: string
  fileSize: number
  sha256: string | null
  state: 'pending' | 'binding' | 'failed'
  error?: string
  createdAt: string
  /** 待绑定期间预选的二级资料类型编码（绑定时随 documentType 提交；仅存编码，不存 File/Blob）（dev-20261012-024） */
  documentType?: string | null
  /** 预填展示名（可选）（dev-20261012-024） */
  displayName?: string | null
}

export type BindStatus = 'document-not-saved' | 'all-ok' | 'partial' | 'all-failed'

export interface BindResult {
  status: BindStatus
  bound: { attachmentId: number; refId: number }[]
  failed: { attachmentId: number; fileName: string; message: string }[]
}

export interface UseAttachmentRefsOptions {
  sourceType: Ref<string> | string
  sourceId?: Ref<number | null | undefined> | number | null
  /** 新建单据草稿标识：同一张未保存单据复用同一值，用于隔离待绑定队列 */
  draftId?: Ref<string> | string
  traceId?: Ref<string> | string
  readonly?: Ref<boolean> | boolean
  locked?: Ref<boolean> | boolean
  editPerm?: Ref<string> | string
  viewPerm?: Ref<string> | string
  /** 是否在创建时立即加载列表（默认 true） */
  autoLoad?: boolean
}

const STORAGE_PREFIX = 'attach-pending:v2'

function unwrap<T>(value: Ref<T> | T | undefined, fallback: T): T {
  if (value === undefined) return fallback
  return typeof value === 'object' && value !== null && 'value' in (value as Ref<T>)
    ? ((value as Ref<T>).value as T)
    : (value as T)
}

/** 稳定 requestKey：SHA-256(draftKey|文件SHA-256)；无内容指纹时保留旧元数据算法。 */
async function buildRequestKey(draftKey: string, file: File, sha256: string | null): Promise<string> {
  if (sha256 && globalThis.crypto?.subtle) {
    try {
      const raw = new TextEncoder().encode(`${draftKey}|${sha256}`)
      const digest = await globalThis.crypto.subtle.digest('SHA-256', raw)
      return Array.from(new Uint8Array(digest))
        .map((b) => b.toString(16).padStart(2, '0'))
        .join('')
    } catch {
      // 指纹键计算失败也不阻断上传，回退旧算法；回退键未含内容指纹。
    }
  }
  // crypto.subtle不可用或文件指纹失败：保留旧算法，未含内容指纹。

  const raw = `${draftKey}|${file.name}|${file.size}|${file.lastModified}`
  let h1 = 0x811c9dc5
  let h2 = 0x1000193
  for (let i = 0; i < raw.length; i++) {
    const c = raw.charCodeAt(i)
    h1 = (h1 ^ c) * 0x01000193
    h2 = (h2 * 0x01000193 + c) >>> 0
  }
  const a = (h1 >>> 0).toString(16).padStart(8, '0')
  const b = (h2 >>> 0).toString(16).padStart(8, '0')
  return `${a}${b}${a}${b}${a}${b}${a}${b}`.slice(0, 64)
}

/** 浏览器端 SHA-256（用于绑定后的范围重复检测；不可用时返回 null，不阻断上传） */
async function sha256Of(file: File): Promise<string | null> {
  try {
    if (!globalThis.crypto?.subtle) return null
    const buffer = await file.arrayBuffer()
    const digest = await globalThis.crypto.subtle.digest('SHA-256', buffer)
    return Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('')
  } catch {
    return null
  }
}

export function useAttachmentRefs(options: UseAttachmentRefsOptions) {
  const { hasPermission } = usePermission()
  const userStore = useUserStore()

  const sourceType = ref(canonicalAttachmentSource(unwrap<string>(options.sourceType, '')))
  const sourceId = computed<number | null>(() => {
    const raw = unwrap<number | null | undefined>(options.sourceId, null)
    return raw === undefined || raw === null ? null : Number(raw)
  })
  const traceId = computed(() => unwrap<string>(options.traceId, ''))
  const readonly = computed(() => !!unwrap<boolean>(options.readonly, false))
  const locked = computed(() => !!unwrap<boolean>(options.locked, false))
  const editPerm = computed(() => unwrap<string>(options.editPerm, ''))
  const viewPerm = computed(() => unwrap<string>(options.viewPerm, ''))

  /** 待绑定队列的隔离键：必须含 userId + sourceType + draftId，避免跨用户/跨草稿污染。
   *  sourceId 有值时用 `id-{sourceId}`；否则**必须**由调用方提供 draftId（缺失即拒绝上传）。 */
  const explicitDraftId = computed(() => unwrap<string>(options.draftId, '').trim())
  const draftReady = computed(() => !!sourceId.value || !!explicitDraftId.value)
  const draftKey = computed(() => (sourceId.value ? `id-${sourceId.value}` : explicitDraftId.value))
  const storageKey = computed(() =>
    draftReady.value ? `${STORAGE_PREFIX}:${userStore.userId ?? 'anonymous'}:${sourceType.value}:${draftKey.value}` : '',
  )

  const refs = ref<AttachmentRefItem[]>([])
  const legacyRows = ref<any[]>([])
  const documentTypes = ref<AttachmentDocumentTypeItem[]>([])
  const pending = ref<PendingEntry[]>([])
  const loading = ref(false)
  const error = ref('')

  // ==================== 权限与可写性（组件能力，业务规则由调用方定） ====================
  const permissionPrefix = computed(() => ATTACHMENT_SOURCE_PERMISSION[sourceType.value] || '')
  const canView = computed(() => {
    if (viewPerm.value) return hasPermission(viewPerm.value)
    return permissionPrefix.value ? hasPermission(`${permissionPrefix.value}:view`) : true
  })
  const canEdit = computed(() => {
    if (readonly.value || locked.value) return false
    if (editPerm.value) return hasPermission(editPerm.value)
    return permissionPrefix.value ? hasPermission(`${permissionPrefix.value}:edit`) : true
  })
  const pendingCount = computed(() => pending.value.length)

  // ==================== 待绑定队列持久化 ====================
  /** 写入当前队列；返回是否写入成功（供键迁移时决定能否清理旧键） */
  function persist(): boolean {
    if (!storageKey.value) return false
    try {
      if (pending.value.length) localStorage.setItem(storageKey.value, JSON.stringify(pending.value))
      else localStorage.removeItem(storageKey.value) // 空队列不留无用id/草稿键。
      return true
    } catch {
      /* 存储不可用不阻断上传；调用方据此保留旧键 */
      return false
    }
  }

  /** 清理旧键：只在新键已成功持久化后调用；键相同或为空时不动 */
  function dropStoredKey(key?: string) {
    if (!key || key === storageKey.value) return
    try {
      localStorage.removeItem(key)
    } catch {
      /* 忽略：清理失败不影响功能 */
    }
  }

  function restore() {
    if (!storageKey.value) {
      pending.value = []
      return
    }
    try {
      const raw = localStorage.getItem(storageKey.value)
      if (!raw) {
        pending.value = []
        return
      }
      const parsed = JSON.parse(raw)
      pending.value = Array.isArray(parsed)
        ? parsed
            .filter((e: any) => Number(e?.attachmentId) > 0)
            .map((e: any) => ({
              attachmentId: Number(e.attachmentId),
              requestKey: String(e.requestKey || ''),
              fileName: String(e.fileName || ''),
              fileSize: Number(e.fileSize || 0),
              sha256: e.sha256 ? String(e.sha256) : null,
              state: 'pending' as const,
              error: e.error ? String(e.error) : undefined,
              createdAt: String(e.createdAt || ''),
              documentType: e.documentType ? String(e.documentType) : null,
              displayName: e.displayName ? String(e.displayName) : null,
            }))
        : []
    } catch {
      pending.value = []
    }
  }

  function removePending(attachmentId: number) {
    pending.value = pending.value.filter((e) => e.attachmentId !== attachmentId)
    persist()
  }

  // ==================== 数据加载（A 段引用 / B 段历史） ====================
  async function load(keepPending = true) {
    if (!sourceId.value) {
      refs.value = []
      legacyRows.value = []
      return
    }
    loading.value = true
    error.value = ''
    try {
      const [refRes, legacyRes] = await Promise.allSettled([
        attachmentRefApi.listRefs(sourceType.value, sourceId.value),
        attachmentApi.list(sourceType.value, sourceId.value),
      ])
      if (refRes.status === 'fulfilled') {
        refs.value = ((refRes.value as any)?.data || []) as AttachmentRefItem[]
      } else {
        refs.value = []
        error.value = (refRes.reason as Error)?.message || '附件引用加载失败'
      }
      legacyRows.value = legacyRes.status === 'fulfilled' ? ((legacyRes.value as any)?.data || []) : []
    } finally {
      loading.value = false
      if (!keepPending) pending.value = []
    }
  }

  async function loadDocumentTypes() {
    if (documentTypes.value.length) return documentTypes.value
    try {
      const res: any = await attachmentRefApi.documentTypes()
      documentTypes.value = (res?.data || []) as AttachmentDocumentTypeItem[]
    } catch {
      documentTypes.value = []
    }
    return documentTypes.value
  }

  // ==================== 合并展示规则（refId 与 attachmentId 不混用） ====================
  /** A 段：全部保留（不同 refId 是不同业务上下文） */
  const refRows = computed(() => refs.value.filter((r) => !r.deleted))
  /** B 段：已有有效引用（同业务上下文）的附件不再重复展示；其余历史行保留 */
  const legacyOnlyRows = computed(() => {
    const referenced = new Set(refRows.value.map((r) => Number(r.attachmentId)))
    return legacyRows.value.filter((row) => !referenced.has(Number(row?.id)))
  })

  // ==================== 上传 / 绑定 / 解绑 / 元数据 ====================
  async function uploadTemp(file: File, onProgress?: (percent: number) => void): Promise<number> {
    // 隔离要求：sourceId=null 时必须提供唯一 draftId（禁止退化为共享队列）
    if (!draftReady.value) {
      throw new Error('缺少草稿标识（draftId）：新建单据必须提供唯一 draftId 才能上传附件，避免多个单据共用待绑定队列')
    }
    const sha = await sha256Of(file)
    const requestKey = await buildRequestKey(draftKey.value, file, sha)
    const res: any = await attachmentRefApi.tempUpload(file, requestKey, onProgress)
    const attachmentId = Number(res?.data)
    if (!attachmentId) throw new Error(res?.msg || '临时上传失败')
    const existing = pending.value.find((e) => e.attachmentId === attachmentId)
    if (existing) {
      // 同内容 + 同草稿 = 服务端幂等返回原 id：不重复入队，避免出现两条同 id 条目
      await load()
      return attachmentId
    }
    pending.value.push({
      attachmentId,
      requestKey,
      fileName: file.name,
      fileSize: file.size,
      sha256: sha,
      state: 'pending',
      createdAt: new Date().toISOString(),
      documentType: null,
      displayName: null,
    })
    persist()
    return attachmentId
  }

  async function bindOne(entry: PendingEntry, body: AttachmentRefCreateBody): Promise<number> {
    const res: any = await attachmentRefApi.createRef(body)
    const refId = Number(res?.data)
    if (!refId) throw new Error(res?.msg || '绑定失败')
    removePending(entry.attachmentId)
    return refId
  }

  /** 绑定单条待上传附件（sourceId 有值时的即时绑定） */
  async function bindPendingOne(entry: PendingEntry, meta?: AttachmentRefMetadata): Promise<number> {
    if (!sourceId.value) throw new Error('单据尚未保存，无法建立引用')
    try {
      return await bindOne(entry, {
        attachmentId: entry.attachmentId,
        sourceType: sourceType.value,
        sourceId: sourceId.value,
        documentType: meta?.documentType ?? null,
        displayName: meta?.displayName ?? null,
        remark: meta?.remark ?? null,
        sortOrder: meta?.sortOrder ?? null,
      })
    } catch (error: any) {
      const message = error?.message || '绑定失败'
      if (/附件不存在|附件已删除|附件.*已过期/.test(message)) {
        entry.error = `${message}；可能已过期或不存在，可移除后重新上传`
        persist()
        throw new Error(entry.error)
      }
      if (message === '无权查看附件或历史来源尚未确认') {
        // 后端对过期TEMP也返回此读取拒绝，无法主动确认是否过期，保留权限可能性。
        entry.error = `${message}；可能已过期或不存在，也可能缺少读取权限，确认后可移除并重新上传`
        persist()
        throw new Error(entry.error)
      }
      entry.error = message // 最新失败覆盖旧提示，避免残留上一次的过期猜测。
      persist()
      throw error // 编辑权限/网络等其他错误保留原义，不冒称附件过期。
    }
  }

  /**
   * 批量绑定待上传附件（保存成功后调用）。
   * 单据未保存 → status='document-not-saved'（与绑定失败区分）；
   * 逐条独立处理：部分成功返回 partial，不做整批回滚。
   */
  async function bindPending(meta?: AttachmentRefMetadata): Promise<BindResult> {
    const queue = pending.value.filter((e) => e.state !== 'binding')
    if (!sourceId.value) return { status: 'document-not-saved', bound: [], failed: [] }
    if (!queue.length) return { status: 'all-ok', bound: [], failed: [] }
    const bound: BindResult['bound'] = []
    const failed: BindResult['failed'] = []
    for (const entry of queue) {
      entry.state = 'binding'
      try {
        // 每条待绑定行可各自预选类型（dev-20261012-024）：优先 entry 自身，其次批量 meta
        const refId = await bindPendingOne(entry, {
          documentType: meta?.documentType ?? entry.documentType ?? null,
          displayName: meta?.displayName ?? entry.displayName ?? null,
          remark: meta?.remark ?? null,
          sortOrder: meta?.sortOrder ?? null,
        })
        bound.push({ attachmentId: entry.attachmentId, refId })
      } catch (e: any) {
        entry.state = 'failed'
        const message = e?.message || '绑定失败'
        entry.error = message
        failed.push({ attachmentId: entry.attachmentId, fileName: entry.fileName, message })
      }
    }
    persist()
    const status: BindStatus = !bound.length ? 'all-failed' : failed.length ? 'partial' : 'all-ok'
    await load()
    return { status, bound, failed }
  }

  async function unbind(refId: number) {
    await attachmentRefApi.unbind(refId)
    await load()
  }

  async function updateMeta(refId: number, meta: AttachmentRefMetadata) {
    await attachmentRefApi.updateMeta(refId, meta)
    await load()
  }

  /** 绑定后按范围检测同内容（TEMP 上传无重复响应头，唯一可用通道） */
  async function checkDuplicate(sha256: string | null) {
    if (!sha256 || !sourceId.value) return []
    try {
      const res: any = await attachmentRefApi.refsBySha256(sha256, sourceType.value, sourceId.value)
      return (res?.data || []) as { refId: number; attachmentId: number }[]
    } catch {
      return []
    }
  }

  restore()
  if (options.autoLoad !== false) {
    load()
  }
  watch(sourceId, () => load())
  /**
   * 键切换（新建草稿 draft-X → 保存后 id-N）时的处理：
   * - 跨单据/用户/草稿切换只读取目标键，不携带旧内存队列；
   * - 把当前队列落到新键；**只有写入成功后**才清理旧键（写失败则保留旧键，避免丢失唯一副本）。
   */
  watch(storageKey, (key, previousKey) => {
    if (!key) {
      pending.value = [] // 保留原键持久化副本，不把内存条目带到下一张单据。
      return
    }
    const scope = key.slice(0, key.lastIndexOf(':') + 1)
    const migratingDraft = !!explicitDraftId.value && !!sourceId.value
      && previousKey === `${scope}${explicitDraftId.value}`
    if (!migratingDraft) {
      pending.value = []
      restore() // 其他单据/用户/草稿切换只恢复自己的键，不迁移旧内存队列。
      return
    }
    // 只有当前草稿保存成当前单据才迁移；落新键成功后清旧草稿键。
    if (!pending.value.length) restore()
    if (persist()) dropStoredKey(previousKey)
  })

  return {
    sourceType,
    sourceId,
    refs,
    refRows,
    legacyOnlyRows,
    documentTypes,
    pending,
    pendingCount,
    loading,
    error,
    canView,
    canEdit,
    /** 草稿就绪：sourceId 有值，或调用方提供了唯一 draftId（否则禁止上传） */
    draftReady,
    storageKey,
    load,
    loadDocumentTypes,
    uploadTemp,
    bindPending,
    bindPendingOne,
    unbind,
    updateMeta,
    checkDuplicate,
    removePending,
    persist,
  }
}
