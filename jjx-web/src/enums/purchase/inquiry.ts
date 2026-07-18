import { createEnum } from '@/enums/base'

/**
 * 询价状态枚举
 * 0=待报价, 1=已报价, 2=已确认, 3=已过期, 4=已取消
 */
export const InquiryStatusEnum = createEnum({
  items: [
    { value: 0, label: '待报价', tagProps: { type: 'warning' } },
    { value: 1, label: '已报价', tagProps: { type: 'primary' } },
    { value: 2, label: '已确认', tagProps: { type: 'success' } },
    { value: 3, label: '已过期', tagProps: { type: 'danger' } },
    { value: 4, label: '已取消', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})
