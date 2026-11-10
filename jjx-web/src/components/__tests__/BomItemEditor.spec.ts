import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
vi.mock('@/api/inventory/material', () => ({ materialApi: { search: vi.fn() } }))
vi.mock('@/api/inventory/materialMaster', () => ({ materialMasterApi: { detail: vi.fn() } }))
vi.mock('@/components/inventory/MaterialFormDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/Selector/StandardProcessSelector.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/composables/useEnabledProcesses', () => ({ useEnabledProcesses: () => ({ processes: [] }) }))
vi.mock('sortablejs', () => ({ default: { create: () => ({ destroy() {} }) } }))
import { ElMessage } from 'element-plus'
import { materialApi } from '@/api/inventory/material'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import BomItemEditor from '../BomItemEditor.vue'
const original = { itemId: 1, materialId: 5, materialCode: 'RP-W195', materialName: '离型纸', specification: '195mm', materialSpecification: '195mm', quantity: 1, unit: 'M', lossRate: 0, sortOrder: 1 }
const replacement = { materialId: 9, materialCode: 'NEW', materialName: '新离型纸', specification: '195mm*80mm', unit: 'PCS' }
const detail = (specification = '195mm') => ({ data: { material: { specification, categoryId: 7 }, attributes: {} } })
const page = (records: any[], total = records.length) => ({ data: { records, total } })
const wrappers: any[] = []
function editor(row: any = original) {
  const wrapper = shallowMount(BomItemEditor, { props: { modelValue: [{ ...row }] }, global: { stubs: { ElTable: { template: '<div class="el-table__body-wrapper"><table><tbody /></table></div>' } } } })
  wrappers.push(wrapper)
  const state = (wrapper.vm as any).$.setupState
  return { state, row: state.items[0], exposed: wrapper.vm as any }
}
async function commit(state: any, row: any, value = replacement.specification) {
  row.specification = value; state.invalidateSpecificationMatch(row); state.queueSpecificationMatch(row)
  await vi.advanceTimersByTimeAsync(300)
}
beforeEach(() => {
  vi.useFakeTimers()
  vi.mocked(materialMasterApi.detail).mockReset().mockImplementation(async (id: any) => detail(id === 9 ? replacement.specification : '195mm') as any)
  vi.mocked(materialApi.search).mockReset().mockResolvedValue(page([]) as any)
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.useRealTimers(); vi.restoreAllMocks() })
describe('BOM按规格换料', () => {
  it('打开及刷新保留数据库规格，不拼属性', async () => {
    const { state, row } = editor({ ...original, specification: '450*225mm', materialSpecificationSource: { attributes: { coating: '单面加硬' } } })
    expect(row.specification).toBe('450*225mm'); await state.prepareSpecifications(); expect(row.specification).toBe('450*225mm')
  })
  it('输入不查询，提交防抖；同品类唯一命中同步更换物料', async () => {
    vi.mocked(materialApi.search).mockResolvedValue(page([replacement]) as any)
    const { state, row, exposed } = editor()
    row.specification = replacement.specification; state.invalidateSpecificationMatch(row)
    await vi.advanceTimersByTimeAsync(500); expect(materialApi.search).not.toHaveBeenCalled()
    state.queueSpecificationMatch(row); state.queueSpecificationMatch(row)
    await vi.advanceTimersByTimeAsync(299); expect(materialApi.search).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    expect(materialApi.search).toHaveBeenCalledExactlyOnceWith({ specification: replacement.specification, categoryId: 7, pageNum: 1, pageSize: 100 })
    expect(row).toMatchObject(replacement); expect(exposed.validateItems()).toBe(true)
  })
  it('同品类无结果再查全库，多条候选人工选择', async () => {
    vi.mocked(materialApi.search).mockResolvedValueOnce(page([]) as any).mockResolvedValueOnce(page([replacement, { ...replacement, materialId: 10 }]) as any)
    const { state, row } = editor(); await commit(state, row)
    expect(materialApi.search).toHaveBeenLastCalledWith({ specification: replacement.specification, categoryId: undefined, pageNum: 1, pageSize: 100 })
    expect(state.candidateVisible).toBe(true); expect(state.specCandidates).toHaveLength(2); expect(row.materialId).toBe(5)
    state.chooseSpecificationCandidate(replacement); expect(row.materialId).toBe(9)
  })
  it('跨页候选完整加载，第一页唯一不直接替换', async () => {
    vi.mocked(materialApi.search).mockResolvedValueOnce(page([replacement], 2) as any).mockResolvedValueOnce(page([{ ...replacement, materialId: 10 }], 2) as any)
    const { state, row } = editor(); await commit(state, row)
    expect(state.candidateVisible).toBe(true); expect(state.specCandidates).toHaveLength(2)
  })
  it('无匹配建料，预填规格和单位，建成回填', async () => {
    const { state, row } = editor(); await commit(state, row)
    expect(state.materialFormVisible).toBe(true)
    expect(state.materialPreset).toEqual({ materialName: '离型纸', specification: replacement.specification, unit: 'M' })
    state.handleMaterialCreated(replacement); expect(row).toMatchObject(replacement)
  })
  it('建料期间再改规格，迟到结果不覆盖输入', async () => {
    const { state, row } = editor(); await commit(state, row)
    row.specification = '最新规格'; state.invalidateSpecificationMatch(row); state.handleMaterialCreated(replacement)
    expect(row.materialId).toBe(5); expect(row.specification).toBe('最新规格')
  })
  it('规格一致不匹配；未建档快速行不弹窗并可保存', async () => {
    const { state, row } = editor(); state.queueSpecificationMatch(row)
    const quick = editor({ ...original, materialId: 0 }); quick.state.setQuickMode(true); await commit(quick.state, quick.row)
    expect(materialApi.search).not.toHaveBeenCalled(); expect(quick.state.materialFormVisible).toBe(false); expect(quick.exposed.validateItems()).toBe(true)
  })
  it('清空规格不发送无条件搜索，保留输入并阻断不一致保存', async () => {
    const notice = vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({} as any))
    const { state, row, exposed } = editor(); await commit(state, row, '')
    expect(materialApi.search).not.toHaveBeenCalled(); expect(row.materialId).toBe(5)
    expect(row.specification).toBe(''); expect(state.materialFormVisible).toBe(false)
    expect(notice).toHaveBeenCalledWith('规格为空，请重新选料或填写规格后匹配；已保留输入规格')
    expect(exposed.validateItems()).toBe(false)
  })
  it('规格不一致保存阻断并提示中文', () => {
    const notice = vi.spyOn(ElMessage, 'warning').mockImplementation(() => ({} as any))
    const { row, exposed } = editor(); row.specification = '别的规格'
    expect(exposed.validateItems()).toBe(false); expect(notice).toHaveBeenCalledWith('规格与物料档案不一致，请重新选料或按规格匹配')
  })
  it('新输入立刻作废旧查询，旧响应不能换料或弹窗', async () => {
    let resolve!: (value: any) => void
    vi.mocked(materialApi.search).mockReturnValueOnce(new Promise(done => { resolve = done }))
    const { state, row } = editor(); await commit(state, row)
    row.specification = '更新输入'; state.invalidateSpecificationMatch(row); resolve(page([replacement])); await vi.advanceTimersByTimeAsync(0)
    expect(row.materialId).toBe(5); expect(row.specification).toBe('更新输入'); expect(state.candidateVisible).toBe(false)
  })
  it('查询失败保留输入并给中文提示', async () => {
    vi.mocked(materialApi.search).mockRejectedValueOnce(new Error('网络失败')); vi.spyOn(console, 'error').mockImplementation(() => {})
    const notice = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({} as any))
    const { state, row } = editor(); await commit(state, row)
    expect(row.specification).toBe(replacement.specification); expect(row.materialId).toBe(5)
    expect(notice).toHaveBeenCalledWith('按规格匹配物料失败，请重试；已保留输入规格')
  })
})
