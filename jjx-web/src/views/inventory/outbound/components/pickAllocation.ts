import type {
  PickAllocationView,
  PickAllocationDemand,
  PickAllocationBatch,
} from '@/types/inventory/outbound'
import { OutboundOrderStatusEnum } from '@/enums/inventory/OutboundEnum'

export const draftKey = (demandId: string | number, batchId: string | number) =>
  `${demandId}:${batchId}`
export const sameId = (a: unknown, b: unknown) => String(a) === String(b)
export function allocationDraft(view: PickAllocationView) {
  const draft: Record<string, number> = {}
  for (const demand of view.demands)
    for (const row of demand.allocations) {
      if (row.stockItemId != null) {
        const key = draftKey(demand.demandItemId, row.stockItemId)
        draft[key] = (draft[key] || 0) + Number(row.quantity)
      }
    }
  return draft
}
export function materialRows(view: PickAllocationView, hints: any[] = []) {
  const materials = new Map<string, any>()
  for (const demand of view.demands) {
    const id = String(demand.materialId)
    const hint =
      hints.find(
        (row) =>
          sameId(row.sourceBomItemId, demand.sourceBomItemId) &&
          sameId(row.materialId, demand.materialId)
      ) || {}
    const row = materials.get(id) || {
      ...hint,
      ...demand,
      materialId: id,
      demands: [],
      quantity: 0,
      assigned: 0,
      supplierIds: new Set<string>(),
      batchIds: new Set<string>(),
      unknownSupplier: false,
    }
    row.demands.push(demand)
    row.quantity += Number(demand.quantity)
    for (const allocation of demand.allocations)
      if (allocation.stockItemId != null && Number(allocation.quantity) > 0) {
        row.assigned += Number(allocation.quantity)
        row.batchIds.add(String(allocation.stockItemId))
        const batch = view.batches.find((b) => sameId(b.stockItemId, allocation.stockItemId))
        if (batch?.supplierId != null) row.supplierIds.add(String(batch.supplierId))
        else row.unknownSupplier = true
      }
    materials.set(id, row)
  }
  const result: any[] = []
  const parents = new Set<string>()
  for (const row of materials.values()) {
    if (row.parentRowKey && !parents.has(row.parentRowKey)) {
      const parent = hints.find((hint) => hint.rowKey === row.parentRowKey && !hint.outboundItemId)
      if (parent) {
        result.push({ ...parent, isGroup: true, materialId: `parent:${row.parentRowKey}` })
        parents.add(row.parentRowKey)
      }
    }
    row.quantity = Number(row.quantity.toFixed(4))
    row.assigned = Number(row.assigned.toFixed(4))
    result.push({
      ...row,
      issued: view.status === OutboundOrderStatusEnum.COMPLETED.value ? row.assigned : 0,
    })
  }
  return result
}
export function batchLimit(
  demand: PickAllocationDemand,
  batch: PickAllocationBatch,
  draft: Record<string, number>,
  view: PickAllocationView
) {
  let sameBatch = 0,
    sameDemand = 0
  for (const other of view.demands)
    for (const candidate of view.batches) {
      const value = Number(draft[draftKey(other.demandItemId, candidate.stockItemId)] || 0)
      if (
        sameId(other.demandItemId, demand.demandItemId) &&
        !sameId(candidate.stockItemId, batch.stockItemId)
      )
        sameDemand += value
      if (
        sameId(candidate.stockItemId, batch.stockItemId) &&
        !sameId(other.demandItemId, demand.demandItemId)
      )
        sameBatch += value
    }
  const limit = Math.max(
    0,
    Math.min(Number(demand.quantity) - sameDemand, Number(batch.availableQuantity) - sameBatch)
  )
  return batch.rollNo
    ? Number(batch.quantity) <= limit
      ? Number(batch.quantity)
      : 0
    : Number(limit.toFixed(4))
}
export function pickingRows(view: PickAllocationView) {
  return view.demands
    .flatMap((demand) =>
      demand.allocations
        .filter((row) => row.stockItemId != null && Number(row.quantity) > 0)
        .map((row) => ({
          ...view.batches.find((batch) => sameId(batch.stockItemId, row.stockItemId)),
          ...row,
          materialCode: demand.materialCode,
          materialName: demand.materialName,
          unit: demand.unit,
        }))
    )
    .sort(
      (a, b) =>
        String(a.locationName || a.locationId || '').localeCompare(
          String(b.locationName || b.locationId || ''),
          'zh-CN',
          { numeric: true }
        ) || a.materialCode.localeCompare(b.materialCode)
    )
}
