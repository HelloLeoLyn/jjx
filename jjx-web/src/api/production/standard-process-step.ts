import request from '@/utils/request'
import type { StandardProcessFormData, StandardProcessQueryParams } from '@/types/product/standardProcess'

// 标准工序定义接口

// 查询标准工序列表
export function listStep(query?: StandardProcessQueryParams) {
  return request({
    url: '/product/standard-process-step/list',
    method: 'get',
    params: query,
  })
}

// 查询启用的标准工序列表
export function listEnabled() {
  return request({
    url: '/product/standard-process-step/enabled',
    method: 'get',
  })
}

// 查询推荐的标准工序
export function listRecommended() {
  return request({
    url: '/product/standard-process-step/recommended',
    method: 'get',
  })
}

// 根据工序编码查询
export function getStepByCode(stepCode: string) {
  return request({
    url: `/product/standard-process-step/code/${stepCode}`,
    method: 'get',
  })
}

// 根据工序类型查询
export function listByStepType(stepType: string) {
  return request({
    url: `/product/standard-process-step/type/${stepType}`,
    method: 'get',
  })
}

// 根据工序类别查询
export function listByStepCategory(stepCategory: string) {
  return request({
    url: `/product/standard-process-step/category/${stepCategory}`,
    method: 'get',
  })
}

// 搜索标准工序
export function searchStep(keyword?: string) {
  return request({
    url: '/product/standard-process-step/search',
    method: 'get',
    params: { keyword },
  })
}

// 查询最常用的标准工序
export function listMostUsed(limit = 10) {
  return request({
    url: '/product/standard-process-step/most-used',
    method: 'get',
    params: { limit },
  })
}

// 查询标准工序详情
export function getStep(stepId: number) {
  return request({
    url: `/product/standard-process-step/${stepId}`,
    method: 'get',
  })
}

// 新增标准工序
export function addStep(data: StandardProcessFormData) {
  return request({
    url: '/product/standard-process-step',
    method: 'post',
    data: data,
  })
}

// 修改标准工序
export function updateStep(data: StandardProcessFormData) {
  return request({
    url: '/product/standard-process-step',
    method: 'put',
    data: data,
  })
}

// 删除标准工序
export function delStep(stepId: number) {
  return request({
    url: `/product/standard-process-step/${stepId}`,
    method: 'delete',
  })
}

// 批量删除标准工序
export function batchDelStep(stepIds: number[]) {
  return request({
    url: '/product/standard-process-step/batch',
    method: 'delete',
    data: stepIds,
  })
}

// 启用标准工序
export function enableStep(stepId: number) {
  return request({
    url: `/product/standard-process-step/enable/${stepId}`,
    method: 'put',
  })
}

// 禁用标准工序
export function disableStep(stepId: number) {
  return request({
    url: `/product/standard-process-step/disable/${stepId}`,
    method: 'put',
  })
}

// 批量启用标准工序
export function batchEnableStep(stepIds: number[]) {
  return request({
    url: '/product/standard-process-step/batch-enable',
    method: 'put',
    data: stepIds,
  })
}

// 批量禁用标准工序
export function batchDisableStep(stepIds: number[]) {
  return request({
    url: '/product/standard-process-step/batch-disable',
    method: 'put',
    data: stepIds,
  })
}

// 验证工序编码是否唯一
export function checkCodeUnique(stepCode: string, excludeStepId?: number) {
  return request({
    url: '/product/standard-process-step/check-code-unique',
    method: 'get',
    params: { stepCode, excludeStepId },
  })
}

// 获取工序类别列表
export function listCategories() {
  return request({
    url: '/product/standard-process-step/categories',
    method: 'get',
  })
}

// 获取工序类型列表
export function listTypes() {
  return request({
    url: '/product/standard-process-step/types',
    method: 'get',
  })
}

// 获取工序使用统计
export function getStatistics() {
  return request({
    url: '/product/standard-process-step/statistics',
    method: 'get',
  })
}

// 初始化默认工序
export function initDefaultSteps() {
  return request({
    url: '/product/standard-process-step/init-default',
    method: 'post',
  })
}

// 导出标准工序
export function exportStep(query?: StandardProcessQueryParams) {
  return request({
    url: '/product/standard-process-step/export',
    method: 'get',
    params: query,
    responseType: 'blob',
  })
}

// 导入标准工序
export function importStep(data: FormData) {
  return request({
    url: '/product/standard-process-step/import',
    method: 'post',
    data: data,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

// 下载导入模板
export function downloadTemplate() {
  return request({
    url: '/product/standard-process-step/import-template',
    method: 'get',
    responseType: 'blob',
  })
}
