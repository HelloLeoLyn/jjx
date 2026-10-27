import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import ProductSpecPreview from '../ProductSpecPreview.vue'
import WorkSpecPaper from '../WorkSpecPaper.vue'
import { normalizeWorkSpec } from '@/api/product/workSpec'
import { makePages, workSpecFlowSource, type DocsetData } from '../docset'
import { defaultWorkSpecFontSettings } from '../workSpecFont'
import { parseWorkSpecCellFonts } from '../workSpecCellFont'

vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/theme-chalk/base.css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox-group/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/link/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/collapse/style/css', () => ({}))
vi.mock('element-plus/es/components/collapse-item/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('@/utils/request', () => ({ default: {} }))
vi.mock('../useWorkSpecOverflow', () => ({ useWorkSpecOverflow: () => ({ measure: vi.fn() }) }))
const mocks = vi.hoisted(() => ({ load: vi.fn() }))
vi.mock('../docset', async original => ({
  ...(await original<typeof import('../docset')>()), loadDocset: mocks.load, loadDocsetForVersion: mocks.load,
}))
vi.mock('@/components/product/ProductFilePreview.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/print/PrintToolbar.vue', () => ({ default: { template: '<div><slot name="actions" /></div>' } }))

const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); vi.restoreAllMocks() })
function fixture(): DocsetData {
  return { product: { productCode: 'CELL-FONT' }, bom: { items: [] }, routing: { items: [
    { itemId: 41, majorCategory: 'ASSEMBLY', processCategory: 'PANEL', workInstruction: '文字工序' },
    { itemId: 42, majorCategory: 'ASSEMBLY', processCategory: 'PANEL', workInstruction: '另一格' },
  ] }, workSpec: normalizeWorkSpec({}), files: [], warnings: [] }
}

describe('单格字号的纸张与分页标识', () => {
  it.each(['A', 'B'] as const)('%s模板只覆盖指定工序文字，图形尺寸仍继承列设置', template => {
    const data = fixture()
    data.routing.items[0].icon = '面板'
    const page = template === 'A' ? makePages(data, ['spec'], [], {}, {}, 'A')[0] : workSpecFlowSource(data)
    const wrapper = mount(WorkSpecPaper, { props: { data, page, pageIndex: 0, pageCount: 1,
      specTemplate: template, fontSettings: defaultWorkSpecFontSettings(), cellFonts: { 'flow0-id-41': 8 } } })
    wrappers.push(wrapper)
    expect(wrapper.get('[data-work-spec-cell="flow0-id-41"]').attributes('data-cell-font')).toBe('8')
    expect(wrapper.get('[data-work-spec-cell="flow0-id-42"]').attributes('data-cell-font')).toBe('12')
    expect(wrapper.get('svg').attributes('width')).toBe('12')
  })
  it('A续页与B连续测量源引用相同工序键', () => {
    const data = fixture()
    data.routing.items = Array.from({ length: 16 }, (_, i) => ({ itemId: i + 100, majorCategory: 'ASSEMBLY', processCategory: 'PANEL', workInstruction: `工序${i}` }))
    for (const [template, page] of [['A', makePages(data, ['spec'], [], {}, {}, 'A')[1]], ['B', workSpecFlowSource(data)]] as const) {
      const wrapper = mount(WorkSpecPaper, { props: { data, page, pageIndex: 1, pageCount: 2,
        specTemplate: template, fontSettings: defaultWorkSpecFontSettings(), cellFonts: { 'flow0-id-114': 7 } } })
      wrappers.push(wrapper)
      expect(wrapper.get('[data-work-spec-cell="flow0-id-114"]').attributes('data-cell-font')).toBe('7')
    }
  })
})

// 模拟B分页后的静态HTML，验证父级事件代理而非Vue子节点监听。
const SheetsStub = defineComponent({
  name: 'ProductSpecSheets', props: ['pages', 'fontSettings', 'cellFonts'], emits: ['layout'],
  template: `<div class="doc-sheets"><div v-html="'<table><tr><td data-work-spec-cell=&quot;flow0-id-41&quot; data-cell-label=&quot;面板第1道作业流程&quot; tabindex=&quot;0&quot;><span>文字工序</span></td><td data-work-spec-cell=&quot;flow0-id-42&quot; data-cell-label=&quot;面板第2道作业流程&quot; tabindex=&quot;0&quot;>另一格</td></tr></table>'" /></div>`,
})
async function preview(initialCellFontSettings?: string) {
  mocks.load.mockResolvedValue(fixture())
  const wrapper = mount(ProductSpecPreview, { props: { productId: 42, initialSections: ['spec'], initialCellFontSettings },
    global: { stubs: { ProductSpecSheets: SheetsStub } } })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}
describe('预览选格、复原与打印设置传递', () => {
  it('静态分页HTML可选格、键盘切换，覆盖不串格；单格复原及全部复原都生效', async () => {
    const wrapper = await preview()
    const sheets = wrapper.findComponent(SheetsStub)
    await wrapper.get('[data-work-spec-cell="flow0-id-41"] span').trigger('click')
    let input = wrapper.get('.cell-font-control').findComponent({ name: 'ElInputNumber' })
    expect(input.props('modelValue')).toBe(12)
    input.vm.$emit('update:modelValue', 8)
    await flushPromises()
    expect(sheets.props('cellFonts')).toEqual({ 'flow0-id-41': 8 })
    await wrapper.get('[data-work-spec-cell="flow0-id-42"]').trigger('keydown', { key: 'Enter' })
    input = wrapper.get('.cell-font-control').findComponent({ name: 'ElInputNumber' })
    expect(input.props('modelValue')).toBe(12)
    input.vm.$emit('update:modelValue', 9)
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text() === '该格恢复默认')!.trigger('click')
    expect(sheets.props('cellFonts')).toEqual({ 'flow0-id-41': 8 })
    await wrapper.findAll('button').find(b => b.text() === '恢复默认')!.trigger('click')
    expect(sheets.props('cellFonts')).toEqual({})
  })
  it('打印窗口收到相同单格设置，重开预览保留；更换产品清除上个产品覆盖', async () => {
    const wrapper = await preview('flow0-id-41:8')
    const sheets = wrapper.findComponent(SheetsStub)
    sheets.vm.$emit('layout', 'spec-0', [])
    await flushPromises()
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)
    await wrapper.findAll('button').find(b => b.text() === '打印所选文档')!.trigger('click')
    expect(open).toHaveBeenCalledTimes(1)
    const params = new URL(String(open.mock.calls[0][0]), 'https://jjx.test').searchParams
    expect(parseWorkSpecCellFonts(params.get('cellFonts'))).toEqual({ 'flow0-id-41': 8 })
    const reopened = await preview(params.get('cellFonts')!)
    expect(reopened.findComponent(SheetsStub).props('cellFonts')).toEqual({ 'flow0-id-41': 8 })
    await wrapper.setProps({ productId: 43, initialCellFontSettings: undefined })
    await flushPromises()
    expect(wrapper.findComponent(SheetsStub).props('cellFonts')).toEqual({})
  })
})
