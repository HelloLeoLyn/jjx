import request from '@/utils/request'
import type { R } from '@/types'
import type { TaskNodeVO, MyTaskNodeVO, TaskCandidateVO, TaskAssignItem } from '@/types/production/taskNode'

// 生产任务树 API（P1/P2/P3）
export const taskNodeApi = {
  /** 工序执行任务树（第一次访问自动建立根节点） */
  getTree(executionId: number) {
    return request.get<R<TaskNodeVO>>(`/production/task-node/execution/${executionId}/tree`)
  },

  /** 分配任务给下级（创建子节点；一次可多人，合计不得超过父节点可分配数量） */
  assign(parentNodeId: number, items: TaskAssignItem[]) {
    return request.post<R<TaskNodeVO[]>>(`/production/task-node/${parentNodeId}/assign`, items)
  },

  /** 收回直接子节点部分剩余任务 */
  recall(childNodeId: number, quantity: number) {
    return request.post<R<any>>(`/production/task-node/${childNodeId}/recall`, { quantity })
  },

  /** 退回部分剩余任务给父节点 */
  returnNode(nodeId: number, quantity: number) {
    return request.post<R<any>>(`/production/task-node/${nodeId}/return`, { quantity })
  },

  /** 我的任务节点（当前用户持有） */
  my() {
    return request.get<R<MyTaskNodeVO[]>>('/production/task-node/my')
  },

  /** 分配任务候选人员（当前用户部门子树内成员） */
  candidates() {
    return request.get<R<TaskCandidateVO[]>>('/production/task-node/candidates')
  },
}
