import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import ManufacturingBomItemTable from '@/views/product/bom/components/ManufacturingBomItemTable.vue'
import BomEntryTools from '../BomEntryTools.vue'
vi.mock('@/api/production/bomConsumption', () => ({ previewBomConsumption: vi.fn().mockResolvedValue({ data: { readOnly: true, rows: [] } }), previewPitchRequirement: vi.fn() }))
vi.mock('@/components/inventory/MaterialFormDialog.vue', () => ({ default: { template: '<div />' } }))
const material = { itemId: 1, materialId: 110, materialCode: 'AUX000106', materialName: '卷料', specification: '195mm*50000mm', archiveUnit: 'M2', unit: 'M2', quantity: 1, moduleQty: 1, baseQty: 1, lossRate: 0, minIssueQty: 10, sortOrder: 1, split: '78mm' }
const Table = defineComponent({ props: ['data'], setup(_props, { slots }) { return () => h('div', slots.default?.()) } })
const Slot = defineComponent({ template: '<div><slot /></div>' })
let wrapper: ReturnType<typeof mount>
afterEach(() => wrapper?.unmount())
function table() {
  wrapper = mount(ManufacturingBomItemTable, { props: { modelValue: [{ ...material }], outputUnit: 'PCS', outputLengthMm: 80 }, global: { stubs: { ElTable: Table, ElTableColumn: { props: ['label'], template: '<div class="column" :data-label="label" />' }, ElButton: Slot, ElCheckbox: true, ElInputNumber: true, ElTag: true, ElTooltip: Slot } } })
  return wrapper
}
describe('制造BOM正式表格接入精简工具', () => {
  it('保留制造入口和审核保存契约，单位/最低投料/跳距不再铺成可编辑列', async () => {
    const form = table(); await flushPromises()
    const labels = form.findAll('.column').map(column => column.attributes('data-label'))
    expect(labels).toContain('单位')
    expect(labels).toContain('每件用量')
    expect(labels).toContain('总需求')
    expect(labels).toContain('下层方案')
    expect(labels).not.toContain('投入单位')
    expect(labels).not.toContain('最低投料')
    expect(labels).not.toContain('跳距(mm)')
    const tools = form.findComponent(BomEntryTools)
    expect(tools.props('outputUnit')).toBe('PCS')
    expect(tools.props('outputLengthMm')).toBe(80)
    const row = form.props('modelValue')[0]
    expect(row.minIssueQty).toBe(10)
    expect(row.split).toBe('78mm')
    await form.setProps({ readonly: true })
    expect(tools.props('readonly')).toBe(true)
  })
})
