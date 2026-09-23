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

// ==================== 2026-09-23（dev-20260923-004）方案 A2：收货域批量收货 + 收货票据 ====================
// 背景：原来收货只能逐条（confirm/{itemId}），多明细一次收只能借用订单域端点 POST /purchase/order/{id}/receive
//（后端权限是 purchase:order:edit，与前端收货按钮的 purchase:receipt:add 错位）。本组接口把"收货"收口到收货域。

/**
 * 批量收货（收货域主入口）
 * 一次可收多个明细，明细入库单按"一次收货"粒度生成一张；后端权限 purchase:receipt:add
 */
export function confirmBatchReceive(
  orderId: number,
  items: { itemId: number; receivedQuantity: number }[],
) {
  return request({
    url: '/purchase/receipt/confirm-batch',
    method: 'post',
    params: { orderId },
    data: { items },
  })
}

/** 本订单收货生成的入库单（只读，倒序；用于提示入库单号与跳转来料检验） */
export function getReceiptInboundOrders(orderId: number) {
  return request({
    url: `/purchase/receipt/inbound-orders/${orderId}`,
    method: 'get',
  })
}

/** 上传收货票据（临时落盘，确认收货时入库） */
export function uploadReceiptDocTemp(orderId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: `/purchase/receipt/doc/upload-temp/${orderId}`,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 收货票据临时文件列表（扫磁盘目录） */
export function getReceiptDocDiskFiles(orderId: number) {
  return request({
    url: `/purchase/receipt/doc/disk-files/${orderId}`,
    method: 'get',
  })
}

/** 删除收货票据临时文件 */
export function deleteReceiptDocTemp(fileUrl: string) {
  return request({
    url: '/purchase/receipt/doc/temp-file',
    method: 'delete',
    params: { fileUrl },
  })
}

/**
 * 收货票据落库
 * supplierId 由服务端从订单解析，documentType 固定 receipt（与采购发票票据区分）
 */
export function confirmReceiptDocs(
  orderId: number,
  files: { fileName: string; fileUrl: string; fileSize: number }[],
) {
  return request({
    url: '/purchase/receipt/doc/batch-confirm',
    method: 'post',
    data: { orderId, files },
  })
}
