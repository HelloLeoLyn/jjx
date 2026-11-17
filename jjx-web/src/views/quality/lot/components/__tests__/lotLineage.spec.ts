import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import Workbench from '../LotWorkbench.vue'
import Drawer from '@/views/inventory/iqc/components/BatchLineageDrawer.vue'
import { InspectionType } from '@/enums/quality'
import { IqcBatchStatus, IqcBatchTypeEnum } from '@/enums/inventory/IqcBatchEnum'

const api = vi.hoisted(() => ({ page: vi.fn(), detail: vi.fn(), listBatches: vi.fn(), write: vi.fn() }))
const auth = vi.hoisted(() => ({ view: true }))
vi.mock('@/api/quality/lot', () => ({ qualityLotApi: { ...api, judge: api.write, saveItems: api.write, reinspect: api.write }, qualityNcrApi: {} }))
vi.mock('@/api/inventory/iqc', () => ({ iqcApi: { listBatches: api.listBatches } }))
vi.mock('@/directives', () => ({ hasPermi: (permission: string) => permission === 'quality:lot:view' && auth.view }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }), useRoute: () => ({ query: {} }) }))
vi.mock('element-plus', async (original) => ({
  ...(await original<typeof import('element-plus')>()),
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() }, ElMessageBox: {},
}))

const wrappers: ReturnType<typeof mount>[] = []
const lot = (id: number, sourceId = id + 100) => ({ lotId: id, lotNo: `LOT-${id}`, lotType: InspectionType.IQC, sourceType: 'INBOUND', sourceId, sourceNo: `IN-${sourceId}` })
const batches = [
  { batchId: 1, batchNo: 'ROOT', batchType: IqcBatchTypeEnum.ORIGINAL.value, status: IqcBatchStatus.SOURCE },
  { batchId: 2, batchNo: 'CHILD', parentBatchNo: 'ROOT', batchType: IqcBatchTypeEnum.REWORK.value, status: IqcBatchStatus.PENDING_REINSPECTION },
  { batchId: 3, batchNo: 'GRANDCHILD', parentBatchNo: 'CHILD', batchType: IqcBatchTypeEnum.REINSPECTION.value, status: IqcBatchStatus.QUALIFIED },
]
const stubs = {
  ElCard: { template: '<div><slot /></div>' }, ElTable: { template: '<div />' },
  ElDrawer: { template: '<div v-if="modelValue"><slot /></div>', props: ['modelValue'] },
  ElDialog: { template: '<div />' }, ElTabs: { template: '<div />' }, ElTableColumn: true,
  ElAlert: { template: '<p>{{title}}</p>', props: ['title'] },
  ElEmpty: { template: '<p>{{description}}</p>', props: ['description'] },
  ElPagination: true, ElInput: true, ElSelect: true, ElOption: true, ElForm: true, ElFormItem: true,
}
async function render(type: string = InspectionType.IQC) {
  const wrapper = mount(Workbench, { props: { lotType: type }, global: { directives: { loading: () => {} }, stubs } })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, state: (wrapper.vm as any).$.setupState }
}
beforeEach(() => {
  vi.clearAllMocks()
  auth.view = true
  api.page.mockResolvedValue({ data: { records: [], total: 0 } })
  api.detail.mockImplementation(async (id: number) => ({ data: lot(id) }))
  api.listBatches.mockResolvedValue({ data: batches })
})
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))

describe('台账与工作台共用批次溯源', () => {
  it('只读查看权限可看；按最新详情入库ID读取，父子孙批完整交给同一个抽屉', async () => {
    const { wrapper, state } = await render()
    await state.openLineage({ ...lot(1), sourceId: 999 })
    expect(api.detail).toHaveBeenCalledWith(1)
    expect(api.listBatches).toHaveBeenCalledWith('101')
    const drawer = wrapper.getComponent(Drawer)
    expect(drawer.props('batchNo')).toBe('')
    expect(drawer.props('batches')).toEqual(batches)
    expect((drawer.vm as any).$.setupState.rows).toEqual(batches)
    expect(wrapper.text()).toContain('来源入库单：IN-101')
    expect(wrapper.text()).toContain('当前检验批：LOT-1')
    expect(api.write).not.toHaveBeenCalled()
  })
  it('两次请求反序完成时保留后点击的批次，不被旧结果覆盖', async () => {
    const { state } = await render()
    let first!: (value: any) => void
    api.listBatches.mockImplementationOnce(() => new Promise((resolve) => { first = resolve }))
    const pending = state.openLineage(lot(1))
    await flushPromises()
    await state.openLineage(lot(2))
    first({ data: [{ batchNo: 'WRONG-OLD' }] })
    await pending
    expect(state.lineageLot.lotId).toBe(2)
    expect(state.lineageRows).toEqual(batches)
  })
  it('失败会清空旧数据并明确报错，不显示成功空链路', async () => {
    const { wrapper, state } = await render()
    await state.openLineage(lot(1))
    api.listBatches.mockRejectedValueOnce(new Error('403：无查看权限'))
    await state.openLineage(lot(2))
    expect(state.lineageRows).toEqual([])
    expect(wrapper.text()).toContain('403：无查看权限')
    expect(wrapper.text()).not.toContain('暂无批次链路数据')
    expect(state.lineageLoading).toBe(false)
  })
  it('缺失或不支持的来源不能把来源行ID当入库ID请求', async () => {
    const { state } = await render()
    api.detail.mockResolvedValueOnce({ data: { ...lot(1), sourceType: 'INBOUND_ITEM' } })
    await state.openLineage(lot(1))
    expect(api.listBatches).not.toHaveBeenCalled()
    expect(state.lineageError).toContain('未关联有效来源入库单')
  })
  it('关闭后在途详情结果不能重新打开或填回旧数据', async () => {
    const { state } = await render()
    let finish!: (value: any) => void
    api.detail.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
    const pending = state.openLineage(lot(1))
    state.lineageVisible = false
    finish({ data: lot(1) })
    await pending
    expect(state.lineageRows).toEqual([])
    expect(state.lineageVisible).toBe(false)
    expect(api.listBatches).not.toHaveBeenCalled()
  })
  it('成功空链路与响应格式错误分别展示，不保留旧批次', async () => {
    const { wrapper, state } = await render()
    api.listBatches.mockResolvedValueOnce({ data: [] })
    await state.openLineage(lot(1))
    expect(wrapper.text()).toContain('暂无批次链路数据')
    expect(state.lineageError).toBe('')
    api.listBatches.mockResolvedValueOnce({ data: null })
    await state.openLineage(lot(2))
    expect(wrapper.text()).toContain('批次溯源响应无效')
    expect(wrapper.text()).not.toContain('暂无批次链路数据')
    expect(state.lineageRows).toEqual([])
  })
  it('非IQC和缺查看权限均不发起溯源读取', async () => {
    const fqc = await render(InspectionType.FQC)
    await fqc.state.openLineage(lot(1))
    auth.view = false
    const denied = await render()
    await denied.state.openLineage(lot(1))
    expect(api.detail).not.toHaveBeenCalled()
    expect(api.listBatches).not.toHaveBeenCalled()
  })
})
