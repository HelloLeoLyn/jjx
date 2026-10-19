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
import Panel from '../ProductWorkSpecPanel.vue'
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
vi.mock('@/utils/request', () => ({ default: {} }))
vi.mock('@/api/system/attachmentRef', () => ({
  attachmentRefApi: { listRefs: vi.fn(), tempUpload: vi.fn(), createRef: vi.fn() },
}))
vi.mock('@/api/system/attachment', () => ({
  attachmentApi: { setCurrent: vi.fn(), release: vi.fn() },
}))
vi.mock('@/api/product/workSpec', async (original) => ({
  ...(await original<typeof import('@/api/product/workSpec')>()),
  productWorkSpecApi: {
    get: vi.fn(),
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
