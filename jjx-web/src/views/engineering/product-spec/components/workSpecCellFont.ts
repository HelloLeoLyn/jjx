import { WORK_SPEC_FONT_MAX, WORK_SPEC_FONT_MIN } from './workSpecFont'

/** 仅影响当前打印排版；键跟随工序，不依赖分页后的页号或 DOM 行号。 */
export type WorkSpecCellFonts = Record<string, number>
const CELL_KEY = /^flow[0-2]-(?:id|row)-\d+$/

export function workSpecCellKey(column: number, row: Record<string, any>, number: number): string {
  const id = Number(row.itemId)
  return `flow${column}-${Number.isSafeInteger(id) && id > 0 ? `id-${id}` : `row-${number}`}`
}

export function parseWorkSpecCellFonts(value?: string | null): WorkSpecCellFonts {
  const fonts: WorkSpecCellFonts = {}
  for (const entry of value?.split(';') || []) {
    const parts = entry.split(':')
    if (parts.length !== 2) continue
    const [key, token] = parts
    const size = Number(token)
    if (CELL_KEY.test(key) && token.trim() && Number.isInteger(size)
      && size >= WORK_SPEC_FONT_MIN && size <= WORK_SPEC_FONT_MAX) fonts[key] = size
  }
  return fonts
}

export function serializeWorkSpecCellFonts(fonts: WorkSpecCellFonts): string {
  return Object.entries(fonts).filter(([key, size]) => CELL_KEY.test(key)
    && Number.isInteger(size) && size >= WORK_SPEC_FONT_MIN && size <= WORK_SPEC_FONT_MAX)
    .sort(([a], [b]) => a.localeCompare(b)).map(([key, size]) => `${key}:${size}`).join(';')
}

/** 测量真实单行宽度，只缩小文字；SVG/图片和格子尺寸不参与缩放。 */
export function fitWorkSpecOperationCells(root: HTMLElement): void {
  for (const cell of root.querySelectorAll<HTMLElement>('[data-work-spec-cell]')) {
    const operation = cell.querySelector<HTMLElement>('.operation')
    if (!operation?.clientWidth) continue
    const requested = Number(cell.dataset.cellFont)
    if (!Number.isFinite(requested) || requested <= 0) continue
    const text = Array.from(operation.querySelectorAll<HTMLElement>('.symbol-text, .symbol sub, .plus, .operation-remark'))
    const apply = (size: number) => text.forEach(node => { node.style.fontSize = `${size}px` })
    apply(requested)
    // 每次从请求字号重新测量，缩小后再次加大字号或变宽时可以正常恢复。
    let size = requested
    if (operation.scrollWidth > operation.clientWidth + 1) {
      let low = WORK_SPEC_FONT_MIN * 4, high = Math.floor(requested * 4)
      while (low < high) {
        const mid = Math.ceil((low + high) / 2)
        apply(mid / 4)
        if (operation.scrollWidth <= operation.clientWidth + 1) low = mid
        else high = mid - 1
      }
      size = low / 4
      apply(size)
    }
    cell.dataset.fittedFont = String(size)
    cell.toggleAttribute('data-cell-font-overflow', operation.scrollWidth > operation.clientWidth + 1)
  }
}
