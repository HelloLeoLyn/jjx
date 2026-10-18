/**
 * EditableSearchSelect 组件测试（dev-20261018-011）
 * 覆盖：已选名称可编辑、输入不改业务值、选中新项回填、显式清空、失焦还原、新建入口。
 *
 * 运行（带内存笼子，禁止裸跑）：
 *   cd /home/administrator/jjx/jjx-web
 *   systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- timeout 120 npx vitest run
 *     src/components/Selector/__tests__/editableSearchSelect.spec.ts --maxWorkers=1
 */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

// 仓库 vitest 未把 element-plus 加入 inline，外部化依赖的 style/css 会走 Node 加载器
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/popover/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))

import EditableSearchSelect from '../EditableSearchSelect.vue'

const options = [
  { id: 1, name: '物料A' },
  { id: 2, name: '物料B' },
]

function mountIt(overrides: Record<string, any> = {}) {
  return mount(EditableSearchSelect, {
    attachTo: document.body,
    props: {
      modelValue: 1,
      displayValue: '物料A',
      options,
      optionKey: 'id',
      optionLabel: 'name',
      ...overrides,
    },
  })
}

afterEach(() => {
  document.body.innerHTML = ''
})

describe('EditableSearchSelect', () => {
  it('已选名称显示在输入框内，聚焦后可直接改写并触发 search', async () => {
    const w = mountIt()
    const input = w.find('input')
    expect((input.element as HTMLInputElement).value).toBe('物料A')

    await input.trigger('focus')
    await input.setValue('物料')

    expect((input.element as HTMLInputElement).value).toBe('物料')
    expect(w.emitted('search')?.at(-1)).toEqual(['物料'])
    w.unmount()
  })

  it('输入文本变化不直接改业务值（不 emit update:modelValue）', async () => {
    const w = mountIt()
    const input = w.find('input')
    await input.trigger('focus')
    await input.setValue('物料B')

    expect(w.emitted('update:modelValue')).toBeUndefined()
    expect(w.emitted('clear')).toBeUndefined()
    w.unmount()
  })

  it('选中新项：emit select + update:modelValue（业务键）', async () => {
    const w = mountIt()
    await w.find('input').trigger('focus')
    await nextTick()

    const items = Array.from(document.body.querySelectorAll('.ess-option')) as HTMLElement[]
    expect(items.length).toBe(2)
    items[1].click()
    await nextTick()

    expect(w.emitted('update:modelValue')?.[0]).toEqual([2])
    expect(w.emitted('select')?.[0]?.[0]).toMatchObject({ id: 2, name: '物料B' })
    w.unmount()
  })

  it('显式清空：emit clear + update:modelValue(undefined)', async () => {
    const w = mountIt()
    w.findComponent({ name: 'ElInput' }).vm.$emit('clear')
    await nextTick()

    expect(w.emitted('clear')).toBeTruthy()
    expect(w.emitted('update:modelValue')?.at(-1)).toEqual([undefined])
    w.unmount()
  })

  it('失焦未选中时，文本还原为已选名称', async () => {
    vi.useFakeTimers()
    const w = mountIt()
    const input = w.find('input')
    await input.trigger('focus')
    await input.setValue('乱打')
    await input.trigger('blur')
    vi.advanceTimersByTime(200)
    await nextTick()

    expect((input.element as HTMLInputElement).value).toBe('物料A')
    vi.useRealTimers()
    w.unmount()
  })

  it('新建入口：点击 create 项 emit create(关键字)', async () => {
    const w = mountIt({ showCreate: true, createLabel: '新建物料' })
    const input = w.find('input')
    await input.trigger('focus')
    await input.setValue('物料X')
    await nextTick()

    const createEl = document.body.querySelector('.ess-create') as HTMLElement
    expect(createEl).toBeTruthy()
    createEl.click()

    expect(w.emitted('create')?.[0]).toEqual(['物料X'])
    w.unmount()
  })
})
