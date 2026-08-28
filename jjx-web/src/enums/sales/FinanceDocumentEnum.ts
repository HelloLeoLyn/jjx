import { createNamedEnum } from '@/enums/base'

export const SalesFinanceDocumentStatusEnum = createNamedEnum(
  {
    VOID: { value: 0, label: '作废', tagProps: { type: 'danger' } },
    NORMAL: { value: 1, label: '正常', tagProps: { type: 'success' } },
  },
  { type: 'info' },
)

export const SalesReceiptPaymentMethodEnum = createNamedEnum(
  {
    BANK_TRANSFER: { value: 1, label: '银行转账', tagProps: { type: 'primary' } },
    CASH: { value: 2, label: '现金', tagProps: { type: 'success' } },
    ACCEPTANCE: { value: 3, label: '承兑汇票', tagProps: { type: 'warning' } },
  },
  { type: 'info' },
)
