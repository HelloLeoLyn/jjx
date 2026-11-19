import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import Dialog from '../InventoryMigrationDialog.vue'
const api = vi.hoisted(() => ({ batches: vi.fn(), rows: vi.fn(), costs: vi.fn(), history: vi.fn(), preview: vi.fn(), import: vi.fn() }))
const messages = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn(), warning: vi.fn(), confirm: vi.fn() }))
vi.mock('@/api/inventory/migration', () => ({ migrationApi: api }))
vi.mock('element-plus', async original => ({ ...await original<typeof import('element-plus')>(), ElMessage: messages, ElMessageBox: { confirm: messages.confirm } }))
const Slot = defineComponent({ template: '<div><slot /><slot name="footer" /></div>' })
const stubs = Object.fromEntries(['ElDialog', 'ElSelect', 'ElOption', 'ElTable', 'ElUpload'].map(name => [name, Slot]))
const row = { lineId: 101, sourceRow: 2, materialCode: 'RM1', materialName: 'PC', specification: '470mm', unit: 'M', initialQuantity: 258, remainingQuantity: 258, unitCost: null, costVersion: null, location: '', issues: '{"issues":[]}' }
function mounted() { return mount(Dialog, { props: { modelValue: true }, global: { stubs: { ...stubs, ElAlert: true, ElTableColumn: true, ElPagination: true, ElInputNumber: true, ElInput: true, ElDivider: true }, directives: { hasPermi: {} } } }) }
describe('库存迁移数据维护', () => {
  beforeEach(() => {
    vi.clearAllMocks(); messages.confirm.mockResolvedValue(true)
    api.batches.mockResolvedValue({ data: [{ inboundId: 1, batchCode: 'MIG-A', businessDate: '2026-11-17', rows: 1, missingCosts: 1 }] })
    api.rows.mockResolvedValue({ data: [row] }); api.costs.mockResolvedValue({ data: { changedRows: 1, adjustmentRecords: 1, amountDelta: 0 } })
    api.history.mockResolvedValue({ data: [{ recordId: 1, beforeCost: null, afterCost: 0, operator: '客户', reason: '确认零成本' }] })
  })
  it('实际零成本与缺失成本分别显示，补录携带批次、明细及版本', async () => {
    const wrapper = mounted(); const vm = wrapper.vm as any
    await vm.loadBatches(); vm.selected = [row]; vm.cost = 0; vm.reason = '客户确认零成本'
    await vm.saveCosts()
    expect(api.costs).toHaveBeenCalledWith(1, [{ lineId: 101, expectedVersion: null, unitCost: 0, reason: '客户确认零成本' }])
    expect(wrapper.emitted('changed')).toHaveLength(1)
    vm.rows = [row, { ...row, lineId: 102, unitCost: 0 }]
    expect(vm.filteredRows).toHaveLength(1)
    wrapper.unmount()
  })
  it('不同单位不能被同一个单价批量覆盖', async () => {
    const wrapper = mounted(); const vm = wrapper.vm as any
    vm.batchId = 1; vm.selected = [row, { ...row, lineId: 102, unit: '㎡' }]; vm.cost = 3; vm.reason = '确认'
    await vm.saveCosts(); expect(api.costs).not.toHaveBeenCalled(); expect(messages.warning).toHaveBeenCalled()
    wrapper.unmount()
  })
  it('失败或并发冲突不显示成功，也不触发库存刷新成功事件', async () => {
    const wrapper = mounted(); const vm = wrapper.vm as any
    vm.batchId = 1; vm.selected = [row]; vm.cost = 3; vm.reason = '确认'
    api.costs.mockRejectedValue(new Error('version conflict'))
    await vm.saveCosts(); expect(messages.success).not.toHaveBeenCalled(); expect(wrapper.emitted('changed')).toBeUndefined(); expect(vm.saving).toBe(false)
    wrapper.unmount()
  })
  it('迟到的旧批次响应不覆盖新批次', async () => {
    const wrapper = mounted(); const vm = wrapper.vm as any
    let release: (value: unknown) => void = () => {}
    api.rows.mockImplementationOnce(() => new Promise(resolve => { release = resolve })).mockResolvedValueOnce({ data: [{ ...row, lineId: 202 }] })
    vm.batchId = 1; const first = vm.loadRows(); vm.batchId = 2; await vm.loadRows()
    release({ data: [row] }); await first; await flushPromises()
    expect(vm.rows[0].lineId).toBe(202); wrapper.unmount()
  })
  it('预检有错误或已导入时不能执行导入', async () => {
    const wrapper = mounted(); const vm = wrapper.vm as any
    vm.manifest = { batchCode: 'a' }; vm.check = { rows: 1, zeroRows: 0, errors: ['源行错误'], alreadyImported: false }
    await vm.executeImport(); expect(api.import).not.toHaveBeenCalled()
    vm.check = { rows: 1, zeroRows: 0, errors: [], alreadyImported: true }
    await vm.executeImport(); expect(api.import).not.toHaveBeenCalled(); wrapper.unmount()
  })
  it('成本记录按当前批次和明细读取，保留未知与零成本的区别', async () => {
    const wrapper = mounted(); const vm = wrapper.vm as any
    vm.batchId = 1; await vm.loadHistory(row)
    expect(api.history).toHaveBeenCalledWith(1, 101)
    expect(vm.historyVisible).toBe(true); expect(vm.history[0].beforeCost).toBeNull(); expect(vm.history[0].afterCost).toBe(0)
    wrapper.unmount()
  })
})
