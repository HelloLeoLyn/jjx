/** 模式只影响展示/打印；原始项目材料和人工规格永远保留。 */
import { composeMaterialSpec } from '@/utils/materialSpec'
export type WorkSpecMaterialMode = 'scheme' | 'original'
export const MATERIAL_MODE_SCHEME: WorkSpecMaterialMode = 'scheme'
export const MATERIAL_MODE_ORIGINAL: WorkSpecMaterialMode = 'original'

export interface MaterialScheme {
  bomId?: number
  bomCode?: string
  bomVersion?: string
  items?: Record<string, any>[]
  warning?: string
}
export interface MaterialSchemeSources {
  byItem: Record<string, MaterialScheme>
  byMaterial: Record<string, MaterialScheme>
}
export interface MaterialModeData {
  published?: boolean
  materialMode?: WorkSpecMaterialMode
  materialSchemes?: MaterialSchemeSources
}
export function normalizeMaterialMode(value?: unknown): WorkSpecMaterialMode {
  return value === MATERIAL_MODE_ORIGINAL ? MATERIAL_MODE_ORIGINAL : MATERIAL_MODE_SCHEME
}
export function effectiveMaterialMode(data: MaterialModeData): WorkSpecMaterialMode {
  // 已发布历史资料缺少方案快照时，只读当时的原始内容，不能补取当前方案。
  if (data.published && !data.materialSchemes) return MATERIAL_MODE_ORIGINAL
  return normalizeMaterialMode(data.materialMode)
}
const text = (value: unknown) => String(value ?? '').trim()
const positive = (value: unknown) => Number.isFinite(Number(value)) && Number(value) > 0
const decimal = (value: unknown) => String(Number(value))

/** 跳距用方案实际 pitchMm；卷长不是裁片长度，195×50000mm + 跳80mm应显示195mm跳80mm。 */
export function manufacturingMaterialSpec(row: Record<string, any>): string {
  if (positive(row.pitchMm)) {
    const width = positive(row.widthMm)
      ? decimal(row.widthMm)
      : /^([\d.]+)\s*(?:mm|[×*x])/i.exec(text(row.specification))?.[1]
    return `${width ? `${decimal(width)}mm` : text(row.specification)}跳${decimal(row.pitchMm)}mm`
  }
  // 无跳距时直接沿用制造 BOM 规格原文，不改符号/单位，也不另用宽长覆盖。
  return row.specification == null ? '' : String(row.specification)
}
function sourceFor(row: Record<string, any>, sources?: MaterialSchemeSources): MaterialScheme | undefined {
  return (row.itemId != null ? sources?.byItem?.[row.itemId] : undefined)
    ?? sources?.byMaterial?.[row.materialId]
}

/** 只组合规格，材料身份、用量和分组始终沿用产品BOM/项目材料。 */
function materialDisplayRows<T extends Record<string, any>>(rows: T[], data: MaterialModeData): T[] {
  const mode = effectiveMaterialMode(data)
  return rows.map(row => {
    if (mode === MATERIAL_MODE_ORIGINAL && text(row.params)) return row
    const scheme = sourceFor(row, data.materialSchemes)
    // 多投入无法唯一对应备料宽度，保留原规格，不猜第一条，也不增加材料行。
    if (scheme?.warning || scheme?.items?.length !== 1) return row
    const size = /^\s*(\d+(?:\.\d+)?)\s*(?:mm)?\s*[×*xX]\s*(\d+(?:\.\d+)?)\s*(?:mm)?\s*$/.exec(text(row.specification))
    const width = positive(row.widthMm) ? Number(row.widthMm) : Number(size?.[1])
    const length = positive(row.lengthMm) ? Number(row.lengthMm) : Number(size?.[2])
    const input = scheme.items[0]
    const inputWidth = positive(input.widthMm) ? Number(input.widthMm)
      : Number(/^\s*(\d+(?:\.\d+)?)\s*(?:mm|[×*xX])/i.exec(text(input.specification))?.[1])
    if (!positive(width) || !positive(length) || !positive(inputWidth)) return row
    if (mode === MATERIAL_MODE_ORIGINAL && !positive(input.pitchMm)) return row
    const specification = mode === MATERIAL_MODE_SCHEME
      ? `${decimal(inputWidth)}mm宽切${decimal(width)}mm*${decimal(length)}mm`
      : `${decimal(width)}mm宽跳${decimal(input.pitchMm)}mm`
    return { ...row, specification, params: specification, manufacturingBomId: scheme.bomId }
  })
}

/** A版在B版同源规格上合并成品件数；此显示文本不进入BOM保存或全局BOM打印。 */
export function materialModeRows<T extends Record<string, any>>(rows: T[], data: MaterialModeData, template: 'A' | 'B' = 'A'): T[] {
  const displayed = materialDisplayRows(rows, data)
  if (template === 'B') return displayed
  return displayed.map((row, index) => {
    const generated = row !== rows[index]
    let combined: string
    if (!generated && text(row.params)) combined = String(row.params)
    else {
      const dimensions = positive(row.widthMm) && positive(row.lengthMm)
        ? `${decimal(row.widthMm)}*${decimal(row.lengthMm)}mm` : text(row.specification)
      const specification = generated ? String(row.params)
        : dimensions + (text(row.split) ? `跳${text(row.split)}` : '')
      const module = Number(row.moduleQty), base = Number(row.baseQty)
      combined = specification && positive(module) && positive(base)
        ? base === 1 ? `${specification}=${decimal(module)}PCS`
          : `${specification}（${decimal(base)}${text(row.unit) || '份'}材料供${decimal(module)}PCS成品）`
        : composeMaterialSpec(row)
    }
    return { ...row, workSpecCombinedSpec: combined }
  })
}
export function materialModeWarnings(data: MaterialModeData): string[] {
  if (effectiveMaterialMode(data) === MATERIAL_MODE_ORIGINAL) return []
  if (!data.materialSchemes) return ['生产BOM资料尚未提供，当前显示原材料内容；请重新加载后核对。']
  return [...new Set([
    ...Object.values(data.materialSchemes.byItem || {}),
    ...Object.values(data.materialSchemes.byMaterial || {}),
  ].flatMap(source => source.warning ? [source.warning] : []))]
}
