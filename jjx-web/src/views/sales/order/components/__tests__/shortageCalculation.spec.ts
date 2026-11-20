import { beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('@/api/inventory/alert', () => ({ alertApi: { orderShortagePreview: vi.fn(), checkOrderShortage: vi.fn() } }))
vi.mock('@/directives', () => ({ hasPermi: () => true }))
import { alertApi } from '@/api/inventory/alert'
import Dialog from '../ShortageCheckDialog.vue'

const container = defineComponent({ setup(_, { slots }) { return () => h('div', [slots.default?.(), slots.footer?.()]) } })
const alert = defineComponent({ props: ['title'], setup(p, { slots }) { return () => h('aside', [p.title, slots.default?.()]) } })
const button = defineComponent({ props: ['disabled'], setup(p, { slots }) { return () => h('button', { disabled: p.disabled }, slots.default?.()) } })
function mountDialog() {
  return mount(Dialog, {
    props: { modelValue: true, orderId: 7, orderNo: 'SO7' },
    global: { directives: { loading: () => {} }, stubs: { ElDialog: container, ElAlert: alert, ElButton: button,
      ElTable: true, ElTableColumn: true, ElTag: true } },
  })
}
beforeEach(() => { vi.clearAllMocks() })
it('shows the affected row and never announces success for incomplete demand', async () => {
  vi.mocked(alertApi.orderShortagePreview).mockResolvedValue({ data: {
    orderNo: 'SO7', productRows: [], materialRows: [], calculationComplete: false,
    calculationIssues: [{ message: 'BOM9/明细10/MAT11：PCS→M缺少已保存换算依据' }],
    summary: { shortageCount: 0, coveredCount: 0, stockCoveredCount: 0, noBomCount: 0 },
  } } as any)
  const wrapper = mountDialog(); await flushPromises()
  expect(wrapper.text()).toContain('MAT11')
  expect(wrapper.text()).toContain('需求尚未完整计算')
  expect(wrapper.text()).not.toContain('齐套通过：')
  expect(alertApi.checkOrderShortage).not.toHaveBeenCalled()
  wrapper.unmount()
})
it('disables confirmation even when an incomplete response has a positive shortage count', async () => {
  vi.mocked(alertApi.orderShortagePreview).mockResolvedValue({ data: {
    calculationIssues: [{ message: '需核实' }], summary: { shortageCount: 1 }, productRows: [], materialRows: [],
  } } as any)
  const wrapper = mountDialog(); await flushPromises()
  const confirm = wrapper.findAll('button').find(b => b.text().includes('确认生成'))!
  expect(confirm.attributes('disabled')).toBeDefined(); await confirm.trigger('click')
  expect(alertApi.checkOrderShortage).not.toHaveBeenCalled(); wrapper.unmount()
})
it('complete demand keeps normal confirmation available', async () => {
  vi.mocked(alertApi.orderShortagePreview).mockResolvedValue({ data: {
    calculationComplete: true, calculationIssues: [], summary: { shortageCount: 1 }, productRows: [], materialRows: [],
  } } as any)
  vi.mocked(alertApi.checkOrderShortage).mockResolvedValue({} as any)
  const wrapper = mountDialog(); await flushPromises()
  await wrapper.findAll('button').find(b => b.text().includes('确认生成'))!.trigger('click'); await flushPromises()
  expect(alertApi.checkOrderShortage).toHaveBeenCalledWith(7); expect(wrapper.emitted('success')).toHaveLength(1)
  wrapper.unmount()
})
