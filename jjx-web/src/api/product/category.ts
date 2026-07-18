import request from '@/utils/request'
import type {
  ProductCategoryQueryParams,
  ProductCategoryItem,
  ProductCategoryFormData,
} from '@/types/product/category'
import type { PageResult, R } from '@/types'
// ==================== 产品分类API ====================

/**
 * 获取产品分类列表
 */
export function listProductCategory(params: ProductCategoryQueryParams) {
  return request.get<R<ProductCategoryItem[]>>('/product/category/list', {
    params,
  })
}

/**
 * 获取产品分类树
 */
export function getProductCategoryTree(params?: ProductCategoryQueryParams) {
  return request.get<R<ProductCategoryItem[]>>('/product/category/tree', { params })
}

/**
 * 获取产品分类详情
 */
export function getProductCategoryInfo(categoryId: number) {
  return request.get<R<ProductCategoryItem>>(`/product/category/${categoryId}`)
}

/**
 * 新增产品分类
 */
export function addProductCategory(data: ProductCategoryFormData) {
  return request.post('/product/category', data)
}

/**
 * 修改产品分类
 */
export function editProductCategory(data: ProductCategoryFormData) {
  return request.put('/product/category', data)
}

/**
 * 删除产品分类
 */
export function removeProductCategory(categoryId: number) {
  return request.delete(`/product/category/${categoryId}`)
}

export const productCategoryApi = {
  list: listProductCategory,
  tree: getProductCategoryTree,
  getInfo: getProductCategoryInfo,
  add: addProductCategory,
  edit: editProductCategory,
  remove: removeProductCategory,
}
