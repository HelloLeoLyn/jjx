import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import RouteProcessTable from '../RouteProcessTable.vue'
import ProductPrintSpecPanel from '@/views/engineering/product-spec/components/ProductPrintSpecPanel.vue'
import PrintPlateDetails from '@/components/printing/PrintPlateDetails.vue'
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ hasPermission: () => false }) }))
vi.mock('@/views/engineering/product-spec/components/WorkSpecWorkflow.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/api/engineering/printingSearch', () => ({ printingSearchApi: { plate: vi.fn(), search: vi.fn() } }))
const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))
describe('工艺路线详情的印刷资料列', () => {
  it('仅显示网版及关联资料入口，不再平铺菲林、版本、网框和固定要求', async () => {
    const wrapper = mount(RouteProcessTable, { props: { items: [{ itemId: 1, majorCategory: 'PRINT', processCategory: 'PANEL', processName: '面板印刷', defaultPlateId: 4, defaultPlateNo: 'PLATE-004', artworkVersionId: 3, artworkNo: 'ART-03', artworkVersionNo: 'V1', printRequirements: '{"meshMin":100}', customProcessParams: '{"filmNo":"旧菲林文字"}' }] as any }, global: { directives: { loading: {} } } })
    wrappers.push(wrapper); await flushPromises()
    expect(wrapper.findComponent(ProductPrintSpecPanel).props('compactResources')).toBe(true)
    const headers = wrapper.findAll('th').map(th => th.text())
    expect(headers).toContain('网版'); expect(headers).toContain('关联资料')
    for (const old of ['菲林', '图案版本', '固定工艺要求', '建议网版 / 网框']) expect(headers).not.toContain(old)
    expect(wrapper.text()).toContain('PLATE-004')
    expect(wrapper.findComponent(PrintPlateDetails).props('readOnly')).toBe(true)
    expect(wrapper.text()).not.toContain('旧菲林文字')
  })
})
