import request from '@/utils/request'

// ============ P3 WorkReport + Approval 类型 ============

export type WorkReportStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface WorkReportVO {
  reportId: number
  reportNo?: string
  orderId?: number
  orderNo?: string
  executionId: number
  /** 生产任务ID（非任务执行人提交时须持有代报权限） */
  taskId?: number
  reporterId?: number
  reporterName?: string
  proxyId?: number
  proxyName?: string
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
  pendingReviewerId?: number
  pendingReviewerName?: string
  /** P3 审批事实（approve/reject 落库；一笔只审批一次） */
  reviewerId?: number
  reviewerName?: string
  reviewTime?: string
  reviewRemark?: string
  cancelledByName?: string
  cancelledAt?: string
  cancelReason?: string
}

export interface WorkReportSubmitPayload {
  executionId: number
  /** 报工必须绑定 ProductionTask（本次数量 <= 当前剩余） */
  taskId: number
  /** 事实报工人ID（本人报工可空；代报时必填） */
  reporterId?: number
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

export interface WorkReportReviewPayload {
  /** 审批备注（驳回必填；通过可空） */
  reviewRemark?: string
}

export interface WorkReportQuery {
  pageNum?: number
  pageSize?: number
  status?: WorkReportStatus
  taskId?: number
  executionId?: number
}

export interface PageResult<T> {
  total: number
  records: T[]
  pageNum: number
  pageSize: number
  totalPages: number
}

// ============ API ============

// 提交报工（Task 执行人本人，或持 proxy 权限代报；INSERT PENDING）
export function submitWorkReport(data: WorkReportSubmitPayload) {
  return request({
    url: '/production/work-report',
    method: 'post',
    data,
  })
}

// 审批通过（PENDING→APPROVED）
export function approveWorkReport(reportId: number, data?: WorkReportReviewPayload) {
  return request({
    url: `/production/work-report/${reportId}/approve`,
    method: 'post',
    data,
  })
}

// 审批驳回（PENDING→REJECTED；驳回原因必填）
export function rejectWorkReport(reportId: number, data: WorkReportReviewPayload) {
  return request({
    url: `/production/work-report/${reportId}/reject`,
    method: 'post',
    data,
  })
}

// 撤销报工（PENDING→CANCELLED；APPROVED 不可撤销）
export function cancelWorkReport(reportId: number, data: WorkReportCancelPayload) {
  return request({
    url: `/production/work-report/${reportId}/cancel`,
    method: 'post',
    data,
  })
}

// 我的报工（分页）
export function getMyWorkReports(params: WorkReportQuery) {
  return request({
    url: '/production/work-report/mine',
    method: 'get',
    params,
  })
}

// 待我审批（分页；生产管理=全部 PENDING，普通=下级任务报工）
export function getPendingApprovalWorkReports(params: WorkReportQuery) {
  return request({
    url: '/production/work-report/pending-approval',
    method: 'get',
    params,
  })
}

// 报工历史（含已撤销/驳回）
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
