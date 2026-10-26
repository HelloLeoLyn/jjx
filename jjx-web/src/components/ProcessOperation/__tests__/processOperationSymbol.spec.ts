import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

// 仓库 vitest 未把 element-plus inline，外部化 style/css 会走 Node 加载器
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/popover/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
// 直接 mock 掉真实角标组件，避免把 element-plus 组件样式拉进测试依赖
vi.mock('@/components/IconStepBadge/index.vue', () => ({
  default: { name: 'IconStepBadge', template: '<span class="badge-stub" />' },
}))

import ProcessOperation from '../index.vue'

/** dev-20261026-006：空 icon = 文字符号工序，符号取作业说明，不再显示「工」占位。 */
function mountItems(items: any[]) {
  return mount(ProcessOperation, {
    props: { items, draggable: false, editable: false },
    global: { stubs: { IconStepBadge: { template: '<span class="badge-stub" />' } } },
  })
}

describe('ProcessOperation 文字符号工序', () => {
  it('空 icon 且有作业说明：显示作业说明文本，不显示工占位', () => {
    const wrapper = mountItems([{ key: 'a', icon: '', workInstruction: '冲窗口灯孔' }])
    expect(wrapper.text()).toContain('冲窗口灯孔')
    expect(wrapper.text()).not.toContain('工')
    wrapper.unmount()
  })

  it('空 icon 且作业说明为空：不渲染符号也不破格', () => {
    const wrapper = mountItems([{ key: 'b', icon: '', workInstruction: '  ' }])
    expect(wrapper.find('.symbol-text').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('工')
    wrapper.unmount()
  })

  it('有 icon：仍渲染图标，不出现文字符号', () => {
    const wrapper = mountItems([{ key: 'c', icon: 'icon-cut', workInstruction: '冲切' }])
    expect(wrapper.find('.badge-stub').exists()).toBe(true)
    expect(wrapper.find('.symbol-text').exists()).toBe(false)
    wrapper.unmount()
  })
})
