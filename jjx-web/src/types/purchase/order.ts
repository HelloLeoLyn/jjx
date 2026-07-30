/**
 * 采购订单类型定义
 */

// 采购订单VO（视图对象）
export interface PurchaseOrderVO {
  orderId?: string
  orderNo: string
  supplierId?: string
  supplierName: string
  orderDate: string
  expectedDeliveryDate: string
  currency: string
  orderType: string
  deliveryMethod?: string
  contractNo?: string
  deliveryAddress?: string
  urgentFlag: boolean
  urgentReason?: string
  // 合并后的审批状态：1草稿/2已取消/3待审批/4已批准/5已拒绝
  approvalStatus: number
  receiptStatus: number
  paymentStatus: number
  orderAmount: number
  orderTax: number
  orderTotalAmount: number
  createTime?: string
  updateTime?: string
  items: PurchaseOrderItem[]
}

// 采购订单明细
export interface PurchaseOrderItem {
  itemId?: string
  orderId?: string
  materialId?: string
  materialCode: string
  materialName: string
  materialSpec?: string
  unit: string
  quantity: number
  unitPrice: number
  amount: number
  taxRate?: number
  taxAmount?: number
  inspectionRemark?: string
}

// 采购订单查询参数
export interface PurchaseOrderQuery {
  // 基本查询
  orderNo?: string
  supplierName?: string

  // 状态筛选 - 合并后的审批状态
  approvalStatus?: number
  receiptStatus?: number
  paymentStatus?: number

  // 时间筛选
  orderDateStart?: string
  orderDateEnd?: string
  createTimeStart?: string
  createTimeEnd?: string

  // 其他筛选
  urgentFlag?: boolean
  orderType?: string

  // 分页参数
  pageNum: number
  pageSize: number

  // 排序参数
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

// 采购订单创建DTO
export interface PurchaseOrderCreateDTO {
  supplierId?: string
  supplierName: string
  orderDate: string
  expectedDeliveryDate: string
  currency: string
  orderType: string
  deliveryMethod?: string
  contractNo?: string
  deliveryAddress?: string
  urgentFlag: boolean
  urgentReason?: string
  orderAmount: number
  orderTax: number
  orderTotalAmount: number
  items: PurchaseOrderItem[]
}

// 采购订单更新DTO
export interface PurchaseOrderUpdateDTO {
  orderId: string
  supplierId?: string
  supplierName: string
  orderDate: string
  expectedDeliveryDate: string
  currency: string
  orderType: number
  deliveryMethod?: string
  contractNo?: string
  deliveryAddress?: string
  urgentFlag: boolean
  urgentReason?: string
  orderAmount: number
  orderTax: number
  orderTotalAmount: number
  items: PurchaseOrderItem[]
}

// 订单状态更新DTO
export interface OrderStatusUpdateDTO {
  orderId: string
  approvalStatus: number
  remark?: string
}

// 审批状态更新DTO
export interface ApprovalStatusUpdateDTO {
  orderId: string
  approvalStatus: number
  approvalComment?: string
}

// 收货状态更新DTO
export interface ReceiptStatusUpdateDTO {
  orderId: string
  receiptStatus: number
  receiptQuantity?: number
  receiptComment?: string
}

// 付款状态更新DTO
export interface PaymentStatusUpdateDTO {
  orderId: string
  paymentStatus: number
  paymentAmount?: number
  paymentComment?: string
}

// 采购订单统计
export interface PurchaseOrderStats {
  totalCount: number
  draftCount: number
  pendingApprovalCount: number
  approvedCount: number
  rejectedCount: number
  cancelledCount: number
  urgentCount: number
  overdueCount: number
  todayCount: number
  weekCount: number
  monthCount: number
  totalAmount: number
  avgAmount: number
  maxAmount: number
  minAmount: number
}

// 状态选项
export interface StatusOption {
  label: string
  value: string
}

// 状态标签类型映射
export interface StatusTagTypes {
  approval: Record<string, string>
  receipt: Record<string, string>
  payment: Record<string, string>
}
