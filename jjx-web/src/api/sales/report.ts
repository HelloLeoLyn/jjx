import request from '@/utils/request'

export function getSalesReport(params?: { startDate?: string; endDate?: string }) {
  return request({ url: '/sales/orders/statistics', method: 'get', params })
}

export function getCustomerReport() {
  return request({ url: '/sales/customers/statistics', method: 'get' })
}

export function getQuotationReport() {
  return request({ url: '/sales/quotation/statistics', method: 'get' })
}
