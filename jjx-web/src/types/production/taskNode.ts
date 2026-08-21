// 生产任务树（TaskNode）类型定义 —— P1/P2/P3 统一模型，替代旧 Dispatch/Assignment

// 任务节点状态（后端动态投影，不落库）
export type TaskNodeStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED'

// 任务节点 VO（树形：children 递归）
export interface TaskNodeVO {
  taskNodeId: number
  executionId: number
  parentNodeId?: number | null
  assigneeId?: number
  assigneeName?: string
  taskQuantity?: number
  recalledQuantity?: number
  /** 本人有效报工量（SUBMITTED WorkReport 动态汇总） */
  selfReported?: number
  /** 已下分给直接子节点的有效数量 */
  childOccupied?: number
  status?: TaskNodeStatus
  statusLabel?: string
  /** selfRemaining：effective - childOccupied - selfReported */
  remainingQuantity?: number
  availableToAssign?: number
  children?: TaskNodeVO[]
}

// 我的任务节点 VO（工序执行页「我的当前任务/我已完成」）
export interface MyTaskNodeVO {
  taskNodeId: number
  executionId: number
  parentNodeId?: number | null
  assigneeId?: number
  assigneeName?: string
  taskQuantity?: number
  recalledQuantity?: number
  selfReported?: number
  childOccupied?: number
  selfRemaining?: number
  availableToAssign?: number
  status?: TaskNodeStatus
  statusLabel?: string
  orderId?: number
  orderNo?: string
  processName?: string
  processOrder?: number
  executionStatus?: number
  executionStatusDesc?: string
  executionInputQuantity?: number
}

// 分配任务候选人员（当前用户组织范围内）
export interface TaskCandidateVO {
  userId: number
  userName?: string
  nickName?: string
  deptId?: number
  deptName?: string
}

// 分配明细（一次可多人；合计不得超过父节点可分配数量）
export interface TaskAssignItem {
  userId: number
  quantity: number
}
