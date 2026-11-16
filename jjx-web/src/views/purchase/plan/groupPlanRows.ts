import type { PlanRow } from '@/api/purchase/planEntry'

/** 计划行按本次供应商分组；尚未指定的行单独成组。 */
export interface PlanGroup {
  supplierId: number | null
  supplierName: string
  rows: PlanRow[]
  earliestNeedDate?: string
  earliestDeliveryDate?: string
  urgent: boolean
  missingSupplier: boolean
}

export function groupPlanRows(rows: PlanRow[]): PlanGroup[] {
  const map = new Map<string, PlanGroup>()
  for (const row of rows) {
    const id = row.supplierId == null ? null : Number(row.supplierId)
    const key = id == null || !Number.isFinite(id) || id <= 0 ? 'missing' : String(id)
    let group = map.get(key)
    if (!group) {
      group = {
        supplierId: key === 'missing' ? null : Number(key),
        supplierName:
          key === 'missing' ? '（未指定本次供应商）' : row.supplierName || `供应商 #${key}`,
        rows: [],
        urgent: false,
        missingSupplier: key === 'missing',
      }
      map.set(key, group)
    }
    group.rows.push(row)
    if (row.priority === 'urgent') group.urgent = true
    if (row.needDate && (!group.earliestNeedDate || row.needDate < group.earliestNeedDate)) {
      group.earliestNeedDate = row.needDate
    }
    const delivery = row.procurement?.expectedDeliveryDate || row.needDate
    if (delivery && (!group.earliestDeliveryDate || delivery < group.earliestDeliveryDate))
      group.earliestDeliveryDate = delivery
  }
  return [...map.values()].sort((a, b) => (a.missingSupplier ? 1 : 0) - (b.missingSupplier ? 1 : 0))
}

/** 组内合计数量（按单位展示，不能跨单位相加）。 */
export function groupQtyLabel(group: PlanGroup): string {
  const byUnit = new Map<string, number>()
  for (const row of group.rows) {
    const purchase = row.procurement?.purchase || row
    const unit = purchase.unit || '—'
    byUnit.set(unit, (byUnit.get(unit) || 0) + Number(purchase.quantity || 0))
  }
  return [...byUnit.entries()].map(([unit, qty]) => `${Number(qty.toFixed(4))} ${unit}`).join(' / ')
}
