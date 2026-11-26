import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import RouteFormDialog from '../RouteFormDialog.vue'
import { productRouteApi } from '@/api/product/routing'
import PrintResourceSelector from '@/components/Selector/PrintResourceSelector.vue'

vi.mock('@/api/product/routing', () => ({
  productRouteApi: {
    getEnabledProcesses: vi.fn(),
    getProductRouteInfo: vi.fn(),
    addProductRoute: vi.fn(),
    editProductRoute: vi.fn(),
  },
}))
vi.mock('@/components/Selector/ProductSelector.vue', () => ({
  default: { name: 'ProductSelector', template: '<div />' },
}))
vi.mock('../RouteRemarkAutocomplete.vue', () => ({
  default: { name: 'RouteRemarkAutocomplete', template: '<div />' },
}))
vi.mock('@/components/print/PrintFieldAutocomplete.vue', () => ({
  default: { name: 'PrintFieldAutocomplete', template: '<div />' },
}))
vi.mock('@/components/product/EngineeringRoutingItem.vue', () => ({
  default: { name: 'EngineeringRoutingItem', template: '<div />' },
}))
vi.mock('@/components/ProcessOperation/index.vue', () => ({
  default: { name: 'ProcessOperation', template: '<div />' },
}))
vi.mock('sortablejs', () => ({ default: { create: vi.fn(() => ({ destroy() {} })) } }))
const Slot = defineComponent({
  template: '<div><slot name="header" /><slot /><slot name="footer" /></div>',
})
const Form = defineComponent({
  setup(_, { expose }) {
    expose({ validate: async () => true, resetFields() {} })
    return {}
  },
  template: '<div><slot /></div>',
})
const clone = (value: any) => JSON.parse(JSON.stringify(value))
let stored: any
const wrappers: ReturnType<typeof mount>[] = []
beforeEach(() => {
  vi.clearAllMocks()
  stored = null
  vi.mocked(productRouteApi.getEnabledProcesses).mockResolvedValue({ data: [] } as any)
  vi.mocked(productRouteApi.addProductRoute).mockImplementation(async (request) => {
    stored = { ...clone(request), routingId: 77, approveStatus: 0 }
    return { data: clone(stored) } as any
  })
  vi.mocked(productRouteApi.editProductRoute).mockImplementation(async (id, request) => {
    stored = { ...clone(request), routingId: id, approveStatus: 0 }
    return { data: clone(stored) } as any
  })
  vi.mocked(productRouteApi.getProductRouteInfo).mockImplementation(
    async () => ({ data: clone(stored) }) as any
  )
})
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))
async function open() {
  const wrapper = mount(RouteFormDialog, {
    props: { modelValue: false },
    global: { directives: { loading: {} }, stubs: { ElDialog: Slot, ElForm: Form, SvgIcon: true } },
  })
  wrappers.push(wrapper)
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
  ;(wrapper.vm as any).handleProductChange(null, {
    productId: 1,
    productCode: 'P1',
    productName: '面板',
  })
  await nextTick()
  const printTab = wrapper.findAll('.el-tabs__item').find((tab) => tab.text().includes('印刷'))
  expect(printTab).toBeTruthy()
  await printTab!.trigger('click')
  await flushPromises()
  return wrapper
}
async function addPrint(wrapper: ReturnType<typeof mount>, name: string) {
  const add = wrapper.findAll('button').find((button) => button.text().includes('添加印刷工序'))!
  await add.trigger('click')
  await nextTick()
  await flushPromises()
  const names = wrapper.findAll('input[placeholder="如：丝印/移印/网印"]')
  expect(names.length).toBeGreaterThan(0)
  await names.at(-1)!.setValue(name)
}
async function save(wrapper: ReturnType<typeof mount>, close = false) {
  const button = wrapper
    .findAll('button')
    .find((button) =>
      close ? button.text() === '保存并关闭' : button.text().replace(/\s/g, '') === '保存'
    )!
  expect(button).toBeTruthy()
  await button.trigger('click')
  await flushPromises()
  await nextTick()
}
const printInput = (wrapper: ReturnType<typeof mount>) =>
  wrapper.find('input[placeholder="如：丝印/移印/网印"]')
describe('新增工艺路线印刷名称保存', () => {
  it('选择建议网版后保存并重开保留实体ID，工艺要求不从版面参数覆盖', async () => {
    const wrapper = await open()
    await addPrint(wrapper, '面板丝印')
    const artwork = wrapper.findAllComponents(PrintResourceSelector).find(c => c.props('placeholder') === '按图案/菲林号搜索')!
    artwork.vm.$emit('select', { versionId: 3, artworkNo: 'A1', versionNo: 'V2' })
    await nextTick()
    const plate = wrapper.findAllComponents(PrintResourceSelector).find(c => c.props('placeholder') === '选择同版本实体网版')!
    plate.vm.$emit('select', { versionId: 3, plateId: 4, plateNo: 'P4', frameNo: 'F1', mesh: 200 })
    await nextTick()
    await wrapper.find('input[placeholder="目数下限"]').setValue('100')
    await save(wrapper)
    expect(stored.items[0]).toMatchObject({ artworkVersionId: 3, defaultPlateId: 4 })
    expect(JSON.parse(stored.items[0].printRequirements)).toEqual({ meshMin: 100 })
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ routingId: 77, modelValue: true })
    await flushPromises()
    await save(wrapper)
    expect(stored.items[0]).toMatchObject({ artworkVersionId: 3, defaultPlateId: 4 })
    expect(JSON.parse(stored.items[0].printRequirements)).toEqual({ meshMin: 100 })
  })
  it('真实编辑器输入后新增请求带名称，保存回读和关闭重开保持名称，再次编辑使用相同字段', async () => {
    const wrapper = await open()
    await addPrint(wrapper, '面板丝印')
    await wrapper.find('input[placeholder="印刷位置"]').setValue('面板')
    await wrapper.find('input[placeholder="目数下限"]').setValue('100')
    await wrapper.find('input[placeholder="张力上限 N/cm"]').setValue('25')
    await save(wrapper)
    expect(productRouteApi.addProductRoute).toHaveBeenCalledTimes(1)
    const created = vi.mocked(productRouteApi.addProductRoute).mock.calls[0][0].items[0]
    expect(created).toMatchObject({
      processName: '面板丝印',
      majorCategory: 'PRINT',
      processCategory: 'PANEL',
    })
    expect((printInput(wrapper).element as HTMLInputElement).value).toBe('面板丝印')
    expect(JSON.parse(created.printRequirements!)).toEqual({ position: '面板', meshMin: 100, tensionMax: 25 })
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ routingId: 77, modelValue: true })
    await flushPromises()
    expect((printInput(wrapper).element as HTMLInputElement).value).toBe('面板丝印')
    expect((wrapper.find('input[placeholder="目数下限"]').element as HTMLInputElement).value).toBe('100')
    await printInput(wrapper).setValue('面板移印')
    await save(wrapper)
    expect(productRouteApi.addProductRoute).toHaveBeenCalledTimes(1)
    expect(productRouteApi.editProductRoute).toHaveBeenCalledWith(
      77,
      expect.objectContaining({
        items: [expect.objectContaining({ processName: '面板移印', majorCategory: 'PRINT' })],
      })
    )
    expect((printInput(wrapper).element as HTMLInputElement).value).toBe('面板移印')
  })
  it.each(['', '   '])('空名称%j在新增时不发请求，保留未保存的输入行', async (name) => {
    const wrapper = await open()
    await addPrint(wrapper, name)
    await save(wrapper)
    expect(productRouteApi.addProductRoute).not.toHaveBeenCalled()
    expect(productRouteApi.editProductRoute).not.toHaveBeenCalled()
    expect(printInput(wrapper).exists()).toBe(true)
  })
  it('再次编辑清空名称后阻止保存，不覆盖已保存名称', async () => {
    const wrapper = await open()
    await addPrint(wrapper, '保留名称')
    await save(wrapper)
    await printInput(wrapper).setValue(' ')
    await save(wrapper)
    expect(productRouteApi.editProductRoute).not.toHaveBeenCalled()
    expect(stored.items[0].processName).toBe('保留名称')
  })
  it('保存并关闭同样携带印刷名称', async () => {
    const wrapper = await open()
    await addPrint(wrapper, '下线印刷')
    await save(wrapper, true)
    expect(stored.items[0].processName).toBe('下线印刷')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
  })
})
