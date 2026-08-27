// 生产任务（统一任务责任树）类型定义 —— P1 Foundation + P2 Task Flow
// 第一层与所有下级完全同构，唯一差异是 parentTaskId 值

// 任务状态（P5 生命周期，与 assignee_id 解耦）
// PENDING=尚未进入责任执行 / ACTIVE=已进入责任执行 / COMPLETED=人工确认完成 / CANCELLED=责任取消或归零
export type ProductionTaskStatus = 'PENDING' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED'

export interface MyProductionExecution {
  executionId: number
  orderId?: number
  orderNo?: string
  orderStatus?: number
  processId?: number
  processName?: string
  processOrder?: number
  executionStatus?: number
  actualStartTime?: string
  equipmentId?: number
  equipmentCode?: string
  equipmentName?: string
  taskCount: number
  taskNo?: string
  plannedQuantity: number
  myResponsibilityQuantity: number
  myCompletedQuantity: number
  myPendingReviewQuantity: number
  myProcessableQuantity: number
  childCompletedQuantity: number
  childProcessingQuantity: number
  pendingMyApprovalQuantity: number
}

export interface ChildProcessingRecord {
  taskId: number
  taskNo?: string
  assigneeId?: number
  assigneeName?: string
  departmentName?: string
  taskQuantity: number
  completedQuantity: number
  pendingApprovalQuantity: number
  processingQuantity: number
  status?: ProductionTaskStatus
  statusLabel?: string
}

export interface ChildProcessingDetail {
  executionId: number
  orderNo?: string
  processName?: string
  executionStatus?: number
  myResponsibilityQuantity: number
  childCompletedQuantity: number
  childProcessingQuantity: number
  pendingMyApprovalQuantity: number
  records: ChildProcessingRecord[]
}

// P6 类型拆分（不再混用一个 TaskAction）：
// - TaskEventAction：production_task_event.action 业务事件（历史值 FIRST_ASSIGN/UNASSIGN 不再产生）
// - TaskAllowedAction：allowedActions 前端动作集合（比事件多一个纯 UI 动作 FLOW）
export type TaskEventAction = 'ASSIGN' | 'RECALL' | 'RETURN' | 'COMPLETE'
export type TaskAllowedAction = TaskEventAction | 'FLOW'

// 统一任务树行（第一层与所有下级同构；children 永远为数组，永不 null）
export interface TaskTreeRow {
  taskId: number
  taskNo?: string
  parentTaskId: number | null // null = 第一层真实任务（非 System Root）
  executionId: number
  orderNo?: string
  processName?: string
  processCode?: string
  processOrder?: number
  assigneeId?: number | null
  assigneeName?: string | null // null = 未分配
  /** 上级执行人姓名（任务来源展示） */
  parentAssigneeName?: string
  taskQuantity?: number
  /** P4：当前 Task 整棵有效子树 APPROVED WorkReport 合计（点击查看 completion-details） */
  completedQuantity?: number
  /** P4：当前 Task 整棵有效子树 PENDING WorkReport 合计 */
  pendingQuantity?: number
  /** P4 展示值：下游仍未 completed/pending 的有效责任量（写 gate 不使用本值） */
  assignedQuantity?: number
  /** gate 口径：taskQuantity - childAssigned - ownPending - ownCompleted（下限 0；唯一可分配/可报工额度） */
  remainingQuantity: number
  status?: ProductionTaskStatus
  statusLabel?: string
  /** 是否有直接子任务（懒加载：决定展开箭头；活动树排除 CANCELLED） */
  hasChildren: boolean
  /** 是否具备分配能力（有可分配下属；未分配=当前登录人，已分配=assignee） */
  canAssign?: boolean
  children: TaskTreeRow[]
  /** P5 后端统一投影：ASSIGN/RECALL/RETURN/COMPLETE/FLOW；前端只按此渲染动作 */
  allowedActions: TaskAllowedAction[]
}

// ==================== P2 请求 DTO ====================

export interface TaskAssignItem {
  assigneeId: number
  /** 分配数量；统一分配必填，须大于 0；允许部分分配，合计不超过任务剩余 */
  quantity?: number
}

export interface TaskAssignPayload {
  items: TaskAssignItem[]
  remark?: string
}

export interface TaskRecallPayload {
  childTaskId: number
  quantity: number
  remark?: string
}

export interface TaskReturnPayload {
  quantity: number
  remark?: string
}

export interface TaskCompletePayload {
  remark?: string
}

/** 候选责任树节点（树根 = 分配根用户；角色只作资格展示） */
export interface TaskCandidate {
  userId: number
  userName: string
  nickName?: string
  deptId?: number
  deptName?: string
  /** 主生产角色标识（dispatch_mgr/dispatch_leader/worker；Resolver 组织资格展示） */
  roleKey?: string
  /** 主生产角色名称 */
  roleName?: string
  /** 是否分配根节点（当前分配人，仅作为责任树入口） */
  root?: boolean
  /** 是否可以作为本次分配对象；根节点为 false，合法后代为 true */
  selectable?: boolean
  /** 下属责任树（空=叶子） */
  children?: TaskCandidate[]
}

// ==================== P6 流水事件（GET /production/tasks/{taskId}/events） ====================

export interface TaskEvent {
  eventId: number
  taskId: number
  relatedTaskId?: number | null
  action: TaskEventAction
  operatorId: number
  operatorName?: string
  fromAssigneeId?: number | null
  fromAssigneeName?: string | null
  toAssigneeId?: number | null
  toAssigneeName?: string | null
  quantity: number
  /** 唯一语义：event.taskId 的 task_quantity 动作前值 */
  beforeTaskQuantity: number
  /** 唯一语义：event.taskId 的 task_quantity 动作后值 */
  afterTaskQuantity: number
  remark?: string
  createTime?: string
}

// ==================== P4 完成明细（subtree 内 APPROVED WorkReport） ====================

export interface TaskCompletionDetail {
  reportId: number
  taskId: number
  taskAssigneeId?: number | null
  taskAssigneeName?: string | null
  reporterId?: number
  reporterName?: string
  executionId: number
  orderNo?: string
  processName?: string
  qualifiedQuantity?: number
  defectiveQuantity?: number
  /** 报工数量 = qualified + defective；SUM === 对应 TaskTreeRow.completedQuantity */
  reportQuantity?: number
  reportTime?: string
  reviewerName?: string
  reviewTime?: string
  remark?: string
}
