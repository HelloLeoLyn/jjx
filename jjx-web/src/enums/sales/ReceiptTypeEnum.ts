import { createNamedEnum } from '@/enums/base'

export const ReceiptTypeEnum = createNamedEnum(
  {
    DEPOSIT: { value: 1, label: '定金', tagProps: { type: 'primary' } },
    PROGRESS: { value: 2, label: '进度款', tagProps: { type: 'warning' } },
    FINAL: { value: 3, label: '尾款', tagProps: { type: 'success' } },
  },
  { type: 'info' },
)
