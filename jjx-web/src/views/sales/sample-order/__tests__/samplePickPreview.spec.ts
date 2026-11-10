import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, h, inject, provide } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import SampleOrders from '../index.vue'
import { SampleOrderStatusEnum } from '@/enums/sales'
import SamplePickDialog from '../components/SamplePickDialog.vue'

const api = vi.hoisted(() => ({ preview: vi.fn(), create: vi.fn(), error: vi.fn(), permissions: [] as string[] }))
vi.mock('@/api/inventory/outbound', () => ({
  outboundApi: { samplePickPreview: api.preview, createSamplePick: api.create },
}))
vi.mock('@/api/sales/sampleOrder', () => ({
  sampleOrderApi: { page: vi.fn().mockResolvedValue({ data: { records: [], total: 0 } }) },
}))
vi.mock('vue-router', () => ({ useRouter: () => ({}), useRoute: () => ({ query: {} }) }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ roles: [], getPermissions: api.permissions }) }))
vi.mock('@/utils/request', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
vi.mock('element-plus', async (original) => ({
  ...await original<typeof import('element-plus')>(),
  ElMessage: { error: api.error, warning: vi.fn(), success: vi.fn() },
}))
vi.mock('@/components/attachment/AttachmentCenter.vue', () => ({ default: {} }))
vi.mock('@/components/TraceTimeline/index.vue', () => ({ default: {} }))
vi.mock('../components/SampleConvertCheckDialog.vue', () => ({ default: {} }))
vi.mock('@/views/sales/order/components/SalesOrderFormDialog.vue', () => ({ default: {} }))
vi.mock('@/components/OperationPreviewDialog/index.vue', () => ({ default: {} }))
vi.mock('@/components/OperationPreviewDialog/registry', () => ({ getOperation: vi.fn() }))
vi.mock('@/views/sales/quotation/components/QuotationDetailDialog.vue', () => ({ default: {} }))
vi.mock('@/views/sales/customer/components/CustomerDetailDialog.vue', () => ({ default: {} }))
vi.mock('../components/SampleReviewPreview.vue', () => ({ default: {} }))
vi.mock('../components/RequisitionSignDialog.vue', () => ({ default: {} }))
vi.mock('../components/SampleForm.vue', () => ({ default: {} }))
vi.mock('../components/SampleDetail.vue', () => ({ default: {} }))

const Table = defineComponent({
  props: ['data'],
  setup(props, { slots }) {
    provide('rows', computed(() => props.data || []))
    return () => h('div', slots.default?.())
  },
})
const Column = defineComponent({
  props: ['label', 'prop'],
  setup(props, { slots }) {
    const rows = inject<any>('rows')
    return () => h('div', { 'data-column': props.label }, [
      h('span', props.label),
      ...rows.value.map((row: any) => h('div', slots.default?.({ row }) || String(row[props.prop] ?? ''))),
    ])
  },
})
const Dialog = defineComponent({
  props: ['modelValue'],
  setup(props, { slots }) {
    return () => props.modelValue ? h('section', { 'data-pick-dialog': '' }, [slots.default?.(), slots.footer?.()]) : null
  },
})

let wrapper: ReturnType<typeof shallowMount>
const row = (extra = {}) => ({
  materialId: 11, materialCode: 'M11', materialName: '材料',
  specification: '10×20', unit: 'PCS', quantity: 2, issuedQuantity: 1, availableQuantity: 0,
  baseQty: 3, moduleQty: 4, unitQuantity: 0.75,
  ...extra,
})
async function open(rows = [row()]) {
  api.preview.mockResolvedValue({ data: rows })
  wrapper = shallowMount(SampleOrders, {
    global: {
      renderStubDefaultSlot: true,
      directives: { loading: () => {}, hasPermi: () => {} },
      stubs: { TableActionColumn: false, SamplePickDialog: false, ElTable: Table, ElTableColumn: Column, ElDialog: Dialog },
    },
  })
  await flushPromises()
  await (wrapper.vm as any).openSamplePick({ orderId: 7, orderNo: 'SP7', sampleQty: 2, unit: 'PCS' })
  await flushPromises()
  return wrapper.findComponent(SamplePickDialog).vm as any
}

describe('打样领料预览与提交', () => {
  beforeEach(() => { vi.clearAllMocks(); api.permissions = [] })
  afterEach(() => {
    wrapper?.unmount()
    vi.restoreAllMocks()
  })

  it('显示打样数量、规格、单位、模数、基数、单位用量和后端需求，默认扣除已领', async () => {
    await open()
    const dialog = wrapper.get('[data-pick-dialog]')
    expect(dialog.text()).toContain('打样数量：2 PCS')
    expect(dialog.text()).toContain('单位用量 = 基数 ÷ 模数')
    expect(dialog.text()).toContain('需求 = 单位用量 × 打样数量（向上取整）')
    expect(dialog.get('[data-column="规格"]').text()).toContain('10×20')
    expect(dialog.get('[data-column="单位"]').text()).toContain('PCS')
    expect(dialog.get('[data-column="模数"]').text()).toContain('4')
    expect(dialog.get('[data-column="基数"]').text()).toContain('3')
    expect(dialog.get('[data-column="单位用量"]').text()).toContain('0.75')
    expect(dialog.get('[data-column="BOM需求"]').text()).toContain('2')
    const input = wrapper.findComponent({ name: 'ElInputNumber' })
    expect(input.props('modelValue')).toBe(1)
    expect(input.props('disabled')).toBe(false)
    expect(api.preview).toHaveBeenCalledWith(7)
  })

  it('直接展示后端已向上取整的需求，前端不再乘打样数量', async () => {
    await open([row({ baseQty: 1, moduleQty: 4, unitQuantity: 0.25, quantity: 1, issuedQuantity: 0 })])
    expect(wrapper.get('[data-column="BOM需求"]').text()).toContain('1')
    expect(wrapper.findComponent({ name: 'ElInputNumber' }).props('modelValue')).toBe(1)
  })

  it('缺少模数时展示后端回退的单位用量与需求', async () => {
    await open([row({ baseQty: 3, moduleQty: null, unitQuantity: 0.6, quantity: 2, issuedQuantity: 0 })])
    expect(wrapper.get('[data-column="单位用量"]').text()).toContain('0.6')
    expect(wrapper.get('[data-column="BOM需求"]').text()).toContain('2')
    expect(wrapper.findComponent({ name: 'ElInputNumber' }).props('modelValue')).toBe(2)
  })

  it('未匹配物料禁用输入、给出提示且不参与提交，生成后打开打印页', async () => {
    const vm = await open([row(), row({ materialId: null, materialCode: null })])
    const inputs = wrapper.findAllComponents({ name: 'ElInputNumber' })
    expect(inputs[1].props('disabled')).toBe(true)
    expect(wrapper.findComponent({ name: 'ElAlert' }).props('title')).toContain('有 1 条样品BOM物料未匹配')
    api.create.mockResolvedValue({ data: 123 })
    const print = vi.spyOn(window, 'open').mockReturnValue(null)
    await vm.submitSamplePick()
    expect(api.create).toHaveBeenCalledWith(7, [{
      materialId: 11, materialCode: 'M11', materialName: '材料', quantity: 1,
    }])
    expect(print).toHaveBeenCalledWith('/print/outbound/123', '_blank')
    expect(wrapper.findComponent(SamplePickDialog).emitted('success')).toHaveLength(1)
  })

  it('后端超领拒绝时展示提示、保留弹窗且不打开打印页', async () => {
    const vm = await open()
    api.create.mockRejectedValue(new Error('打样领料超出剩余需求'))
    const print = vi.spyOn(window, 'open').mockReturnValue(null)
    await vm.submitSamplePick()
    expect(api.error).toHaveBeenCalledWith('打样领料超出剩余需求')
    expect(wrapper.find('[data-pick-dialog]').exists()).toBe(true)
    expect(print).not.toHaveBeenCalled()
  })
})


describe('销售样品单领料备用入口权限', () => {
  it.each([true, false])('出库新增权限=%s 时入口显隐一致', async (allowed) => {
    api.permissions = allowed ? ['inventory:outbound:add'] : []
    await open()
    ;(wrapper.vm as any).sampleList = [{ orderId: 7, sampleStatus: SampleOrderStatusEnum.ENGINEERING.value }]
    await flushPromises()
    const actions = wrapper.find('.table-action-column')
    expect(actions.text().includes('生成领料单')).toBe(allowed)
  })
})
