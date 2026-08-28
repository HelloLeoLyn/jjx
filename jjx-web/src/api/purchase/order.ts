import request from '@/utils/request'
import type { R } from '@/types'
import type { PurchaseOrder, PurchaseOrderItem, PurchaseDocument } from '@/types/purchase'
import type { PurchaseOrderQuery } from '@/types/purchase/order'

export function logPlanPrint() {
  return request.post('/purchase/order/plan-suggestions/print-log')
}

/**
 * 获取采购订单总数
 */
export function getOrderCount() {
  return request.get<R<number>>('/purchase/order/count')
}

/**
 * 查询采购订单列表
 */
export function listOrder(params?: PurchaseOrderQuery) {
  return request({
    url: '/purchase/order/list',
    method: 'get',
    params,
  })
}

// 查询采购订单详细
export function getOrder(orderId: number) {
  return request({
    url: `/purchase/order/${orderId}`,
    method: 'get',
  })
}

// 查询订单明细列表
export function getOrderItems(orderId: number) {
  return request({
    url: `/purchase/order/${orderId}/items`,
    method: 'get',
  })
}

// 新增采购订单
// DEV-664：data.items 传明细；data.saveAsPlan=true 时存为计划单（跳过供应商/明细强校验）
export function addOrder(data: PurchaseOrder, itemList?: PurchaseOrderItem[]) {
  const payload: any = { ...data }
  if (itemList) payload.items = itemList
  return request({
    url: '/purchase/order',
    method: 'post',
    data: payload,
  })
}

// 修改采购订单
export function updateOrder(data: PurchaseOrder, itemList?: PurchaseOrderItem[]) {
  return request({
    url: '/purchase/order',
    method: 'put',
    data: { ...data, itemList },
  })
}

// 导出采购订单列表（支持按查询条件导出或按ID导出）
export interface PurchaseExportParams extends Partial<PurchaseOrderQuery> {
  orderId?: string
  orderIds?: string
  format?: 'excel' | 'pdf'
}
export function exportOrder(params?: PurchaseExportParams) {
  return request({
    url: '/purchase/order/export',
    method: 'get',
    params,
    responseType: 'blob',
  })
}

// 导出订单详情
export function exportOrderDetail(orderId: number) {
  return request({
    url: `/purchase/order/export-detail/${orderId}`,
    method: 'get',
    responseType: 'blob',
  })
}

export function exportOrderPdf(orderId: number) {
  return request({
    url: `/purchase/order/export-pdf/${orderId}`,
    method: 'get',
    responseType: 'blob',
  })
}

// 提交订单审批
export function submitOrder(orderId: number) {
  return request({
    url: `/purchase/order/submit/${orderId}`,
    method: 'put',
  })
}

// 提交订单审批
export function cancleOrder(orderId: number) {
  return request({
    url: `/purchase/order/cancel/${orderId}`,
    method: 'put',
  })
}

// 批量提交订单审批
export function batchSubmitOrders(orderIds: number[]) {
  return request({
    url: '/purchase/order/batch-submit',
    method: 'put',
    data: orderIds,
  })
}

// 审批订单
export function approveOrder(data: {
  orderId: number
  approverId: number
  approverName: string
  approvalComment?: string
  approvalStatus?: number
}) {
  return request({
    url: '/purchase/order/approve',
    method: 'put',
    data,
  })
}

// 收货操作（单条明细 - 兼容旧接口）
export function receiveOrderItem(
  orderId: number,
  itemId: number,
  receivedQuantity: number,
  inspectionResult?: string,
  inspectionRemark?: string
) {
  return request({
    url: `/purchase/order/receive/${orderId}/${itemId}`,
    method: 'put',
    params: { receivedQuantity, inspectionResult, inspectionRemark },
  })
}

// 批量收货（含检验）- 使用DTO模式
export function batchReceiveOrderItems(
  orderId: number,
  data: {
    items: Array<{
      itemId: number
      receivedQuantity: number
      inspectionResult?: string
      inspectionRemark?: string
    }>
  }
) {
  return request({
    url: `/purchase/order/${orderId}/receive`,
    method: 'post',
    data,
  })
}

// 更新付款信息
export function updatePaymentInfo(orderId: number, paidAmount: number, paymentStatus: number) {
  return request({
    url: `/purchase/order/payment/${orderId}`,
    method: 'put',
    params: { paidAmount, paymentStatus },
  })
}

// 更新实际交货日期
export function updateActualDeliveryDate(orderId: number, actualDeliveryDate: string) {
  return request({
    url: `/purchase/order/delivery-date/${orderId}`,
    method: 'put',
    params: { actualDeliveryDate },
  })
}

// 根据供应商ID查询订单列表
export function getOrdersBySupplier(supplierId: number) {
  return request({
    url: `/purchase/order/supplier/${supplierId}`,
    method: 'get',
  })
}

// 根据订单状态查询订单列表
export function getOrdersByStatus(orderStatus: number) {
  return request({
    url: `/purchase/order/status/${orderStatus}`,
    method: 'get',
  })
}

// 查询待审批的订单列表
export function getPendingApprovalOrders() {
  return request({
    url: '/purchase/order/pending-approval',
    method: 'get',
  })
}

// 查询待收货的订单列表
export function getPendingReceiptOrders() {
  return request({
    url: '/purchase/order/pending-receipt',
    method: 'get',
  })
}

// 查询待付款的订单列表
export function getPendingPaymentOrders() {
  return request({
    url: '/purchase/order/pending-payment',
    method: 'get',
  })
}

// 查询紧急订单列表
export function getUrgentOrders() {
  return request({
    url: '/purchase/order/urgent',
    method: 'get',
  })
}

// 根据日期范围查询订单
export function getOrdersByDateRange(startDate: string, endDate: string) {
  return request({
    url: '/purchase/order/date-range',
    method: 'get',
    params: { startDate, endDate },
  })
}

// 检查订单号是否唯一
export function checkOrderNoUnique(orderNo: string) {
  return request({
    url: '/purchase/order/check-order-no-unique',
    method: 'get',
    params: { orderNo },
  })
}

// 获取订单统计信息
export function getOrderStatistics() {
  return request({
    url: '/purchase/order/statistics',
    method: 'get',
  })
}

// 生成采购订单号
export function generateOrderNo() {
  return request({
    url: '/purchase/order/generate-order-no',
    method: 'get',
  })
}

// 复制订单
export function copyOrder(sourceOrderId: number) {
  return request({
    url: `/purchase/order/copy/${sourceOrderId}`,
    method: 'post',
  })
}

// 更新订单状态
export function changeOrderStatus(orderId: number, orderStatus: number) {
  return request({
    url: `/purchase/order/status/${orderId}`,
    method: 'put',
    params: { orderStatus },
  })
}

// 更新收货状态
export function changeReceiptStatus(orderId: number, receiptStatus: number) {
  return request({
    url: `/purchase/order/receipt-status/${orderId}`,
    method: 'put',
    params: { receiptStatus },
  })
}

// ==================== 收货票据（PurchaseDocument - 磁盘临时文件模式） ====================

/**
 * 临时上传票据文件（只保存到磁盘，不插入数据库）
 * 文件名按 {orderNo}-{seq}{ext} 格式生成
 * @param orderId 订单ID
 * @param file 文件（图片、PDF等）
 * @returns 文件信息 {fileName, storageName, fileUrl, fileSize, orderNo}
 */
export function uploadTempReceiptFile(orderId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: `/purchase/invoice/upload-temp/${orderId}`,
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

/**
 * 查询订单的磁盘票据文件列表（扫描订单号目录）
 * @param orderId 订单ID
 * @returns 文件列表
 */
export function getDiskReceiptFiles(orderId: number) {
  return request({
    url: `/purchase/invoice/disk-files/${orderId}`,
    method: 'get',
  })
}

/**
 * 批量确认票据（将临时文件插入数据库）
 * @param orderId 订单ID
 * @param supplierId 供应商ID
 * @param files 文件信息列表 [{fileName, fileUrl, fileSize}]
 */
export function confirmReceiptDocuments(orderId: number, supplierId: number, files: Pick<PurchaseDocument, 'fileName' | 'fileUrl' | 'fileSize'>[]) {
  return request({
    url: '/purchase/invoice/batch-confirm',
    method: 'post',
    data: { orderId, supplierId, files },
  })
}

/**
 * 删除临时票据文件
 * @param fileUrl 文件URL
 */
export function deleteTempReceiptFile(fileUrl: string) {
  return request({
    url: '/purchase/invoice/temp-file',
    method: 'delete',
    params: { fileUrl },
  })
}

// ==================== DEV-664 采购计划 ====================

/**
 * 获取采购计划建议（安全库存预警 + 订单缺料预警）
 */
export function getPlanSuggestions() {
  return request({
    url: '/purchase/order/plan-suggestions',
    method: 'get',
  })
}

/**
 * 查询物料在途采购量（2026-08-18 P1-B：含草稿单，防重复下单）
 */
export function inTransit(materialIds: number[]) {
  return request({
    url: '/purchase/order/in-transit',
    method: 'get',
    params: { materialIds: materialIds.join(',') },
  })
}

/**
 * 确认计划单转正式采购单
 */
export function confirmPlan(orderId: string, supplierId: string, supplierName: string) {
  return request({
    url: `/purchase/order/${orderId}/confirm-plan`,
    method: 'put',
    params: { supplierId, supplierName },
  })
}
