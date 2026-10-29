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
import { ElMessage, ElMessageBox } from 'element-plus'
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
    preparedById: 7,
    preparedByName: '工程甲',
    draftManaged: true,
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
      null,
      undefined
    )
  })
  it('作业规范面板保存前汇总附件问题并提示文件名，不调用保存接口', async () => {
    const w = await render()
    const state = (w.vm as any).$.setupState
    const error = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({}) as any)
    state.draftSession.register({
      attachmentId: 226,
      requestKey: 'request-0000000002',
      fileName: 'pending.pdf',
      fileSize: 3,
      sha256: 'hash',
      state: 'pending',
      createdAt: '2026-10-20',
    })
    await state.save()
    expect(error).toHaveBeenCalledWith(expect.stringContaining('以下 1 个附件未选择资料类型'))
    expect(error).toHaveBeenCalledWith(expect.stringContaining('pending.pdf'))
    expect(productWorkSpecApi.saveDraft).not.toHaveBeenCalled()
    error.mockRestore()
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
  it.each(['note', 'issuance', 'die', 'editor', 'structure', 'emboss'])(
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
      if (mode === 'emboss') {
        local.startEmbossEdit()
        local.embossDraft.setupHeight = '0.25'
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
      if (mode === 'emboss') expect(local.embossDraft.setupHeight).toBe('0.25')
      local.noteDraft = null
      local.issuanceDraft = null
      local.dieEditing = false
      local.editorVisible = false
      local.structureEditing = false
      local.embossEditing = false
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


describe('凹凸条件显式编辑保存', () => {
  const embossSection = (w: any) => w.findAll('.spec-top-col')[1]
  async function begin(w: any) {
    await embossSection(w).get('.section-heading button').trigger('click')
    await flushPromises()
  }
  it('默认只读，输入和失焦不保存；点击保存提交六项新值，刷新重开可见', async () => {
    const w = await render()
    expect(embossSection(w).findAll('input')).toHaveLength(0)
    await begin(w)
    const values = ['0.25', '0.2', '100', '90', '2', '3']
    const inputs = embossSection(w).findAll('input')
    expect(inputs).toHaveLength(6)
    for (let i = 0; i < inputs.length; i++) {
      await inputs[i].setValue(values[i])
      await inputs[i].trigger('blur')
    }
    expect(productWorkSpecApi.saveDraft).not.toHaveBeenCalled()
    await embossSection(w).findAll('.section-heading button')[1].trigger('click')
    await flushPromises()
    const expected = { setupHeight: '0.25', requiredHeight: '0.2', upperTemperature: '100',
      lowerTemperature: '90', pressTime: '2', holdTime: '3' }
    expect(productWorkSpecApi.saveDraft).toHaveBeenCalledTimes(1)
    expect(productWorkSpecApi.saveDraft).toHaveBeenCalledWith(2, expect.objectContaining({
      spec: expect.objectContaining({ emboss: expected, engineeringRequirements: '保留备注' }),
    }))
    expect(embossSection(w).findAll('input')).toHaveLength(0)
    expect(spec.emboss).toEqual(expected)
    const reopened = await render()
    for (const value of values) expect(embossSection(reopened).text()).toContain(value)
  })
  it('取消还原原值，不提交；外部刷新不覆盖正在编辑的输入', async () => {
    spec.emboss = { setupHeight: '0.1' }
    const w = await render()
    await begin(w)
    await embossSection(w).get('input').setValue('0.5')
    await (w.vm as any).$.setupState.loadData()
    expect((embossSection(w).get('input').element as HTMLInputElement).value).toBe('0.5')
    await embossSection(w).get('.section-heading button').trigger('click')
    expect(productWorkSpecApi.saveDraft).not.toHaveBeenCalled()
    expect(embossSection(w).text()).toContain('0.1')
    expect(embossSection(w).findAll('input')).toHaveLength(0)
  })
  it('保存失败保留编辑值并可重试', async () => {
    const w = await render()
    await begin(w)
    await embossSection(w).get('input').setValue('0.3')
    vi.mocked(productWorkSpecApi.saveDraft).mockRejectedValueOnce(new Error('网络错误'))
    await (w.vm as any).$.setupState.saveEmboss()
    await flushPromises()
    expect((embossSection(w).get('input').element as HTMLInputElement).value).toBe('0.3')
    expect((w.vm as any).$.setupState.embossEditing).toBe(true)
    await (w.vm as any).$.setupState.saveEmboss()
    await flushPromises()
    expect(spec.emboss.setupHeight).toBe('0.3')
    expect((w.vm as any).$.setupState.embossEditing).toBe(false)
  })
  it('非空文本按现口径提交，清空原值后显式保存可删除', async () => {
    spec.emboss = { setupHeight: '0.1' }
    const w = await render()
    await begin(w)
    await embossSection(w).get('input').setValue('不是数值')
    await (w.vm as any).$.setupState.saveEmboss()
    await flushPromises()
    expect(productWorkSpecApi.saveDraft).toHaveBeenCalledWith(2, expect.objectContaining({
      spec: expect.objectContaining({ emboss: { setupHeight: '不是数值' } }),
    }))
    expect(spec.emboss).toEqual({ setupHeight: '不是数值' })
    await begin(w)
    await embossSection(w).get('input').setValue('   ')
    await (w.vm as any).$.setupState.saveEmboss()
    await flushPromises()
    expect(productWorkSpecApi.saveDraft).toHaveBeenCalledTimes(2)
    expect(spec.emboss).toEqual({})
    expect(embossSection(w).findAll('input')).toHaveLength(0)
  })
  it('只读用户不能进入凹凸条件编辑或提交', async () => {
    permission.edit = false
    const w = await render()
    const state = (w.vm as any).$.setupState
    state.startEmbossEdit()
    state.embossDraft.setupHeight = '0.1'
    await state.saveEmboss()
    expect(state.embossEditing).toBe(false)
    expect(embossSection(w).findAll('button')).toHaveLength(0)
    expect(productWorkSpecApi.saveDraft).not.toHaveBeenCalled()
  })
  it('冲突后继续输入，重新应用取最新凹凸条件，恢复后退出编辑', async () => {
    const { state, panel } = await drawer()
    const local = (panel.vm as any).$.setupState
    local.startEmbossEdit()
    local.embossDraft.setupHeight = '0.2'
    const normalSave = vi.mocked(productWorkSpecApi.saveDraft).getMockImplementation()!
    spec = normalizeWorkSpec({ ...spec, revision: 'server-r2', emboss: { setupHeight: '0.1' } })
    vi.mocked(productWorkSpecApi.saveDraft).mockImplementation(async (id, body) => {
      if (body.revision !== spec.revision) throw new Error('revision 冲突')
      return normalSave(id, body)
    })
    await local.saveEmboss()
    expect(state.draftSession.conflict.value).toBe(true)
    expect(local.embossEditing).toBe(true)
    local.embossDraft.setupHeight = '0.4'
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    try {
      await state.draftSession.recover()
      await flushPromises()
      expect(spec.emboss.setupHeight).toBe('0.4')
      expect(local.embossEditing).toBe(false)
    } finally {
      confirm.mockRestore()
    }
  })
})
