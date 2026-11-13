import { shallowMount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import Plan from '../index.vue'

const api = vi.hoisted(() => ({
  getPlanSuggestions: vi.fn(),
  inTransit: vi.fn().mockResolvedValue({ data: {} }),
  addOrder: vi.fn(),
  generateOrderNo: vi.fn(),
  confirmPlan: vi.fn(),
}))
vi.mock('@/api/purchase/order', () => api)
vi.mock('@/utils/request', () => ({ default: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('@/views/purchase/order/components/OrderFormDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/api/purchase/planEntry', () => ({
  getPlanUnits: vi.fn().mockResolvedValue({ data: [{ value: 'PCS', label: '片' }] }),
  getPlanMaterial: vi
    .fn()
    .mockImplementation(async (id) => ({
      data: {
        unit: 'PCS',
        materialCode: `M${id}`,
        materialName: `物料${id}`,
        supplierMaterialName: id === 7 ? '供应商品名' : '手工品名',
        materialId: id,
      },
    })),
  previewPlanQuantity: vi.fn().mockImplementation(async (row) => ({ data: row })),
}))
vi.mock('@/api/inventory/material', () => ({ materialApi: {} }))
vi.mock('@/api/inventory/alert', () => ({ alertApi: {} }))
vi.mock('@/api/purchase/supplier', () => ({ listSupplier: vi.fn() }))
vi.mock('@/components/inventory/MaterialDetailDialog.vue', () => ({
  default: { props: ['modelValue', 'materialId'], template: '<div />' },
}))

// 挂载真实工作台；调用真实交互，核对弹窗状态不修改计划行。
describe('采购计划供应商品名与只读物料详情', () => {
  it('从建议带出品名，关闭详情后保留数量和勾选行', async () => {
    api.getPlanSuggestions.mockResolvedValue({
      data: [
        {
          materialId: 7,
          materialCode: 'M7',
          materialName: '物料',
          supplierMaterialName: '供应商品名',
          suggestQuantity: 5,
        },
      ],
    })
    const wrapper = shallowMount(Plan)
    await flushPromises()
    const vm = wrapper.vm as any
    expect(vm.planRows[0].supplierMaterialName).toBe('供应商品名')
    vm.planRows[0].quantity = 12
    vm.handleSelectionChange([vm.planRows[0]])
    vm.openMaterialDetail(7)
    expect(vm.detailMaterialId).toBe(7)
    expect(vm.showMaterialDetail).toBe(true)
    vm.showMaterialDetail = false
    expect(vm.planRows).toHaveLength(1)
    expect(vm.planRows[0].quantity).toBe(12)
    expect(vm.selectedRows[0]).toBe(vm.planRows[0])
    wrapper.unmount()
  })
  it('手工添加物料保留搜索返回的供应商品名', async () => {
    api.getPlanSuggestions.mockResolvedValue({ data: [] })
    const wrapper = shallowMount(Plan)
    await flushPromises()
    const vm = wrapper.vm as any
    vm.openAdd()
    vm.addMaterialId = 8
    await vm.selectMaterial(8)
    vm.draft.purchaseQuantity = 3
    vm.draft.quantity = 3
    await vm.confirmAddMaterial()
    expect(vm.planRows[0]).toMatchObject({ supplierMaterialName: '手工品名', quantity: 3 })
    wrapper.unmount()
  })
})
