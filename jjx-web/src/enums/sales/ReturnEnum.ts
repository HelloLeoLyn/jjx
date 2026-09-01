import { createNamedEnum } from '@/enums/base'

/** 销售退货单状态（对齐后端 SalesReturnStatusEnum） */
export const SalesReturnStatusEnum = createNamedEnum(
  {
    APPLYING: { value: 1, label: '申请中', tagProps: { type: 'warning' } },
    APPROVED: { value: 2, label: '已审核', tagProps: { type: 'primary' } },
    RECEIVED: { value: 3, label: '已收货', tagProps: { type: 'success' } },
    REFUNDED: { value: 4, label: '已退款', tagProps: { type: 'success' } },
    COMPLETED: { value: 5, label: '已完成', tagProps: { type: 'info' } },
    CANCELLED: { value: 6, label: '已取消', tagProps: { type: 'danger' } },
  },
  { type: 'info' },
)

/** 退货类型：1质量问题 2规格不符 3数量错误 4客户取消 5其他 */
export const SalesReturnTypeEnum = createNamedEnum(
  {
    QUALITY: { value: 1, label: '质量问题', tagProps: { type: 'danger' } },
    SPEC_MISMATCH: { value: 2, label: '规格不符', tagProps: { type: 'warning' } },
    QTY_ERROR: { value: 3, label: '数量错误', tagProps: { type: 'warning' } },
    CUSTOMER_CANCEL: { value: 4, label: '客户取消', tagProps: { type: 'info' } },
    OTHER: { value: 5, label: '其他', tagProps: { type: 'info' } },
  },
  { type: 'info' },
)
