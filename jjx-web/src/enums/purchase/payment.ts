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
  { type: 'info' }
)

export const PaymentApprovalStatus = {
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
} as const

export const PaymentApprovalStatusEnum = createEnum({
  items: [
    { value: PaymentApprovalStatus.PENDING, label: '待审批', tagProps: { type: 'warning' } },
    { value: PaymentApprovalStatus.APPROVED, label: '已批准', tagProps: { type: 'success' } },
    { value: PaymentApprovalStatus.REJECTED, label: '已拒绝', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/** 页面阶段仅由现有审批/付款状态派生，不新增持久化状态。 */
export const PaymentStageEnum = createNamedEnum({
  PENDING_APPROVAL: { value: 'PENDING_APPROVAL', label: '待审批', tagProps: { type: 'warning' } },
  WAITING_PAYMENT: { value: 'WAITING_PAYMENT', label: '待付款', tagProps: { type: 'info' } },
  PARTIALLY_PAID: { value: 'PARTIALLY_PAID', label: '部分付款', tagProps: { type: 'warning' } },
  PAID: { value: 'PAID', label: '已付款', tagProps: { type: 'success' } },
  REJECTED: { value: 'REJECTED', label: '已驳回', tagProps: { type: 'danger' } },
  UNKNOWN: { value: 'UNKNOWN', label: '状态未知', tagProps: { type: 'info' } },
}, { type: 'info' })

export function paymentStageOf(payment: { paymentStatus?: number; approvalStatus?: string }): string {
  if (payment.approvalStatus === PaymentApprovalStatus.REJECTED) return PaymentStageEnum.REJECTED.value
  if (payment.paymentStatus === PaymentStatusEnum.COMPLETED.value) return PaymentStageEnum.PAID.value
  if (payment.approvalStatus === PaymentApprovalStatus.PENDING) return PaymentStageEnum.PENDING_APPROVAL.value
  if (payment.approvalStatus === PaymentApprovalStatus.APPROVED) {
    if (payment.paymentStatus === PaymentStatusEnum.PARTIALLY_PAID.value) return PaymentStageEnum.PARTIALLY_PAID.value
    if (payment.paymentStatus === PaymentStatusEnum.PENDING.value) return PaymentStageEnum.WAITING_PAYMENT.value
  }
  return PaymentStageEnum.UNKNOWN.value
}

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
export const ApprovalStatusEnum = createNamedEnum(
  {
    DRAFT: { value: 1, label: '草稿', tagProps: { type: 'info' } },
    PENDING: { value: 2, label: '待审批', tagProps: { type: 'warning' } },
    APPROVED: { value: 3, label: '已批准', tagProps: { type: 'success' } },
    REJECTED: { value: 4, label: '已拒绝', tagProps: { type: 'danger' } },
    CANCELLED: { value: 5, label: '已取消', tagProps: { type: 'danger' } },
  },
  { type: 'info' }
)
