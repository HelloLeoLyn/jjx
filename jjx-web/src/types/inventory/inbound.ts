// 入库查询参数
export interface InboundQueryParams {
  current?: number
  pageSize?: number
  inboundNo?: string
  inboundType?: string
  warehouseId?: string
  warehouseName?: string
  supplierId?: string
  supplierName?: string
  materialCode?: string
  materialName?: string
  status?: string
  createTimeStart?: string
  createTimeEnd?: string
  inboundTimeStart?: string
  inboundTimeEnd?: string
}

// 入库单VO
export interface InboundVO {
  inboundId: string
  inboundNo: string
  inboundType: string
  inboundTypeName: string
  warehouseId: string
  warehouseName: string
  supplierId?: string
  supplierName?: string
  sourceType?: string
  sourceId?: string
  sourceNo?: string
  totalQuantity: number
  totalAmount: number
  status: number
  statusName: string
  remark?: string
  createBy: string
  createTime: string
  updateTime: string
  items: InboundItemVO[]
}

// 入库单明细VO
export interface InboundItemVO {
  itemId: string
  inboundId: string
  materialId: string
  materialCode: string
  materialName: string
  materialType: string
  specification: string
  unit: string
  batchNo?: string
  quantity: number
  unitPrice: number
  amount: number
  locationId?: string
  locationCode?: string
  productionDate?: string
  expiryDate?: string
  shelfLifeDays?: number
  remark?: string
}

// 入库单创建参数
export interface InboundCreateParams {
  inboundType: string
  warehouseId: string
  supplierId?: string
  sourceType?: string
  sourceId?: string
  remark?: string
  items: InboundItemCreateParams[]
}

// 入库单明细创建参数
export interface InboundItemCreateParams {
  materialId: string
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  batchNo?: string
  quantity: number
  unitPrice: number
  amount?: number
  locationId?: string
  productionDate?: string
  expiryDate?: string
  remark?: string
}

// 入库单更新参数
export interface InboundUpdateParams {
  inboundId: string
  warehouseId?: string
  supplierId?: string
  remark?: string
  items?: InboundItemUpdateParams[]
}

// 入库单明细更新参数
export interface InboundItemUpdateParams {
  itemId?: string
  materialId: string
  batchNo?: string
  quantity: number
  unitPrice: number
  locationId?: string
  productionDate?: string
  expiryDate?: string
  remark?: string
}

// 入库单状态更新参数
export interface InboundStatusUpdateParams {
  inboundId: string
  status: string
  operatorId?: string
  operatorName?: string
  reason?: string
  remark?: string
}

// 入库单审批参数
export interface InboundApproveParams {
  inboundId: string
  approverId: string
  approverName: string
  remark?: string
}

// 入库单驳回参数
export interface InboundRejectParams {
  inboundId: string
  approverId: string
  approverName: string
  remark: string
}

// 入库单仪表板数据
export interface InboundDashboardData {
  todayInboundCount: number
  todayInboundAmount: number
  pendingApprovalCount: number
  pendingConfirmCount: number
  monthlyInboundTrend: Array<{
    date: string
    count: number
    amount: number
  }>
  topMaterials: Array<{
    materialId: string
    materialCode: string
    materialName: string
    totalQuantity: number
    totalAmount: number
  }>
}
