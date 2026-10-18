import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, inject, provide } from 'vue'
import Dialog, { sampleEntryBlockedReason } from '../OrderBatchSampleDialog.vue'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SalesOrderStatusEnum } from '@/enums/sales/OrderEnum'

vi.mock('@/api/sales/sampleOrder', () => ({
  sampleOrderApi: { getOrderSampleOptions: vi.fn(), createBatchFromOrder: vi.fn() },
}))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn() } }))
vi.mock('../../../sample-order/components/SampleDetail.vue', () => ({
  default: { template: '<div />' },
}))

const slot = defineComponent({
  setup(_, { slots }) {
    return () => h('div', [slots.default?.(), slots.footer?.()])
  },
})
const table = defineComponent({
  props: ['data'],
  setup(p, { slots }) {
    provide('rows', () => p.data)
    return () => h('div', slots.default?.())
  },
})
const column = defineComponent({
  setup(_, { slots }) {
    const rows = inject<() => any[]>('rows')!
    return () =>
      h(
        'div',
        rows().map((row) => slots.default?.({ row }))
      )
  },
})
const input = defineComponent({
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue'],
  setup(p, { emit, attrs }) {
    return () =>
      h('input', {
        ...attrs,
        value: p.modelValue,
        disabled: p.disabled,
        onInput: (e: any) => emit('update:modelValue', e.target.value),
      })
  },
})
const checkbox = defineComponent({
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue'],
  setup(p, { emit, attrs }) {
    return () =>
      h('input', {
        ...attrs,
        type: 'checkbox',
        checked: p.modelValue,
        disabled: p.disabled,
        onChange: (e: any) => emit('update:modelValue', e.target.checked),
      })
  },
})
const button = defineComponent({
  props: ['disabled', 'loading'],
  setup(p, { slots, attrs }) {
    return () => h('button', { ...attrs, disabled: p.disabled || p.loading }, slots.default?.())
  },
})

function options() {
  return {
    orderId: 100,
    orderNo: 'SO100',
    customerId: 7,
    customerName: 'Customer',
    contactPerson: 'Contact',
    sourceVersion: 'v1',
    items: [
      {
        itemId: 1,
        productId: 11,
        productCode: 'P1',
        productName: 'Product1',
        orderQuantity: 100,
        activeSamples: [] as any[],
      },
    ],
  }
}

function render(orderStatus: number | null | undefined) {
  return mount(Dialog, {
    props: { modelValue: true, sourceOrderId: 100, orderStatus },
    global: {
      directives: { loading: {} },
      stubs: {
        ElDialog: slot,
        ElDescriptions: slot,
        ElDescriptionsItem: slot,
        ElForm: slot,
        ElFormItem: slot,
        ElTable: table,
        ElTableColumn: column,
        ElInput: input,
        ElDatePicker: input,
        ElCheckbox: checkbox,
        ElButton: button,
        ElLink: button,
        ElAlert: { props: ['title'], template: '<p class="alert">{{ title }}</p>' },
        ElEmpty: { props: ['description'], template: '<p class="empty">{{ description }}</p>' },
      },
    },
  })
}

const submitButton = (w: ReturnType<typeof render>) =>
  w.findAll('button').find((b) => b.text().startsWith('发起打样'))

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(sampleOrderApi.getOrderSampleOptions).mockResolvedValue({ data: options() } as any)
})

describe('sales order sample status gate', () => {
  it.each([
    SalesOrderStatusEnum.DRAFT,
    SalesOrderStatusEnum.PENDING_REVIEW,
    SalesOrderStatusEnum.REVIEWING,
    SalesOrderStatusEnum.REVIEWED,
    SalesOrderStatusEnum.REJECTED,
    SalesOrderStatusEnum.CONFIRMED,
    SalesOrderStatusEnum.PRODUCING,
  ])('allows non-terminal status $label', (status) => {
    expect(sampleEntryBlockedReason(status.value)).toBe('')
  })

  it.each([
    SalesOrderStatusEnum.SHIPPED,
    SalesOrderStatusEnum.COMPLETED,
    SalesOrderStatusEnum.CANCELLED,
  ])('rejects terminal status $label with its name', (status) => {
    const reason = sampleEntryBlockedReason(status.value)
    expect(reason).toContain(SalesOrderStatusEnum.getLabel(status.value))
    expect(reason).toContain('不可发起打样')
  })

  it.each([null, undefined, 0, 99])('rejects unknown status %s instead of allowing', (status) => {
    const reason = sampleEntryBlockedReason(status as number | null | undefined)
    expect(reason).toContain('未知')
    expect(reason).toContain('刷新')
  })

  it.each([
    SalesOrderStatusEnum.SHIPPED.value,
    SalesOrderStatusEnum.COMPLETED.value,
    SalesOrderStatusEnum.CANCELLED.value,
  ])('terminal status %s never queries options nor submits', async (status) => {
    const w = render(status)
    await flushPromises()
    expect(sampleOrderApi.getOrderSampleOptions).not.toHaveBeenCalled()
    expect(w.get('.alert').text()).toContain('不可发起打样')
    expect(w.text()).not.toContain('未加载到订单打样资料')
    const submit = submitButton(w)
    expect(submit).toBeDefined()
    expect(submit!.attributes('disabled')).toBeDefined()
    await submit!.trigger('click')
    await flushPromises()
    expect(sampleOrderApi.createBatchFromOrder).not.toHaveBeenCalled()
    w.unmount()
  })

  it('unknown or empty status is blocked the same way', async () => {
    const w = render(undefined)
    await flushPromises()
    expect(sampleOrderApi.getOrderSampleOptions).not.toHaveBeenCalled()
    expect(w.get('.alert').text()).toContain('未知')
    w.unmount()
  })

  it('production status still loads the dialog and keeps the resample entry', async () => {
    const w = render(SalesOrderStatusEnum.PRODUCING.value)
    await flushPromises()
    expect(sampleOrderApi.getOrderSampleOptions).toHaveBeenCalledWith(100)
    expect(w.find('.alert').exists()).toBe(false)
    expect(submitButton(w)!.attributes('disabled')).toBeUndefined()
    w.unmount()
  })
})
