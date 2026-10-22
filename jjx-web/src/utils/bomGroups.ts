export interface BomGroupItem {
  itemId?: number | null
  parentMaterialId?: number | null
  processId?: number | string | null
  processName?: string | null
}

export const bomProjectKey = (row: BomGroupItem): string =>
  Number(row.processId) > 0 ? `p:${row.processId}` : row.processName?.trim() ? `n:${row.processName.trim()}` : ''

/** 草稿按当前顺序分组；已保存数据优先用归属，整批无归属的历史数据显式推定。保留输入行序。 */
export function bomGroupRows<T extends BomGroupItem>(items: T[], saved = false) {
  const byId = new Map(items.filter(row => row.itemId != null).map(row => [row.itemId!, row]))
  const explicit = saved && items.some(row => row.parentMaterialId != null && row.parentMaterialId !== 0)
  const legacy = saved && !explicit
  let run = 0
  let previous = ''
  const rows = items.map((item, index) => {
    let leader: T = item
    let invalid = false
    let groupKey: string
    if (explicit) {
      const seen = new Set<T>()
      while (leader.parentMaterialId != null && leader.parentMaterialId !== 0) {
        const parent = byId.get(leader.parentMaterialId)
        if (seen.has(leader) || !parent) { invalid = true; break }
        seen.add(leader)
        leader = parent
      }
      groupKey = invalid ? `invalid:${index}` : `saved:${leader.itemId ?? `row:${index}`}`
    } else {
      const project = bomProjectKey(item)
      if (!project || project !== previous) run++
      previous = project
      groupKey = `run:${run}`
    }
    return { item, index, leader, groupKey, legacy, invalid, number: 0, size: 0, span: 1, label: '' }
  })
  const sizes = new Map<string, number>()
  const numbers = new Map<string, number>()
  for (const row of rows) {
    sizes.set(row.groupKey, (sizes.get(row.groupKey) || 0) + 1)
    if (!numbers.has(row.groupKey)) numbers.set(row.groupKey, numbers.size + 1)
  }
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i]
    row.size = sizes.get(row.groupKey)!
    row.number = numbers.get(row.groupKey)!
    row.span = i > 0 && rows[i - 1].groupKey === row.groupKey ? 0 : 1
    if (row.span) while (i + row.span < rows.length && rows[i + row.span].groupKey === row.groupKey) row.span++
    const project = (explicit ? row.leader : row.item).processName?.trim() || '未选项目'
    row.label = `${row.invalid ? '归属异常' : row.size > 1 ? `组合${row.number}` : '独立材料'} · ${project} · ${row.size}项材料${legacy ? '（历史推定）' : ''}`
  }
  return rows
}
