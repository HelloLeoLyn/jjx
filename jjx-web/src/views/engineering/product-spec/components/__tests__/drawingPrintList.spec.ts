import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ProductSpecPreview from '../ProductSpecPreview.vue'
import { normalizeWorkSpec } from '@/api/product/workSpec'
import {
  canPrintDocumentFile,
  defaultDocumentFiles,
  makePages,
  orderedPrintFiles,
  buildDocsetFromSnapshot,
  type DocFile,
  type DocsetData,
} from '../docset'

vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox-group/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/link/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
const currentApi = vi.hoisted(() => ({
  get: vi.fn(),
  refsByProduct: vi.fn(),
  documentTypes: vi.fn(),
  customerDocs: vi.fn(),
}))
vi.mock('@/api/product/workSpec', async (original) => ({
  ...(await original<typeof import('@/api/product/workSpec')>()),
  productWorkSpecApi: currentApi,
}))
vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: currentApi }))
vi.mock('@/api/system/attachment', () => ({ attachmentApi: currentApi }))
const mocks = vi.hoisted(() => ({ load: vi.fn(), loadVersion: vi.fn(), renderFile: vi.fn() }))
vi.mock('../docset', async (original) => ({
  ...(await original<typeof import('../docset')>()),
  loadDocset: mocks.load,
  loadDocsetForVersion: mocks.loadVersion,
  renderFile: mocks.renderFile,
}))
vi.mock('@/components/product/ProductFilePreview.vue', () => ({
  default: { props: ['modelValue', 'file'], template: '<div />' },
}))
vi.mock('../ProductSpecSheets.vue', () => ({
  default: { props: ['pages'], template: '<div class="doc-sheets" />' },
}))
vi.mock('@/components/print/PrintToolbar.vue', () => ({
  default: { template: '<div><slot name="actions" /></div>' },
}))
const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.restoreAllMocks()
})

function drawing(id: number, overrides: Partial<DocFile> = {}): DocFile {
  return {
    id,
    key: String(id),
    name: `${id}.pdf`,
    category: '结构图',
    documentType: 'STRUCTURE_DRAWING',
    kind: 'pdf',
    section: 'atlas',
    productFile: true,
    drawingNo: 'P1-ST',
    version: 'A',
    fileRole: 'PRINT',
    isCurrent: 1,
    released: 1,
    ...overrides,
  }
}
const image = { url: 'data:image/png;base64,test', width: 10, height: 10 }

describe('版本打印清单（dev-20261019-021）', () => {
  it('旧非托管结构图仍保留原有下发保护', async () => {
    mocks.load.mockResolvedValue({
      product: {},
      bom: { items: [] },
      routing: { items: [] },
      workSpec: normalizeWorkSpec({ structureFileId: 1 }),
      files: [drawing(1, { isCurrent: 0, released: 0 })],
      warnings: [],
    })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    const wrapper = mount(ProductSpecPreview, {
      props: { productId: 42, initialSections: ['spec'] },
    })
    wrappers.push(wrapper)
    await flushPromises()
    const state = (wrapper.vm as any).$.setupState
    expect(state.error).toContain('尚未下发')
    expect(state.canPrint).toBe(false)
  })
  it.each([
    { frozen: false, print: true },
    { frozen: false, print: false },
    { frozen: true, print: true },
    { frozen: true, print: false },
  ])('新结构图无旧图种，定版=$frozen 打印=$print 时规范正文仍可打印', async ({ frozen, print }) => {
    const file = drawing(1, {
      category: '',
      documentType: 'STRUCTURE_DRAWING',
      published: frozen,
      draftManaged: !frozen,
      printSelected: print,
      isCurrent: 0,
      released: 0,
    })
    const data = {
      product: {},
      bom: { items: [] },
      routing: { items: [] },
      workSpec: normalizeWorkSpec({ draftManaged: true, structureFileId: 1 }),
      files: [file],
      warnings: [],
    }
    mocks.renderFile.mockClear()
    mocks.renderFile.mockResolvedValue([image])
    if (frozen) mocks.loadVersion.mockResolvedValue(data)
    else mocks.load.mockResolvedValue(data)
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    const wrapper = mount(ProductSpecPreview, {
      props: { productId: 42, versionId: frozen ? 10 : undefined, initialSections: ['spec'] },
    })
    wrappers.push(wrapper)
    await flushPromises()
    const state = (wrapper.vm as any).$.setupState
    state.overflow = { 'spec-0': [] }
    await flushPromises()
    expect(state.error).toBe('')
    expect(state.canPrint).toBe(true)
    expect(state.structureImage).toBe(print ? image.url : '')
    expect(mocks.renderFile).toHaveBeenCalledTimes(print ? 1 : 0)
    state.showHistory = true
    state.selectedSections = ['spec', 'atlas']
    await flushPromises()
    expect(wrapper.findAll('.file-choice')[0].text()).toContain('下载原文件')
    expect(wrapper.findAll('.file-choice')[0].text()).toContain('预览')
  })
  it('完整快照组装保留附件冻结顺序、打印选择和负责人，不按附件ID重排', () => {
    const data = buildDocsetFromSnapshot(
      42,
      JSON.stringify({
        schemaVersion: 1,
        spec: { draftManaged: true, preparedById: 7, preparedByName: '工程甲' },
        counts: { files: 3 },
        files: [3, 1, 2].map((attachmentId) => ({
          attachmentId,
          section: 'atlas',
          documentType: 'STRUCTURE_DRAWING',
          fileName: attachmentId + '.pdf',
          printSelected: attachmentId !== 1,
        })),
      })
    )
    expect(data.files.map((file) => file.id)).toEqual([3, 1, 2])
    expect(defaultDocumentFiles(data.files).map((file) => file.id)).toEqual([3, 2])
    expect(data.workSpec.preparedByName).toBe('工程甲')
  })
  it('统一定版按冻结打印选择及原顺序，未下发也可打印；不打印文件仍保留', () => {
    const files = [
      drawing(3, { published: true, printSelected: true, released: 0 }),
      drawing(1, { published: true, printSelected: false }),
      drawing(2, { published: true, printSelected: true }),
    ]
    expect(defaultDocumentFiles(files).map((file) => file.id)).toEqual([3, 2])
    expect(orderedPrintFiles(files, ['2', '1', '3']).map((file) => file.id)).toEqual([3, 2])
    expect(files).toHaveLength(3)
  })
  it('旧版不打印PDF仍有预览和下载，打印窗口保持versionId和冻结顺序', async () => {
    const files = [
      drawing(3, { published: true, printSelected: true }),
      drawing(1, { published: true, printSelected: false }),
      drawing(2, { published: true, printSelected: true }),
    ]
    mocks.loadVersion.mockResolvedValue({
      product: {},
      bom: {},
      routing: {},
      workSpec: { draftManaged: true },
      warnings: [],
      files,
    })
    mocks.renderFile.mockResolvedValue([image])
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)
    const wrapper = mount(ProductSpecPreview, {
      props: {
        productId: 42,
        versionId: 10,
        initialSections: ['atlas'],
        initialFiles: ['2', '3', '1'],
      },
    })
    wrappers.push(wrapper)
    await flushPromises()
    const state = (wrapper.vm as any).$.setupState
    expect(state.selectedFiles).toEqual(['3', '2'])
    state.showHistory = true
    await flushPromises()
    const nonPrinting = wrapper.findAll('.file-choice').find((row) => row.text().includes('1.pdf'))!
    expect(nonPrinting.text()).toContain('下载原文件')
    expect(nonPrinting.text()).toContain('预览')
    expect(nonPrinting.get('input').attributes('disabled')).toBeDefined()
    await nonPrinting
      .findAll('button')
      .find((button) => button.text() === '预览')!
      .trigger('click')
    expect(state.attachmentPreviewFile.id).toBe(1)
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '打印所选文档')!
      .trigger('click')
    expect(open).toHaveBeenCalledWith(expect.stringContaining('versionId=10'), '_blank')
    expect(open).toHaveBeenCalledWith(expect.stringContaining('files=3%2C2'), '_blank')
    expect(mocks.load).not.toHaveBeenCalled()
  })
  it('同版多个打印件均可选，未下发新增成员不进入清单', () => {
    const files = [drawing(1), drawing(2), drawing(3, { released: 0 })]
    expect(defaultDocumentFiles(files).map((file) => file.key)).toEqual(['1', '2'])
    expect(
      orderedPrintFiles(files, ['3', '2', '1', '2', 'missing']).map((file) => file.key)
    ).toEqual(['2', '1'])
  })

  it('正式出纸遵守所选顺序，并再次剔除未下发文件和历史工作态', () => {
    const files = [
      drawing(1),
      drawing(2),
      drawing(3, { released: 0 }),
      drawing(4, { isCurrent: 0 }),
    ]
    const data = {
      product: {},
      bom: {},
      routing: {},
      workSpec: {},
      warnings: [],
      files,
    } as DocsetData
    const pages = makePages(data, ['atlas'], ['3', '2', '4', '1'], {
      '1': [image],
      '2': [image],
      '3': [image],
      '4': [image],
    })
    expect(pages.map((page) => page.file?.key)).toEqual(['2', '1'])
  })

  it('已下发原稿不被尚未下发的新增打印件挤出默认清单', () => {
    const original = drawing(1, { fileRole: 'ORIGINAL' })
    expect(
      defaultDocumentFiles([original, drawing(2, { released: 0 })]).map((file) => file.key)
    ).toEqual(['1'])
  })

  it('已发布规范快照集合不受工作态下发/现行变化影响', () => {
    const published = drawing(1, { published: true, released: 0, isCurrent: 0 })
    expect(canPrintDocumentFile(published)).toBe(true)
    expect(orderedPrintFiles([published], ['1'])).toEqual([published])
  })

  it('打印清单可操作排序，顺序经打印窗口参数保留，未下发成员不能勾选', async () => {
    const files = [drawing(1), drawing(2), drawing(3, { released: 0 })]
    mocks.load.mockResolvedValue({
      product: {},
      bom: {},
      routing: {},
      workSpec: {},
      warnings: [],
      files,
    })
    mocks.renderFile.mockResolvedValue([image])
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)
    const wrapper = mount(ProductSpecPreview, {
      props: { productId: 42, initialSections: ['atlas'], initialFiles: ['2', '3', '1'] },
    })
    wrappers.push(wrapper)
    await flushPromises()
    const state = (wrapper.vm as any).$.setupState
    expect(state.selectedFiles).toEqual(['2', '1'])
    const selected = wrapper.findAll('.file-choice')
    expect(selected.map((row) => row.text())).toEqual([
      expect.stringContaining('2.pdf'),
      expect.stringContaining('1.pdf'),
    ])
    const up = selected[1].findAll('button').find((button) => button.text() === '上移')!
    await up.trigger('click')
    await flushPromises()
    expect(state.selectedFiles).toEqual(['1', '2'])
    expect(state.pages.map((page: any) => page.file?.key)).toEqual(['1', '2'])
    state.showHistory = true
    await flushPromises()
    const pending = wrapper.findAll('.file-choice').find((row) => row.text().includes('3.pdf'))!
    expect(pending.get('input').attributes('disabled')).toBeDefined()
    const print = wrapper.findAll('button').find((button) => button.text() === '打印所选文档')!
    await print.trigger('click')
    await flushPromises()
    expect(open).toHaveBeenCalledWith(expect.stringContaining('files=1%2C2'), '_blank')
    const reopened = mount(ProductSpecPreview, {
      props: { productId: 42, initialSections: ['atlas'], initialFiles: ['1', '2'] },
    })
    wrappers.push(reopened)
    await flushPromises()
    expect((reopened.vm as any).$.setupState.selectedFiles).toEqual(['1', '2'])
  })
})

describe('工程字典和冻结分栏实际出纸', () => {
  it('WORK_INSTRUCTION及未知工程类型按冻结atlas出纸，不按今天映射改栏；spec附件也实际出纸', () => {
    const data = buildDocsetFromSnapshot(
      42,
      JSON.stringify({
        schemaVersion: 1,
        spec: { draftManaged: true },
        counts: { files: 3 },
        files: [
          {
            attachmentId: 1,
            fileName: 'work.pdf',
            documentType: 'WORK_INSTRUCTION',
            section: 'atlas',
            printSelected: true,
          },
          {
            attachmentId: 2,
            fileName: 'future.pdf',
            documentType: 'FUTURE_TYPE',
            section: 'atlas',
            printSelected: true,
          },
          {
            attachmentId: 3,
            fileName: 'spec.pdf',
            documentType: 'WORK_INSTRUCTION',
            section: 'spec',
            printSelected: true,
          },
        ],
      })
    )
    const pages = makePages(data, ['spec', 'atlas'], ['2', '3', '1'], {
      '1': [image],
      '2': [image],
      '3': [image],
    })
    expect(
      pages.filter((page) => page.kind === 'image').map((page) => [page.file?.id, page.section])
    ).toEqual([
      [3, 'spec'],
      [1, 'atlas'],
      [2, 'atlas'],
    ])
  })
  it('当前态承接新增工程字典类型，移除后重开不混回BOM/停用工程引用', async () => {
    const actual = await vi.importActual<typeof import('../docset')>('../docset')
    let ids = [1, 2]
    currentApi.get.mockImplementation(async () => ({
      data: {
        sourceData: { product: { productId: 42 }, bom: { items: [] }, routing: { items: [] } },
        revision: 'r1',
        draftManaged: true,
        draftAttachments: ids.map((attachmentId) => ({
          attachmentId,
          documentType: attachmentId === 1 ? 'WORK_INSTRUCTION' : 'BOM',
          print: true,
        })),
        draftFiles: ids.map((attachmentId) => ({
          attachmentId,
          refId: attachmentId + 100,
          fileName: attachmentId + '.pdf',
          documentType: attachmentId === 1 ? 'WORK_INSTRUCTION' : 'BOM',
        })),
      },
    }))
    currentApi.customerDocs.mockResolvedValue({ data: [] })
    currentApi.refsByProduct.mockResolvedValue({
      data: [
        { attachmentId: 2, fileName: 'removed-bom.pdf', documentType: 'BOM' },
        { attachmentId: 3, fileName: 'disabled.pdf', documentType: 'DISABLED_ENGINEERING' },
        { attachmentId: 4, fileName: 'customer.pdf', documentType: 'CUSTOMER_ORIGINAL' },
      ],
    })
    currentApi.documentTypes.mockResolvedValue({
      data: [
        ...['WORK_INSTRUCTION', 'BOM', 'DISABLED_ENGINEERING'].map((item_key) => ({
          dict_code: 'attachment_document_type',
          item_key,
          is_active: item_key === 'DISABLED_ENGINEERING' ? 0 : 1,
          ext_data: { parent: 'ENGINEERING_DOC' },
        })),
        {
          dict_code: 'attachment_document_type',
          item_key: 'CUSTOMER_ORIGINAL',
          ext_data: JSON.stringify({ parent: 'CUSTOMER_DOC' }),
        },
      ],
    })
    const initial = await actual.loadDocset(42)
    expect(initial.files.filter((file) => file.draftManaged).map((file) => file.section)).toEqual([
      'atlas',
      'atlas',
    ])
    const pages = makePages(initial, ['atlas'], ['1', '2'], { '1': [image], '2': [image] })
    expect(pages.map((page) => page.file?.id)).toEqual([1, 2])
    ids = [1]
    const reopened = await actual.loadDocset(42)
    expect(reopened.files.map((file) => file.id)).toEqual([1, 4])
    currentApi.documentTypes.mockRejectedValue(new Error('字典服务不可用'))
    const unavailable = await actual.loadDocset(42)
    expect(unavailable.files.map((file) => file.id)).toEqual([1])
    expect(unavailable.warnings.join()).toContain('字典不可用')
  })
})
