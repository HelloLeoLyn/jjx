import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import RoutePrintStyleFields from '../RoutePrintStyleFields.vue'
import { printingSearchApi } from '@/api/engineering/printingSearch'
vi.mock('@/api/engineering/printingSearch', () => ({ printingSearchApi: { plate: vi.fn() } }))
const sources = [{ filmId: 21, filmNo: 'FL-A', artworkNo: 'F001', versionNo: 'V1', versionId: 11,
  styles: [{ styleNo: 'A', name: '正面文字', inkColor: 'RED' }, { styleNo: 'B', name: '指示图标', inkColor: 'BLUE' }] }]
const raw = JSON.stringify({ meshMin: 100, styles: [{ versionId: 11, styleNo: 'A', inkColor: 'SPECIAL' }, { versionId: 11, styleNo: 'B', inkColor: 'BLUE' }] })
const wrappers: ReturnType<typeof mount>[] = []
function render(props: Record<string, unknown> = {}) {
  const w = mount(RoutePrintStyleFields, { props: { plateId: 4, sourcesJson: JSON.stringify(sources), requirements: raw, ...props },
    slots: { legacy: '<input placeholder="原录入资料" />' }, global: { directives: { loading: {} } } })
  wrappers.push(w); return w
}
beforeEach(() => { vi.clearAllMocks(); vi.mocked(printingSearchApi.plate).mockResolvedValue({ data: { plateId: 4, sourcesJson: JSON.stringify(sources) } } as any) })
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))
describe('路线印刷样式前端预览', () => {
  it('真实样式名称自动带出，色号覆盖保留，油墨缺口明示且不伪造新字段', async () => {
    const w = render(); await flushPromises()
    expect(w.emitted('update:name')!.at(-1)).toEqual(['正面文字 / 指示图标'])
    expect(w.text()).toContain('F001 V1'); expect(w.text()).toContain('待关联')
    expect(w.find('input[placeholder="样式色号"]').element).toHaveProperty('value', 'SPECIAL')
    await w.find('input[placeholder="样式色号"]').setValue('LOCAL')
    const value = JSON.parse(w.emitted('update:requirements')!.at(-1)![0] as string)
    expect(value.meshMin).toBe(100); expect(value.styles[0].inkColor).toBe('LOCAL')
    expect(value.styles[0]).not.toHaveProperty('inkNo'); expect(value.styles[0]).not.toHaveProperty('name')
    expect(sources[0].styles[0].inkColor).toBe('RED'); expect(printingSearchApi.plate).not.toHaveBeenCalled()
  })
  it('取消样式只修改现有requirements字段，名称随选择变化', async () => {
    const Host = defineComponent({ components: { RoutePrintStyleFields }, setup() { return { value: ref(raw), sources: JSON.stringify(sources), name: ref('') } },
      template: '<RoutePrintStyleFields :plate-id="4" :sources-json="sources" v-model:requirements="value" @update:name="name=$event" />' })
    const w = mount(Host, { global: { directives: { loading: {} } } }); wrappers.push(w); await flushPromises()
    await w.findAll('input[type="checkbox"]')[1].setValue(false); await flushPromises()
    expect((w.vm as any).name).toBe('正面文字')
    expect(JSON.parse((w.vm as any).value).styles).toEqual([{ versionId: 11, styleNo: 'A', inkColor: 'SPECIAL' }])
  })
  it('旧资料未记录样式时保留原录入，不清空旧名称', async () => {
    const w = render({ sourcesJson: '[]' }); await flushPromises()
    expect(w.find('input[placeholder="原录入资料"]').exists()).toBe(true)
    expect(w.emitted('update:name')).toBeUndefined(); expect(w.text()).toContain('未记录样式')
  })
  it('编辑重开读取真实网版，失败显示重试，关闭或换版后旧响应不回填', async () => {
    vi.mocked(printingSearchApi.plate).mockRejectedValueOnce(new Error('offline'))
    const w = render({ sourcesJson: undefined }); await flushPromises()
    expect(w.text()).toContain('加载失败')
    await w.findAll('button').find(button => button.text() === '重试')!.trigger('click'); await flushPromises()
    expect(w.text()).toContain('正面文字')
    let finish!: (value: any) => void
    vi.mocked(printingSearchApi.plate).mockReturnValueOnce(new Promise(resolve => { finish = resolve }) as any)
    await w.setProps({ plateId: 5 }); await w.setProps({ plateId: null })
    finish({ data: { plateId: 5, sourcesJson: JSON.stringify(sources) } }); await flushPromises()
    expect(w.text()).not.toContain('正面文字'); expect(w.find('input[placeholder="原录入资料"]').exists()).toBe(true)
  })
})
