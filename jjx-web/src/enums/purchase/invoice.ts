import { createEnum, createNamedEnum } from '@/enums/base'

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
 * 发票状态枚举（对齐后端 DocumentStatus：0 待处理 / 1 已核验 / 2 已归档）
 */
export const InvoiceStatusEnum = createNamedEnum(
  {
    PENDING: { value: 0, label: '待处理', tagProps: { type: 'warning' } },
    VERIFIED: { value: 1, label: '已核验', tagProps: { type: 'success' } },
    ARCHIVED: { value: 2, label: '已归档', tagProps: { type: 'info' } },
  },
  { type: 'info' },
)
