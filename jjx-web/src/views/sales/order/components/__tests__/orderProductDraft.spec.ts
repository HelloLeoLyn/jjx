// dev-20261019-005：本用例挂载真实 ProductFormContent（含 element-plus 自动导入组件），
// 与 ProductFormContent.spec 一致地屏蔽样式模块，避免 vitest css:false 下报 Unknown file extension ".css"。
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/cascader/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/row/style/css', () => ({}))
vi.mock('element-plus/es/components/col/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
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
    Object.assign(w.vm.formData, { minOrderQty: 7, leadTime: 10, remark: 'remark' })
    await w.vm.handleSubmit()
    expect(productApi.add).not.toHaveBeenCalled(); expect(productApi.edit).not.toHaveBeenCalled()
    const draft = w.emitted('draft-confirm')?.[0]?.[0] as any
    expect(draft).toMatchObject({ customerId: 7, productCode: 'AA001MEOO', productName: 'New', minOrderQty: 7, leadTime: 10, remark: 'remark' })
    // dev-20261019-005：产品类型已隐藏，订单草稿回填不带 productType
    expect(draft).not.toHaveProperty('productType')
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
    expect(productApi.add.mock.calls[0][0]).not.toHaveProperty('productType')
    expect(w.emitted('success')).toHaveLength(1); w.unmount()
  })
  it('product management edit still calls its edit API', async () => {
    const w = await render({ productId: 1 }); await w.vm.handleSubmit(); expect(productApi.edit).toHaveBeenCalledOnce()
    expect(productApi.add).not.toHaveBeenCalled(); w.unmount()
  })
})
