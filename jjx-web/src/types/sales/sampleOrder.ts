import type { PageQuery } from '@/types'

export interface SampleOrderQueryParams extends PageQuery {
  orderNo?: string
  customerId?: number
  customerName?: string
  customerShortName?: string
  /** 样品单一对一产品快照（来源 sales_sample_order） */
  productId?: number
  productCode?: string
  productName?: string
  productSpecification?: string
  customerMaterialNo?: string
  unit?: string
  unitPrice?: number
  amount?: number
  productRemark?: string
  sampleStatus?: number
  salesPersonId?: number
  hasAcceptor?: boolean
}

export interface SampleOrderListParams {
  customerId?: number
  sampleStatus?: number
  salesPersonId?: number
  hasAcceptor?: boolean
}

export interface SampleOrder {
  orderId: number
  traceId?: string
  orderNo: string
  quotationId?: number
  customerId: number
  customerName: string
  customerShortName?: string
  contactPerson?: string
  contactPhone?: string
  orderDate?: string
  deliveryDate?: string
  orderType: number
  orderStatus?: number
  sampleStatus: number
  sampleRound?: number
  sampleQty?: number
  engineeringNote?: string
  engineeringAcceptor?: string
  engineeringAcceptTime?: string
  rejectReason?: string
  currentProcess?: string
  sampleCost?: number
  sampleWorkHours?: number
  sampleTrackingNo?: string
  sampleSendDate?: string
  sampleConfirmDate?: string
  confirmBy?: string
  confirmMethod?: string
  confirmTime?: string
  confirmSentTime?: string
  sampleClientName?: string
  convertedOrderId?: number
  convertOrderTime?: string
  salesManagerId?: number
  salesManagerName?: string
  quotationNo?: string
  inquiryId?: number
  inquiryNo?: string
  transferCount?: number
  lastTransferNo?: string
  lastTransferTime?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface SampleOrderItemInput {
  productId?: number
  productCode?: string
  productName?: string
  quantity?: number
  unit?: string
}

export interface SampleOrderCreateDTO {
  customerId: number
  quotationId?: number
  items?: SampleOrderItemInput[]
  deliveryDate?: string
  contactPerson?: string
  contactPhone?: string
  techRequirement?: string
  remark?: string
}

export type SampleOrderUpdateDTO = Omit<SampleOrderCreateDTO, 'quotationId'>

export interface SampleConvertItem {
  orderProductId: number
  productId: number
}

export interface SampleConvertExtras {
  paymentTerms?: string
  deliveryTerms?: string
  deliveryAddress?: string
  contactPerson?: string
  contactPhone?: string
}

export interface CreateFromQuotationDTO {
  sampleQty?: number
  remark?: string
  deliveryDate?: string
  contactPerson?: string
  contactPhone?: string
  techRequirement?: string
}

export interface SalesOrderProduct {
  id: number
  orderId: number
  productId: number
  productCode?: string
  productName?: string
  specification?: string
  customerMaterialNo?: string
  quantity?: number
  unit?: string
  unitPrice?: number
  amount?: number
  remark?: string
  lineRemark?: string
}

export interface SampleProcessMaterial {
  name?: string
  spec?: string
  qty?: number
  unit?: string
  materialId?: number
  materialCode?: string
}

export interface SampleProcess {
  processId: number
  orderId: number
  roundNo: number
  majorCategory?: string
  processName: string
  stdProcessId?: number
  hasIndex?: number
  indexNumber?: number
  processOrder?: number
  processCategory?: string
  status: number
  materials?: string
  processNote?: string
  customProcessParams?: string
  operator?: string
  startTime?: string
  endTime?: string
  durationMinutes?: number
  remark?: string
  createTime?: string
}

export interface SampleProcessPlanItem {
  processId?: number
  stdProcessId?: number
  indexNumber?: number
  processOrder?: number
  processCategory?: string
  majorCategory?: string
  customProcessParams?: string
  processName: string
  materials?: string
  processNote?: string
  status?: number
}

export interface SampleProcessPlanDTO {
  roundNo?: number
  items: SampleProcessPlanItem[]
}

export interface SampleProcessStatusDTO {
  status: number
  durationMinutes?: number
  processNote?: string
  materials?: string | null
}

export interface SampleBomItem {
  bomId?: number
  orderId?: number
  roundNo?: number
  layerName?: string
  materialName: string
  specification?: string
  quantity?: number
  unit?: string
  remark?: string
  createBy?: string
  createTime?: string
}

export interface SampleRound {
  roundId: number
  orderId: number
  roundNo: number
  engineeringNote?: string
  attachmentIds?: string
  bomSnapshot?: string
  processSnapshot?: string
  result?: string
  rejectReason?: string
  createTime?: string
}

export interface SampleSummaryMaterial {
  name?: string
  specification?: string
  quantity?: number
  unit?: string
  cost?: number
}

export interface SampleSummary {
  totalMinutes: number
  totalHours: number
  materialCount: number
  materials: SampleSummaryMaterial[]
  materialCost: number
  processCount: number
}

export interface SampleConvertCheckItem {
  code: string
  name: string
  level: 'required' | 'suggest' | 'info'
  pass: boolean
  status: string
  message?: string
  productId?: number
  action?: string
}

export interface SampleConvertCheck {
  orderId: number
  orderNo: string
  allPass: boolean
  items: SampleConvertCheckItem[]
}

export interface SampleTransferResult {
  transferNo?: string
  transferId?: number
  productAction?: string
  bomAction?: string
  routingAction?: string
  productId?: number
  bomId?: number
  routingId?: number
  version?: string
  detail?: string[]
}

export interface SampleTransferReminder {
  reminded: boolean
  duplicated: boolean
  message?: string
  orderNo?: string
}

export interface SampleSourceQuotationItem {
  productCode?: string
  productName?: string
  keyCount?: number
  width?: number
  height?: number
  thickness?: number
  materialType?: string
  color?: string
  circuitType?: string
  serialNo?: string
  panelType?: string
  panelFeature?: string
  circuitFeature?: string
  connectorType?: string
  quantity?: number
  unit?: string
  deliveryDays?: number
  estimatedDeliveryDate?: string
  customRequirements?: string
  logoRequirement?: string
  certificationRequirement?: string
}

export interface SampleSourceQuotation {
  quotationId: number
  quotationNo: string
  quotationType?: number
  customerName?: string
  quotationDate?: string
  validUntil?: string
  quotationStatus?: number
  sourceInquiryNo?: string
  remark?: string
  items: SampleSourceQuotationItem[]
}

export interface SampleSourceInquiry {
  inquiryId: number
  inquiryNo: string
  customerName?: string
  contactPerson?: string
  inquiryDate?: string
  expectedQuantity?: number
  productCode?: string
  productName?: string
  keyCount?: number
  sizeDescription?: string
  materialRequirements?: string
  circuitRequirements?: string
  connectorRequirements?: string
  specialRequirements?: string
  productDescription?: string
  hasDrawing?: number
  inquiryType?: number
  inquiryStatus?: number
  startDate?: string
  endDate?: string
  remark?: string
}

export interface SampleStatusOption {
  value: number
  label: string
  description: string
  terminal: boolean
}

export interface InkSuggestion {
  text: string
  materialId?: number
}

export type SampleConvertDTO = Record<string, unknown>
