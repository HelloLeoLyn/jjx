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
import ProductBizTimeline from '../ProductBizTimeline.vue'
const api = vi.hoisted(() => ({ bizAttachments: vi.fn(), previewUrlByRef: vi.fn(id => `preview/${id}`), downloadUrlByRef: vi.fn(id => `download/${id}`) }))
vi.mock('@/api/product', () => ({ productApi: api }))
vi.mock('@/api/system/attachmentRef', () => ({ attachmentRefApi: api }))
const groups = [
  { sourceType: 'quotation', sourceId: 2, sourceNo: 'QT-2', files: [
    { id: 61, attachmentId: 10, fileName: 'ledger.pdf' },
    { id: 62, attachmentId: 20, fileName: 'quote.pdf', createTime: '2026-10-19T10:00:00' },
    { id: 63, attachmentId: 20, fileName: 'duplicate.pdf' },
  ] },
  { sourceType: 'inquiry', sourceId: 1, sourceNo: 'INQ-1', files: [{ id: 64, attachmentId: 30, fileName: 'inquiry.pdf' }] },
]
let w: ReturnType<typeof mount>
beforeEach(() => { vi.clearAllMocks(); api.bizAttachments.mockResolvedValue({ data: groups }) })
afterEach(() => { w?.unmount(); vi.restoreAllMocks() })
async function open() { w = mount(ProductBizTimeline, { props: { productId: 7, productAttachmentIds: [10] } }); await flushPromises() }
const button = (text: string) => w.findAll('button').find(item => item.text() === text)!

describe('产品业务流转时间线', () => {
  it('单据按接口顺序展示，附件计数去重，已归入资料的文件只给关联链接', async () => {
    await open()
    expect(w.text().indexOf('QT-2')).toBeLessThan(w.text().indexOf('INQ-1'))
    expect(w.text()).toContain('附件 2')
    expect(w.text()).toContain('客户：—')
    expect(w.text()).toContain('日期：—')
    expect(w.findAll('button').filter(item => item.text() === '下载')).toHaveLength(1)
    const openUrl = vi.spyOn(window, 'open').mockImplementation(() => null)
    await button('已归入产品资料').trigger('click')
    expect(w.emitted('openProductDoc')).toEqual([[10]])
    expect(openUrl).not.toHaveBeenCalled()
    await button('quote.pdf').trigger('click')
    expect(openUrl).toHaveBeenCalledWith('preview/62', '_blank', 'noopener,noreferrer')
    await button('INQ-1').trigger('click'); await flushPromises()
    expect(w.text()).toContain('inquiry.pdf')
    expect(w.text()).not.toMatch(/上传附件|移除/)
  })
  it('产品资料筛选不导致重复下载入口；产品资料加载中不提前显示文件', async () => {
    await open()
    await w.setProps({ productDocsLoading: true })
    expect(w.find('.el-skeleton').exists()).toBe(true)
    expect(w.findAll('button').filter(item => item.text() === '下载')).toHaveLength(0)
    await w.setProps({ productDocsLoading: false })
    await flushPromises()
    expect(button('已归入产品资料').exists()).toBe(true)
  })
  it('接口失败可重试，成功空列表显示空态', async () => {
    api.bizAttachments.mockRejectedValueOnce(new Error('流转读取失败')).mockResolvedValue({ data: [] })
    await open()
    expect(w.text()).toContain('流转读取失败')
    await button('重试').trigger('click'); await flushPromises()
    expect(w.text()).toContain('暂无业务流转')
    expect(api.bizAttachments).toHaveBeenCalledTimes(2)
  })
  it('切换产品不接收旧回包', async () => {
    let resolve!: (value: any) => void
    api.bizAttachments.mockReturnValueOnce(new Promise(done => { resolve = done })).mockResolvedValueOnce({ data: [] })
    w = mount(ProductBizTimeline, { props: { productId: 7, productAttachmentIds: [] } })
    await w.setProps({ productId: 8 }); await flushPromises()
    resolve({ data: groups }); await flushPromises()
    expect(w.text()).toContain('暂无业务流转')
    expect(w.text()).not.toContain('QT-2')
  })
})
