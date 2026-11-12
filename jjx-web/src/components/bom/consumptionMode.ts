import type { EngineeringBomItem } from '@/types/product/bom'

/** 每跳片数默认1只是缺省值；独立的缺省值不锁住模数/基数。split 不参与新逻辑。 */
export const hasPitchValues = (row: EngineeringBomItem): boolean =>
  row.pitchMm != null || (row.pcsPerPitch != null && row.pcsPerPitch !== 1)
export const hasRatioValues = (row: EngineeringBomItem): boolean =>
  row.moduleQty != null || row.baseQty != null
export const normalizeBomUnit = (unit?: string | null): string => {
  const value = String(unit || '').trim().toUpperCase()
  if (['M2', 'M²', '㎡', '平方米', '平方'].includes(value)) return 'M2'
  if (value === '米') return 'M'
  if (['个', '件', '片', '张'].includes(value)) return 'PCS'
  return value
}
export const unitMismatch = (row: EngineeringBomItem): boolean =>
  Boolean(row.archiveUnit && normalizeBomUnit(row.archiveUnit) !== normalizeBomUnit(row.unit))

/** 只校验输入形状；耗用公式仅在后端实现。两组皆空可保存草稿。 */
export function consumptionInputError(row: EngineeringBomItem): string {
  if (hasPitchValues(row) && hasRatioValues(row)) return '跳距组与模数/基数不能同时填写，请二选一'
  if (hasPitchValues(row)) {
    if (!(Number(row.pitchMm) > 0)) return '跳距(mm)必须大于0'
    if (!/^\d+(\.\d{1,2})?$/.test(String(row.pitchMm)) || Number(row.pitchMm) >= 100000000) return '跳距最多2位小数、8位整数'
    const pcs = row.pcsPerPitch ?? 1
    if (!Number.isInteger(pcs) || pcs < 1 || pcs > 2147483647) return '每跳片数须为正整数'
  }
  for (const [value, name] of [[row.moduleQty, '模数'], [row.baseQty, '基数']] as const) {
    if (value != null && !(Number(value) > 0)) return `${name}必须大于0`
    if (value != null && (!/^\d+(\.\d{1,4})?$/.test(String(value)) || Number(value) >= 100000000)) return `${name}最多4位小数、8位整数`
  }
  if (hasRatioValues(row) && (row.moduleQty == null || row.baseQty == null)) return '请同时填写模数和基数'
  if (unitMismatch(row)) return `BOM单位 ${row.unit} 与档案单位 ${row.archiveUnit} 不一致，请人工确认并重新选料`
  return ''
}
