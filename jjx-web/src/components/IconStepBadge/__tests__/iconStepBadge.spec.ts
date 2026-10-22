import { describe, expect, it, vi } from 'vitest'
// 避免 Element Plus 样式在 node 环境下按 .css 解析失败（与既有组件测试同法）
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/popover/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
import { mount } from '@vue/test-utils'
import IconStepBadge from '../index.vue'

const stubs = {
  SvgIcon: true,
  ElPopover: {
    props: ['disabled'],
    template: '<div class="popover-stub"><slot name="reference" /></div>',
  },
  ElInput: true,
  ElInputNumber: true,
  ElButton: true,
}

const factory = (props: Record<string, unknown>) =>
  mount(IconStepBadge, { props, global: { stubs } })

describe('IconStepBadge 作业说明角标（dev-20261022-009）', () => {
  it('有作业说明时角标显示全文，title 也是全文', () => {
    const text = '线路外形 冲窗口灯孔'
    const wrapper = factory({ icon: 'route', workInstruction: text })
    const badge = wrapper.find('.subscript')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe(text)
    expect(badge.attributes('title')).toBe(text)
  })

  it('只有 index 没有作业说明时不显示角标（不再显示数字）', () => {
    const wrapper = factory({ icon: 'route', index: 3 })
    expect(wrapper.find('.subscript').exists()).toBe(false)
  })

  it('作业说明仅空白时也不显示角标', () => {
    const wrapper = factory({ icon: 'route', workInstruction: '   ' })
    expect(wrapper.find('.subscript').exists()).toBe(false)
  })

  it('默认 editable=false：点击图标不弹层、不触发任何更新事件', async () => {
    const wrapper = factory({ icon: 'route', index: 3, workInstruction: '线路外形' })
    await wrapper.find('.icon-step-badge').trigger('click')
    expect(wrapper.emitted('update:index')).toBeUndefined()
    expect(wrapper.emitted('update-description')).toBeUndefined()
    expect(wrapper.emitted('update:work-instruction')).toBeUndefined()
    expect(wrapper.emitted('jump')).toBeUndefined()
  })
})
