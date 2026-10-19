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
  saveDraft: vi.fn(),
  upgradeDraft: vi.fn(),
  listVersions: vi.fn(),
  publishVersion: vi.fn(),
  listRefs: vi.fn(),
  documentTypes: vi.fn(),
  removeRef: vi.fn(),
}))
const messages = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  prompt: vi.fn(),
  confirm: vi.fn(),
}))
const auth = vi.hoisted(() => ({edit:true}))
vi.mock('element-plus', async (original) => ({
  ...(await original<typeof import('element-plus')>()),
  ElMessage: messages,
  ElMessageBox: { prompt: messages.prompt, confirm: messages.confirm },
}))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ userId: 7, permissions: auth.edit ? ['*'] : ['product:view','product:spec:view','product:work-spec:publish'], roles: [], hasPermission: (permission:string) => permission !== 'product:edit' || auth.edit }),
}))
vi.mock('@/api/product/workSpec', async (original) => ({
  ...(await original<typeof import('@/api/product/workSpec')>()),
  productWorkSpecApi: api,
}))
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
  api.documentTypes.mockResolvedValue({ data: [] })
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
    const wrapper = mount(VersionPanel,{props:{productId:42},global:{directives:{hasPermi:()=>{},loading:()=>{}},stubs:{ElTable:{template:'<div />'}}}})
    wrappers.push(wrapper);await flushPromises()
    const state=(wrapper.vm as any).$.setupState
    expect(state.draft.session.canEdit.value).toBe(false)
    await state.publish()
    expect(api.saveDraft).not.toHaveBeenCalled()
    expect(api.publishVersion).toHaveBeenCalledWith(42,undefined,'r1')
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
  it('移除仅暂存，新增/改类型/清空备注/打印/排序/负责人一并保存最终集合', async () => {
    const { draft } = await setup()
    await draft.session.unbind(101)
    expect(api.removeRef).not.toHaveBeenCalled()
    draft.session.setEdit(102, { documentType: 'PANEL_DRAWING', remark: null })
    draft.register(pending(), 'STRUCTURE_DRAWING')
    draft.prints[2] = false
    draft.owner.value = 8
    draft.move(3, -1)
    expect(draft.dirty.value).toBe(true)
    await draft.save()
    expect(api.saveDraft).toHaveBeenCalledWith(
      42,
      expect.objectContaining({
        revision: 'r1',
        preparedById: 8,
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
  it('保存失败保留新增、移除、负责人和排序；重试成功才清队列', async () => {
    const { draft } = await setup()
    await draft.session.unbind(101)
    draft.register(pending(), 'STRUCTURE_DRAWING')
    draft.owner.value = 8
    api.saveDraft.mockRejectedValueOnce(new Error('revision conflict'))
    await expect(draft.save()).rejects.toThrow('revision conflict')
    expect(draft.files.value.map((file) => file.attachmentId)).toEqual([2, 3])
    expect(draft.owner.value).toBe(8)
    expect(draft.saving.value).toBe(false)
    expect(draft.session.pendingCount.value).toBe(1)
    await draft.save()
    expect(draft.session.pendingCount.value).toBe(0)
  })
  it('升级不覆盖未保存改动；成功升级采用冻结正文、文件和负责人', async () => {
    const { draft } = await setup()
    draft.owner.value = 8
    await expect(draft.upgrade(10)).rejects.toThrow('先保存或取消')
    expect(api.upgradeDraft).not.toHaveBeenCalled()
    draft.owner.value = 7
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
      'attach-pending:v2:7:product:id-42',
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
