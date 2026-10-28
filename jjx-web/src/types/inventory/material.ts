import type { PageQuery } from '@/types'
import type { SysTag } from '@/types/system/tag'

// 物料实体
export interface InventoryMaterial {
  materialId: number
  materialCode: string
  materialName: string
  materialMeta?: string | null
  materialForm?: string | null
  attributeJson?: string | null
  readonly templateId?: number | null
  /** 历史响应类型兼容；新接口不返回。 */
  readonly thickness?: string | null
  readonly widthSize?: string | null
  isCustomerSupplied?: boolean
  isSample?: boolean
  categoryId?: number
  readonly rootType?: string | null
  readonly categoryRootName?: string | null
  categoryName?: string
  specification?: string
  materialGrade?: string
  model?: string
  color?: string
  brand?: string
  /** 别名/常用叫法，参与搜索 */
  searchKeywords?: string
  /** 后端由数值字段拼装的只读尺寸展示值。 */
  readonly size?: string
  /** 尺寸数值真源。 */
  widthMm?: number | null
  lengthMm?: number | null
  thicknessMm?: number | null
  /** 克重 g/m²（specBasis=GRAMMAGE 时使用） */
  grammage?: number | null
  /** 厚度公差 ±mm */
  thicknessTolerance?: number | null
  sizeUnit?: string | null
  /** 规格口径：THICKNESS/GRAMMAGE/SIZE */
  specBasis?: string | null
  /** 规格补充（属性生成的规格文本之外，≤30 字） */
  unit: string
  /** 库存视图字段（MaterialVO 返回，只读展示） */
  currentStock?: number
  availableStock?: number
  reservedStock?: number
  inTransitStock?: number
  safeStock: number
  maxStock: number
  reorderPoint: number
  standardPrice?: number
  leadTime?: number
  // 后端 MaterialVO 同时返回 supplierId/supplierName（物料默认供应商）
  supplierId?: number
  supplierName?: string
  /** 供应商侧品名/叫法：采购下单与来料核对用 */
  supplierMaterialName?: string
  batchControl: boolean
  shelfLife?: number
  expiryAlertDays: number
  status: string
  /** 归类来源：RULE/MANUAL/IMPORT，NULL=未归类（后端 MaterialVO，dev-20261024-004 阶段三） */
  classSource?: string | null
  /** 是否人工确认归类 */
  classConfirmed?: boolean | null
  remark?: string
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  tags?: SysTag[]
}

// 物料查询参数
export interface InventoryMaterialQueryParams extends PageQuery {
  /** 统一模糊搜索：名称/编码/型号/别名/规格/分类，多词空格 AND（dev-20261024-024） */
  keyword?: string
  rootType?: string
  materialId?: number
  materialCode?: string
  materialName?: string
  materialMeta?: string
  materialForm?: string
  isCustomerSupplied?: boolean
  isSample?: boolean
  tagIds?: number[]
  tagMatchMode?: 'AND' | 'OR'
  categoryId?: number
  specification?: string
  materialGrade?: string
  model?: string
  color?: string
  brand?: string
  /** 归类三态：unclassified / rule_unconfirmed / confirmed */
  classStatus?: string
  status?: string
  batchControl?: boolean
  supplierId?: number
  defaultWarehouseId?: number
  lowStock?: boolean
  expiring?: boolean
  createTimeStart?: string
  createTimeEnd?: string
  orderBy?: string
  orderDirection?: string
}
export interface MaterialQueryDTO extends PageQuery {
  rootType?: string
  keyword?: string
  materialCode?: string
  materialName?: string
  specification?: string
  supplierId?: number
  categoryId?: number
  materialGrade?: string
  materialMeta?: string
  materialForm?: string
  model?: string
  color?: string
  brand?: string
  tagId?: number
}
// 物料保存DTO
export interface MaterialSaveDTO {
  attributeJson?: string | null
  readonly templateId?: number | null
  materialCode: string
  materialName: string
  categoryId?: number
  specification?: string
  materialGrade?: string
  model?: string
  color?: string
  brand?: string
  searchKeywords?: string
  widthMm?: number | null
  lengthMm?: number | null
  thicknessMm?: number | null
  sizeUnit?: string | null
  unit: string
  safeStock: number
  maxStock: number
  reorderPoint: number
  standardPrice?: number
  leadTime?: number
  supplierName?: string
  batchControl: boolean
  shelfLife?: number
  expiryAlertDays: number
  remark?: string
  tagIds?: number[]
}

// 物料更新DTO
export interface MaterialUpdateDTO {
  attributeJson?: string | null
  readonly templateId?: number | null
  materialId: number
  materialCode?: string
  materialName?: string
  categoryId?: number | null
  specification?: string | null
  /** 传 null 表示显式清空；不传（undefined）表示保持原值 */
  materialGrade?: string | null
  model?: string | null
  color?: string | null
  brand?: string | null
  searchKeywords?: string | null
  widthMm?: number | null
  lengthMm?: number | null
  thicknessMm?: number | null
  sizeUnit?: string | null
  unit?: string
  safeStock?: number
  maxStock?: number
  reorderPoint?: number
  standardPrice?: number
  leadTime?: number
  supplierName?: string
  batchControl?: boolean
  shelfLife?: number
  expiryAlertDays?: number
  remark?: string
  status?: string
  tagIds?: number[]
}

// ==================== 材料分类类型 ====================

export interface MaterialCategoryQueryParams extends PageQuery {
  categoryCode?: string
  readonly rootType?: string | null
  readonly categoryRootName?: string | null
  categoryName?: string
  parentId?: number
  status?: string
}

export interface MaterialCategoryFormData {
  categoryId?: number
  parentId: number
  categoryCode: string
  categoryName: string
  categoryLevel: number
  sortOrder: number
  status: string
  remark?: string
}

export interface MaterialCategoryListResult extends PageQuery {
  rows: MaterialCategoryItem[]
  total: number
}

export interface MaterialCategoryItem {
  categoryId: number
  parentId: number
  categoryCode: string
  categoryName: string
  categoryLevel: number
  sortOrder: number
  status: string
  description: string
  createTime: string
  updateTime: string
  createBy: string
  updateBy: string
  children?: MaterialCategoryItem[]
}
// 组件 Props 类型
// types/material.ts
export interface MaterialSelectorProps {
  modelValue: InventoryMaterial | number | string | null
  placeholder?: string
  clearable?: boolean
  disabled?: boolean
  size?: 'large' | 'default' | 'small'
  // ✅ 修改这里：添加 materialId, materialCode, materialName
  valueType?: 'object' | 'materialId' | 'materialCode' | 'materialName'
  debounceDelay?: number
  minKeywordLength?: number
  autoSelectFirst?: boolean
}
