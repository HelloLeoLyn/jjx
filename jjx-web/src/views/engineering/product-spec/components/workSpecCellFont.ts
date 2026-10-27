import { WORK_SPEC_FONT_MAX, WORK_SPEC_FONT_MIN } from './workSpecFont'

/** 仅影响当前打印排版；键跟随工序/材料行/固定区域，不依赖分页后的页号或 DOM 行号。 */
export type WorkSpecCellFonts = Record<string, number>
const MATERIAL_COLUMNS = 'number|project|name|spec|die|emboss|embossValue'
const FIXED_KEYS = 'note|change|struct-title|struct-cap|footer'
const CELL_KEY = new RegExp(
  `^(?:flow[0-2]-(?:id|row)-\\d+|mat\\d+-(?:${MATERIAL_COLUMNS})|(?:${FIXED_KEYS}))$`
)

export function workSpecCellKey(column: number, row: Record<string, any>, number: number): string {
  const id = Number(row.itemId)
  return `flow${column}-${Number.isSafeInteger(id) && id > 0 ? `id-${id}` : `row-${number}`}`
}

/** 材料表格子：行号取整页行序（续页接续），列取 workSpecFont 的列键。 */
export function materialCellKey(index: number, column: string): string {
  return `mat${index}-${column}`
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
export function fitWorkSpecCells(root: HTMLElement): void {
  for (const cell of root.querySelectorAll<HTMLElement>('[data-work-spec-cell]')) {
    const requested = Number(cell.dataset.cellFont)
    if (!Number.isFinite(requested) || requested <= 0) continue
    const operation = cell.querySelector<HTMLElement>('.operation')
    // 流程格只缩符号与说明文字，图形尺寸不变；其他格缩格内直接承载文字的节点。
    const text = operation
      ? Array.from(
          operation.querySelectorAll<HTMLElement>('.symbol-text, .symbol sub, .plus, .operation-remark')
        )
      : textNodes(cell)
    if (!text.length) continue
    const apply = (size: number) => text.forEach((node) => { node.style.fontSize = `${size}px` })
    const box = operation ?? cell
    if (!box.clientWidth) continue
    const clipped = () => cell.hasAttribute('data-cell-fit')
    const overflows = () =>
      box.scrollWidth > box.clientWidth + 1 || (clipped() && box.scrollHeight > box.clientHeight + 1)
    apply(requested)
    // 每次从请求字号重新测量，缩小后再次加大字号或变宽时可以正常恢复。
    let size = requested
    if (overflows()) {
      let low = WORK_SPEC_FONT_MIN * 4, high = Math.floor(requested * 4)
      while (low < high) {
        const mid = Math.ceil((low + high) / 2)
        apply(mid / 4)
        if (overflows()) high = mid - 1
        else low = mid
      }
      size = low / 4
      apply(size)
    }
    cell.dataset.fittedFont = String(size)
    cell.toggleAttribute('data-cell-font-overflow', overflows())
  }
}

/** 格内直接承载文字的节点；没有子节点时退回格子本身。 */
function textNodes(cell: HTMLElement): HTMLElement[] {
  const nodes = Array.from(cell.querySelectorAll<HTMLElement>('*')).filter((element) =>
    Array.from(element.childNodes).some((node) => node.nodeType === 3 && !!node.textContent?.trim())
  )
  if (!nodes.length && cell.textContent?.trim()) nodes.push(cell)
  return nodes
}
