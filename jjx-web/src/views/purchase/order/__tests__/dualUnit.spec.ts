import { shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import OrderFormDialog from '../components/OrderFormDialog.vue'
vi.mock('@/api/inventory/material', () => ({ materialApi: {} }))
const api = vi.hoisted(() => ({
  addOrder: vi.fn().mockResolvedValue({}),
  updateOrder: vi.fn(),
  generateOrderNo: vi.fn(),
  getOrder: vi.fn(),
  previewOrderPricing: vi.fn(),
}))
vi.mock('@/api/purchase/order', () => api)
const mount = (orderId?: number) => shallowMount(OrderFormDialog, { props: { visible: false, orderId } })
describe('采购双单位与服务端金额预览', () => {
  it('称重旧单改变卷数后必须重新确认净重，不能沿用旧重量', async () => {
    api.previewOrderPricing.mockRejectedValueOnce(new Error('请确认实际净重'))
    const w=mount(55),vm=w.vm as any
    vm.form.items=[{itemId:9,materialId:11,unit:'KG',purchaseUnit:'ROLL',purchaseQuantity:1,unitPrice:2,
      measurementSnapshot:JSON.stringify({policy:{mode:'MEASURED_WEIGHT',physicalUnit:'ROLL',priceUnit:'KG'}}),
      measurementActual:{physicalQuantity:2,actualWeightKg:10}}]
    vm.handlePurchaseQuantityChange(0)
    expect(vm.form.items[0].measurementActual).toMatchObject({physicalQuantity:1,actualWeightKg:undefined})
    await Promise.resolve();await Promise.resolve();w.unmount()
  })
  it('旧单预览从父单传归属，并回显冻结净重与卷长供再次保存', async () => {
    const actual = { physicalQuantity: 2, actualWeightKg: 10, widthMm: 195, rollLengthM: 50 }
    api.previewOrderPricing.mockResolvedValue({ data: { items: [{ unit: 'KG', purchaseUnit: 'ROLL', priceUnit: 'KG', quantity: 10, purchaseQuantity: 2, amount: 20, measurementActual: actual, rollLengthM: 50 }] } })
    const w = mount(55), vm = w.vm as any
    vm.form.items = [{ itemId: 9, materialId: 11, unit: 'KG', purchaseUnit: 'ROLL', purchaseQuantity: 2, unitPrice: 2 }]
    await vm.calculateRowTotal(0)
    expect(api.previewOrderPricing).toHaveBeenLastCalledWith(expect.objectContaining({ orderId: 55, items: [expect.objectContaining({ itemId: 9 })] }))
    expect(vm.form.items[0].measurementActual).toEqual(actual)
    expect(vm.form.items[0].rollLengthM).toBe(50)
    w.unmount()
  })
  it('默认采用物料单位、卷宽；把服务端金额和空折合价作为只读值', async () => {
    api.previewOrderPricing.mockResolvedValue({
      data: { orderAmount: 0, orderTax: 0, orderTotalAmount: 0,
        items: [
          {
            purchaseUnit: '㎡',
            priceUnit: '㎡',
            quantity: 0,
            amount: 0,
            equivalentUnitPrice: null,
          },
        ],
      },
    })
    const w = mount()
    const vm = w.vm as any
    vm.supplierMaterials = [
      { materialId: 7, materialCode: 'M7', materialName: '料', unit: '㎡', widthMm: 195 },
    ]
    vm.handleMaterialChange(7, 0)
    expect(vm.form.items[0]).toMatchObject({
      purchaseUnit: '㎡',
      priceUnit: '㎡',
      rollWidthMm: 195,
    })
    await vm.calculateRowTotal(0)
    expect(vm.form.items[0].equivalentUnitPrice).toBeNull()
    w.unmount()
  })
  it('按卷采购提交全部卷维度，金额沿用后端结果', async () => {
    api.previewOrderPricing.mockResolvedValue({
      data: { orderAmount: 487.5, orderTax: 0, orderTotalAmount: 487.5,
        items: [
          {
            purchaseUnit: 'ROLL',
            priceUnit: '㎡',
            rollCount: 5,
            quantity: 48.75,
            amount: 487.5,
            equivalentUnitPrice: 10,
            rollWidthMm: 195,
          },
        ],
      },
    })
    const w = mount()
    const vm = w.vm as any
    vm.formRef = { validate: vi.fn().mockResolvedValue(true) }
    vm.form.items = [
      {
        materialId: 7,
        materialCode: 'M7',
        unit: '㎡',
        purchaseUnit: 'ROLL',
        priceUnit: '㎡',
        purchaseQuantity: 5,
        rollCount: 5,
        rollLengthM: 50,
        rollWidthMm: 195,
        quantity: 0,
        unitPrice: 10,
        taxRate: 0,
      },
    ]
    await vm.handleSubmit()
    expect(api.addOrder).toHaveBeenLastCalledWith(
      expect.objectContaining({
        items: [
          expect.objectContaining({
            purchaseUnit: 'ROLL',
            priceUnit: '㎡',
            rollCount: 5,
            rollLengthM: 50,
            rollWidthMm: 195,
            quantity: 48.75,
            amount: 487.5,
          }),
        ],
      })
    )
    w.unmount()
  })
  it('换算失败阻止保存，不显示上次金额', async () => {
    api.addOrder.mockClear()
    api.previewOrderPricing.mockRejectedValue(new Error('请补齐卷长/卷宽'))
    const w = mount()
    const vm = w.vm as any
    vm.formRef = { validate: vi.fn().mockResolvedValue(true) }
    vm.form.items = [
      {
        materialId: 7,
        materialCode: 'M7',
        unit: '㎡',
        purchaseUnit: 'ROLL',
        purchaseQuantity: 5,
        quantity: 5,
        unitPrice: 500,
        taxRate: 0,
        amount: 2500,
      },
    ]
    await vm.handleSubmit()
    expect(api.addOrder).not.toHaveBeenCalled()
    expect(vm.form.items[0]).toMatchObject({
      amount: 0,
      equivalentUnitPrice: null,
      pricingError: '请补齐卷长/卷宽',
    })
    w.unmount()
  })
  it('后到的旧预览不能覆盖最新输入', async () => {
    let old: (v: unknown) => void = () => {}
    api.previewOrderPricing
      .mockReturnValueOnce(
        new Promise((resolve) => {
          old = resolve
        })
      )
      .mockResolvedValueOnce({
        data: { orderAmount: 20, orderTax: 0, orderTotalAmount: 20, items: [{ quantity: 2, amount: 20, equivalentUnitPrice: 10 }] },
      })
    const w = mount()
    const vm = w.vm as any
    vm.form.items = [{ materialId: 7, unit: '㎡', quantity: 1, unitPrice: 10 }]
    const pending = vm.calculateRowTotal(0)
    vm.form.items[0].quantity = 2
    await vm.calculateRowTotal(0)
    old({ data: { items: [{ quantity: 1, amount: 10 }] } })
    await pending
    expect(vm.form.items[0].amount).toBe(20)
    w.unmount()
  })
})
