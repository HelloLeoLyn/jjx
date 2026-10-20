vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/message-box/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/card/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions-item/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import {
  createWorkSpecAttachmentSession,
  useWorkSpecAttachmentSession,
  type WorkSpecDraftSession,
} from '../useWorkSpecAttachmentSession'
import { normalizeWorkSpec } from '@/api/product/workSpec'
import VersionPanel from '../ProductWorkSpecVersionPanel.vue'
const api = vi.hoisted(() => ({
  get: vi.fn(),
  getVersion: vi.fn(),
  saveDraft: vi.fn(),
  upgradeDraft: vi.fn(),
  listVersions: vi.fn(),
  publishVersion: vi.fn(),
  listRefs: vi.fn(),
  documentTypes: vi.fn(),
  removeRef: vi.fn(),
  post: vi.fn(),
  responsibleUsers: vi.fn(),
}))
const messages = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  prompt: vi.fn(),
  confirm: vi.fn(),
}))
const auth = vi.hoisted(() => ({ edit: true }))
vi.mock('element-plus', async (original) => ({
  ...(await original<typeof import('element-plus')>()),
  ElMessage: messages,
  ElMessageBox: { prompt: messages.prompt, confirm: messages.confirm },
}))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({
    userId: 7,
    permissions: auth.edit
      ? ['*']
      : ['product:view', 'product:spec:view', 'product:work-spec:publish'],
    roles: [],
    hasPermission: (permission: string) => permission !== 'product:edit' || auth.edit,
  }),
}))
vi.mock('@/api/product/workSpec', async (original) => ({
  ...(await original<typeof import('@/api/product/workSpec')>()),
  productWorkSpecApi: api,
}))
vi.mock('@/utils/request', () => ({ default: { post: api.post } }))
vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: api }))
vi.mock('@/api/system/attachment', () => ({
  attachmentApi: { list: vi.fn().mockResolvedValue({ data: [] }) },
}))
const wrappers: ReturnType<typeof mount>[] = []
const saved = (ids = [1, 2], revision = 'r1') =>
  normalizeWorkSpec({
    revision,
    draftManaged: true,
    preparedById: 7,
    preparedByName: '工程甲',
    engineeringRequirements: '原规范',
    draftAttachments: ids.map((attachmentId) => ({
      attachmentId,
      documentType: 'STRUCTURE_DRAWING',
      print: true,
    })),
    draftFiles: ids.map((attachmentId, index) => ({
      attachmentId,
      refId: attachmentId + 100,
      documentType: 'STRUCTURE_DRAWING',
      fileName: attachmentId + '.pdf',
      remark: '旧备注',
      sortOrder: index,
    })),
  })
const pending = (attachmentId = 3) => ({
  attachmentId,
  requestKey: 'test-request-key-0001',
  fileName: attachmentId + '.pdf',
  fileSize: 5,
  sha256: 'hash',
  state: 'pending' as const,
  createdAt: '2026-10-19',
})
async function setup(initialId = 42) {
  let draft!: WorkSpecDraftSession
  const id = ref(initialId)
  const wrapper = mount(
    defineComponent({
      setup() {
        draft = createWorkSpecAttachmentSession(id, ref('P42'))
        return () => null
      },
    })
  )
  wrappers.push(wrapper)
  await flushPromises()
  return { draft, id }
}
beforeEach(() => {
  auth.edit = true
  vi.clearAllMocks()
  localStorage.clear()
  api.get.mockResolvedValue({ data: saved() })
  api.documentTypes.mockResolvedValue({
    data: [
      { dict_code: 'attachment_category', item_key: 'ENGINEERING_DOC', label: '工程技术资料' },
      ...['STRUCTURE_DRAWING', 'PANEL_DRAWING', 'BOM', 'WORK_INSTRUCTION'].map((item_key) => ({
        dict_code: 'attachment_document_type',
        item_key,
        label: item_key,
        is_active: 1,
        ext_data: { parent: 'ENGINEERING_DOC' },
      })),
    ],
  })
  api.listRefs.mockResolvedValue({ data: [] })
  api.listVersions.mockResolvedValue({ data: [] })
  api.publishVersion.mockResolvedValue({ code: 200, data: { id: 10, versionNo: 'V1.0' } })
  messages.prompt.mockResolvedValue({ value: '' })
  messages.confirm.mockResolvedValue(true)
  api.saveDraft.mockImplementation(async (_id, body) => ({
    data: {
      ...saved(
        body.attachments.map((file: any) => file.attachmentId),
        'r2'
      ),
      preparedById: body.preparedById,
      draftAttachments: body.attachments,
    },
  }))
})
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))
describe('作业规范统一草稿会话', () => {
  it('仅有定版权限的人可以定版已保存草稿，无需取得编辑权限', async () => {
    auth.edit = false
    const wrapper = mount(VersionPanel, {
      props: { productId: 42 },
      global: {
        directives: { hasPermi: () => {}, loading: () => {} },
        stubs: { ElTable: { template: '<div />' } },
      },
    })
    wrappers.push(wrapper)
    await flushPromises()
    const state = (wrapper.vm as any).$.setupState
    expect(state.draft.session.canEdit.value).toBe(false)
    await state.publish()
    expect(api.saveDraft).not.toHaveBeenCalled()
    expect(api.publishVersion).toHaveBeenCalledWith(42, undefined, 'r1')
    expect(messages.success).toHaveBeenCalled()
  })
  it('先完整保存再用返回revision定版，快速连点不重复提交', async () => {
    let finish!: (value: any) => void
    api.saveDraft.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        })
    )
    const wrapper = mount(VersionPanel, {
      props: { productId: 42 },
      global: {
        directives: { hasPermi: () => {}, loading: () => {} },
        stubs: { ElTable: { template: '<div />' } },
      },
    })
    wrappers.push(wrapper)
    await flushPromises()
    const state = (wrapper.vm as any).$.setupState
    const publishing = state.publish()
    await flushPromises()
    await state.publish()
    expect(api.saveDraft).toHaveBeenCalledTimes(1)
    expect(api.publishVersion).not.toHaveBeenCalled()
    finish({ data: saved([1, 2], 'saved-r2') })
    await publishing
    expect(api.publishVersion).toHaveBeenCalledWith(42, undefined, 'saved-r2')
    expect(messages.success).toHaveBeenCalled()
  })
  it('草稿保存失败中断定版并保留待保存附件', async () => {
    const wrapper = mount(VersionPanel, {
      props: { productId: 42 },
      global: {
        directives: { hasPermi: () => {}, loading: () => {} },
        stubs: { ElTable: { template: '<div />' } },
      },
    })
    wrappers.push(wrapper)
    await flushPromises()
    const state = (wrapper.vm as any).$.setupState
    state.draft.register(pending(), 'STRUCTURE_DRAWING')
    api.saveDraft.mockRejectedValueOnce(new Error('save conflict'))
    await state.publish()
    expect(api.publishVersion).not.toHaveBeenCalled()
    expect(messages.prompt).not.toHaveBeenCalled()
    expect(state.draft.session.pendingCount.value).toBe(1)
    expect(state.publishing).toBe(false)
  })
  it('移除仅暂存，新增/改类型/清空备注/打印/排序一并保存最终集合', async () => {
    const { draft } = await setup()
    await draft.session.unbind(101)
    expect(api.removeRef).not.toHaveBeenCalled()
    draft.session.setEdit(102, { documentType: 'PANEL_DRAWING', remark: null })
    draft.register(pending(), 'STRUCTURE_DRAWING')
    draft.prints[2] = false
    draft.move(3, -1)
    expect(draft.dirty.value).toBe(true)
    await draft.save()
    expect(api.saveDraft).toHaveBeenCalledWith(
      42,
      expect.objectContaining({
        revision: 'r1',
        preparedById: 7,
        attachments: [
          expect.objectContaining({
            attachmentId: 3,
            documentType: 'STRUCTURE_DRAWING',
            print: true,
          }),
          expect.objectContaining({
            attachmentId: 2,
            documentType: 'PANEL_DRAWING',
            remark: null,
            print: false,
          }),
        ],
      })
    )
    expect(draft.session.pending.value).toEqual([])
    expect(draft.dirty.value).toBe(false)
  })
  it('保存失败保留新增、移除和排序；重试成功才清队列', async () => {
    const { draft } = await setup()
    await draft.session.unbind(101)
    draft.register(pending(), 'STRUCTURE_DRAWING')
    api.saveDraft.mockRejectedValueOnce(new Error('revision conflict'))
    await expect(draft.save()).rejects.toThrow('revision conflict')
    expect(draft.files.value.map((file) => file.attachmentId)).toEqual([2, 3])
    expect(draft.owner.value).toBe(7)
    expect(draft.saving.value).toBe(false)
    expect(draft.session.pendingCount.value).toBe(1)
    await draft.save()
    expect(draft.session.pendingCount.value).toBe(0)
  })
  it('升级不覆盖未保存改动；成功升级采用冻结正文、文件和负责人', async () => {
    const { draft } = await setup()
    draft.move(2, -1)
    await expect(draft.upgrade(10)).rejects.toThrow('先保存或取消')
    expect(api.upgradeDraft).not.toHaveBeenCalled()
    draft.move(2, 1)
    api.upgradeDraft.mockResolvedValue({
      data: { ...saved([4, 5], 'r3'), preparedById: 8, preparedByName: '工程乙' },
    })
    await draft.upgrade(10)
    expect(api.upgradeDraft).toHaveBeenCalledWith(42, 'r1', 10)
    expect(draft.files.value.map((file) => file.attachmentId)).toEqual([4, 5])
    expect(draft.spec.value.engineeringRequirements).toBe('原规范')
    expect(draft.owner.value).toBe(8)
  })
  it('迟到的通用附件加载不能覆盖工作规范清单，切产品不会永久loading', async () => {
    let resolveOld!: (value: any) => void
    let resolveRefs!: (value: any) => void
    api.listRefs.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveRefs = resolve
        })
    )
    api.get
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve
          })
      )
      .mockResolvedValue({ data: saved([4, 5], 'new') })
    const { draft, id } = await setup(1)
    expect(draft.loading.value).toBe(true)
    id.value = 42
    await flushPromises()
    expect(draft.loading.value).toBe(false)
    expect(draft.files.value.map((file) => file.attachmentId)).toEqual([4, 5])
    resolveRefs({
      data: [
        {
          id: 999,
          attachmentId: 999,
          documentType: 'CUSTOMER_ORIGINAL',
          fileName: 'unrelated.pdf',
        },
      ],
    })
    await flushPromises()
    expect(draft.files.value.map((file) => file.attachmentId)).toEqual([4, 5])
    resolveOld({ data: saved([1, 2], 'old') })
    await flushPromises()
    expect(draft.spec.value.revision).toBe('new')
    expect(draft.files.value.map((file) => file.attachmentId)).toEqual([4, 5])
  })
  it('刷新恢复的待保存附件必须仍算未保存，不能阻止保存或假装已保存', async () => {
    localStorage.setItem(
      'attach-pending:v2:7:work_spec:id-42',
      JSON.stringify([{ ...pending(), documentType: 'STRUCTURE_DRAWING' }])
    )
    const { draft } = await setup()
    expect(draft.session.pendingCount.value).toBe(1)
    expect(draft.dirty.value).toBe(true)
  })
  it('相同上传幂等返回已绑定附件ID时不重复加入清单', async () => {
    const { draft } = await setup()
    draft.register(pending(1), 'STRUCTURE_DRAWING')
    expect(draft.manifest.value.map((file) => file.attachmentId)).toEqual([1, 2])
    expect(draft.session.pendingCount.value).toBe(0)
  })
  it('同一容器的结构图/工程图集子组件复用同一个会话', async () => {
    let parent!: WorkSpecDraftSession
    let child!: WorkSpecDraftSession
    const Child = defineComponent({
      setup() {
        child = useWorkSpecAttachmentSession(ref(42), ref('P42'))
        return () => null
      },
    })
    const wrapper = mount(
      defineComponent({
        components: { Child },
        setup() {
          parent = useWorkSpecAttachmentSession(ref(42), ref('P42'))
          return {}
        },
        template: '<Child />',
      })
    )
    wrappers.push(wrapper)
    await flushPromises()
    expect(child).toBe(parent)
    child.register(pending(), 'STRUCTURE_DRAWING')
    expect(parent.session.pendingCount.value).toBe(1)
  })
})

const snapshot = (ids: number[]) => ({
  data: {
    items: [
      {
        resourceType: 'DOCSET_JSON',
        snapshotJson: JSON.stringify({
          schemaVersion: 1,
          spec: {},
          counts: { files: ids.length },
          files: ids.map((attachmentId) => ({
            attachmentId,
            fileName: attachmentId + '.pdf',
            section: 'atlas',
          })),
        }),
      },
    ],
  },
})
async function versionPanel() {
  const wrapper = mount(VersionPanel, {
    props: { productId: 42 },
    global: {
      directives: { hasPermi: () => {}, loading: () => {} },
      stubs: { ElTable: { template: '<div />' } },
    },
  })
  wrappers.push(wrapper)
  await flushPromises()
  return (wrapper.vm as any).$.setupState
}
describe('定版差异使用已保存草稿与冻结基版', () => {
  it.each(['先保存再定版', '重开后定版'])('%s仍列出新增C、移除A和真实顺序', async (scenario) => {
    api.listVersions.mockResolvedValue({ data: [{ id: 10 }] })
    api.getVersion.mockResolvedValue(snapshot([1, 2]))
    if (scenario === '重开后定版') api.get.mockResolvedValue({ data: saved([2, 3]) })
    const state = await versionPanel()
    if (scenario === '先保存再定版') {
      await state.draft.session.unbind(101)
      state.draft.register(pending(), 'STRUCTURE_DRAWING')
      await state.draft.save()
    }
    expect(state.draft.session.pendingCount.value).toBe(0)
    await state.publish()
    expect(messages.prompt).toHaveBeenCalledWith(
      expect.stringContaining('新增 1（3.pdf），移除 1（1.pdf）'),
      '规范定版',
      expect.objectContaining({ confirmButtonText: '定版' })
    )
    expect(messages.prompt.mock.calls[0][0]).toContain('工程图集：2.pdf → 3.pdf')
    expect(api.publishVersion).toHaveBeenCalledWith(42, undefined, 'r2')
  })
  it('第一次定版列全新清单；定版等待期间发出busy且冻结附件编辑', async () => {
    const state = await versionPanel()
    let finish!: (value: any) => void
    messages.prompt.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        })
    )
    const publish = state.publish()
    await flushPromises()
    expect(messages.prompt.mock.calls[0][0]).toContain(
      '首次定版，全新清单：新增 2（1.pdf、2.pdf），移除 0（无）'
    )
    expect(state.draft.session.canEdit.value).toBe(false)
    expect(state.draft.busy.value).toBe(true)
    finish({ value: '' })
    await publish
    expect(state.draft.busy.value).toBe(false)
  })
  it('仅重排、无变化重试都不会伪报附件增删', async () => {
    api.listVersions.mockResolvedValue({ data: [{ id: 10 }] })
    api.getVersion.mockResolvedValue(snapshot([1, 2]))
    const state = await versionPanel()
    state.draft.move(2, -1)
    api.get.mockResolvedValue({ data: saved([2, 1], 'r2') })
    await state.publish()
    await state.publish()
    for (const call of messages.prompt.mock.calls) {
      expect(call[0]).toContain('新增 0（无），移除 0（无）')
      expect(call[0]).toContain('工程图集：2.pdf → 1.pdf')
    }
  })
})
describe('持续严格revision冲突恢复', () => {
  it('重复旧revision不成功；取消比较保留输入；显式比较重新应用才保存上传身份和本地正文', async () => {
    let server = saved([1, 2], 'server-r2')
    const { draft } = await setup()
    api.get.mockImplementation(async () => ({ data: server }))
    api.saveDraft.mockImplementation(async (_id, body) => {
      if (body.revision !== server.revision)
        throw new Error('工程规范已被其他入口修改，请重新加载后编辑')
      server = {
        ...saved(
          body.attachments.map((file: any) => file.attachmentId),
          'server-r3'
        ),
        ...body.spec,
        draftAttachments: body.attachments,
        preparedById: body.preparedById,
      }
      return { data: server }
    })
    await draft.session.unbind(101)
    draft.register(pending(), 'STRUCTURE_DRAWING')
    draft.move(3, -1)
    const text = normalizeWorkSpec({ ...draft.spec.value, engineeringRequirements: '本地文字' })
    const retry = { ownerTransferConfirmed: true }
    await expect(draft.save(text)).rejects.toThrow('其他入口修改')
    await expect(draft.save(text)).rejects.toThrow('其他入口修改')
    server = { ...saved([1, 2, 4], 'server-r2'), engineeringRequirements: '他人文字' }
    messages.confirm.mockRejectedValueOnce('cancel')
    await expect(draft.recover()).rejects.toBe('cancel')
    expect(draft.spec.value.revision).toBe('r1')
    expect(draft.session.pendingCount.value).toBe(1)
    await draft.recover()
    expect(messages.confirm.mock.lastCall?.[0]).toContain('4.pdf')
    expect(messages.confirm.mock.lastCall?.[0]).toContain('他人文字')
    expect(messages.confirm.mock.lastCall?.[0]).toContain('本地文字')
    expect(api.saveDraft).toHaveBeenLastCalledWith(
      42,
      expect.objectContaining({
        revision: 'server-r2',
        preparedById: 7,
        spec: expect.objectContaining({ engineeringRequirements: '本地文字' }),
        attachments: [
          expect.objectContaining({ attachmentId: 3 }),
          expect.objectContaining({ attachmentId: 2 }),
        ],
      })
    )
    expect(draft.conflict.value).toBe(false)
    expect(draft.session.pendingCount.value).toBe(0)
  })
  it('放弃并重读只读服务器、不回写旧清单；并发再次变化时重新应用仍失败且保留本地', async () => {
    const { draft } = await setup()
    draft.register(pending(), 'STRUCTURE_DRAWING')
    api.saveDraft.mockRejectedValue(new Error('revision conflict'))
    await expect(draft.save()).rejects.toThrow('conflict')
    api.get.mockResolvedValue({ data: saved([2, 4], 'server-r2') })
    await expect(draft.recover()).rejects.toThrow('conflict')
    expect(draft.session.pendingCount.value).toBe(1)
    expect(draft.conflict.value).toBe(true)
    const writes = api.saveDraft.mock.calls.length
    await draft.recover(true)
    expect(api.saveDraft).toHaveBeenCalledTimes(writes)
    expect(draft.spec.value.revision).toBe('server-r2')
    expect(draft.files.value.map((file) => file.attachmentId)).toEqual([2, 4])
    expect(draft.session.pendingCount.value).toBe(0)
  })
})

describe('后端工程技术资料字典', () => {
  it('承接固定列表以外的有效二级类型，不放开其它父类；停用类型阻止保存且保留附件', async () => {
    api.documentTypes.mockResolvedValue({
      data: [
        { dict_code: 'attachment_category', item_key: 'ENGINEERING_DOC', label: '工程技术资料' },
        { dict_code: 'attachment_category', item_key: 'CUSTOMER_DOC', label: '客供资料' },
        ...[
          'STRUCTURE_DRAWING',
          'BOM',
          'PROCESS_ROUTE',
          'WORK_INSTRUCTION',
          'DIE_CUTTING_PARAMETER',
          'PRINTING_PROCESS',
          'INK_MIXING_RECORD',
          'COLOR_CHECK',
        ].map((item_key) => ({
          dict_code: 'attachment_document_type',
          item_key,
          label: item_key,
          is_active: 1,
          ext_data: JSON.stringify({ parent: 'ENGINEERING_DOC' }),
        })),
        {
          dict_code: 'attachment_document_type',
          item_key: 'DISABLED_TYPE',
          label: '已停用',
          is_active: 0,
          ext_data: { parent: 'ENGINEERING_DOC' },
        },
        {
          dict_code: 'attachment_document_type',
          item_key: 'CUSTOMER_ORIGINAL',
          label: '客户资料',
          is_active: 1,
          ext_data: { parent: 'CUSTOMER_DOC' },
        },
      ],
    })
    const { draft } = await setup()
    await draft.session.loadDocumentTypes()
    expect(draft.engineeringTypes.value.map((type) => type.item_key)).toContain('WORK_INSTRUCTION')
    expect(draft.engineeringTypes.value.map((type) => type.item_key)).toContain('BOM')
    expect(draft.engineeringTypes.value.map((type) => type.item_key)).not.toContain(
      'CUSTOMER_ORIGINAL'
    )
    expect(draft.engineeringTypes.value.map((type) => type.item_key)).not.toContain('DISABLED_TYPE')
    draft.register(pending(), 'WORK_INSTRUCTION')
    await draft.save()
    expect(api.saveDraft).toHaveBeenCalledWith(
      42,
      expect.objectContaining({
        attachments: expect.arrayContaining([
          expect.objectContaining({ attachmentId: 3, documentType: 'WORK_INSTRUCTION' }),
        ]),
      })
    )
    draft.register(pending(4), 'DISABLED_TYPE')
    await expect(draft.save()).rejects.toThrow('资料类型已停用')
    expect(draft.session.pendingCount.value).toBe(1)
    draft.session.pending.value[0].documentType = 'CUSTOMER_ORIGINAL'
    await expect(draft.save()).rejects.toThrow('非工程技术资料')
  })
})

describe('制定负责人锁（dev-20261020-013）', () => {
  it('非负责人只读：保存被拒，附件会话不可编辑', async () => {
    api.get.mockResolvedValue({
      data: { ...saved(), preparedById: 8, preparedByName: '工程乙' },
    })
    const { draft } = await setup()
    expect(draft.isOwner.value).toBe(false)
    expect(draft.readOnlyReason.value).toBe('当前由 工程乙 制定，你只能查看')
    expect(draft.session.canEdit.value).toBe(false)
    await expect(draft.save()).rejects.toThrow('你只能查看')
    expect(api.saveDraft).not.toHaveBeenCalled()
  })
  it('显式制定后才能编辑，未指定负责人不能通过保存自动占用', async () => {
    api.get.mockResolvedValue({ data: normalizeWorkSpec({ revision: 'r0' }) })
    const { draft } = await setup()
    expect(draft.stateLabel.value).toBe('待制定')
    expect(draft.canClaim.value).toBe(true)
    expect(draft.session.canEdit.value).toBe(false)
    await expect(draft.save()).rejects.toThrow()
    api.post.mockResolvedValue({ data: saved() })
    await draft.claim()
    expect(api.post).toHaveBeenCalledWith('/product/42/work-spec/claim')
    expect(draft.stateLabel.value).toBe('制定中')
    expect(draft.session.canEdit.value).toBe(true)
    expect(api.saveDraft).not.toHaveBeenCalled()
  })
  it('转交是独立确认操作，不提交附件和正文，转交后立即只读', async () => {
    const { draft } = await setup()
    api.responsibleUsers.mockResolvedValue({ data: [{ id: 8, name: '工程乙' }] })
    messages.confirm.mockImplementationOnce(async (render) => {
      render().props['onUpdate:modelValue'](8)
      return true
    })
    api.post.mockResolvedValue({ data: { ...saved(), preparedById: 8, preparedByName: '工程乙' } })
    await draft.transfer()
    expect(messages.confirm).toHaveBeenCalled()
    expect(api.post).toHaveBeenCalledWith('/product/42/work-spec/transfer', {
      revision: 'r1',
      preparedById: 8,
      ownerTransferConfirmed: true,
    })
    expect(api.saveDraft).not.toHaveBeenCalled()
    expect(draft.session.canEdit.value).toBe(false)
    expect(draft.readOnlyReason.value).toContain('工程乙')
  })
  it('无编辑权限不能制定；定版后不能保存或转交', async () => {
    auth.edit = false
    api.get.mockResolvedValue({ data: normalizeWorkSpec({ revision: 'r0' }) })
    const { draft } = await setup()
    expect(draft.canClaim.value).toBe(false)
    await expect(draft.claim()).rejects.toThrow()
    auth.edit = true
    api.get.mockResolvedValue({ data: { ...saved(), finalizedVersionId: 10 } })
    await draft.load(true)
    expect(draft.stateLabel.value).toBe('已定版')
    expect(draft.canTransfer.value).toBe(false)
    expect(draft.session.canEdit.value).toBe(false)
    await expect(draft.save()).rejects.toThrow()
  })
})

describe('保存前附件类型与待上传汇总校验（dev-20261020-025）', () => {
  it('未选类型按文件名阻止保存；同一会话补选后直接保存，无需刷新', async () => {
    const { draft } = await setup()
    draft.session.setEdit(101, { documentType: null })
    await expect(draft.save()).rejects.toThrow('以下 1 个附件未选择资料类型：1.pdf')
    expect(api.saveDraft).not.toHaveBeenCalled()
    draft.session.setEdit(101, { documentType: 'STRUCTURE_DRAWING' })
    await draft.save()
    expect(api.saveDraft).toHaveBeenCalledTimes(1)
    expect(draft.session.pendingCount.value).toBe(0)
  })
  it('多条缺失一次性汇总：列出前5个文件名，其余只报等N个', async () => {
    const { draft } = await setup()
    for (let index = 0; index < 7; index += 1) {
      draft.register({ ...pending(10 + index), fileName: `p${index + 1}.pdf` })
    }
    await expect(draft.save()).rejects.toThrow(
      '以下 7 个附件未选择资料类型：p1.pdf、p2.pdf、p3.pdf、p4.pdf、p5.pdf 等 2 个'
    )
    expect(api.saveDraft).not.toHaveBeenCalled()
  })
  it('类型停用或非工程技术资料时按文件名阻止，不调用保存接口', async () => {
    const { draft } = await setup()
    draft.register(pending(), 'NOT_ENGINEERING_TYPE')
    await expect(draft.save()).rejects.toThrow(
      '以下 1 个附件资料类型已停用或非工程技术资料，请重新选择：3.pdf'
    )
    expect(api.saveDraft).not.toHaveBeenCalled()
  })
  it('上传未完成或待绑定附件按数量阻止，提示具体总数', async () => {
    const { draft } = await setup()
    draft.register(pending(), 'STRUCTURE_DRAWING')
    draft.session.pending.value[0].state = 'failed'
    draft.session.uploadingCount.value = 1
    await expect(draft.save()).rejects.toThrow('仍有 2 个附件上传未完成或尚未绑定')
    expect(api.saveDraft).not.toHaveBeenCalled()
  })
  it('字典不可用时提前阻止并给出同口径提示', async () => {
    const { draft } = await setup()
    api.documentTypes.mockRejectedValueOnce(new Error('dict down'))
    draft.session.setEdit(101, { documentType: null })
    await expect(draft.save()).rejects.toThrow('资料类型字典不可用，请重新加载后再保存')
    expect(api.saveDraft).not.toHaveBeenCalled()
  })
  it('只读/非负责人优先报权限原因，不进入附件校验', async () => {
    api.get.mockResolvedValue({
      data: { ...saved(), preparedById: 8, preparedByName: '工程乙' },
    })
    const { draft } = await setup()
    draft.session.edits[101] = { documentType: null }
    const check = await draft.validateBeforeSave()
    expect(check).toEqual({ ok: false, message: '当前由 工程乙 制定，你只能查看' })
    expect(check.message).not.toContain('未选择资料类型')
  })
})
