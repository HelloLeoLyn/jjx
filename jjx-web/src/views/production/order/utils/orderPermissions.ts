import type { OrderStatus, OrderType } from '@/types/production/order'

/**
 * 判断订单是否可以转为工单
 */
export function canConvertToWorkOrder(order: {
  orderType: OrderType
  orderStatus: OrderStatus
  parentOrderId?: string
}): boolean {
  return (
    order.orderType === 'plan' &&
    order.orderStatus === 'approved' &&
    (!order.parentOrderId || order.parentOrderId === '')
  )
}

/**
 * 判断工单是否可以开始执行
 */
export function canStart(order: { orderType: OrderType; orderStatus: OrderStatus }): boolean {
  return order.orderType === 'work_order' && order.orderStatus === 'scheduled'
}

/**
 * 判断工单是否可以完成
 */
export function canComplete(order: { orderType: OrderType; orderStatus: OrderStatus }): boolean {
  return order.orderType === 'work_order' && order.orderStatus === 'in_progress'
}

/**
 * 判断订单是否可以取消
 */
export function canCancel(order: { orderStatus: OrderStatus }): boolean {
  return order.orderStatus !== 'completed' && order.orderStatus !== 'cancelled'
}

/**
 * 判断订单是否可以编辑
 */
export function canEdit(order: { orderStatus: OrderStatus }): boolean {
  return order.orderStatus === 'draft' || order.orderStatus === 'pending_approval'
}

/**
 * 判断订单是否可以删除
 */
export function canDelete(order: { orderStatus: OrderStatus }): boolean {
  return order.orderStatus === 'draft' || order.orderStatus === 'cancelled'
}

/**
 * 判断订单是否可以审批
 */
export function canApprove(order: { orderType: OrderType; orderStatus: OrderStatus }): boolean {
  return order.orderType === 'plan' && order.orderStatus === 'pending_approval'
}

/**
 * 判断订单是否可以查看详情
 */
export function canViewDetail(order: { orderStatus: OrderStatus }): boolean {
  return true // 所有状态都可以查看详情
}

/**
 * 获取订单可用的操作列表
 */
export function getAvailableActions(order: {
  orderType: OrderType
  orderStatus: OrderStatus
  parentOrderId?: string
}): string[] {
  const actions: string[] = []

  // 通用操作
  if (canViewDetail(order)) actions.push('view')
  if (canEdit(order)) actions.push('edit')
  if (canCancel(order)) actions.push('cancel')
  if (canDelete(order)) actions.push('delete')

  // 计划特有操作
  if (order.orderType === 'plan') {
    if (canConvertToWorkOrder(order)) actions.push('convert')
    if (canApprove(order)) actions.push('approve')
  }

  // 工单特有操作
  if (order.orderType === 'work_order') {
    if (canStart(order)) actions.push('start')
    if (canComplete(order)) actions.push('complete')
  }

  return actions
}

/**
 * 检查批量操作权限
 */
export function checkBatchOperationPermission(
  orders: Array<{
    orderType: OrderType
    orderStatus: OrderStatus
  }>,
  operation: string
): {
  allowed: boolean
  message?: string
} {
  if (orders.length === 0) {
    return { allowed: false, message: '请选择要操作的订单' }
  }

  // 检查所有订单是否支持该操作
  for (const order of orders) {
    const availableActions = getAvailableActions(order)
    if (!availableActions.includes(operation)) {
      return {
        allowed: false,
        message: `订单 ${order.orderType === 'plan' ? '计划' : '工单'} 状态不支持该操作`,
      }
    }
  }

  return { allowed: true }
}

/**
 * 根据订单状态获取操作按钮配置
 */
export function getActionButtonConfig(order: {
  orderType: OrderType
  orderStatus: OrderStatus
  parentOrderId?: string
}): Array<{
  action: string
  label: string
  type: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  icon: string
  tooltip: string
}> {
  const config: Array<{
    action: string
    label: string
    type: 'primary' | 'success' | 'warning' | 'danger' | 'info'
    icon: string
    tooltip: string
  }> = []

  const availableActions = getAvailableActions(order)

  // 定义操作按钮配置
  const actionConfigs = {
    view: { label: '查看', type: 'primary' as const, icon: 'View', tooltip: '查看详情' },
    edit: { label: '编辑', type: 'primary' as const, icon: 'Edit', tooltip: '编辑订单' },
    convert: {
      label: '转工单',
      type: 'success' as const,
      icon: 'RefreshRight',
      tooltip: '转为生产工单',
    },
    start: { label: '开始', type: 'warning' as const, icon: 'VideoPlay', tooltip: '开始执行' },
    complete: { label: '完成', type: 'success' as const, icon: 'CircleCheck', tooltip: '完成工单' },
    cancel: { label: '取消', type: 'danger' as const, icon: 'CircleClose', tooltip: '取消订单' },
    approve: { label: '审批', type: 'success' as const, icon: 'Check', tooltip: '审批计划' },
    delete: { label: '删除', type: 'danger' as const, icon: 'Delete', tooltip: '删除订单' },
  }

  // 添加可用的操作按钮
  for (const action of availableActions) {
    if (actionConfigs[action as keyof typeof actionConfigs]) {
      config.push({
        action,
        ...actionConfigs[action as keyof typeof actionConfigs],
      })
    }
  }

  return config
}
