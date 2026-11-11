// ==================== BOM管理类型 ====================
// ==================== 通用类型 ====================
import type { PageQuery, PageResult } from '@/types/index'
import type { BomSpecificationSource } from '@/components/bomSpecification'

/** 制造BOM产出身份（对应 Java ManufacturingOutputType；不是状态枚举，不复用 bomType）。 */
export type ManufacturingOutputType = 'PRODUCT' | 'MATERIAL'

/** 制造方案显示用编码+版本（前端展示，不落库；避免把数据库自增ID当标题）。 */
export interface ManufacturingSchemeLabel {
  bomCode?: string
  bomVersion?: string
}

/** 某产出对象的候选制造方案（GET /engineering/bom/manufacturing/options）。 */
export interface ManufacturingScheme {
  bomId: number
  bomCode: string
  bomName: string
  bomVersion: string
  outputType: ManufacturingOutputType
  outputId: number
  /** 方案状态，取值复用 BomStatusEnum（1草稿2审核中3已批准4已驳回5已作废）。 */
  approveStatus: number
  isCurrent?: boolean
  /** 当前产品上下文下该物料的主方案；productId 为空时为 null。 */
  isPrimary?: boolean | null
  itemCount?: number
  effectiveDate?: string | null
  expiryDate?: string | null
  remark?: string | null
  /** 采用 token：只证明已保存方案被选中，不落库。 */
  selectionToken?: string | null
}

/** 制造BOM保存/回读视图（POST/PUT /engineering/bom/manufacturing）。 */
export interface ManufacturingBomDetail {
  bomId: number
  bomCode: string
  bomName: string
  bomVersion: string
  outputType: ManufacturingOutputType
  outputId: number
  productId?: number | null
  approveStatus?: number
  isCurrent?: boolean
  effectiveDate?: string | null
  expiryDate?: string | null
  remark?: string | null
  createTime?: string
  updateTime?: string
  items: EngineeringBomItem[]
}

/**
 * 保存请求里的投入行：quantity 可为空——未保存的比例改动不把旧参考用量当新值提交，
 * 由后端按模数/基数重算。
 */
export type ManufacturingBomSaveItem = Omit<EngineeringBomItem, 'quantity'> & {
  quantity?: number
}

/** 制造BOM保存请求（一种产出、多行投入；不改父BOM、不自动发布）。 */
export interface ManufacturingBomSavePayload {
  bomId?: number
  bomCode: string
  bomName: string
  bomVersion?: string
  outputType: ManufacturingOutputType
  outputId: number
  effectiveDate?: string | null
  expiryDate?: string | null
  remark?: string
  items: ManufacturingBomSaveItem[]
}

/** 产品×投入物料的主方案选择（随父产品BOM保存持久化）。 */
export interface ManufacturingSchemeChoice {
  materialId: number
  bomId: number
  /** 只有用户明确调整主方案时才传真值；未调整不要传 false，避免覆盖原主。 */
  isPrimary?: boolean
  /** 只读回显字段，保存时后端忽略。 */
  bomCode?: string
  bomVersion?: string
  approveStatus?: number
  warning?: string
}
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
  /** 产出类型（PRODUCT/MATERIAL）；历史调用不传按 PRODUCT 兼容。 */
  outputType?: ManufacturingOutputType
  /** 产出ID：PRODUCT=productId，MATERIAL=materialId。 */
  outputId?: number | null
  /** 用户本次明确调整的产品主方案（只有显式调整才随父保存提交）。 */
  schemeChoices?: ManufacturingSchemeChoice[]
  /** 加载详情时的 product.scheme_revision，提交主方案时必带（后端乐观锁）。 */
  schemeRevision?: number | null
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
  /** 父节点明细ID（NULL=根节点；组合子行指向组合父行） */
  parentMaterialId?: number | null
  /** 显式下层制造方案 BOM ID（source_type=make 时使用；不是分组父行） */
  subBomId?: number | null
  /** 来源类型：buy 外购 / make 自制；subBomId 非空时必须是 make */
  sourceType?: string | null
  /** BOM 单位 → 物料档案单位的换算系数与依据（跨单位时后端必填） */
  unitFactor?: number | null
  unitFactorBasis?: string | null
  unitFactorSource?: string | null
  /** 前端标记：本行是否组合容器（半成品父行），不落库 */
  isGroup?: boolean
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
  /** 物料档案规格对照值，不落业务表 */
  materialSpecification?: string
  /** 物料档案单位（前端展示用，不落库；用于区分档案单位与 BOM 投入单位） */
  archiveUnit?: string
  /** 参考用量因未保存修改而失效（前端标记，不落库；后端重算后以回读值为准） */
  quantityStale?: boolean
  /** 物料档案规格及原始尺寸、属性和模板。 */
  materialSpecificationSource?: BomSpecificationSource
  lossRate: number
  remark?: string
  sortOrder: number
  /** 后端排序字段别名（engineering_bom_item.item_order）；提交沿用 sortOrder。 */
  itemOrder?: number
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
  action: 'APPROVE' | 'REJECT' | 'CONFIRM_REMOVE' | 'LINK_MATERIAL' | 'UNLINK_MATERIAL'
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
  /** 候选档案（dev-20261024-020）：人工确认用，唯一/多候选都返回 */
  candidates?: Array<{
    materialId?: number
    materialCode?: string
    materialName?: string
    specification?: string
    size?: string
    unit?: string
  }>
}

/** BOM 批量关联物料结果（dev-20261024-020） */
export interface BomMaterialBatchResult {
  total: number
  successCount: number
  failedCount: number
  rows: Array<{
    itemId: number
    success: boolean
    message?: string
    materialId?: number
    materialCode?: string
  }>
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
  /** 产品主方案 JSON 当前 revision（父 BOM 保存主方案时必带）。 */
  schemeRevision?: number | null
  /** 已保存的产品主方案选择。 */
  schemeChoices?: ManufacturingSchemeChoice[]
}

export interface BomItemBatchReviewRequest {
  bomId: number
  itemIds: number[]
  action: 'APPROVE'
  remark?: string
}

export interface BomItemBatchReviewResult {
  successIds: number[]
  failed: { itemId: number; reason: string }[]
}

/** SysOperLog的数字结果；现有OperResultEnum使用字符串，不能用于此接口。 */
export enum BomSaveLogResult {
  SUCCESS = 1,
}

export type BomOriginalValues = Record<string, { materialName?: string | null; specification?: string | null }>
