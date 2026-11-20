import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import Page from '../index.vue'
const mocks = vi.hoisted(() => ({ list: vi.fn(), summary: vi.fn(), alerts: vi.fn(), push: vi.fn(), warning: vi.fn(), allowed: new Set<string>() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/components/inventory/QuickStocktakeDialog.vue', () => ({ default: { name: 'QuickStocktakeDialog', props: ['modelValue', 'inventoryItemId', 'materialName', 'warehouseId', 'locationId'], template: '<div />' } }))
vi.mock('../StockSearchForm.vue', () => ({ default: { name: 'StockSearchForm', props: ['modelValue'], emits: ['search', 'reset'], template: '<div />' } }))
vi.mock('@/api/inventory/stock', () => ({ stockApi: { list: mocks.list, summary: mocks.summary, getAlertInfo: mocks.alerts } }))
vi.mock('@/composables/usePermission', () => ({ usePermission: () => ({ hasPermission: (p: string) => mocks.allowed.has(p), hasAnyPermission: (ps: string[]) => ps.some(p => mocks.allowed.has(p)) }) }))
vi.mock('element-plus', async original => ({ ...await original<typeof import('element-plus')>(), ElMessage: { warning: mocks.warning, error: vi.fn(), success: vi.fn() } }))
vi.mock('@/components/inventory/MaterialDetailDialog.vue', () => ({ default: { name: 'MaterialDetailDialog', props: ['modelValue', 'materialId'], emits: ['changed'], template: '<div />' } }))
vi.mock('@/views/product/list/components/ProductDetailDialog.vue', () => ({ default: { name: 'ProductDetailDialog', props: ['modelValue', 'productId'], template: '<div />' } }))
vi.mock('@/components/inventory/StockImportDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/inventory/StockBatchImportDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/inventory/StockDetailDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/inventory/InventoryMigrationDialog.vue', () => ({ default: { template: '<div />' } }))
const row = { sourceId: '358', materialId: '999', inventoryItemId: '3266', stockId: '777', itemType: 'MATERIAL', materialCode: 'RM000354' }
function mounted() { return shallowMount(Page, { global: { directives: { hasPermi: {}, loading: {} } } }) }
beforeEach(() => {
  vi.clearAllMocks(); mocks.allowed.clear(); mocks.allowed.add('inventory:material:view'); mocks.allowed.add('product:list:view')
  mocks.list.mockResolvedValue({ data: { records: [], total: 0 } }); mocks.summary.mockResolvedValue({ data: {} }); mocks.alerts.mockResolvedValue({ data: {} })
})
describe('即时库存编码联查档案', () => {
  it('材料使用来源档案ID；成品使用产品来源ID，不把库存ID传给详情', async () => {
    const w = mounted(); await flushPromises(); const vm = w.vm as any
    vm.handleViewArchive(row); await flushPromises()
    expect(w.findComponent({ name: 'MaterialDetailDialog' }).props('materialId')).toBe(358)
    vm.handleViewArchive({ ...row, itemType: 'PRODUCT', sourceId: '87' }); await flushPromises()
    expect(w.findComponent({ name: 'MaterialDetailDialog' }).exists()).toBe(false)
    expect(w.findComponent({ name: 'ProductDetailDialog' }).props('productId')).toBe(87)
    expect(mocks.push).not.toHaveBeenCalled(); w.unmount()
  })
  it('缺少权限、来源ID无效不打开，也不把库存身份ID当产品ID', async () => {
    const w = mounted(); const vm = w.vm as any
    mocks.allowed.clear(); vm.handleViewArchive(row); expect(vm.materialDetailVisible).toBe(false)
    mocks.allowed.add('product:list:view')
    vm.handleViewArchive({ ...row, itemType: 'PRODUCT', sourceId: undefined }); expect(vm.productDetailVisible).toBe(false)
    expect(mocks.warning).toHaveBeenCalled(); w.unmount()
  })
  it('编辑成功刷新数据，关闭/刷新保留筛选、页码和每页条数', async () => {
    const w = mounted(); await flushPromises(); const vm = w.vm as any
    Object.assign(vm.queryParams, { pageNum: 1, pageSize: 20, keyword: 'RM000354' })
    vm.handleQuery(); await flushPromises(); vm.queryParams.pageNum = 3
    const before = { ...vm.queryParams }
    vm.handleViewArchive(row); await flushPromises()
    w.findComponent({ name: 'MaterialDetailDialog' }).vm.$emit('changed'); await flushPromises()
    expect(mocks.list).toHaveBeenLastCalledWith(expect.objectContaining(before)); expect(vm.queryParams).toEqual(before)
    vm.materialDetailVisible = false; await flushPromises(); expect(vm.queryParams).toEqual(before); w.unmount()
  })
})

describe('即时库存搜索与分页', () => {
  it('第二页传pageNum并保留已应用条件，未搜索的表单改动不混入翻页', async () => {
    const w = mounted(); await flushPromises(); const vm = w.vm as any
    Object.assign(vm.queryParams, { keyword: '离型纸 120g', warehouseId: 1, parentCategoryId: 18 })
    vm.handleQuery(); await flushPromises(); vm.queryParams.pageNum = 2; vm.queryParams.keyword = '尚未搜索'
    await vm.getList()
    expect(mocks.list).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 2, keyword: '离型纸 120g', warehouseId: 1, categoryId: 18 }))
    expect(mocks.list.mock.calls.at(-1)?.[0]).not.toHaveProperty('current')
    expect(mocks.summary).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '离型纸 120g', warehouseId: 1, categoryId: 18 }))
    w.unmount()
  })
  it('较早查询迟到时不能覆盖新页结果', async () => {
    const w = mounted(); await flushPromises(); const vm = w.vm as any
    let resolveOld!: (v: unknown) => void
    mocks.list.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    const old = vm.getList(); vm.queryParams.pageNum = 2
    mocks.list.mockResolvedValueOnce({ data: { records: [{ stockId: 'new' }], total: 25 } })
    await vm.getList(); resolveOld({ data: { records: [{ stockId: 'old' }], total: 10 } }); await old
    expect(vm.stockList).toEqual([{ stockId: 'new' }]); expect(vm.total).toBe(25)
    w.unmount()
  })
})

describe('即时库存调整入口', () => {
  it('按盘点权限打开快捷盘点并传库存物品身份，不传材料档案或库存汇总ID', async () => {
    const w = mounted(); await flushPromises(); const vm = w.vm as any
    vm.handleAdjust(row); expect(vm.adjustVisible).toBe(false)
    mocks.allowed.add('inventory:stocktake:view'); mocks.allowed.add('inventory:stocktake:add')
    // 权限在真实应用登录时固定；重新挂载模拟有盘点权限的用户。
    w.unmount(); const allowed = mounted(); await flushPromises(); const avm = allowed.vm as any
    avm.handleAdjust({ ...row, materialName: '测试材料' }); await flushPromises()
    expect(allowed.findComponent({ name: 'QuickStocktakeDialog' }).props('inventoryItemId')).toBe('3266')
    allowed.unmount()
  })
})
