// types/product/ProductRoutingItem.ts
// ==================== 工艺路线类型 ====================

/**
 * 工艺路线查询参数
 * 对应后端 ProductRoutingQueryDTO
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
 * 对应后端 ProductRoutingDTO
 */
export interface ProductRouteFormData {
  routingId?: number
  routingCode: string
  routingName: string
  productId: number
  productCode: string
  productName: string
  routingVersion: string
  description?: string
  remark?: string
  items: ProductRoutingItemVO[]
}

/**
 * 工艺路线列表项
 * 对应后端 ProductRoutingVO
 */
export interface ProductRoutingVO {
  routingId: number
  routingCode: string
  routingName: string
  productId: number
  productCode: string
  productName: string
  routingVersion: string
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
  items: ProductRoutingItemVO[]
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
 * 对应后端 ProductRoutingItem
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
 * 对应后端 ProductRoutingItem（含非数据库字段）
 * 产品路线明细VO
 */
export interface ProductRoutingItemVO {
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
}

/**
 * 产品路线明细DTO（新增/编辑）
 * 对应后端 ProductRoutingItemDTO
 */
export interface ProductRoutingItemDTO {
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
}

/**
 * 产品路线明细查询参数
 */
export interface ProductRoutingItemQueryDTO {
  routingId?: number
  processId?: number
  pageNum?: number
  pageSize?: number
}
