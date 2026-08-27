// src/enums/sales/QuotationEnum.ts
import { createEnum } from '../base'

/**
 * 报价单状态枚举
 * 对应后端 QuotationStatus
 */
export const QuotationStatusEnum = createEnum<number>({
  items: [
    { value: 0, label: '草稿', tagProps: { type: 'info' } },
    { value: 1, label: '已发送', tagProps: { type: 'warning' } },
    { value: 2, label: '已确认', tagProps: { type: 'success' } },
    { value: 3, label: '已拒绝', tagProps: { type: 'danger' } },
    { value: 4, label: '已过期', tagProps: { type: 'info' } },
    { value: 5, label: '待审核', tagProps: { type: 'warning' } },
    { value: 6, label: '已审核', tagProps: { type: 'primary' } },
    { value: 8, label: '改单', tagProps: { type: 'warning' } },
    { value: 9, label: '已完成', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})
