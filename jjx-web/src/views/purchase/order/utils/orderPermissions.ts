import type { PurchaseOrderVO } from '@/types/purchase/order'
import {
  isOrderEditable,
  isOrderApprovable,
  isOrderReceivable,
  isOrderPayable,
} from './orderFormatters'

/**
 * 采购订单权限检查工具函数
 */

/**
 * 检查用户是否有创建订单权限
 */
export function checkCreatePermission(): boolean {
  // 模拟权限检查，实际应从用户权限中获取
  return true
}

/**
 * 检查用户是否有编辑订单权限
 */
export function checkEditPermission(order: PurchaseOrderVO): boolean {
  if (!order) return false

  // 检查订单是否可编辑
  if (!isOrderEditable(order.approvalStatus)) {
    return false
  }

  // 模拟用户权限检查
  const userPermissions = ['purchase:order:edit'] // 模拟用户权限
  return userPermissions.includes('purchase:order:edit')
}

/**
 * 检查用户是否有删除订单权限
 */
export function checkDeletePermission(order: PurchaseOrderVO): boolean {
  if (!order) return false

  // 只有草稿(1)和已拒绝(5)的订单可以删除
  if (order.approvalStatus !== 1 && order.approvalStatus !== 5) {
    return false
  }

  // 模拟用户权限检查
  const userPermissions = ['purchase:order:delete'] // 模拟用户权限
  return userPermissions.includes('purchase:order:delete')
}

/**
 * 检查用户是否有审批订单权限
 */
export function checkApprovePermission(order: PurchaseOrderVO): boolean {
  if (!order) return false

  // 检查订单是否可审批
  if (!isOrderApprovable(order.approvalStatus)) {
    return false
  }

  // 模拟用户权限检查
  const userPermissions = ['purchase:order:approve'] // 模拟用户权限
  return userPermissions.includes('purchase:order:approve')
}

/**
 * 检查用户是否有收货权限
 */
export function checkReceivePermission(order: PurchaseOrderVO): boolean {
  if (!order) return false

  // 检查订单是否可收货（已批准且未完全收货）
  if (!isOrderReceivable(order.approvalStatus, order.receiptStatus)) {
    return false
  }

  // 模拟用户权限检查
  const userPermissions = ['purchase:order:receive'] // 模拟用户权限
  return userPermissions.includes('purchase:order:receive')
}

/**
 * 检查用户是否有付款权限
 */
export function checkPaymentPermission(order: PurchaseOrderVO): boolean {
  if (!order) return false

  // 检查订单是否可付款（已批准且未完全付款）
  if (!isOrderPayable(order.approvalStatus, order.paymentStatus)) {
    return false
  }

  // 模拟用户权限检查
  const userPermissions = ['purchase:order:payment'] // 模拟用户权限
  return userPermissions.includes('purchase:order:payment')
}

/**
 * 检查用户是否有查看详情权限
 */
export function checkViewPermission(): boolean {
  // 模拟权限检查
  return true
}

/**
 * 检查用户是否有导出权限
 */
export function checkExportPermission(): boolean {
  // 模拟权限检查
  const userPermissions = ['purchase:order:export'] // 模拟用户权限
  return userPermissions.includes('purchase:order:export')
}

/**
 * 检查批量操作权限
 */
export function checkBatchOperationPermission(
  orders: PurchaseOrderVO[],
  operation: 'delete' | 'approve' | 'receive' | 'payment'
): boolean {
  if (!orders || orders.length === 0) return false

  // 检查所有订单是否都有相应权限
  for (const order of orders) {
    let hasPermission = false

    switch (operation) {
      case 'delete':
        hasPermission = checkDeletePermission(order)
        break
      case 'approve':
        hasPermission = checkApprovePermission(order)
        break
      case 'receive':
        hasPermission = checkReceivePermission(order)
        break
      case 'payment':
        hasPermission = checkPaymentPermission(order)
        break
    }

    if (!hasPermission) {
      return false
    }
  }

  return true
}

/**
 * 获取订单可执行的操作列表
 */
export function getAvailableActions(order: PurchaseOrderVO): string[] {
  const actions: string[] = []

  if (!order) return actions

  // 查看详情
  if (checkViewPermission()) {
    actions.push('view')
  }

  // 编辑
  if (checkEditPermission(order)) {
    actions.push('edit')
  }

  // 删除
  if (checkDeletePermission(order)) {
    actions.push('delete')
  }

  // 审批
  if (checkApprovePermission(order)) {
    actions.push('approve')
  }

  // 收货
  if (checkReceivePermission(order)) {
    actions.push('receive')
  }

  // 付款
  if (checkPaymentPermission(order)) {
    actions.push('payment')
  }

  // 复制
  if (checkCreatePermission()) {
    actions.push('copy')
  }

  // 导出
  if (checkExportPermission()) {
    actions.push('export')
  }

  return actions
}

/**
 * 检查订单是否可批量操作
 */
export function isBatchOperationAvailable(orders: PurchaseOrderVO[], operation: string): boolean {
  if (!orders || orders.length === 0) return false

  switch (operation) {
    case 'delete':
      return checkBatchOperationPermission(orders, 'delete')
    case 'approve':
      return checkBatchOperationPermission(orders, 'approve')
    case 'receive':
      return checkBatchOperationPermission(orders, 'receive')
    case 'payment':
      return checkBatchOperationPermission(orders, 'payment')
    default:
      return false
  }
}

/**
 * 获取批量操作提示信息
 */
export function getBatchOperationHint(orders: PurchaseOrderVO[], operation: string): string {
  if (!orders || orders.length === 0) {
    return '请先选择订单'
  }

  const count = orders.length

  switch (operation) {
    case 'delete':
      return `确定要删除 ${count} 个订单吗？`
    case 'approve':
      return `确定要审批 ${count} 个订单吗？`
    case 'receive':
      return `确定要收货 ${count} 个订单吗？`
    case 'payment':
      return `确定要付款 ${count} 个订单吗？`
    default:
      return `确定要执行 ${operation} 操作吗？`
  }
}
