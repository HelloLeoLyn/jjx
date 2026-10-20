import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { productRouteApi } from '@/api/product/routing'
import RouteRemarkAutocomplete from '../RouteRemarkAutocomplete.vue'

vi.mock('@/api/product/routing', () => ({ productRouteApi: { suggestRemarks: vi.fn() } }))
vi.mock('element-plus/es/components/autocomplete/style/css', () => ({}))
vi.mock('element-plus/es/components/base/style/css', () => ({}))
const Autocomplete = defineComponent({
  name: 'ElAutocomplete',
  props: ['modelValue', 'fetchSuggestions'],
  emits: ['update:modelValue'],
  template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
})
function editor() {
  return mount(RouteRemarkAutocomplete, {
    props: { modelValue: '已有备注', processCategory: 'PANEL', processIds: [3, 20] },
    global: { stubs: { ElAutocomplete: Autocomplete } },
  })
}
beforeEach(() => vi.clearAllMocks())
describe('路线父行备注联想', () => {
  it('查询传上下文、只展示候选，不改已有文字；选后仍可自由修改', async () => {
    vi.mocked(productRouteApi.suggestRemarks).mockResolvedValue({ data: ['粘合后检查'] } as any)
    const w = editor()
    const cb = vi.fn()
    await w.findComponent(Autocomplete).props('fetchSuggestions')('粘', cb)
    expect(productRouteApi.suggestRemarks).toHaveBeenCalledWith({
      processCategory: 'PANEL', processIds: [3, 20], keyword: '粘',
    })
    expect(cb).toHaveBeenCalledWith([{ value: '粘合后检查' }])
    expect(w.emitted('update:modelValue')).toBeUndefined()
    await w.find('input').setValue('粘合后检查')
    await w.find('input').setValue('粘合后检查边缘')
    expect(w.emitted('update:modelValue')?.at(-1)).toEqual(['粘合后检查边缘'])
  })
  it('无候选或查询失败仍允许输入，不触发任何保存请求', async () => {
    const w = editor()
    const cb = vi.fn()
    vi.mocked(productRouteApi.suggestRemarks).mockResolvedValue({ data: [] } as any)
    await w.findComponent(Autocomplete).props('fetchSuggestions')('', cb)
    expect(cb).toHaveBeenLastCalledWith([])
    vi.mocked(productRouteApi.suggestRemarks).mockRejectedValue(new Error('offline'))
    await w.findComponent(Autocomplete).props('fetchSuggestions')('任意', cb)
    expect(cb).toHaveBeenLastCalledWith([])
    await w.find('input').setValue('自定义备注')
    expect(w.emitted('update:modelValue')?.at(-1)).toEqual(['自定义备注'])
  })
  it('上下文变化时丢弃旧响应', async () => {
    let resolve!: (value: any) => void
    vi.mocked(productRouteApi.suggestRemarks).mockReturnValue(new Promise(r => { resolve = r }))
    const w = editor()
    const cb = vi.fn()
    const pending = w.findComponent(Autocomplete).props('fetchSuggestions')('', cb)
    await w.setProps({ processIds: [4] })
    resolve({ data: ['旧上下文'] })
    await pending
    await flushPromises()
    expect(cb).toHaveBeenCalledWith([])
    expect(w.emitted('update:modelValue')).toBeUndefined()
  })
})
