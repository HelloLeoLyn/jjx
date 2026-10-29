import { describe, expect, it, vi } from 'vitest'
import { makePages, type DocsetData } from '../docset'
import { normalizeWorkSpec } from '@/api/product/workSpec'
import WorkSpecPaper from '../WorkSpecPaper.vue'
import { mount } from '@vue/test-utils'
import { defaultWorkSpecFontSettings } from '../workSpecFont'
vi.mock('../useWorkSpecOverflow', () => ({ useWorkSpecOverflow: () => ({ measure: vi.fn() }) }))

function data(count: number): DocsetData {
  return {
    product: { productCode: 'TEST-007' },
    bom: {
      items: Array.from({ length: count }, (_, i) => ({ itemId: i + 1, materialName: '材料' + (i + 1) })),
    },
    routing: { items: [] },
    workSpec: normalizeWorkSpec({ engineeringRequirements: '加工要求', emboss: { setupHeight: '0.25' } }),
    files: [],
    warnings: [],
  }
}

describe('B版使用A版排版并连续材料行（现口径：flowHtml/flowPending）', () => {
  it.each([5, 14, 18])('%i行材料在B版只出一页 flow 占位，不再走 A 版 rows 分页', count => {
    const pages = makePages(data(count), ['spec'], [], {}, {}, 'B')
    expect(pages).toHaveLength(1)
    expect(pages[0].section).toBe('spec')
    expect(pages[0].kind).toBe('spec')
    // 未测量前是 pending 占位；关键是不能再按材料行拆页（旧 rows/groups 已废弃）
    expect(pages[0].flowPending).toBe(true)
    expect(pages[0].rows).toBeUndefined()
    expect(pages[0].groups).toBeUndefined()
  })

  it('B版按测量结果逐页铺 flowHtml，材料不重复、不额外拆页', () => {
    const measured = ['<div class="flow">材料1 材料2</div>', '<div class="flow">材料3</div>']
    const pages = makePages(data(3), ['spec'], [], {}, {}, 'B', measured)
    expect(pages).toHaveLength(2)
    expect(pages.map(page => page.flowHtml)).toEqual(measured)
    expect(pages.every(page => !page.rows && !page.groups)).toBe(true)
    // 两页拼起来材料各出现一次（不重复）
    const merged = pages.map(page => page.flowHtml).join('')
    for (const name of ['材料1', '材料2', '材料3']) {
      expect(merged.split(name)).toHaveLength(2)
    }
  })

  it('A版仍按14行分页；材料不重复、工序组合继续分页', () => {
    const source = data(18)
    source.routing.items = Array.from({ length: 16 }, (_, i) => ({ itemId: i + 1, processCategory: 'PANEL' }))
    const pages = makePages(source, ['spec'], [], {}, {}, 'A')
    const specPages = pages.filter(page => page.kind === 'spec')
    expect(specPages.map(page => page.rows?.length)).toEqual([14, 4])
    expect(specPages.map(page => page.groups?.[0].rows.length)).toEqual([14, 2])
    const names = specPages.flatMap(page => (page.rows || []).map((row: any) => row.materialName))
    expect(names).toEqual(Array.from({ length: 18 }, (_, i) => '材料' + (i + 1)))
    expect(new Set(names).size).toBe(18)
  })

  it('A版右侧六行凹凸与加工要求合并行仍成立', () => {
    const source = data(18)
    const page = makePages(source, ['spec'], [], {}, {}, 'A').find(item => item.kind === 'spec')!
    const paper = mount(WorkSpecPaper, {
      props: {
        data: source,
        page,
        pageIndex: 0,
        pageCount: 2,
        specTemplate: 'A',
        fontSettings: defaultWorkSpecFontSettings(),
      },
      global: { stubs: { SvgIcon: true } },
    })
    expect(paper.findAll('.materials tbody tr')).toHaveLength(14)
    expect(paper.findAll('.emboss-label')).toHaveLength(6)
    expect(paper.get('.engineering-notes').attributes('rowspan')).toBe('8')
    expect(paper.find('.spec-overview').exists()).toBe(false)
    expect(paper.get('.materials thead').text()).toContain('规格及模数')
    paper.unmount()
  })
})
