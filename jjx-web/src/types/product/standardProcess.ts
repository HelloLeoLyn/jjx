import type { PageQuery } from '@/types'

/**
 * 鏍囧噯宸ュ簭鏌ヨ鍙傛暟
 */
export interface StandardProcessQueryParams extends PageQuery {
  processCode?: string
  processName?: string
  processType?: string
  processCategory?: string
  isEnabled?: number
  orderByColumn?: string
  isAsc?: string
}

/**
 * 鏍囧噯宸ュ簭琛ㄥ崟鏁版嵁
 */
export interface StandardProcessFormData {
  processId?: number
  processCode: string
  processName: string
  processType: string
  processCategory: string
  standardLaborHours: number
  standardMachineHours: number
  processParamTemplate: string
  skillRequirement: string
  equipmentType: string
  qualityStandard: string
  description: string
  icon?: string
  /** 是否带下标：0-不带,1-带 */
  hasIndex: number
  isEnabled: number
  displayOrder: number
}

/**
 * 鏍囧噯宸ュ簭鍒楄〃椤癸紙瀵瑰簲鍚庣 VO锛?
 */
export interface StandardProcessItem {
  processId: number
  processCode: string
  processName: string
  processType: string
  processTypeName: string
  processTypeTagType: string
  processCategory: string
  processCategoryName: string
  processCategoryTagType: string
  standardLaborHours: number
  standardMachineHours: number
  processParamTemplate: string
  skillRequirement: string
  equipmentType: string
  qualityStandard: string
  description: string
  icon?: string
  /** 是否带下标：0-不带,1-带 */
  hasIndex: number
  isEnabled: number
  isEnabledName: string
  isEnabledTagType: string
  displayOrder: number
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}


