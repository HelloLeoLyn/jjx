import request from '@/utils/request'
import type { PurchaseSupplier, SupplierQueryParams } from '@/types/purchase'

// 查询供应商列表
export function listSupplier(params?: SupplierQueryParams) {
  return request({
    url: '/purchase/supplier/list',
    method: 'get',
    params,
  })
}

// 查询供应商详细
export function getSupplier(supplierId: number) {
  return request({
    url: `/purchase/supplier/${supplierId}`,
    method: 'get',
  })
}

// 新增供应商
export function addSupplier(data: PurchaseSupplier) {
  return request({
    url: '/purchase/supplier',
    method: 'post',
    data,
  })
}

// 修改供应商
export function updateSupplier(data: PurchaseSupplier) {
  return request({
    url: '/purchase/supplier',
    method: 'put',
    data,
  })
}

// 删除供应商
export function delSupplier(supplierIds: number | number[]) {
  return request({
    url: `/purchase/supplier/${supplierIds}`,
    method: 'delete',
  })
}

// 导出供应商
export function exportSupplier(params?: SupplierQueryParams) {
  return request({
    url: '/purchase/supplier/export',
    method: 'get',
    params,
    responseType: 'blob',
  })
}

// 更新供应商状态
export function changeSupplierStatus(supplierId: number, status: string) {
  return request({
    url: `/purchase/supplier/status/${supplierId}`,
    method: 'put',
    params: { status },
  })
}

// 更新供应商评估信息
export function updateSupplierEvaluation(
  supplierId: number,
  evaluationScore: number,
  qualityScore: number,
  deliveryScore: number,
  priceScore: number
) {
  return request({
    url: `/purchase/supplier/evaluation/${supplierId}`,
    method: 'put',
    data: { supplierId, evaluationScore, qualityScore, deliveryScore, priceScore },
  })
}

// 根据供应商类型查询供应商列表
export function getSuppliersByType(supplierType: string) {
  return request({
    url: `/purchase/supplier/type/${supplierType}`,
    method: 'get',
  })
}

// 查询活跃供应商列表
export function getActiveSuppliers() {
  return request({
    url: '/purchase/supplier/active',
    method: 'get',
  })
}

// 查询优质供应商列表
export function getHighQualitySuppliers(minScore: number = 80) {
  return request({
    url: '/purchase/supplier/high-quality',
    method: 'get',
    params: { minScore },
  })
}

// 检查供应商编码是否唯一
export function checkSupplierCodeUnique(supplierCode: string) {
  return request({
    url: '/purchase/supplier/check-supplier-code-unique',
    method: 'get',
    params: { supplierCode },
  })
}

// 检查供应商名称是否唯一
export function checkSupplierNameUnique(supplierName: string) {
  return request({
    url: '/purchase/supplier/check-supplier-name-unique',
    method: 'get',
    params: { supplierName },
  })
}

// 获取供应商统计信息
export function getSupplierStatistics() {
  return request({
    url: '/purchase/supplier/statistics',
    method: 'get',
  })
}

// 导入供应商数据
export function importSuppliers(data: FormData) {
  return request({
    url: '/purchase/supplier/import',
    method: 'post',
    data,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 下载导入模板
export function importTemplate() {
  return request({
    url: '/purchase/supplier/importTemplate',
    method: 'get',
    responseType: 'blob',
  })
}
