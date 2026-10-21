// ==================== BOM管理类型 ====================
// ==================== 通用类型 ====================
import type { PageQuery, PageResult } from '@/types/index'
export interface EngineeringBomQueryParams extends PageQuery {
  showAllVersions?: boolean
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
  productId: number | null
  productCode: string
  productName: string
  approveStatus?: number
  isCurrent: boolean
  effectiveDate?: string
  expiryDate?: string
  remark?: string
  items: EngineeringBomItem[]
}

/** BOM 明细行审核状态（dev-20261020-029，对应 Java ProductEnums.BomItemReviewStatus） */
export type BomItemReviewStatus = 1 | 2 | 3 | 4 | 5

export interface EngineeringBomItem {
  itemId?: number
  bomId?: number
  /** 行审核状态：1草稿 2待审核 3审核通过 4审核驳回 5待移除 */
  reviewStatus?: BomItemReviewStatus
  /** 行审核驳回原因 */
  rejectReason?: string
  /** 批准时内容指纹 */
  approvedFingerprint?: string
  approvedBy?: string
  approvedTime?: string
  /** 待移除行是否已被审核员确认 */
  removeConfirmed?: boolean
  removeConfirmedBy?: string
  removeConfirmedTime?: string
  /** 编辑时请求移除该行（已通过行将转待移除） */
  removeRequested?: boolean
  /** 父节点明细ID（NULL=根节点，树形结构） */
  parentMaterialId?: number | null
  /** 子节点（树形表格用） */
  children?: EngineeringBomItem[]
  materialId: number
  materialCode: string
  materialName: string
  specification: string
  /** 项目关联的标准工序ID；未匹配或自填名称时可空 */
  processId?: number
  /** 项目名称；未匹配时原样保存用户填写的名称 */
  processName?: string
  /** 兼容字段，由项目是否为空派生 */
  isCommon?: boolean
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
  /** 跳距（含单位，如50mm） */
  split?: string
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
/** BOM 明细行级审核请求（dev-20261020-029） */
export interface BomItemReviewRequest {
  itemId: number
  action: 'APPROVE' | 'REJECT' | 'CONFIRM_REMOVE' | 'LINK_MATERIAL'
  remark?: string
  materialId?: number
}

export interface BomCheckIssue {
  /** 明细ID（问题所在行；主表级问题为空） */
  itemId?: number
  materialCode?: string
  materialName?: string
  /** 字段名 */
  field?: string
  /** 问题描述 */
  message?: string
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

/** BOM 物料全量同步请求项（dev-20261010-002） */
export interface BomMaterialMatchItem {
  /** 行定位（前端回填用） */
  index: number
  /** 物料名称 */
  name: string
  /** 规格 */
  spec: string
}

/** BOM 物料全量同步匹配结果（dev-20261010-002） */
export interface BomMaterialMatchResult {
  index: number
  /** MATCHED 唯一命中 / AMBIGUOUS 多条待确认 / NOT_FOUND 未匹配 */
  status: 'MATCHED' | 'AMBIGUOUS' | 'NOT_FOUND'
  materialId?: number
  materialCode?: string
  unit?: string
  matchCount: number
}
export interface EngineeringBom {
  versionCount?: number
  bomId: number
  bomCode: string
  bomName: string
  productId: number
  productCode: string
  productName: string
  bomVersion: string
  /** 版本号（V1.0/V2.0，统一字段） */
  version?: string
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
  /** 版本号（V1.0/V2.0，统一字段） */
  version?: string
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
