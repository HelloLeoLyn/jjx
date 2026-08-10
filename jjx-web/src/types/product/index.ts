// ==================== 通用类型 ====================
import type { PageQuery, PageResult } from '@/types/index'

// ==================== 产品管理类型 ====================

// 规格参数项类型
export interface ProductSpecItem {
  name: string // 参数名称，如"颜色"
  value: string // 参数值，如"黑色"
  unit?: string // 单位，如"-"、"cm"、"g"
}

// 规格参数JSON结构
export interface ProductSpecJson {
  specifications: ProductSpecItem[]
}

export interface ProductQueryParams extends PageQuery {
  productCode?: string
  productName?: string
  categoryId?: number
  productStatus?: string
  startDate?: string
  endDate?: string
}

export interface ProductFormData {
  productId?: number
  productCode: string
  productName: string
  categoryId?: number
  categoryName?: string
  categoryCode?: string
  specification?: string
  unit?: string
  weight?: number
  volume?: number
  material?: string
  color?: string
  brand?: string
  model?: string
  description?: string
  productStatus?: number
  approveStatus?: number
  remark?: string
  attachments?: ProductAttachment[]
  specJson?: string // 规格参数（JSON字符串）
  useDefaultSpec?: boolean // 是否使用默认规格参数
  // BOM和Route关联
  currentBomId?: number
  currentRouteId?: number
  bomName?: string
  bomCode?: string
  bomVersion?: string
  routeName?: string
  routeCode?: string
  routeVersion?: string
  minOrderQty?: number
  leadTime: number
  basePrice?: number
  costPrice?: number
  productType?: number
  // 产品编码结构字段
  codeCustomerId?: number
  codeSerialNo?: string
}

export interface ProductAttachment {
  attachmentId?: number
  fileName: string
  filePath: string
  fileSize: number
  fileType: string
  uploadTime?: string
}

export interface ProductItem {
  productId: number
  productCode: string
  productName: string
  categoryId: number
  categoryName: string
  specification: string
  unit: string
  weight: number
  volume: number
  material: string
  color: string
  brand: string
  model: string
  description: string
  productStatus: number
  approveStatus: number
  remark: string
  createTime: string
  updateTime: string
  createBy: string
  updateBy: string
  specJson?: string // 规格参数（JSON字符串）
}

// ==================== 产品实例类型 ====================

export interface ProductInstanceQueryParams extends PageQuery {
  instanceCode?: string
  productCode?: string
  productName?: string
  orderNo?: string
  instanceStatus?: number
  lifecycleStatus?: string
  startDate?: string
  endDate?: string
}

export interface ProductInstanceFormData {
  instanceId?: number
  instanceCode: string
  productId: number
  productCode: string
  productName: string
  orderId?: number
  orderNo?: string
  orderItemId?: number
  plannedQuantity: number
  actualQuantity?: number
  instanceStatus: string
  lifecycleStatus: string
  productionOrderId?: number
  productionOrderNo?: string
  productionStartTime?: string
  productionEndTime?: string
  shippingTime?: string
  deliveryTime?: string
  installationTime?: string
  serviceStartTime?: string
  scrapTime?: string
  remark?: string
  attributes?: Record<string, any>
}

export interface ProductInstanceItem {
  instanceId: number
  instanceCode: string
  productId: number
  productCode: string
  productName: string
  orderId?: number
  orderNo?: string
  orderItemId?: number
  plannedQuantity: number
  actualQuantity?: number
  instanceStatus: string
  lifecycleStatus: string
  productionOrderId?: number
  productionOrderNo?: string
  productionStartTime?: string
  productionEndTime?: string
  shippingTime?: string
  deliveryTime?: string
  installationTime?: string
  serviceStartTime?: string
  scrapTime?: string
  remark: string
  createTime: string
  updateTime: string
  createBy: string
  updateBy: string
}

// ==================== 生命周期状态类型 ====================

export interface LifecycleStatus {
  instanceId: number
  instanceCode: string
  currentState: string
  allowedTransitions: string[]
  lifecyclePhase: string
  progressPercentage: number
}

export interface StateTransitionResult {
  instanceId: number
  instanceCode: string
  currentState: string
  targetState: string
  success: boolean
  errorMessage?: string
  previousState?: string
  newState?: string
  transitionTime?: string
  stateHistory?: StateHistory
}

export interface StateHistory {
  instanceId: number
  fromState: string
  toState: string
  transitionTime: string
  operatorId?: number
  operatorName?: string
  remark?: string
  additionalData?: Record<string, any>
}

// ==================== 成本计算类型 ====================

export interface BomCostResult {
  bomId: number
  bomCode: string
  bomName: string
  productId: number
  productCode: string
  productName: string
  materialCost: number
  laborCost: number
  manufacturingCost: number
  totalCost: number
  unitCost: number
  costBreakdown: CostBreakdownItem[]
  costVariance?: CostVariance
}

export interface CostBreakdownItem {
  itemId: number
  materialCode: string
  materialName: string
  quantity: number
  unitPrice: number
  totalPrice: number
  costPercentage: number
}

export interface CostVariance {
  standardCost: number
  actualCost: number
  variance: number
  variancePercentage: number
}

// ==================== 配置验证类型 ====================

export interface ProductConfigValidation {
  productId: number
  productCode: string
  productName: string
  isValid: boolean
  completenessScore: number
  missingComponents: string[]
  validationErrors: ValidationError[]
  configSummary: ConfigSummary
}

export interface ValidationError {
  component: string
  errorCode: string
  errorMessage: string
  severity: 'error' | 'warning' | 'info'
}

import type { EngineeringRoutingItemVO } from '@/types/product/routing'
import type { EngineeringBomItem } from '@/types/product/bom'
export interface ConfigSummary {
  product: ProductItem
  bom?: EngineeringBomItem
  route?: EngineeringRoutingItemVO
  hasBom: boolean
  hasRoute: boolean
  isComplete: boolean
}

// ==================== 选项类型 ====================

export interface ProductOption {
  productId: number
  productCode: string
  productName: string
  specification: string
  unit: string
}

export interface MaterialOption {
  materialId: number
  materialCode: string
  materialName: string
  materialSpec: string
  unit: string
  unitPrice: number
}

export interface ProcessOption {
  processId: number
  processCode: string
  processName: string
  workCenterId: number
  workCenterName: string
  standardTime: number
}

/**
 * 标准工序选项（用于工序明细选择）
 * 对应后端 ProductStandardProcessVO
 */
export interface StandardProcessOption {
  processId: number
  processCode: string
  processName: string
  processType: string
  processTypeName: string
  processCategory: string
  processCategoryName: string
  standardLaborHours: number
  standardMachineHours: number
  processParamTemplate: string
  skillRequirement: string
  equipmentType: string
  qualityStandard: string
  description: string
  /** 是否带下标：0-不带,1-带 */
  hasIndex: number
  isEnabled: number
  displayOrder: number
  icon: string
}

export interface WorkCenterOption {
  workCenterId: number
  workCenterCode: string
  workCenterName: string
  departmentId: number
  departmentName: string
}

export interface ProductVo {
  productId: number
  productCode: string
  productName: string
  categoryId?: number
  categoryName?: string
  productType: string
  productStatus: number
  currentBomId?: number
  bomName?: string
  bomCode?: string
  bomVersion?: string
  currentRouteId?: number
  routeName?: string
  routeCode?: string
  routeVersion?: string
  unit?: string
  basePrice?: number
  costPrice?: number
  minOrderQty?: number
  leadTime?: number
  specJson?: string
  remark?: string
  createTime: string
  updateTime: string
  filmCount?: number
}

export interface CategoryTree {
  categoryId: number
  categoryCode: string
  categoryName: string
  parentId: number
  children?: CategoryTree[]
}

export interface RoutingSimpleVo {
  routingId: number
  routingCode: string
  routingName: string
  routingVersion: string
  isCurrent: number
  approveStatus: number
  processCount: number
}

export interface UnitVo {
  code: string
  name: string
}
import type { EngineeringBomVO } from '@/types/product/bom'
import type { EngineeringRoutingVO } from '@/types/product/routing'
import type { ProductCategoryDictItem } from '@/types/product/category'

export interface ProductFullVO {
  product?: ProductVo
  bom?: EngineeringBomVO
  routing?: EngineeringRoutingVO
  category?: ProductCategoryDictItem
  films: EngineeringFilmVO[]
}

export interface EngineeringFilmVO {
  /** 菲林ID */
  filmId: number
  /** 菲林编码 */
  filmCode: string
  /** 菲林名称 */
  filmName: string
  /** 菲林类型 */
  filmType: string
  /** 菲林类型名称 */
  filmTypeName: string
  /** 产品ID */
  productId: number
  /** 产品编码 */
  productCode: string
  /** 产品名称 */
  productName: string
  /** 版本号 */
  version: string
  /** 是否当前版本 */
  isCurrent: number
  /** 是否当前版本名称 */
  isCurrentName: string
  /** 父菲林ID */
  parentFilmId?: number
  /** 菲林尺寸 */
  filmSize?: string
  /** 菲林厚度(mm) */
  filmThickness?: number
  /** 菲林材料 */
  filmMaterial?: string
  /** 颜色 */
  color?: string
  /** 文件ID */
  fileId?: number
  /** 文件路径 */
  filePath?: string
  /** 文件名 */
  fileName?: string
  /** 技术规格 */
  technicalSpec?: string
  /** 设计说明 */
  designNotes?: string
  /** 关联工序ID */
  processId?: number
  /** 关联工序编码 */
  processCode?: string
  /** 审核状态 */
  approveStatus: number
  /** 审核状态名称 */
  approveStatusName: string
  /** 审核人ID */
  approverId?: number
  /** 审核人姓名 */
  approverName?: string
  /** 审核时间 */
  approveTime?: string
  /** 审核意见 */
  approveRemark?: string
  /** 设计人员ID */
  designerId?: number
  /** 设计人员姓名 */
  designerName?: string
  /** 设计完成时间 */
  designTime?: string
  /** 是否下发生产 */
  isReleased: number
  /** 创建人 */
  createBy: string
  /** 创建时间 */
  createTime: string
  /** 更新人 */
  updateBy: string
  /** 更新时间 */
  updateTime: string
  /** 备注 */
  remark?: string
}

/**
 * 菲林DTO（新增/编辑）
 */
export interface EngineeringFilmDTO {
  /** 菲林ID（编辑时必填） */
  filmId?: number
  /** 菲林编码 */
  filmCode: string
  /** 菲林名称 */
  filmName: string
  /** 菲林类型 */
  filmType: string
  /** 产品ID */
  productId: number
  /** 菲林尺寸 */
  filmSize?: string
  /** 菲林厚度(mm) */
  filmThickness?: number
  /** 菲林材料 */
  filmMaterial?: string
  /** 颜色 */
  color?: string
  /** 技术规格 */
  technicalSpec?: string
  /** 设计说明 */
  designNotes?: string
  /** 关联工序ID */
  processId?: number
  /** 备注 */
  remark?: string
}

/**
 * 菲林查询参数
 */
export interface EngineeringFilmQuery {
  /** 产品ID */
  productId?: number
  /** 菲林类型 */
  filmType?: string
  /** 审核状态 */
  approveStatus?: number
  /** 是否当前版本 */
  isCurrent?: number
  /** 页码 */
  pageNum?: number
  /** 每页大小 */
  pageSize?: number
}
