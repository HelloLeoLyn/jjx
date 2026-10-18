import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AddPage from '../../add.vue'
const api = vi.hoisted(() => ({ getOrderDraft: vi.fn(), convert: vi.fn(), push: vi.fn() }))
vi.mock('@/api/sales/quotation', () => ({ quotationApi: api }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: { quotationId: '31' } }), useRouter: () => ({ push: api.push }) }))
vi.mock('../OrderForm.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return { default: defineComponent({ name: 'OrderFormStub', props: ['initialData', 'sourceQuotationId', 'quotationSourceVersion'], setup(_, { expose }) { expose({ submitForm: vi.fn().mockResolvedValue(false) }); return () => h('div') } }) }
})
beforeEach(() => {
  vi.clearAllMocks()
  api.getOrderDraft.mockResolvedValue({ data: { sourceVersion: 'v31', order: { customerId: 7, customerName: 'Customer', contactPerson: 'Contact', deliveryAddress: 'Address', salesManagerId: 3, salesManagerName: 'Sales', items: [{ productId: 9, productCode: 'P9', productName: 'Product', quantity: 3, unitPrice: 10 }] } } })
})
describe('quotation conversion add page', () => {
  it('only reads preview and maps source data; cancel creates no order', async () => {
    const w = mount(AddPage); await flushPromises()
    expect(api.getOrderDraft).toHaveBeenCalledWith(31)
    const form = w.findComponent({ name: 'OrderFormStub' })
    expect(form.props('sourceQuotationId')).toBe(31); expect(form.props('quotationSourceVersion')).toBe('v31')
    expect(form.props('initialData')).toMatchObject({ customerId: 7, shippingAddress: 'Address', salesPersonId: 3 })
    await w.findAll('button').find(b => b.text().replace(/\s/g, '') === '取消')!.trigger('click')
    expect(api.push).toHaveBeenCalledWith('/sales/order'); expect(api.convert).not.toHaveBeenCalled(); w.unmount()
  })
  it('failed preview keeps save disabled and does not mount an empty source form', async () => {
    api.getOrderDraft.mockRejectedValue(new Error('quotation already converted'))
    const w = mount(AddPage); await flushPromises()
    expect(w.text()).toContain('quotation already converted')
    expect(w.findComponent({ name: 'OrderFormStub' }).exists()).toBe(false)
    expect(w.findAll('button').find(b => b.text().replace(/\s/g, '') === '提交')!.attributes('disabled')).toBeDefined()
    expect(api.convert).not.toHaveBeenCalled(); w.unmount()
  })
})
