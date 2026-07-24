// 订单明细
export interface OrderItem {
  id?: number
  productId?: number | null
  orderId?: number
  productCode: string
  productName: string
  specification: string
  unit: string
  quantity: number
  unitPrice: number
  amount: number
  deliveryDays: number
  customRequirements: string
  customerMaterialNo?: string
  lineRemark?: string
}

// 查询参数
export interface OrderQueryParams {
  pageNum: number
  pageSize: number
  orderNo?: string
  customerName?: string
  orderStatus?: string
  startDate?: string
  endDate?: string
  orderByColumn?: string
  isAsc?: 'asc' | 'desc'
}

/**
 * 销售订单添加DTO
 */
export interface SalesOrderAddDTO {
  /** 报价单ID */
  quotationId?: number
  /** 客户ID */
  customerId: number
  /** 客户名称 */
  customerName: string
  /** 联系人 */
  contactPerson?: string
  /** 联系电话 */
  contactPhone?: string
  /** 订单日期 */
  orderDate: string
  /** 客户要求交货日期 */
  deliveryDate: string
  /** 订单类型: 1标准订单,2样品订单 */
  orderType: number
  /** 是否急单: 0否,1是 */
  isUrgent?: number
  /** 加急原因 */
  urgentReason?: string
  /** 币种 */
  currency: string
  /** 汇率 */
  exchangeRate: number
  /** 付款条件 */
  paymentTerms?: string
  /** 交货条件 */
  deliveryTerms?: string
  /** 交货地址 */
  deliveryAddress?: string
  /** 总金额 */
  totalAmount: number
  /** 税率 */
  taxRate?: number
  /** 折扣率 */
  discountRate?: number
  /** 总数量 */
  totalQuantity: number
  /** 销售负责人ID */
  salesManagerId: number
  /** 销售负责人姓名 */
  salesManagerName: string
  /** 备注 */
  remark?: string
}

/**
 * 销售订单编辑DTO
 */
export interface SalesOrderEditDTO {
  /** 订单ID */
  orderId: number
  /** 报价单ID */
  quotationId?: number
  /** 客户ID */
  customerId: number
  /** 客户名称 */
  customerName: string
  /** 联系人 */
  contactPerson?: string
  /** 联系电话 */
  contactPhone?: string
  /** 订单日期 */
  orderDate: string
  /** 客户要求交货日期 */
  deliveryDate: string
  /** 订单类型: 1标准订单,2样品订单 */
  orderType: number
  /** 是否急单: 0否,1是 */
  isUrgent?: number
  /** 加急原因 */
  urgentReason?: string
  /** 币种 */
  currency: string
  /** 汇率 */
  exchangeRate: number
  /** 付款条件 */
  paymentTerms?: string
  /** 交货条件 */
  deliveryTerms?: string
  /** 交货地址 */
  deliveryAddress?: string
  /** 总金额 */
  totalAmount: number
  /** 税率 */
  taxRate?: number
  /** 折扣率 */
  discountRate?: number
  /** 总数量 */
  totalQuantity: number
  /** 销售负责人ID */
  salesManagerId: number
  /** 销售负责人姓名 */
  salesManagerName: string
  /** 备注 */
  remark?: string
}

/**
 * 销售订单查询DTO
 */
export interface SalesOrderQueryDTO {
  /** 订单编号 */
  orderNo?: string
  /** 客户ID */
  customerId?: number
  /** 客户名称（模糊查询） */
  customerName?: string
  /** 订单类型 */
  orderType?: number
  /** 订单状态 */
  orderStatus?: number
  /** 生产状态 */
  prodStatus?: number
  /** 支付状态 */
  paymentStatus?: number
  /** 是否急单 */
  isUrgent?: number
  /** 销售负责人ID */
  salesManagerId?: number

  // ========== 订单日期查询条件 ==========
  /** 订单开始日期（起始） */
  orderDateStart?: string
  /** 订单结束日期 */
  orderDateEnd?: string

  // ========== 交货日期查询条件 ==========
  /** 交货开始日期（起始） */
  deliveryDateStart?: string
  /** 交货结束日期 */
  deliveryDateEnd?: string

  // ========== 创建时间查询条件 ==========
  /** 创建开始时间 */
  createTimeStart?: string
  /** 创建结束时间 */
  createTimeEnd?: string

  // ========== 排序字段 ==========
  /** 排序字段 */
  orderByColumn?: string
  /** 排序方式：asc升序，desc降序 */
  isAsc?: 'asc' | 'desc'

  // ========== 分页参数 ==========
  /** 页码 */
  pageNum?: number
  /** 每页大小 */
  pageSize?: number
}

/**
 * 销售订单响应VO
 */
export interface SalesOrderVO {
  /** 订单ID */
  orderId: number
  /** 订单编号 */
  orderNo: string
  /** 报价单ID */
  quotationId?: number
  /** 客户ID */
  customerId: number
  /** 客户名称 */
  customerName: string
  /** 联系人 */
  contactPerson?: string
  /** 联系电话 */
  contactPhone?: string
  /** 订单日期 */
  orderDate: string
  /** 客户要求交货日期 */
  deliveryDate: string
  /** 订单类型 */
  orderType: number
  /** 订单类型描述 */
  orderTypeDesc: string
  /** 订单状态 */
  orderStatus: number
  /** 订单状态描述 */
  orderStatusDesc: string
  /** 生产状态 */
  prodStatus: number
  /** 生产状态描述 */
  prodStatusDesc: string
  /** 是否急单 */
  isUrgent: number
  /** 是否急单描述 */
  isUrgentDesc: string
  /** 加急原因 */
  urgentReason?: string
  /** 币种 */
  currency: string
  /** 汇率 */
  exchangeRate: number
  /** 付款条件 */
  paymentTerms?: string
  /** 交货条件 */
  deliveryTerms?: string
  /** 交货地址 */
  deliveryAddress?: string
  /** 总金额 */
  totalAmount: number
  /** 税率 */
  taxRate: number
  /** 税额 */
  taxAmount: number
  /** 含税总金额 */
  totalAmountWithTax: number
  /** 折扣率 */
  discountRate: number
  /** 折扣金额 */
  discountAmount: number
  /** 最终金额 */
  finalAmount: number
  /** 支付状态 */
  paymentStatus: number
  /** 支付状态描述 */
  paymentStatusDesc: string
  /** 已付金额 */
  paidAmount: number
  /** 未付金额 */
  unpaidAmount: number
  /** 总数量 */
  totalQuantity: number
  /** 已发货数量 */
  shippedQuantity: number
  /** 已生产数量 */
  producedQuantity: number
  /** 销售负责人ID */
  salesManagerId: number
  /** 销售负责人姓名 */
  salesManagerName: string
  /** 备注 */
  remark?: string
  /** 创建时间 */
  createTime: string
  /** 创建人 */
  createBy?: string
  /** 更新时间 */
  updateTime: string
  /** 更新人 */
  updateBy?: string
  /** 订单明细 */
  items?: SalesOrderProductVO[]
}
// 订单产品明细VO类型
export interface SalesOrderProductVO {
  id: number
  quantity: number
  amount: number
  orderId: number
  productId: number
  unit: number
  unitDesc: string
  unitPrice: number
  productCode: string
  productName: string
  createTime: string
  createBy: string
  updateTime: string
  updateBy: string
}

// 订单产品明细添加DTO
export interface SalesOrderProductAddDTO {
  quantity: number
  amount: number
  orderId: number
  productId: number
  unit?: number
  unitPrice?: number
  productCode: string
  productName: string
}

// 订单产品明细编辑DTO
export interface SalesOrderProductEditDTO {
  id: number
  quantity: number
  amount: number
  orderId: number
  productId: number
  unit?: number
  unitPrice?: number
  productCode: string
  productName: string
}

// 订单产品明细查询DTO
export interface SalesOrderProductQueryDTO {
  id?: number
  orderId?: number
  productId?: number
  productCode?: string
  productName?: string
  minQuantity?: number
  maxQuantity?: number
  pageNum?: number
  pageSize?: number
  orderByColumn?: string
  isAsc?: string
}

// 订单明细项类型（与后端SalesOrderProductAddDTO保持一致）
export interface OrderItem {
  id?: number
  productCode: string
  productName: string
  specification: string
  unit: string
  quantity: number
  unitPrice: number
  amount: number
  deliveryDays: number
  customRequirements: string
  productId?: number
  orderId?: number
}

// 订单表单数据类型
export interface OrderFormData {
  orderId?: number
  orderNo: string
  customerId?: number
  customerName: string
  contactPerson: string
  contactPhone: string
  email: string
  creditLimit: number
  orderDate: string
  deliveryDate: string
  orderType?: number
  currency: string
  exchangeRate: number
  paymentTerms: string
  shippingMethod: string
  shippingAddress: string
  subtotalAmount: number
  taxRate: number
  taxAmount: number
  shippingFee: number
  discountAmount: number
  totalAmount: number
  totalQuantity: number // 总数量
  orderStatus: number
  prodStatus: number
  paymentStatus: number
  salesPersonId?: number
  salesPersonName: string
  remark: string
  items: OrderItem[]
}

// 可组合函数参数
export interface UseOrderFormOptions {
  // 是否为编辑模式
  isEdit?: boolean
  // 初始表单数据（编辑时使用）
  initialData?: Partial<OrderFormData>
}

/**
 * 产品验证VO
 */
export interface ProductValidationVO {
  productId: number
  productCode: string
  productName: string
  productStatus?: number
  productCategory?: string
  bomId?: number
  bomCode?: string
  bomVersion?: string
  isBomCurrentVersion?: boolean
  bomStatus?: number
  routingId?: number
  routingCode?: string
  routingName?: string
  isRoutingCurrentVersion?: boolean
  routingVersion?: string
  routingStatus?: number
}

/**
 * 关联报价单VO
 */
export interface QuotationVO {
  quotationId: number
  quotationNo: string
  customerName: string
  totalAmount: number
  status: number
  createTime: string
}

/**
 * 生产任务VO
 */
export interface ProductionTaskVO {
  taskId: number
  taskNo: string
  productName: string
  status: number
  planStartDate: string
  planEndDate: string
}

/**
 * 发货单VO
 */
export interface ShipmentVO {
  shipmentId: number
  shipmentNo: string
  deliveryDate: string
  status: number
  totalQuantity: number
}

/**
 * 收款记录VO
 */
export interface PaymentVO {
  paymentId: number
  paymentNo: string
  amount: number
  paymentDate: string
  paymentMethod: string
  status: number
}

/**
 * 发票VO
 */
export interface InvoiceVO {
  invoiceId: number
  invoiceNo: string
  amount: number
  invoiceDate: string
  status: number
}

/**
 * 操作日志VO
 */
export interface OperationLogVO {
  logId: number
  operationType: string
  operator: string
  operationTime: string
  detail: string
}

/**
 * 审核记录VO
 */
export interface ReviewRecordVO {
  reviewId: number
  reviewerName: string
  reviewAction: string
  reviewComment: string
  reviewTime: string
}

/**
 * 确认记录VO
 */
export interface ConfirmationRecordVO {
  confirmId: number
  confirmType: string
  confirmResult: string
  confirmTime: string
  remark: string
}

/**
 * 订单进度VO
 */
export interface OrderProgressVO {
  orderId: number
  overallProgress: number
  stages: Array<{
    stageName: string
    status: string
    progress: number
    startTime?: string
    endTime?: string
  }>
}

/**
 * 字典项VO
 */
export interface DictItemVO {
  value: string | number
  label: string
}

/**
 * 订单验证结果VO
 */
export interface OrderValidationResultVO {
  valid: boolean
  issues: Array<{
    code: string
    message: string
    severity: 'error' | 'warning'
  }>
}

/**
 * 订单统计VO
 */
export interface OrderStatisticsVO {
  totalOrders: number
  pendingOrders: number
  completedOrders: number
  totalAmount: number
  monthlyData?: Array<{
    month: string
    orderCount: number
    amount: number
  }>
}

import type { CustomerVO } from '@/types/sales/customer'
/**
 * 订单引用验证VO
 */
export interface OrderReferValidationVO {
  orderId: number
  orderNo?: string
  customerVO?: CustomerVO
  items: ProductValidationVO[]
  canSubmit?: boolean
}
