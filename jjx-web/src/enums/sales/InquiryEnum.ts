// src/enums/sales/InquiryEnum.ts
import { createNamedEnum } from '../base'

/**
 * 销售询价单状态枚举
 * 对应后端 sales SalesInquiryStatus
 */
export const InquiryStatusEnum = createNamedEnum(
  {
    DRAFT: { value: 0, label: '草稿', tagProps: { type: 'info' } },
    PENDING: { value: 1, label: '待处理', tagProps: { type: 'warning' } },
    SENT: { value: 2, label: '已发送', tagProps: { type: 'primary' } },
    CONVERTED: { value: 3, label: '已转报价', tagProps: { type: 'success' } },
    CONFIRMED: { value: 4, label: '已确认', tagProps: { type: 'success' } },
    REJECTED: { value: 5, label: '已拒绝', tagProps: { type: 'danger' } },
    EXPIRED: { value: 6, label: '已过期', tagProps: { type: 'info' } },
  },
  { type: 'info' }
)
