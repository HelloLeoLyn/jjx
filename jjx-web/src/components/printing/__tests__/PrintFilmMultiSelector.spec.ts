import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import PrintFilmMultiSelector from '../PrintFilmMultiSelector.vue'
import { printingSearchApi } from '@/api/engineering/printingSearch'
import { printingArtworkApi } from '@/api/product/film'
import type { PlateSource } from '../printStyles'

vi.mock('@/api/engineering/printingSearch', () => ({ printingSearchApi: { search: vi.fn() } }))
vi.mock('@/api/product/film', () => ({ printingArtworkApi: { detail: vi.fn() } }))
const first = { artworkId: 11, artworkNo: 'F001', name: '面板菲林', versionId: 21, versionNo: 'V1', filmId: 31, filmNo: 'FL-001', filmStatus: 'AVAILABLE', versionStatus: 'RELEASED' }
const second = { artworkId: 12, artworkNo: 'F002', name: '按键菲林', versionId: 22, versionNo: 'V2', filmId: 32, filmNo: 'FL-002', filmStatus: 'AVAILABLE', versionStatus: 'RELEASED' }
const styles = [{ styleNo: 'A', name: '正面', inkColor: 'RED' }, { styleNo: 'B', name: '背面', inkColor: 'BLUE' }]
const detail = (id: number) => {
  const row = id === 11 ? first : second
  return { artworkId: row.artworkId, artworkNo: row.artworkNo, name: row.name,
    versions: [{ versionId: row.versionId, versionNo: row.versionNo, status: 'RELEASED', stylesJson: JSON.stringify(styles) }],
    films: [{ filmId: row.filmId, filmNo: row.filmNo, versionId: row.versionId, status: 'AVAILABLE' }] }
}
const wrappers: ReturnType<typeof mount>[] = []
function render() {
  const Host = defineComponent({ components: { PrintFilmMultiSelector }, setup() { return { sources: ref<PlateSource[]>([]) } }, template: '<PrintFilmMultiSelector v-model="sources" />' })
  const w = mount(Host, { global: { directives: { loading: {} } } })
  wrappers.push(w)
  return w
}
async function search(w: ReturnType<typeof mount>, keyword = '菲林') {
  await w.find('input[placeholder="搜索菲林名称、版本或实体片编号，勾选可直接加入"]').setValue(keyword)
  await vi.advanceTimersByTimeAsync(300); await flushPromises()
}
beforeEach(() => {
  vi.useFakeTimers(); vi.clearAllMocks()
  vi.mocked(printingSearchApi.search).mockResolvedValue({ data: { records: [first, second, first], total: 3 } } as any)
  vi.mocked(printingArtworkApi.detail).mockImplementation(async id => ({ data: detail(id) }) as any)
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.useRealTimers() })

describe('制版实体菲林直接多选', () => {
  it('连续勾选两片立即进入已选列表，默认全选样式且同一实体去重', async () => {
    const w = render(); await search(w)
    const checks = w.findAll('input[type="checkbox"]')
    expect(checks).toHaveLength(2)
    await checks[0].setValue(true); await flushPromises()
    await checks[1].setValue(true); await flushPromises()
    expect((w.vm as any).sources.map((row: PlateSource) => row.filmId)).toEqual([31, 32])
    expect((w.vm as any).sources[0].styleNos).toEqual(['A', 'B'])
    expect((w.vm as any).sources[0]).toMatchObject({ artworkName: '面板菲林', versionNo: 'V1', filmNo: 'FL-001' })
    expect(w.text()).not.toContain('加入本次制版')
  })
  it('换关键词和加载分页保留已选，取消勾选立即移除', async () => {
    vi.mocked(printingSearchApi.search).mockResolvedValueOnce({ data: { records: Array.from({ length: 10 }, () => first), total: 11 } } as any)
    const w = render(); await search(w)
    await w.find('input[type="checkbox"]').setValue(true); await flushPromises()
    vi.mocked(printingSearchApi.search).mockResolvedValueOnce({ data: { records: [second], total: 11 } } as any)
    await w.findAll('button').find(button => button.text() === '加载更多')!.trigger('click'); await flushPromises()
    expect(printingSearchApi.search).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 2 }))
    expect(w.findAll('input[type="checkbox"]')).toHaveLength(2)
    await w.findAll('input[type="checkbox"]')[1].setValue(true); await flushPromises()
    vi.mocked(printingSearchApi.search).mockResolvedValueOnce({ data: { records: [second], total: 1 } } as any)
    await search(w, 'FL-002')
    expect((w.vm as any).sources).toHaveLength(2)
    expect((w.find('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(true)
    await w.find('input[type="checkbox"]').setValue(false); await flushPromises()
    expect((w.vm as any).sources.map((row: PlateSource) => row.filmId)).toEqual([31])
  })
  it('搜索失败可重试，样式详情失败不加入且可重新勾选重试', async () => {
    vi.mocked(printingSearchApi.search).mockRejectedValueOnce(new Error('offline'))
    const w = render(); await search(w)
    await w.findAll('button').find(button => button.text().includes('查询失败'))!.trigger('click'); await flushPromises()
    vi.mocked(printingArtworkApi.detail).mockRejectedValueOnce(new Error('详情连接失败'))
    await w.find('input[type="checkbox"]').setValue(true); await flushPromises()
    expect((w.vm as any).sources).toHaveLength(0); expect(w.text()).toContain('详情连接失败')
    await w.find('input[type="checkbox"]').setValue(true); await flushPromises()
    expect((w.vm as any).sources).toHaveLength(1)
  })
  it('没有版本样式或详情身份不符时不加入，草稿/非实体结果不展示', async () => {
    vi.mocked(printingSearchApi.search).mockResolvedValueOnce({ data: { records: [first, { ...second, versionStatus: 'DRAFT' }, { ...second, filmId: undefined }], total: 3 } } as any)
    const w = render(); await search(w)
    expect(w.findAll('input[type="checkbox"]')).toHaveLength(1)
    vi.mocked(printingArtworkApi.detail).mockResolvedValueOnce({ data: { ...detail(11), versions: [{ ...detail(11).versions[0], stylesJson: '[]' }] } } as any)
    await w.find('input[type="checkbox"]').setValue(true); await flushPromises()
    expect(w.text()).toContain('尚未定义样式'); expect((w.vm as any).sources).toHaveLength(0)
    const other = render(); await search(other)
    vi.mocked(printingArtworkApi.detail).mockResolvedValueOnce({ data: { ...detail(11), films: [{ ...detail(11).films[0], versionId: 99 }] } } as any)
    await other.find('input[type="checkbox"]').setValue(true); await flushPromises()
    expect(other.text()).toContain('已不可用'); expect((other.vm as any).sources).toHaveLength(0)
  })
  it('连续两次详情同时返回也不会互相覆盖', async () => {
    let a!: (value: any) => void, b!: (value: any) => void
    vi.mocked(printingArtworkApi.detail).mockImplementation(id => new Promise(resolve => { if (id === 11) a = resolve; else b = resolve }) as any)
    const w = render(); await search(w)
    await w.findAll('input[type="checkbox"]')[0].setValue(true)
    await w.findAll('input[type="checkbox"]')[1].setValue(true)
    a({ data: detail(11) }); b({ data: detail(12) }); await flushPromises()
    expect((w.vm as any).sources).toHaveLength(2)
  })
  it('输入变化立即丢弃旧搜索响应，关闭后迟到详情不会回填', async () => {
    let finish!: (value: any) => void
    vi.mocked(printingSearchApi.search).mockReturnValueOnce(new Promise(resolve => { finish = resolve }) as any)
    const w = render(); await search(w, '旧词')
    await w.find('input[placeholder="搜索菲林名称、版本或实体片编号，勾选可直接加入"]').setValue('新词')
    finish({ data: { records: [first], total: 1 } }); await flushPromises()
    expect(w.findAll('input[type="checkbox"]')).toHaveLength(0)
    await vi.advanceTimersByTimeAsync(300); await flushPromises()
    let resolveDetail!: (value: any) => void
    vi.mocked(printingArtworkApi.detail).mockReturnValueOnce(new Promise(resolve => { resolveDetail = resolve }) as any)
    const picker = w.findComponent(PrintFilmMultiSelector)
    await w.find('input[type="checkbox"]').setValue(true)
    w.unmount(); resolveDetail({ data: detail(11) }); await flushPromises()
    expect(picker.emitted('update:modelValue')).toBeUndefined()
  })
})
