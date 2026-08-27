import request from '@/utils/request'

export function getPurchaseReport(params?: { startDate?: string; endDate?: string }) {
  return request({ url: '/purchase/order/statistics', method: 'get', params })
}

export function getSupplierReport() {
  return request({ url: '/purchase/supplier/statistics', method: 'get' })
}
