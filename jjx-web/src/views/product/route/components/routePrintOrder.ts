/**
 * 工艺路线印刷工序拖拽排序（dev-20261019-015）。
 *
 * 印刷表格按"子结构 tab"展示，每个 tab 只是 printRows 的一个子集；
 * 拖拽只能在该子集内换序，且必须保持其它分类行在原数组中的位置（槽位不变），
 * 否则跨分类拖动会把行挤到别的结构下面，保存顺序也跟着乱。
 */

/** 用新的子集顺序回填到原数组的同一批槽位，返回新数组（不修改入参）。 */
export function reorderSubset<T>(all: T[], subset: T[], orderedSubset: T[]): T[] {
  if (!all.length) return []
  const slots: number[] = []
  all.forEach((row, index) => {
    if (subset.includes(row)) slots.push(index)
  })
  const next = [...all]
  slots.forEach((slot, i) => {
    if (i < orderedSubset.length) next[slot] = orderedSubset[i]
  })
  return next
}

/** 子集内把 from 移到 to（Sortable 的 oldIndex/newIndex 语义）。 */
export function moveWithinSubset<T>(subset: T[], from: number, to: number): T[] {
  const next = [...subset]
  if (from < 0 || from >= next.length || to < 0 || to >= next.length || from === to) return next
  const [moved] = next.splice(from, 1)
  next.splice(to, 0, moved)
  return next
}
