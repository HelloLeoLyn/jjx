/** 作业规范数据视图：保留组合工序，按工序大类和面板/上线/下线分组。 */
export interface SpecProcessRow {
  [key: string]: any
  children?: SpecProcessRow[]
}

/** 历史优先读取发布快照；缺失身份时明确未记录，不查询当前版面补历史。 */
export function printingBinding(row: SpecProcessRow): Record<string, any> {
  return row.printingSnapshot || row
}
export function printingIdentity(row: SpecProcessRow): string {
  const binding = printingBinding(row)
  if (!binding.artworkVersionId) return '图案版本未记录'
  return `${binding.artworkNo || '图案'} / ${binding.artworkVersionNo || binding.artworkVersionId}`
}
export function printingRequirements(row: SpecProcessRow): string {
  const binding = printingBinding(row)
  let value = binding.requirements ?? binding.printRequirements
  if (typeof value === 'string') { try { value = JSON.parse(value) } catch { return '要求未核验' } }
  if (!value) return '要求未记录'
  return [value.styles?.length && `样式 ${value.styles.map((s: any) => `${s.styleNo} · ${s.inkColor ?? '未记色号'}`).join(' / ')}`, value.position && `位置 ${value.position}`,
    (value.meshMin != null || value.meshMax != null) && `目数 ${value.meshMin ?? '不限'}—${value.meshMax ?? '不限'}`,
    (value.tensionMin != null || value.tensionMax != null) && `张力 ${value.tensionMin ?? '不限'}—${value.tensionMax ?? '不限'} N/cm`,
  ].filter(Boolean).join('；') || '未限定参数'
}
export function printingPlate(row: SpecProcessRow): string {
  const binding = printingBinding(row)
  if (binding.defaultPlateId) return `${binding.defaultPlateNo || binding.defaultPlateId} / ${binding.defaultFrameNo || '框号未记录'}`
  if (!row.printingSnapshot) {
    try {
      const legacy = typeof row.customProcessParams === 'string' ? JSON.parse(row.customProcessParams) : row.customProcessParams
      if (legacy?.screenNo || legacy?.plateNo) return `历史记录：${legacy.plateNo || '版号未记录'} / ${legacy.screenNo || '框号未记录'}（未核验）`
    } catch { /* 无法解析的历史文本不补造身份。 */ }
  }
  return '未指定建议网版'
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
