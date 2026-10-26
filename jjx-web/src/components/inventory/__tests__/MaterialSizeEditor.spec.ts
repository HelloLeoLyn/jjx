import { describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import MaterialSizeEditor from '../MaterialSizeEditor.vue'

// 仓库 vitest 未把 element-plus 加入 server.deps.inline，样式副作用入口会走 Node 加载器
// 报 Unknown file extension ".css"；这里只 mock 样式入口，组件仍真实挂载（同其它既有用例）。
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))

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
  it('打开历史符号自动收敛为半角*，已是标准值不重复写回', async () => {
    const legacy = create('300×305mm')
    expect(legacy.emitted('update:modelValue')?.at(-1)).toEqual(['300*305mm'])
    legacy.unmount()
    const letter = create('300x305')
    expect(letter.emitted('update:modelValue')?.at(-1)).toEqual(['300*305'])
    letter.unmount()
    const canonical = create('300*305mm')
    expect(canonical.emitted('update:modelValue')).toBeUndefined()
    canonical.unmount()
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
