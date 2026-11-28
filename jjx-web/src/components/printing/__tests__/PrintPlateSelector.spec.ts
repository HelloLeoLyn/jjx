import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import PrintResourceSelector from '@/components/Selector/PrintResourceSelector.vue'
import EditableSearchSelect from '@/components/Selector/EditableSearchSelect.vue'
import { printingSearchApi } from '@/api/engineering/printingSearch'
vi.mock('@/api/engineering/printingSearch', () => ({ printingSearchApi: { search: vi.fn() } }))
afterEach(() => { vi.useRealTimers(); vi.clearAllMocks() })
describe('实体网版选择器', () => {
  it('按网版ID区分同版本的不同实体；传递场景到服务端，只有可用实体才确认选择', async () => {
    vi.useFakeTimers()
    const rows = [
      { plateId: 1, versionId: 3, plateNo: 'P1', availability: true },
      { plateId: 2, versionId: 3, plateNo: 'P2', availability: false },
    ]
    vi.mocked(printingSearchApi.search).mockResolvedValue({ data: { records: rows, total: 2 } } as any)
    const wrapper = mount(PrintResourceSelector, { props: { scene: 'PLATE', modelValue: rows[0] } })
    const select = wrapper.findComponent(EditableSearchSelect)
    expect(select.props('modelValue')).toBe(1); expect(select.props('optionKey')).toBe('plateId')
    select.vm.$emit('search', 'FRAME-001')
    await vi.advanceTimersByTimeAsync(300); await flushPromises()
    expect(printingSearchApi.search).toHaveBeenCalledWith({ q: 'FRAME-001', pageNum: 1, pageSize: 10, scene: 'PLATE' })
    select.vm.$emit('select', rows[1]); expect(wrapper.emitted('select')).toBeUndefined()
    select.vm.$emit('select', { versionId: 3 }); expect(wrapper.emitted('select')).toBeUndefined()
    select.vm.$emit('select', rows[0]); expect(wrapper.emitted('select')![0]).toEqual([rows[0]])
    select.vm.$emit('clear'); expect(wrapper.emitted('update:modelValue')!.at(-1)).toEqual([null])
    wrapper.unmount()
  })
})
