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
import EngineeringDrawingLibrary from '../EngineeringDrawingLibrary.vue'
const api = vi.hoisted(() => ({
  get: vi.fn(),
  saveDraft: vi.fn(),
  responsibleUsers: vi.fn(),
  history: vi.fn(),
  documentTypes: vi.fn(),
  listRefs: vi.fn(),
}))
const messages = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
  warning: vi.fn(),
  confirm: vi.fn(),
}))
vi.mock('@/api/product/workSpec', async (original) => ({
  ...(await original<typeof import('@/api/product/workSpec')>()),
  productWorkSpecApi: api,
}))
vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: api }))
vi.mock('@/api/system/attachment', () => ({
  attachmentApi: { list: async () => ({ data: [] }) },
}))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ userId: 7, permissions: ['*'], roles: [], hasPermission: () => true }),
}))
vi.mock('element-plus', async (original) => ({
  ...(await original<typeof import('element-plus')>()),
  ElMessage: messages,
  ElMessageBox: { confirm: messages.confirm },
}))
vi.mock('@/components/attachment/AttachmentUpload.vue', () => ({
  default: {
    props: ['session', 'ownership', 'disabled', 'blockedReason'],
    template:
      "<button class=\"upload-stub\" :disabled=\"disabled\" :data-blocked-reason=\"blockedReason\" @click=\"$emit('uploaded',{pending:{attachmentId:3,requestKey:'request-0000000001',fileName:'new.pdf',fileSize:3,state:'pending',createdAt:'2026-10-19'}})\">上传附件</button>",
  },
}))
vi.mock('@/components/attachment/AttachmentManager.vue', () => ({
  default: {
    props: ['session'],
    template:
      '<div class="manager-stub"><button v-for="row in session.refs.value" :key="row.id" class="remove-stub" @click="session.unbind(row.id)">移除{{row.fileName}}</button></div>',
  },
}))
vi.mock('@/views/engineering/product-spec/components/ProductWorkSpecVersionPanel.vue', () => ({
  default: { template: '<div />' },
}))
const wrappers: ReturnType<typeof mount>[] = []
function response(ids = [1, 2], revision = 'r1', documentType = 'STRUCTURE_DRAWING') {
  return {
    data: {
      revision,
      draftManaged: true,
      preparedById: 7,
      preparedByName: '工程甲',
      draftAttachments: ids.map((attachmentId) => ({
        attachmentId,
        documentType,
        print: true,
      })),
      draftFiles: ids.map((attachmentId) => ({
        refId: attachmentId + 100,
        attachmentId,
        fileName: attachmentId + '.pdf',
        documentType,
      })),
    },
  }
}
beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  api.get.mockResolvedValue(response())
  api.documentTypes.mockResolvedValue({
    data: [
      { dict_code: 'attachment_category', item_key: 'ENGINEERING_DOC', label: '工程技术资料' },
      ...['STRUCTURE_DRAWING', 'PANEL_DRAWING', 'BOM', 'WORK_INSTRUCTION'].map((item_key) => ({
        dict_code: 'attachment_document_type',
        item_key,
        label: item_key,
        is_active: 1,
        ext_data: { parent: 'ENGINEERING_DOC', sources: ['product', 'work_spec'] },
      })),
    ],
  })
  api.listRefs.mockResolvedValue({ data: [] })
  api.responsibleUsers.mockResolvedValue({ data: [{ id: 7, name: '工程甲' }] })
  api.saveDraft.mockImplementation(async (_id, body) =>
    response(
      body.attachments.map((f: any) => f.attachmentId),
      'r2'
    )
  )
  messages.confirm.mockResolvedValue(true)
})
afterEach(() => wrappers.splice(0).forEach((w) => w.unmount()))
async function setup() {
  const w = mount(EngineeringDrawingLibrary, {
    props: { productId: 42, productCode: 'P42', productName: '产品' },
    global: { directives: { loading: () => {} } },
  })
  wrappers.push(w)
  await flushPromises()
  return w
}
describe('工程图集采用作业规范草稿', () => {
  it('新上传会话使用work_spec，读取清单保留历史product和新来源', async () => {
    const value = response()
    Object.assign(value.data.draftFiles[0], { sourceType: 'product' })
    Object.assign(value.data.draftFiles[1], { sourceType: 'work_spec' })
    api.get.mockResolvedValue(value)
    const w = await setup()
    const draft = (w.vm as any).$.setupState.draft
    expect(draft.session.refs.value.map((r: any) => r.sourceType)).toEqual(['product', 'work_spec'])
    expect(w.find('.manager-stub').attributes('source-type')).toBe('work_spec')
    expect(draft.engineeringTypes.value.length).toBe(4)
  })
  it('按内容hash提示同内容异名重复，同名异内容不误报', async () => {
    const w = await setup()
    const draft = (w.vm as any).$.setupState.draft
    draft.session.refs.value[0].sha256 = 'same-content'
    const state = (w.vm as any).$.setupState
    state.uploaded({
      pending: {
        attachmentId: 3,
        requestKey: 'key-0000000000001',
        fileName: 'renamed.pdf',
        fileSize: 5,
        sha256: 'same-content',
        state: 'pending',
        createdAt: '2026-10-19',
      },
    })
    expect(messages.info).toHaveBeenCalledWith('已有相同内容的文件，请检查后保存')
    messages.info.mockClear()
    state.uploaded({
      pending: {
        attachmentId: 4,
        requestKey: 'key-0000000000002',
        fileName: '1.pdf',
        fileSize: 5,
        sha256: 'different-content',
        state: 'pending',
        createdAt: '2026-10-19',
      },
    })
    expect(messages.info).not.toHaveBeenCalled()
  })
  it('新上传与管理组件共享会话，移除暂存，保存使用完整最终清单', async () => {
    const w = await setup()
    const state = (w.vm as any).$.setupState
    await w.get('.remove-stub').trigger('click')
    await w.get('.upload-stub').trigger('click')
    await flushPromises()
    state.setRowDocumentType({ attachmentId: 3 }, 'STRUCTURE_DRAWING')
    expect(api.saveDraft).not.toHaveBeenCalled()
    expect(w.text()).not.toContain('设现行')
    expect(w.text()).not.toContain('下发')
    expect(w.text()).not.toContain('图纸版本')
    await w
      .findAll('button')
      .find((b) => b.text() === '保存草稿')!
      .trigger('click')
    await flushPromises()
    expect(api.saveDraft).toHaveBeenCalledWith(
      42,
      expect.objectContaining({
        revision: 'r1',
        attachments: [
          expect.objectContaining({ attachmentId: 2 }),
          expect.objectContaining({ attachmentId: 3 }),
        ],
      })
    )
    expect(messages.success).toHaveBeenCalledWith('草稿已保存')
  })
  it('保存失败保留输入和附件；取消改动后重读已保存草稿', async () => {
    const w = await setup()
    const state = (w.vm as any).$.setupState
    await flushPromises()
    await w.get('.upload-stub').trigger('click')
    state.setRowDocumentType({ attachmentId: 3 }, 'STRUCTURE_DRAWING')
    api.saveDraft.mockRejectedValueOnce(new Error('save failed'))
    await w
      .findAll('button')
      .find((b) => b.text() === '保存草稿')!
      .trigger('click')
    await flushPromises()
    expect(messages.error).toHaveBeenCalledWith('save failed')
    expect(messages.success).not.toHaveBeenCalled()
    expect((w.vm as any).$.setupState.draft.session.pendingCount.value).toBe(1)
    await w
      .findAll('button')
      .find((b) => b.text() === '取消未保存改动')!
      .trigger('click')
    await flushPromises()
    expect((w.vm as any).$.setupState.draft.session.pendingCount.value).toBe(0)
    expect((w.vm as any).$.setupState.draft.files.value.map((f: any) => f.attachmentId)).toEqual([
      1, 2,
    ])
  })
})

it('上传选项来自工程字典，旧固定categories不截断BOM/作业指导书', async () => {
  const w = await setup()
  await w.setProps({ categories: ['STRUCTURE_DRAWING'] })
  const state = (w.vm as any).$.setupState
  expect(state.types.map((type: any) => type.value)).toContain('BOM')
  expect(state.types.map((type: any) => type.value)).toContain('WORK_INSTRUCTION')
  await w.get('.upload-stub').trigger('click')
  state.setRowDocumentType({ attachmentId: 3 }, 'BOM')
  await state.save()
  expect(api.saveDraft).toHaveBeenCalledWith(
    42,
    expect.objectContaining({
      attachments: expect.arrayContaining([
        expect.objectContaining({ attachmentId: 3, documentType: 'BOM' }),
      ]),
    })
  )
})

describe('工程图集先上传后选择资料类型', () => {
  it('未预选类型也能上传：清单出现类型为空的行，不默认结构图', async () => {
    const w = await setup()
    const state = (w.vm as any).$.setupState
    expect(state.uploadTypeAvailable).toBe(true)
    expect(state.uploadBlockedReason).toBe('')
    expect(w.get('.upload-stub').attributes('disabled')).toBeUndefined()
    await w.get('.upload-stub').trigger('click')
    await flushPromises()
    expect(messages.warning).not.toHaveBeenCalledWith('先选择资料类型再上传')
    expect(state.draft.session.pendingCount.value).toBe(1)
    const pending = state.draft.files.value.find((file: any) => file.attachmentId === 3)
    expect(pending.documentType).toBe('')
  })
  it('清单行逐条选择类型；历史附件可改，保存按所选类型提交', async () => {
    const w = await setup()
    const state = (w.vm as any).$.setupState
    await w.get('.upload-stub').trigger('click')
    await flushPromises()
    state.setRowDocumentType({ attachmentId: 3 }, 'PANEL_DRAWING')
    state.setRowDocumentType({ attachmentId: 2 }, 'BOM')
    await state.save()
    expect(api.saveDraft).toHaveBeenCalledWith(
      42,
      expect.objectContaining({
        attachments: expect.arrayContaining([
          expect.objectContaining({ attachmentId: 1, documentType: 'STRUCTURE_DRAWING' }),
          expect.objectContaining({ attachmentId: 2, documentType: 'BOM' }),
          expect.objectContaining({ attachmentId: 3, documentType: 'PANEL_DRAWING' }),
        ]),
      })
    )
    expect(messages.success).toHaveBeenCalledWith('草稿已保存')
  })
  it('多个待保存附件可分别选择不同类型，互不影响', async () => {
    const w = await setup()
    const state = (w.vm as any).$.setupState
    await w.get('.upload-stub').trigger('click')
    state.uploaded({
      pending: {
        attachmentId: 4,
        requestKey: 'key-0000000000004',
        fileName: 'four.pdf',
        fileSize: 4,
        state: 'pending',
        createdAt: '2026-10-19',
      },
    })
    state.setRowDocumentType({ attachmentId: 3 }, 'PANEL_DRAWING')
    state.setRowDocumentType({ attachmentId: 4 }, 'WORK_INSTRUCTION')
    await state.save()
    const sent = api.saveDraft.mock.calls[0][1].attachments
    expect(sent.find((file: any) => file.attachmentId === 3).documentType).toBe('PANEL_DRAWING')
    expect(sent.find((file: any) => file.attachmentId === 4).documentType).toBe('WORK_INSTRUCTION')
  })
  it('任一行未选类型时阻止保存，提示中列出文件名且不调用保存接口', async () => {
    const w = await setup()
    const state = (w.vm as any).$.setupState
    await w.get('.upload-stub').trigger('click')
    await flushPromises()
    await state.save()
    expect(api.saveDraft).not.toHaveBeenCalled()
    expect(messages.error).toHaveBeenCalledWith(expect.stringContaining('new.pdf'))
    expect(messages.success).not.toHaveBeenCalled()
  })
  it('切换产品重新加载后，类型按服务端保存值恢复', async () => {
    const w = await setup()
    const state = (w.vm as any).$.setupState
    api.get.mockResolvedValue(response([1, 2], 'r9', 'PANEL_DRAWING'))
    await w.setProps({ productId: 43, productCode: 'P43' })
    await flushPromises()
    expect(state.draft.files.value.map((file: any) => file.documentType)).toEqual([
      'PANEL_DRAWING',
      'PANEL_DRAWING',
    ])
  })
  it('字典不可用：上传禁用并提示，不能上传', async () => {
    api.documentTypes.mockRejectedValueOnce(new Error('字典不可用'))
    const w = await setup()
    const state = (w.vm as any).$.setupState
    expect(state.uploadTypeAvailable).toBe(false)
    expect(state.uploadBlockedReason).toBe('资料类型不可用，暂不能上传')
    expect(w.get('.upload-stub').attributes('disabled')).toBeDefined()
    expect(w.text()).toContain('资料类型不可用，暂不能上传，请重试加载')
    expect(messages.error).toHaveBeenCalled()
  })
})

describe('制定负责人锁（dev-20261020-013）', () => {
  it('非负责人只能查看：隐藏保存/上传/排序/负责人下拉', async () => {
    api.get.mockResolvedValue({
      data: { ...response().data, preparedById: 8, preparedByName: '工程乙' },
    })
    const w = await setup()
    const state = (w.vm as any).$.setupState
    expect(state.draft.isOwner.value).toBe(false)
    expect(state.canEditSpec).toBe(false)
    expect(w.text()).toContain('当前由 工程乙 制定，你只能查看')
    expect(w.findAll('button').some((b) => b.text() === '保存草稿')).toBe(false)
    expect(w.find('.upload-stub').exists()).toBe(false)
    expect(w.findAll('.el-select').length).toBe(0)
    expect(w.text()).toContain('制定中')
  })
  it('负责人转交走独立入口，不混入附件草稿保存', async () => {
    const w = await setup()
    const state = (w.vm as any).$.setupState
    expect(state.draft.canTransfer.value).toBe(true)
    await state.save()
    expect(messages.confirm).not.toHaveBeenCalled()
    expect(api.saveDraft).toHaveBeenCalledWith(42, expect.objectContaining({ preparedById: 7 }))
  })
})
