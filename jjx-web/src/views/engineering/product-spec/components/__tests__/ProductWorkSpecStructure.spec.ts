vi.mock('element-plus/es/components/drawer/style/css', () => ({}))
vi.mock('element-plus/es/components/tabs/style/css', () => ({}))
vi.mock('element-plus/es/components/tab-pane/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/card/style/css', () => ({}))
vi.mock('element-plus/es/components/link/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox-group/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/message-box/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/date-picker/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions-item/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/base/style/css', () => ({}))

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ElMessageBox } from 'element-plus'
import Panel from '../ProductWorkSpecPanel.vue'
import Drawer from '@/components/product/ProductSpecDrawer.vue'
import Header from '@/components/product/ProductSpecHeader.vue'
import VersionPanel from '../ProductWorkSpecVersionPanel.vue'
import { normalizeWorkSpec, productWorkSpecApi } from '@/api/product/workSpec'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import { attachmentApi } from '@/api/system/attachment'
import { loadDocset, renderFile, defaultDocumentFiles } from '../docset'
const permission = vi.hoisted(() => ({ edit: true }))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({
    permissions: ['*'],
    roles: [],
    userId: 7,
    hasPermission: (p: string) => p === 'product:edit' && permission.edit,
  }),
}))
vi.mock('@/api/product', () => ({
  getFullProduct: vi.fn().mockResolvedValue({
    data: { product: { productId: 2, productCode: 'P2', productName: '产品' } },
  }),
}))
vi.mock('@/components/product/customer-doc/CustomerDocPanel.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/components/product/ProductBasicInfo.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/product/ProductDocPanel.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../ProductColorCheckPanel.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../InkMixingForm.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/utils/request', () => ({ default: {} }))
vi.mock('@/api/system/attachmentRef', () => ({
  attachmentRefApi: {
    documentTypes: vi.fn(),
    listRefs: vi.fn(),
    tempUpload: vi.fn(),
    createRef: vi.fn(),
  },
}))
vi.mock('@/api/system/attachment', () => ({
  attachmentApi: {
    list: vi.fn().mockResolvedValue({ data: [] }),
    setCurrent: vi.fn(),
    release: vi.fn(),
  },
}))
vi.mock('@/api/product/workSpec', async (original) => ({
  ...(await original<typeof import('@/api/product/workSpec')>()),
  productWorkSpecApi: {
    get: vi.fn(),
    getVersion: vi.fn(),
    saveDraft: vi.fn(),
    listVersions: vi.fn(),
    publishVersion: vi.fn(),
  },
}))
vi.mock('../docset', async (original) => ({
  ...(await original<typeof import('../docset')>()),
  loadDocset: vi.fn(),
  renderFile: vi.fn(),
}))
vi.mock('../ProductSpecPreview.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../ProductPrintSpecPanel.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../WorkSpecWorkflow.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/engineering/drawing/components/EngineeringDrawingLibrary.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/product/bom/components/BomFormDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/views/product/route/components/RouteFormDialog.vue', () => ({
  default: { template: '<div />' },
}))
let spec: ReturnType<typeof normalizeWorkSpec>
let files: any[]
const wrappers: any[] = []
const drawing = (id: number, kind = 'image') => ({
  id,
  key: String(id),
  name: `图${id}.png`,
  kind,
  category: '结构图',
  documentType: 'STRUCTURE_DRAWING',
  section: 'atlas',
  productFile: true,
  drawingNo: 'P2-ST',
  version: 'A',
  fileRole: 'PRINT',
  isCurrent: 0,
  released: 0,
})
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(attachmentRefApi.documentTypes).mockResolvedValue({
    data: [
      { dict_code: 'attachment_category', item_key: 'ENGINEERING_DOC', label: '工程技术资料' },
      {
        dict_code: 'attachment_document_type',
        item_key: 'STRUCTURE_DRAWING',
        label: '结构图',
        is_active: 1,
        ext_data: { parent: 'ENGINEERING_DOC' },
      },
    ],
  } as any)
  localStorage.clear()
  vi.stubGlobal('crypto', {})
  permission.edit = true
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
  spec = normalizeWorkSpec({
    structureFileId: 224,
    revision: 'old',
    engineeringRequirements: '保留备注',
    draftFiles: [
      { attachmentId: 224, refId: 300, fileName: '图224.png', documentType: 'STRUCTURE_DRAWING' },
    ],
  })
  files = [drawing(224)]
  vi.mocked(loadDocset).mockImplementation(async () => ({
    product: {},
    workSpec: { ...spec },
    files: [...files],
    bom: { items: [] },
    routing: { items: [] },
    warnings: [],
  }))
  vi.mocked(renderFile).mockImplementation(async (file) => [
    { url: `blob:${file.id}`, width: 100, height: 100 },
  ])
  vi.mocked(productWorkSpecApi.get).mockImplementation(async () => ({ data: { ...spec } }) as any)
  vi.mocked(productWorkSpecApi.listVersions).mockResolvedValue({
    data: [{ versionId: 1, versionNo: 'V1.0', publishedAt: '2026-10-19' }],
  } as any)
  vi.mocked(attachmentRefApi.listRefs).mockResolvedValue({ data: [] } as any)
  vi.mocked(attachmentRefApi.tempUpload).mockResolvedValue({ data: 225 } as any)
  vi.mocked(attachmentRefApi.createRef).mockResolvedValue({ data: 300 } as any)
  vi.mocked(productWorkSpecApi.saveDraft).mockImplementation(async (_, value) => {
    spec = normalizeWorkSpec({
      ...spec,
      ...value.spec,
      revision: 'new',
      draftManaged: true,
      draftAttachments: value.attachments,
      draftFiles: value.attachments.map((f: any) => ({
        ...f,
        refId: f.attachmentId + 100,
        fileName: '?' + f.attachmentId + '.png',
      })),
    })
    files = [drawing(224), drawing(225)]
    return { data: spec } as any
  })
})
afterEach(() => wrappers.splice(0).forEach((w) => w.unmount()))
async function render() {
  const wrapper = mount(Panel, {
    props: { productId: 2, productCode: 'P2', productName: '产品' },
    global: {
      directives: { loading: () => {} },
      stubs: {
        ElTable: { template: '<div />' },
        ElButton: {
          props: ['disabled'],
          template: '<button :disabled="disabled"><slot/></button>',
        },
      },
    },
  })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}
async function edit(w: any) {
  await w.get('.spec-top-col .section-heading button').trigger('click')
  await flushPromises()
}
async function selectFile(w: any) {
  const input = w.get('[data-test="file"]')
  Object.defineProperty(input.element, 'files', {
    configurable: true,
    value: [new File(['drawing'], 'new.png', { type: 'image/png' })],
  })
  await input.trigger('change')
  await flushPromises()
}
describe('作业规范面板结构图编辑交互', () => {
  it('默认只读，编辑后仅图片区可上传；取消无保存，无选择器和第二次保存', async () => {
    const w = await render()
    expect(w.find('img').attributes('src')).toBe('blob:224')
    expect(w.get('[data-test="open"]').element.tagName).toBe('DIV')
    await selectFile(w)
    expect(attachmentRefApi.tempUpload).not.toHaveBeenCalled()
    await edit(w)
    const section = w.get('.spec-top-col')
    expect(w.get('[data-test="open"]').element.tagName).toBe('BUTTON')
    expect(section.find('el-select').exists()).toBe(false)
    expect(section.findAll('button').some((b) => b.text() === '保存')).toBe(false)
    expect(section.text()).not.toContain('上传结构图')
    await section.get('.section-heading button').trigger('click')
    expect(w.get('[data-test="open"]').element.tagName).toBe('DIV')
    expect(productWorkSpecApi.saveDraft).not.toHaveBeenCalled()
  })
  it('保存成功后显示新图并退出编辑，重新打开仍是新图，发布/下发接口未调用', async () => {
    const w = await render()
    await edit(w)
    await selectFile(w)
    expect(w.find('img').attributes('src')).toBe('blob:225')
    expect(w.get('[data-test="open"]').element.tagName).toBe('DIV')
    expect(productWorkSpecApi.saveDraft).toHaveBeenCalledWith(
      2,
      expect.objectContaining({
        spec: expect.objectContaining({
          structureFileId: 225,
          engineeringRequirements: '保留备注',
        }),
      })
    )
    expect(attachmentApi.setCurrent).not.toHaveBeenCalled()
    expect(attachmentApi.release).not.toHaveBeenCalled()
    expect(productWorkSpecApi.publishVersion).not.toHaveBeenCalled()
    w.unmount()
    const reopened = await render()
    expect(reopened.find('img').attributes('src')).toBe('blob:225')
    expect(files.map((f) => f.id)).toEqual([224, 225])
    // 上传工作态不改变工程图集默认打印门禁；发布快照按既有清单处理。
    expect(defaultDocumentFiles([drawing(225)])).toEqual([])
    expect(defaultDocumentFiles([{ ...drawing(224), published: true }])).toHaveLength(1)
  })
  it('保存失败保留旧图和编辑状态，点击图片区重试不重复上传且保留最新备注', async () => {
    vi.mocked(productWorkSpecApi.saveDraft).mockRejectedValueOnce(new Error('revision 冲突'))
    const w = await render()
    await edit(w)
    await selectFile(w)
    expect(w.find('img').attributes('src')).toBe('blob:224')
    expect(w.find('[role="alert"]').text()).toContain('草稿保存失败')
    expect(w.emitted('updated')).toBeUndefined()
    await w.get('[data-test="open"]').trigger('click')
    await flushPromises()
    expect(w.find('img').attributes('src')).toBe('blob:225')
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledTimes(1)
    expect(attachmentRefApi.createRef).not.toHaveBeenCalled()
    expect(productWorkSpecApi.saveDraft).toHaveBeenLastCalledWith(
      2,
      expect.objectContaining({
        spec: expect.objectContaining({ engineeringRequirements: '保留备注' }),
      })
    )
  })
  it('保存请求进行中保留旧图，不能再次上传或退出编辑', async () => {
    let finish!: () => void
    vi.mocked(productWorkSpecApi.saveDraft).mockImplementationOnce(async (_, value) => {
      await new Promise<void>((r) => {
        finish = r
      })
      spec = normalizeWorkSpec({
        ...spec,
        ...value.spec,
        revision: 'new',
        draftFiles: value.attachments.map((f: any) => ({
          ...f,
          refId: f.attachmentId + 100,
          fileName: '?' + f.attachmentId + '.png',
        })),
      })
      files = [drawing(224), drawing(225)]
      return { data: spec } as any
    })
    const w = await render()
    await edit(w)
    await selectFile(w)
    expect(w.find('img').attributes('src')).toBe('blob:224')
    expect(w.get('.spec-top-col .section-heading button').attributes('disabled')).toBeDefined()
    await w.get('[data-test="open"]').trigger('click')
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledTimes(1)
    finish()
    await flushPromises()
    expect(w.find('img').attributes('src')).toBe('blob:225')
  })
  it('没有编辑权限只展示当前图，不出现编辑/上传入口', async () => {
    permission.edit = false
    const w = await render()
    expect(w.find('.spec-top-col .section-heading button').exists()).toBe(false)
    expect(w.get('[data-test="open"]').element.tagName).toBe('DIV')
    expect(w.find('img').attributes('src')).toBe('blob:224')
    await selectFile(w)
    expect(attachmentRefApi.tempUpload).not.toHaveBeenCalled()
  })
  it('空态可编辑上传，PDF沿用现有渲染结果', async () => {
    spec.structureFileId = null
    files = []
    const w = await render()
    expect(w.text()).toContain('暂无结构图')
    await edit(w)
    const input = w.get('[data-test="file"]')
    Object.defineProperty(input.element, 'files', {
      configurable: true,
      value: [new File(['pdf'], 'drawing.pdf', { type: 'application/pdf' })],
    })
    await input.trigger('change')
    await flushPromises()
    expect(w.find('img').attributes('src')).toBe('blob:225')
    expect(attachmentRefApi.tempUpload).toHaveBeenCalledWith(
      expect.any(File),
      expect.stringMatching(/^[A-Za-z0-9_-]{16,100}$/),
      undefined,
      null,
      null
    )
  })
})

async function drawer() {
  const w = mount(Drawer, {
    global: {
      directives: { loading: () => {}, hasPermi: () => {} },
      stubs: {
        ElDrawer: { template: '<div><slot/></div>' },
        ElTabs: { template: '<div><slot/></div>' },
        ElTabPane: { template: '<div><slot/></div>' },
        ElTable: { template: '<div />' },
        ElDialog: { template: '<div />' },
        ProductColorCheckPanel: true,
        EngineeringDrawingLibrary: true,
      },
    },
  })
  wrappers.push(w)
  await (w.vm as any).open(2)
  const state = (w.vm as any).$.setupState
  state.activeTab = 'spec'
  await flushPromises()
  return { w, state, panel: w.getComponent(Panel), header: w.getComponent(Header) }
}
describe('真实Header→Drawer→正文面板切版保护', () => {
  it.each(['note', 'issuance', 'die', 'editor', 'structure'])(
    '%s编辑中保持原面板和输入，明确取消后才可切版',
    async (mode) => {
      const { w, state, panel, header } = await drawer()
      const local = (panel.vm as any).$.setupState
      if (mode === 'note') {
        local.startNoteEdit()
        local.noteDraft = '未保存备注'
      }
      if (mode === 'issuance') {
        local.startIssuanceEdit()
        local.issuanceDraft.issueUnit = '未保存发行'
      }
      if (mode === 'die') {
        local.startDieEdit()
        local.dieDraft = { key: '未保存刀模' }
      }
      if (mode === 'editor') {
        local.edit('requirements')
        local.draft.engineeringRequirements = '未保存正文'
      }
      if (mode === 'structure') local.startStructureEdit()
      await flushPromises()
      header.vm.$emit('update:versionId', 11)
      await flushPromises()
      expect(state.selectedVersionId).toBe(0)
      expect((w.getComponent(Panel).vm as any).$.uid).toBe((panel.vm as any).$.uid)
      if (mode === 'note') expect(local.noteDraft).toBe('未保存备注')
      if (mode === 'issuance') expect(local.issuanceDraft.issueUnit).toBe('未保存发行')
      if (mode === 'die') expect(local.dieDraft.key).toBe('未保存刀模')
      if (mode === 'editor') expect(local.draft.engineeringRequirements).toBe('未保存正文')
      local.noteDraft = null
      local.issuanceDraft = null
      local.dieEditing = false
      local.editorVisible = false
      local.structureEditing = false
      await flushPromises()
      vi.mocked(productWorkSpecApi.getVersion).mockResolvedValue({
        code: 200,
        data: { version: {}, items: [] },
      } as any)
      header.vm.$emit('update:versionId', 11)
      await flushPromises()
      expect(state.selectedVersionId).toBe(11)
      expect(w.findComponent(Panel).exists()).toBe(false)
      header.vm.$emit('update:versionId', 0)
      await flushPromises()
      expect(state.selectedVersionId).toBe(0)
    }
  )
  it('附件待保存、上传保存和定版等待中均不能切走；无改动可正常切换', async () => {
    const { state, header, w } = await drawer()
    state.draftSession.register(
      {
        attachmentId: 225,
        requestKey: 'key-0000000000001',
        fileName: 'new.pdf',
        fileSize: 1,
        createdAt: '2026-10-20',
        state: 'pending',
      },
      'STRUCTURE_DRAWING'
    )
    header.vm.$emit('update:versionId', 11)
    await flushPromises()
    expect(state.selectedVersionId).toBe(0)
    state.draftSession.session.pending.value = []
    state.draftSession.saving.value = true
    header.vm.$emit('update:versionId', 11)
    await flushPromises()
    expect(state.selectedVersionId).toBe(0)
    state.draftSession.saving.value = false
    const version = w.getComponent(VersionPanel)
    version.vm.$emit('busy', true)
    header.vm.$emit('update:versionId', 11)
    await flushPromises()
    expect(state.selectedVersionId).toBe(0)
    version.vm.$emit('busy', false)
    vi.mocked(productWorkSpecApi.getVersion).mockResolvedValue({
      code: 200,
      data: { version: {}, items: [] },
    } as any)
    header.vm.$emit('update:versionId', 11)
    await flushPromises()
    expect(state.selectedVersionId).toBe(11)
  })
})

it('真实正文面板冲突后继续编辑，显式重新应用保留最新文字', async () => {
  const { state, panel } = await drawer()
  const local = (panel.vm as any).$.setupState
  local.startNoteEdit()
  local.noteDraft = '第一次输入'
  const normalSave = vi.mocked(productWorkSpecApi.saveDraft).getMockImplementation()!
  spec = normalizeWorkSpec({
    ...spec,
    revision: 'server-r2',
    engineeringRequirements: '他人已保存',
  })
  vi.mocked(productWorkSpecApi.saveDraft).mockImplementation(async (id, body) => {
    if (body.revision !== spec.revision)
      throw new Error('工程规范已被其他入口修改，请重新加载后编辑')
    return normalSave(id, body)
  })
  await local.saveNote()
  expect(local.noteDraft).toBe('第一次输入')
  expect(state.draftSession.conflict.value).toBe(true)
  local.noteDraft = '冲突后继续编辑的新文字'
  const confirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
  try {
    await state.draftSession.recover()
    await flushPromises()
    expect(productWorkSpecApi.saveDraft).toHaveBeenLastCalledWith(
      2,
      expect.objectContaining({
        revision: 'server-r2',
        spec: expect.objectContaining({ engineeringRequirements: '冲突后继续编辑的新文字' }),
      })
    )
    expect(local.noteDraft).toBe(null)
    expect(spec.engineeringRequirements).toBe('冲突后继续编辑的新文字')
  } finally {
    confirm.mockRestore()
  }
})

it('真实结构图上传/保存等待中Header切版不会卸载编辑；保存后可切版', async () => {
  let finish!: () => void
  const normalSave = vi.mocked(productWorkSpecApi.saveDraft).getMockImplementation()!
  vi.mocked(productWorkSpecApi.saveDraft).mockImplementationOnce(async (id, body) => {
    await new Promise<void>((resolve) => {
      finish = resolve
    })
    return normalSave(id, body)
  })
  const { w, state, panel, header } = await drawer()
  const local = (panel.vm as any).$.setupState
  local.startStructureEdit()
  await flushPromises()
  await selectFile(w)
  header.vm.$emit('update:versionId', 11)
  await flushPromises()
  expect(state.selectedVersionId).toBe(0)
  expect(w.findComponent(Panel).exists()).toBe(true)
  expect(attachmentRefApi.tempUpload).toHaveBeenCalledTimes(1)
  finish()
  await flushPromises()
  vi.mocked(productWorkSpecApi.getVersion).mockResolvedValue({
    code: 200,
    data: { version: {}, items: [] },
  } as any)
  header.vm.$emit('update:versionId', 11)
  await flushPromises()
  expect(state.selectedVersionId).toBe(11)
})
