/**
 * 采购订单格式化工具函数
 */

/**
 * 格式化货币金额
 */
export function formatCurrency(amount: number, currency: string = 'CNY'): string {
  if (amount === undefined || amount === null) return '0.00'

  const formatter = new Intl.NumberFormat('zh-CN', {
    style: 'currency',
    currency: currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })

  return formatter.format(amount)
}

/**
 * 格式化日期
 */
export function formatDate(date: string | Date, format: string = 'YYYY-MM-DD'): string {
  if (!date) return ''

  const d = typeof date === 'string' ? new Date(date) : date
  if (isNaN(d.getTime())) return ''

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  switch (format) {
    case 'YYYY-MM-DD':
      return `${year}-${month}-${day}`
    case 'YYYY-MM-DD HH:mm':
      return `${year}-${month}-${day} ${hours}:${minutes}`
    case 'YYYY-MM-DD HH:mm:ss':
      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
    case 'MM-DD':
      return `${month}-${day}`
    default:
      return `${year}-${month}-${day}`
  }
}

/**
 * 格式化紧急标志
 */
export function formatUrgentFlag(urgentFlag: boolean): string {
  return urgentFlag ? '紧急' : '普通'
}

/**
 * 计算订单总金额
 */
export function calculateOrderTotal(items: Array<{ quantity: number; unitPrice: number }>): number {
  if (!items || items.length === 0) return 0

  return items.reduce((total, item) => {
    const quantity = item.quantity || 0
    const unitPrice = item.unitPrice || 0
    return total + quantity * unitPrice
  }, 0)
}

/**
 * 格式化订单明细
 */
export function formatOrderItems(items: any[]): string {
  if (!items || items.length === 0) return '无明细'

  return items
    .map((item, index) => {
      const materialName = item.materialName || item.materialCode || '未知物料'
      const quantity = item.quantity || 0
      const unit = item.unit || '个'
      return `${index + 1}. ${materialName} ${quantity}${unit}`
    })
    .join('; ')
}

/**
 * 获取状态图标
 */
export function getStatusIcon(status: string): string {
  const iconMap: Record<string, string> = {
    draft: 'Document',
    pending_approval: 'Clock',
    approved: 'CircleCheck',
    rejected: 'Close',
    cancelled: 'Close',
  }

  return iconMap[status] || 'QuestionFilled'
}

/**
 * 获取状态颜色
 */
export function getStatusColor(status: string): string {
  const colorMap: Record<string, string> = {
    draft: '#909399',
    pending_approval: '#e6a23c',
    approved: '#67c23a',
    rejected: '#f56c6c',
    cancelled: '#909399',
  }

  return colorMap[status] || '#909399'
}

/**
 * 检查订单是否可取消（草稿、待审批、已拒绝可取消）
 * 已取消(2)和已批准(4)的订单不可取消
 */
export function isOrderCancellable(approvalStatus: number): boolean {
  return approvalStatus === 1 || approvalStatus === 3 || approvalStatus === 5
}

/**
 * 检查订单是否可编辑（草稿和已拒绝可编辑）
 */
export function isOrderEditable(approvalStatus: number): boolean {
  return approvalStatus === 1 || approvalStatus === 5
}

/**
 * 检查订单是否可审批（待审批可审批）
 */
export function isOrderApprovable(approvalStatus: number): boolean {
  return approvalStatus === 3
}

/**
 * 检查订单是否可收货（已批准且未完全收货）
 */
export function isOrderReceivable(approvalStatus: number, receiptStatus: number): boolean {
  return approvalStatus === 4 && (receiptStatus === 0 || receiptStatus === 1)
}

/**
 * 检查订单是否可付款（已批准且未完全付款）
 */
export function isOrderPayable(approvalStatus: number, paymentStatus: number): boolean {
  return approvalStatus === 4 && (paymentStatus === 0 || paymentStatus === 1)
}
