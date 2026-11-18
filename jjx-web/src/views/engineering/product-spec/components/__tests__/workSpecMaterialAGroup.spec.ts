import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
vi.mock('@/utils/request', () => ({ default: {} }))
import { makePages, workSpecFlowSource, type DocsetData } from '../docset'
import WorkSpecPaper from '../WorkSpecPaper.vue'
import { defaultWorkSpecFontSettings } from '../workSpecFont'
import { normalizeWorkSpec } from '@/api/product/workSpec'

;(globalThis as any).ResizeObserver = class { observe() {} disconnect() {} }
;(document as any).fonts = { ready: Promise.resolve(), addEventListener() {}, removeEventListener() {} }
const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))
const item = (id: number, processId: number, processName = `项目${processId}`) => ({ itemId: id, processId, processName, materialId: id, materialName: `材料${id}`, specification: '205mm*440mm', moduleQty: 6, baseQty: 1, unit: 'PCS', dieLocation: `刀模${id}` })
const data = (items: Record<string, any>[]): DocsetData => ({ product: {}, bom: { items }, routing: { items: [] }, workSpec: normalizeWorkSpec({}), files: [], warnings: [] } as DocsetData)
function paper(source: DocsetData, page: any, index = 0) {
  const wrapper = mount(WorkSpecPaper, { props: { data: source, page, pageIndex: index, pageCount: 2, specTemplate: 'A', fontSettings: defaultWorkSpecFontSettings() }, global: { stubs: { SvgIcon: true } } })
  wrappers.push(wrapper)
  return wrapper
}
describe('A版序号项目与B版同规则合并（dev-20261118-004）', () => {
  it('五条材料显示四个项目组，背胶共享序号2与项目格，规格/刀模仍逐行对应', () => {
    const source = data([item(8, 17), item(9, 20, '面板背胶'), item(10, 20, '面板背胶'), item(11, 76), item(12, 77)])
    const before = JSON.stringify(source), page = makePages(source, ['spec'], [], {}, {}, 'A')[0]
    expect(page.rows!.map(row => [row.projectNo, row.projectSpan])).toEqual([[1, 1], [2, 2], [2, 0], [3, 1], [4, 1]])
    expect(page.rows!.map(row => [row.projectNo, row.projectSpan])).toEqual(workSpecFlowSource(source).rows!.map(row => [row.projectNo, row.projectSpan]))
    expect(JSON.stringify(source)).toBe(before)
    const wrapper = paper(source, page)
    const numbered = wrapper.findAll('td.material-number').filter(cell => cell.text())
    expect(numbered.map(cell => [cell.text(), cell.attributes('rowspan')])).toEqual([['1', '1'], ['2', '2'], ['3', '1'], ['4', '1']])
    expect(wrapper.get('td.material-project[rowspan="2"]').text()).toBe('面板背胶')
    expect(wrapper.findAll('[data-work-spec-cell$="-spec"]').slice(0, 5).map(cell => cell.text())).toEqual(Array(5).fill('205mm*440mm=6PCS'))
    expect(wrapper.findAll('td.die-position').slice(0, 5).map(cell => cell.text())).toEqual(['刀模8', '刀模9', '刀模10', '刀模11', '刀模12'])
    expect(wrapper.find('.materials .engineering-notes').exists()).toBe(true)
    expect(wrapper.find('.materials .emboss-label').exists()).toBe(true)
  })
  it('跨第14材料行时每页独立合并，续页沿用同一项目序号，后续项目继续递增', () => {
    const items = [...Array.from({ length: 13 }, (_, i) => item(i + 1, i + 1)), item(14, 99), item(15, 99), item(16, 99), item(17, 100)]
    const source = data(items), pages = makePages(source, ['spec'], [], {}, {}, 'A')
    expect(pages).toHaveLength(2)
    expect(pages[0].rows![13]).toMatchObject({ projectNo: 14, projectSpan: 1 })
    expect(pages[1].rows!.map(row => [row.projectNo, row.projectSpan])).toEqual([[14, 2], [14, 0], [15, 1]])
    const next = paper(source, pages[1], 1)
    expect(next.get('td.material-number[rowspan="2"]').text()).toBe('14')
    expect(next.findAll('td.material-number').filter(cell => cell.text()).map(cell => cell.text())).toEqual(['14', '15'])
    expect(pages[1].diePositionRows).toEqual(['刀模15', '刀模16', '刀模17'])
  })
  it('整组超过一页仍沿用组号，每页的rowspan不越过本页材料数', () => {
    const pages = makePages(data(Array.from({ length: 30 }, (_, i) => item(i + 1, 20))), ['spec'], [], {}, {}, 'A')
    expect(pages.map(page => page.rows![0].projectSpan)).toEqual([14, 14, 2])
    expect(pages.every(page => page.rows!.every(row => row.projectNo === 1))).toBe(true)
  })
  it('非连续同项目不跨材料合并；显式分属两组的同名项目也不误合并', () => {
    const separated = makePages(data([item(1, 20), item(2, 17), item(3, 20)]), ['spec'], [], {}, {}, 'A')[0]
    expect(separated.rows!.map(row => [row.projectNo, row.projectSpan])).toEqual([[1, 1], [2, 1], [3, 1]])
    const explicit = makePages(data([item(1, 20), { ...item(2, 20), parentMaterialId: 1 }, item(3, 20)]), ['spec'], [], {}, {}, 'A')[0]
    expect(explicit.rows!.map(row => [row.projectNo, row.projectSpan])).toEqual([[1, 2], [1, 0], [2, 1]])
  })
})
