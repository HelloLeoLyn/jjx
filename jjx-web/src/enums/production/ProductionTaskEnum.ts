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
