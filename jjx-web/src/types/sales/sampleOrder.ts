import type { PageQuery } from '@/types'

export interface OrderSampleOption {
  itemId: number
  productId?: number
  productCode: string
  productName: string
  unit?: string
  orderQuantity: number
  remark?: string
  activeSamples: { orderId: number; orderNo: string; sampleStatus: number }[]
}

export interface OrderSampleOptions {
  orderId: number
  orderNo: string
  customerId: number
  customerName: string
  contactPerson?: string
  contactPhone?: string
  sourceVersion: string
  deliveryDate?: string
  items: OrderSampleOption[]
}

export interface OrderBatchSampleRequest {
  sourceVersion: string
  deliveryDate?: string
  techRequirement?: string
  items: { itemId: number; quantity: number; remark?: string }[]
}

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
  /** 按产品过滤（作业规范页「样品需求单」只读列表；dev-20261011-026） */
  productId?: number
}

export interface SampleOrder {
  orderId: number
  sampleProductCode?: string
  sampleProductName?: string
  traceId?: string
  orderNo: string
  sourceType?: 'QUOTATION' | 'SALES_ORDER' | 'SAMPLE_ORDER'
  sourceNo?: string
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
  /** 现场新建产品的编码参数（productId 为空时，后端据此自动建档） */
  serialNo?: string
  panelType?: string
  panelFeature?: string
  circuitType?: string
  circuitFeature?: string
}

export interface SampleOrderCreateDTO {
  customerId: number
  sourceType?: 'QUOTATION' | 'SALES_ORDER'
  sourceNo?: string
  items?: SampleOrderItemInput[]
  deliveryDate?: string
  contactPerson?: string
  contactPhone?: string
  techRequirement?: string
  remark?: string
}

export type SampleOrderUpdateDTO = Omit<SampleOrderCreateDTO, 'sourceType' | 'sourceNo'>

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
  remark?: string
  status?: number
}

export interface SampleProcessPlanDTO {
  engineeringNote?: string
  standaloneMaterials?: SampleBomItem[]
  roundNo?: number
  items?: SampleProcessPlanItem[]
}

export interface SampleProcessStatusDTO {
  status: number
  durationMinutes?: number
  processNote?: string
  materials?: string | null
}

// 打样工作台「复制打样方案 → 成品方案」只读带出（dev-20261012-012）
export interface SampleProductPlan {
  eligible: boolean
  reason?: string
  productId?: number
  productCode?: string
  productName?: string
  bomId?: number
  bomVersion?: string
  bomApproveStatus?: number
  routingId?: number
  routingVersion?: string
  routingApproveStatus?: number
  processCount?: number
  materialCount?: number
  unlinkedMaterials?: SampleProductPlanMaterial[]
  rows: SampleProcess[]
}

// 成品方案带出的材料（工序 materials JSON 键名沿用）
export interface SampleProductPlanMaterial {
  name?: string
  spec?: string
  qty?: number
  unit?: string
  materialId?: number
  materialCode?: string
}

export interface SampleBomItem {
  materialId?: number
  materialCode?: string
  projectIds?: string
  moduleQty?: number
  baseQty?: number
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
  returnToSourceOrder?: boolean
  sourceOrderId?: number
  sourceOrderNo?: string
  sourceOrderProductId?: number
  productCode?: string
  productName?: string
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

/** 样品需求单会签记录（dev-20261010-028） */
export interface SampleRequisitionSign {
  id: number
  sampleOrderId: number
  roundNo: number
  /** SALES业务/APPROVE核准/DEPT部门主管 */
  signRole: string
  /** 1同意/0不同意 */
  approveResult: number
  comment?: string
  signerId?: number
  signerName?: string
  signTime?: string
}

/** 打样不良原因及改善记录（dev-20261010-028） */
export interface SampleDefectRecord {
  id: number
  sampleOrderId: number
  roundNo: number
  /** PRINT印刷/PUNCH加工冲型 */
  craftType: string
  defectReason: string
  improvement?: string
  recorderId?: number
  recorderName?: string
  recordDate?: string
  createTime?: string
}

/** 新增打样不良记录入参 */
export interface SampleDefectRecordDTO {
  craftType: string
  defectReason: string
  improvement?: string
  recordDate?: string
  roundNo?: number
}
