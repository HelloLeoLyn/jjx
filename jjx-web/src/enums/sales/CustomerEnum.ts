import { createEnum, createNamedEnum } from '../base'

export const CustomerPaymentMethodEnum = createNamedEnum(
  {
    PREPAID: { value: 1, label: '预付', tagProps: { type: 'info' } },
    CASH_ON_DELIVERY: { value: 2, label: '货到付款', tagProps: { type: 'warning' } },
    MONTHLY_30: { value: 3, label: '月结30天', tagProps: { type: 'success' } },
    MONTHLY_60: { value: 4, label: '月结60天', tagProps: { type: 'success' } },
  },
  { type: 'info' }
)

export const PaymentTermTypeEnum = createNamedEnum(
  {
    PREPAID: { value: 'PREPAID', label: '预付', tagProps: { type: 'info' } },
    COD: { value: 'COD', label: '货到付款', tagProps: { type: 'warning' } },
    NET_DAYS: { value: 'NET_DAYS', label: '签收后N天', tagProps: { type: 'primary' } },
    MONTH_END: { value: 'MONTH_END', label: '签收月月底后N天', tagProps: { type: 'success' } },
  },
  { type: 'info' }
)

export const CreditStartBasisEnum = createNamedEnum(
  {
    CUSTOMER_RECEIPT_DATE: {
      value: 'CUSTOMER_RECEIPT_DATE',
      label: '客户签收日',
      tagProps: { type: 'info' },
    },
  },
  { type: 'info' }
)
/**客户状态 (1: 潜在客户, 2: 正式客户, 3: 暂停合作, 4: 终止合作) */
export const CustomerStatusEnum = createEnum({
  items: [
    { value: 1, label: '潜在客户', tagProps: { type: 'warning' } },
    { value: 2, label: '正式客户', tagProps: { type: 'info' } },
    { value: 3, label: '暂停合作', tagProps: { type: 'warning' } },
    { value: 4, label: '终止合作', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

export const CustomerTypeEnum = createEnum<number>({
  items: [
    { value: 1, label: '终端客户', tagProps: { type: 'primary', color: '#0099DD' } },
    { value: 2, label: '代理商', tagProps: { type: 'success', color: '#00ABBD' } },
    { value: 3, label: '经销商', tagProps: { type: 'info', color: '#FF9933' } },
  ],
  defaultTag: { type: 'info' },
})

export const CustomerLevelEnum = createEnum<number>({
  items: [
    { value: 1, label: 'A级', tagProps: { type: 'warning', color: '#d81fb9' } },
    { value: 2, label: 'B级', tagProps: { type: 'info', color: '#b11111' } },
    { value: 3, label: 'C级', tagProps: { type: 'success', color: '#07b17e' } },
  ],
  defaultTag: { type: 'info' },
})
