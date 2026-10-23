import { bomGroupRows } from '@/utils/bomGroups'
/** BOM 打印独立适配；与作业规范 workSpecMaterialSpec 保持相同规格拼接口径。 */
export interface BomPrintItem {
  itemId?: number | null
  parentMaterialId?: number | null
  processId?: number | string | null
  processName?: string | null
  processCategory?: string | null
  materialName?: string | null
  specification?: string | null
  widthMm?: number | null
  lengthMm?: number | null
  split?: string | null
  moduleQty?: number | null
  /** 单位（PCS/个）与基数：计件回退显示用 */
  unit?: string | null
  baseQty?: number | null
  dieLocation?: string | null
  children?: BomPrintItem[]
}

const text = (value: unknown) => (value == null ? '' : String(value).trim())

export function bomMaterialSpec(row: BomPrintItem): string {
  const width = Number(row.widthMm)
  const length = Number(row.lengthMm)
  const rawSpecification = text(row.specification)
  // 规格纯零值（0/0.0）按空处理，避免打印出 0=1PCS（dev-20261023-010）。
  const specification =
    width > 0 && Number.isFinite(width) && length > 0 && Number.isFinite(length)
      ? `${width}*${length}mm`
      : /^0+(?:\.0+)?$/.test(rawSpecification)
        ? ''
        : rawSpecification
  const moduleRaw = row.moduleQty
  const module = Number(moduleRaw)
  const moduleMissing =
    moduleRaw == null || text(moduleRaw) === '' || !Number.isFinite(module)
  const unit = text(row.unit).toUpperCase()
  const base = Number(row.baseQty)
  // BOM 缺少规格：基数=1 且模数为空或 0<模数<1 时按计件显示 1个/PCS（dev-20261022-020）。
  if (!specification) {
    if (
      (unit === 'PCS' || unit === '个') &&
      base === 1 &&
      (moduleMissing || (module > 0 && module < 1))
    ) {
      return '1个/PCS'
    }
    return '-'
  }
  const split = text(row.split)
  return (
    specification +
    (split ? `跳${split}` : '') +
    (Number.isFinite(module) && module > 0 ? `=${module}PCS` : '')
  )
}

export interface BomPrintRow {
  item: BomPrintItem
  key: number
  groupKey: string
  number: number
  displayNumber: number
  span: number
}

export function bomPrintRows(items: BomPrintItem[]): BomPrintRow[] {
  const flat: BomPrintItem[] = []
  const visit = (rows: BomPrintItem[]) =>
    rows.forEach((row) => {
      flat.push(row)
      if (row.children?.length) visit(row.children)
    })
  visit(items)
  return bomGroupRows(flat, true).map((row) => ({
    item: row.item,
    key: row.index,
    groupKey: row.groupKey,
    number: row.number,
    displayNumber: row.displayNumber,
    span: 1,
  }))
}

/** 每页独立计算 rowspan；跨页组沿用序号，避免 rowspan 越过物理页边界。 */
export function mergeBomPage(rows: BomPrintRow[]): BomPrintRow[] {
  const result = rows.map((row) => ({ ...row, span: 0 }))
  for (let i = 0; i < result.length; ) {
    let end = i + 1
    while (end < result.length && result[end].groupKey === result[i].groupKey) end++
    result[i].span = end - i
    i = end
  }
  return result
}

/** 使用浏览器测得的未合并行高分页；超高单行保留自然高度，不裁切内容。 */
export function paginateBomRows(
  rows: BomPrintRow[],
  heights: number[],
  capacity: number
): BomPrintRow[][] {
  const pages: BomPrintRow[][] = []
  let page: BomPrintRow[] = []
  let used = 0
  rows.forEach((row, index) => {
    const height = Math.ceil(heights[index] || 22)
    if (page.length && used + height > capacity) {
      pages.push(mergeBomPage(page))
      page = []
      used = 0
    }
    page.push(row)
    used += height
  })
  if (page.length || !pages.length) pages.push(mergeBomPage(page))
  return pages
}
