// src/types/sales/order-validation.ts

import type {
  ValidationLevelEnum,
  ValidationTypeEnum,
  ValidationStatusEnum,
  FixStatusEnum,
} from '@/enums/sales/ValidationEnum'
import type { SalesOrderStatusEnum } from '@/enums/sales/OrderEnum'
import type { ProductStatusEnum } from '@/enums/product/ProductEnum'
import type { BomApproveStatusEnum } from '@/enums/product/BomEnum'

/**
 * 验证错误项
 */
export interface ValidationItem {
  /** 错误代码 */
  code: string
  /** 错误消息 */
  message: string
  /** 错误级别：1错误，2警告，3提示 */
  level: number
  /** 关联字段 */
  field?: string
  /** 修复建议 */
  suggestion?: string
  /** 修复链接 */
  link?: string
  /** 修复数据 */
  fixData?: Record<string, any>
}

/**
 * 产品验证结果
 */
export interface ProductValidationResult {
  /** 产品ID */
  productId: number
  /** 产品编码 */
  productCode: string
  /** 产品名称 */
  productName: string
  /** 是否有效 */
  isValid: boolean
  /** 产品状态 */
  status: string
  /** 产品状态标签 */
  statusLabel: string
  /** 是否有有效的BOM */
  hasValidBom: boolean
  /** 是否有有效的工艺路线 */
  hasValidRouting: boolean
  /** BOM版本 */
  bomVersion?: string
  /** 工艺路线版本 */
  routingVersion?: string
  /** 错误列表 */
  errors: ValidationItem[]
  /** 警告列表 */
  warnings: ValidationItem[]
  /** 提示列表 */
  infos: ValidationItem[]
}

/**
 * BOM验证结果
 */
export interface BomValidationResult {
  /** BOM ID */
  bomId: number
  /** BOM编码 */
  bomCode: string
  /** BOM名称 */
  bomName: string
  /** 是否有效 */
  isValid: boolean
  /** 审批状态 */
  approveStatus: string
  /** 审批状态标签 */
  approveStatusLabel: string
  /** 是否当前版本 */
  isCurrent: boolean
  /** 物料数量 */
  materialCount: number
  /** 缺失物料数量 */
  missingMaterialCount: number
  /** 错误列表 */
  errors: ValidationItem[]
  /** 警告列表 */
  warnings: ValidationItem[]
  /** 提示列表 */
  infos: ValidationItem[]
}

/**
 * 工艺路线验证结果
 */
export interface RoutingValidationResult {
  /** 工艺路线ID */
  routingId: number
  /** 工艺路线编码 */
  routingCode: string
  /** 工艺路线名称 */
  routingName: string
  /** 是否有效 */
  isValid: boolean
  /** 审批状态 */
  approveStatus: number
  /** 审批状态标签 */
  approveStatusLabel: string
  /** 是否当前版本 */
  isCurrent: boolean
  /** 工序数量 */
  processCount: number
  /** 错误列表 */
  errors: ValidationItem[]
  /** 警告列表 */
  warnings: ValidationItem[]
  /** 提示列表 */
  infos: ValidationItem[]
}

/**
 * 生产能力验证结果
 */
export interface CapacityValidationResult {
  /** 是否有效 */
  isValid: boolean
  /** 计划开始日期 */
  planStartDate: string
  /** 计划结束日期 */
  planEndDate: string
  /** 所需产能 */
  requiredCapacity: number
  /** 可用产能 */
  availableCapacity: number
  /** 产能利用率 */
  capacityUtilization: number
  /** 是否超负荷 */
  isOverload: boolean
  /** 建议开始日期 */
  suggestedStartDate?: string
  /** 建议结束日期 */
  suggestedEndDate?: string
  /** 错误列表 */
  errors: ValidationItem[]
  /** 警告列表 */
  warnings: ValidationItem[]
  /** 提示列表 */
  infos: ValidationItem[]
}

/**
 * 成本验证结果
 */
export interface CostValidationResult {
  /** 是否有效 */
  isValid: boolean
  /** 材料成本 */
  materialCost: number
  /** 人工成本 */
  laborCost: number
  /** 制造费用 */
  manufacturingCost: number
  /** 总成本 */
  totalCost: number
  /** 销售价格 */
  salePrice: number
  /** 毛利率 */
  grossMargin: number
  /** 是否低于最低毛利率 */
  isBelowMinMargin: boolean
  /** 最低毛利率要求 */
  minMarginRequirement: number
  /** 错误列表 */
  errors: ValidationItem[]
  /** 警告列表 */
  warnings: ValidationItem[]
  /** 提示列表 */
  infos: ValidationItem[]
}

/**
 * 验证摘要
 */
export interface ValidationSummary {
  /** 总错误数 */
  totalErrors: number
  /** 总警告数 */
  totalWarnings: number
  /** 总提示数 */
  totalInfos: number
  /** 错误消息列表 */
  errorMessages: string[]
  /** 警告消息列表 */
  warningMessages: string[]
  /** 提示消息列表 */
  infoMessages: string[]
  /** 验证时间 */
  validationTime: string
  /** 验证耗时（毫秒） */
  validationDuration: number
}

/**
 * 订单提交审核验证请求DTO
 */
export interface OrderReviewValidationRequestDTO {
  /** 订单ID */
  orderId: number
  /** 是否验证产品 */
  validateProducts: boolean
  /** 是否验证BOM */
  validateBom: boolean
  /** 是否验证工艺路线 */
  validateRouting: boolean
  /** 是否验证生产能力 */
  validateCapacity: boolean
  /** 是否验证成本 */
  validateCost: boolean
  /** 验证选项 */
  options?: Record<string, any>
}

/**
 * 订单提交审核验证响应VO
 */
export interface OrderReviewValidationResponseVO {
  /** 订单ID */
  orderId: number
  /** 订单编号 */
  orderNo: string
  /** 是否有效 */
  isValid: boolean
  /** 是否可以提交审核 */
  canSubmit: boolean
  /** 验证状态：1未验证，2验证中，3验证通过，4验证失败，5部分通过 */
  validationStatus: number
  /** 验证状态标签 */
  validationStatusLabel: string
  /** 产品验证结果 */
  productValidations: ProductValidationResult[]
  /** BOM验证结果 */
  bomValidations: BomValidationResult[]
  /** 工艺路线验证结果 */
  routingValidations: RoutingValidationResult[]
  /** 生产能力验证结果 */
  capacityValidations: CapacityValidationResult[]
  /** 成本验证结果 */
  costValidations: CostValidationResult[]
  /** 验证摘要 */
  summary: ValidationSummary
  /** 时间戳 */
  timestamp: string
}

/**
 * 验证历史记录
 */
export interface ValidationHistoryRecord {
  /** 记录ID */
  recordId: number
  /** 订单ID */
  orderId: number
  /** 订单编号 */
  orderNo: string
  /** 验证类型 */
  validationType: number
  /** 验证类型标签 */
  validationTypeLabel: string
  /** 验证状态 */
  validationStatus: number
  /** 验证状态标签 */
  validationStatusLabel: string
  /** 验证结果 */
  validationResult: OrderReviewValidationResponseVO
  /** 验证时间 */
  validationTime: string
  /** 验证人ID */
  validatorId?: number
  /** 验证人姓名 */
  validatorName?: string
  /** 备注 */
  remark?: string
}

/**
 * 修复问题请求DTO
 */
export interface FixValidationIssueRequestDTO {
  /** 订单ID */
  orderId: number
  /** 问题代码 */
  issueCode: string
  /** 修复数据 */
  fixData?: Record<string, any>
  /** 修复备注 */
  remark?: string
}

/**
 * 修复问题响应VO
 */
export interface FixValidationIssueResponseVO {
  /** 是否成功 */
  success: boolean
  /** 修复状态：1未修复，2修复中，3已修复，4无法修复 */
  fixStatus: number
  /** 修复状态标签 */
  fixStatusLabel: string
  /** 修复后的验证结果 */
  validationResult?: OrderReviewValidationResponseVO
  /** 错误消息 */
  errorMessage?: string
  /** 修复时间 */
  fixTime: string
}

/**
 * 批量验证请求DTO
 */
export interface BatchValidationRequestDTO {
  /** 订单ID列表 */
  orderIds: number[]
  /** 验证选项 */
  options: OrderReviewValidationRequestDTO
}

/**
 * 批量验证结果项
 */
export interface BatchValidationResultItem {
  /** 订单ID */
  orderId: number
  /** 订单编号 */
  orderNo: string
  /** 验证结果 */
  validationResult: OrderReviewValidationResponseVO
  /** 是否成功 */
  success: boolean
  /** 错误消息 */
  errorMessage?: string
}

/**
 * 批量验证响应VO
 */
export interface BatchValidationResponseVO {
  /** 总订单数 */
  totalOrders: number
  /** 成功数 */
  successCount: number
  /** 失败数 */
  failureCount: number
  /** 验证结果列表 */
  results: BatchValidationResultItem[]
  /** 验证摘要 */
  summary: ValidationSummary
  /** 时间戳 */
  timestamp: string
}

/**
 * 验证配置
 */
export interface ValidationConfig {
  /** 配置ID */
  configId: number
  /** 配置名称 */
  configName: string
  /** 验证类型 */
  validationType: number
  /** 验证规则 */
  rules: ValidationRule[]
  /** 是否启用 */
  enabled: boolean
  /** 优先级 */
  priority: number
  /** 创建时间 */
  createTime: string
  /** 更新时间 */
  updateTime: string
}

/**
 * 验证规则
 */
export interface ValidationRule {
  /** 规则ID */
  ruleId: number
  /** 规则代码 */
  ruleCode: string
  /** 规则名称 */
  ruleName: string
  /** 规则描述 */
  ruleDescription: string
  /** 错误级别：1错误，2警告，3提示 */
  errorLevel: number
  /** 错误消息模板 */
  errorMessageTemplate: string
  /** 修复建议模板 */
  fixSuggestionTemplate?: string
  /** 修复链接模板 */
  fixLinkTemplate?: string
  /** 是否启用 */
  enabled: boolean
  /** 条件表达式 */
  conditionExpression?: string
  /** 创建时间 */
  createTime: string
  /** 更新时间 */
  updateTime: string
}
