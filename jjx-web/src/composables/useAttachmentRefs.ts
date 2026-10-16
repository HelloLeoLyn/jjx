import { computed, ref, watch, onMounted, onBeforeUnmount, onDeactivated, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import type {
  AttachmentDocumentTypeItem,
  AttachmentDrawingMetadata,
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
  /** 行级附件归属：产品编码（上传时记录；绑定时随引用提交。S5 dev-20261013-043） */
  productCode?: string | null
  /** 草稿行引用 ID（库里的待保存附件行；dev-20261016-009）。有值则改类型/移除直接打草稿行接口。 */
  draftRefId?: number | null
  /** 归属上下文稳定键（dev-20261016-048 共享会话）：上传发起时固定，用于筛选/分组；
   *  产品级用稳定标识（正式 product_id 或不随渲染变化的行键），不用产品编码/数组下标作唯一身份。 */
  ownershipKey?: string | null
}

/** 单次上传的归属上下文（dev-20261016-048）：请求发起时固定，切换产品/单据不改变进行中上传的归属。 */
export interface AttachmentUploadContext {
  productId?: number | null
  /** 仅展示/透传草稿行，不作唯一身份 */
  productCode?: string | null
  ownershipKey?: string | null
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
  /** 单据产品（单产品单据才有；多产品单据传 null 表示整单级）。写引用时随 product_id 落库 */
  productId?: Ref<number | null | undefined> | number | null
  /** 行级附件归属：产品编码（内部字段，S5 dev-20261013-043）；为空=不归属产品 */
  productCode?: Ref<string | null | undefined> | string | null
  /** 可选图纸元数据（dev-20261014-029，019 方案B）：非空时随 TEMP 上传写入 sys_attachment 图纸列；
   *  不传/null = 不写这些列，行为与改造前一致，不影响其它来源。 */
  drawingMeta?: Ref<AttachmentDrawingMetadata | null | undefined> | AttachmentDrawingMetadata | null
  /** 是否在创建时立即加载列表（默认 true） */
  autoLoad?: boolean
}

const STORAGE_PREFIX = 'attach-pending:v2'

/** 每个实例单独存储（dev-20261016-041：改存 localStorage——关掉标签页后仍能在「恢复未保存草稿」里找回）。 */
const DRAFT_INSTANCE_PREFIX = 'attach-instance:v2:'
/** 旧版草稿实例键（sessionStorage，仅当次会话可见）：读到即迁到 localStorage，避免升级当次丢草稿。 */
const DRAFT_INSTANCE_PREFIX_V1 = 'attach-instance:v1:'
/** 「本标签页正在编辑哪份草稿」指针（sessionStorage）：刷新能自动恢复，新开的标签页不会抢。 */
const DRAFT_ACTIVE_PREFIX = 'attach-active:v1:'
/** 草稿保留期（天）与每人每来源条数上限：与 TEMP 附件 7 天到期对齐。 */
const DRAFT_RETENTION_DAYS = 7
const DRAFT_MAX_PER_SOURCE = 20
const draftPageInstance = `${Date.now()}-${Math.random()}`
export interface AttachmentDraftChoice { id: string; label: string }
interface AttachmentDraftRecord {
  id: string
  owner: string
  source: string
  page: string
  pageInstance: string
  active: boolean
  createdAt: string
  snapshot?: Record<string, any>
}

/** 新建和恢复是两个显式动作；关闭保留实例，成功保存才移除实例记录。 */
export function useAttachmentDraftInstance(source: string, form: {
  snapshot: () => Record<string, any>
  restore: (snapshot: Record<string, any>) => void
}) {
  const user = useUserStore()
  const draftId = ref('')
  const records = ref<AttachmentDraftRecord[]>([])
  const owner = () => String(user.userId ?? 'anonymous')
  const page = () => window.location.pathname + window.location.search + window.location.hash
  const instanceKey = (id: string) => DRAFT_INSTANCE_PREFIX + id
  const activeKey = () => DRAFT_ACTIVE_PREFIX + owner() + ':' + source
  const pendingKey = (id: string) => `${STORAGE_PREFIX}:${owner()}:${source}:${id}`
  let current: AttachmentDraftRecord | undefined
  let restoring = false
  /** 写入实例记录（localStorage 持久），并同步本标签页的「正在编辑」指针。 */
  function write(record: AttachmentDraftRecord) {
    try { localStorage.setItem(instanceKey(record.id), JSON.stringify(record)) }
    catch (error) { console.warn('草稿会话保存失败，关标签页后可能无法恢复', error) }
    records.value = [...records.value.filter((r) => r.id !== record.id), record]
    try {
      if (record.active) sessionStorage.setItem(activeKey(), record.id)
      else if (sessionStorage.getItem(activeKey()) === record.id) sessionStorage.removeItem(activeKey())
    } catch (error) { console.warn('草稿指针写入失败，刷新自动恢复不可用', error) }
  }
  /** 丢掉一条实例记录（不动待绑定队列，也不动库里的草稿行）。 */
  function dropRecord(record: AttachmentDraftRecord) {
    try { localStorage.removeItem(instanceKey(record.id)) }
    catch (error) { console.warn('草稿会话清理失败', error) }
    try { if (sessionStorage.getItem(activeKey()) === record.id) sessionStorage.removeItem(activeKey()) }
    catch { /* 指针清理失败不影响主流程 */ }
  }
  function readRecords() {
    const found: AttachmentDraftRecord[] = []
    try {
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i)
        if (!key?.startsWith(DRAFT_INSTANCE_PREFIX)) continue
        try {
          const r = JSON.parse(localStorage.getItem(key) || 'null')
          // 同一浏览器可能被换过账号：只认本人 + 本源；别人的原样留着，换回去还能看到。
          if (r?.id && r.owner === owner() && r.source === source) found.push(r)
        } catch (error) { console.warn('跳过损坏的草稿会话记录', key, error) }
      }
      // 迁移旧 sessionStorage 版实例（v1）→ localStorage，迁完清旧键。
      const legacyKeys: string[] = []
      for (let i = 0; i < sessionStorage.length; i++) {
        const key = sessionStorage.key(i)
        if (key?.startsWith(DRAFT_INSTANCE_PREFIX_V1)) legacyKeys.push(key)
      }
      for (const key of legacyKeys) {
        let parsed: AttachmentDraftRecord | null = null
        try { parsed = JSON.parse(sessionStorage.getItem(key) || 'null') } catch { parsed = null }
        try { sessionStorage.removeItem(key) } catch { /* 旧键清理失败不影响迁移 */ }
        const r = parsed
        const duplicated = r ? found.some((x) => x.id === r.id) : false
        if (r && !duplicated && r.owner === owner() && r.source === source) {
          try { localStorage.setItem(instanceKey(r.id), JSON.stringify(r)) } catch { /* 存储不可用：不迁移，只当次可用 */ }
          found.push(r)
        }
      }
    } catch (error) { console.warn('读取草稿会话失败', error) }
    // 保留策略：超期（与 TEMP 一致 7 天）或超出上限的最老记录，连本地待绑定队列一起清，避免只增不减。
    const cutoff = Date.now() - DRAFT_RETENTION_DAYS * 86400000
    const kept = found
      .filter((r) => {
        const t = Date.parse(r.createdAt)
        return !Number.isFinite(t) || t >= cutoff
      })
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, DRAFT_MAX_PER_SOURCE)
    const keptIds = new Set(kept.map((r) => r.id))
    for (const r of found) {
      if (keptIds.has(r.id)) continue
      dropRecord(r)
      try { localStorage.removeItem(pendingKey(r.id)) } catch { /* 队列清理失败不影响读取 */ }
    }
    records.value = kept
    // 旧共享键仅迁移为可选恢复项，绝不自动拿它创建下一张新单。
    try {
      const legacyKey = `attach-draft:v1:${owner()}:${source}`
      const legacyId = sessionStorage.getItem(legacyKey)
      if (legacyId && !kept.some((r) => r.id === legacyId)) {
        write({ id: legacyId, owner: owner(), source, page: page(), pageInstance: '',
          active: false, createdAt: new Date().toISOString() })
      }
      if (legacyId) sessionStorage.removeItem(legacyKey)
    } catch (error) { console.warn('迁移旧共享草稿键失败', error) }
  }
  readRecords()
  // 只自动恢复「本标签页」仍打开的实例（刷新场景）；新开的标签页不抢，仍可从下拉手动恢复。
  const resumeId: string | undefined = (() => {
    try {
      const id = sessionStorage.getItem(activeKey())
      return id && records.value.some((r) => r.id === id && r.active) ? id : undefined
    } catch { return undefined }
  })()
  const choices = computed<AttachmentDraftChoice[]>(() => records.value
    .filter((r) => r.owner === owner() && r.id !== draftId.value)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .map((r) => {
      const data = r.snapshot?.form || r.snapshot
      const product = data?.productCode || data?.product?.productCode || data?.items?.[0]?.productCode
      const detail = [data?.customerName, product].filter(Boolean).join(' / ')
      return { id: r.id, label: `${new Date(r.createdAt).toLocaleString()}.${r.createdAt.slice(20, 23)}${detail ? ` · ${detail}` : ''}` }
    }))
  function remember() {
    if (!current || current.owner !== owner() || restoring) return
    current = { ...current, snapshot: JSON.parse(JSON.stringify(form.snapshot())) }
    write(current)
  }
  function close() {
    if (!current) return
    remember()
    write({ ...current, active: false })
    current = undefined
    draftId.value = ''
  }
  function startNew() {
    close()
    const id = `${source}-${globalThis.crypto?.randomUUID?.() || `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`}`
    current = { id, owner: owner(), source, page: page(), pageInstance: draftPageInstance,
      active: true, createdAt: new Date().toISOString() }
    draftId.value = id
    remember()
  }
  function restore(id: string): boolean {
    const record = records.value.find((r) => r.id === id && r.owner === owner())
    if (!record) return false
    close()
    restoring = true
    try {
      // 无表单快照的历史实例也必须清空当前表单，避免携带另一张单的产品。
      form.restore(record.snapshot ? JSON.parse(JSON.stringify(record.snapshot)) : {})
      current = { ...record, page: page(), pageInstance: draftPageInstance, active: true }
      draftId.value = id
      write(current)
    } finally { restoring = false }
    return true
  }
  function complete() {
    if (!current) return
    dropRecord(current)
    records.value = records.value.filter((r) => r.id !== current?.id)
    current = undefined
    draftId.value = ''
  }
  /** 草稿摘要（客户 / 产品编码）：确认弹窗与下拉 label 共用。 */
  function summaryOf(record: AttachmentDraftRecord): string {
    const data = record.snapshot?.form || record.snapshot
    const product = data?.productCode || data?.product?.productCode || data?.items?.[0]?.productCode
    return [data?.customerName, product].filter(Boolean).join(' / ')
  }
  /**
   * 放弃一份未保存草稿（dev-20261016-042）：清本地实例记录 + 本地待绑定队列 + 库里的草稿行；
   * 已上传的 TEMP 文件不在这里删，留待 7 天到期由清理流程回收（与既有解绑口径一致）。
   */
  async function discard(id: string): Promise<{ ok: boolean; removed: number; failed: number }> {
    const record = records.value.find((r) => r.id === id && r.owner === owner())
    if (!record) return { ok: false, removed: 0, failed: 0 }
    if (current?.id === id) {
      current = { ...current, active: false }
      write(current)
      current = undefined
      draftId.value = ''
    }
    let removed = 0
    let failed = 0
    try {
      const res: any = await attachmentRefApi.drafts(id)
      for (const row of (res?.data || []) as { id: number }[]) {
        try { await attachmentRefApi.removeDraft(Number(row.id)); removed++ }
        catch (error) { console.warn('草稿行移除失败', row?.id, error); failed++ }
      }
    } catch (error) { console.warn('读取草稿行失败，仅清理本地草稿', error) }
    try { localStorage.removeItem(pendingKey(id)) } catch { /* 队列清理失败不影响其余 */ }
    dropRecord(record)
    records.value = records.value.filter((r) => r.id !== id)
    return { ok: failed === 0, removed, failed }
  }
  /** 恢复草稿（带确认，dev-20261016-042）：整单覆盖当前表单；当前已填内容会先保留成一份草稿，可再切回。 */
  async function confirmRestore(id: string): Promise<boolean> {
    const record = records.value.find((r) => r.id === id && r.owner === owner())
    if (!record) { ElMessage.warning('该草稿已不存在'); return false }
    const detail = summaryOf(record)
    try {
      await ElMessageBox.confirm(
        `将载入这份草稿的整单内容（客户/产品/明细 + 附件）${detail ? `：${detail}` : ''}。`
          + '当前已填内容会先保留成一份草稿，可再切回。',
        '恢复未保存草稿',
        { type: 'warning', confirmButtonText: '载入草稿', cancelButtonText: '取消' },
      )
    } catch { return false }
    return restore(id)
  }
  /** 放弃草稿（带确认，dev-20261016-042）：删本地记录/队列与库中草稿行，不可恢复。 */
  async function confirmDiscard(id: string): Promise<boolean> {
    const record = records.value.find((r) => r.id === id && r.owner === owner())
    if (!record) { ElMessage.warning('该草稿已不存在'); return false }
    const detail = summaryOf(record)
    try {
      await ElMessageBox.confirm(
        `将放弃这份草稿${detail ? `（${detail}）` : ''}：本地草稿与已上传的临时附件都会丢弃，不可恢复。`,
        '放弃草稿',
        { type: 'warning', confirmButtonText: '放弃', cancelButtonText: '取消' },
      )
    } catch { return false }
    const result = await discard(id)
    if (result.failed) ElMessage.warning(`草稿已放弃，但有 ${result.failed} 个附件引用未能清理，可稍后在文件管理页清理`)
    else ElMessage.success('草稿已放弃')
    return true
  }
  // 连同产品/报价行上下文恢复，避免附件恢复后被当前新单的产品信息误带。
  onMounted(() => watch(form.snapshot, remember, { deep: true, flush: 'post' }))
  onBeforeUnmount(close)
  onDeactivated(close)
  watch(() => user.userId, () => { close(); readRecords() })
  return { draftId, choices, resumeId, startNew, restore, close, complete, discard, confirmRestore, confirmDiscard }
}

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
  /** 单据产品（单产品单据）；未传 = null（整单级引用） */
  const productId = computed<number | null>(() => {
    const raw = unwrap<number | null | undefined>(options.productId, null)
    return raw === undefined || raw === null ? null : Number(raw)
  })
  /** 行级附件归属：产品编码（内部字段）；未传 = null（不归属产品） */
  const productCode = computed<string | null>(() => {
    const raw = unwrap<string | null | undefined>(options.productCode, null)
    return raw === undefined || raw === null || raw === '' ? null : String(raw)
  })
  /** 可选图纸元数据；未传 = null（不写图纸列）。仅上传时透传，不参与任何读取/唯一性判定。 */
  const drawingMeta = computed<AttachmentDrawingMetadata | null>(() =>
    unwrap<AttachmentDrawingMetadata | null | undefined>(options.drawingMeta, null) ?? null,
  )

  /** 待绑定队列的隔离键：用户、来源、草稿及产品行隔离。
   *  已保存单据的行级键包含 productCode；未保存单据必须提供 draftId。 */
  const explicitDraftId = computed(() => unwrap<string>(options.draftId, '').trim())
  const draftReady = computed(() => !!sourceId.value || !!explicitDraftId.value)
  const draftKey = computed(() => sourceId.value
    ? productCode.value
      ? `id-${sourceId.value}::p:${productCode.value}`
      : `id-${sourceId.value}`
    : explicitDraftId.value)
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
  /** 待绑定队列中尚未选择二级资料类型的条数（保存前校验用，dev-20261015-032）。
   *  这类附件绑定必然失败，故保存前拦下，避免「单据保存成功但附件未绑定」。 */
  const pendingMissingTypeCount = computed(
    () => pending.value.filter((e) => e.state !== 'binding' && !e.documentType).length
  )

  // ==================== 待绑定队列（以库里的「草稿行」为准，本地 localStorage 作离线兜底） ====================
  /** 从后端拉草稿行（仅未保存单据）：以库为准刷新本地队列，展上未反映的本地上传项保留；失败则继续用本地。dev-20261016-009 */
  let draftSyncSequence = 0
  async function syncDraftsFromBackend() {
    if (sourceId.value || !draftReady.value) return
    const identity = storageKey.value
    const sequence = ++draftSyncSequence
    try {
      const res: any = await attachmentRefApi.drafts(draftKey.value)
      if (identity !== storageKey.value || sequence !== draftSyncSequence || sourceId.value) return
      const rows = Array.isArray(res?.data) ? res.data : []
      const local = pending.value
      const mapped: PendingEntry[] = rows.map((r: any) => {
        const prev = local.find((e) => e.attachmentId === Number(r.attachmentId))
        return {
          attachmentId: Number(r.attachmentId),
          requestKey: prev?.requestKey || '',
          fileName: String(r.fileName || ''),
          fileSize: Number(r.fileSize || 0),
          sha256: null,
          state: 'pending' as const,
          createdAt: String(r.createTime || ''),
          documentType: r.documentType ? String(r.documentType) : null,
          displayName: r.displayName ? String(r.displayName) : null,
          productCode: r.productCode ? String(r.productCode) : null,
          ownershipKey: prev?.ownershipKey ?? null,
          draftRefId: Number(r.id),
        }
      })
      const seen = new Set(mapped.map((e) => e.attachmentId))
      pending.value = [...mapped, ...local.filter((e) => !seen.has(e.attachmentId))]
    } catch {
      /* 拉取失败：保留本地队列（离线兜底），不阻断 */
    }
  }

  /** 把本地队列里的二级类型推回草稿行（仅未保存单据；失败不影响本地）。 */
  function syncDraftTypes() {
    if (sourceId.value) return
    for (const e of pending.value) {
      if (e.draftRefId) void attachmentRefApi.updateDraft(e.draftRefId, e.documentType ?? null).catch(() => {})
    }
  }

  /** 写入当前队列；返回是否写入成功（供键迁移时决定能否清理旧键） */
  function persist(): boolean {
    syncDraftTypes() // 队列变更同时推回草稿行（库为准）
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
    ++draftSyncSequence
    if (!storageKey.value) {
      pending.value = []
      return
    }
    try {
      const raw = localStorage.getItem(storageKey.value)
      const parsed = raw ? JSON.parse(raw) : []
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
              productCode: e.productCode ? String(e.productCode) : null,
              ownershipKey: e.ownershipKey ? String(e.ownershipKey) : null,
            }))
        : []
    } catch {
      pending.value = []
    }
    void syncDraftsFromBackend() // 以库里的草稿行为准补齐/刷新（dev-20261016-009）
  }

  function removePending(attachmentId: number) {
    ++draftSyncSequence // 在途草稿列表不能把刚移除的行补回。
    const entry = pending.value.find((e) => e.attachmentId === attachmentId)
    if (entry?.draftRefId) void attachmentRefApi.removeDraft(entry.draftRefId).catch(() => {})
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
  const refRows = computed(() => refs.value.filter((r) =>
    !r.deleted && (!productCode.value || r.productCode === productCode.value)))
  /** B 段：已有有效引用（同业务上下文）的附件不再重复展示；其余历史行保留 */
  const legacyOnlyRows = computed(() => {
    // 历史文件没有可靠的行级归属，不得在任意产品行展示为该行资料。
    if (productCode.value) return []
    const referenced = new Set(refRows.value.map((r) => Number(r.attachmentId)))
    return legacyRows.value.filter((row) => !referenced.has(Number(row?.id)))
  })

  // ==================== 上传 / 绑定 / 解绑 / 元数据 ====================
  async function uploadTemp(file: File, onProgress?: (percent: number) => void, context?: AttachmentUploadContext | null): Promise<number> {
    // 隔离要求：sourceId=null 时必须提供唯一 draftId（禁止退化为共享队列）
    if (!draftReady.value) {
      throw new Error('缺少草稿标识（draftId）：新建单据必须提供唯一 draftId 才能上传附件，避免多个单据共用待绑定队列')
    }
    const uploadIdentity = storageKey.value
    const uploadDraftKey = draftKey.value
    // 归属上下文在发起时固定（dev-20261016-048）：显式传入优先，否则用实例默认；不随后续切换改变。
    const uploadProductId = context ? (context.productId ?? null) : (productId.value ?? null)
    const uploadProductCode = context ? (context.productCode ?? null) : productCode.value
    const uploadOwnershipKey = context ? (context.ownershipKey ?? null) : null
    const draft = sourceId.value
      ? null
      : { sourceType: sourceType.value, productId: uploadProductId, draftKey: uploadDraftKey }
    const uploadDrawingMeta = drawingMeta.value
    const sha = await sha256Of(file)
    const requestKey = await buildRequestKey(uploadDraftKey, file, sha)
    // 未保存单据上传时带草稿信息：后端同事务落一条草稿引用（dev-20261016-009）
    const res: any = await attachmentRefApi.tempUpload(file, requestKey, onProgress, uploadDrawingMeta, draft)
    if (uploadIdentity !== storageKey.value) {
      throw new Error('文件已上传到原草稿，请恢复原草稿查看')
    }
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
      productCode: uploadProductCode,
      ownershipKey: uploadOwnershipKey,
    })
    persist()
    void syncDraftsFromBackend() // 取回刚建的草稿行 refId（dev-20261016-009）
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
    // 资料类型必选（dev-20261015-028）：所有绑定路径统一在此拦（上传即绑定 / 批量 / 重试），避免漏拦
    const requiredType = meta?.documentType ?? entry.documentType ?? null
    if (!requiredType) throw new Error('请先选择资料类型（二级）')
    entry.documentType = requiredType
    try {
      return await bindOne(entry, {
        attachmentId: entry.attachmentId,
        sourceType: sourceType.value,
        sourceId: sourceId.value,
        productId: productId.value,
        productCode: entry.productCode ?? productCode.value,
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
      // 资料类型必选（dev-20261015-028）：缺类型不发绑定请求，直接给出可操作提示
      const documentType = meta?.documentType ?? entry.documentType ?? null
      if (!documentType) {
        entry.state = 'failed'
        entry.error = '请先选择资料类型（二级）'
        failed.push({ attachmentId: entry.attachmentId, fileName: entry.fileName, message: entry.error })
        continue
      }
      entry.state = 'binding'
      try {
        // 每条待绑定行可各自预选类型（dev-20261012-024）：优先 entry 自身，其次批量 meta
        const refId = await bindPendingOne(entry, {
          documentType,
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

  onBeforeUnmount(() => { ++draftSyncSequence })
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
    ++draftSyncSequence
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
  }, { flush: 'sync' })

  return {
    sourceType,
    sourceId,
    refs,
    refRows,
    legacyOnlyRows,
    documentTypes,
    pending,
    pendingCount,
    pendingMissingTypeCount,
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

/**
 * 冲刷“某草稿键”下已存的待绑定队列：读取 localStorage，逐条建立引用（带 productCode），成功后清空该键。
 * 用于“行级附件”在单据保存成功后，由父组件统一绑定（此时行弹窗组件可能已卸载）。幂等：绑成功即从键中移除，失败保留待重试。
 */
export async function flushStoredQueue(opts: {
  userId: string | number | null | undefined
  sourceType: string
  draftId: string
  sourceId: number
  productCode?: string | null
}): Promise<{ bound: number; failed: number; remaining: number }> {
  const source = canonicalAttachmentSource(opts.sourceType)
  const prefix = `${STORAGE_PREFIX}:${opts.userId ?? 'anonymous'}:${source}:`
  // 保存时组件若仍挂载，草稿队列会迁移到带单据 ID 的行键；兼容迁移前共用整单键。
  const keys = [
    `${prefix}${opts.draftId}`,
    ...(opts.productCode ? [`${prefix}id-${opts.sourceId}::p:${opts.productCode}`,
      `${prefix}id-${opts.sourceId}`] : []),
  ]
  let bound = 0
  let failed = 0
  let remaining = 0
  for (const key of new Set(keys)) {
    let entries: any[]
    try {
      const raw = localStorage.getItem(key)
      if (!raw) continue
      const parsed = JSON.parse(raw)
      entries = Array.isArray(parsed) ? parsed.filter((e: any) => Number(e?.attachmentId) > 0) : []
    } catch {
      continue
    }
    const keep: any[] = []
    for (const e of entries) {
      // 旧整单键可能混有其他产品行；绝不把别行文件绑定到当前产品。
      if (opts.productCode && key === `${prefix}id-${opts.sourceId}`
        && e.productCode !== opts.productCode) {
        keep.push(e)
        continue
      }
      try {
        const res: any = await attachmentRefApi.createRef({
          attachmentId: Number(e.attachmentId),
          sourceType: source,
          sourceId: opts.sourceId,
          productCode: e.productCode ? String(e.productCode) : opts.productCode ?? null,
          documentType: e.documentType ?? null,
          displayName: e.displayName ?? null,
        })
        if (Number(res?.data)) bound++
        else { failed++; keep.push(e) }
      } catch {
        failed++
        keep.push(e)
      }
    }
    remaining += keep.length
    try {
      if (keep.length) localStorage.setItem(key, JSON.stringify(keep))
      else localStorage.removeItem(key)
    } catch {
      /* 存储清理失败不影响已建立的引用 */
    }
  }
  return { bound, failed, remaining }
}
