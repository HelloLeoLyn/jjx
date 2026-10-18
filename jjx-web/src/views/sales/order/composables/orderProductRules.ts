type Identity = { productId?: number | null; productCode?: string | null }
export const normalizedProductCode = (code?: string | null) => code?.trim().toUpperCase() || ''
export function sameOrderProduct(a: Identity, b: Identity) {
  if (a.productId != null && b.productId != null) return a.productId === b.productId
  const code = normalizedProductCode(a.productCode)
  return !!code && code === normalizedProductCode(b.productCode)
}
export function duplicateOrderProduct<T extends Identity>(items: T[], candidate?: Identity, except?: T) {
  if (candidate) return items.find(row => row !== except && sameOrderProduct(row, candidate))
  return items.find((row, index) => items.slice(0, index).some(other => sameOrderProduct(row, other)))
}
