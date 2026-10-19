import { describe, expect, it } from 'vitest'
import { moveWithinSubset, reorderSubset } from '../routePrintOrder'

/** dev-20261019-015：印刷工序拖拽排序的顺序计算（子集内换序、槽位不变） */
describe('routePrintOrder', () => {
  it('子集内上移/下移按 Sortable 的 from/to 语义换序', () => {
    expect(moveWithinSubset(['a', 'b', 'c'], 2, 0)).toEqual(['c', 'a', 'b'])
    expect(moveWithinSubset(['a', 'b', 'c'], 0, 2)).toEqual(['b', 'c', 'a'])
    expect(moveWithinSubset(['a', 'b', 'c'], 1, 1)).toEqual(['a', 'b', 'c'])
    expect(moveWithinSubset(['a', 'b', 'c'], -1, 2)).toEqual(['a', 'b', 'c'])
    expect(moveWithinSubset(['a', 'b', 'c'], 0, 5)).toEqual(['a', 'b', 'c'])
  })

  it('回填时保持其它分类行的槽位不变', () => {
    const all = ['P1', 'L1', 'P2', 'L2']
    const subset = ['P1', 'P2']
    expect(reorderSubset(all, subset, ['P2', 'P1'])).toEqual(['P2', 'L1', 'P1', 'L2'])
  })

  it('多行交错时只重排本分类，跨分类相对顺序不动', () => {
    const all = ['P1', 'L1', 'P2', 'L2', 'P3']
    const subset = ['P1', 'P2', 'P3']
    expect(reorderSubset(all, subset, ['P3', 'P1', 'P2'])).toEqual(['P3', 'L1', 'P1', 'L2', 'P2'])
  })

  it('空数组与不相干子集不抛错', () => {
    expect(reorderSubset([], [], [])).toEqual([])
    expect(reorderSubset(['a'], ['x'], ['y'])).toEqual(['a'])
  })
})
