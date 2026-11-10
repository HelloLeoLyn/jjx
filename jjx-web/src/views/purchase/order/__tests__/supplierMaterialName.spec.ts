import { shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import OrderFormDialog from '../components/OrderFormDialog.vue'
vi.mock('@/api/inventory/material', () => ({ materialApi: {} }))
const api = vi.hoisted(() => ({ addOrder: vi.fn().mockResolvedValue({}), updateOrder: vi.fn(), generateOrderNo: vi.fn(), getOrder: vi.fn() }))
vi.mock('@/api/purchase/order', () => api)

describe('采购订单明细供应商品名', () => {
  it('提交订单时携带供应商品名', async () => {
    const wrapper = shallowMount(OrderFormDialog, { props: { visible: false } })
    const vm = wrapper.vm as any
    vm.formRef = { validate: vi.fn().mockResolvedValue(true) }
    vm.form.items = [{ materialId: 7, materialCode: 'M7', materialName: '物料7', supplierMaterialName: '提交品名', quantity: 2, unitPrice: 3, amount: 6 }]
    await vm.handleSubmit()
    expect(api.addOrder).toHaveBeenCalledWith(expect.objectContaining({ items: [expect.objectContaining({ supplierMaterialName: '提交品名' })] }))
    wrapper.unmount()
  })
  it('选料带出品名，清空或选择重复物料时清空旧品名', () => {
    const wrapper = shallowMount(OrderFormDialog, { props: { visible: false } })
    const vm = wrapper.vm as any
    vm.supplierMaterials = [{ materialId: 7, materialCode: 'M7', materialName: '物料7', supplierMaterialName: '搜索品名' }]
    vm.handleMaterialChange(7, 0)
    expect(vm.form.items[0].supplierMaterialName).toBe('搜索品名')
    vm.handleMaterialChange(undefined, 0)
    expect(vm.form.items[0].supplierMaterialName).toBe('')
    vm.handleMaterialChange(7, 0)
    vm.addItem()
    vm.form.items[1].supplierMaterialName = '残留值'
    vm.handleMaterialChange(7, 1)
    expect(vm.form.items[1].supplierMaterialName).toBe('')
    wrapper.unmount()
  })
})
