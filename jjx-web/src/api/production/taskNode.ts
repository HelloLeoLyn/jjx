import request from '@/utils/request'
import type { R } from '@/types'
import type { TaskNodeVO, MyTaskNodeVO, TaskCandidateVO, TaskAssignItem, TaskTreeEventVO } from '@/types/production/taskNode'

// 生产任务树 API（P1/P2/P3）
export const taskNodeApi = {
  /** 工序执行任务树（第一次访问自动建立根节点） */
  getTree(executionId: number) {
    return request.get<R<TaskNodeVO>>(`/production/task-node/execution/${executionId}/tree`)
  },

  /** 任务树懒加载：第一层任务节点（parentNodeId 为空）或指定节点的直接子节点；浏览不建根 */
  children(executionId: number, parentNodeId?: number | null) {
    return request.get<R<TaskNodeVO[]>>(`/production/task-node/execution/${executionId}/children`, {
      params: { parentNodeId: parentNodeId ?? undefined },
    })
  },

  /** 任务节点详情（单节点数量投影；视图范围校验） */
  detail(taskNodeId: number) {
    return request.get<R<TaskNodeVO>>(`/production/task-node/detail/${taskNodeId}`)
  },

  /** 分配任务给下级（创建子节点；一次可多人，合计不得超过父节点可分配数量） */
  assign(parentNodeId: number, items: TaskAssignItem[]) {
    return request.post<R<TaskNodeVO[]>>(`/production/task-node/${parentNodeId}/assign`, items)
  },

  /** 收回直接子节点部分剩余任务 */
  recall(childNodeId: number, quantity: number, remark?: string) {
    return request.post<R<any>>(`/production/task-node/${childNodeId}/recall`, { quantity, remark })
  },

  /** 退回部分剩余任务给父节点 */
  returnNode(nodeId: number, quantity: number, remark?: string) {
    return request.post<R<any>>(`/production/task-node/${nodeId}/return`, { quantity, remark })
  },

  /** 任务树完整操作流水（分配/收回/退回/报工/撤销报工） */
  events(executionId: number) {
    return request.get<R<TaskTreeEventVO[]>>(`/production/task-node/execution/${executionId}/events`)
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
