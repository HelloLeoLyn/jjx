import { createEnum } from '@/enums/base'

/**
 * 发票类型枚举
 */
export const InvoiceTypeEnum = createEnum({
  items: [
    { value: 'vat', label: '增值税发票', tagProps: { type: 'primary' } },
    { value: 'ordinary', label: '普通发票', tagProps: { type: 'info' } },
    { value: 'special', label: '专用发票', tagProps: { type: 'warning' } },
    { value: 'electronic', label: '电子发票', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 发票状态枚举
 */
export const InvoiceStatusEnum = createEnum({
  items: [
    { value: 'pending', label: '待开票', tagProps: { type: 'warning' } },
    { value: 'issued', label: '已开票', tagProps: { type: 'primary' } },
    { value: 'verified', label: '已核销', tagProps: { type: 'success' } },
    { value: 'cancelled', label: '已作废', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})
