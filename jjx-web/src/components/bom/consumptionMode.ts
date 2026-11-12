/** 材料来源方式（dev-20261112-008）：按来源决定字段与用量算法。 */
export type ConsumptionMode = 'roll_cut' | 'sheet_nest' | 'direct' | 'continuous'

export const CONSUMPTION_MODE_OPTIONS: Array<{ value: ConsumptionMode; label: string }> = [
  { value: 'roll_cut', label: '卷材裁切' },
  { value: 'sheet_nest', label: '片材冲型' },
  { value: 'direct', label: '直接领用' },
  { value: 'continuous', label: '连续耗材' },
]

/** 与后端 normalizeConsumptionMode 同一套取值；未知值不猜，返回 null。 */
export function isConsumptionMode(value: unknown): value is ConsumptionMode {
  return value === 'roll_cut' || value === 'sheet_nest' || value === 'direct' || value === 'continuous'
}

/** 显隐口径：未设置（历史行）按原有模数/基数口径展示，避免老数据字段突然消失。 */
export const showModuleFields = (mode?: string | null): boolean => !mode || mode === 'sheet_nest'
export const showPitchFields = (mode?: string | null): boolean => mode === 'roll_cut'
export const showUnitConsumptionField = (mode?: string | null): boolean => mode === 'continuous'

/**
 * 默认推断（可人工改）：
 * 1）已有跳距文本 → 卷材裁切；
 * 2）档案单位为 M/M2 且有卷宽 → 卷材裁切；
 * 3）档案宽、长均有 → 片材冲型；
 * 4）其余 → 直接领用。
 */
export function inferConsumptionMode(
  material?: { unit?: string | null; widthMm?: number | null; lengthMm?: number | null } | null,
  split?: string | null,
): ConsumptionMode {
  if (split && String(split).trim() !== '') return 'roll_cut'
  const unit = String(material?.unit || '').trim().toUpperCase()
  const width = Number(material?.widthMm)
  const length = Number(material?.lengthMm)
  if ((unit === 'M' || unit === 'M2') && width > 0) return 'roll_cut'
  if (width > 0 && length > 0) return 'sheet_nest'
  return 'direct'
}
