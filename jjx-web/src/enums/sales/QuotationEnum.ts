// src/enums/sales/QuotationEnum.ts
import { createNamedEnum } from '../base'

/**
 * 报价单状态枚举
 * 对应后端 QuotationStatus
 */
export const QuotationStatusEnum = createNamedEnum(
  {
    DRAFT: { value: 0, label: '草稿', tagProps: { type: 'info' } },
    SENT: { value: 1, label: '已发送', tagProps: { type: 'warning' } },
    ACCEPTED: { value: 2, label: '已确认', tagProps: { type: 'success' } },
    REJECTED: { value: 3, label: '已拒绝', tagProps: { type: 'danger' } },
    EXPIRED: { value: 4, label: '已过期', tagProps: { type: 'info' } },
    PENDING_REVIEW: { value: 5, label: '待审核', tagProps: { type: 'warning' } },
    APPROVED: { value: 6, label: '已审核', tagProps: { type: 'primary' } },
    MODIFYING: { value: 8, label: '改单', tagProps: { type: 'warning' } },
    COMPLETED: { value: 9, label: '已完成', tagProps: { type: 'success' } },
  },
  { type: 'info' }
)
