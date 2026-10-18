import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import Content from '@/views/product/list/components/ProductFormContent.vue'
import { productApi } from '@/api/product'
vi.mock('@/api/product', () => ({ productApi: { category: { getProductCategoryTree: vi.fn().mockResolvedValue({ data: [] }) }, add: vi.fn().mockResolvedValue({ data: 1 }), edit: vi.fn().mockResolvedValue({ data: true }), info: vi.fn().mockResolvedValue({ data: { productId: 1, productCode: 'AA001MEOO', productName: 'Existing', customerId: 7 } }) } }))
vi.mock('@/components/Selector/CustomerSelector.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/ProductCodeGenerator/index.vue', () => ({ default: { name: 'GeneratorStub', props: ['state'], template: '<div />' } }))
const formStub = defineComponent({ methods: { validate: async () => true, resetFields: () => {} }, template: '<div><slot /></div>' })
const code = { serialNo: '001', panelType: 'M', panelFeature: 'E', circuitType: 'O', circuitFeature: 'O' }
async function render(props: any) {
  const w = mount(Content, { props, global: { stubs: { ElForm: formStub } } }); await flushPromises()
  Object.assign(w.vm.formData, { productName: 'New', codeCustomerId: 7 })
  w.findComponent({ name: 'GeneratorStub' }).vm.$emit('update:state', code)
  await flushPromises(); return w
}
beforeEach(() => vi.clearAllMocks())
describe('shared product form persistence and order draft modes', () => {
  it('draft confirms fields without calling product create or edit API', async () => {
    const w = await render({ mode: 'draft', customerId: 7, customerShortName: 'AA' })
    Object.assign(w.vm.formData, { minOrderQty: 7, leadTime: 10, remark: 'remark', productType: 1 })
    await w.vm.handleSubmit()
    expect(productApi.add).not.toHaveBeenCalled(); expect(productApi.edit).not.toHaveBeenCalled()
    expect(w.emitted('draft-confirm')?.[0]?.[0]).toMatchObject({ customerId: 7, productCode: 'AA001MEOO', productName: 'New', minOrderQty: 7, leadTime: 10, remark: 'remark' })
    w.unmount()
  })
  it('missing code rejects without losing the entered name', async () => {
    const w = await render({ mode: 'draft', customerId: 7, customerShortName: 'AA' })
    w.findComponent({ name: 'GeneratorStub' }).vm.$emit('update:state', { ...code, panelType: '' }); await flushPromises()
    await expect(w.vm.handleSubmit()).rejects.toThrow(); expect(w.vm.formData.productName).toBe('New')
    expect(productApi.add).not.toHaveBeenCalled(); w.unmount()
  })
  it('product management create still calls its save API', async () => {
    const w = await render({}); await w.vm.handleSubmit(); expect(productApi.add).toHaveBeenCalledOnce()
    expect(w.emitted('success')).toHaveLength(1); w.unmount()
  })
  it('product management edit still calls its edit API', async () => {
    const w = await render({ productId: 1 }); await w.vm.handleSubmit(); expect(productApi.edit).toHaveBeenCalledOnce()
    expect(productApi.add).not.toHaveBeenCalled(); w.unmount()
  })
})
