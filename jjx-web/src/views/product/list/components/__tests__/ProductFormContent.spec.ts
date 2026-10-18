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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import ProductFormContent from '../ProductFormContent.vue'
import ProductCodeGenerator from '@/components/ProductCodeGenerator/index.vue'

vi.mock('element-plus/es/components/message/style/css', () => ({}))
const api = vi.hoisted(() => ({
  info: vi.fn(), edit: vi.fn(), generateSerialNo: vi.fn(),
  category: { getProductCategoryTree: vi.fn() },
}))
vi.mock('@/api/product', () => ({ productApi: api }))
vi.mock('element-plus', async (importOriginal) => ({
  ...await importOriginal<typeof import('element-plus')>(),
  ElMessage: { error: vi.fn(), success: vi.fn() },
}))

vi.mock('@/components/Selector/CustomerSelector.vue', () => ({ default: { template: '<div />' } }))

const Form = defineComponent({
  setup(_, { expose }) {
    expose({ validate: async () => true })
  },
  template: '<div><slot /></div>',
})
// Keep slots visible while replacing Element Plus controls with lightweight shells.
const Slot = { template: '<div><slot /></div>' }
let wrapper: ReturnType<typeof shallowMount>
beforeEach(() => {
  vi.clearAllMocks()
  api.info.mockResolvedValue({ data: {
    productId: 7, customerId: 5, productCode: 'ABC1000MEOO', productName: '产品', productType: '1',
  } })
  api.category.getProductCategoryTree.mockResolvedValue({ data: [] })
  api.edit.mockResolvedValue({})
})
afterEach(() => wrapper?.unmount())

async function open() {
  wrapper = shallowMount(ProductFormContent, { props: { productId: 7 }, global: {
    renderStubDefaultSlot: true,
    stubs: { ProductCodeGenerator: false, ElForm: Form, ElRow: Slot, ElCol: Slot, ElFormItem: Slot },
  } })
  await flushPromises()
  return wrapper.findComponent(ProductCodeGenerator)
}

describe('产品编辑编码', () => {
  it('恢复原前缀和四位序号，修改结构实时拼码且不取新号', async () => {
    const generator = await open()
    expect(generator.props('customerShort')).toBe('ABC')
    const state = generator.props('state')!
    expect(state.serialNo).toBe('1000')
    expect(wrapper.vm.formData.productCode).toBe('ABC1000MEOO')
    state.panelType = 'P'
    state.circuitFeature = 'C'
    await nextTick()
    expect(wrapper.vm.formData.productCode).toBe('ABC1000PEOC')
    await wrapper.vm.handleSubmit()
    expect(api.edit).toHaveBeenCalledWith(expect.objectContaining({ productCode: 'ABC1000PEOC' }))
    expect(api.generateSerialNo).not.toHaveBeenCalled()
  })

  it('结构缺失时禁止提交旧编码', async () => {
    const generator = await open()
    generator.props('state')!.panelFeature = ''
    await expect(wrapper.vm.handleSubmit()).rejects.toThrow('编码要素不完整')
    expect(api.edit).not.toHaveBeenCalled()
  })
})
