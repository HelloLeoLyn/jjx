// components/StatusTag/presets.ts

// 状态标签配置类型
export interface StatusConfig {
  [key: string]: {
    label: string
    type: 'success' | 'info' | 'warning' | 'danger' | 'primary'
    effect?: 'dark' | 'light' | 'plain'
  }
}

// 通用状态配置
export const commonStatusConfig: StatusConfig = {
  '0': { label: '正常', type: 'success' },
  '1': { label: '停用', type: 'danger' },
  true: { label: '是', type: 'success' },
  false: { label: '否', type: 'info' },
}

// 用户状态配置
export const userStatusConfig: StatusConfig = {
  '0': { label: '正常', type: 'success' },
  '1': { label: '停用', type: 'danger' },
  '2': { label: '锁定', type: 'warning' },
}

// 订单状态配置
export const orderStatusConfig: StatusConfig = {
  pending: { label: '待付款', type: 'warning' },
  paid: { label: '已付款', type: 'success' },
  shipped: { label: '已发货', type: 'info' },
  delivered: { label: '已送达', type: 'success' },
  completed: { label: '已完成', type: 'success' },
  cancelled: { label: '已取消', type: 'danger' },
  refunding: { label: '退款中', type: 'warning' },
  refunded: { label: '已退款', type: 'info' },
}

// 任务状态配置
export const taskStatusConfig: StatusConfig = {
  todo: { label: '待处理', type: 'info' },
  in_progress: { label: '进行中', type: 'warning' },
  review: { label: '待审核', type: 'warning' },
  done: { label: '已完成', type: 'success' },
  archived: { label: '已归档', type: 'info' },
  blocked: { label: '已阻塞', type: 'danger' },
}

// 审核状态配置
export const auditStatusConfig: StatusConfig = {
  draft: { label: '草稿', type: 'info' },
  submitted: { label: '待审核', type: 'warning' },
  reviewing: { label: '审核中', type: 'warning' },
  approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  revoked: { label: '已撤回', type: 'info' },
}

// 启用状态配置
export const enableStatusConfig: StatusConfig = {
  true: { label: '启用', type: 'success' },
  false: { label: '禁用', type: 'danger' },
  '1': { label: '启用', type: 'success' },
  '0': { label: '禁用', type: 'danger' },
  Y: { label: '启用', type: 'success' },
  N: { label: '禁用', type: 'danger' },
}

// 优先级配置
export const priorityStatusConfig: StatusConfig = {
  high: { label: '高', type: 'danger' },
  medium: { label: '中', type: 'warning' },
  low: { label: '低', type: 'info' },
  '1': { label: '高', type: 'danger' },
  '2': { label: '中', type: 'warning' },
  '3': { label: '低', type: 'info' },
}

// 审批状态配置
export const approvalStatusConfig: StatusConfig = {
  pending: { label: '待审批', type: 'warning' },
  approved: { label: '已批准', type: 'success' },
  rejected: { label: '已拒绝', type: 'danger' },
  cancelled: { label: '已取消', type: 'info' },
}
