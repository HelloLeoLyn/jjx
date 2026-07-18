import type { PageQuery } from '@/types'
// 物料实体
export interface InventoryMaterial {
  materialId: number
  materialCode: string
  materialName: string
  materialNameEn?: string
  materialType: string
  categoryId?: number
  specification?: string
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
  status: string
  remark?: string
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
}

// 物料查询参数
export interface InventoryMaterialQueryParams extends PageQuery {
  materialId?: number
  materialCode?: string
  materialName?: string
  materialNameEn?: string
  materialType?: string
  categoryId?: number
  specification?: string
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
  materialName?: string
  specification?: string
  supplierId?: number
}
// 物料保存DTO
export interface MaterialSaveDTO {
  materialCode: string
  materialName: string
  materialType: string
  specification?: string
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
}

// 物料更新DTO
export interface MaterialUpdateDTO {
  materialId: number
  materialCode?: string
  materialName?: string
  materialType?: string
  specification?: string
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
