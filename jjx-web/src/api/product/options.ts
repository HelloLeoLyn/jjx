import request from '@/utils/request'
/**
 * 获取产品选项
 */
export function getProductOptions() {
  return request.get('/inventory/material/options')
}

/**
 * 获取物料选项
 */
export function getMaterialOptions() {
  return request.get('/inventory/material/options')
}

/**
 * 获取工序选项
 */
export function getProcessOptions() {
  return request.get('/engineering/standard-processes/enabled')
}
export const productOptionsApi = {
  product: getProcessOptions,
  material: getMaterialOptions,
  process: getProcessOptions,
}
