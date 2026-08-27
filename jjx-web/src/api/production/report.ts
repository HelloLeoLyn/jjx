import request from '@/utils/request'

export function getOutputReport(params?: { startDate?: string; endDate?: string }) {
  return request({ url: '/production/report/output', method: 'get', params })
}

export function getEfficiencyReport(params?: { startDate?: string; endDate?: string }) {
  return request({ url: '/production/report/efficiency', method: 'get', params })
}

export function getQualityReport(params?: { startDate?: string; endDate?: string }) {
  return request({ url: '/production/report/quality', method: 'get', params })
}
