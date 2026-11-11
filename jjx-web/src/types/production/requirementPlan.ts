/** 多层制造净需求只读试算（dev-20261111-007 接入 D 阶段能力） */
export interface RequirementPreviewRequest {
  productId: number
  bomId?: number | null
  quantity: number
  demandDate?: string | null
  warehouseId?: number | null
}

export interface RequirementLine {
  materialId: number
  materialCode: string
  materialName: string
  stockUnit?: string | null
  make?: boolean | null
  subBomId?: number | null
  bomUnit?: string | null
  unitFactorExact?: string | null
  unitFactorBasis?: string | null
  demandDate?: string | null
  warehouseId?: number | null
  theoreticalQtyApprox?: number | null
  lossAppliedQtyApprox?: number | null
  aggregateQtyDisplay?: number | null
  stockOnHand?: number | null
  stockReserved?: number | null
  safeStock?: number | null
  availableForDemand?: number | null
  safeStockDeducted?: boolean | null
  productionSupply?: number | null
  purchaseSupply?: number | null
  plannedPurchaseSupply?: number | null
  allocatedStock?: number | null
  allocatedProduction?: number | null
  allocatedPurchase?: number | null
  gapQtyApprox?: number | null
  gapQtyDisplay?: number | null
  issueQty?: number | null
  minIssueQty?: number | null
  warnings?: string[]
  limitations?: string[]
}

export interface RequirementSummary {
  readOnly?: boolean
  stockWritten?: boolean
  confirmSupported?: boolean
  note?: string
  productId?: number
  productCode?: string
  productName?: string
  productUnit?: string
  bomId?: number
  bomCode?: string
  requestedQuantity?: number
  demandDate?: string
  warehouseId?: number
  generatedAt?: string
  plannedPurchaseSupply?: number
  nodes?: unknown[]
  lines: RequirementLine[]
  warnings?: string[]
  limitations?: string[]
}
