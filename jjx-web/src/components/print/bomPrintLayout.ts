import { bomGroupRows } from '@/utils/bomGroups'
import { composeMaterialSpec } from '@/utils/materialSpec'
/** BOM 打印独立适配；规格拼接口径与作业规范/定版同源（@/utils/materialSpec）。 */
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

/**
 * 规格及模数：判定顺序只在 @/utils/materialSpec 维护一份（作业规范/定版同源）；
 * 这里只把「没有任何可打印内容」渲染成 '-'（打印占位）。
 */
export function bomMaterialSpec(row: BomPrintItem): string {
  return composeMaterialSpec(row) || '-'
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
