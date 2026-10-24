import { describe, expect, it } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import MaterialSizeEditor from '../MaterialSizeEditor.vue'

const Input = defineComponent({ props: ['modelValue'], emits: ['update:modelValue'],
  template: `<input :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />` })
const Select = defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' })
function create(value: string) {
  return mount(MaterialSizeEditor, { props: { modelValue: value },
    global: { stubs: { ElInput: Input, ElSelect: Select, ElOption: true } } })
}
describe('专用尺寸编辑组件', () => {
  it('打开回显不改写原值，修改厚度写回同一个尺寸字段', async () => {
    const wrapper = create('30*90mm')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    const inputs = wrapper.findAll('input')
    expect(inputs.map(input => input.element.value)).toEqual(['30', '90', ''])
    await inputs[2].setValue('0.175')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['30*90*0.175mm'])
    wrapper.unmount()
  })
  it('无效输入阻止保存，改正后恢复；历史未知原文原样保留', async () => {
    const wrapper = create('30*90mm')
    await wrapper.findAll('input')[0].setValue('0')
    expect(wrapper.emitted('valid-change')?.at(-1)).toEqual([false])
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    await wrapper.findAll('input')[0].setValue('30')
    expect(wrapper.emitted('valid-change')?.at(-1)).toEqual([true])
    wrapper.unmount()
    const legacy = create('按图纸尺寸')
    expect(legacy.find('input').element.value).toBe('按图纸尺寸')
    expect(legacy.emitted('update:modelValue')).toBeUndefined()
    await legacy.find('input').setValue('还是无法拆分')
    await legacy.setProps({ modelValue: '还是无法拆分' })
    await nextTick()
    expect(legacy.emitted('valid-change')?.at(-1)).toEqual([false])
    legacy.unmount()
  })
})
