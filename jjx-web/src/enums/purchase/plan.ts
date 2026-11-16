import { createNamedEnum } from '@/enums/base'

/** 方案校核结果，与后端 PurchasePlanSchemeStatus 同口径；不表示到货。 */
export const PurchasePlanSchemeStatusEnum = createNamedEnum(
  {
    UNARRANGED: { value: 'UNARRANGED', label: '未安排', tagProps: { type: 'info' } },
    NEEDS_CONFIRMATION: {
      value: 'NEEDS_CONFIRMATION',
      label: '待技术确认',
      tagProps: { type: 'warning' },
    },
    READY: { value: 'READY', label: '已安排', tagProps: { type: 'success' } },
  },
  { type: 'info' }
)
