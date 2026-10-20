vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/tabs/style/css', () => ({}))
vi.mock('element-plus/es/components/tab-pane/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/image-viewer/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/skeleton/style/css', () => ({}))
vi.mock('element-plus/es/components/skeleton-item/style/css', () => ({}))
vi.mock('element-plus/es/components/timeline/style/css', () => ({}))
vi.mock('element-plus/es/components/timeline-item/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions-item/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/row/style/css', () => ({}))
vi.mock('element-plus/es/components/col/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ElInput, ElSelect, ElTable, ElTabs } from 'element-plus'
import ProductDocLedger from '../ProductDocLedger.vue'
import { AttachmentSourceTypeEnum } from '@/enums/system/AttachmentEnum'
const api = vi.hoisted(() => ({ refsByProduct: vi.fn(), documentTypes: vi.fn(), previewUrlByRef: vi.fn(id => `preview/${id}`), downloadUrlByRef: vi.fn(id => `download/${id}`) }))
vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: api }))
const dictionary = [
  { dict_code: 'attachment_category', item_key: 'ENG', label: '工程资料' },
  { dict_code: 'attachment_category', item_key: 'CUS', label: '客户资料' },
  { dict_code: 'attachment_document_type', item_key: 'DRAWING', label: '工程图纸', ext_data: { parent: 'ENG', sources: ['product'] } },
  { dict_code: 'attachment_document_type', item_key: 'REQUIREMENT', label: '客户要求', ext_data: { parent: 'CUS', sources: ['product'] } },
]
const files = [
  { id: 1, attachmentId: 10, sourceType: 'product', sourceId: 7, fileName: 'panel.pdf', documentType: 'DRAWING', productCode: 'AA001MEOO', fileSize: 1024 },
  { id: 2, attachmentId: 11, sourceType: 'product', sourceId: 7, fileName: 'spec.pdf', documentType: 'REQUIREMENT' },
  { id: 3, attachmentId: 12, sourceType: 'product', sourceId: 7, fileName: 'other.pdf' },
  { id: 4, attachmentId: 13, sourceType: 'quotation', sourceId: 8, fileName: 'quotation.pdf' },
]
let w: ReturnType<typeof mount>
beforeEach(() => {
  vi.clearAllMocks()
  api.refsByProduct.mockResolvedValue({ data: files })
  api.documentTypes.mockResolvedValue({ data: dictionary })
})
afterEach(() => { w?.unmount(); vi.restoreAllMocks(); document.body.innerHTML = '' })
async function open() { w = mount(ProductDocLedger, { props: { productId: 7 }, attachTo: document.body }); await flushPromises(); return w }
const rows = () => w.findComponent(ElTable).props('data') as typeof files

describe('产品资料台账', () => {
  it('按产品保留work_spec与历史product引用，过滤其它来源', async () => {
    api.refsByProduct.mockResolvedValue({ data: [...files,
      { id: 5, attachmentId: 14, sourceType: 'work_spec', sourceId: 7, fileName: 'work-spec.pdf', documentType: 'DRAWING' },
    ] })
    await open()
    expect(api.refsByProduct).toHaveBeenCalledWith(7)
    expect(rows().map(row => row.attachmentId)).toEqual([10, 11, 12, 14])
    expect(AttachmentSourceTypeEnum.getLabel('work_spec')).toBe('产品作业规范')
  })
  it('只读读取产品引用，按一级/二级/文件名筛选且计数同步', async () => {
    await open()
    expect(rows()).toHaveLength(3)
    expect(w.text()).toContain('3 / 3 个文件')
    w.findComponent(ElTabs).vm.$emit('update:modelValue', 'ENG'); await flushPromises()
    expect(rows().map(row => row.attachmentId)).toEqual([10])
    expect(w.text()).toContain('1 / 3 个文件')
    w.findComponent(ElTabs).vm.$emit('update:modelValue', ''); await flushPromises()
    w.findComponent(ElSelect).vm.$emit('update:modelValue', 'REQUIREMENT'); await flushPromises()
    expect(rows().map(row => row.attachmentId)).toEqual([11])
    w.findComponent(ElSelect).vm.$emit('update:modelValue', ''); await flushPromises()
    w.findComponent(ElInput).vm.$emit('update:modelValue', 'PANEL'); await flushPromises()
    expect(rows().map(row => row.attachmentId)).toEqual([10])
    expect(w.text()).not.toMatch(/上传附件|移除|只看当前版本/)
    expect(w.emitted('loaded')!.at(-1)![0]).toHaveLength(3)
  })
  it('关联链接能清除筛选并定位对应文件，预览使用引用ID', async () => {
    await open()
    w.findComponent(ElInput).vm.$emit('update:modelValue', 'no-match'); await flushPromises()
    expect(w.text()).toContain('未找到符合条件的产品资料')
    await w.vm.focusAttachment(10); await flushPromises()
    expect(rows()).toHaveLength(3)
    const file = w.find('[data-attachment-id="10"]')
    expect(document.activeElement).toBe(file.element)
    const openUrl = vi.spyOn(window, 'open').mockImplementation(() => null)
    await file.trigger('click')
    expect(openUrl).toHaveBeenCalledWith('preview/1', '_blank', 'noopener,noreferrer')
  })
  it('加载失败可重试，成功空列表展示空态', async () => {
    api.refsByProduct.mockRejectedValueOnce(new Error('network')).mockResolvedValue({ data: [] })
    await open()
    expect(w.text()).toContain('产品资料加载失败')
    await w.findAll('button').find(button => button.text() === '重试')!.trigger('click'); await flushPromises()
    expect(api.refsByProduct).toHaveBeenCalledTimes(2)
    expect(w.text()).toContain('暂无产品资料')
  })
  it('切换产品后忽略旧请求，不带入上一产品资料', async () => {
    let resolve!: (value: any) => void
    api.refsByProduct.mockReturnValueOnce(new Promise(done => { resolve = done })).mockResolvedValueOnce({ data: [] })
    w = mount(ProductDocLedger, { props: { productId: 7 } })
    await w.setProps({ productId: 8 }); await flushPromises()
    resolve({ data: files }); await flushPromises()
    expect(w.text()).toContain('暂无产品资料')
    expect(w.emitted('loaded')!.at(-1)![0]).toEqual([])
  })
})
