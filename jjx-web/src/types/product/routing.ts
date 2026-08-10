// types/product/EngineeringRoutingItem.ts
// ==================== 工艺路线类型 ====================

/**
 * 工艺路线查询参数
 * 对应后端 EngineeringRoutingQueryDTO
 */
export interface ProductRouteQueryParams {
  routingCode?: string
  routingName?: string
  productId?: number
  productCode?: string
  approveStatus?: number
  isCurrent?: number
  pageNum?: number
  pageSize?: number
  orderByColumn?: string
  isAsc?: string
}

/**
 * 工艺路线表单数据
 * 对应后端 EngineeringRoutingDTO
 */
export interface ProductRouteFormData {
  routingId?: number
  routingCode: string
  routingName: string
  productId: number
  productCode: string
  productName: string
  routingVersion: string
  /** 版本号（V1.0/V2.0） */
  version?: string
  /** 父版本路线ID */
  parentRoutingId?: number
  /** 来源打样单ID */
  sourceSampleId?: number
  /** 保存时是否自动升版 */
  bumpVersion?: boolean
  /** 变更说明 */
  changeNote?: string
  description?: string
  remark?: string
  items: EngineeringRoutingItemVO[]
}

/**
 * 工艺路线列表项
 * 对应后端 EngineeringRoutingVO
 */
export interface EngineeringRoutingVO {
  routingId: number
  routingCode: string
  routingName: string
  productId: number
  productCode: string
  productName: string
  routingVersion: string
  /** 版本号（V1.0/V2.0） */
  version?: string
  /** 父版本路线ID */
  parentRoutingId?: number
  /** 来源打样单ID */
  sourceSampleId?: number
  isCurrent: number
  isCurrentName: string
  approveStatus: number
  approveStatusName: string
  totalLaborHours: number
  totalMachineHours: number
  processCount: number
  description: string
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
  remark: string
  items: EngineeringRoutingItemVO[]
  groupSummaries?: GroupSummary[]
}

/**
 * 组合汇总信息
 */
export interface GroupSummary {
  groupId: number
  groupOrder: number
  groupName: string
  totalLaborHours: number
  totalMachineHours: number
  processCount: number
}

/**
 * 工艺路线工序明细表单数据
 * 对应后端 EngineeringRoutingItem
 */
export interface ProductRouteItemFormData {
  itemId?: number
  routingId?: number
  processId: number
  processOrder: number
  customLaborHours?: number
  customMachineHours?: number
  customProcessParams?: string
  description?: string
  processName?: string
  processType?: string
}

/**
 * 工艺路线工序明细
 * 对应后端 EngineeringRoutingItem（含非数据库字段）
 * 产品路线明细VO
 */
export interface EngineeringRoutingItemVO {
  // 路线明细字段
  itemId: number
  routingId: number
  processOrder: number
  customLaborHours?: number
  customMachineHours?: number
  customProcessParams?: string
  description?: string
  createTime: string
  updateTime: string

  // 组合字段
  groupId?: number
  groupOrder?: number
  groupName?: string

  // 标准工序字段（平铺）
  processId: number
  processCode: string
  processName: string
  processType: string
  processTypeName: string
  processTypeTagType: string
  processCategory?: string
  processCategoryName?: string
  processCategoryTagType?: string
  standardLaborHours: number
  standardMachineHours: number
  processParamTemplate?: string
  skillRequirement?: string
  equipmentType?: string
  qualityStandard?: string
  isEnabled: number
  isEnabledName: string
  isEnabledTagType: string
  displayOrder: number
  icon?: string

  // ==================== 下标/依赖/可选（批次1新增） ====================
  /** 下标数字（带下标工序的下标值） */
  indexNumber?: number | null
  /** 前置依赖标识（如 PANEL_4） */
  precondition?: string | null
  /** 前置依赖显示名 */
  preconditionDisplay?: string | null
  /** 可选工序：0-必做,1-可选 */
  isOptional?: number
  /** 标准工序是否带下标 */
  hasIndex?: number
}

/**
 * 产品路线明细DTO（新增/编辑）
 * 对应后端 EngineeringRoutingItemDTO
 */
export interface EngineeringRoutingItemDTO {
  itemId?: number
  routingId?: number
  groupId?: number
  groupOrder?: number
  groupName?: string
  processId: number
  processOrder?: number
  customLaborHours?: number
  customMachineHours?: number
  customProcessParams?: string
  description?: string
  processCategory?: string
  indexNumber?: number
  precondition?: string
  preconditionDisplay?: string
  isOptional?: number
}

/**
 * 产品路线明细查询参数
 */
export interface EngineeringRoutingItemQueryDTO {
  routingId?: number
  processId?: number
  pageNum?: number
  pageSize?: number
}
