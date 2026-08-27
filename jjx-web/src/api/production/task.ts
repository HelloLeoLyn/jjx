import request from '@/utils/request'
import type {
  TaskCandidate,
  TaskTreeRow,
  TaskCompletionDetail,
  TaskEvent,
  TaskAssignPayload,
  TaskRecallPayload,
  TaskReturnPayload,
  TaskCompletePayload,
  MyProductionExecution,
  ChildProcessingDetail,
} from '@/types/production/task'
import type { PageResult, R } from '@/types'

// 生产任务（统一任务责任树）API —— P1 Foundation + P2 Task Flow

export interface TaskTreeQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: string
}

export interface MyProductionExecutionQuery {
  pageNum?: number
  pageSize?: number
  orderNo?: string
  processName?: string
  executionStatus?: number | ''
  equipmentId?: number
}

// ==================== P1 只读 ====================

// 第一层任务分页（parent_task_id IS NULL；生产全局=全部，普通用户=本人持有；活动树排除 CANCELLED）
export function getTaskTreePage(params: TaskTreeQuery) {
  return request.get<R<PageResult<TaskTreeRow>>>('/production/tasks/page', { params })
}

// 任务详情
export function getTaskDetail(taskId: number) {
  return request.get<R<TaskTreeRow>>(`/production/tasks/${taskId}`)
}

// 按 Execution 获取唯一 First Task 责任与数量投影
export function getExecutionRootTask(executionId: number) {
  return request.get<R<TaskTreeRow>>(`/production/tasks/execution/${executionId}/root`)
}

// 直接子任务（真懒加载：每次只查 parent_task_id = taskId 的一层，排除 CANCELLED）
export function getTaskChildren(taskId: number) {
  return request.get<R<TaskTreeRow[]>>(`/production/tasks/${taskId}/children`)
}

// 完成明细（P4：当前 Task 有效子树内全部 APPROVED WorkReport；SUM(reportQuantity) === completedQuantity）
export function getTaskCompletionDetails(taskId: number) {
  return request.get<R<TaskCompletionDetail[]>>(`/production/tasks/${taskId}/completion-details`)
}

// 任务流转流水（P6：task_id 或 related_task_id = 当前任务；按时间倒序）
export function getTaskEvents(taskId: number) {
  return request.get<R<TaskEvent[]>>(`/production/tasks/${taskId}/events`)
}

// 我的任务（P6 报工入口：assignee_id = 当前登录人；可空 executionId 收窄）
export function getMyTasks(executionId?: number) {
  return request.get<R<TaskTreeRow[]>>('/production/tasks/mine', {
    params: executionId ? { executionId } : undefined,
  })
}

export function getMyProductionExecutions(params: MyProductionExecutionQuery) {
  return request.get<R<PageResult<MyProductionExecution>>>('/production/tasks/my-executions', { params })
}

export function getProductionExecutionScope() {
  return request.get<R<{ global: boolean }>>('/production/tasks/execution-scope')
}

export function getMyChildProcessingDetail(executionId: number) {
  return request.get<R<ChildProcessingDetail>>(`/production/tasks/my-executions/${executionId}/children`)
}

// ==================== P2 Task Flow ====================

// 可分配候选人员
export function getTaskCandidates(taskId: number) {
  return request.get<R<TaskCandidate[]>>(`/production/tasks/${taskId}/candidates`)
}

// 任务分配（每个层级同构：一次事务创建全部 Child，可多人、逐人拆量，合计不超过剩余）
export function assignTask(taskId: number, data: TaskAssignPayload) {
  return request({
    url: `/production/tasks/${taskId}/assign`,
    method: 'post',
    data,
  })
}

// 收回（父执行人从直接 Child 拿回；禁止跨树/越级）
export function recallTask(parentTaskId: number, data: TaskRecallPayload) {
  return request({
    url: `/production/tasks/${parentTaskId}/recall`,
    method: 'post',
    data,
  })
}

// 退回（当前执行人把自身剩余退给父任务；第一层禁止）
export function returnTask(taskId: number, data: TaskReturnPayload) {
  return request({
    url: `/production/tasks/${taskId}/return`,
    method: 'post',
    data,
  })
}

// 人工确认完成（P5 自底向上确认链；完成后禁止 assign/recall/return/report）
export function completeTask(taskId: number, data?: TaskCompletePayload) {
  return request({
    url: `/production/tasks/${taskId}/complete`,
    method: 'post',
    data: data ?? {},
  })
}
