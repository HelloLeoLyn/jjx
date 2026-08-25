// 任务状态 / 流水动作展示常量（dispatch 页面纯展示配置）
export const STATUS_TEXT: Record<string, string> = {
  PENDING: '未分配',
  ACTIVE: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

export type TagType = 'info' | 'success' | 'primary' | 'danger' | 'warning'

export const STATUS_TAG: Record<string, TagType> = {
  PENDING: 'info',
  ACTIVE: 'success',
  COMPLETED: 'primary',
  CANCELLED: 'danger',
}

export const FLOW_ACTION_LABEL: Record<string, string> = {
  ASSIGN: '分配',
  RECALL: '收回',
  RETURN: '退回',
  COMPLETE: '完成',
  FIRST_ASSIGN: '首次分配（历史）',
  UNASSIGN: '解除分配（历史）',
}
