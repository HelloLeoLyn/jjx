import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, inject, provide, type Ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import RollReceiptPlanDialog from '../RollReceiptPlanDialog.vue'

const mocks = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn(), success: vi.fn(), warning: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: { post: mocks.post, get: mocks.get } }))
vi.mock('element-plus', async original => ({ ...await original<typeof import('element-plus')>(),
  ElMessage: { success: mocks.success, warning: mocks.warning } }))
const Slot = defineComponent({ template: '<div><slot /><slot name="footer" /></div>' })
const Table = defineComponent({ props: ['data'], setup(props) { provide('testRows', computed(() => props.data)); }, template: '<div><slot /></div>' })
const Column = defineComponent({ setup() { return { rows: inject<Ref<any[]>>('testRows') }; }, template: '<div><div v-for="(row, index) in rows" :key="index"><slot :row="row" :$index="index" /></div></div>' })
const Input = defineComponent({ props: ['modelValue','disabled'], emits: ['update:modelValue'],
  template: `<input type="text" :disabled="disabled" :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />` })
const NumberInput = defineComponent({ props: ['modelValue','disabled'], emits: ['update:modelValue','change'],
  template: `<input type="number" :disabled="disabled" :value="modelValue" @input="$emit('update:modelValue', Number($event.target.value)); $emit('change')" />` })
const Button = defineComponent({ emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' })
const stubs = { ElDialog: Slot, ElDescriptions: Slot, ElDescriptionsItem: Slot, ElTable: Table, ElTableColumn: Column,
  ElInput: Input, ElInputNumber: NumberInput, ElButton: Button }
const receipt = () => ({ inboundItemId: '101', materialCode: 'AUX-TEST', materialName: '卷材', quantity: 29.25,
  acceptedQuantity: 14.625, rollPlan: [{ rollNo: 'P1', widthMm: 195, lengthM: 50, area: 9.75 },
    { rollNo: 'P2', widthMm: 195, lengthM: 50, area: 9.75 }] } as any)
beforeEach(() => { vi.clearAllMocks(); mocks.post.mockResolvedValue({ data: true }); mocks.get.mockResolvedValue({ data: 4.875 }) })
describe('原采购明细多卷计划', () => {
  it('保留原收货和IQC允收，编辑副本，面积预览来自API，保存只传原始尺寸', async () => {
    const item = receipt()
    const w = mount(RollReceiptPlanDialog, { props: { modelValue: false, item }, global: { stubs } })
    await w.setProps({ modelValue: true }); await flushPromises()
    await w.findAll('input[type=number]')[2].setValue('25'); await flushPromises()
    expect(mocks.get).toHaveBeenCalledWith('/inventory/roll/area', { params: { widthMm: 195, lengthM: 25 } })
    expect(w.text()).toContain('14.6250㎡')
    expect(item.quantity).toBe(29.25); expect(item.rollPlan[0].lengthM).toBe(50)
    await w.findAll('button').find(b => b.text() === '保存卷计划')!.trigger('click'); await flushPromises()
    expect(mocks.post).toHaveBeenCalledWith('/inventory/inbound/roll-plan/101', { rolls: [
      { rollNo: 'P1', widthMm: 195, lengthM: 25 }, { rollNo: 'P2', widthMm: 195, lengthM: 50 },
    ] })
    expect(w.emitted('success')).toHaveLength(1)
    w.unmount()
  })
  it('已过账计划只读，不提供保存或增加卷的动作', async () => {
    const w = mount(RollReceiptPlanDialog, { props: { modelValue: false, item: receipt(), readonly: true }, global: { stubs } })
    await w.setProps({ modelValue: true }); await flushPromises()
    expect(w.findAll('button').map(b => b.text())).toEqual(['关闭'])
    expect(w.findAll('input').every(input => input.attributes('disabled') !== undefined)).toBe(true)
    expect(mocks.post).not.toHaveBeenCalled()
    w.unmount()
  })
})
