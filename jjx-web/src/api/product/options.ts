import request from '@/utils/request'
/**
 * 获取产品选项
 */
export function getProductOptions() {
  return request.get('/product/options')
}

/**
 * 获取物料选项
 */
export function getMaterialOptions() {
  return request.get('/product/material/options')
}

/**
 * 获取工序选项
 */
export function getProcessOptions() {
  return request.get('/product/process/options')
}
export const productOptionsApi = {
  product: getProcessOptions,
  material: getMaterialOptions,
  process: getProcessOptions,
}
