// 出库查询参数
export interface OutboundQueryParams {
  current?: number
  pageSize?: number
  outboundNo?: string
  outboundType?: string
  warehouseId?: string
  warehouseName?: string
  customerId?: string
  customerName?: string
  materialCode?: string
  materialName?: string
  status?: string
  sourceType?: string
  sourceTypeNe?: string
  createTimeStart?: string
  createTimeEnd?: string
  outboundTimeStart?: string
  outboundTimeEnd?: string
}

// 出库单VO
export interface OutboundVO {
  outboundId: string
  outboundNo: string
  traceId?: string
  outboundType: string
  outboundTypeName: string
  warehouseId: string
  warehouseName: string
  customerId?: string
  customerName?: string
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
  items: OutboundItemVO[]
}

// 出库单明细VO
export interface OutboundItemVO {
  itemId: string
  outboundId: string
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
  remark?: string
}

// 出库单创建参数
export interface OutboundCreateParams {
  outboundType: string
  warehouseId: string
  customerId?: string
  sourceType?: string
  sourceId?: string
  remark?: string
  items: OutboundItemCreateParams[]
}

// 出库单明细创建参数
export interface OutboundItemCreateParams {
  materialId: string
  batchNo?: string
  quantity: number
  unitPrice: number
  locationId?: string
  remark?: string
}

// 出库单仪表板数据
export interface OutboundDashboardData {
  todayOutboundCount: number
  todayOutboundAmount: number
  pendingApprovalCount: number
  pendingConfirmCount: number
  monthlyOutboundTrend: Array<{
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

// 出库单更新参数
export interface OutboundUpdateParams {
  outboundId: string
  outboundType?: string
  warehouseId?: string
  customerId?: string
  sourceType?: string
  sourceId?: string
  remark?: string
  items?: OutboundItemCreateParams[]
}

// 出库单状态更新参数
export interface OutboundStatusUpdateParams {
  outboundId: string
  status: string
}

// 出库单审批参数
export interface OutboundApproveParams {
  outboundId: string
  approverId: string
  approverName: string
  remark?: string
}

// 出库单驳回参数
export interface OutboundRejectParams {
  outboundId: string
  approverId: string
  approverName: string
  remark?: string
}
