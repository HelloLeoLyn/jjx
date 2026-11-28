import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import RouteFormDialog from '../RouteFormDialog.vue'
import { productRouteApi } from '@/api/product/routing'
import PrintResourceSelector from '@/components/Selector/PrintResourceSelector.vue'
import PrintPlateDetails from '@/components/printing/PrintPlateDetails.vue'
import RoutePrintStyleFields from '@/components/printing/RoutePrintStyleFields.vue'
import { printingSearchApi } from '@/api/engineering/printingSearch'

vi.mock('@/api/engineering/printingSearch', () => ({ printingSearchApi: { search: vi.fn(), plate: vi.fn(async id => ({ data: { plateId: id, plateNo: `P${id}`, frameNo: 'F1', mesh: 200, tensionNCm: 22, availability: true } })) } }))

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
  vi.mocked(printingSearchApi.plate).mockImplementation(async id => ({ data: { plateId: id, plateNo: `P${id}`, frameNo: 'F1', mesh: 200, tensionNCm: 22, availability: true } }) as any)
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
async function choosePlate(wrapper: ReturnType<typeof mount>, overrides: Record<string, unknown> = {}) {
  const selector = wrapper.findAllComponents(PrintResourceSelector).find(c => c.props('scene') === 'PLATE')!
  expect(selector).toBeTruthy()
  selector.vm.$emit('select', { versionId: 3, artworkNo: 'A1', versionNo: 'V2', plateId: 4, plateNo: 'P4', frameNo: 'F1', mesh: 200, availability: true, ...overrides })
  await nextTick()
  await flushPromises()
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
  it('样式平铺单选带名称和色号，保存重开且不提交预览字段', async () => {
    const sourcesJson = JSON.stringify([{ filmId: 21, filmNo: 'FL-A', artworkNo: 'F001', versionNo: 'V2', versionId: 3,
      styles: [{ styleNo: 'A', name: '正面文字', inkColor: 'RED' }, { styleNo: 'B', name: '图标', inkColor: 'BLUE' }] }])
    vi.mocked(printingSearchApi.plate).mockImplementation(async id => ({ data: { plateId: id, sourcesJson, availability: true } }) as any)
    const wrapper = await open(); await addPrint(wrapper, '旧印刷名称'); await choosePlate(wrapper, { sourcesJson })
    const fields = wrapper.findComponent(RoutePrintStyleFields)
    expect(fields.text()).toContain('正面文字'); expect(fields.text()).toContain('图标')
    expect(fields.findAll('input[type="checkbox"]')).toHaveLength(0)
    expect(fields.find('input[placeholder="样式色号"]').exists()).toBe(false)
    await save(wrapper); expect(productRouteApi.addProductRoute).not.toHaveBeenCalled()
    await fields.findAll('input[type="radio"]')[0].setValue(true); await flushPromises()
    await fields.find('input[placeholder="样式色号"]').setValue('LOCAL'); await flushPromises()
    await save(wrapper)
    expect(stored.items[0].processName).toBe('正面文字')
    expect(JSON.parse(stored.items[0].printRequirements).styles).toEqual([{ versionId: 3, styleNo: 'A', inkColor: 'LOCAL' }])
    expect(stored.items[0]).not.toHaveProperty('_printSourcesJson')
    expect(JSON.parse(stored.items[0].printRequirements).styles[0]).not.toHaveProperty('inkNo')
    await wrapper.setProps({ modelValue: false }); await wrapper.setProps({ routingId: 77, modelValue: true }); await flushPromises()
    expect(wrapper.findComponent(RoutePrintStyleFields).text()).toContain('正面文字')
    expect(wrapper.findComponent(RoutePrintStyleFields).find('input[placeholder="样式色号"]').element).toHaveProperty('value', 'LOCAL')
    await save(wrapper); expect(stored.items[0].processName).toBe('正面文字')
  })
  it('单个候选直接带出，历史多项保存不静默裁减，显式选择后才改单项', async () => {
    const sourcesJson = JSON.stringify([{ filmId: 21, filmNo: 'FL-A', versionId: 3,
      styles: [{ styleNo: 'A', name: '正面文字', inkColor: 'RED' }] }])
    vi.mocked(printingSearchApi.plate).mockImplementation(async id => ({ data: { plateId: id, sourcesJson, availability: true } }) as any)
    const wrapper = await open(); await addPrint(wrapper, '旧名'); await choosePlate(wrapper, { sourcesJson }); await save(wrapper)
    expect(stored.items[0].processName).toBe('正面文字')
    expect(JSON.parse(stored.items[0].printRequirements).styles).toHaveLength(1)
    const multipleSources = JSON.stringify([{ filmId: 21, filmNo: 'FL-A', versionId: 3, styles: [
      { styleNo: 'A', name: '正面文字', inkColor: 'RED' }, { styleNo: 'B', name: '图标', inkColor: 'BLUE' },
    ] }])
    const historical = { styles: [{ versionId: 3, styleNo: 'A', inkColor: 'SPECIAL' }, { versionId: 3, styleNo: 'B', inkColor: 'BLUE' }] }
    stored.items[0].printRequirements = JSON.stringify(historical)
    stored.items[0].processName = '历史多样式工序'
    vi.mocked(printingSearchApi.plate).mockImplementation(async id => ({ data: { plateId: id, sourcesJson: multipleSources, availability: true } }) as any)
    await wrapper.setProps({ modelValue: false }); await wrapper.setProps({ routingId: 77, modelValue: true }); await flushPromises()
    await save(wrapper)
    expect(JSON.parse(stored.items[0].printRequirements)).toEqual(historical)
    expect(stored.items[0].processName).toBe('历史多样式工序')
    await wrapper.findComponent(RoutePrintStyleFields).findAll('input[type="radio"]')[1].setValue(true); await flushPromises(); await save(wrapper)
    expect(stored.items[0].processName).toBe('图标')
    expect(JSON.parse(stored.items[0].printRequirements).styles).toEqual([{ versionId: 3, styleNo: 'B', inkColor: 'BLUE' }])
  })
  it('跨菲林单选仅保存一项，覆盖色号通过新增编辑保存重开，同身份换版保留覆盖', async () => {
    const wrapper = await open()
    await addPrint(wrapper, '多菲林印刷')
    const sourcesJson = JSON.stringify([{ filmId: 21, filmNo: 'FL-A', versionId: 3, styles: [{ styleNo: 'A', name: '正面', inkColor: 'RED' }] }, { filmId: 22, filmNo: 'FL-B', versionId: 5, styles: [{ styleNo: 'B', name: '背面', inkColor: 'BLACK' }] }])
    await choosePlate(wrapper, { sourcesJson })
    const fields = wrapper.findComponent(RoutePrintStyleFields)
    await fields.findAll('input[type="radio"]')[1].setValue(true); await flushPromises()
    await save(wrapper)
    expect(JSON.parse(stored.items[0].printRequirements).styles).toEqual([{ versionId: 5, styleNo: 'B', inkColor: 'BLACK' }])
    expect(stored.items[0].processName).toBe('背面')
    const override = { meshMin: 100, styles: [{ versionId: 5, styleNo: 'B', inkColor: 'SPECIAL' }] }
    wrapper.findComponent(PrintPlateDetails).vm.$emit('update:requirements', JSON.stringify(override))
    await nextTick(); await save(wrapper)
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ routingId: 77, modelValue: true })
    await flushPromises(); await save(wrapper)
    expect(JSON.parse(stored.items[0].printRequirements)).toEqual(override)
    await choosePlate(wrapper, { plateId: 8, sourcesJson })
    await save(wrapper)
    const requirements = JSON.parse(stored.items[0].printRequirements)
    expect(requirements.meshMin).toBe(100)
    expect(requirements.styles.find((style: any) => style.styleNo === 'B').inkColor).toBe('SPECIAL')
    expect(productRouteApi.editProductRoute).toHaveBeenCalled()
  })
  it('选择建议网版后保存并重开保留实体ID，工艺要求不从版面参数覆盖', async () => {
    const wrapper = await open()
    await addPrint(wrapper, '面板丝印')
    await choosePlate(wrapper)
    await wrapper.findComponent(PrintPlateDetails).find('button').trigger('click')
    await flushPromises()
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
  it('更换网版更新菲林版本，固定要求保留；清空选择移除关联身份但保留要求', async () => {
    const wrapper = await open()
    await addPrint(wrapper, '换版印刷')
    await choosePlate(wrapper)
    await wrapper.findComponent(PrintPlateDetails).find('button').trigger('click')
    await flushPromises()
    await wrapper.find('input[placeholder="目数下限"]').setValue('100')
    await choosePlate(wrapper, { plateId: 8, versionId: 9, plateNo: 'P8' })
    await save(wrapper)
    expect(stored.items[0]).toMatchObject({ artworkVersionId: 9, defaultPlateId: 8 })
    expect(JSON.parse(stored.items[0].printRequirements)).toEqual({ meshMin: 100 })
    wrapper.findComponent(PrintResourceSelector).vm.$emit('clear')
    await nextTick()
    await save(wrapper)
    expect(stored.items[0].defaultPlateId).toBeUndefined()
    expect(stored.items[0].artworkVersionId).toBeUndefined()
    expect(JSON.parse(stored.items[0].printRequirements)).toEqual({ meshMin: 100 })
  })
  it('不可用网版及非网版候选不改变已选身份', async () => {
    const wrapper = await open()
    await addPrint(wrapper, '面板丝印')
    await choosePlate(wrapper)
    await choosePlate(wrapper, { plateId: 88, versionId: 99, availability: false })
    await choosePlate(wrapper, { plateId: null, versionId: 99 })
    await save(wrapper)
    expect(stored.items[0]).toMatchObject({ artworkVersionId: 3, defaultPlateId: 4 })
  })
  it('真实编辑器输入后新增请求带名称，保存回读和关闭重开保持名称，再次编辑使用相同字段', async () => {
    const wrapper = await open()
    await addPrint(wrapper, '面板丝印')
    await choosePlate(wrapper)
    await wrapper.findComponent(PrintPlateDetails).find('button').trigger('click')
    await flushPromises()
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
