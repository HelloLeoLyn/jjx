import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import PrintPlateDetails from '../PrintPlateDetails.vue'
import { printingSearchApi } from '@/api/engineering/printingSearch'
vi.mock('@/api/engineering/printingSearch', () => ({ printingSearchApi: { plate: vi.fn(), search: vi.fn() } }))
const Dialog = defineComponent({ props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' })
const wrappers: ReturnType<typeof mount>[] = []
const mountDetails = (row: any, readOnly = true) => {
  const wrapper = mount(PrintPlateDetails, { props: { row, readOnly }, global: { directives: { loading: {} }, stubs: { ElDialog: Dialog } } })
  wrappers.push(wrapper)
  return wrapper
}
const row = () => ({ defaultPlateId: 4, defaultPlateNo: 'P4', artworkVersionId: 3, artworkNo: 'A1', artworkVersionNo: 'V2', printRequirements: '{"meshMin":100}' })
const source = (id = 4) => ({ plateId: id, plateNo: `P${id}`, artworkNo: 'A1', versionNo: 'V2', filmNo: 'FILM-001', frameNo: 'F1', stretchId: 7, mesh: 305, tensionNCm: 22.5, plateStatus: 'RELEASED', availability: true })
beforeEach(() => { vi.clearAllMocks(); vi.mocked(printingSearchApi.plate).mockResolvedValue({ data: source() } as any) })
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))
describe('网版关联资料', () => {
  it('未选择时不查接口；打开详情后展示实体关联及工序要求，只读模式没有输入框', async () => {
    const empty = mountDetails({})
    expect(empty.text()).toBe('请先选择网版')
    expect(printingSearchApi.plate).not.toHaveBeenCalled()
    const wrapper = mountDetails(row())
    await wrapper.find('button').trigger('click'); await flushPromises()
    expect(printingSearchApi.plate).toHaveBeenCalledWith(4)
    for (const value of ['FILM-001', 'F1', '#7', '305 目', '22.5 N/cm', '目数 100']) expect(wrapper.text()).toContain(value)
    expect(wrapper.find('input').exists()).toBe(false)
  })
  it('资料失败可重试，重试成功后清除错误，不吞掉工序要求', async () => {
    vi.mocked(printingSearchApi.plate).mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountDetails(row())
    await wrapper.find('button').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('加载失败')
    await wrapper.findAll('button').find(b => b.text() === '重试')!.trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('FILM-001'); expect(wrapper.text()).not.toContain('加载失败')
    expect(wrapper.text()).toContain('目数 100')
  })
  it('更换网版后丢弃旧详情请求，固定工序要求不变', async () => {
    let resolveOld!: (value: any) => void
    vi.mocked(printingSearchApi.plate).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }) as any)
    const wrapper = mountDetails(row(), false)
    await wrapper.find('button').trigger('click')
    vi.mocked(printingSearchApi.plate).mockResolvedValueOnce({ data: source(8) } as any)
    await wrapper.setProps({ row: { ...row(), defaultPlateId: 8, defaultPlateNo: 'P8' } }); await flushPromises()
    resolveOld({ data: source(4) }); await flushPromises()
    expect(wrapper.text()).toContain('P8'); expect(wrapper.text()).not.toContain('P4')
    expect((wrapper.find('input[placeholder="目数下限"]').element as HTMLInputElement).value).toBe('100')
    await wrapper.find('input[placeholder="张力上限 N/cm"]').setValue('25')
    expect(JSON.parse(wrapper.emitted('update:requirements')!.at(-1)![0] as string)).toEqual({ meshMin: 100, tensionMax: 25 })
  })
  it('发布快照只展示已记录资料，不读取当前网版冒充历史信息', async () => {
    const wrapper = mountDetails({ ...row(), printingSnapshot: { defaultPlateId: 2, defaultPlateNo: '历史版', artworkVersionId: 1, artworkNo: '历史菲林', requirements: { tensionMax: 20 } } })
    await wrapper.find('button').trigger('click'); await flushPromises()
    expect(printingSearchApi.plate).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('历史版'); expect(wrapper.text()).toContain('历史菲林'); expect(wrapper.text()).toContain('20 N/cm')
    expect(wrapper.text()).not.toContain('FILM-001')
  })
  it('保留仅有旧版号的历史文字，明确未核验，不自动推造实体关联', async () => {
    const wrapper = mountDetails({ customProcessParams: '{"plateNo":"旧版面"}' })
    await wrapper.find('button').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('旧版面（未核验）')
    expect(wrapper.text()).toContain('关联关系未核验')
    expect(printingSearchApi.plate).not.toHaveBeenCalled()
  })
  it('关闭弹窗后晚到结果不能带入下一次打开', async () => {
    let resolveOld!: (value: any) => void
    vi.mocked(printingSearchApi.plate).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }) as any)
    const wrapper = mountDetails(row())
    await wrapper.find('button').trigger('click')
    await wrapper.findAll('button').find(b => b.text() === '关闭')!.trigger('click'); await nextTick()
    resolveOld({ data: source(4) }); await flushPromises()
    expect(wrapper.text()).not.toContain('FILM-001')
  })
})
