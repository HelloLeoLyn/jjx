import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import Dialog from '../MaterialDetailDialog.vue'
const mocks = vi.hoisted(() => ({ info: vi.fn(), detail: vi.fn(), dict: vi.fn(), stock: vi.fn(), allowed: new Set<string>(), error: vi.fn() }))
vi.mock('@/api/inventory/material', () => ({ materialApi: { getInfo: mocks.info } }))
vi.mock('@/api/inventory/materialMaster', () => ({ materialMasterApi: { detail: mocks.detail, dictionaries: mocks.dict } }))
vi.mock('@/api/inventory/stock', () => ({ stockApi: { getByMaterial: mocks.stock } }))
vi.mock('@/composables/usePermission', () => ({ usePermission: () => ({ hasPermission: (p: string) => mocks.allowed.has(p) }) }))
vi.mock('../materialMasterFields', () => ({ loadMaterialCategoryOptions: async () => [] }))
vi.mock('../MaterialMasterFormDialog.vue', () => ({ default: { name: 'MaterialMasterFormDialog', props: ['modelValue', 'materialId'], template: '<div />' } }))
vi.mock('element-plus', async original => ({ ...await original<typeof import('element-plus')>(), ElMessage: { error: mocks.error } }))
const material = { materialId: 358, materialCode: 'RM000354', materialName: 'PC', remark: '客户说明\n【迁移来源 MIG-A / 源行428】\n专用材料', currentStock: 999, unit: 'M' }
function mounted() { return shallowMount(Dialog, { props: { modelValue: true, materialId: 358, allowEdit: true }, global: { renderStubDefaultSlot: true, stubs: { ElDialog: { template: '<div><slot /><slot name="footer" /></div>' } } } }) }
beforeEach(() => {
  vi.clearAllMocks(); mocks.allowed.clear(); mocks.allowed.add('inventory:stock:view')
  mocks.info.mockResolvedValue({ data: material }); mocks.detail.mockResolvedValue({ data: { material, attributes: {}, configuration: null } }); mocks.dict.mockResolvedValue({ data: {} })
  mocks.stock.mockResolvedValue({ data: { totalQuantity: 258, totalReserved: 10, availableQuantity: 248, unit: 'M' } })
})
describe('共用物料详情', () => {
  it('完整展示换行备注，实时库存沿用库存接口，明确全部仓库', async () => {
    const w = mounted(); await flushPromises(); const vm = w.vm as any
    expect(w.find('[data-test="material-remark"]').text()).toContain('源行428')
    expect(w.find('[data-test="material-remark"]').text()).toContain('专用材料')
    expect(w.html()).toContain('库存（全部仓库）'); expect(w.html()).not.toContain('原文已归档至 Excel')
    expect(vm.stock.totalQuantity).toBe(258); expect(vm.liveQuantity(vm.stock.totalQuantity, vm.stock.unit)).toBe('258 M')
    expect(mocks.stock).toHaveBeenCalledWith('358'); expect(w.find('[data-test="edit-material"]').exists()).toBe(false); w.unmount()
  })
  it('编辑权限控制按钮和表单，保存后刷新当前详情并通知列表', async () => {
    mocks.allowed.add('inventory:material:edit'); const w = mounted(); await flushPromises(); const vm = w.vm as any
    expect(w.find('[data-test="edit-material"]').exists()).toBe(true)
    vm.editVisible = true; await flushPromises(); expect(w.findComponent({ name: 'MaterialMasterFormDialog' }).props('materialId')).toBe(358)
    await vm.handleEdited(); expect(w.emitted('changed')).toHaveLength(1); expect(vm.editVisible).toBe(false)
    expect(mocks.info).toHaveBeenCalledTimes(2); expect(mocks.stock).toHaveBeenCalledTimes(2)
    await w.setProps({ allowEdit: false }); expect(w.find('[data-test="edit-material"]').exists()).toBe(false); w.unmount()
  })
  it('无库存查看权限不请求；库存失败不显示档案缓存999，也不妨碍查看资料', async () => {
    mocks.allowed.clear(); let w = mounted(); await flushPromises(); expect(mocks.stock).not.toHaveBeenCalled(); expect(w.html()).not.toContain('库存（全部仓库）'); w.unmount()
    mocks.allowed.add('inventory:stock:view'); mocks.stock.mockRejectedValue(new Error('failed'))
    w = mounted(); await flushPromises(); const vm = w.vm as any
    expect(vm.material.materialId).toBe(358); expect(vm.stock).toBeNull(); expect(vm.stockError).toContain('读取失败'); expect(vm.liveQuantity(undefined)).toBe('—'); w.unmount()
  })
  it('切换物料或传空ID清除旧资料，迟到响应不能覆盖当前详情', async () => {
    let release: (v: unknown) => void = () => {}
    mocks.info.mockImplementationOnce(() => new Promise(resolve => { release = resolve })).mockResolvedValue({ data: { ...material, materialId: 359 } })
    const w = mounted(); await w.setProps({ materialId: 359 }); await flushPromises()
    release({ data: material }); await flushPromises(); expect((w.vm as any).material.materialId).toBe(359)
    await w.setProps({ materialId: null }); await flushPromises(); expect((w.vm as any).material).toBeNull(); w.unmount()
  })
})
