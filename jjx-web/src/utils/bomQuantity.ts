/** 根据BOM当前基数、模数计算单位用量；无效参数交由既有明细校验处理。 */
export function calculateBomQuantity(baseQty: number | undefined, moduleQty: number | undefined): number | undefined {
  const base = Number(baseQty)
  const module = Number(moduleQty)
  if (!Number.isFinite(base) || base <= 0 || !Number.isFinite(module) || module <= 0) return undefined
  return Number((base / module).toFixed(4))
}
