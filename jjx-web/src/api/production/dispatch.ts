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
  dispatchId?: number
  orderId: number
  orderNo?: string
  executionId: number
  processName?: string
  processOrder?: number
  majorCategory?: string
  executionStatus?: number
  dispatchStatus?: number
  plannedQuantity?: number
  teamId?: number
  teamName?: string
  equipmentId?: number
  equipmentName?: string
  operators?: string
  assignedBy?: number
  assignedByName?: string
  assignTime?: string
  status?: number
  statusLabel?: string
  rejectReason?: string
  reDispatchCount?: number
  remark?: string
  createTime?: string
  // P1-B/P1-D Node-first projection
  currentNodeId?: number
  currentAssigneeId?: number
  currentAssigneeName?: string
  currentOrgId?: number
  currentOrgName?: string
  assigneeSource?: string
  allowedActions?: string[]
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
  level?: number
  chainComplete?: boolean
  transferFrom?: number
  remark?: string
  batch?: boolean
}

// ============ P1-D V1 正式动作 DTO（无 level/transferFrom） ============

export interface DispatchAssignV1Payload {
  executionId: number
  orderId: number
  targetUserId: number
  equipmentId?: number
  remark?: string
}

export interface DispatchDelegatePayload {
  targetUserId: number
  remark?: string
}

export interface DispatchReassignPayload {
  targetUserId: number
  reason?: string
}

export interface DispatchReturnPayload {
  reason: string
}

export interface DispatchNodeVO {
  nodeId?: number
  dispatchId?: number
  parentNodeId?: number
  assigneeType?: string
  assigneeId?: number
  assigneeName?: string
  orgId?: number
  orgName?: string
  nodeStatus?: string
  assignedBy?: number
  assignedByName?: string
  assignedAt?: string
  closedAt?: string
  remark?: string
  source?: string
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

// 工单待派工序（未派工/已退回，批量派工计数用）
export function getPendingDispatches(orderId: number) {
  return request({
    url: `/production/dispatch/order/${orderId}/pending`,
    method: 'get',
  })
}

// 某人的手下（其负责部门+下级部门成员，转派候选）
export function getUnderlings(userId: number) {
  return request({
    url: `/production/dispatch/underlings/${userId}`,
    method: 'get',
  })
}

// 责任班组可选执行人（该部门及下级部门成员）
export function getTeamPersons(teamId: number) {
  return request({
    url: `/production/dispatch/team-persons/${teamId}`,
    method: 'get',
  })
}

// 当前用户可管辖部门树（负责部门+下级，超管全量；责任班组可选范围）
export function getMyDepts() {
  return request({
    url: '/production/dispatch/my-depts',
    method: 'get',
  })
}

// 当前用户可派工？（超管/生产负责人/被派工过）
export function getCanAssign() {
  return request({
    url: '/production/dispatch/can-assign',
    method: 'get',
  })
}

// 执行人候选（自己 + 手下，按部门树组织）
export function getMyPersons() {
  return request({
    url: '/production/dispatch/my-persons',
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

// 单工序指派/改派（Legacy adapter，V1 前端不使用）
export function assignDispatch(data: DispatchAssignPayload) {
  return request({
    url: '/production/dispatch/assign',
    method: 'post',
    data,
  })
}

// ============ P1-D V1 正式动作 API ============

// 初始派工（ASSIGN V1，无 level/transferFrom）
export function assignDispatchV1(data: DispatchAssignV1Payload) {
  return request({
    url: '/production/dispatch/assign-v1',
    method: 'post',
    data,
  })
}

// 继续派工（DELEGATE）
export function delegateDispatch(dispatchId: number, data: DispatchDelegatePayload) {
  return request({
    url: `/production/dispatch/${dispatchId}/delegate`,
    method: 'post',
    data,
  })
}

// 改派（REASSIGN）
export function reassignDispatch(dispatchId: number, data: DispatchReassignPayload) {
  return request({
    url: `/production/dispatch/${dispatchId}/reassign`,
    method: 'post',
    data,
  })
}

// 退回（RETURN）
export function returnDispatch(dispatchId: number, data: DispatchReturnPayload) {
  return request({
    url: `/production/dispatch/${dispatchId}/return`,
    method: 'post',
    data,
  })
}

// 责任链历史（Node-first）
export function getDispatchNodes(dispatchId: number) {
  return request({
    url: `/production/dispatch/${dispatchId}/nodes`,
    method: 'get',
  })
}

// 当前 ACTIVE 责任节点
export function getDispatchCurrentNode(dispatchId: number) {
  return request({
    url: `/production/dispatch/${dispatchId}/current-node`,
    method: 'get',
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
