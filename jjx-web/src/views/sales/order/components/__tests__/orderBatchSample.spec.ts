vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/overlay/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))
vi.mock('element-plus/es/components/popper/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/row/style/css', () => ({}))
vi.mock('element-plus/es/components/col/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions-item/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/date-picker/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/link/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h, provide, inject } from 'vue'
import Dialog from '../OrderBatchSampleDialog.vue'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SampleOrderStatusEnum } from '@/enums/sales/SampleEnum'
import { SalesOrderStatusEnum } from '@/enums/sales/OrderEnum'

vi.mock('@/api/sales/sampleOrder', () => ({
  sampleOrderApi: { getOrderSampleOptions: vi.fn(), createBatchFromOrder: vi.fn() },
}))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: { success: vi.fn(), warning: vi.fn() },
  ElAlert: { props: ['title'], template: '<p class="alert">{{ title }}</p>' },
}))
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
function options(count = 3) {
  return {
    orderId: 100,
    orderNo: 'SO100',
    customerId: 7,
    customerName: 'Customer',
    contactPerson: 'Contact',
    sourceVersion: 'v1',
    items: Array.from({ length: count }, (_, n) => ({
      itemId: n + 1,
      productId: n + 11,
      productCode: `P${n + 1}`,
      productName: `Product${n + 1}`,
      orderQuantity: 100,
      activeSamples: [] as any[],
    })),
  }
}
function render() {
  return mount(Dialog, {
    props: {
      modelValue: true,
      sourceOrderId: 100,
      orderStatus: SalesOrderStatusEnum.DRAFT.value,
    },
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
        ElAlert: { props: ['title'], template: '<p>{{ title }}</p>' },
        ElEmpty: true,
      },
    },
  })
}
const submit = (w: ReturnType<typeof render>) =>
  w.findAll('button').find((b) => b.text().startsWith('发起打样'))!

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(sampleOrderApi.getOrderSampleOptions).mockResolvedValue({ data: options() } as any)
  vi.mocked(sampleOrderApi.createBatchFromOrder).mockResolvedValue({
    data: [{ orderId: 200, orderNo: 'SP200', sampleQty: 3 }],
  } as any)
})

describe('sales order batch sampling', () => {
  it('selects two of three, submits quantities once and displays results', async () => {
    const w = render()
    await flushPromises()
    expect(
      w.findAll('input[type=checkbox]').every((i) => !(i.element as HTMLInputElement).checked)
    ).toBe(true)
    await w.get('[aria-label="选择P1"]').setValue(true)
    await w.get('[aria-label="选择P2"]').setValue(true)
    await w.get('[aria-label="P1打样数量"]').setValue(3)
    await w.get('[aria-label="P2打样数量"]').setValue(5)
    await submit(w).trigger('click')
    await flushPromises()
    expect(sampleOrderApi.createBatchFromOrder).toHaveBeenCalledOnce()
    expect(sampleOrderApi.createBatchFromOrder).toHaveBeenCalledWith(
      100,
      expect.objectContaining({
        sourceVersion: 'v1',
        items: [
          { itemId: 1, quantity: 3, remark: '' },
          { itemId: 2, quantity: 5, remark: '' },
        ],
      })
    )
    expect(w.text()).toContain('SP200')
    expect(w.emitted('created')).toHaveLength(1)
    w.unmount()
  })
  it.each(['', '0', '-1', '1.5', '2147483648'])(
    'rejects invalid quantity %s without a request',
    async (value) => {
      const w = render()
      await flushPromises()
      await w.get('[aria-label="选择P1"]').setValue(true)
      await w.get('[aria-label="P1打样数量"]').setValue(value)
      await submit(w).trigger('click')
      expect(sampleOrderApi.createBatchFromOrder).not.toHaveBeenCalled()
      expect(w.text()).toContain('请输入正整数')
      w.unmount()
    }
  )
  it('automatically selects the only product and cancel does not create', async () => {
    vi.mocked(sampleOrderApi.getOrderSampleOptions).mockResolvedValue({ data: options(1) } as any)
    const w = render()
    await flushPromises()
    expect((w.get('[type=checkbox]').element as HTMLInputElement).checked).toBe(true)
    await w
      .findAll('button')
      .find((b) => b.text() === '取消')!
      .trigger('click')
    expect(sampleOrderApi.createBatchFromOrder).not.toHaveBeenCalled()
    expect(w.emitted('update:modelValue')).toEqual([[false]])
    w.unmount()
  })
  it('shows existing active sample and prevents selecting that row', async () => {
    const data = options()
    data.items[0].activeSamples = [
      { orderId: 80, orderNo: 'SP80', sampleStatus: SampleOrderStatusEnum.CREATED.value },
    ]
    vi.mocked(sampleOrderApi.getOrderSampleOptions).mockResolvedValue({ data } as any)
    const w = render()
    await flushPromises()
    expect(w.get('[aria-label="选择P1"]').attributes('disabled')).toBeDefined()
    expect(w.text()).toContain('SP80')
    expect(w.text()).toContain(SampleOrderStatusEnum.getLabel(SampleOrderStatusEnum.CREATED.value))
    w.unmount()
  })
  it('preserves quantities on failure and prevents double submission while pending', async () => {
    let reject!: (e: Error) => void
    vi.mocked(sampleOrderApi.createBatchFromOrder).mockImplementation(
      () =>
        new Promise((_, r) => {
          reject = r
        }) as any
    )
    const w = render()
    await flushPromises()
    await w.get('[aria-label="选择P1"]').setValue(true)
    await w.get('[aria-label="P1打样数量"]').setValue(3)
    const b = submit(w)
    await b.trigger('click')
    await b.trigger('click')
    expect(sampleOrderApi.createBatchFromOrder).toHaveBeenCalledOnce()
    reject(new Error('P2 failed, batch rolled back'))
    await flushPromises()
    expect(w.text()).toContain('batch rolled back')
    expect((w.get('[aria-label="P1打样数量"]').element as HTMLInputElement).value).toBe('3')
    w.unmount()
  })
})

describe('sales order sample progress summary', () => {
  it('summarizes unsampled products and keeps all available', async () => {
    const w = render()
    await flushPromises()
    expect(w.text()).toContain('本单 3 个产品，0 个已有进行中样品单，可为剩余 3 个发起打样')
    expect(
      w.findAll('input[type=checkbox]').every((b) => b.attributes('disabled') === undefined)
    ).toBe(true)
    w.unmount()
  })
  it('keeps active A disabled and allows submitting B', async () => {
    const data = options(2)
    data.items[0].activeSamples = [
      { orderId: 80, orderNo: 'SP80', sampleStatus: SampleOrderStatusEnum.CREATED.value },
    ]
    vi.mocked(sampleOrderApi.getOrderSampleOptions).mockResolvedValue({ data } as any)
    const w = render()
    await flushPromises()
    expect(w.text()).toContain('本单 2 个产品，1 个已有进行中样品单，可为剩余 1 个发起打样')
    expect((w.get('[aria-label="选择P1"]').element as HTMLInputElement).checked).toBe(false)
    await w.get('[aria-label="选择P2"]').setValue(true)
    await submit(w).trigger('click')
    await flushPromises()
    expect(sampleOrderApi.createBatchFromOrder).toHaveBeenCalledWith(
      100,
      expect.objectContaining({ items: [{ itemId: 2, quantity: 1, remark: '' }] })
    )
    expect(w.emitted('created')).toHaveLength(1)
    w.unmount()
  })
  it('all active shows existing links without submit, closing a sample restores availability on refresh', async () => {
    const data = options(1)
    data.items[0].activeSamples = [
      { orderId: 80, orderNo: 'SP80', sampleStatus: SampleOrderStatusEnum.CREATED.value },
    ]
    vi.mocked(sampleOrderApi.getOrderSampleOptions).mockResolvedValue({ data } as any)
    const w = render()
    await flushPromises()
    expect(w.text()).toContain('全部产品已有进行中样品单')
    expect(w.text()).toContain('SP80')
    expect(w.findAll('button').some((b) => b.text().startsWith('发起打样'))).toBe(false)
    expect(w.findAll('input[type=checkbox]')).toHaveLength(0)
    await w
      .findAll('button')
      .find((b) => b.text() === 'SP80')!
      .trigger('click')
    expect(sampleOrderApi.createBatchFromOrder).not.toHaveBeenCalled()
    vi.mocked(sampleOrderApi.getOrderSampleOptions).mockResolvedValue({ data: options(1) } as any)
    await w
      .findAll('button')
      .find((b) => b.text() === '刷新资料')!
      .trigger('click')
    await flushPromises()
    expect(w.text()).toContain('可为剩余 1 个发起打样')
    expect(w.get('[aria-label="选择P1"]').attributes('disabled')).toBeUndefined()
    expect(submit(w)).toBeDefined()
    w.unmount()
  })
})
