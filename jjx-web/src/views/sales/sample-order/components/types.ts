// src/views/sales/sample-order/components/types.ts
import type { SampleOrderStatusEnum } from '@/enums/sales'

/** 展示模式 */
export type SampleDisplayMode = 'dialog' | 'inline'

/** 样品单产品行 */
export interface SampleProductItem {
  productId?: number
  productCode?: string
  productName?: string
  quantity?: number
  unit?: string
  specification?: string
  unitPrice?: number
  /** 现场新建产品带出的编码参数（随单自动建档用） */
  serialNo?: string
  panelType?: string
  panelFeature?: string
  circuitType?: string
  circuitFeature?: string
}

/** 客户选项 */
export interface CustomerOption {
  customerId: number
  customerName: string
  customerShortName?: string
  contactPerson?: string
  contactPhone?: string
}

/** 报价单选项 */
export interface QuotationOption {
  quotationId: number
  quotationNo: string
  customerId: number
  customerName: string
  contactPerson?: string
  contactPhone?: string
  finalAmount?: number | string
  validUntil?: string
}

/** 样品单主体 */
export interface SampleOrder {
  orderId: number
  orderNo: string
  customerId?: number
  customerName?: string
  customerShortName?: string
  sourceType?: '' | 'QUOTATION' | 'SALES_ORDER' | 'SAMPLE_ORDER'
  sourceNo?: string
  sampleStatus?: number
  sampleRound?: number
  sampleQty?: number
  currentProcess?: string
  engineeringAcceptor?: string
  engineeringNote?: string
  sampleTrackingNo?: string
  sampleSendDate?: string
  sampleConfirmDate?: string
  sampleClientName?: string
  convertedOrderId?: number
  convertOrderTime?: string
  deliveryDate?: string
  contactPerson?: string
  contactPhone?: string
  techRequirement?: string
  remark?: string
  traceId?: string
  createTime?: string
  sampleCost?: number
  sampleWorkHours?: number
  [key: string]: any
}

/** 表单 payload */
export interface SampleFormPayload {
  customerId: number
  items: SampleProductItem[]
  deliveryDate?: string
  contactPerson?: string
  contactPhone?: string
  techRequirement?: string
  remark?: string
  sourceType?: string
  sourceNo?: string
}

/** 组件可注入的 API 集合（便于替换默认实现） */
export interface SampleOrderApiLike {
  getInfo: (id: number) => Promise<any>
  getProducts: (id: number) => Promise<any>
  getRounds?: (id: number) => Promise<any>
  getSourceQuotationSummary?: (id: number) => Promise<any>
  create: (payload: any) => Promise<any>
  update: (id: number, payload: any) => Promise<any>
}

/** 来源类型标签 */
export function sourceTypeLabel(type?: string): string {
  if (type === 'QUOTATION') return '报价单'
  if (type === 'SALES_ORDER') return '销售订单'
  if (type === 'SAMPLE_ORDER') return '样品单'
  return '-'
}
