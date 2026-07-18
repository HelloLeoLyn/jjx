// ==================== 产品分类类型 ====================
import type { PageQuery, PageResult } from '@/types/index'
export interface ProductCategoryQueryParams extends PageQuery {
  categoryCode?: string
  categoryName?: string
  parentId?: number
  status?: string
}

export interface ProductCategoryFormData {
  categoryId?: number
  parentId: number
  categoryCode: string
  categoryName: string
  categoryLevel: number
  sortOrder: number
  status: string
  remark?: string
}

export interface ProductCategoryItem {
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
  children?: ProductCategoryItem[]
}
export interface ProductCategoryDictItem {
  categoryId: number
  parentId: number
  categoryCode: string
  categoryName: string
  categoryLevel: number
}
