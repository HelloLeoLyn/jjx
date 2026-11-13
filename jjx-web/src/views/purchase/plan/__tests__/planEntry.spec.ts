import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import Plan from '../index.vue'
const mocks = vi.hoisted(() => ({
  suggestions: vi.fn(),
  material: vi.fn(),
  units: vi.fn(),
  preview: vi.fn(),
  batch: vi.fn(),
  search: vi.fn(),
}))
vi.mock('@/utils/request', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
vi.mock('@/api/purchase/order', () => ({ getPlanSuggestions: mocks.suggestions }))
vi.mock('@/api/purchase/planEntry', async (original) => ({
  ...(await original<typeof import('@/api/purchase/planEntry')>()),
  getPlanMaterial: mocks.material,
  getPlanUnits: mocks.units,
  previewPlanQuantity: mocks.preview,
}))
vi.mock('@/api/inventory/alert', () => ({ alertApi: { batchProcess: mocks.batch } }))
vi.mock('@/api/inventory/material', () => ({ materialApi: { search: mocks.search } }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('@/views/purchase/order/components/OrderFormDialog.vue', () => ({
  default: { props: ['visible', 'planSeed'], template: '<div />' },
}))
vi.mock('@/components/Selector/SupplierSelector.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/inventory/MaterialDetailDialog.vue', () => ({
  default: { template: '<div />' },
}))
const material = {
  materialId: 110,
  materialCode: 'AUX000106',
  materialName: '3M 9080',
  supplierMaterialName: '供应商品名',
  materialSpec: '195mm*50000mm',
  unit: '㎡',
  allowedUnits: ['㎡', 'ROLL', 'M', 'PCS'],
  rollWidthMm: 195,
  rollLengthM: 50,
  pieceWidthMm: 195,
  pieceLengthMm: 50000,
  archiveWidthMm: 195,
  archiveLengthMm: 50000,
  currentStock: 9.75,
  availableStock: 8,
  inTransit: 19.5,
}
let wrappers: ReturnType<typeof shallowMount>[] = []
function mount() {
  const w = shallowMount(Plan)
  wrappers.push(w)
  return { w, vm: w.vm as any }
}
async function addRoll(vm: any) {
  vm.openAdd()
  vm.addMaterialId = 110
  await vm.selectMaterial(110)
  vm.draft.purchaseUnit = 'ROLL'
  vm.draft.purchaseQuantity = 2
  await vm.confirmAddMaterial()
}
beforeEach(() => {
  vi.clearAllMocks()
  mocks.suggestions.mockResolvedValue({ data: [] })
  mocks.material.mockResolvedValue({ data: material })
  mocks.units.mockResolvedValue({
    data: [
      { value: '㎡', label: '㎡' },
      { value: 'ROLL', label: '卷' },
      { value: 'PCS', label: '片' },
    ],
  })
  mocks.batch.mockResolvedValue({})
  mocks.preview.mockImplementation(async (row) => ({
    data: { ...row, quantity: row.purchaseUnit === 'ROLL' ? 19.5 : row.purchaseQuantity },
  }))
})
afterEach(() => {
  wrappers.forEach((w) => w.unmount())
  wrappers = []
})
describe('采购计划数量与订单核对', () => {
  it('预警小数需求原样保留，档案补基本单位；不再取整或补成1', async () => {
    mocks.suggestions.mockResolvedValue({
      data: [{ ...material, unit: undefined, suggestQuantity: 0.156, sourceAlertId: 7 }],
    })
    const { vm } = mount()
    await flushPromises()
    expect(vm.planRows[0]).toMatchObject({
      demandQuantity: 0.156,
      purchaseQuantity: 0.156,
      quantity: 0.156,
      unit: '㎡',
      purchaseUnit: '㎡',
      sourceAlertIds: [7],
    })
  })
  it('添加卷材带出真实档案，调用后端换算并显示库存和在途', async () => {
    const { vm } = mount()
    await flushPromises()
    await addRoll(vm)
    expect(vm.planRows[0]).toMatchObject({
      purchaseQuantity: 2,
      purchaseUnit: 'ROLL',
      quantity: 19.5,
      unit: '㎡',
      currentStock: 9.75,
      inTransit: 19.5,
      supplierMaterialName: '供应商品名',
    })
    expect(mocks.preview).toHaveBeenLastCalledWith(
      expect.objectContaining({
        materialId: 110,
        purchaseUnit: 'ROLL',
        purchaseQuantity: 2,
        rollLengthM: 50,
      })
    )
  })
  it('修改计划采购量不改变原始需求，取消编辑不改变原行', async () => {
    mocks.suggestions.mockResolvedValue({
      data: [{ ...material, suggestQuantity: 10.5, sourceAlertId: 7 }],
    })
    const { vm } = mount()
    await flushPromises()
    await vm.openEdit(vm.planRows[0])
    vm.draft.purchaseUnit = 'ROLL'
    vm.draft.purchaseQuantity = 2
    await vm.confirmAddMaterial()
    expect(vm.planRows[0]).toMatchObject({
      demandQuantity: 10.5,
      purchaseQuantity: 2,
      quantity: 19.5,
    })
    await vm.openEdit(vm.planRows[0])
    vm.draft.purchaseQuantity = 99
    vm.showAddMaterial = false
    vm.invalidateDraft()
    expect(vm.planRows[0].purchaseQuantity).toBe(2)
  })
  it('同物料不同采购单位不能直接累加', async () => {
    const { vm } = mount()
    await flushPromises()
    await addRoll(vm)
    await addRoll(vm)
    expect(vm.planRows).toHaveLength(1)
    expect(vm.planRows[0].quantity).toBe(19.5)
    expect(vm.showAddMaterial).toBe(true)
  })
  it('换算失败保留草稿且不加入计划', async () => {
    const { vm } = mount()
    await flushPromises()
    vm.openAdd()
    await vm.selectMaterial(110)
    mocks.preview.mockRejectedValueOnce(new Error('卷数必须为整数'))
    vm.draft.purchaseUnit = 'ROLL'
    vm.draft.purchaseQuantity = 0.5
    await vm.confirmAddMaterial()
    expect(vm.planRows).toHaveLength(0)
    expect(vm.draft.purchaseQuantity).toBe(0.5)
    expect(vm.draft.pricingError).toContain('整数')
  })
  it('迟到的物料读取不覆盖新选择的物料', async () => {
    let resolve!: (r: any) => void
    mocks.material.mockReturnValueOnce(new Promise((done) => (resolve = done)))
    const { vm } = mount()
    await flushPromises()
    vm.openAdd()
    const old = vm.selectMaterial(110)
    mocks.material.mockResolvedValueOnce({
      data: { ...material, materialId: 354, materialCode: 'RM000350' },
    })
    await vm.selectMaterial(354)
    resolve({ data: material })
    await old
    expect(vm.draft.materialId).toBe(354)
  })
  it('迟到的换算结果不覆盖最新采购数量', async () => {
    const { vm } = mount()
    await flushPromises()
    vm.openAdd()
    await vm.selectMaterial(110)
    let resolve!: (r: any) => void
    mocks.preview.mockReturnValueOnce(new Promise((done) => (resolve = done)))
    vm.draft.purchaseQuantity = 2
    const old = vm.calculateDraft()
    vm.draft.purchaseQuantity = 3
    await vm.calculateDraft()
    resolve({ data: { ...material, purchaseQuantity: 2, quantity: 2 } })
    await old
    expect(vm.draft.quantity).toBe(3)
  })
  it('关闭添加窗口后迟到响应失效', async () => {
    const { vm } = mount()
    await flushPromises()
    vm.openAdd()
    let resolve!: (r: any) => void
    mocks.material.mockReturnValueOnce(new Promise((done) => (resolve = done)))
    const old = vm.selectMaterial(110)
    vm.showAddMaterial = false
    vm.invalidateDraft()
    resolve({ data: material })
    await old
    expect(vm.draft).toBeUndefined()
  })
  it('库存读取失败显示未知，不伪装成0', async () => {
    mocks.suggestions.mockResolvedValue({ data: [{ ...material, suggestQuantity: 1 }] })
    mocks.material.mockRejectedValue(new Error('offline'))
    const { vm } = mount()
    await flushPromises()
    expect(vm.planRows[0].currentStock).toBeNull()
    expect(vm.quantityLabel(null, '㎡')).toBe('—')
    expect(vm.planRows[0].contextError).toBeTruthy()
  })
  it('核对订单只预填，不提前创建订单或处理预警，取消保留计划', async () => {
    mocks.suggestions.mockResolvedValue({
      data: [{ ...material, suggestQuantity: 10.5, sourceAlertId: 7 }],
    })
    const { vm } = mount()
    await flushPromises()
    vm.handleSelectionChange(vm.planRows)
    vm.selectSupplier({ supplierId: 16, supplierName: '供应商' })
    vm.doConfirmPlan()
    expect(vm.orderVisible).toBe(true)
    expect(vm.orderSeed.items[0]).toMatchObject({
      quantity: 10.5,
      purchaseQuantity: 10.5,
      unit: '㎡',
    })
    expect(vm.orderSeed.items[0].purchaseRemark).toContain('原始需求 10.5')
    expect(mocks.batch).not.toHaveBeenCalled()
    vm.orderVisible = false
    expect(vm.planRows).toHaveLength(1)
  })
  it('订单成功后才移除覆盖行并关联预警；失败可重试关联且不重复下单', async () => {
    mocks.suggestions.mockResolvedValue({
      data: [{ ...material, suggestQuantity: 10.5, sourceAlertId: 7 }],
    })
    mocks.batch.mockRejectedValueOnce(new Error('offline'))
    const { vm } = mount()
    await flushPromises()
    vm.handleSelectionChange(vm.planRows)
    vm.selectSupplier({ supplierId: 16, supplierName: '供应商' })
    vm.doConfirmPlan()
    await vm.handleOrderSaved({ orderNo: 'PO-TEST', items: [{ ...material, quantity: 19.5 }] })
    expect(vm.planRows).toHaveLength(0)
    expect(vm.pendingAlert.relatedOrderNo).toBe('PO-TEST')
    expect(mocks.batch).toHaveBeenCalledWith(
      expect.objectContaining({ alertIds: [7], materialIds: [110], relatedOrderNo: 'PO-TEST' })
    )
    await vm.retryAlertLink()
    expect(vm.pendingAlert).toBeUndefined()
    expect(mocks.batch).toHaveBeenCalledTimes(2)
  })
  it('下单量不足需求时保留原始需求和计划，不解除预警', async () => {
    mocks.suggestions.mockResolvedValue({
      data: [{ ...material, suggestQuantity: 30, sourceAlertId: 7 }],
    })
    const { vm } = mount()
    await flushPromises()
    vm.handleSelectionChange(vm.planRows)
    vm.selectSupplier({ supplierId: 16, supplierName: '供应商' })
    vm.doConfirmPlan()
    await vm.handleOrderSaved({ orderNo: 'PO-SMALL', items: [{ ...material, quantity: 19.5 }] })
    expect(vm.planRows[0]).toMatchObject({
      demandQuantity: 30,
      quantity: 30,
      linkedOrderNo: 'PO-SMALL',
    })
    expect(mocks.batch).not.toHaveBeenCalled()
  })
  it('导出保留数量单位和需求，不混合汇总为件', async () => {
    const { vm } = mount()
    await flushPromises()
    await addRoll(vm)
    expect(vm.exportRows()[0]).toMatchObject({
      计划采购量: 2,
      采购单位: '卷',
      基本单位: '㎡',
      折合基本数量: 19.5,
      原始需求: '',
    })
  })
})
