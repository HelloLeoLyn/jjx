/** 模式只影响展示/打印；原始项目材料和人工规格永远保留。 */
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
  if (positive(row.widthMm) && positive(row.lengthMm))
    return `${decimal(row.widthMm)}×${decimal(row.lengthMm)}mm`
  return text(row.specification).replace(/([\d.]+)\s*mm\s*[×*x]\s*([\d.]+)\s*mm/gi, '$1×$2mm')
}
function sourceFor(row: Record<string, any>, sources?: MaterialSchemeSources): MaterialScheme | undefined {
  return (row.itemId != null ? sources?.byItem?.[row.itemId] : undefined)
    ?? sources?.byMaterial?.[row.materialId]
}

/** 只展开一层制造投入，沿用原行项目/刀模/分组身份，不把原始材料写成投入原料。 */
export function materialModeRows<T extends Record<string, any>>(rows: T[], data: MaterialModeData): T[] {
  if (effectiveMaterialMode(data) === MATERIAL_MODE_ORIGINAL) return rows
  return rows.flatMap(row => {
    const scheme = sourceFor(row, data.materialSchemes)
    if (scheme?.warning || !scheme?.items?.length) return [row]
    return scheme.items.map(input => ({
      ...row,
      materialId: input.materialId,
      materialCode: input.materialCode,
      materialName: input.materialName,
      specification: manufacturingMaterialSpec(input),
      params: manufacturingMaterialSpec(input),
      // 规格已经按方案整理成一份文本，防止原尺寸/旧跳距再次参与拼接。
      widthMm: null,
      lengthMm: null,
      split: '',
      moduleQty: input.moduleQty,
      baseQty: input.baseQty,
      quantity: input.quantity,
      unit: input.unit,
      manufacturingBomId: scheme.bomId,
    })) as T[]
  })
}
export function materialModeWarnings(data: MaterialModeData): string[] {
  if (effectiveMaterialMode(data) === MATERIAL_MODE_ORIGINAL) return []
  if (!data.materialSchemes) return ['制造方案资料尚未提供，当前显示原材料内容；请重新加载后核对。']
  return [...new Set([
    ...Object.values(data.materialSchemes.byItem || {}),
    ...Object.values(data.materialSchemes.byMaterial || {}),
  ].flatMap(source => source.warning ? [source.warning] : []))]
}
