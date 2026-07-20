import request from '@/utils/request'
import type {
  ProductQueryParams,
  ProductFormData,
  ProductInstanceQueryParams,
  ProductInstanceFormData,
  ProductItem,
  ProductInstanceItem,
  ProductVo,
  ProductFullVO,
} from '@/types/product'
import type { PageResult, R } from '@/types'
// ==================== 产品管理API ====================
/**
 * 搜索产品
 */
export function searchProduct(keyword: string) {
  return request.get('/product/search', { params: { keyword } })
}

/**
 * 获取产品列表
 */
export function listProduct(params: ProductQueryParams) {
  return request.get<R<ProductItem[]>>('/product/list', { params })
}

/**
 * 获取产品列表
 */
export function listProductPage(params: ProductQueryParams) {
  return request.get<R<PageResult<ProductVo>>>('/product/page', { params })
}

/**
 * 获取产品详情
 */
export function getProductInfo(productId: number) {
  return request.get(`/product/${productId}`)
}

/**
 * 新增产品
 */
export function addProduct(data: ProductFormData) {
  return request.post('/product', data)
}

/**
 * 修改产品
 */
export function editProduct(data: ProductFormData) {
  return request.put('/product', data)
}

/**
 * 删除产品
 */
export function removeProduct(productIds: number | number[]) {
  return request.delete(`/product/${productIds}`)
}

/**
 * 导出产品列表
 */
export function exportProduct(params: ProductQueryParams) {
  return request
    .get('/product/export', {
      params,
      responseType: 'blob',
    })
    .then((response: Blob) => {
      // 创建下载链接
      const url = window.URL.createObjectURL(new Blob([response]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', '产品列表.xlsx')
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
    })
}

/**
 * 发布产品
 */
export function publishProduct(productId: number) {
  return request.put(`/product/release/${productId}`)
}

/**
 * 停用产品（对应后端 PUT /product/obsolete/{productId}）
 */
export function disableProduct(productId: number) {
  return request.put(`/product/obsolete/${productId}`)
}

/**
 * 提交审核
 */
export function submitApprove(productId: number) {
  return request.put(`/product/submit/${productId}`)
}

/**
 * 审核通过
 */
export function approveProduct(productId: number) {
  return request.put(`/product/approve/${productId}`)
}

/**
 * 审核驳回
 */
export function rejectProduct(productId: number, approveRemark: string) {
  return request.put(`/product/reject/${productId}`, { params: { productId, approveRemark } })
}

/**
 * 取消发布/取消审核
 */
export function cancelProduct(productId: number) {
  return request.put(`/product/cancel/${productId}`)
}

/**
 * 停产
 */
export function obsoleteProduct(productId: number) {
  return request.put(`/product/obsolete/${productId}`)
}

// ==================== 产品实例API ====================

/**
 * 获取产品实例列表
 */
export function listProductInstance(params: ProductInstanceQueryParams) {
  return request.get<R<PageResult<ProductInstanceItem>>>('/product/instance/list', {
    params,
  })
}

/**
 * 获取产品实例详情
 */
export function getProductInstanceInfo(instanceId: number) {
  return request.get(`/product/instance/${instanceId}`)
}

/**
 * 新增产品实例
 */
export function addProductInstance(data: ProductInstanceFormData) {
  return request.post('/product/instance', data)
}

/**
 * 修改产品实例
 */
export function editProductInstance(data: ProductInstanceFormData) {
  return request.put('/product/instance', data)
}

/**
 * 删除产品实例
 */
export function removeProductInstance(instanceId: number) {
  return request.delete(`/product/instance/${instanceId}`)
}

/**
 * 批量创建产品实例
 */
export function batchCreateProductInstances(orderId: number, config: Record<string, unknown>) {
  return request.post(`/product/instance/batch/${orderId}`, config)
}

/**
 * 更新实例状态
 */
export function updateInstanceStatus(instanceId: number, status: string, remark?: string) {
  return request.put(`/product/instance/status/${instanceId}`, {
    status,
    remark,
  })
}

/**
 * 获取实例生命周期状态
 */
export function getInstanceLifecycleStatus(instanceId: number) {
  return request.get(`/product/instance/lifecycle/${instanceId}`)
}

// ==================== 其他API ====================

/**
 * 验证产品配置
 */
export function validateProductConfig(productId: number) {
  return request.get(`/product/validate/${productId}`)
}

/**
 * 获取产品已审批的BOM列表
 */
export function getApprovedBomList(productId: number) {
  return request.get(`/product/bom/approved/${productId}`)
}

/**
 * 配置产品BOM
 */
export function configProductBom(productId: number, currentBomId: number) {
  return request.post('/product/config/bom', { productId, currentBomId })
}

/**
 * 配置产品工艺路线
 */
export function configProductRoute(productId: number, currentRouteId: number) {
  return request.post('/product/config/route', { productId, currentRouteId })
}

export function generateProductCode(categoryId: number) {
  return request.get<R<string>>(`/product/product-code/${categoryId}`)
}

export function generateSerialNo(customerId: number) {
  return request.get<R<string>>(`/product/serial-no/${customerId}`)
}

export function isUniqueProductCode(productCode: string) {
  return request.get<R<boolean>>(`/product/product-code/${productCode}/unique`)
}

export function getFullProduct(productId: number) {
  return request.get<R<ProductFullVO>>(`/product/${productId}/full`)
}

import * as category from '@/api/product/category'
import { productBomApi } from '@/api/product/bom'
import { productRouteApi } from '@/api/product/routing'
import { standardProcessApi } from '@/api/product/standardProcess'
export const productApi = {
  list: listProduct,
  page: listProductPage,
  info: getProductInfo,
  add: addProduct,
  edit: editProduct,
  remove: removeProduct,
  export: exportProduct,
  publish: publishProduct,
  disable: disableProduct,
  submitApprove: submitApprove,
  approve: approveProduct,
  reject: rejectProduct,
  cancel: cancelProduct,
  obsolete: obsoleteProduct,
  search: searchProduct,
  productCode: generateProductCode,
  generateSerialNo,
  category,
  bom: productBomApi,
  route: productRouteApi,
  standardProcess: standardProcessApi,
  isUniqueProductCode,
  full: getFullProduct,
  configBom: configProductBom,
  configRoute: configProductRoute,
}

export const productInstanceApi = {
  listProductInstance,
  getProductInstanceInfo,
  addProductInstance,
  editProductInstance,
  removeProductInstance,
  batchCreateProductInstances,
  updateInstanceStatus,
  getInstanceLifecycleStatus,
}
