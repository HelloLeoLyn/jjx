import { createEnum } from '../base'

export const ProductionTaskStatus = {
  PENDING: 'PENDING',
  ACTIVE: 'ACTIVE',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
} as const

export const ProductionTaskStatusEnum = createEnum<string>({
  items: [
    { value: ProductionTaskStatus.PENDING, label: '未分配', tagProps: { type: 'info' } },
    { value: ProductionTaskStatus.ACTIVE, label: '进行中', tagProps: { type: 'success' } },
    { value: ProductionTaskStatus.COMPLETED, label: '已完成', tagProps: { type: 'primary' } },
    { value: ProductionTaskStatus.CANCELLED, label: '已取消', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * dev-20260924-002：生产任务业务类型（production_task.task_type）。
 * STANDARD=普通任务；SUPPLEMENT=报废补产任务（挂老工单、绑补料单、可独立派工）。
 */
export const ProductionTaskType = {
  STANDARD: 'STANDARD',
  SUPPLEMENT: 'SUPPLEMENT',
} as const

export const ProductionTaskTypeEnum = createEnum<string>({
  items: [
    { value: ProductionTaskType.STANDARD, label: '普通', tagProps: { type: 'info' } },
    { value: ProductionTaskType.SUPPLEMENT, label: '补产', tagProps: { type: 'warning' } },
  ],
  defaultTag: { type: 'info' },
})
