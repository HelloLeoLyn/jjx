import request from '@/utils/request'

// ============ WP-B/WP-D 作业分配类型 ============

export type AssignmentStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED'

export interface AssignmentLineVO {
  assignmentId: number
  executionId: number
  orderId?: number
  dispatchId?: number
  dispatchNodeId?: number
  assigneeId: number
  assigneeName?: string
  assignedQuantity: number
  releasedQuantity: number
  effectiveQuantity: number
  reportedQuantity: number
  remainingQuantity: number
  derivedStatus: AssignmentStatus
  derivedStatusLabel?: string
  assignmentStatus?: string
  assignedBy?: number
  assignedByName?: string
  assignedAt?: string
  cancelledAt?: string
  cancelReason?: string
}

export interface AssignmentViewVO {
  executionId: number
  plannedQuantity: number
  assignedQuantity: number
  reportedQuantity: number
  unassignedQuantity: number
  assignments: AssignmentLineVO[]
}

export interface AssignmentItemDTO {
  assigneeId: number
  quantity: number
}

export interface AssignmentCreateDTO {
  executionId: number
  assignments: AssignmentItemDTO[]
}

export interface AssignmentReleaseDTO {
  reason: string
}

// ============ API ============

// 创建作业分配（一次多人，整批原子）
export function createAssignment(data: AssignmentCreateDTO) {
  return request({
    url: '/production/execution-assignment',
    method: 'post',
    data,
  })
}

// 释放作业剩余（部分报工后剩余回到未分配池）
export function releaseAssignment(assignmentId: number, data: AssignmentReleaseDTO) {
  return request({
    url: `/production/execution-assignment/${assignmentId}/release`,
    method: 'post',
    data,
  })
}

// 按工序查询分配视图
export function getAssignmentByExecution(executionId: number) {
  return request({
    url: `/production/execution-assignment/execution/${executionId}`,
    method: 'get',
  })
}
