import {
  computed,
  h,
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
import { ElMessageBox, ElSelect, ElOption } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/store/modules/user'
import { useAttachmentDocTypes } from '@/components/attachment/useAttachmentDocTypes'
import { detectFileKind } from './versionDocset'
import type { PendingEntry } from '@/composables/useAttachmentRefs'
import { attachmentRefApi, type AttachmentRefItem } from '@/api/system/attachmentRef'

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
  const user = useUserStore()
  const loading = ref(false)
  const saving = ref(false)
  const confirming = ref(false)
  const error = ref('')
  const ready = ref(false)
  const conflict = ref(false)
  const recovered = ref(0)
  const recoveryContent = ref<(attempt?: ProductWorkSpec) => ProductWorkSpec | undefined>()
  let failedContent: ProductWorkSpec | undefined
  const baseline = ref('')
  const owner = computed(() => spec.value.preparedById)
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
    locked: computed(() => loading.value || saving.value || confirming.value || !ready.value),
  })
  // 状态由定版标记和负责人派生，复用既有版本枚举之外不引入状态码。
  const finalized = computed(() => !!spec.value.finalizedVersionId)
  const isOwner = computed(
    () => !!spec.value.preparedById && spec.value.preparedById === user.userId
  )
  const canWrite = computed(() => isOwner.value && !finalized.value)
  const canClaim = computed(
    () =>
      ready.value &&
      !spec.value.preparedById &&
      !finalized.value &&
      user.hasPermission('product:edit')
  )
  const canTransfer = computed(
    () =>
      ready.value &&
      !!spec.value.preparedById &&
      !finalized.value &&
      user.hasPermission('product:edit') &&
      (isOwner.value ||
        user.roles.includes('admin') ||
        user.roles.includes('engineering:all') ||
        user.permissions.includes('*') ||
        user.permissions.includes('*:*:*'))
  )
  const transferLabel = computed(() => (isOwner.value ? '转交' : '改派'))
  const stateLabel = computed(() =>
    finalized.value ? '已定版' : spec.value.preparedById ? '制定中' : '待制定'
  )
  const ownerLabel = computed(() => spec.value.preparedByName || '未指定')
  const readOnlyReason = computed(() =>
    finalized.value
      ? '作业规范已定版，请升级新版本后修改'
      : !spec.value.preparedById
        ? '作业规范待制定，请有权限的用户点击制定规范'
        : isOwner.value
          ? ''
          : `当前由 ${ownerLabel.value} 制定，你只能查看`
  )
  // 只读锁同样作用于附件管理区：非负责人看得到清单，但不能上传/移除/排序/改元数据。
  const baseCanEdit = session.canEdit
  session.canEdit = computed(() => baseCanEdit.value && canWrite.value)
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
  const docTypes = useAttachmentDocTypes(session.documentTypes, () => 'product')
  const engineeringTypes = computed(() =>
    docTypes.selectableTypes.value.filter(
      (type) => docTypes.categoryKeyOfType(type.item_key) === 'ENGINEERING_DOC'
    )
  )
  session.loadDocumentTypes = async () => {
    const response = await attachmentRefApi.documentTypes()
    if (!Array.isArray(response.data) || !response.data.length)
      throw new Error('工程资料字典不可用，请稍后重试，改动已保留')
    session.documentTypes.value = response.data
    // 只保留后端ENGINEERING_DOC父类及其二级项；停用项仍保留用于显式识别失效类型。
    session.documentTypes.value = session.documentTypes.value.filter((type) =>
      type.dict_code === 'attachment_category'
        ? type.item_key === 'ENGINEERING_DOC'
        : docTypes.categoryKeyOfType(type.item_key) === 'ENGINEERING_DOC'
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
  const busy = computed(
    () => loading.value || saving.value || confirming.value || session.uploading.value
  )

  function accept(value: Partial<ProductWorkSpec>) {
    conflict.value = false
    error.value = ''
    failedContent = undefined
    spec.value = normalizeWorkSpec(value)
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
    if (!isOwner.value) throw new Error(readOnlyReason.value)
    if (!session.canEdit.value) throw new Error('无草稿编辑权限')
    if (manifest.value.some((file) => !file.documentType))
      throw new Error('请为每个附件选择资料类型')
    const current = generation
    const id = productId.value
    saving.value = true
    try {
      await session.loadDocumentTypes()
      const active = new Set(engineeringTypes.value.map((type) => type.item_key))
      const invalid = files.value.filter((file) => !active.has(file.documentType || ''))
      if (invalid.length)
        throw new Error(
          '资料类型已停用、非工程技术资料或字典不可用，请重新选择：' +
            invalid.map((file) => file.displayName || file.fileName).join('、')
        )
      const payload = {
        revision: content?.revision || spec.value.revision,
        preparedById: spec.value.preparedById,
        spec: content ? workSpecContent(content) : undefined,
        attachments: manifest.value,
      }
      const response = await productWorkSpecApi.saveDraft(
        id,
        payload as Parameters<typeof productWorkSpecApi.saveDraft>[1]
      )
      if (!response.data?.revision) throw new Error('草稿保存未返回有效结果')
      if (current !== generation || id !== productId.value)
        throw new Error('保存期间产品已切换，请重新加载原产品草稿确认')
      accept(response.data)
      return response
    } catch (cause) {
      failedContent = content ? normalizeWorkSpec(JSON.parse(JSON.stringify(content))) : undefined
      const message = cause instanceof Error ? cause.message : String(cause)
      if (/revision|冲突|其他入口修改|草稿已变化/i.test(message)) {
        conflict.value = true
        error.value = '草稿已被其他入口修改。本地输入和上传已保留，请比较后重新应用或放弃重读。'
      }
      throw cause
    } finally {
      if (current === generation) saving.value = false
    }
  }
  async function ownershipAction(target?: number, targetName?: string) {
    if (busy.value || dirty.value || session.pendingCount.value)
      throw new Error('请先保存或取消当前草稿改动')
    if (target === undefined ? !canClaim.value : !canTransfer.value)
      throw new Error('当前不可制定或转交/改派')
    const current = generation
    const id = productId.value
    const revision = spec.value.revision
    if (target !== undefined)
      await ElMessageBox.confirm(
        `${transferLabel.value}：${ownerLabel.value} → ${targetName || '新负责人'}。更换后原负责人立即只能查看，确认继续？`,
        transferLabel.value,
        { type: 'warning', confirmButtonText: '确认' + transferLabel.value }
      )
    if (current !== generation || id !== productId.value || revision !== spec.value.revision)
      throw new Error('确认期间产品或草稿已变化，请重新操作')
    if (busy.value || dirty.value || session.pendingCount.value)
      throw new Error('请先保存或取消当前草稿改动')
    saving.value = true
    try {
      const response =
        target === undefined
          ? await request.post(`/product/${id}/work-spec/claim`)
          : await request.post(`/product/${id}/work-spec/transfer`, {
              revision,
              preparedById: target,
              ownerTransferConfirmed: true,
            })
      if (current !== generation || id !== productId.value)
        throw new Error('产品已切换，请重新加载')
      if (!response.data?.revision) throw new Error('负责人操作未返回有效结果')
      accept(response.data)
    } finally {
      if (current === generation) saving.value = false
    }
  }
  async function transfer() {
    // 用独立确认操作转交，不与草稿正文或附件保存混在一起。
    if (!canTransfer.value || busy.value || dirty.value || session.pendingCount.value)
      throw new Error('当前不可转交/改派，请先保存或取消草稿改动')
    const current = generation
    const id = productId.value
    const response = await productWorkSpecApi.responsibleUsers(id)
    const users = (response.data || []) as { id: number; name: string }[]
    const choices = users.filter((person) => person.id !== spec.value.preparedById)
    const selected = ref<number>()
    await ElMessageBox.confirm(
      () =>
        h(
          ElSelect,
          {
            modelValue: selected.value,
            'onUpdate:modelValue': (value: number) => {
              selected.value = value
            },
            placeholder: '请选择新负责人',
            filterable: true,
          },
          () =>
            choices.map((person) =>
              h(ElOption, { key: person.id, value: person.id, label: person.name })
            )
        ),
      transferLabel.value + '作业规范',
      { confirmButtonText: '下一步', cancelButtonText: '取消' }
    )
    if (current !== generation || id !== productId.value) throw new Error('产品已切换，请重新加载')
    const target = choices.find((person) => person.id === selected.value)
    if (!target) throw new Error('请选择有效的新负责人')
    await ownershipAction(target.id, target.name)
  }

  async function recover(discard = false) {
    if (!conflict.value || busy.value) return
    const current = generation
    const id = productId.value
    const localContent = recoveryContent.value?.(failedContent) || failedContent
    loading.value = true
    try {
      const response = await productWorkSpecApi.get(id)
      if (current !== generation || id !== productId.value) return
      if (!response.data?.revision) throw new Error('未读取到最新草稿，改动已保留')
      const latest = normalizeWorkSpec(response.data)
      const names = (
        rows: { displayName?: string | null; fileName?: string; attachmentId: number }[]
      ) =>
        rows
          .map((file) => file.displayName || file.fileName || String(file.attachmentId))
          .join(' → ') || '无'
      await ElMessageBox.confirm(
        discard
          ? '放弃本地文字、附件增删和排序改动，重读服务端草稿？'
          : `服务端附件：${names(latest.draftFiles || [])}；负责人：${latest.preparedByName || latest.preparedById || '未指定'}。\n本地附件：${names(files.value)}；负责人：${owner.value || '未指定'}。\n服务端正文：${JSON.stringify(workSpecContent(latest))}\n本地正文：${JSON.stringify(workSpecContent(localContent || spec.value))}\n重新应用将以本地完整附件清单${localContent ? '和正文' : ''}替换以上服务端内容，可能移除他人新增附件。请核对后明确确认；取消则保留双方内容。`,
        discard ? '放弃并重读' : '冲突比较 · 重新应用本地改动',
        {
          confirmButtonText: discard ? '放弃并重读' : '确认替换并保存',
          cancelButtonText: '继续保留',
          type: 'warning',
        }
      )
      if (current !== generation || id !== productId.value) return
      if (discard) {
        session.pending.value = []
        accept(latest)
        error.value = ''
        recovered.value++
      } else {
        // 只在用户看过两份内容并明确确认后使用最新revision；请求仍接受后端严格并发校验。
        const local = localContent
          ? normalizeWorkSpec({ ...localContent, revision: latest.revision })
          : undefined
        loading.value = false
        // 附件独立保存不覆盖服务端正文；正文提交保持完整的本地输入。
        const originalRevision = spec.value.revision
        spec.value.revision = latest.revision
        try {
          // 保存重试仍只保存正文和附件，不变更负责人。
          await save(local)
          error.value = ''
          recovered.value++
        } catch (cause) {
          spec.value.revision = originalRevision
          throw cause
        }
      }
    } finally {
      if (current === generation) loading.value = false
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
    engineeringTypes,
    spec,
    owner,
    isOwner,
    canWrite,
    canClaim,
    canTransfer,
    transferLabel,
    stateLabel,
    finalized,
    claim: () => ownershipAction(),
    transfer,
    ownerLabel,
    readOnlyReason,
    files,
    manifest,
    prints,
    removed,
    dirty,
    busy,
    loading,
    saving,
    confirming,
    ready,
    error,
    conflict,
    recovered,
    recoveryContent,
    recover,
    load,
    save,
    upgrade,
    register,
    move,
    adopt,
  }
}
