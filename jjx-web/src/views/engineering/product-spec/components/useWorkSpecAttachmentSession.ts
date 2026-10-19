import {
  computed,
  inject,
  onBeforeUnmount,
  provide,
  reactive,
  ref,
  watch,
  type InjectionKey,
  type Ref,
} from 'vue'
import { useAttachmentSession, productOwnership } from '@/composables/useAttachmentSession'
import {
  normalizeWorkSpec,
  productWorkSpecApi,
  workSpecContent,
  type ProductWorkSpec,
  type WorkSpecAttachment,
} from '@/api/product/workSpec'
import { ENGINEERING_DOCUMENT_TYPE_CODES } from '@/components/product/productFileCategories'
import { detectFileKind } from './versionDocset'
import type { PendingEntry } from '@/composables/useAttachmentRefs'
import type { AttachmentRefItem } from '@/api/system/attachmentRef'

const key: InjectionKey<WorkSpecDraftSession> = Symbol('work-spec-draft')
export type WorkSpecDraftSession = ReturnType<typeof createWorkSpecAttachmentSession>

/** 每个产品容器一份会话；正文、结构图和图集共享完整草稿保存边界。 */
export function useWorkSpecAttachmentSession(productId: Ref<number>, productCode: Ref<string>) {
  const inherited = inject(key, undefined)
  if (inherited) return inherited
  const draft = createWorkSpecAttachmentSession(productId, productCode)
  provide(key, draft)
  return draft
}

export function createWorkSpecAttachmentSession(productId: Ref<number>, productCode: Ref<string>) {
  const spec = ref<ProductWorkSpec>(normalizeWorkSpec())
  const loading = ref(false)
  const saving = ref(false)
  const error = ref('')
  const ready = ref(false)
  const baseline = ref('')
  const owner = ref<number>()
  const prints = reactive<Record<number, boolean>>({})
  const ranks = reactive<Record<number, number>>({})
  const removed = ref<number[]>([])
  let generation = 0
  const session = useAttachmentSession({
    sourceType: 'product',
    sourceId: productId,
    rowLevel: true,
    autoLoad: false,
    editPerm: 'product:edit',
    viewPerm: 'product:view',
    locked: computed(() => loading.value || saving.value || !ready.value),
  })
  // 来源切换时公共store会刷新通用引用；业务清单单独由work-spec响应接管，避免混入其它产品资料。
  const selectedRefs = ref<AttachmentRefItem[]>([])
  session.refs = selectedRefs
  session.refRows = computed(() => selectedRefs.value)
  const ownership = computed(() =>
    productOwnership({
      rowKey: String(productId.value),
      productId: productId.value,
      productCode: productCode.value,
    })
  )
  const loadTypes = session.loadDocumentTypes
  session.loadDocumentTypes = async () => {
    const types = await loadTypes()
    session.documentTypes.value = types.filter(
      (type) =>
        type.dict_code !== 'attachment_document_type' ||
        ENGINEERING_DOCUMENT_TYPE_CODES.includes(type.item_key)
    )
    return session.documentTypes.value
  }
  // 通用移除入口只暂存；唯一落库入口是saveDraft的完整清单，不能立即调用通用unbind。
  session.unbind = async (refId) => {
    if (!session.canEdit.value) throw new Error('当前草稿不可编辑')
    const row = session.refs.value.find((file) => file.id === refId)
    if (!row) return
    removed.value.push(row.attachmentId)
    session.refs.value = session.refs.value.filter((file) => file.id !== refId)
    session.clearEdit(refId)
  }
  const setEdit = session.setEdit
  session.setEdit = (refId, patch) => {
    setEdit(refId, patch)
    if ('sortOrder' in patch) {
      const row = session.refs.value.find((file) => file.id === refId)
      if (row) delete ranks[row.attachmentId]
    }
  }
  const files = computed(() =>
    [
      ...session.refs.value.map((row) => ({
        attachmentId: row.attachmentId,
        documentType: session.effectiveDocumentType(row.id, row.documentType),
        displayName: row.displayName,
        remark: Object.prototype.hasOwnProperty.call(session.editOf(row.id) || {}, 'remark')
          ? session.editOf(row.id)?.remark
          : row.remark,
        sortOrder:
          ranks[row.attachmentId] ?? session.editOf(row.id)?.sortOrder ?? row.sortOrder ?? 0,
        fileName: row.fileName || '',
        fileType: row.fileType || '',
        fileSize: row.fileSize,
        sha256: row.sha256,
        print:
          prints[row.attachmentId] ??
          detectFileKind(row.fileName || '', row.fileType || '') !== 'other',
      })),
      ...session.pending.value.map((row, index) => ({
        attachmentId: row.attachmentId,
        documentType: row.documentType || '',
        displayName: row.displayName,
        remark: undefined,
        sortOrder: ranks[row.attachmentId] ?? session.refs.value.length + index,
        fileName: row.fileName,
        fileSize: row.fileSize,
        sha256: row.sha256,
        fileType: '',
        print: prints[row.attachmentId] ?? detectFileKind(row.fileName) !== 'other',
      })),
    ].sort((a, b) => a.sortOrder - b.sortOrder)
  )
  const manifest = computed<WorkSpecAttachment[]>(() =>
    files.value.map(({ attachmentId, documentType, displayName, remark, print }) => ({
      attachmentId,
      documentType,
      displayName,
      remark,
      print,
    }))
  )
  const signature = () => JSON.stringify({ owner: owner.value, files: manifest.value })
  const dirty = computed(() => ready.value && signature() !== baseline.value)
  const busy = computed(() => loading.value || saving.value || session.uploading.value)

  function accept(value: Partial<ProductWorkSpec>) {
    spec.value = normalizeWorkSpec(value)
    owner.value = spec.value.preparedById
    removed.value = []
    Object.keys(prints).forEach((id) => delete prints[Number(id)])
    Object.keys(ranks).forEach((id) => delete ranks[Number(id)])
    for (const entry of spec.value.draftAttachments || [])
      prints[entry.attachmentId] = entry.print !== false
    session.refs.value = (spec.value.draftFiles || []).map((file, index) => ({
      ...file,
      id: file.refId,
      sourceType: 'product',
      sourceId: productId.value,
      productId: productId.value,
      productCode: productCode.value,
      sortOrder: index,
    }))
    const ids = new Set(session.refs.value.map((file) => file.attachmentId))
    session.pending.value = session.pending.value.filter((file) => !ids.has(file.attachmentId))
    Object.keys(session.edits).forEach((id) => session.clearEdit(Number(id)))
    session.persist()
    ready.value = true
    baseline.value = JSON.stringify({
      owner: owner.value,
      files: manifest.value.filter((file) => ids.has(file.attachmentId)),
    })
  }
  async function load(discard = false) {
    if (!productId.value || busy.value || (dirty.value && !discard)) return
    const current = ++generation
    loading.value = true
    error.value = ''
    try {
      const response = await productWorkSpecApi.get(productId.value)
      if (current !== generation) return
      if (!response.data?.revision) throw new Error('未读取到作业规范草稿')
      accept(response.data)
    } catch (e) {
      if (current === generation) error.value = e instanceof Error ? e.message : '草稿加载失败'
    } finally {
      if (current === generation) loading.value = false
    }
  }
  async function save(content?: ProductWorkSpec) {
    if (!ready.value || busy.value) throw new Error('草稿尚未就绪或正在处理，请稍后重试')
    if (!session.canEdit.value) throw new Error('无草稿编辑权限')
    if (manifest.value.some((file) => !file.documentType))
      throw new Error('请为每个附件选择资料类型')
    const current = generation
    const id = productId.value
    saving.value = true
    try {
      const response = await productWorkSpecApi.saveDraft(id, {
        revision: content?.revision || spec.value.revision,
        preparedById: owner.value,
        spec: content ? workSpecContent(content) : undefined,
        attachments: manifest.value,
      })
      if (!response.data?.revision) throw new Error('草稿保存未返回有效结果')
      if (current !== generation || id !== productId.value)
        throw new Error('保存期间产品已切换，请重新加载原产品草稿确认')
      accept(response.data)
      return response
    } finally {
      if (current === generation) saving.value = false
    }
  }
  async function upgrade(versionId: number) {
    if (!ready.value || busy.value || dirty.value || session.pendingCount.value)
      throw new Error('请先保存或取消当前草稿改动')
    const current = generation
    const id = productId.value
    saving.value = true
    try {
      const response = await productWorkSpecApi.upgradeDraft(id, spec.value.revision, versionId)
      if (!response.data?.revision) throw new Error('升级草稿未返回有效结果')
      if (current !== generation || id !== productId.value)
        throw new Error('升级期间产品已切换，请重新加载原产品草稿确认')
      accept(response.data)
    } finally {
      if (current === generation) saving.value = false
    }
  }
  function register(entry: PendingEntry, documentType?: string) {
    if (session.refs.value.some((row) => row.attachmentId === entry.attachmentId)) return
    if (documentType) entry.documentType = documentType
    session.registerUploaded(entry, ownership.value)
    session.persist()
  }
  function move(attachmentId: number, delta: number) {
    if (!session.canEdit.value) return
    const ids = files.value.map((file) => file.attachmentId)
    const index = ids.indexOf(attachmentId)
    if (index < 0 || index + delta < 0 || index + delta >= ids.length) return
    ;[ids[index], ids[index + delta]] = [ids[index + delta], ids[index]]
    ids.forEach((id, index) => {
      ranks[id] = index
    })
  }
  function adopt(value: ProductWorkSpec) {
    if (!dirty.value && !busy.value) accept(value)
  }
  session.load = () => load()
  session.refresh = async () => {
    await load()
    return [undefined, undefined]
  }
  watch(
    productId,
    () => {
      generation++
      loading.value = false
      saving.value = false
      ready.value = false
      spec.value = normalizeWorkSpec()
      baseline.value = ''
      owner.value = undefined
      session.refs.value = []
      removed.value = []
      void load()
    },
    { immediate: true }
  )
  onBeforeUnmount(() => {
    generation++
  })
  return {
    session,
    ownership,
    spec,
    owner,
    files,
    manifest,
    prints,
    removed,
    dirty,
    busy,
    loading,
    saving,
    ready,
    error,
    load,
    save,
    upgrade,
    register,
    move,
    adopt,
  }
}
