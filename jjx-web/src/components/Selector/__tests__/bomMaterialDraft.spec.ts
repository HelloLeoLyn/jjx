import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
vi.mock('@/api/inventory/material', () => ({ materialApi: { search: vi.fn() } }))
vi.mock('@/directives', () => ({ hasPermi: () => true }))
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
