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
import { shallowMount, flushPromises } from '@vue/test-utils'
import ProductDetail from '@/views/product/list/components/ProductDetail.vue'
const api = vi.hoisted(() => ({ full: vi.fn(), focusAttachment: vi.fn() }))
vi.mock('@/api/product', () => ({ productApi: api }))
vi.mock('@/directives', () => ({ hasPermi: () => false }))
vi.mock('@/components/product/ProductBasicInfo.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/product/bom/components/BomDetailTable.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/product/route/components/RouteProcessTable.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/product/film/components/ProductFilmPanel.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/product/ProductDocLedger.vue', async () => {
  const { defineComponent } = await import('vue')
  return { default: defineComponent({ name: 'Ledger', props: ['productId'], emits: ['loaded', 'loading'],
    setup(_, { expose }) { expose({ focusAttachment: api.focusAttachment }); }, template: '<div />' }) }
})
vi.mock('@/components/product/ProductBizTimeline.vue', () => ({ default: {
  name: 'Timeline', props: ['productId', 'productAttachmentIds', 'productDocsLoading'], emits: ['openProductDoc'], template: '<div />',
} }))
const Slot = { template: '<div><slot /></div>' }
const Pane = { props: ['name'], template: '<section :data-tab="name"><slot /></section>' }
let w: ReturnType<typeof shallowMount>
beforeEach(() => { vi.clearAllMocks(); api.full.mockResolvedValue({ data: { product: { productId: 7, productCode: 'AA001MEOO', productStatus: 1 }, films: [] } }) })
afterEach(() => w?.unmount())
async function open() {
  w = shallowMount(ProductDetail, { props: { productId: 7 }, global: {
    renderStubDefaultSlot: true, directives: { loading: () => {} },
    stubs: { ElTabs: Slot, ElTabPane: Pane, ProductDocLedger: false, ProductBizTimeline: false },
  } })
  await flushPromises()
}
describe('产品详情接入', () => {
  it('共享完整台账文件ID用于去重，关联链接定位台账，原有三个tab保留', async () => {
    await open()
    const ledger = w.findComponent({ name: 'Ledger' })
    const timeline = w.findComponent({ name: 'Timeline' })
    ledger.vm.$emit('loaded', [{ attachmentId: 10 }, { attachmentId: 11 }])
    ledger.vm.$emit('loading', false)
    await flushPromises()
    expect(timeline.props('productAttachmentIds')).toEqual([10, 11])
    expect(timeline.props('productDocsLoading')).toBe(false)
    timeline.vm.$emit('openProductDoc', 10)
    expect(api.focusAttachment).toHaveBeenCalledWith(10)
    for (const tab of ['bom', 'routing', 'film']) expect(w.find(`[data-tab="${tab}"]`).exists()).toBe(true)
    expect(w.text()).toContain('业务流转')
    expect(w.text()).not.toContain('业务流转附件')
  })
  it('切换产品不被上一产品详情回包覆盖', async () => {
    let resolve!: (value: any) => void
    api.full.mockReturnValueOnce(new Promise(done => { resolve = done }))
    await open()
    await w.setProps({ productId: 8 }); await flushPromises()
    resolve({ data: { product: { productId: 7 }, films: [] } }); await flushPromises()
    expect(w.emitted('loaded')).toHaveLength(1)
    expect(w.findComponent({ name: 'Ledger' }).props('productId')).toBe(8)
  })
})
