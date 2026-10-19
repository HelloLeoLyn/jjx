import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ProductSpecPreview from '../ProductSpecPreview.vue'
import { canPrintDocumentFile, defaultDocumentFiles, makePages, orderedPrintFiles, type DocFile, type DocsetData } from '../docset'

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
const mocks = vi.hoisted(() => ({ load: vi.fn(), renderFile: vi.fn() }))
vi.mock('../docset', async original => ({ ...await original<typeof import('../docset')>(), loadDocset: mocks.load, renderFile: mocks.renderFile }))
vi.mock('../ProductSpecSheets.vue', () => ({ default: { props: ['pages'], template: '<div class="doc-sheets" />' } }))
vi.mock('@/components/print/PrintToolbar.vue', () => ({ default: { template: '<div><slot name="actions" /></div>' } }))
const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.restoreAllMocks() })

function drawing(id: number, overrides: Partial<DocFile> = {}): DocFile {
  return { id, key: String(id), name: `${id}.pdf`, category: '结构图', documentType: 'STRUCTURE_DRAWING',
    kind: 'pdf', section: 'atlas', productFile: true, drawingNo: 'P1-ST', version: 'A', fileRole: 'PRINT', isCurrent: 1, released: 1, ...overrides }
}
const image = { url: 'data:image/png;base64,test', width: 10, height: 10 }

describe('版本打印清单（dev-20261019-021）', () => {
  it('同版多个打印件均可选，未下发新增成员不进入清单', () => {
    const files = [drawing(1), drawing(2), drawing(3, { released: 0 })]
    expect(defaultDocumentFiles(files).map(file => file.key)).toEqual(['1', '2'])
    expect(orderedPrintFiles(files, ['3', '2', '1', '2', 'missing']).map(file => file.key)).toEqual(['2', '1'])
  })

  it('正式出纸遵守所选顺序，并再次剔除未下发文件和历史工作态', () => {
    const files = [drawing(1), drawing(2), drawing(3, { released: 0 }), drawing(4, { isCurrent: 0 })]
    const data = { product: {}, bom: {}, routing: {}, workSpec: {}, warnings: [], files } as DocsetData
    const pages = makePages(data, ['atlas'], ['3', '2', '4', '1'], { '1': [image], '2': [image], '3': [image], '4': [image] })
    expect(pages.map(page => page.file?.key)).toEqual(['2', '1'])
  })

  it('已下发原稿不被尚未下发的新增打印件挤出默认清单', () => {
    const original = drawing(1, { fileRole: 'ORIGINAL' })
    expect(defaultDocumentFiles([original, drawing(2, { released: 0 })]).map(file => file.key)).toEqual(['1'])
  })

  it('已发布规范快照集合不受工作态下发/现行变化影响', () => {
    const published = drawing(1, { published: true, released: 0, isCurrent: 0 })
    expect(canPrintDocumentFile(published)).toBe(true)
    expect(orderedPrintFiles([published], ['1'])).toEqual([published])
  })

  it('打印清单可操作排序，顺序经打印窗口参数保留，未下发成员不能勾选', async () => {
    const files = [drawing(1), drawing(2), drawing(3, { released: 0 })]
    mocks.load.mockResolvedValue({ product: {}, bom: {}, routing: {}, workSpec: {}, warnings: [], files })
    mocks.renderFile.mockResolvedValue([image])
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)
    const wrapper = mount(ProductSpecPreview, { props: { productId: 42, initialSections: ['atlas'], initialFiles: ['2', '3', '1'] } })
    wrappers.push(wrapper); await flushPromises()
    const state = (wrapper.vm as any).$.setupState
    expect(state.selectedFiles).toEqual(['2', '1'])
    const selected = wrapper.findAll('.file-choice')
    expect(selected.map(row => row.text())).toEqual([expect.stringContaining('2.pdf'), expect.stringContaining('1.pdf')])
    const up = selected[1].findAll('button').find(button => button.text() === '上移')!
    await up.trigger('click'); await flushPromises()
    expect(state.selectedFiles).toEqual(['1', '2'])
    expect(state.pages.map((page: any) => page.file?.key)).toEqual(['1', '2'])
    state.showHistory = true; await flushPromises()
    const pending = wrapper.findAll('.file-choice').find(row => row.text().includes('3.pdf'))!
    expect(pending.get('input').attributes('disabled')).toBeDefined()
    const print = wrapper.findAll('button').find(button => button.text() === '打印所选文档')!
    await print.trigger('click'); await flushPromises()
    expect(open).toHaveBeenCalledWith(expect.stringContaining('files=1%2C2'), '_blank')
    const reopened = mount(ProductSpecPreview, { props: { productId: 42, initialSections: ['atlas'], initialFiles: ['1', '2'] } })
    wrappers.push(reopened); await flushPromises()
    expect((reopened.vm as any).$.setupState.selectedFiles).toEqual(['1', '2'])
  })
})
