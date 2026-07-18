// 付款条件枚举值
export enum PaymentTermCode {
  ADVANCE_100 = 'ADVANCE_100',
  ADVANCE_30 = 'ADVANCE_30',
  ADVANCE_50 = 'ADVANCE_50',
  COD = 'COD',
  UPON_RECEIPT = 'UPON_RECEIPT',
  UPON_ACCEPTANCE = 'UPON_ACCEPTANCE',
  NET_15 = 'NET_15',
  NET_30 = 'NET_30',
  NET_45 = 'NET_45',
  NET_60 = 'NET_60',
  NET_90 = 'NET_90',
  MONTHLY_30 = 'MONTHLY_30',
  MONTHLY_60 = 'MONTHLY_60',
  INVOICE_15 = 'INVOICE_15',
  INVOICE_30 = 'INVOICE_30',
  INVOICE_45 = 'INVOICE_45',
  INVOICE_60 = 'INVOICE_60',
  CASH_2_10_NET_30 = 'CASH_2_10_NET_30',
  CASH_3_15_NET_45 = 'CASH_3_15_NET_45',
  STAGE_30_70 = 'STAGE_30_70',
  STAGE_20_80 = 'STAGE_20_80',
  WEEKLY = 'WEEKLY',
  QUARTERLY = 'QUARTERLY',
  ANNUAL = 'ANNUAL',
}

// 付款条件项接口
export interface PaymentTerm {
  code: PaymentTermCode
  name: string
  type: string
  description: string
  status: number
}

// 付款条件数据映射
export const PAYMENT_TERMS: Record<PaymentTermCode, PaymentTerm> = {
  [PaymentTermCode.ADVANCE_100]: {
    code: PaymentTermCode.ADVANCE_100,
    name: '100%预付',
    type: 'payment_term',
    description: '下单后即付全款',
    status: 1,
  },
  [PaymentTermCode.ADVANCE_30]: {
    code: PaymentTermCode.ADVANCE_30,
    name: '预付30%',
    type: 'payment_term',
    description: '预付30%，发货前付70%',
    status: 1,
  },
  [PaymentTermCode.ADVANCE_50]: {
    code: PaymentTermCode.ADVANCE_50,
    name: '预付50%',
    type: 'payment_term',
    description: '预付50%，发货前付50%',
    status: 1,
  },
  [PaymentTermCode.COD]: {
    code: PaymentTermCode.COD,
    name: '货到付款',
    type: 'payment_term',
    description: '货物验收合格后立即付款',
    status: 1,
  },
  [PaymentTermCode.UPON_RECEIPT]: {
    code: PaymentTermCode.UPON_RECEIPT,
    name: '票到付款',
    type: 'payment_term',
    description: '收到发票即付',
    status: 1,
  },
  [PaymentTermCode.UPON_ACCEPTANCE]: {
    code: PaymentTermCode.UPON_ACCEPTANCE,
    name: '验收后付款',
    type: 'payment_term',
    description: '货物验收合格后付款',
    status: 1,
  },
  [PaymentTermCode.NET_15]: {
    code: PaymentTermCode.NET_15,
    name: '月结15天',
    type: 'payment_term',
    description: '货到/票到后15天付款',
    status: 1,
  },
  [PaymentTermCode.NET_30]: {
    code: PaymentTermCode.NET_30,
    name: '月结30天',
    type: 'payment_term',
    description: '货到/票到后30天付款',
    status: 1,
  },
  [PaymentTermCode.NET_45]: {
    code: PaymentTermCode.NET_45,
    name: '月结45天',
    type: 'payment_term',
    description: '货到/票到后45天付款',
    status: 1,
  },
  [PaymentTermCode.NET_60]: {
    code: PaymentTermCode.NET_60,
    name: '月结60天',
    type: 'payment_term',
    description: '货到/票到后60天付款',
    status: 1,
  },
  [PaymentTermCode.NET_90]: {
    code: PaymentTermCode.NET_90,
    name: '月结90天',
    type: 'payment_term',
    description: '货到/票到后90天付款',
    status: 1,
  },
  [PaymentTermCode.MONTHLY_30]: {
    code: PaymentTermCode.MONTHLY_30,
    name: '月结30天(次月)',
    type: 'payment_term',
    description: '次月30日前付款',
    status: 1,
  },
  [PaymentTermCode.MONTHLY_60]: {
    code: PaymentTermCode.MONTHLY_60,
    name: '月结60天(次次月)',
    type: 'payment_term',
    description: '次次月30日前付款',
    status: 1,
  },
  [PaymentTermCode.INVOICE_15]: {
    code: PaymentTermCode.INVOICE_15,
    name: '票到15天',
    type: 'payment_term',
    description: '收到发票后15天付款',
    status: 1,
  },
  [PaymentTermCode.INVOICE_30]: {
    code: PaymentTermCode.INVOICE_30,
    name: '票到30天',
    type: 'payment_term',
    description: '收到发票后30天付款',
    status: 1,
  },
  [PaymentTermCode.INVOICE_45]: {
    code: PaymentTermCode.INVOICE_45,
    name: '票到45天',
    type: 'payment_term',
    description: '收到发票后45天付款',
    status: 1,
  },
  [PaymentTermCode.INVOICE_60]: {
    code: PaymentTermCode.INVOICE_60,
    name: '票到60天',
    type: 'payment_term',
    description: '收到发票后60天付款',
    status: 1,
  },
  [PaymentTermCode.CASH_2_10_NET_30]: {
    code: PaymentTermCode.CASH_2_10_NET_30,
    name: '2/10, n/30',
    type: 'payment_term',
    description: '10天内付款享2%折扣，最晚30天',
    status: 1,
  },
  [PaymentTermCode.CASH_3_15_NET_45]: {
    code: PaymentTermCode.CASH_3_15_NET_45,
    name: '3/15, n/45',
    type: 'payment_term',
    description: '15天内付款享3%折扣，最晚45天',
    status: 1,
  },
  [PaymentTermCode.STAGE_30_70]: {
    code: PaymentTermCode.STAGE_30_70,
    name: '预付30%+尾款70%',
    type: 'payment_term',
    description: '预付30%，验收后付70%',
    status: 1,
  },
  [PaymentTermCode.STAGE_20_80]: {
    code: PaymentTermCode.STAGE_20_80,
    name: '预付20%+尾款80%',
    type: 'payment_term',
    description: '预付20%，验收后付80%',
    status: 1,
  },
  [PaymentTermCode.WEEKLY]: {
    code: PaymentTermCode.WEEKLY,
    name: '周结',
    type: 'payment_term',
    description: '每周结算一次',
    status: 1,
  },
  [PaymentTermCode.QUARTERLY]: {
    code: PaymentTermCode.QUARTERLY,
    name: '季结',
    type: 'payment_term',
    description: '每季度结算一次',
    status: 1,
  },
  [PaymentTermCode.ANNUAL]: {
    code: PaymentTermCode.ANNUAL,
    name: '年结',
    type: 'payment_term',
    description: '每年结算一次',
    status: 1,
  },
}

// 获取所有付款条件列表（用于下拉选择等）
export const getPaymentTermList = (): Array<{
  value: PaymentTermCode
  label: string
  description: string
}> => {
  return Object.values(PAYMENT_TERMS).map((item) => ({
    value: item.code,
    label: item.name,
    description: item.description,
  }))
}

// 根据code获取名称（用于展示）
export const getPaymentTermName = (code: PaymentTermCode): string => {
  return PAYMENT_TERMS[code]?.name || code
}

// 根据code获取完整描述
export const getPaymentTermDescription = (code: PaymentTermCode): string => {
  return PAYMENT_TERMS[code]?.description || ''
}
