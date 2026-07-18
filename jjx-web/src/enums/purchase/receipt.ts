import { createEnum } from '@/enums/base'

/**
 * 收货状态枚举
 * 0=待收货, 1=部分收货, 2=已收货
 */
export const ReceiptStatusEnum = createEnum({
  items: [
    { value: 0, label: '待收货', tagProps: { type: 'warning' } },
    { value: 1, label: '部分收货', tagProps: { type: 'info' } },
    { value: 2, label: '已收货', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 检验结果枚举
 */
export const InspectionResultEnum = createEnum({
  items: [
    { value: 'passed', label: '合格', tagProps: { type: 'success' } },
    { value: 'failed', label: '不合格', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})
