/**
 * 采购收货类型定义
 * 收货操作直接关联采购订单明细，通过 /purchase/order/receive/{orderId}/{itemId} 完成
 * /purchase/receipt/list 返回的是已批准待收货的采购订单列表
 */

// 收货单VO（视图对象）- 实际是采购订单VO
export interface ReceiptVO {
  orderId: number
  orderNo: string
  supplierName: string
  receiptStatus: number
  orderDate: string
  createTime: string
  items: ReceiptItemVO[]
}

// 收货明细VO - 实际是采购订单明细VO
export interface ReceiptItemVO {
  itemId: number
  orderId: number
  materialCode: string
  materialName: string
  materialSpec: string
  unit: string
  quantity: number
  receivedQuantity: number
  receiptStatus: number
  inspectionResult: string
  inspectionRemark: string
  batchNo: string
  productionDate: string
  expiryDate: string
}

// 收货单查询参数
export interface ReceiptQuery {
  pageNum: number
  pageSize: number
  orderNo?: string
  supplierName?: string
  receiptStatus?: number
  receiptDateStart?: string
  receiptDateEnd?: string
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

// 收货统计
export interface ReceiptStats {
  totalCount: number
  pendingReceiptCount: number
  todayCount: number
  pendingInspectionCount: number
  inspectedCount: number
}
