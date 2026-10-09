/** 工单需求来自后端绑定BOM的领料预览，数量均按原材料单位抵扣。 */
export interface Order {
  orderId: string | number
  orderNo: string
  orderType: string
  parentOrderId?: string | number
  productName: string
  productCode: string
  plannedQuantity: number
  productUnit: string
  remark?: string
}
export interface PickRow {
  materialId: string | number
  materialCode: string
  materialName: string
  specification: string
  unit: string
  demand: number
  picked: number
  qtyNeeded: number
  available: number
  substitute: boolean
}
export interface Material {
  id: string
  code: string
  name: string
  spec: string
  unit: string
  available: number
  inventoryItemId?: string
}
export interface Allocation {
  id: string
  materialId: string
  coverage: number
  ratio: number
  loss: number
  step: number
  reason: string
}
export interface Demand {
  id: string
  original: Material
  total: number
  opened: number
  remaining: number
  allocations: Allocation[]
}
let allocationSequence = 0
export function allocation(materialId: string, coverage: number): Allocation {
  allocationSequence += 1
  return {
    id: `allocation-${Date.now()}-${allocationSequence}`,
    materialId,
    coverage,
    ratio: 1,
    loss: 0,
    step: 1,
    reason: '',
  }
}
export function buildDemands(rows: PickRow[]): Demand[] {
  // 现有接口不返回BOM行ID：只作为本页分组键，不能当作落库需求ID。
  return rows
    .filter((row) => !row.substitute)
    .map((row, index) => ({
      id: `${row.materialId}-${index}`,
      original: {
        id: String(row.materialId),
        code: row.materialCode,
        name: row.materialName,
        spec: row.specification || '',
        unit: row.unit,
        available: Number(row.available),
      },
      total: Number(row.demand),
      opened: Number(row.picked),
      remaining: Number(row.qtyNeeded),
      allocations:
        row.available > 0
          ? [
              allocation(
                String(row.materialId),
                Math.min(Number(row.available), Number(row.qtyNeeded))
              ),
            ]
          : [],
    }))
}
export function planned(d: Demand) {
  return d.allocations.reduce((sum, a) => sum + (Number(a.coverage) || 0), 0)
}
export function quantity(a: Allocation) {
  if (!(a.coverage > 0) || !(a.ratio > 0) || !(a.step > 0) || !(a.loss >= 0)) return 0
  return Number(
    (Math.ceil(((a.coverage / a.ratio) * (1 + a.loss / 100)) / a.step - 1e-9) * a.step).toFixed(4)
  )
}
export function materialTotals(demands: Demand[]) {
  const totals: Record<string, number> = {}
  for (const d of demands)
    for (const a of d.allocations)
      totals[a.materialId] = Number(((totals[a.materialId] || 0) + quantity(a)).toFixed(4))
  return totals
}
export function validationIssues(demands: Demand[], materials: Record<string, Material>) {
  const errors: string[] = []
  for (const d of demands) {
    if (planned(d) > d.remaining + 1e-8) errors.push(`${d.original.name}：本次分配超过剩余需求`)
    for (const a of d.allocations) {
      if (
        ![a.coverage, a.ratio, a.loss, a.step].every(Number.isFinite) ||
        !(a.coverage > 0) ||
        !(a.ratio > 0) ||
        !(a.step > 0) ||
        a.loss < 0
      )
        errors.push(`${d.original.name}：请填写有效的分配量、换算系数、损耗和取料步长`)
      if (!materials[a.materialId]) errors.push(`${d.original.name}：实际材料信息尚未加载`)
      if (a.materialId !== d.original.id && !a.reason.trim())
        errors.push(`${d.original.name}：替换材料需要填写换算依据`)
      if (a.materialId === d.original.id && (a.ratio !== 1 || a.loss !== 0))
        errors.push(`${d.original.name}：原料需求已包含BOM损耗，原规格保持1:1抵扣且不重复加损耗`)
    }
  }
  const totals = materialTotals(demands)
  for (const [id, qty] of Object.entries(totals)) {
    const m = materials[id]
    if (m && qty > m.available + 1e-8)
      errors.push(
        `${m.name}（${m.spec}）：合计需领${qty}${m.unit}，当前可用${m.available}${m.unit}`
      )
  }
  if (!demands.some((d) => d.allocations.length)) errors.push('请至少选择一种实际用料')
  return [...new Set(errors)]
}
