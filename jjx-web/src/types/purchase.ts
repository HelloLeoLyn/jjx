/**
 * 采购管理模块类型定义
 */

// 供应商类型
export interface PurchaseSupplier {
  supplierId: number
  supplierCode: string
  supplierName: string
  supplierType: string
  contactPerson?: string
  phone?: string
  email?: string
  address?: string
  paymentTerms?: string
  bankAccount?: string
  taxNumber?: string
  evaluationScore?: number
  qualityScore?: number
  deliveryScore?: number
  priceScore?: number
  lastEvaluationDate?: string
  status?: string
  delFlag?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
  remark?: string
}

// 采购订单类型
export interface PurchaseOrder {
  orderId?: number
  orderNo: string
  supplierId: number
  supplierName: string
  orderType?: string
  orderDate: string
  expectedDeliveryDate: string
  actualDeliveryDate?: string
  orderAmount?: number
  orderTax?: number
  orderTotalAmount?: number
  currency?: string
  orderStatus?: string
  approvalStatus?: string
  receiptStatus?: string
  approverId?: number
  approverName?: string
  approvalTime?: string
  approvalComment?: string
  paymentStatus?: string
  paidAmount?: number
  contractNo?: string
  deliveryMethod?: string
  deliveryAddress?: string
  remark?: string
  urgentFlag?: boolean
  urgentReason?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

// 采购订单明细类型
export interface PurchaseOrderItem {
  itemId?: number
  orderId: number
  materialId: number
  materialCode: string
  materialName: string
  materialSpec?: string
  unit?: string
  quantity: number
  unitPrice: number
  amount: number
  receivedQuantity?: number
  receiptStatus?: string
  inquiryInfo?: string
  inquiryStatus?: string
  batchNo?: string
  productionDate?: string
  expiryDate?: string
  inspectionResult?: string
  inspectionRemark?: string
  itemOrder?: number
}

// 采购票据类型
export interface PurchaseDocument {
  documentId?: number
  documentNo: string
  documentType: string
  orderId: number
  supplierId: number
  documentDate: string
  documentAmount: number
  currency?: string
  documentStatus?: string
  verificationDate?: string
  fileName?: string
  fileUrl?: string
  fileSize?: number
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

// 采购付款类型
export interface PurchasePayment {
  paymentId?: number
  paymentNo: string
  orderId: number
  documentId?: number
  paymentDate: string
  paymentAmount: number
  paymentMethod: string
  bankAccount?: string
  paymentStatus?: string
  approvalTime?: string
  actualPaymentDate?: string
  voucherNo?: string
  voucherFileUrl?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
  remark?: string
}

// 查询参数类型
export interface PurchaseQueryParams {
  pageNum?: number
  pageSize?: number
  orderByColumn?: string
  isAsc?: string
  [key: string]: any
}

// 供应商查询参数
export interface SupplierQueryParams extends PurchaseQueryParams {
  supplierCode?: string
  supplierName?: string
  supplierType?: string
  status?: string
  startDate?: string
  endDate?: string
}

// 采购订单查询参数
export interface OrderQueryParams extends PurchaseQueryParams {
  orderNo?: string
  supplierName?: string
  orderStatus?: string
  approvalStatus?: string
  receiptStatus?: string
  paymentStatus?: string
  urgentFlag?: boolean
  startDate?: string
  endDate?: string
}

// 采购票据查询参数
export interface DocumentQueryParams extends PurchaseQueryParams {
  documentNo?: string
  documentType?: string
  orderId?: number
  supplierId?: number
  documentStatus?: string
  startDate?: string
  endDate?: string
}

// 采购付款查询参数
export interface PaymentQueryParams extends PurchaseQueryParams {
  paymentNo?: string
  orderId?: number
  documentId?: number
  paymentStatus?: string
  paymentMethod?: string
  startDate?: string
  endDate?: string
}

// 材料询价类型
export interface MaterialInquiry {
  inquiryId?: number
  materialId?: number
  materialCode: string
  materialName: string
  materialSpec?: string
  unit?: string
  inquiryDate: string
  supplierId?: number
  supplierName?: string
  supplierCode?: string
  inquiryPrice?: number
  currency?: string
  quantity?: number
  deliveryDays?: number
  paymentTerms?: string
  validityDays?: number
  inquiryPerson?: string
  inquiryStatus?: string
  remark?: string
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
}

// 材料询价DTO类型
export interface MaterialInquiryDTO extends MaterialInquiry {
  totalAmount?: number
  withinValidityPeriod?: boolean
  materialCategory?: string
  supplierContact?: string
  supplierPhone?: string
}

// 材料询价VO类型
export interface MaterialInquiryVO extends MaterialInquiry {
  inquiryDateStr?: string
  inquiryPriceStr?: string
  quantityStr?: string
  inquiryStatusLabel?: string
  statusTagType?: string
  createTimeStr?: string
  updateTimeStr?: string
  totalAmount?: number
  totalAmountStr?: string
  withinValidityPeriod?: boolean
  validityEndDate?: string
  validityEndDateStr?: string
  remainingValidityDays?: number
  expired?: boolean
  active?: boolean
  materialCategory?: string
  materialCategoryName?: string
  supplierContact?: string
  supplierPhone?: string
  supplierAddress?: string
  supplierRating?: number
  inquiryCount?: number
  avgInquiryPrice?: number
  minInquiryPrice?: number
  maxInquiryPrice?: number
  priceTrend?: string
  priceChangePercent?: number
  canEdit?: boolean
  canDelete?: boolean
  canUse?: boolean
  canCopy?: boolean
}

// 材料询价查询参数
export interface MaterialInquiryQueryParams extends PurchaseQueryParams {
  materialId?: number
  materialCode?: string
  materialName?: string
  materialSpec?: string
  supplierId?: number
  supplierName?: string
  inquiryStatus?: string
  inquiryPerson?: string
  inquiryDateStart?: string
  inquiryDateEnd?: string
  minPrice?: number
  maxPrice?: number
  currency?: string
  onlyActive?: boolean
  onlyWithinValidity?: boolean
  includeExpired?: boolean
  keyword?: string
}

// 材料询价查询DTO（兼容后端）
export interface MaterialInquiryQueryDTO extends MaterialInquiryQueryParams {
  orderByColumn?: string
  orderDirection?: string
}
