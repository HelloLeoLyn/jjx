import { defineComponent, h, inject, provide } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AlertPage from '../index.vue'
import { AlertStatusEnum } from '@/enums/inventory/AlertEnum'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/api/inventory/alert', () => ({ alertApi: {
  list: vi.fn().mockResolvedValue({ data: { total: 2, records: [
    { alertId: 1, currentStock: 0, status: 0, relatedOrderNo: 'PO-FIELD', processRemark: '生成采购订单 PO-OLD' },
    { alertId: 2, currentStock: 0, status: 2, relatedOrderNo: null, processRemark: '生成采购订单 PO-LEGACY' },
  ] } }),
  unprocessed: vi.fn().mockResolvedValue({ data: [] }),
} }))
const Table = defineComponent({
  props: ['data'], setup(props, { slots }) {
    provide('rows', () => props.data || [])
    return () => h('div', slots.default?.())
  },
})
const Column = defineComponent({
  props: ['label'], setup(props, { slots }) {
    const rows = inject<() => any[]>('rows', () => [])
    return () => h('div', { 'data-column': props.label }, rows().map(row => slots.default?.({ row })))
  },
})
const Slot = defineComponent({ setup(_, { slots }) { return () => h('div', slots.default?.()) } })
function renderPage() {
  return shallowMount(AlertPage, { global: {
    stubs: { ElTable: Table, ElTableColumn: Column, ElButton: Slot, ElLink: Slot, ElRow: Slot,
      ElCol: Slot, ElCard: Slot, ElForm: Slot, ElFormItem: Slot, ElSelect: Slot },
    directives: { hasPermi: {}, loading: {} },
  } })
}
describe('库存预警页面', () => {
  it('不渲染单条或批量上报按钮', async () => {
    const wrapper = renderPage(); await flushPromises()
    expect(wrapper.text()).not.toContain('标记已上报')
    wrapper.unmount()
  })
  it('状态筛选仅含未处理、已转采购单、已解除', async () => {
    const wrapper = renderPage(); await flushPromises()
    const options = wrapper.findAll('el-option-stub')
    for (const status of AlertStatusEnum.items) {
      expect(options.some(option => option.attributes('label') === status.label
        && option.attributes('value') === String(status.value))).toBe(true)
    }
    expect(options.some(option => option.attributes('label') === '已上报')).toBe(false)
    expect(AlertStatusEnum.items.map(item => item.label)).toEqual(['未处理', '已转采购单', '已解除'])
    wrapper.unmount()
  })
  it('采购单号优先显示结构化字段，旧行回退到备注', async () => {
    const wrapper = renderPage(); await flushPromises()
    const column = wrapper.find('[data-column="关联采购订单"]')
    expect(column.text()).toContain('PO-FIELD')
    expect(column.text()).toContain('PO-LEGACY')
    expect(column.text()).not.toContain('PO-OLD')
    wrapper.unmount()
  })
})
