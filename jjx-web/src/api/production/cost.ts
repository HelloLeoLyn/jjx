import request from '@/utils/request'

export function getCostList(params?: { productId?: number; startDate?: string; endDate?: string }) {
  return request({ url: '/production/cost/list', method: 'get', params })
}

export function getCostSummary() {
  return request({ url: '/production/cost/summary', method: 'get' })
}
