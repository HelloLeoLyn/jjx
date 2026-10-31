import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import ProductSpecPreview from '../ProductSpecPreview.vue'
import WorkSpecPaper from '../WorkSpecPaper.vue'
import { normalizeWorkSpec } from '@/api/product/workSpec'
import { makePages, workSpecFlowSource, type DocsetData } from '../docset'
import {
  defaultWorkSpecFontSettings,
  parseWorkSpecFontSettings,
  serializeWorkSpecFontSettings,
  workSpecFontAreas,
} from '../workSpecFont'
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
  it.each(['A', 'B'] as const)('%s模板只覆盖指定工序文字，图形尺寸按独立图标设置', template => {
    const data = fixture()
    data.routing.items[0].icon = '面板'
    const page = template === 'A' ? makePages(data, ['spec'], [], {}, {}, 'A')[0] : workSpecFlowSource(data)
    const wrapper = mount(WorkSpecPaper, { props: { data, page, pageIndex: 0, pageCount: 1,
      specTemplate: template, fontSettings: defaultWorkSpecFontSettings(), cellFonts: { 'flow0-id-41': 8 } } })
    wrappers.push(wrapper)
    expect(wrapper.get('[data-work-spec-cell="flow0-id-41"]').attributes('data-cell-font')).toBe('8')
    expect(wrapper.get('[data-work-spec-cell="flow0-id-42"]').attributes('data-cell-font')).toBe('12')
    expect(wrapper.get('svg').attributes('width')).toBe('18')
  })
  it('图标尺寸独立：改文字字号不动图标，改图标项才生效', () => {
    const data = fixture()
    data.routing.items[0].icon = '面板'
    const settings = defaultWorkSpecFontSettings()
    const page = workSpecFlowSource(data)
    const paper = (fontSettings: typeof settings) =>
      mount(WorkSpecPaper, { props: { data, page, pageIndex: 0, pageCount: 1,
        specTemplate: 'B', fontSettings } })
    const textOnly = paper({ ...settings, flow0OperationContent: 7 })
    wrappers.push(textOnly)
    expect(textOnly.get('svg').attributes('width')).toBe('18')
    expect(textOnly.get('[data-work-spec-cell="flow0-id-41"]').attributes('data-cell-font')).toBe('7')
    const iconOnly = paper({ ...settings, flow0Icon: 24 })
    wrappers.push(iconOnly)
    expect(iconOnly.get('svg').attributes('width')).toBe('24')
  })
  it('材料表、加工要求、结构图、页脚都有单格键，默认取列字号，覆盖后按格生效', () => {
    const data = fixture()
    data.bom.items = [{
      materialCode: 'M1', materialName: '面材', specification: '100*200mm', quantity: 1, unit: 'PCS',
      dieLocation: '位置1', sortOrder: 0, processName: '面板',
    }]
    const settings = defaultWorkSpecFontSettings()
    const page = makePages(data, ['spec'], [], {}, {}, 'A')[0]
    const wrapper = mount(WorkSpecPaper, { props: { data, page, pageIndex: 0, pageCount: 1,
      specTemplate: 'A', fontSettings: settings, cellFonts: { 'mat0-name': 9, note: 10, footer: 11 } } })
    wrappers.push(wrapper)
    const fontOf = (key: string) => wrapper.get(`[data-work-spec-cell="${key}"]`).attributes('data-cell-font')
    expect(fontOf('mat0-number')).toBe(String(settings.materialNumberContent))
    expect(fontOf('mat0-spec')).toBe(String(settings.materialSpecContent))
    expect(fontOf('mat0-die')).toBe(String(settings.materialDieContent))
    expect(fontOf('mat0-name')).toBe('9')
    expect(fontOf('note')).toBe('10')
    expect(fontOf('change')).toBe(String(settings.notes))
    expect(fontOf('struct-title')).toBe(String(settings.structureTitle))
    expect(fontOf('footer')).toBe('11')
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

describe('材料表默认字号', () => {
  it('项目内容默认 18，序号/规格及模数 13，其余内容列 12（dev-20261031-005）', () => {
    const settings = defaultWorkSpecFontSettings()
    expect(settings.materialProjectContent).toBe(18)
    expect(settings.materialNumberContent).toBe(13)
    expect(settings.materialSpecContent).toBe(13)
    expect([settings.materialNameContent, settings.materialDieContent, settings.materialEmbossValueContent])
      .toEqual([12, 12, 12])
    // 标题字号不受内容列默认值影响
    expect(settings.materialProjectTitle).toBe(14)
  })

  it('旧打印链接里的项目内容字号仍按链接值解析，不被新默认覆盖', () => {
    const override = serializeWorkSpecFontSettings({ ...defaultWorkSpecFontSettings(), materialProjectContent: 12 })
    expect(parseWorkSpecFontSettings(override).materialProjectContent).toBe(12)
    expect(parseWorkSpecFontSettings(null).materialProjectContent).toBe(18)
  })
})

describe('图标尺寸设置项', () => {
  it('默认18px，序列化为v5可回读；v4旧链接图标回落默认', () => {
    const settings = defaultWorkSpecFontSettings()
    expect([
      settings.materialProjectIcon, settings.flow0Icon, settings.flow1Icon, settings.flow2Icon,
    ]).toEqual([18, 18, 18, 18])
    const text = serializeWorkSpecFontSettings({ ...settings, flow0Icon: 24, flow0OperationContent: 9 })
    expect(text.startsWith('v5,')).toBe(true)
    expect(parseWorkSpecFontSettings(text)).toMatchObject({ flow0Icon: 24, flow0OperationContent: 9 })
    const legacyCount = workSpecFontAreas.length - 4
    const restored = parseWorkSpecFontSettings('v4,' + Array(legacyCount).fill(12).join(','))
    expect(restored.title).toBe(12)
    expect(restored.flow0Icon).toBe(18)
  })
})

// 模拟B分页后的静态HTML，验证父级事件代理而非Vue子节点监听。
const SheetsStub = defineComponent({
  name: 'ProductSpecSheets', props: ['pages', 'fontSettings', 'cellFonts'], emits: ['layout'],
  template: `<div class="doc-sheets"><div v-html="'<table><tr><td data-work-spec-cell=&quot;flow0-id-41&quot; data-cell-label=&quot;面板第1道作业流程&quot; tabindex=&quot;0&quot;><span>文字工序</span></td><td data-work-spec-cell=&quot;flow0-id-42&quot; data-cell-label=&quot;面板第2道作业流程&quot; tabindex=&quot;0&quot;>另一格</td></tr><tr><td data-work-spec-cell=&quot;mat0-name&quot; data-cell-label=&quot;材料第1项 · 材料&quot; data-cell-font=&quot;10&quot;><span class=&quot;material-name&quot;>面材</span></td></tr></table>'" /></div>`,
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
  it('材料表格子同样可选可复原，默认字号取该格当前字号', async () => {
    const wrapper = await preview()
    const sheets = wrapper.findComponent(SheetsStub)
    await wrapper.get('[data-work-spec-cell="mat0-name"] .material-name').trigger('click')
    const input = wrapper.get('.cell-font-control').findComponent({ name: 'ElInputNumber' })
    expect(input.props('modelValue')).toBe(10)
    input.vm.$emit('update:modelValue', 9)
    await flushPromises()
    expect(sheets.props('cellFonts')).toEqual({ 'mat0-name': 9 })
    await wrapper.findAll('button').find(b => b.text() === '该格恢复默认')!.trigger('click')
    expect(sheets.props('cellFonts')).toEqual({})
  })
  it('打印窗口收到相同单格设置，重开预览保留；更换产品清除上个产品覆盖', async () => {
    const fonts = { 'flow0-id-41': 8, 'mat0-name': 10, note: 9 }
    const wrapper = await preview('flow0-id-41:8;mat0-name:10;note:9')
    const sheets = wrapper.findComponent(SheetsStub)
    sheets.vm.$emit('layout', 'spec-0', [])
    await flushPromises()
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)
    await wrapper.findAll('button').find(b => b.text() === '打印所选文档')!.trigger('click')
    expect(open).toHaveBeenCalledTimes(1)
    const params = new URL(String(open.mock.calls[0][0]), 'https://jjx.test').searchParams
    expect(parseWorkSpecCellFonts(params.get('cellFonts'))).toEqual(fonts)
    const reopened = await preview(params.get('cellFonts')!)
    expect(reopened.findComponent(SheetsStub).props('cellFonts')).toEqual(fonts)
    await wrapper.setProps({ productId: 43, initialCellFontSettings: undefined })
    await flushPromises()
    expect(wrapper.findComponent(SheetsStub).props('cellFonts')).toEqual({})
  })
})
