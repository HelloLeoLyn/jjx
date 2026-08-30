// src/enums/sales/InquiryEnum.ts
import { createEnum } from '../base'

/**
 * 销售询价单状态枚举
 * 对应后端 sales SalesInquiryStatus
 */
export const InquiryStatusEnum = createEnum<number>({
  items: [
    { value: 0, label: '草稿', tagProps: { type: 'info' } },
    { value: 1, label: '待处理', tagProps: { type: 'warning' } },
    { value: 2, label: '已发送', tagProps: { type: 'primary' } },
    { value: 3, label: '已转报价', tagProps: { type: 'success' } },
    { value: 4, label: '已确认', tagProps: { type: 'success' } },
    { value: 5, label: '已拒绝', tagProps: { type: 'danger' } },
    { value: 6, label: '已过期', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})
