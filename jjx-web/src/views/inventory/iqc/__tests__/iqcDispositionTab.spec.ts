import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { reactive } from 'vue'
import Page from '../index.vue'
import DispositionPage from '@/views/inventory/iqc-quarantine/index.vue'

const api = vi.hoisted(() => ({ iqcList: vi.fn(), replace: vi.fn(), push: vi.fn() }))
const auth = vi.hoisted(() => ({ view: true }))
const navigation = vi.hoisted(() => ({ route: null as any }))
vi.mock('vue-router', () => ({ useRouter: () => ({ replace: api.replace, push: api.push }), useRoute: () => navigation.route }))
vi.mock('@/api/inventory/inbound', () => ({ inboundApi: { iqcList: api.iqcList } }))
vi.mock('@/directives', () => ({ hasPermi: (permission: string) => permission === 'quality:ncr:view' && auth.view }))
vi.mock('../components/IqcWorkbenchPanel.vue', () => ({ default: { template: '<div data-testid="inbound-workbench" />', props: ['inboundId'] } }))
vi.mock('@/views/quality/lot/components/LotWorkbench.vue', () => ({ default: { template: '<div data-testid="lot-workbench" />' } }))
vi.mock('@/views/inventory/iqc-quarantine/index.vue', () => ({ default: { name: 'OriginalDispositionPage', template: '<div data-testid="original-disposition">原不合格处置页</div>' } }))
const wrappers: ReturnType<typeof mount>[] = []
async function render(query: Record<string, string> = {}) {
  navigation.route = reactive({ query })
  const wrapper = mount(Page, { global: { directives: { loading: () => {} }, stubs: {
    ElTabs: { template: '<div><slot /></div>' },
    ElTabPane: { template: '<section :data-tab="name"><slot /></section>', props: ['name', 'label'] },
    ElCard: { template: '<div><slot name="header" /><slot /></div>' },
    ElButton: { template: '<button><slot /></button>' },
    ElForm: { template: '<form><slot /></form>' }, ElFormItem: { template: '<div><slot /></div>' },
    ElTable: { template: '<div />' }, ElSelect: { template: '<div />' }, ElOption: true,
    ElInput: true, ElPagination: true, ElEmpty: true,
  } } })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, state: (wrapper.vm as any).$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  auth.view = true
  api.iqcList.mockResolvedValue({ data: { records: [], total: 0 } })
})
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))

describe('来料检验第三页签复用原处置页', () => {
  it('保留作业和台账两个页签，有权限时新增第三页签；未选中不加载处置组件', async () => {
    const { wrapper } = await render()
    expect(wrapper.findAll('[data-tab]').map((tab) => tab.attributes('data-tab'))).toEqual(['inbound', 'lots', 'disposition'])
    expect(wrapper.findComponent(DispositionPage).exists()).toBe(false)
  })
  it('深链直接打开第三页签，挂载同一个原处置页且没有自动提交或跳旧路由', async () => {
    const { wrapper, state } = await render({ tab: 'disposition' })
    expect(state.activeTab).toBe('disposition')
    expect(wrapper.findComponent(DispositionPage).exists()).toBe(true)
    expect(api.push).not.toHaveBeenCalled()
    expect(api.replace).not.toHaveBeenCalled()
  })
  it('跨批次待处置按钮切换第三页签，保留当前来源查询', async () => {
    const { wrapper, state } = await render({ inboundId: '12' })
    const button = wrapper.findAll('button').find((item) => item.text() === '跨批次待处置')!
    await button.trigger('click')
    expect(state.activeTab).toBe('disposition')
    expect(api.replace).toHaveBeenLastCalledWith({ path: '/inventory/iqc', query: { inboundId: '12', tab: 'disposition' } })
    expect(wrapper.findComponent(DispositionPage).exists()).toBe(true)
  })
  it('同页URL变化可定位第三页签，返回作业页仍保留已选入库单', async () => {
    const { wrapper, state } = await render({ inboundId: '12' })
    navigation.route.query.tab = 'disposition'
    await flushPromises()
    expect(state.activeTab).toBe('disposition')
    const originalInstance = wrapper.getComponent(DispositionPage).vm.$.uid
    await state.handleTabChange('inbound')
    await flushPromises()
    expect(wrapper.getComponent(DispositionPage).vm.$.uid).toBe(originalInstance)
    expect(state.selectedRow.inboundId).toBe(12)
  })
  it('无处置查看权限时隐藏页签和入口，深链不能绕过权限', async () => {
    auth.view = false
    const { wrapper, state } = await render({ tab: 'disposition' })
    expect(wrapper.findAll('[data-tab]').map((tab) => tab.attributes('data-tab'))).toEqual(['inbound', 'lots'])
    expect(state.activeTab).toBe('inbound')
    expect(wrapper.text()).not.toContain('跨批次待处置')
    await state.handleTabChange('disposition')
    expect(state.activeTab).toBe('inbound')
    expect(wrapper.findComponent(DispositionPage).exists()).toBe(false)
  })
  it('原检验批深链与材料行定位仍正常', async () => {
    const { state } = await render({ tab: 'lots' })
    expect(state.activeTab).toBe('lots')
    await state.openLotEntry({ lotId: 8, qualityLotNo: 'LOT-8' })
    expect(api.replace).toHaveBeenLastCalledWith({ path: '/inventory/iqc', query: { tab: 'lots', lotNo: 'LOT-8' } })
  })
})
