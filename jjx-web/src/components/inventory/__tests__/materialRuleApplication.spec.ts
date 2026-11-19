import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import Dialog from '../MaterialRuleApplicationDialog.vue'

const api = vi.hoisted(() => ({ ruleChanges: vi.fn(), applyRules: vi.fn() }))
vi.mock('@/api/inventory/materialMaster', () => ({ materialMasterApi: api }))
vi.mock('element-plus', async importOriginal => ({ ...await importOriginal<typeof import('element-plus')>(), ElMessage: { warning: vi.fn() } }))
const row = (id: number, selectable = true) => ({ materialId: id, materialCode: `AUX${id}`, oldName: '旧名', newName: '新名',
  oldModel: 'OLD', newModel: 'NEW', oldSpecification: '2*365mm', newSpecification: '厚2mm*宽365mm',
  selectable, message: '', previewToken: selectable ? String(id % 10).repeat(64) : null })
const mountDialog = () => shallowMount(Dialog, { props: { modelValue: true, categoryId: 26 }, global: {
  directives: { loading: {} }, stubs: { ElDialog: { template: '<div><slot /><slot name="footer" /></div>' } },
} })

describe('现有物料选择应用规则', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.ruleChanges.mockImplementation(async (query) => ({ data: { total: 45, records: query.pageNum === 1 ? [row(1), row(2), row(3, false)] : [row(41)] } }))
    api.applyRules.mockResolvedValue({ data: { success: 2, skipped: 0, failed: 0, records: [] } })
  })
  it('跨页保留选择，取消勾选有效，只有选中的ID与指纹提交，型号默认不更新', async () => {
    const wrapper = mountDialog(); await flushPromises(); const vm = wrapper.vm as any
    expect(api.ruleChanges.mock.calls[0][0].applyModel).toBe(false)
    vm.togglePage(true)
    expect(vm.selected.size).toBe(2)
    vm.toggle(row(2), false)
    vm.pageNum = 3; await vm.load(); await flushPromises()
    vm.toggle(row(41), true)
    expect(vm.selected.size).toBe(2)
    await vm.apply(); await flushPromises()
    expect(api.applyRules).toHaveBeenCalledWith({ categoryId: 26, applyName: true, applySpecification: true, applyModel: false }, [
      { materialId: 1, previewToken: '1'.repeat(64) }, { materialId: 41, previewToken: '1'.repeat(64) },
    ])
    expect(vm.selected.size).toBe(0); expect(wrapper.emitted('applied')).toHaveLength(1)
    wrapper.unmount()
  })
  it('切换应用范围清空旧选择并重新预览；全不选时不能提交', async () => {
    const wrapper = mountDialog(); await flushPromises(); const vm = wrapper.vm as any
    vm.toggle(row(1), true); vm.options.applyModel = true; await flushPromises()
    expect(vm.selected.size).toBe(0)
    expect(api.ruleChanges.mock.lastCall?.[0].applyModel).toBe(true)
    vm.options.applyName = false; vm.options.applySpecification = false; vm.options.applyModel = false
    await flushPromises(); await vm.apply()
    expect(vm.error).toContain('至少选择'); expect(api.applyRules).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('资料或规则变化后刷新取消旧选择，不静默替换预览指纹', async () => {
    const wrapper = mountDialog(); await flushPromises(); const vm = wrapper.vm as any
    vm.toggle(row(1), true)
    api.ruleChanges.mockResolvedValueOnce({ data: { total: 1, records: [{ ...row(1), previewToken: 'a'.repeat(64) }] } })
    await vm.load(); expect(vm.selected.size).toBe(0)
    wrapper.unmount()
  })
  it('延迟返回的旧请求不会覆盖切换范围后的结果', async () => {
    let finish!: (value: unknown) => void
    api.ruleChanges.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const wrapper = mountDialog(); await flushPromises(); const vm = wrapper.vm as any
    vm.options.applyModel = true; await flushPromises()
    finish({ data: { total: 999, records: [row(99)] } }); await flushPromises()
    expect(vm.total).toBe(45); expect(vm.records[0].materialId).toBe(1)
    wrapper.unmount()
  })
  it('应用失败显示原因，保留核对依据，不发送未勾选记录', async () => {
    const wrapper = mountDialog(); await flushPromises(); const vm = wrapper.vm as any
    vm.toggle(row(1), true)
    api.applyRules.mockResolvedValueOnce({ data: { success: 0, skipped: 0, failed: 1, records: [
      { materialId: 1, materialCode: 'AUX1', success: false, skipped: false, message: '规则已变化，请重新预览' },
    ] } })
    await vm.apply(); await flushPromises()
    expect(vm.failures[0].message).toContain('规则已变化'); expect(wrapper.emitted('applied')).toBeUndefined()
    expect(api.applyRules.mock.calls[0][1]).toHaveLength(1)
    wrapper.unmount()
  })
})
