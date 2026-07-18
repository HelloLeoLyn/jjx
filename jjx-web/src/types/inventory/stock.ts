// 库存汇总查询参数
export interface StockQueryParams {
  current?: number
  pageSize?: number
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
  materialId: string
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
  materialId: string
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
