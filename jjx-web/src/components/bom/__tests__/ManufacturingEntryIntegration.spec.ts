import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, reactive } from 'vue'
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
    expect(labels).toContain('跳距(mm)')
    expect(labels).toContain('每跳片数')
    expect(labels).not.toContain('来源方式')
    expect(labels).not.toContain('单耗(g/㎡)')
    expect(form.text()).not.toContain('跳距辅助定额')
    const tools = form.findComponent(BomEntryTools)
    expect(tools.props('outputUnit')).toBe('PCS')
    expect(tools.props('outputLengthMm')).toBe(80)
    const row = form.props('modelValue')[0]
    expect(row.minIssueQty).toBe(10)
    expect(row.split).toBe('78mm')
    await form.setProps({ readonly: true })
    expect(tools.props('readonly')).toBe(true)
  })
  it('制造投入行允许跳距与空组草稿，保存拦截参数冲突与单位不一致', async () => {
    const form = table(); await flushPromises()
    const row = form.props('modelValue')[0] as any
    const validate = () => (form.vm as any).validate()
    row.baseQty = undefined; row.moduleQty = undefined; row.pitchMm = 80; row.pcsPerPitch = 1
    expect(validate()).toBe('')
    row.baseQty = 1; row.moduleQty = 6
    expect(validate()).toContain('二选一')
    row.baseQty = undefined; row.moduleQty = undefined; row.pitchMm = null
    expect(validate()).toBe('')
    row.archiveUnit = 'PCS'
    expect(validate()).toContain('人工确认')
    expect(row.unit).toBe('M2')
  })
})

describe('制造投入表格真实字段交互', () => {
  it('填写一组后另一组显示灰色—，清空最后值即时解锁；只读派生无编辑框', async () => {
    const input = reactive({ ...material, baseQty: undefined as number | undefined, moduleQty: undefined as number | undefined, pitchMm: null as number | null, pcsPerPitch: 1 })
    const NumberInput = defineComponent({
      props: ['modelValue', 'disabled'], emits: ['update:modelValue', 'change'],
      template: `<input type="number" :value="modelValue" :disabled="disabled" @input="$emit('update:modelValue', $event.target.value === '' ? undefined : Number($event.target.value)); $emit('change')" />`,
    })
    wrapper = mount(ManufacturingBomItemTable, {
      props: { modelValue: [input], outputUnit: 'PCS' },
      global: { stubs: {
        ElTable: Table,
        ElTableColumn: defineComponent({ props: ['label'], setup(props, { slots }) {
          return () => h('div', { 'data-label': props.label }, slots.default?.({ row: input, $index: 0 }))
        } }),
        ElInputNumber: NumberInput, ElButton: Slot, ElCheckbox: true, ElTag: true, ElTooltip: Slot,
        BomMaterialSelector: true, MaterialFormDialog: true,
      } },
    })
    const cell = (label: string) => wrapper.get(`[data-label="${label}"]`)
    await cell('跳距(mm)').get('input').setValue(80)
    expect(input.pitchMm).toBe(80)
    expect(cell('模数').find('input').exists()).toBe(false)
    expect(cell('模数').text()).toBe('—')
    expect(cell('基数').text()).toBe('—')
    await cell('跳距(mm)').get('input').setValue('')
    expect(cell('模数').find('input').exists()).toBe(true)
    await cell('模数').get('input').setValue(6)
    await cell('基数').get('input').setValue(1)
    expect(cell('跳距(mm)').text()).toBe('—')
    expect(cell('每跳片数').text()).toBe('—')
    await cell('模数').get('input').setValue('')
    expect(cell('跳距(mm)').find('input').exists()).toBe(false)
    await cell('基数').get('input').setValue('')
    expect(cell('跳距(mm)').find('input').exists()).toBe(true)
    for (const label of ['档案规格', '单位', '每件用量', '总需求']) expect(cell(label).find('input').exists()).toBe(false)
    await wrapper.setProps({ readonly: true })
    expect(cell('跳距(mm)').get('input').attributes()).toHaveProperty('disabled')
    expect(input.quantity).toBe(1)
  })
})
