import { describe, expect, it, vi } from 'vitest'
import { makePages, type DocsetData } from '../docset'
import { normalizeWorkSpec } from '@/api/product/workSpec'
import WorkSpecPaper from '../WorkSpecPaper.vue'
import { mount } from '@vue/test-utils'
import { defaultWorkSpecFontSettings } from '../workSpecFont'
vi.mock('../useWorkSpecOverflow', () => ({ useWorkSpecOverflow: () => ({ measure: vi.fn() }) }))
function data(count: number): DocsetData {
  return { product: { productCode: 'TEST-007' }, bom: { items: Array.from({ length: count }, (_, i) => ({ itemId: i + 1, materialName: `材料${i + 1}` })) },
    routing: { items: [] }, workSpec: normalizeWorkSpec({ engineeringRequirements: '加工要求', emboss: { setupHeight: '0.25' } }), files: [], warnings: [] }
}
describe('B版使用A版排版并连续材料行', () => {
  it.each([5, 14, 18])('%i行材料连续，右侧六行凹凸及动态备注合并', count => {
    const source = data(count), pages = makePages(source, ['spec'], [], {}, {}, 'B')
    expect(pages).toHaveLength(1)
    expect(pages[0].rows).toHaveLength(count)
    const paper = mount(WorkSpecPaper, { props: { data: source, page: pages[0], pageIndex: 0, pageCount: 1,
      specTemplate: 'B', fontSettings: defaultWorkSpecFontSettings() }, global: { stubs: { SvgIcon: true } } })
    expect(paper.findAll('.materials tbody tr')).toHaveLength(Math.max(14, count))
    expect(paper.findAll('.emboss-label')).toHaveLength(6)
    expect(paper.get('.engineering-notes').attributes('rowspan')).toBe(String(Math.max(14, count) - 6))
    expect(paper.find('.spec-overview').exists()).toBe(false)
    expect(paper.get('.materials thead').text()).toContain('规格及模数')
    paper.unmount()
  })
  it('A仍按14行分页；B材料不重复，超额流程继续分页', () => {
    const source = data(18)
    source.routing.items = Array.from({ length: 16 }, (_, i) => ({ itemId: i + 1, processCategory: 'PANEL' }))
    const a = makePages(source, ['spec'], [], {}, {}, 'A'), b = makePages(source, ['spec'], [], {}, {}, 'B')
    expect(a.map(p => p.rows?.length)).toEqual([14, 4])
    expect(b.map(p => p.rows?.length)).toEqual([18, 0])
    expect(b.map(p => p.groups?.[0].rows.length)).toEqual([14, 2])
    expect(b.every(p => !p.flowHtml && !p.flowPending)).toBe(true)
  })
})
