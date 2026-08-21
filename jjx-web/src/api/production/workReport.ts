import request from '@/utils/request'

// ============ P2 WorkReport V1 类型 ============

export type WorkReportStatus = 'SUBMITTED' | 'CANCELLED'

export interface WorkReportVO {
  reportId: number
  orderId?: number
  orderNo?: string
  executionId: number
  reporterId?: number
  reporterName?: string
  equipmentId?: number
  equipmentName?: string
  qualifiedQuantity?: number
  defectiveQuantity?: number
  laborHours?: number
  machineHours?: number
  workStartTime?: string
  workEndTime?: string
  reportTime?: string
  defectReason?: string
  remark?: string
  reportStatus?: WorkReportStatus
  reportStatusLabel?: string
  cancelledByName?: string
  cancelledAt?: string
  cancelReason?: string
}

export interface WorkReportSubmitPayload {
  executionId: number
  qualifiedQuantity: number
  defectiveQuantity: number
  laborHours?: number
  machineHours?: number
  workStartTime?: string
  workEndTime?: string
  equipmentId?: number
  defectReason?: string
  remark?: string
}

export interface WorkReportCancelPayload {
  cancelReason: string
}

// ============ API ============

// 提交报工（SUBMIT）
export function submitWorkReport(data: WorkReportSubmitPayload) {
  return request({
    url: '/production/work-report',
    method: 'post',
    data,
  })
}

// 撤销报工（CANCEL）
export function cancelWorkReport(reportId: number, data: WorkReportCancelPayload) {
  return request({
    url: `/production/work-report/${reportId}/cancel`,
    method: 'post',
    data,
  })
}

// 报工历史（含已撤销）
export function getWorkReportsByExecution(executionId: number) {
  return request({
    url: `/production/work-report/execution/${executionId}`,
    method: 'get',
  })
}

// 报工单条详情
export function getWorkReport(reportId: number) {
  return request({
    url: `/production/work-report/${reportId}`,
    method: 'get',
  })
}
