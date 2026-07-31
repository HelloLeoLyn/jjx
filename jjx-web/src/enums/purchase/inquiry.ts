import { createEnum } from '@/enums/base'

/**
 * 询价状态枚举
 * 0=待报价, 1=已报价, 2=已确认, 3=已过期, 4=已取消
 */
export const InquiryStatusEnum = createEnum({
  items: [
    { value: 0, label: '待询价', tagProps: { type: 'warning' } },
    { value: 1, label: '已询价', tagProps: { type: 'primary' } },
    { value: 2, label: '比价中', tagProps: { type: 'warning' } },
    { value: 3, label: '已选中', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})
