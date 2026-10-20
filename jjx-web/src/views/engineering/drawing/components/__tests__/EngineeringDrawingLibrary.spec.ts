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
function response(ids = [1, 2], revision = 'r1') {
  return {
    data: {
      revision,
      draftManaged: true,
      preparedById: 7,
      preparedByName: '工程甲',
      draftAttachments: ids.map((attachmentId) => ({
        attachmentId,
        documentType: 'STRUCTURE_DRAWING',
        print: true,
      })),
      draftFiles: ids.map((attachmentId) => ({
        refId: attachmentId + 100,
        attachmentId,
        fileName: attachmentId + '.pdf',
        documentType: 'STRUCTURE_DRAWING',
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
        ext_data: { parent: 'ENGINEERING_DOC' },
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
  it('按内容hash提示同内容异名重复，同名异内容不误报', async () => {
    const w = await setup()
    const draft = (w.vm as any).$.setupState.draft
    draft.session.refs.value[0].sha256 = 'same-content'
    const state = (w.vm as any).$.setupState
    state.uploadType = 'STRUCTURE_DRAWING'
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
    ;(w.vm as any).$.setupState.uploadType = 'STRUCTURE_DRAWING'
    await w.get('.remove-stub').trigger('click')
    await w.get('.upload-stub').trigger('click')
    await flushPromises()
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
    ;(w.vm as any).$.setupState.uploadType = 'STRUCTURE_DRAWING'
    await flushPromises()
    await w.get('.upload-stub').trigger('click')
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
  state.uploadType = 'BOM'
  await flushPromises()
  await w.get('.upload-stub').trigger('click')
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

describe('上传必须先选择资料类型', () => {
  it('初始未选择类型：上传禁用并提示先选择，不默认结构图', async () => {
    const w = await setup()
    const state = (w.vm as any).$.setupState
    expect(state.uploadType).toBe('')
    expect(state.types.length).toBeGreaterThan(0)
    expect(state.uploadTypeSelected).toBe(false)
    expect(state.uploadBlockedReason).toBe('先选择资料类型再上传')
    expect(w.get('.upload-stub').attributes('disabled')).toBeDefined()
    expect(w.get('.upload-stub').attributes('data-blocked-reason')).toBe('先选择资料类型再上传')
    state.uploaded({
      pending: {
        attachmentId: 3,
        requestKey: 'key-0000000000003',
        fileName: 'new.pdf',
        fileSize: 3,
        state: 'pending',
        createdAt: '2026-10-19',
      },
    })
    await flushPromises()
    expect(messages.warning).toHaveBeenCalledWith('先选择资料类型再上传')
    expect(state.draft.session.pendingCount.value).toBe(0)
  })
  it('选择面板图后上传按面板图登记，上传后保留当前选择', async () => {
    const w = await setup()
    const state = (w.vm as any).$.setupState
    state.uploadType = 'PANEL_DRAWING'
    await flushPromises()
    expect(state.uploadTypeSelected).toBe(true)
    expect(state.uploadBlockedReason).toBe('')
    expect(w.get('.upload-stub').attributes('disabled')).toBeUndefined()
    await w.get('.upload-stub').trigger('click')
    await flushPromises()
    expect(state.uploadType).toBe('PANEL_DRAWING')
    await state.save()
    expect(api.saveDraft).toHaveBeenCalledWith(
      42,
      expect.objectContaining({
        attachments: expect.arrayContaining([
          expect.objectContaining({ attachmentId: 3, documentType: 'PANEL_DRAWING' }),
        ]),
      })
    )
    // 既有附件保持原类型；只有本次新上传的附件用面板图。
    expect(
      api.saveDraft.mock.calls[0][1].attachments.find((file: any) => file.attachmentId === 3)
        ?.documentType
    ).toBe('PANEL_DRAWING')
  })
  it('清空已选类型后重新禁用上传', async () => {
    const w = await setup()
    const state = (w.vm as any).$.setupState
    state.uploadType = 'PANEL_DRAWING'
    await flushPromises()
    state.uploadType = ''
    await flushPromises()
    expect(state.uploadTypeSelected).toBe(false)
    expect(state.uploadBlockedReason).toBe('先选择资料类型再上传')
    expect(w.get('.upload-stub').attributes('disabled')).toBeDefined()
  })
  it('切换产品后回到未选择状态，不继承上一产品类型', async () => {
    const w = await setup()
    const state = (w.vm as any).$.setupState
    state.uploadType = 'PANEL_DRAWING'
    await flushPromises()
    await w.setProps({ productId: 43, productCode: 'P43' })
    await flushPromises()
    expect(state.uploadType).toBe('')
    expect(state.uploadTypeSelected).toBe(false)
    expect(w.get('.upload-stub').attributes('disabled')).toBeDefined()
  })
  it('字典加载失败时不自动选第一项，明确提示且不能上传', async () => {
    api.documentTypes.mockRejectedValueOnce(new Error('字典不可用'))
    const w = await setup()
    const state = (w.vm as any).$.setupState
    expect(state.uploadType).toBe('')
    expect(state.types).toEqual([])
    expect(state.uploadTypeAvailable).toBe(false)
    expect(state.uploadBlockedReason).toBe('资料类型不可用，暂不能上传')
    expect(w.get('.upload-stub').attributes('disabled')).toBeDefined()
    expect(w.text()).toContain('资料类型不可用，暂不能上传，请重试加载')
    expect(messages.error).toHaveBeenCalled()
  })
})
