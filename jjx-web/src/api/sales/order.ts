import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type {
  OrderReferValidationVO,
  SalesOrderAddDTO,
  SalesOrderEditDTO,
  SalesOrderQueryDTO,
  SalesOrderVO,
  QuotationVO,
  ProductionTaskVO,
  ShipmentVO,
  PaymentVO,
  InvoiceVO,
  OperationLogVO,
  ReviewRecordVO,
  ConfirmationRecordVO,
  OrderProgressVO,
  DictItemVO,
  OrderValidationResultVO,
  OrderStatisticsVO,
} from '@/types/sales/order'

export const orderApi = {
  // ==================== 基础 CRUD ====================
  /** 获取订单列表 */
  getOrders(params: SalesOrderQueryDTO) {
    return request.get<R<PageResult<SalesOrderVO>>>('/sales/orders', { params })
  },

  /** 获取订单详情 */
  getOrder(orderId: number) {
    return request.get<R<SalesOrderVO>>(`/sales/orders/${orderId}`)
  },

  /** 新增订单 */
  addOrder(data: SalesOrderAddDTO) {
    return request.post<R<number>>('/sales/orders', data)
  },

  /** 更新订单 */
  updateOrder(data: SalesOrderEditDTO) {
    return request.put<R<void>>(`/sales/orders/${data.orderId}`, data)
  },

  /** 部分更新订单 */
  patchOrder(orderId: number, data: Partial<SalesOrderEditDTO>) {
    return request.patch<R<void>>(`/sales/orders/${orderId}`, data)
  },

  /** 删除订单 */
  deleteOrder(orderId: number) {
    return request.delete<R<void>>(`/sales/orders/${orderId}`)
  },

  // ==================== 批量操作 ====================
  /** 批量删除订单 */
  batchDeleteOrders(orderIds: number[]) {
    return request.delete<R<void>>('/sales/orders/batch', { data: orderIds })
  },

  /** 批量审核通过 */
  batchApproveOrders(
    orderIds: number[],
    approverId: number,
    approverName: string,
    approveRemark: string
  ) {
    return request.put<R<void>>('/sales/orders/batch/approve', orderIds, {
      params: { approverId, approverName, approveRemark },
    })
  },

  /** 批量驳回 */
  batchRejectOrders(orderIds: number[], rejectRemark: string) {
    return request.put<R<void>>('/sales/orders/batch/reject', orderIds, {
      params: { rejectRemark },
    })
  },

  /** 批量导出订单 */
  batchExportOrders(orderIds: number[]) {
    return request.get('/sales/orders/batch/export', {
      params: { orderIds },
      responseType: 'blob',
    })
  },

  // ==================== 订单关联资源 ====================
  /** 创建产品实例 */
  addOrderInstances(orderId: number, config?: Record<string, unknown>) {
    return request.post<R<void>>(`/sales/orders/${orderId}/instances`, config || {})
  },

  /** 获取产品实例列表 */
  getOrderInstances(orderId: number) {
    return request.get<R<SalesOrderVO[]>>(`/sales/orders/${orderId}/instances`)
  },

  /** 获取关联报价单 */
  getOrderQuotations(orderId: number) {
    return request.get<R<QuotationVO[]>>(`/sales/orders/${orderId}/quotations`)
  },

  /** 获取关联生产任务 */
  getOrderProductionTasks(orderId: number) {
    return request.get<R<ProductionTaskVO[]>>(`/sales/orders/${orderId}/production-tasks`)
  },

  /** 获取关联发货单 */
  getOrderShipments(orderId: number) {
    return request.get<R<ShipmentVO[]>>(`/sales/orders/${orderId}/shipments`)
  },

  /** 获取关联收款记录 */
  getOrderPayments(orderId: number) {
    return request.get<R<PaymentVO[]>>(`/sales/orders/${orderId}/payments`)
  },

  /** 获取关联发票 */
  getOrderInvoices(orderId: number) {
    return request.get<R<InvoiceVO[]>>(`/sales/orders/${orderId}/invoices`)
  },

  // ==================== 日志与记录 ====================
  /** 获取操作日志 */
  getOrderOperationLogs(orderId: number) {
    return request.get<R<OperationLogVO[]>>(`/sales/orders/${orderId}/operation-logs`)
  },

  /** 获取审核记录 */
  getOrderReviewRecords(orderId: number) {
    return request.get<R<ReviewRecordVO[]>>(`/sales/orders/${orderId}/review-records`)
  },

  /** 获取确认记录 */
  getOrderConfirmationRecords(orderId: number) {
    return request.get<R<ConfirmationRecordVO[]>>(`/sales/orders/${orderId}/confirmation-records`)
  },

  // ==================== 进度管理 ====================
  /** 获取订单进度 */
  getOrderProgress(orderId: number) {
    return request.get<R<OrderProgressVO>>(`/sales/orders/${orderId}/progress`)
  },

  /** 更新订单进度 */
  updateOrderProgress(orderId: number, progressData: OrderProgressVO) {
    return request.put<R<void>>(`/sales/orders/${orderId}/progress`, progressData)
  },

  // ==================== 导入导出 ====================
  /** 导出订单数据 */
  exportOrders(params: SalesOrderQueryDTO) {
    return request.get('/sales/orders/export', {
      params,
      responseType: 'blob',
    })
  },

  /** 导出订单PDF */
  exportOrderPdf(orderId: number) {
    return request.get(`/sales/orders/${orderId}/export/pdf`, {
      responseType: 'blob',
    })
  },

  /** 导入订单数据 */
  importOrders(data: SalesOrderAddDTO[], updateSupport: boolean = false) {
    return request.post('/sales/orders/import', data, {
      params: { updateSupport },
    })
  },

  /** 下载导入模板 */
  downloadOrderTemplate() {
    return request.get('/sales/orders/import/template', {
      responseType: 'blob',
    })
  },

  // ==================== 辅助接口 ====================
  /** 生成订单号 */
  generateOrderNo() {
    return request.get<R<string>>('/sales/orders/order-no/next')
  },

  /** 校验订单号唯一性 */
  checkOrderNoUnique(orderNo: string) {
    return request.get<R<boolean>>('/sales/orders/order-no/' + orderNo + '/unique')
  },

  // ==================== 订单验证 ====================
  /** 验证订单是否可以提交审核 */
  validateOrderForReview(orderId: number, validateOptions?: Record<string, unknown>) {
    return request.post<R<OrderValidationResultVO>>(
      `/sales/orders/${orderId}/validate-for-review`,
      validateOptions || {}
    )
  },

  /** 获取订单验证结果 */
  getOrderValidationResult(orderId: number) {
    return request.get<R<OrderValidationResultVO>>(`/sales/orders/${orderId}/validation-result`)
  },

  /** 修复订单验证问题 */
  fixOrderValidationIssue(orderId: number, issueCode: string, fixData?: Record<string, unknown>) {
    return request.post<R<OrderValidationResultVO>>(`/sales/orders/${orderId}/fix-validation-issue`, {
      issueCode,
      fixData,
    })
  },

  // ==================== 字典数据 ====================
  /** 获取订单状态字典 */
  getOrderStatusDict() {
    return request.get<R<DictItemVO[]>>('/dict/sales/orders/order-status')
  },

  /** 获取审核状态字典 */
  getApprovalStatusDict() {
    return request.get<R<DictItemVO[]>>('/dict/sales/orders/approval-status')
  },

  /** 获取确认状态字典 */
  getConfirmationStatusDict() {
    return request.get<R<DictItemVO[]>>('/dict/sales/orders/confirmation-status')
  },

  /** 获取币种字典 */
  getCurrencyDict() {
    return request.get<R<DictItemVO[]>>('/dict/sales/orders/currency')
  },

  /** 获取付款条件字典 */
  getPaymentTermsDict() {
    return request.get<R<DictItemVO[]>>('/dict/sales/orders/payment-terms')
  },

  /** 获取运输方式字典 */
  getShippingMethodDict() {
    return request.get<R<DictItemVO[]>>('/dict/sales/orders/shipping-method')
  },

  // ==================== 统计分析 ====================
  /** 获取订单统计概览 */
  getOrderStatistics() {
    return request.get<R<OrderStatisticsVO>>('/sales/orders/statistics')
  },

  /** 获取订单趋势数据 */
  getOrderTrend(params: { startDate?: string; endDate?: string; period?: string }) {
    return request.get<R<OrderStatisticsVO>>('/sales/orders/statistics/trend', { params })
  },

  /** 获取订单金额统计 */
  getOrderAmountStatistics(params: { startDate?: string; endDate?: string }) {
    return request.get<R<OrderStatisticsVO>>('/sales/orders/statistics/amount', { params })
  },

  /** 获取订单产品统计 */
  getOrderProductStatistics(params: { productId?: number; startDate?: string; endDate?: string }) {
    return request.get<R<OrderStatisticsVO>>('/sales/orders/statistics/product', { params })
  },

  /** 获取订单客户统计 */
  getOrderCustomerStatistics(params: { customerId?: number; startDate?: string; endDate?: string }) {
    return request.get<R<OrderStatisticsVO>>('/sales/orders/statistics/customer', { params })
  },

  /** 获取订单校验信息 */
  getOrderValidationInfo(orderId: number) {
    return request.get<R<OrderReferValidationVO>>(`/sales/orders/${orderId}/validation`)
  },

  // ==================== 汇率 ====================
  /** 获取指定币种汇率（相对CNY） */
  getExchangeRate(currency: string) {
    return request.get<R<number>>('/system/exchange-rate/rate', { params: { currency } })
  },
}
