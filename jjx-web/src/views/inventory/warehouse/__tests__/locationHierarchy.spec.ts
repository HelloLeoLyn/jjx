import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import * as XLSX from 'xlsx'
import LocationPage from '../location.vue'
import Subdivision from '@/components/inventory/LocationSubdivisionDialog.vue'
import ImportDialog from '@/components/inventory/LocationImportDialog.vue'
import { LocationStatusEnum } from '@/enums/inventory/LocationEnum'

const api = vi.hoisted(() => ({ tree: vi.fn(), inventory: vi.fn(), subdivide: vi.fn(), add: vi.fn(), update: vi.fn(), updateStatus: vi.fn(), delete: vi.fn(), importLocation: vi.fn(), getOptions: vi.fn(), getItems: vi.fn() }))
const route = vi.hoisted(() => ({ query: {} as Record<string, string> }))
vi.mock('@/api/inventory/location', () => ({ locationApi: api }))
vi.mock('@/api/inventory/warehouse', () => ({ warehouseApi: api }))
vi.mock('@/api/system/dict', () => ({ dictApi: api }))
vi.mock('@/directives', () => ({ hasPermi: () => true }))
vi.mock('vue-router', () => ({ useRoute: () => route }))
const root = () => ({ locationId: 157, warehouseId: 2, locationCode: 'MIG-157', locationName: 'I区架二，顶层 格', path: 'I区架二，顶层 格', capacity: null, usedCapacity: null, capacityEnabled: false, status: LocationStatusEnum.NORMAL.value, leaf: true, children: [], balances: [{ unit: 'M', received: 200, issued: 6, quantity: 194, reserved: 0 }] })
const batch = (id = 1) => ({ item_id: id, material_code: 'AUX001929', material_name: '原料', batch_no: `B-${id}`, quantity: 10, reserved_quantity: 0, unit: 'M' })
const wrappers: VueWrapper[] = []
function render(component: any, props = {}) {
  const wrapper = mount(component, { props, attachTo: document.body, global: { plugins: [ElementPlus], directives: { hasPermi: () => {} } } })
  wrappers.push(wrapper); return wrapper
}
async function settle() { await flushPromises(); await nextTick(); await new Promise(resolve => setTimeout(resolve, 20)) }
beforeEach(() => {
  vi.clearAllMocks(); route.query = {}
  api.getOptions.mockResolvedValue({ data: [{ warehouseId: '2', warehouseName: '原料仓' }] })
  api.getItems.mockResolvedValue({ data: [{ itemValue: 'M', label: '米' }] })
  api.tree.mockResolvedValue({ data: [root()] }); api.inventory.mockResolvedValue({ data: [batch()] })
  api.subdivide.mockResolvedValue({ data: { transferNo: 'LS-001' } }); api.add.mockResolvedValue({}); api.update.mockResolvedValue({}); api.importLocation.mockResolvedValue({ data: '导入完成' })
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); document.body.innerHTML = '' })

describe('库位维护真实组件回归', () => {
  it('从真实仓库选项加载，空容量不影响表格渲染，保留原始名称与收发存', async () => {
    const wrapper = render(LocationPage); await settle()
    expect(api.tree).toHaveBeenCalledWith(2)
    expect(wrapper.text()).toContain('I区架二，顶层 格')
    expect(wrapper.text()).toContain('200 / 6 / 194 M')
    expect(wrapper.text()).toContain('未启用')
    expect(wrapper.text()).toContain('细分并分配库存')
    expect(wrapper.text()).not.toContain('NaN')
  })
  it('路由仓库ID与字符串接口ID兼容，新增不强填编码、容量或尺寸', async () => {
    route.query.warehouseId = '2'
    const wrapper = render(LocationPage); await settle()
    await wrapper.findAll('button').find(button => button.text() === '新增位置')!.trigger('click'); await settle()
    const input = document.body.querySelector<HTMLInputElement>('input[placeholder^="名称由你决定"]')!
    input.value = '在印刷，贴膜房间'; input.dispatchEvent(new Event('input', { bubbles: true })); await settle()
    ;(Array.from(document.body.querySelectorAll('button')).find(button => button.textContent?.trim() === '保存')!).click(); await settle()
    expect(api.add).toHaveBeenCalledWith(expect.objectContaining({ warehouseId: 2, locationName: '在印刷，贴膜房间', locationCode: '', capacity: null, width: null, height: null, depth: null, capacityEnabled: false, status: LocationStatusEnum.NORMAL.value }))
  })
  it('库存分页不丢分配；提交只带用户明确的批次、子位和数量', async () => {
    api.inventory.mockResolvedValue({ data: Array.from({ length: 11 }, (_, index) => batch(index + 1)) })
    const wrapper = render(Subdivision, { location: root() }); await settle()
    const vm = wrapper.vm as any
    vm.children[0].locationName = '格内A'; vm.children[1].locationName = '格内B'
    for (const row of vm.batches) vm.moveAll(row, vm.children[0])
    vm.amounts[vm.key(vm.batches[0], vm.children[0])] = 3
    vm.amounts[vm.key(vm.batches[0], vm.children[1])] = 7
    vm.page = 2; await settle()
    expect(document.body.textContent).toContain('B-11')
    expect(vm.assigned(vm.batches[0])).toBe(10)
    vm.confirmed = true; await vm.submit(); await settle()
    expect(api.subdivide).toHaveBeenCalledWith(157, expect.objectContaining({ confirmed: true, children: [{ locationName: '格内A', locationCode: '' }, { locationName: '格内B', locationCode: '' }], allocations: expect.arrayContaining([{ stockItemId: 1, childIndex: 0, quantity: 3 }, { stockItemId: 1, childIndex: 1, quantity: 7 }]) }))
    expect(wrapper.emitted('success')).toHaveLength(1)
  })
  it('有预留或读取失败均不能细分，修改分配后必须重新确认', async () => {
    api.inventory.mockResolvedValue({ data: [{ ...batch(), reserved_quantity: 1 }] })
    const wrapper = render(Subdivision, { location: root() }); await settle()
    const vm = wrapper.vm as any; vm.children.forEach((child: any, index: number) => child.locationName = `分区${index}`)
    await settle(); expect(vm.invalid).toContain('预留')
    vm.confirmed = true; await vm.submit(); expect(api.subdivide).not.toHaveBeenCalled()
    vm.amounts[vm.key(vm.batches[0], vm.children[0])] = 2; await settle(); expect(vm.confirmed).toBe(false)
    api.inventory.mockRejectedValueOnce(new Error('读取失败'))
    const second = render(Subdivision, { location: root() }); await settle(); expect((second.vm as any).invalid).toContain('读取失败')
  })
  it('导入保留口语名称与重复行，不自动归区、不编造容量和尺寸', async () => {
    const book = XLSX.utils.book_new(); XLSX.utils.book_append_sheet(book, XLSX.utils.json_to_sheet([{ 库位名称: '在印刷，贴膜房间' }, { 库位名称: '在印刷，贴膜房间' }]), '位置')
    const bytes = XLSX.write(book, { type: 'array', bookType: 'xlsx' })
    const wrapper = render(ImportDialog, { visible: true, warehouseId: 2 }); await settle()
    const vm = wrapper.vm as any
    await vm.readFile({ raw: { arrayBuffer: async () => bytes } }); await settle()
    expect(vm.rows).toHaveLength(2)
    expect(vm.rows[0]).toMatchObject({ locationName: '在印刷，贴膜房间', locationCode: '' })
    expect(vm.rows[0].width).toBeUndefined()
    await vm.save()
    expect(api.importLocation).toHaveBeenCalledWith(expect.arrayContaining([expect.objectContaining({ locationName: '在印刷，贴膜房间', locationCode: '', width: '', height: '', depth: '' })]), 2)
  })
})
