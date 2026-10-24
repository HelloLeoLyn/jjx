import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/popover/style/css', () => ({}))
vi.mock('@/api/inventory/material', () => ({ materialApi: { search: vi.fn() } }))
vi.mock('@/directives', () => ({ hasPermi: vi.fn(() => true) }))
import { hasPermi } from '@/directives'
import { materialApi } from '@/api/inventory/material'
import BomMaterialSelector from '../BomMaterialSelector.vue'
import EditableSearchSelect from '../EditableSearchSelect.vue'

afterEach(() => { vi.useRealTimers(); document.body.innerHTML = '' })
const mountSelector = (confirmSelection = true) => mount(BomMaterialSelector, {
  attachTo: document.body, props: { materialId: 5, materialName: '旧物料', confirmSelection },
})

describe('BOM搜索草稿与确认隔离', () => {
  it('失焦保留搜索文本但明确未确认，查询失败仍不改变旧业务物料', async () => {
    vi.useFakeTimers()
    vi.mocked(materialApi.search).mockRejectedValueOnce(new Error('网络失败'))
    const log = vi.spyOn(console, 'error').mockImplementation(() => {})
    const wrapper = mountSelector()
    const input = wrapper.get('input')
    await input.trigger('focus')
    await input.setValue('新搜索')
    await input.trigger('blur')
    await vi.advanceTimersByTimeAsync(450)
    expect((input.element as HTMLInputElement).value).toBe('新搜索')
    expect(wrapper.emitted('draft-change')?.at(-1)).toEqual([{ text: '新搜索', pending: true }])
    expect(wrapper.emitted('select')).toBeUndefined()
    await input.trigger('focus')
    expect(document.body.textContent).toContain('搜索文字尚未确认')
    wrapper.unmount(); log.mockRestore()
  })
  it('取消恢复原文，选择候选同步清除草稿并发出完整物料，显式清空不复用旧值', async () => {
    const wrapper = mountSelector()
    const input = wrapper.get('input')
    await input.trigger('focus')
    await input.setValue('草稿')
    ;(wrapper.vm as any).cancelDraft()
    await nextTick()
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('旧物料')
    expect(wrapper.emitted('draft-change')?.at(-1)).toEqual([{ text: '旧物料', pending: false }])
    const candidate = { materialId: 9, materialName: '新物料', specification: '新规格', unit: 'KG' }
    wrapper.getComponent(EditableSearchSelect).vm.$emit('select', candidate)
    expect(wrapper.emitted('select')?.at(-1)).toEqual([candidate])
    expect(wrapper.emitted('draft-change')?.at(-1)?.[0]).toEqual({ text: '新物料', pending: false })
    wrapper.getComponent(EditableSearchSelect).vm.$emit('clear')
    expect(wrapper.emitted('clear')).toHaveLength(1)
    expect(wrapper.emitted('draft-change')?.at(-1)).toEqual([{ text: '', pending: false }])
    wrapper.unmount()
  })
  it('公共选择器默认不发草稿通知；BOM显式启用后IME期间阻止旧候选被回车选中', async () => {
    const baseline = mountSelector(false)
    await baseline.get('input').setValue('搜索')
    expect(baseline.emitted('draft-change')).toBeUndefined()
    baseline.unmount()
    const wrapper = mount(EditableSearchSelect, { attachTo: document.body,
      props: { trackInput: true, displayValue: '旧名', modelValue: 5, options: [{ id: 5, name: '旧候选' }] } })
    const input = wrapper.get('input')
    await input.trigger('focus')
    await input.trigger('compositionstart')
    await input.setValue('拼音')
    await input.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('select')).toBeUndefined()
    expect(wrapper.emitted('draft')?.at(-1)?.[0]).toMatchObject({ composing: true })
    ;(input.element as HTMLInputElement).value = '拼音'
    await input.trigger('compositionend')
    await nextTick(); await nextTick()
    expect(wrapper.emitted('draft')?.at(-1)).toEqual([{ text: '拼音', composing: false }])
    wrapper.unmount()
  })
})

function deferredPage() {
  let resolve!: (value: any) => void
  const promise = new Promise<any>(done => { resolve = done })
  return { promise, resolve }
}
const page = (records: any[], total = records.length) => ({ data: { records, total } })
const PET = { materialId: 11, materialName: 'PET', specification: '008', materialCode: 'MAT-PET', unit: 'M' }

describe('公共物料搜索：多关键词、分页和行间隔离', () => {
  it('连续输入只查询最后的标准化关键词，BOM默认仍只回显名称', async () => {
    vi.useFakeTimers()
    vi.mocked(materialApi.search).mockReset().mockResolvedValue(page([PET]) as any)
    const wrapper = mountSelector(false)
    const input = wrapper.get('input')
    await input.setValue('PET'); await input.setValue(' PET　 008 ')
    await vi.advanceTimersByTimeAsync(399)
    expect(materialApi.search).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    expect(materialApi.search).toHaveBeenCalledExactlyOnceWith({ keyword: 'PET 008', pageNum: 1, pageSize: 10 })
    ;(document.body.querySelector('.ess-option') as HTMLElement).click()
    await nextTick()
    expect(wrapper.emitted('select')?.[0]).toEqual([PET])
    expect((input.element as HTMLInputElement).value).toBe('PET')
    wrapper.unmount()
  })

  it('新关键词立即作废在途旧请求，两行响应不互相覆盖；卸载不回写', async () => {
    vi.useFakeTimers()
    const slow = deferredPage(), fast = deferredPage(), second = deferredPage()
    vi.mocked(materialApi.search).mockReset()
      .mockReturnValueOnce(slow.promise).mockReturnValueOnce(second.promise).mockReturnValueOnce(fast.promise)
    const first = mountSelector(false), other = mountSelector(false)
    await first.get('input').setValue('旧PET')
    await other.get('input').setValue('另一行')
    await vi.advanceTimersByTimeAsync(400)
    await first.get('input').setValue('PET 008')
    slow.resolve(page([{ materialId: 1, materialName: '旧结果' }]))
    await vi.advanceTimersByTimeAsync(400)
    fast.resolve(page([PET]))
    second.resolve(page([{ materialId: 2, materialName: '另一行' }]))
    await vi.advanceTimersByTimeAsync(0)
    expect(first.getComponent(EditableSearchSelect).props('options')).toEqual([PET])
    expect(other.getComponent(EditableSearchSelect).props('options')).toEqual([{ materialId: 2, materialName: '另一行' }])
    expect(first.emitted('select')).toBeUndefined()
    const late = deferredPage()
    vi.mocked(materialApi.search).mockReturnValueOnce(late.promise)
    await first.get('input').setValue('删除行')
    await vi.advanceTimersByTimeAsync(400)
    first.unmount(); late.resolve(page([PET]))
    await vi.advanceTimersByTimeAsync(0)
    expect(other.emitted('select')).toBeUndefined()
    other.unmount()
  })

  it('加载更多沿用PET 008并去重，重复点击只发一次，清空使旧页失效', async () => {
    vi.useFakeTimers()
    vi.mocked(materialApi.search).mockReset().mockResolvedValueOnce(page([PET], 3) as any)
    const wrapper = mountSelector(false)
    await wrapper.get('input').setValue('PET 008')
    await vi.advanceTimersByTimeAsync(400)
    const pending = deferredPage()
    vi.mocked(materialApi.search).mockReturnValueOnce(pending.promise)
    const select = wrapper.getComponent(EditableSearchSelect)
    select.vm.$emit('load-more'); select.vm.$emit('load-more')
    expect(materialApi.search).toHaveBeenCalledTimes(2)
    expect(materialApi.search).toHaveBeenLastCalledWith({ keyword: 'PET 008', pageNum: 2, pageSize: 10 })
    pending.resolve(page([PET, { ...PET, materialId: 12 }], 3))
    await vi.advanceTimersByTimeAsync(0)
    expect(select.props('options')).toHaveLength(2)
    const late = deferredPage()
    vi.mocked(materialApi.search).mockReturnValueOnce(late.promise)
    select.vm.$emit('load-more'); select.vm.$emit('clear')
    late.resolve(page([{ ...PET, materialId: 13 }], 3))
    await vi.advanceTimersByTimeAsync(0)
    expect(select.props('options')).toEqual([])
    expect(wrapper.emitted('clear')).toHaveLength(1)
    expect(wrapper.emitted('select')).toBeUndefined()
    wrapper.unmount()
  })

  it('工作台可按原权限建档，BOM清单/批量显式禁用；空词浏览不改变BOM默认行为', async () => {
    vi.useFakeTimers()
    vi.mocked(materialApi.search).mockReset().mockResolvedValue(page([]) as any)
    const wrapper = mount(BomMaterialSelector, { props: {
      materialId: 11, materialName: 'PET', specification: '008', materialCode: 'MAT-PET',
      showMaterialCode: true, browseOnFocus: true, createPermissions: ['engineering:sample:workbench'],
    } })
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('PET · 008 (MAT-PET)')
    await wrapper.get('input').trigger('focusin')
    await vi.advanceTimersByTimeAsync(400)
    expect(materialApi.search).toHaveBeenLastCalledWith({ keyword: '', pageNum: 1, pageSize: 10 })
    await wrapper.get('input').setValue('新材料')
    await vi.advanceTimersByTimeAsync(400)
    expect(wrapper.getComponent(EditableSearchSelect).props('showCreate')).toBe(true)
    await wrapper.setProps({ allowCreate: false })
    expect(wrapper.getComponent(EditableSearchSelect).props('showCreate')).toBe(false)
    wrapper.unmount()
    vi.mocked(materialApi.search).mockClear()
    const bom = mountSelector(false)
    await bom.get('input').trigger('focusin')
    await vi.advanceTimersByTimeAsync(400)
    expect(materialApi.search).not.toHaveBeenCalled()
    bom.unmount()
  })
})


it('没有工作台权限时不显示建档入口', async () => {
  vi.useFakeTimers()
  vi.mocked(hasPermi).mockReturnValue(false)
  vi.mocked(materialApi.search).mockReset().mockResolvedValue(page([]) as any)
  const wrapper = mount(BomMaterialSelector, { props: { createPermissions: ['engineering:sample:workbench'] } })
  try {
    await wrapper.get('input').setValue('新PET')
    await vi.advanceTimersByTimeAsync(400)
    expect(hasPermi).toHaveBeenLastCalledWith(['engineering:sample:workbench'])
    expect(wrapper.getComponent(EditableSearchSelect).props('showCreate')).toBe(false)
  } finally {
    wrapper.unmount()
    vi.mocked(hasPermi).mockReturnValue(true)
  }
})
