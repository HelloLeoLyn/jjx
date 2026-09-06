import { createEnum } from '../base'

export const IqcReworkStatus = {
  CREATED: 'CREATED',
  PENDING_REINSPECTION: 'PENDING_REINSPECTION',
  COMPLETED: 'COMPLETED',
} as const

export const IqcReworkStatusEnum = createEnum({
  items: [
    { value: IqcReworkStatus.CREATED, label: '待返工', tagProps: { type: 'warning' } },
    { value: IqcReworkStatus.PENDING_REINSPECTION, label: '待复检', tagProps: { type: 'primary' } },
    { value: IqcReworkStatus.COMPLETED, label: '已完成', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})
