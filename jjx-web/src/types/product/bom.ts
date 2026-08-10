// ==================== BOM管理类型 ====================
// ==================== 通用类型 ====================
import type { PageQuery, PageResult } from '@/types/index'
export interface EngineeringBomQueryParams extends PageQuery {
  bomCode?: string
  bomName?: string
  productCode?: string
  productName?: string
  bomStatus?: string
  approveStatus?: string
  startDate?: string
  endDate?: string
}

export interface EngineeringBomFormData {
  bomId?: number
  bomCode: string
  bomName: string
  bomVersion?: string
  productId: number
  productCode: string
  productName: string
  approveStatus?: number
  isCurrent: boolean
  effectiveDate?: string
  expiryDate?: string
  remark?: string
  items: EngineeringBomItem[]
}

export interface EngineeringBomItem {
  itemId?: number
  bomId?: number
  materialId: number
  materialCode: string
  materialName: string
  specification: string
  unit: string
  quantity: number
  /** 应用料（含损耗）= 用量×(1+损耗率/100) */
  appliedQty?: number
  /** 实际投料（板材/卷材按最低投料向上取整） */
  actualIssueQty?: number
  /** 物料类型（R=板材/卷材） */
  materialType?: string
  lossRate: number
  remark?: string
  sortOrder: number
  /** 模数：每份材料可产出产品数量 */
  moduleQty?: number
  /** 基数：每个产品所需材料份数 */
  baseQty?: number
  /** 最低投料量 */
  minIssueQty?: number
  /** 规格-宽度(mm) */
  widthMm?: number
  /** 规格-长度(mm) */
  lengthMm?: number
  create?: boolean
}
export interface BomSimpleVo {
  bomId: number
  bomCode: string
  bomName: string
  bomVersion: string
  isCurrent: number
  approveStatus: number
  materialCount: number
}
export interface EngineeringBom {
  bomId: number
  bomCode: string
  bomName: string
  productId: number
  productCode: string
  productName: string
  bomVersion: string
  approveStatus: number
  isCurrent: boolean
  effectiveDate: string
  expiryDate: string
  remark: string
  approveRemark?: string
  createTime: string
  updateTime: string
  createBy: string
  updateBy: string
}
export interface EngineeringBomVO {
  bomId: number
  bomCode: string
  bomName: string
  productId: number
  productCode: string
  productName: string
  bomVersion: string
  approveStatus: number
  isCurrent: boolean
  effectiveDate: string
  expiryDate: string
  remark: string
  approveRemark?: string
  createTime: string
  updateTime: string
  createBy: string
  updateBy: string
  items: EngineeringBomItem[]
}
