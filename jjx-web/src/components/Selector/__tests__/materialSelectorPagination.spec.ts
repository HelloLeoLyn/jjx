/**
 * MaterialSelector 真实组件回归（dev-20261024-018 复核修复）
 * 覆盖复核缺陷：①分页总数误用当前页条数 ②失败无提示/重试 ③空关键词浏览失败重试不发请求（见 useMaterialSearch.spec）。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/popover/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('@/api/inventory/material', () => ({
  materialApi: { search: vi.fn() },
}))

import { materialApi } from '@/api/inventory/material'
import MaterialSelector from '../MaterialSelector.vue'
import EditableSearchSelect from '../EditableSearchSelect.vue'

const page = (ids: number[], total: number) => ({
  data: {
    records: ids.map((id) => ({ materialId: id, materialName: '物料' + id, materialCode: 'C' + id })),
    total,
  },
})

function mountSelector(props: Record<string, unknown> = {}) {
  return mount(MaterialSelector, {
    attachTo: document.body,
    props: { modelValue: null, debounceDelay: 10, minKeywordLength: 1, ...props },
  })
}

describe('MaterialSelector 分页与失败状态', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(materialApi.search).mockReset()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('总数取后端 total，并可通过 load-more 取下一页', async () => {
    vi.mocked(materialApi.search)
      .mockResolvedValueOnce(page([1, 2], 25) as any)
      .mockResolvedValueOnce(page([3], 25) as any)
    const wrapper = mountSelector()
    wrapper.findComponent(EditableSearchSelect).vm.$emit('search', 'PET 008')
    await vi.advanceTimersByTimeAsync(30)

    const select = wrapper.findComponent(EditableSearchSelect)
    expect(select.props('total')).toBe(25)
    expect(materialApi.search).toHaveBeenCalledWith({ keyword: 'PET 008', pageNum: 1, pageSize: 10 })

    select.vm.$emit('load-more')
    await vi.advanceTimersByTimeAsync(30)
    expect(materialApi.search).toHaveBeenLastCalledWith({ keyword: 'PET 008', pageNum: 2, pageSize: 10 })
    expect(wrapper.emitted('search')).toBeTruthy()
    wrapper.unmount()
  })

  it('查询失败向外暴露 failed 并保留重试入口，重试后恢复', async () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.mocked(materialApi.search)
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce(page([9], 1) as any)
    const wrapper = mountSelector()
    wrapper.findComponent(EditableSearchSelect).vm.$emit('search', 'PET')
    await vi.advanceTimersByTimeAsync(30)
    const select = wrapper.findComponent(EditableSearchSelect)
    expect(select.props('failed')).toBe(true)

    // 打开下拉，footer 的失败提示与重试按钮渲染在 teleport 容器里
    await select.find('input').trigger('focus')
    await nextTick()
    expect(document.body.innerHTML).toContain('查询失败')
    const retry = Array.from(document.body.querySelectorAll('button')).find((button) =>
      (button.textContent || '').includes('重试')
    )
    expect(retry).toBeTruthy()
    retry!.click()
    await vi.advanceTimersByTimeAsync(30)
    expect(select.props('failed')).toBe(false)
    error.mockRestore()
    wrapper.unmount()
  })
})
