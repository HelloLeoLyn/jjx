vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/timeline/style/css', () => ({}))
vi.mock('element-plus/es/components/timeline-item/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/link/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-menu/style/css', () => ({}))
vi.mock('element-plus/es/components/dropdown-item/style/css', () => ({}))
vi.mock('element-plus/es/components/popconfirm/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))
vi.mock('element-plus/es/components/overlay/style/css', () => ({}))
vi.mock('element-plus/theme-chalk/base.css', () => ({}))
vi.mock('@/components/common-ui/TableActionColumn/index.vue', () => ({
  default: { name: 'TableActionColumn', template: '<div />' },
}))
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import BomVersionTimeline from '../BomVersionTimeline.vue'
import { productBomApi } from '@/api/product/bom'
import { BomStatusEnum } from '@/enums/product'

vi.mock('@/api/product/bom', () => ({ productBomApi: { getBomVersions: vi.fn() } }))

const current = {
  bomId: 12,
  bomCode: 'A',
  bomVersion: 'V2',
  approveStatus: BomStatusEnum.APPROVED.value,
  isCurrent: true,
}
const history = {
  bomId: 11,
  bomCode: 'A',
  bomVersion: 'V1',
  approveStatus: BomStatusEnum.DRAFT.value,
  isCurrent: false,
}
const Slot = defineComponent({ template: '<div><slot /></div>' })
function mount() {
  return shallowMount(BomVersionTimeline, {
    props: { bomCode: 'A', actions: [{ key: 'copy', label: '复制BOM' }] },
    global: {
      directives: { loading: () => {} },
      stubs: {
        ElTimeline: Slot,
        ElTimelineItem: Slot,
        ElTag: Slot,
        ElButton: Slot,
        ElTable: Slot,
        TableActionColumn: defineComponent({
          template: "<button @click=\"$emit('action', 'copy', row)\">复制</button>",
          data: () => ({ row: history }),
        }),
      },
    },
  })
}
beforeEach(() => vi.resetAllMocks())

describe('BOM版本时间线', () => {
  it('按编码加载所有版本，显示各自状态和现行标记，操作传递历史版本ID', async () => {
    vi.mocked(productBomApi.getBomVersions).mockResolvedValue({ data: [current, history] } as any)
    const wrapper = mount()
    await flushPromises()
    expect(productBomApi.getBomVersions).toHaveBeenCalledWith('A')
    expect(wrapper.text()).toContain('V2')
    expect(wrapper.text()).toContain('V1')
    expect(wrapper.text()).toContain(BomStatusEnum.APPROVED.label)
    expect(wrapper.text()).toContain(BomStatusEnum.DRAFT.label)
    expect(wrapper.text().match(/现行/g)).toHaveLength(1)
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('action')?.[0]).toEqual(['copy', history])
    wrapper.unmount()
  })

  it('加载失败展示重试入口，重试重新请求；编码变化重新查询', async () => {
    vi.mocked(productBomApi.getBomVersions)
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValue({ data: [history] } as any)
    const wrapper = mount()
    await flushPromises()
    expect(
      wrapper.findComponent({ name: 'ElAlert' }).exists() ||
        wrapper.html().includes('版本时间线加载失败')
    ).toBe(true)
    const retry = wrapper.findAllComponents(Slot).find((component) => component.text() === '重试')
    expect(retry).toBeDefined()
    await retry!.trigger('click')
    await flushPromises()
    expect(productBomApi.getBomVersions).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('V1')
    await wrapper.setProps({ bomCode: 'B' })
    await flushPromises()
    expect(productBomApi.getBomVersions).toHaveBeenLastCalledWith('B')
    wrapper.unmount()
  })
})
