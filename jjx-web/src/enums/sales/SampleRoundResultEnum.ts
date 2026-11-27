import { createNamedEnum } from '@/enums/base'

export const SampleRoundResultEnum = createNamedEnum({
  PENDING: { value: 'pending', label: '待确认', tagProps: { type: 'info' } },
  CONFIRMED: { value: 'confirmed', label: '已确认', tagProps: { type: 'success' } },
  REJECTED: { value: 'rejected', label: '已退回', tagProps: { type: 'danger' } },
}, { type: 'info' })
