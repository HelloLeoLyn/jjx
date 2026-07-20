import request from '@/utils/request'
import type { PurchasePayment } from '@/types/purchase'

// 查询采购付款列表
export function listPayment(params?: Record<string, unknown>) {
  return request({
    url: '/purchase/payment/list',
    method: 'get',
    params,
  })
}

// 查询采购付款详细
export function getPayment(paymentId: number) {
  return request({
    url: `/purchase/payment/${paymentId}`,
    method: 'get',
  })
}

// 新增采购付款
export function addPayment(data: PurchasePayment) {
  return request({
    url: '/purchase/payment',
    method: 'post',
    data,
  })
}

// 修改采购付款
export function updatePayment(data: PurchasePayment) {
  return request({
    url: '/purchase/payment',
    method: 'put',
    data,
  })
}

// 删除采购付款
export function delPayment(paymentIds: number | number[]) {
  return request({
    url: `/purchase/payment/${paymentIds}`,
    method: 'delete',
  })
}

// 导出采购付款列表
export function exportPayment(params?: Record<string, unknown>) {
  return request({
    url: '/purchase/payment/export',
    method: 'get',
    params,
    responseType: 'blob',
  })
}

// 审批付款
export function approvePayment(
  paymentId: number,
  approvalStatus: string,
  approverName: string,
  approvalComment?: string,
) {
  return request({
    url: `/purchase/payment/approve/${paymentId}`,
    method: 'put',
    params: { approvalStatus, approverName, approvalComment },
  })
}

// 确认付款
export function confirmPayment(data: FormData) {
  return request({
    url: '/purchase/payment/confirm',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

// 上传凭证
export function uploadVoucher(data: FormData) {
  return request({
    url: '/purchase/payment/upload-voucher',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

// 查询待付款的订单列表
export function getPendingPaymentOrders() {
  return request({
    url: '/purchase/payment/pending-orders',
    method: 'get',
  })
}

// 根据订单ID查询付款记录
export function getPaymentsByOrder(orderId: number) {
  return request({
    url: `/purchase/payment/order/${orderId}`,
    method: 'get',
  })
}

// 根据供应商ID查询付款记录
export function getPaymentsBySupplier(supplierId: number) {
  return request({
    url: `/purchase/payment/supplier/${supplierId}`,
    method: 'get',
  })
}

// 查询待审批的付款列表
export function getPendingApprovalPayments() {
  return request({
    url: '/purchase/payment/pending-approval',
    method: 'get',
  })
}

// 查询已审批的付款列表
export function getApprovedPayments() {
  return request({
    url: '/purchase/payment/approved',
    method: 'get',
  })
}

// 查询今日付款记录
export function getTodayPayments() {
  return request({
    url: '/purchase/payment/today',
    method: 'get',
  })
}

// 查询本周付款记录
export function getWeekPayments() {
  return request({
    url: '/purchase/payment/week',
    method: 'get',
  })
}

// 查询本月付款记录
export function getMonthPayments() {
  return request({
    url: '/purchase/payment/month',
    method: 'get',
  })
}

// 获取付款统计信息
export function getPaymentStatistics() {
  return request({
    url: '/purchase/payment/statistics',
    method: 'get',
  })
}

// 批量付款
export function batchPayment(data: PurchasePayment[]) {
  return request({
    url: '/purchase/payment/batch',
    method: 'post',
    data,
  })
}

// 批量审批
export function batchApprove(data: { paymentId: number; approvalStatus: string; approverName: string }[]) {
  return request({
    url: '/purchase/payment/batch-approve',
    method: 'post',
    data,
  })
}

// 导入付款数据
export function importPayment(data: Record<string, unknown>) {
  return request({
    url: '/purchase/payment/import',
    method: 'post',
    data,
  })
}

// 下载付款导入模板
export function importTemplate() {
  return request({
    url: '/purchase/payment/import-template',
    method: 'get',
    responseType: 'blob',
  })
}

// 检查付款单号是否唯一
export function checkPaymentNoUnique(paymentNo: string) {
  return request({
    url: '/purchase/payment/check-payment-no-unique',
    method: 'get',
    params: { paymentNo },
  })
}

// 生成付款单号
export function generatePaymentNo() {
  return request({
    url: '/purchase/payment/generate-payment-no',
    method: 'get',
  })
}

// 获取付款提醒
export function getPaymentReminders() {
  return request({
    url: '/purchase/payment/reminders',
    method: 'get',
  })
}

// 获取逾期付款列表
export function getOverduePayments() {
  return request({
    url: '/purchase/payment/overdue',
    method: 'get',
  })
}

// 获取付款趋势分析
export function getPaymentTrendAnalysis(params?: Record<string, unknown>) {
  return request({
    url: '/purchase/payment/trend-analysis',
    method: 'get',
    params,
  })
}

// 获取供应商付款分析
export function getSupplierPaymentAnalysis(supplierId?: number) {
  return request({
    url: '/purchase/payment/supplier-analysis',
    method: 'get',
    params: { supplierId },
  })
}
