import request from '@/utils/request'

// 查询采购发票列表
export function listInvoice(params?: Record<string, unknown>) {
  return request({
    url: '/purchase/invoice/list',
    method: 'get',
    params,
  })
}

// 查询采购发票详细
export function getInvoice(invoiceId: number) {
  return request({
    url: `/purchase/invoice/${invoiceId}`,
    method: 'get',
  })
}

// 新增采购发票
export function addInvoice(data: FormData) {
  return request({
    url: '/purchase/invoice',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

// 修改采购发票
export function updateInvoice(data: FormData) {
  return request({
    url: '/purchase/invoice',
    method: 'put',
    data,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

// 删除采购发票
export function delInvoice(invoiceIds: number | number[]) {
  return request({
    url: `/purchase/invoice/${invoiceIds}`,
    method: 'delete',
  })
}

// 导出采购发票列表
export function exportInvoice(params?: Record<string, unknown>) {
  return request({
    url: '/purchase/invoice/export',
    method: 'get',
    params,
    responseType: 'blob',
  })
}

// 核销发票
export function verifyInvoice(
  invoiceId: number,
  verificationDate: string,
  verifierName: string,
  verificationRemark?: string,
) {
  return request({
    url: `/purchase/invoice/verify/${invoiceId}`,
    method: 'put',
    params: { verificationDate, verifierName, verificationRemark },
  })
}

// 查询待开票的订单列表
export function getPendingInvoiceOrders() {
  return request({
    url: '/purchase/invoice/pending-orders',
    method: 'get',
  })
}

// 根据订单ID查询发票记录
export function getInvoicesByOrder(orderId: number) {
  return request({
    url: `/purchase/invoice/order/${orderId}`,
    method: 'get',
  })
}

// 根据供应商ID查询发票记录
export function getInvoicesBySupplier(supplierId: number) {
  return request({
    url: `/purchase/invoice/supplier/${supplierId}`,
    method: 'get',
  })
}

// 查询待核销的发票列表
export function getPendingVerificationInvoices() {
  return request({
    url: '/purchase/invoice/pending-verification',
    method: 'get',
  })
}

// 查询已核销的发票列表
export function getVerifiedInvoices() {
  return request({
    url: '/purchase/invoice/verified',
    method: 'get',
  })
}

// 查询今日开票记录
export function getTodayInvoices() {
  return request({
    url: '/purchase/invoice/today',
    method: 'get',
  })
}

// 查询本周开票记录
export function getWeekInvoices() {
  return request({
    url: '/purchase/invoice/week',
    method: 'get',
  })
}

// 查询本月开票记录
export function getMonthInvoices() {
  return request({
    url: '/purchase/invoice/month',
    method: 'get',
  })
}

// 获取发票统计信息
export function getInvoiceStatistics() {
  return request({
    url: '/purchase/invoice/statistics',
    method: 'get',
  })
}

// 批量核销
export function batchVerify(data: Record<string, unknown>[]) {
  return request({
    url: '/purchase/invoice/batch-verify',
    method: 'post',
    data,
  })
}

// 导入发票数据
export function importInvoice(data: FormData) {
  return request({
    url: '/purchase/invoice/import',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

// 下载发票导入模板
export function importTemplate() {
  return request({
    url: '/purchase/invoice/import-template',
    method: 'get',
    responseType: 'blob',
  })
}

// 检查发票号码是否唯一
export function checkInvoiceNoUnique(invoiceNo: string) {
  return request({
    url: '/purchase/invoice/check-invoice-no-unique',
    method: 'get',
    params: { invoiceNo },
  })
}

// 生成发票号码
export function generateInvoiceNo() {
  return request({
    url: '/purchase/invoice/generate-invoice-no',
    method: 'get',
  })
}

// 获取发票提醒
export function getInvoiceReminders() {
  return request({
    url: '/purchase/invoice/reminders',
    method: 'get',
  })
}

// 获取逾期未开票列表
export function getOverdueInvoices() {
  return request({
    url: '/purchase/invoice/overdue',
    method: 'get',
  })
}

// 获取发票趋势分析
export function getInvoiceTrendAnalysis(params?: Record<string, unknown>) {
  return request({
    url: '/purchase/invoice/trend-analysis',
    method: 'get',
    params,
  })
}

// 获取供应商发票分析
export function getSupplierInvoiceAnalysis(supplierId?: number) {
  return request({
    url: '/purchase/invoice/supplier-analysis',
    method: 'get',
    params: { supplierId },
  })
}

// 下载发票文件
export function downloadInvoiceFile(invoiceId: number) {
  return request({
    url: `/purchase/invoice/download/${invoiceId}`,
    method: 'get',
    responseType: 'blob',
  })
}

// 预览发票文件
export function previewInvoiceFile(invoiceId: number) {
  return request({
    url: `/purchase/invoice/preview/${invoiceId}`,
    method: 'get',
  })
}

// 批量下载发票文件
export function batchDownloadInvoiceFiles(invoiceIds: number[]) {
  return request({
    url: '/purchase/invoice/batch-download',
    method: 'post',
    data: { invoiceIds },
    responseType: 'blob',
  })
}

// 批量删除发票文件
export function batchDeleteInvoiceFiles(invoiceIds: number[]) {
  return request({
    url: '/purchase/invoice/batch-delete-files',
    method: 'post',
    data: { invoiceIds },
  })
}

// 获取发票类型统计
export function getInvoiceTypeStatistics() {
  return request({
    url: '/purchase/invoice/type-statistics',
    method: 'get',
  })
}

// 获取发票状态统计
export function getInvoiceStatusStatistics() {
  return request({
    url: '/purchase/invoice/status-statistics',
    method: 'get',
  })
}

// 获取月度发票统计
export function getMonthlyInvoiceStatistics(year?: number) {
  return request({
    url: '/purchase/invoice/monthly-statistics',
    method: 'get',
    params: { year },
  })
}

// 获取季度发票统计
export function getQuarterlyInvoiceStatistics(year?: number) {
  return request({
    url: '/purchase/invoice/quarterly-statistics',
    method: 'get',
    params: { year },
  })
}

// 获取年度发票统计
export function getYearlyInvoiceStatistics(
  startYear?: number,
  endYear?: number,
) {
  return request({
    url: '/purchase/invoice/yearly-statistics',
    method: 'get',
    params: { startYear, endYear },
  })
}

/** 上传临时发票文件（按订单） */
export function uploadInvoiceTemp(orderId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: `/purchase/invoice/upload-temp/${orderId}`,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 查询订单的磁盘票据文件列表 */
export function getInvoiceDiskFiles(orderId: number) {
  return request({
    url: `/purchase/invoice/disk-files/${orderId}`,
    method: 'get',
  })
}

/** 批量确认发票（临时文件落库生成发票记录） */
export function batchConfirmInvoice(orderId: number, supplierId: number, files: Record<string, unknown>[]) {
  return request({
    url: '/purchase/invoice/batch-confirm',
    method: 'post',
    data: { orderId, supplierId, files },
  })
}

/** 删除临时发票文件 */
export function deleteInvoiceTempFile(fileUrl: string) {
  return request({
    url: '/purchase/invoice/temp-file',
    method: 'delete',
    params: { fileUrl },
  })
}
