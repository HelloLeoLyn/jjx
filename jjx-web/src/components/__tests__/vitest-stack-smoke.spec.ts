/**
 * 前端测试栈冒烟（dev-20261016-060）
 *
 * 目的：证明 Vitest + @vue/test-utils + jsdom 具备「真挂载真组件 / 断言真 DOM /
 * 观察 watch 生命周期 / 卸载后不残留定时器与异步回调」的能力 ——
 * 这是附件新方案 C/D 组「测试证据」能否算数的前提。
 *
 * 运行（带内核内存笼子，禁止裸跑）：
 *   cd /home/administrator/jjx/jjx-web
 *   systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- npx vitest run
 * 或 `npm run test`（脚本内已带笼子）。
 *
 * 注意：既有手搓 `node --test` + vm 沙箱的用例（`*.test.cjs` / `*.test.ts`）不在本 runner 收集范围内
 * （并存，逐步迁移）；vitest 新用例一律用 `*.spec.ts`。
 */
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, onBeforeUnmount, ref, watch } from 'vue'
import StatusTag from '@/components/StatusTag.vue'

describe('vitest 测试栈冒烟', () => {
  it('真挂载仓库组件并断言真实 DOM', () => {
    const wrapper = mount(StatusTag, {
      props: { status: '待审核', type: 'warning', size: 'small' },
    })

    expect(wrapper.text()).toContain('待审核')
    expect(wrapper.classes()).toContain('status-tag')
    expect(wrapper.classes()).toContain('status-tag-warning')
    expect(wrapper.classes()).toContain('status-tag-small')
    // jsdom 提供真 DOM：挂载结果真的是一个 span 元素
    expect(wrapper.element.tagName).toBe('SPAN')

    wrapper.unmount()
  })

  it('prop 变化驱动响应式渲染与 watch 生命周期', async () => {
    const seen: string[] = []
    const Probe = defineComponent({
      props: { label: { type: String, required: true } },
      setup(props) {
        watch(
          () => props.label,
          (value) => seen.push(value)
        )
        return () => h('div', { class: 'probe' }, props.label)
      },
    })

    const wrapper = mount(Probe, { props: { label: 'A' } })
    expect(wrapper.text()).toBe('A')

    await wrapper.setProps({ label: 'B' })
    expect(wrapper.text()).toBe('B')
    expect(seen).toEqual(['B'])

    wrapper.unmount()
  })

  it('组件卸载后不残留定时器与异步回调', async () => {
    vi.useFakeTimers()
    try {
      const ticked = ref(0)
      const Ticker = defineComponent({
        setup() {
          const timer = setInterval(() => {
            ticked.value += 1
          }, 100)
          let alive = true
          // 提交后残留的异步链（与报价测试挂死同类的缺陷形态）
          void Promise.resolve().then(() => {
            if (alive) ticked.value += 100
          })
          onBeforeUnmount(() => {
            alive = false
            clearInterval(timer)
          })
          return () => h('span', { class: 'ticker' }, String(ticked.value))
        },
      })

      const baseline = vi.getTimerCount()
      const wrapper = mount(Ticker)
      expect(vi.getTimerCount()).toBe(baseline + 1)

      await vi.advanceTimersByTimeAsync(250)
      expect(ticked.value).toBeGreaterThanOrEqual(102)

      wrapper.unmount()

      // 卸载后：定时器被清、异步回调不再写状态
      expect(vi.getTimerCount()).toBe(baseline)
      const afterUnmount = ticked.value
      await vi.advanceTimersByTimeAsync(1000)
      expect(ticked.value).toBe(afterUnmount)
    } finally {
      vi.useRealTimers()
    }
  })
})
