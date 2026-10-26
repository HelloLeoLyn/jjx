/** 作业规范数据视图：保留组合工序，按工序大类和面板/上线/下线分组。 */
export interface SpecProcessRow {
  [key: string]: any
  children?: SpecProcessRow[]
}

export function processRowsForGroup(
  rows: SpecProcessRow[], major: 'ASSEMBLY' | 'PRINT', category: string,
  inheritedMajor = 'ASSEMBLY', inheritedCategory = ''
): SpecProcessRow[] {
  return rows.flatMap(row => {
    const rowMajor = row.majorCategory || inheritedMajor
    const rowCategory = row.processCategory || inheritedCategory
    const matches = rowMajor === major && rowCategory === category
    // 冲型组装的组合按父级流程归组；子件自身类别不再拆散组合。
    // 只调整展示副本，印刷查询仍按原始子件分类独立展开。
    const childRows = major === 'ASSEMBLY' && rowMajor === major
      ? (row.children || []).map(child => ({ ...child, processCategory: rowCategory }))
      : row.children || []
    const children = processRowsForGroup(childRows, major, category, rowMajor, rowCategory)
    if (major === 'PRINT') return [...(matches ? [{ ...row, processCategory: rowCategory }] : []), ...children]
    if (row.children?.length) {
      return matches && children.length ? [{ ...row, children, processCategory: rowCategory }] : children
    }
    return matches ? [{ ...row, processCategory: rowCategory }] : []
  })
}

export function workflowOperations(row: SpecProcessRow): SpecProcessRow[] {
  return row.children?.length ? row.children : [row]
}

/** 与纸张相同：组合行汇总子工序；定制人工工时优先，零值有效。 */
function hours(row: SpecProcessRow): number | null {
  if (row.children?.length) {
    const values = row.children.map(hours).filter((value): value is number => value !== null)
    return values.length ? values.reduce((sum, value) => sum + value, 0) : null
  }
  const value = row.customLaborHours ?? row.standardLaborHours
  if (value == null || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}
export function workflowLabor(row: SpecProcessRow): string {
  const value = hours(row)
  return value == null ? '-' : String(Number(value.toFixed(4)))
}
export function workflowNames(row: SpecProcessRow): string {
  return workflowOperations(row).map(item => item.processName || '-').join(' ＋ ')
}

interface FramePage { records?: { frame_no?: string; plate_no?: string | null }[]; total?: number }
/** 网框接口是模糊分页查询：必须精确匹配，并继续翻页，不能把前缀相似的网框认作目标。 */
export async function currentPlateForFrame(
  frameNo: string, fetchPage: (pageNum: number) => Promise<FramePage>
): Promise<string> {
  let seen = 0
  for (let pageNum = 1; ; pageNum++) {
    const page = await fetchPage(pageNum)
    const rows = page.records || []
    const frame = rows.find(row => row.frame_no === frameNo)
    if (frame) return frame.plate_no || ''
    seen += rows.length
    if (!rows.length || seen >= (page.total ?? seen)) return ''
  }
}
