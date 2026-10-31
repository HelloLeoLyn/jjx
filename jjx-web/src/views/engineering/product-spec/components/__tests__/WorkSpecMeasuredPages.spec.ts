import { describe, expect, it, vi } from 'vitest'
vi.mock('@/utils/request', () => ({ default: {} }))
import { makePages, workSpecFlowSource, type DocsetData } from '../docset'
import { normalizeWorkSpec } from '@/api/product/workSpec'

function fixture(count: number): DocsetData {
  return {
    product: {},
    bom: { items: Array.from({ length: count }, (_, i) => ({ itemId: i + 1 })) },
    routing: { items: [] },
    workSpec: normalizeWorkSpec({}),
    files: [],
    warnings: [],
  }
}
describe('作业规范按测量结果出纸', () => {
  it.each([5, 18, 25, 65])('B %i行完整进入测量源，出纸数量只由DOM分页结果决定', count => {
    const data = fixture(count)
    expect(workSpecFlowSource(data).rows).toHaveLength(count)
    expect(makePages(data, ['spec'], [], {}, {}, 'B')[0].flowPending).toBe(true)
    const html = ['<article>第一页</article>', '<article>续页</article>']
    const pages = makePages(data, ['spec'], [], {}, {}, 'B', html)
    expect(pages.map(page => page.flowHtml)).toEqual(html)
    expect(pages.some(page => page.flowPending)).toBe(false)
  })
  it('A附页默认关闭，手动开启与取消不改变主表分页', () => {
    const data = fixture(25)
    const overflow = { 'spec-0': [{ text: '加工要求（完整内容）' }, { text: '被裁切的全文' }] }
    const build = (enabled?: boolean) => makePages(data, ['spec'], [], {}, overflow, 'A', [], enabled)
    const original = build()
    expect(original.some(page => page.kind === 'spec-details')).toBe(false)
    const enabled = build(true)
    expect(enabled.filter(page => page.kind === 'spec')).toEqual(original.filter(page => page.kind === 'spec'))
    expect(enabled.find(page => page.kind === 'spec-details')?.detailLines).toEqual(Object.values(overflow)[0])
    expect(build(false)).toEqual(original)
  })
  it('B取消工程内容附页：即使开启也不生成 spec-details，超页由测量分页承接（dev-20261031-007）', () => {
    const data = fixture(25)
    const measured = ['<article>首张</article>', '<article>续张</article>']
    const overflow = { 'spec-b-0': [{ text: '加工要求（完整内容）' }, { text: '被裁切的全文' }] }
    const build = (enabled?: boolean) => makePages(data, ['spec'], [], {}, overflow, 'B', measured, enabled)
    expect(build(true).some(page => page.kind === 'spec-details')).toBe(false)
    expect(build(true).filter(page => page.kind === 'spec').map(page => page.flowHtml)).toEqual(measured)
  })
  it('A保留原有14行分页，不受B的测量HTML影响', () => {
    const pages = makePages(fixture(25), ['spec'], [], {}, {}, 'A', ['不应使用'])
    expect(pages.map(page => page.rows?.length)).toEqual([14, 11])
    expect(pages.every(page => !page.flowHtml && !page.flowPending)).toBe(true)
  })
})
