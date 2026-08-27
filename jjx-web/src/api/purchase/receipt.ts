import request from '@/utils/request'
import type { ReceiptVO, ReceiptItemVO } from '@/types/purchase/receipt'

// 查询采购收货列表
export function listReceipt(params?: Record<string, unknown>) {
  return request({
    url: '/purchase/receipt/list',
    method: 'get',
    params,
  })
}

// 查询采购收货详细
export function getReceipt(receiptId: number) {
  return request({
    url: `/purchase/receipt/${receiptId}`,
    method: 'get',
  })
}

// 新增采购收货
export function addReceipt(data: Record<string, unknown>) {
  return request({
    url: '/purchase/receipt',
    method: 'post',
    data,
  })
}

// 修改采购收货
export function updateReceipt(data: Record<string, unknown>) {
  return request({
    url: '/purchase/receipt',
    method: 'put',
    data,
  })
}

// 删除采购收货
export function delReceipt(receiptIds: number | number[]) {
  return request({
    url: `/purchase/receipt/${receiptIds}`,
    method: 'delete',
  })
}

// 导出采购收货列表
export function exportReceipt(params?: Record<string, unknown>) {
  return request({
    url: '/purchase/receipt/export',
    method: 'get',
    params,
    responseType: 'blob',
  })
}

// 检验收货
export function inspectReceipt(
  receiptId: number,
  inspectionResult: string,
  inspectorName: string,
  inspectionDate: string,
  inspectionRemark?: string,
) {
  return request({
    url: `/purchase/receipt/inspect/${receiptId}`,
    method: 'put',
    params: {
      inspectionResult,
      inspectorName,
      inspectionDate,
      inspectionRemark,
    },
  })
}

// 确认收货
export function confirmReceipt(
  receiptId: number,
  receivedQuantity: number,
  receiverName: string,
  receiptDate: string,
  remark?: string,
) {
  return request({
    url: `/purchase/receipt/confirm/${receiptId}`,
    method: 'put',
    params: { receivedQuantity, receiverName, receiptDate, remark },
  })
}

// 查询待收货的订单列表
export function getPendingReceiptOrders() {
  return request({
    url: '/purchase/receipt/pending-orders',
    method: 'get',
  })
}

// 根据订单ID查询收货明细
export function getReceiptsByOrder(orderId: number) {
  return request({
    url: `/purchase/receipt/order/${orderId}`,
    method: 'get',
  })
}

// 根据物料ID查询收货记录
export function getReceiptsByMaterial(materialId: number) {
  return request({
    url: `/purchase/receipt/material/${materialId}`,
    method: 'get',
  })
}

// 根据供应商ID查询收货记录
export function getReceiptsBySupplier(supplierId: number) {
  return request({
    url: `/purchase/receipt/supplier/${supplierId}`,
    method: 'get',
  })
}

// 查询待检验的收货列表
export function getPendingInspectionReceipts() {
  return request({
    url: '/purchase/receipt/pending-inspection',
    method: 'get',
  })
}

// 查询已检验的收货列表
export function getInspectedReceipts() {
  return request({
    url: '/purchase/receipt/inspected',
    method: 'get',
  })
}

// 查询今日收货记录
export function getTodayReceipts() {
  return request({
    url: '/purchase/receipt/today',
    method: 'get',
  })
}

// 查询本周收货记录
export function getWeekReceipts() {
  return request({
    url: '/purchase/receipt/week',
    method: 'get',
  })
}

// 查询本月收货记录
export function getMonthReceipts() {
  return request({
    url: '/purchase/receipt/month',
    method: 'get',
  })
}

// 获取收货统计信息
export function getReceiptStatistics() {
  return request({
    url: '/purchase/receipt/statistics',
    method: 'get',
  })
}

// 批量收货
export function batchReceive(data: ReceiptItemVO[]) {
  return request({
    url: '/purchase/receipt/batch',
    method: 'post',
    data,
  })
}

// 批量检验
export function batchInspect(data: ReceiptItemVO[]) {
  return request({
    url: '/purchase/receipt/batch-inspect',
    method: 'post',
    data,
  })
}

// 导入收货数据
export function importReceipt(data: Record<string, unknown>) {
  return request({
    url: '/purchase/receipt/import',
    method: 'post',
    data,
  })
}

// 下载收货导入模板
export function importTemplate() {
  return request({
    url: '/purchase/receipt/import-template',
    method: 'get',
    responseType: 'blob',
  })
}
