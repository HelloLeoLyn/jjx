import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import Dialog from '../QuickStocktakeDialog.vue'
const mocks = vi.hoisted(() => ({ batches: vi.fn(), records: vi.fn(), preview: vi.fn(), save: vi.fn(), confirm: vi.fn(), cancel: vi.fn(), ask: vi.fn(), push: vi.fn(), allowed: new Set<string>() }))
vi.mock('@/api/inventory/stocktake', () => ({ quickStocktakeApi: mocks }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/composables/usePermission', () => ({ usePermission: () => ({ hasPermission: (p: string) => mocks.allowed.has(p) }) }))
vi.mock('element-plus', async original => ({ ...await original<typeof import('element-plus')>(), ElMessage: { warning: vi.fn(), success: vi.fn() }, ElMessageBox: { confirm: mocks.ask } }))
const batch = { stock: { itemId: '7', quantity: 10, reservedQuantity: 2, batchNo: 'B1' }, unit: 'M', fingerprint: 'fingerprint1', warehouseName: '原料仓', locationName: 'A1' }
const receipt = { stocktakeId: '88', stocktakeNo: 'STQ-88', orderStatus: 5, inventoryItemId: '31', stockItemId: '7', systemQuantity: 10, actualQuantity: 9, diffQuantity: -1, unit: 'M', reason: '实盘' }
function mount() { return shallowMount(Dialog, { props: { modelValue: true, inventoryItemId: '31', materialName: '材料', warehouseId: 1, locationId: 10 }, global: { directives: { loading: {} } } }) }
beforeEach(() => {
  vi.clearAllMocks(); mocks.allowed.clear(); mocks.allowed.add('inventory:stocktake:add'); mocks.allowed.add('inventory:stocktake:approve')
  mocks.batches.mockResolvedValue({ data: { records: [batch], total: 1 } }); mocks.records.mockResolvedValue({ data: { records: [receipt], total: 1 } })
  mocks.preview.mockResolvedValue({ data: { diffQuantity: -1, unit: 'M' } }); mocks.save.mockResolvedValue({ data: receipt }); mocks.confirm.mockResolvedValue({ data: receipt }); mocks.ask.mockResolvedValue('confirm'); mocks.cancel.mockResolvedValue({ data: receipt })
})
describe('快捷盘点保存与过账', () => {
  it('批次按当前库存物品和仓库库位加载，保存携带快照，保存本身不触发库存刷新', async () => {
    const w = mount(); await flushPromises(); const vm = w.vm as any
    expect(mocks.batches).toHaveBeenCalledWith(expect.objectContaining({ inventoryItemId: '31', warehouseId: 1, locationId: 10 }))
    vm.selectBatch(batch); vm.actual = 9; vm.reason = '实盘'; await vm.save()
    expect(mocks.save).toHaveBeenCalledWith(expect.objectContaining({ inventoryItemId: '31', stockItemId: '7', fingerprint: 'fingerprint1', actualQuantity: 9, reason: '实盘' }))
    expect(mocks.confirm).not.toHaveBeenCalled(); expect(w.emitted('changed')).toBeUndefined(); expect(vm.tab).toBe('records'); w.unmount()
  })
  it('网络失败保留同一请求标识供重试，重复点击不能并发保存', async () => {
    const w = mount(); await flushPromises(); const vm = w.vm as any
    vm.selectBatch(batch); vm.actual = 9; vm.reason = '实盘'
    mocks.save.mockRejectedValueOnce(new Error('network')); await vm.save(); const first = mocks.save.mock.calls[0][0]
    await vm.save(); expect(mocks.save.mock.calls[1][0].requestId).toBe(first.requestId)
    expect(first.requestId).toMatch(/^[a-f0-9-]{36}$/); w.unmount()
  })
  it('缺少审批权限不提交；有权限须确认后才过账并刷新库存', async () => {
    mocks.allowed.delete('inventory:stocktake:approve'); const denied = mount(); await flushPromises()
    await (denied.vm as any).confirm(receipt); expect(mocks.confirm).not.toHaveBeenCalled(); denied.unmount()
    mocks.allowed.add('inventory:stocktake:approve'); const w = mount(); await flushPromises(); const vm = w.vm as any; await vm.confirm(receipt)
    expect(mocks.ask).toHaveBeenCalled(); expect(mocks.confirm).toHaveBeenCalledWith('88'); expect(w.emitted('changed')).toHaveLength(1); w.unmount()
  })
  it('作废未生效凭证只刷新记录，不触发库存变动', async () => {
    mocks.allowed.add('inventory:stocktake:edit'); const w = mount(); await flushPromises()
    await (w.vm as any).cancel(receipt)
    expect(mocks.cancel).toHaveBeenCalledWith('88'); expect(w.emitted('changed')).toBeUndefined(); w.unmount()
  })
  it('切换物料后上一物料批次响应迟到不会回写', async () => {
    let resolveOld!: (value: unknown) => void
    mocks.batches.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    const w = mount(); await flushPromises(); await w.setProps({ inventoryItemId: '32' }); await flushPromises()
    resolveOld({ data: { records: [{ ...batch, fingerprint: 'old' }], total: 9 } }); await flushPromises()
    expect((w.vm as any).batches[0].fingerprint).toBe('fingerprint1'); expect((w.vm as any).batchTotal).toBe(1); w.unmount()
  })
})
