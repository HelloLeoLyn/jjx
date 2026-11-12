import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import type { EngineeringBomItem } from '@/types/product/bom'
const api = vi.hoisted(() => ({ previewBomConsumption: vi.fn(), previewPitchRequirement: vi.fn() }))
vi.mock('@/api/production/bomConsumption', () => api)
import BomEntryTools from '../BomEntryTools.vue'
const InputNumber = defineComponent({
  inheritAttrs: false, props: ['modelValue', 'disabled'], emits: ['update:modelValue'],
  template: '<input v-bind="$attrs" type="number" :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value === \'\' ? undefined : Number($event.target.value))" />',
})
const Button = defineComponent({ props: ['disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' })
const Checkbox = defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />' })
const row = (patch: Partial<EngineeringBomItem> = {}): EngineeringBomItem => ({ itemId: 1, materialId: 110, materialCode: 'AUX000106', materialName: '材料', specification: '195mm', unit: 'M2', baseQty: 1, moduleQty: 6, lossRate: 0, minIssueQty: 0, quantity: 77, sortOrder: 1, ...patch })
const quote = (total = '50') => ({ key: '1', valid: true, unit: 'M2', unitConsumption: '0.1667', totalConsumption: total, archiveUnit: 'M2', widthMm: 195, lengthMm: 50000, pitchSupported: true, referenceRollLengthM: 50 })
const response = (total = '50') => ({ data: { readOnly: true, rows: [quote(total)] } })
const wrappers: ReturnType<typeof mount>[] = []
const tick = async () => { await vi.advanceTimersByTimeAsync(260); await flushPromises() }
function tools(rows = [row()], extra: Record<string, unknown> = {}) {
  const wrapper = mount(BomEntryTools, {
    props: { rows, outputUnit: 'PCS', outputLengthMm: 80, ...extra },

    global: { stubs: { ElInputNumber: InputNumber, ElButton: Button, ElCheckbox: Checkbox, ElAlert: { props: ['title'], template: '<div class="alert">{{ title }}</div>' }, ElInput: true } },
  })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  api.previewBomConsumption.mockReset().mockResolvedValue(response())
  api.previewPitchRequirement.mockReset().mockResolvedValue({ data: { moduleQtySuggested: 1, baseQtySuggested: 0.0156, baseQtyStorableInBom: true, displayConsumption: 1.56, unit: 'M2' } })
})
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.useRealTimers() })

describe('正式BOM统一只读试算', () => {
  it('传原始比例和任意产出数量，结果只展示、不覆写BOM快照', async () => {
    const material = row()
    const wrapper = tools([material])
    await tick()
    const state = wrapper.vm as any
    state.outputQuantity = 123.25
    await tick()
    expect(api.previewBomConsumption).toHaveBeenLastCalledWith(expect.objectContaining({ outputQuantity: 123.25, rows: [expect.objectContaining({ baseQty: 1, moduleQty: 6, unit: 'M2' })] }))
    expect(state.resultFor(material).totalConsumption).toBe('50')
    expect(material.quantity).toBe(77)
    expect(wrapper.emitted('change')).toBeUndefined()
  })
  it('比例改变立即清空旧值，忽略旧请求的迟到成功与失败', async () => {
    let resolve!: (value: ReturnType<typeof response>) => void
    api.previewBomConsumption.mockReturnValueOnce(new Promise(done => { resolve = done }))
    const material = row(), wrapper = tools([material])
    await tick()
    await wrapper.setProps({ rows: [{ ...material, moduleQty: 3 }] })
    expect((wrapper.vm as any).resultFor(wrapper.props('rows')[0])).toBeUndefined()
    await tick()
    resolve(response('old'))
    await flushPromises()
    expect((wrapper.vm as any).resultFor(wrapper.props('rows')[0]).totalConsumption).toBe('50')
  })
  it('服务失败不沿用旧值、不把结果当0，草稿保持', async () => {
    const material = row(), wrapper = tools([material]); await tick()
    api.previewBomConsumption.mockRejectedValueOnce(new Error('not available'))
    ;(wrapper.vm as any).outputQuantity = 300
    await tick()
    expect(wrapper.find('.alert').text()).toContain('草稿已保留')
    expect((wrapper.vm as any).resultFor(material)).toBeUndefined()
    expect(material.quantity).toBe(77)
  })
  it('原始跳距直接传给只读试算，默认每跳1片，不发送旧来源或单耗', async () => {
    const material = row({ baseQty: undefined, moduleQty: undefined, pitchMm: 80, pcsPerPitch: null, split: '78mm', consumptionMode: 'continuous', unitConsumption: 99 })
    const wrapper = tools([material]); await tick()
    expect(api.previewBomConsumption).toHaveBeenLastCalledWith(expect.objectContaining({ outputQuantity: 100, rows: [expect.objectContaining({ pitchMm: 80, pcsPerPitch: 1, baseQty: null, moduleQty: null })] }))
    const input = api.previewBomConsumption.mock.calls.at(-1)![0].rows[0]
    expect(input).not.toHaveProperty('consumptionMode')
    expect(input).not.toHaveProperty('unitConsumption')
    expect(wrapper.text()).not.toContain('跳距辅助定额')
    expect(api.previewPitchRequirement).not.toHaveBeenCalled()
    expect(material.split).toBe('78mm')
    expect(material.quantity).toBe(77)
  })
  it('单位不一致立即标红提示人工确认，不藏在高级设置、不改数值', async () => {
    api.previewBomConsumption.mockResolvedValueOnce({ data: { readOnly: true, rows: [{ ...quote(), archiveUnit: 'PCS' }] } })
    const material = row(), wrapper = tools([material]); await tick()
    expect(wrapper.get('.unit-warning').text()).toContain('人工确认')
    expect(material.unit).toBe('M2')
    expect(material.quantity).toBe(77)
  })
  it('只读高级项不可编辑，收起不清值', async () => {
    const material = row({ minIssueQty: 10 }), wrapper = tools([material]); await tick()
    const state = wrapper.vm as any
    state.advancedOpen = true; await flushPromises()
    await wrapper.setProps({ readonly: true })
    expect(wrapper.findAll('input:disabled').length).toBeGreaterThan(0)
    state.advancedOpen = false; await flushPromises()
    expect(material.minIssueQty).toBe(10)
  })
  it('旧请求迟到失败不会抹掉最新结果，卸载后也不回写草稿', async () => {
    let reject!: (error: Error) => void
    api.previewBomConsumption.mockReturnValueOnce(new Promise((_done, fail) => { reject = fail }))
    const material = row(), wrapper = tools([material]); await tick()
    ;(wrapper.vm as any).outputQuantity = 300; await tick()
    reject(new Error('old failed')); await flushPromises()
    expect((wrapper.vm as any).resultFor(material).totalConsumption).toBe('50')
    expect(wrapper.find('.alert').exists()).toBe(false)
    wrapper.unmount()
    expect(material.quantity).toBe(77)
  })

})
