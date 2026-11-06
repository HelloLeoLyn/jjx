import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import WorkSpecPaper from '../WorkSpecPaper.vue'
import { makePages, workSpecFlowSource, type DocsetData } from '../docset'
import { normalizeWorkSpec } from '@/api/product/workSpec'
import { defaultWorkSpecFontSettings } from '../workSpecFont'

vi.mock('@/utils/request', () => ({ default: {} }))

// jsdom 没有 ResizeObserver / document.fonts：纸张测量钩子会用到（与其它纸张用例同口径）
;(globalThis as any).ResizeObserver = class {
  observe() {}
  unobserve() {}
  disconnect() {}
}
;(document as any).fonts = { ready: Promise.resolve(), addEventListener() {}, removeEventListener() {} }

const item = (over: Record<string, unknown> = {}) => ({
  itemId: 1,
  processName: '面板',
  materialCode: 'M1',
  materialName: '面材',
  specification: '100*200mm',
  widthMm: 100,
  lengthMm: 200,
  moduleQty: 3,
  baseQty: 1,
  unit: 'PCS',
  dieLocation: '下左',
  processCategory: 'PANEL',
  sortOrder: 0,
  ...over,
})

function fixture(): DocsetData {
  return {
    product: {},
    bom: { items: [item()] },
    routing: { items: [] },
    workSpec: normalizeWorkSpec({ engineeringRequirements: '加工要求：公差±0.1；注意方向' }),
    files: [],
    warnings: [],
  } as unknown as DocsetData
}

const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
})

function paper(template: 'A' | 'B', continuation = 0) {
  const data = fixture()
  const page =
    template === 'B'
      ? { ...workSpecFlowSource(data), continuation }
      : { ...makePages(data, ['spec'], [], {}, {}, 'A')[0], continuation }
  const wrapper = mount(WorkSpecPaper, {
    props: {
      data,
      page,
      pageIndex: continuation,
      pageCount: 1,
      specTemplate: template,
      fontSettings: defaultWorkSpecFontSettings(),
    },
    global: { stubs: { SvgIcon: true } },
  })
  wrappers.push(wrapper)
  return wrapper
}

describe('B 版材料表拆列与两条横条（dev-20261031-007）', () => {
  it('批准章仅在批准后显示，并使用批准日期而非发行日期', async () => {
    const wrapper = paper('B')
    expect(wrapper.find('svg.approval').exists()).toBe(false)
    const data = fixture()
    data.workSpec.approved = true
    data.workSpec.confirmedAt = '2026-11-06 10:00:00'
    data.workSpec.issueDate = '2026-11-01'
    await wrapper.setProps({ data })
    const stamp = wrapper.get('svg.approval')
    expect(stamp.findAll('text').map((node) => node.text())).toEqual([
      '工程部', '26-11-06', '已批准',
    ])
    expect(wrapper.get('footer').text()).toContain('26-11-01')
    data.workSpec.confirmedAt = undefined
    await wrapper.setProps({ data: { ...data, workSpec: { ...data.workSpec } } })
    expect(wrapper.get('.approval-date').text()).toBe('26-11-01')
  })

  it('B 版表头拆成 规格/模数/基数，材料表里不再有凹凸列与加工要求块', () => {
    const wrapper = paper('B')
    const head = wrapper.get('.materials thead').text()
    expect(head).toContain('规格')
    expect(head).toContain('模数')
    expect(head).toContain('基数')
    expect(head).not.toContain('规格及模数')
    expect(head).not.toContain('凹凸')
    expect(wrapper.find('.materials .engineering-notes').exists()).toBe(false)
    expect(wrapper.find('.materials .emboss-label').exists()).toBe(false)
    expect(wrapper.get('td.material-module').text()).toBe('3')
    // 基数直接取 BOM baseQty，等于 1 也要显示（不再留空）
    expect(wrapper.get('td.material-base').text()).toBe('1')
  })

  it('凹凸条件横条：6 格 3×2，首页出现、续页不出现', () => {
    const first = paper('B', 0)
    const strip = first.get('.emboss-strip')
    expect(strip.get('.strip-label').text()).toBe('凹凸')
    // 与材料表同一套表格列宽：首列 5% 对齐序号列，不是 flex + 百分比
    expect(strip.find('colgroup col').attributes('style')).toContain('width: 5%')
    expect(first.findAll('.emboss-item')).toHaveLength(6)
    expect(strip.text()).toContain('调机高度')
    expect(paper('B', 1).find('.emboss-strip').exists()).toBe(false)
  })

  it('备注横条在工时下方、变更内容上方，文字取加工要求', () => {
    const wrapper = paper('B', 0)
    const strip = wrapper.get('.notes-strip')
    expect(strip.get('.strip-label').text()).toBe('备注')
    expect(strip.find('colgroup col').attributes('style')).toContain('width: 5%')
    expect(strip.text()).toContain('加工要求：公差±0.1')
    const classes = wrapper.findAll('article > *').map((node) => node.classes().join('.'))
    expect(classes.indexOf('notes-strip')).toBeGreaterThan(classes.indexOf('hours'))
    expect(classes.indexOf('notes-strip')).toBeLessThan(classes.indexOf('issuance'))
  })

  it('A 版不受影响：加工要求仍在材料表右侧、没有两条新横条', () => {
    const wrapper = paper('A', 0)
    expect(wrapper.find('.materials .engineering-notes').exists()).toBe(true)
    expect(wrapper.find('.emboss-strip').exists()).toBe(false)
    expect(wrapper.find('.notes-strip').exists()).toBe(false)
    expect(wrapper.get('.materials thead').text()).toContain('规格及模数')
  })

  it('B 版材料表与流程表都有默认高度：材料不足 12 行补空行，工序不足按 12/4/12 补', () => {
    const wrapper = paper('B', 0)
    // 只有 1 条材料，仍要占满 12 行——否则空行会塌掉，纸面看起来只剩有内容的几行
    expect(wrapper.findAll('.materials tbody tr')).toHaveLength(12)
    expect(wrapper.findAll('.materials .material-name').filter((span) => span.text())).toHaveLength(1)
    const flowRows = wrapper.findAll('.flows tbody tr')
    expect(flowRows).toHaveLength(12)
    // 上线列被结构图占 8 行，只剩 4 行可放工序
    const lastRow = flowRows[flowRows.length - 1]
    expect(lastRow.findAll('td')).toHaveLength(9)
  })

  it('B 版材料超 12 行时表格随内容加行（流体，不截断）', () => {
    const data = fixture()
    data.bom.items = Array.from({ length: 15 }, (_, i) =>
      item({ itemId: i + 1, materialName: `材料${i + 1}`, sortOrder: i })
    )
    const wrapper = mount(WorkSpecPaper, {
      props: {
        data,
        page: workSpecFlowSource(data),
        pageIndex: 0,
        pageCount: 1,
        specTemplate: 'B',
        fontSettings: defaultWorkSpecFontSettings(),
      },
      global: { stubs: { SvgIcon: true } },
    })
    wrappers.push(wrapper)
    expect(wrapper.findAll('.materials tbody tr')).toHaveLength(15)
  })

  it('空块开关：取消勾选后凹凸/备注/变更内容不渲染，发行单位仍保留（dev-20261102-002）', () => {
    const data = fixture()
    const mountWith = (hiddenBlocks?: Array<'emboss' | 'notes' | 'change'>) => {
      const wrapper = mount(WorkSpecPaper, {
        props: {
          data,
          page: { ...workSpecFlowSource(data), continuation: 0 },
          pageIndex: 0,
          pageCount: 1,
          specTemplate: 'B',
          fontSettings: defaultWorkSpecFontSettings(),
          hiddenBlocks,
        },
        global: { stubs: { SvgIcon: true } },
      })
      wrappers.push(wrapper)
      return wrapper
    }

    const shown = mountWith()
    expect(shown.find('.emboss-strip').exists()).toBe(true)
    expect(shown.find('.notes-strip').exists()).toBe(true)
    expect(shown.text()).toContain('变更内容')

    const hidden = mountWith(['emboss', 'notes', 'change'])
    expect(hidden.find('.emboss-strip').exists()).toBe(false)
    expect(hidden.find('.notes-strip').exists()).toBe(false)
    expect(hidden.text()).not.toContain('变更内容')
    // 发行单位/批准章位置不能跟着隐藏
    expect(hidden.text()).toContain('发行单位')

    // 只隐藏其中一块时其它块照常渲染
    const partial = mountWith(['notes'])
    expect(partial.find('.emboss-strip').exists()).toBe(true)
    expect(partial.find('.notes-strip').exists()).toBe(false)
    expect(partial.text()).toContain('变更内容')
  })
})
