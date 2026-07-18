export type TagType = 'default' | 'info' | 'success' | 'warning' | 'danger'

export interface StatusOption {
  value: string
  label: string
  type: TagType
  icon?: string
}

export interface StatusConfig {
  [key: string]: StatusOption
}

// 通用状态配置
export const commonStatusConfig: StatusConfig = {
  draft: {
    value: 'draft',
    label: '草稿',
    type: 'info',
    icon: '📝',
  },
  reviewing: {
    value: 'reviewing',
    label: '审核中',
    type: 'warning',
    icon: '⏳',
  },
  approved: {
    value: 'approved',
    label: '已审核',
    type: 'success',
    icon: '✅',
  },
  active: {
    value: 'active',
    label: '生效中',
    type: 'success',
    icon: '🟢',
  },
  inactive: {
    value: 'inactive',
    label: '已失效',
    type: 'danger',
    icon: '🔴',
  },
  pending: {
    value: 'pending',
    label: '待处理',
    type: 'warning',
    icon: '⏳',
  },
  completed: {
    value: 'completed',
    label: '已完成',
    type: 'success',
    icon: '✅',
  },
  cancelled: {
    value: 'cancelled',
    label: '已取消',
    type: 'danger',
    icon: '❌',
  },
  processing: {
    value: 'processing',
    label: '处理中',
    type: 'info',
    icon: '⚙️',
  },
}

// 审批状态配置
export const approvalStatusConfig: StatusConfig = {
  pending: {
    value: 'pending',
    label: '待审核',
    type: 'warning',
    icon: '⏳',
  },
  approved: {
    value: 'approved',
    label: '已通过',
    type: 'success',
    icon: '✅',
  },
  rejected: {
    value: 'rejected',
    label: '已驳回',
    type: 'danger',
    icon: '❌',
  },
}

// 产品状态配置
export const productStatusConfig: StatusConfig = {
  draft: {
    value: 'draft',
    label: '草稿',
    type: 'info',
    icon: '📝',
  },
  active: {
    value: 'active',
    label: '生效中',
    type: 'success',
    icon: '🟢',
  },
  inactive: {
    value: 'inactive',
    label: '已失效',
    type: 'danger',
    icon: '🔴',
  },
  archived: {
    value: 'archived',
    label: '已归档',
    type: 'default',
    icon: '📁',
  },
}

// BOM状态配置
export const bomStatusConfig: StatusConfig = {
  draft: {
    value: 'draft',
    label: '草稿',
    type: 'info',
    icon: '📝',
  },
  reviewing: {
    value: 'reviewing',
    label: '审核中',
    type: 'warning',
    icon: '⏳',
  },
  approved: {
    value: 'approved',
    label: '已审核',
    type: 'success',
    icon: '✅',
  },
  active: {
    value: 'active',
    label: '生效中',
    type: 'success',
    icon: '🟢',
  },
  inactive: {
    value: 'inactive',
    label: '已失效',
    type: 'danger',
    icon: '🔴',
  },
}

// 获取状态配置
export function getStatusConfig(
  status: string,
  configType: 'common' | 'approval' | 'product' | 'bom' = 'common',
): StatusOption {
  const configs = {
    common: commonStatusConfig,
    approval: approvalStatusConfig,
    product: productStatusConfig,
    bom: bomStatusConfig,
  }

  const config = configs[configType]
  return (
    config[status] || {
      value: status,
      label: status,
      type: 'default',
    }
  )
}

// 获取状态标签类型
export function getStatusTagType(
  status: string,
  configType: 'common' | 'approval' | 'product' | 'bom' = 'common',
): TagType {
  const config = getStatusConfig(status, configType)
  return config.type
}

// 获取状态标签文本
export function getStatusLabel(
  status: string,
  configType: 'common' | 'approval' | 'product' | 'bom' = 'common',
): string {
  const config = getStatusConfig(status, configType)
  return config.label
}
