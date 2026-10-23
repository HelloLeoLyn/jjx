import type { PageQuery } from '@/types'
import type { SysTag } from '@/types/system/tag'
// 物料实体
export interface InventoryMaterial {
  materialId: number
  materialCode: string
  materialName: string
  materialNameEn?: string
  materialType: string
  categoryId?: number
  categoryName?: string
  specification?: string
  materialGrade?: string
  model?: string
  color?: string
  brand?: string
  /** 别名/常用叫法，参与搜索 */
  searchKeywords?: string
  /** 尺寸：宽×长/厚，缺省 mm */
  size?: string
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
  batchControl: boolean
  shelfLife?: number
  expiryAlertDays: number
  status: string
  remark?: string
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  tags?: SysTag[]
}

// 物料查询参数
export interface InventoryMaterialQueryParams extends PageQuery {
  materialId?: number
  materialCode?: string
  materialName?: string
  materialNameEn?: string
  materialType?: string
  tagIds?: number[]
  tagMatchMode?: 'AND' | 'OR'
  categoryId?: number
  specification?: string
  materialGrade?: string
  model?: string
  color?: string
  brand?: string
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
  keyword?: string
  materialCode?: string
  materialName?: string
  specification?: string
  supplierId?: number
  categoryId?: number
  materialType?: string
  materialGrade?: string
  model?: string
  color?: string
  brand?: string
  tagId?: number
}
// 物料保存DTO
export interface MaterialSaveDTO {
  materialCode: string
  materialName: string
  materialType: string
  categoryId?: number
  specification?: string
  materialGrade?: string
  model?: string
  color?: string
  brand?: string
  searchKeywords?: string
  size?: string
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
  materialId: number
  materialCode?: string
  materialName?: string
  materialType?: string
  categoryId?: number | null
  specification?: string | null
  /** 传 null 表示显式清空；不传（undefined）表示保持原值 */
  materialGrade?: string | null
  model?: string | null
  color?: string | null
  brand?: string | null
  searchKeywords?: string | null
  size?: string | null
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
