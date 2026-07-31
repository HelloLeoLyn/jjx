import type {
  OrderStatus,
  ApprovalStatus,
  ExecutionStatus,
  Priority,
  PlanType,
  OrderType,
} from '@/types/production/order'

/**
 * 获取订单状态标签
 */
export function getStatusLabel(
  orderStatus: OrderStatus,
  approvalStatus?: ApprovalStatus,
  executionStatus?: ExecutionStatus
): string {
  switch (orderStatus) {
    case 0:
      return '草稿'
    case 1:
      return approvalStatus === 1
        ? '待审批'
        : approvalStatus === 2
          ? '已批准'
          : approvalStatus === 3
            ? '已拒绝'
            : '待审批'
    case 2:
      return '已批准'
    case 4:
      return '已排程'
    case 6:
      return executionStatus === 0
        ? '未开始'
        : executionStatus === 2
          ? '进行中'
          : executionStatus === 3
            ? '已暂停'
            : '进行中'
    case 8:
      return '已完成'
    case 9:
      return '已取消'
    default:
      return '未知'
  }
}

/**
 * 获取订单状态类型（用于标签颜色）
 */
export function getStatusType(
  orderStatus: OrderStatus,
  approvalStatus?: ApprovalStatus,
  executionStatus?: ExecutionStatus
): string {
  switch (orderStatus) {
    case 0:
      return 'info'
    case 1:
      return approvalStatus === 1
        ? 'warning'
        : approvalStatus === 2
          ? 'success'
          : approvalStatus === 3
            ? 'danger'
            : 'warning'
    case 2:
      return 'success'
    case 4:
      return 'primary'
    case 6:
      return executionStatus === 0
        ? 'info'
        : executionStatus === 2
          ? 'warning'
          : executionStatus === 3
            ? 'warning'
            : 'warning'
    case 8:
      return 'success'
    case 9:
      return 'danger'
    default:
      return 'info'
  }
}

/**
 * 获取优先级标签
 */
export function getPriorityLabel(priority: Priority): string {
  switch (priority) {
    case 'low':
      return '低'
    case 'medium':
      return '中'
    case 'high':
      return '高'
    case 'urgent':
      return '紧急'
    default:
      return '中'
  }
}

/**
 * 获取优先级标签类型（用于标签颜色）
 */
export function getPriorityTagType(priority: Priority): 'success' | 'warning' | 'danger' | 'info' {
  switch (priority) {
    case 'low':
      return 'success'
    case 'medium':
      return 'warning'
    case 'high':
      return 'danger'
    case 'urgent':
      return 'danger'
    default:
      return 'info'
  }
}

/**
 * 获取计划类型标签
 */
export function getPlanTypeLabel(planType?: PlanType): string {
  switch (planType) {
    case 'monthly':
      return '月计划'
    case 'weekly':
      return '周计划'
    case 'daily':
      return '日计划'
    case 'special':
      return '专项计划'
    default:
      return '未指定'
  }
}

/**
 * 获取订单类型标签
 */
export function getOrderTypeLabel(orderType: OrderType): string {
  switch (orderType) {
    case 'plan':
      return '生产计划'
    case 'work_order':
      return '生产工单'
    default:
      return '未知类型'
  }
}

/**
 * 计算生产进度百分比
 */
export function calculateProgress(completed: number, planned: number): number {
  if (planned <= 0) return 0
  const progress = Math.round((completed / planned) * 100)
  return Math.min(progress, 100)
}

/**
 * 格式化时间范围
 */
export function formatDateRange(startDate?: string, endDate?: string): string {
  if (!startDate && !endDate) return '未设置'
  if (startDate && !endDate) return `${startDate} ~ 未设置`
  if (!startDate && endDate) return `未设置 ~ ${endDate}`
  return `${startDate} ~ ${endDate}`
}

/**
 * 格式化数量信息
 */
export function formatQuantityInfo(
  planned: number,
  completed: number
): {
  plannedQuantity: number
  completedQuantity: number
  remainingQuantity: number
  progress: number
  progressLabel: string
} {
  const remainingQuantity = Math.max(0, planned - completed)
  const progress = calculateProgress(completed, planned)

  return {
    plannedQuantity: planned,
    completedQuantity: completed,
    remainingQuantity,
    progress,
    progressLabel: `${completed}/${planned}`,
  }
}

/**
 * 格式化订单编号显示
 */
export function formatOrderNoDisplay(
  orderNo: string,
  orderType: OrderType
): {
  orderNo: string
  typeLabel: string
  typeColor: string
} {
  const isPlan = orderType === 'plan'
  return {
    orderNo,
    typeLabel: isPlan ? '计划' : '工单',
    typeColor: isPlan ? 'primary' : 'warning',
  }
}
