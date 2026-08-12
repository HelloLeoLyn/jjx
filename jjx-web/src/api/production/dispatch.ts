import request from '@/utils/request'

export interface DispatchQuery {
  pageNum?: number
  pageSize?: number
  orderNo?: string
  teamId?: number
  status?: number
  keyword?: string
}

export interface DispatchVO {
  dispatchId: number
  orderId: number
  orderNo?: string
  executionId: number
  processName?: string
  processOrder?: number
  teamId?: number
  teamName?: string
  equipmentId?: number
  equipmentName?: string
  operators?: string
  assignedBy?: number
  assignedByName?: string
  assignTime?: string
  status: number
  statusLabel?: string
  rejectReason?: string
  reDispatchCount?: number
  remark?: string
  createTime?: string
}

export interface DispatchLog {
  logId: number
  dispatchId: number
  orderId?: number
  action: string
  content?: string
  operatorId?: number
  operatorName?: string
  createTime?: string
}

export interface DispatchAssignPayload {
  dispatchId?: number
  orderId?: number
  executionId?: number
  teamId?: number
  equipmentId?: number
  operatorIds?: number[]
  remark?: string
  batch?: boolean
}

// 分页查询
export function getDispatchPage(params: DispatchQuery) {
  return request({
    url: '/production/dispatch/page',
    method: 'get',
    params,
  })
}

// 工单全部派工单
export function getDispatchByOrder(orderId: number) {
  return request({
    url: `/production/dispatch/order/${orderId}`,
    method: 'get',
  })
}

// 派工流水
export function getDispatchLogs(dispatchId: number) {
  return request({
    url: `/production/dispatch/${dispatchId}/logs`,
    method: 'get',
  })
}

// 单工序指派/改派
export function assignDispatch(data: DispatchAssignPayload) {
  return request({
    url: '/production/dispatch/assign',
    method: 'post',
    data,
  })
}

// 工单批量派工
export function batchAssignDispatch(data: DispatchAssignPayload) {
  return request({
    url: '/production/dispatch/batch-assign',
    method: 'post',
    data,
  })
}

// 退回
export function rejectDispatch(dispatchId: number, reason: string) {
  return request({
    url: `/production/dispatch/${dispatchId}/reject`,
    method: 'post',
    data: { reason },
  })
}

// 开始
export function startDispatch(dispatchId: number) {
  return request({
    url: `/production/dispatch/${dispatchId}/start`,
    method: 'post',
  })
}

// 完成
export function completeDispatch(dispatchId: number) {
  return request({
    url: `/production/dispatch/${dispatchId}/complete`,
    method: 'post',
  })
}

// 工单级责任班组/负责人
export function updateOrderDispatchTeam(orderId: number, teamId?: number, leaderId?: number) {
  return request({
    url: `/production/dispatch/order/${orderId}/team`,
    method: 'put',
    data: { teamId, leaderId },
  })
}
