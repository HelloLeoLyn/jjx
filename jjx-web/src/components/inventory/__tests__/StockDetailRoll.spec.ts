import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import StockDetailDialog from '../StockDetailDialog.vue'
import RollReturnDialog from '../RollReturnDialog.vue'

const mocks = vi.hoisted(() => ({ list: vi.fn(), batchSummary: vi.fn(), area: vi.fn(), returnRoll: vi.fn(),
  flow: vi.fn(), warehouses: vi.fn(), locations: vi.fn(), stock: vi.fn(), success: vi.fn(), warning: vi.fn() }))
vi.mock('@/api/inventory/stockItem', () => ({ stockItemApi: mocks, rollApi: mocks }))
vi.mock('@/api/inventory/stock', () => ({ stockApi: { getById: mocks.stock } }))
vi.mock('@/api/inventory/warehouse', () => ({ warehouseApi: { list: mocks.warehouses } }))
vi.mock('@/api/inventory/location', () => ({ locationApi: { getByWarehouse: mocks.locations } }))
vi.mock('@/store/modules/user', () => ({ useUserStore: () => ({ hasPermission: () => true }) }))
vi.mock('../BatchTransactionDrawer.vue', () => ({ default: { template: '<div />' } }))
vi.mock('element-plus', async (original) => ({ ...await original<typeof import('element-plus')>(),
  ElMessage: { success: mocks.success, warning: mocks.warning, error: vi.fn() } }))
const Slot = defineComponent({ template: '<div><slot /><slot name="footer" /></div>' })
const Table = defineComponent({ props: ['data'], template: '<div><slot /></div>' })
const Column = defineComponent({ props: ['label'], template: '<span />' })
const Input = defineComponent({ props: ['modelValue', 'type'], emits: ['update:modelValue'],
  template: `<textarea :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />` })
const NumberInput = defineComponent({ props: ['modelValue'], emits: ['update:modelValue','change'],
  template: `<input type="number" :value="modelValue" @input="$emit('update:modelValue', Number($event.target.value)); $emit('change')" />` })
const Select = defineComponent({ props: ['modelValue'], emits: ['update:modelValue','change'],
  template: `<select :value="modelValue" @change="$emit('update:modelValue',$event.target.value); $emit('change')"><slot /></select>` })
const Option = defineComponent({ props: ['value','label'], template: '<option :value="value">{{ label }}</option>' })
const Button = defineComponent({ emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' })
const stubs = { ElDialog: Slot, ElCard: Slot, ElDescriptions: Slot, ElDescriptionsItem: Slot, ElForm: Slot,
  ElFormItem: Slot, ElTable: Table, ElTableColumn: Column, ElInput: Input, ElInputNumber: NumberInput,
  ElSelect: Select, ElOption: Option, ElButton: Button, ElCheckbox: true, Pagination: true, ElTag: Slot }
const roll = { itemId: '1', inventoryItemId: '31', warehouseId: '1', locationId: '', materialCode: 'AUX-TEST',
  materialName: '卷材', rollNo: 'R1', rollWidthMm: 195, originalRollLengthM: 50, remainingRollLengthM: 25,
  quantity: 4.875, reservedQuantity: 0, availableQuantity: 4.875, receivedQuantity: 14.625, issuedQuantity: 9.75,
  lastOutboundItemId: '101', batchNo: 'SAME-BATCH', partialRoll: true } as any
beforeEach(() => {
  vi.clearAllMocks()
  mocks.list.mockResolvedValue({ data: { records: [roll, { ...roll, itemId: '2', rollNo: 'R2', quantity: 9.75,
    receivedQuantity: 9.75, issuedQuantity: 0, remainingRollLengthM: 50, partialRoll: false }], total: 2 } })
  mocks.batchSummary.mockResolvedValue({ data: [{ batchNo: 'SAME-BATCH', receivedQuantity: 24.375, issuedQuantity: 9.75 }] })
  mocks.warehouses.mockResolvedValue({ data: { records: [{ warehouseId: 1, warehouseName: '原料仓' }, { warehouseId: 2, warehouseName: '余卷仓' }] } })
  mocks.locations.mockResolvedValue({ data: [{ locationId: 20, locationName: '余卷位' }] })
  mocks.returnRoll.mockResolvedValue({ data: '999' })
})
describe('逐卷库存明细与退料', () => {
  it('同批两卷保留各自收发存，不把批次总量重复塞到每卷', async () => {
    const w = mount(StockDetailDialog, { props: { visible: false, inventoryItemId: '31' }, global: { stubs } })
    await w.setProps({ visible: true }); await flushPromises()
    const rows = w.findAllComponents(Table)[0].props('data')
    expect(rows.map((r: any) => [r.rollNo, r.receivedQuantity, r.issuedQuantity])).toEqual([['R1',14.625,9.75],['R2',9.75,0]])
    const labels = w.findAllComponents(Column).map(c => c.props('label'))
    expect(labels).toEqual(expect.arrayContaining(['卷号','原始尺寸','剩余长度(m)','入库数量','已出库','结存数量']))
    w.unmount()
  })
  it('退料只提交原出库行/卷号/实测长度/位置，面积不作为可信输入', async () => {
    mocks.area.mockResolvedValue({ data: 4.875 })
    const w = mount(RollReturnDialog, { props: { modelValue: false, row: { ...roll, quantity: 0 } }, global: { stubs } })
    await w.setProps({ modelValue: true }); await flushPromises()
    await w.find('input[type=number]').setValue('25'); await flushPromises()
    expect(mocks.area).toHaveBeenCalledWith(195,25)
    expect(w.text()).toContain('4.875')
    await w.findAll('select')[0].setValue('2'); await flushPromises()
    await w.findAll('select')[1].setValue('20')
    await w.find('textarea').setValue('剩料退仓')
    await w.findAll('button').find(b => b.text() === '确认退料入库')!.trigger('click'); await flushPromises()
    expect(mocks.returnRoll).toHaveBeenCalledWith({ sourceOutboundItemId: '101', rollNo: 'R1', returnedLengthM: 25,
      warehouseId: '2', locationId: '20', reason: '剩料退仓' })
    expect(w.emitted('success')).toHaveLength(1)
    w.unmount()
  })
})
