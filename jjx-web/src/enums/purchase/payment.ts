import { createEnum, createNamedEnum } from '@/enums/base'

/**
 * 付款状态枚举（对齐后端 PurchasePaymentStatusEnum）
 * 0=待付款, 1=部分付款, 2=已付款
 */
export const PaymentStatusEnum = createNamedEnum(
  {
    PENDING: { value: 0, label: '待付款', tagProps: { type: 'warning' } },
    PARTIALLY_PAID: { value: 1, label: '部分付款', tagProps: { type: 'info' } },
    COMPLETED: { value: 2, label: '已付款', tagProps: { type: 'success' } },
  },
  { type: 'info' },
)

/**
 * 付款方式枚举
 */
export const PaymentMethodEnum = createEnum({
  items: [
    { value: 'bank', label: '银行转账', tagProps: { type: 'primary' } },
    { value: 'cash', label: '现金', tagProps: { type: 'success' } },
    { value: 'check', label: '支票', tagProps: { type: 'warning' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 币种枚举
 */
export const CurrencyEnum = createEnum({
  items: [
    { value: 'CNY', label: '人民币', tagProps: { type: 'primary' } },
    { value: 'USD', label: '美元', tagProps: { type: 'success' } },
    { value: 'EUR', label: '欧元', tagProps: { type: 'warning' } },
    { value: 'JPY', label: '日元', tagProps: { type: 'info' } },
    { value: 'HKD', label: '港币', tagProps: { type: 'info' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 审批状态枚举
 * 1=草稿, 2=待审批, 3=已批准, 4=已拒绝, 5=已取消
 */
export const ApprovalStatusEnum = createEnum({
  items: [
    { value: 1, label: '草稿', tagProps: { type: 'info' } },
    { value: 2, label: '待审批', tagProps: { type: 'warning' } },
    { value: 3, label: '已批准', tagProps: { type: 'success' } },
    { value: 4, label: '已拒绝', tagProps: { type: 'danger' } },
    { value: 5, label: '已取消', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})
