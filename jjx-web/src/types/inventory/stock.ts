// 库存汇总查询参数
export interface StockQueryParams {
  current?: number
  pageSize?: number
  itemType?: 'MATERIAL' | 'PRODUCT'
  materialCode?: string
  materialName?: string
  warehouseId?: string
  minQuantity?: number
  maxQuantity?: number
  lowStock?: boolean
  expiring?: boolean
  obsolete?: boolean
  createTimeStart?: string
  createTimeEnd?: string
}

// 库存汇总VO（按物料维度）
export interface StockVO {
  stockId: string
  inventoryItemId: string
  itemType: 'MATERIAL' | 'PRODUCT'
  itemTypeName: string
  sourceId: string
  materialId?: string
  materialCode: string
  materialName: string
  specification: string
  unit: string
  totalQuantity: number
  totalReserved: number
  availableQuantity: number
  totalCost: number
  avgUnitCost: number
  earliestExpiry?: string
  locationId?: string
  locationCode?: string
  locationName?: string
  safeStock?: number
  maxStock?: number
  lowStock?: boolean
  expiring?: boolean
  obsolete?: boolean
  daysToExpiry?: number
  updateTime: string
}

// 库存批次明细查询参数
export interface StockItemQueryParams {
  current?: number
  pageSize?: number
  inventoryItemId?: string
  materialId?: string
  materialCode?: string
  materialName?: string
  warehouseId?: string
  locationId?: string
  batchNo?: string
  status?: number
  createTimeStart?: string
  createTimeEnd?: string
}

// 库存批次明细VO
export interface StockItemVO {
  itemId: string
  inventoryItemId: string
  materialId?: string
  materialCode: string
  materialName: string
  specification: string
  unit: string
  warehouseId: string
  warehouseCode: string
  warehouseName: string
  locationId: string
  locationCode: string
  locationName: string
  batchNo: string
  productionDate?: string
  expiryDate?: string
  quantity: number
  reservedQuantity: number
  availableQuantity: number
  unitCost: number
  status: number
  statusName: string
  lastInboundTime?: string
  lastOutboundTime?: string
  createTime: string
  updateTime: string
  /**
   * 以下两列是展示派生值（dev-20260923-017）：由库存流水按批次聚合得到，
   * 不属于 inventory_stock_item 表字段（避免第二真源）。quantity 本身即“结存”。
   */
  receivedQuantity?: number
  issuedQuantity?: number
}

/** 批次收发存汇总（流水派生：入库合计/出库合计/结存） */
export interface BatchFlowSummaryVO {
  batchNo: string
  receivedQuantity: number
  issuedQuantity: number
  balanceQuantity: number
}

// 库存汇总VO
export interface StockSummaryVO {
  totalQuantity: number
  totalReservedQuantity: number
  totalAvailableQuantity: number
  totalCost: number
  materialCount: number
}

// 库存预警信息
export interface StockAlertInfo {
  lowStockCount: number
  expiringStockCount: number
  obsoleteStockCount: number
}

// 库存仪表板数据
export interface StockDashboardData {
  totalQuantity: number
  stockCount: number
  lowStockCount: number
  expiringStockCount: number
  obsoleteStockCount: number
}
