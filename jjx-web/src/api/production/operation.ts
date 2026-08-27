import request from '@/utils/request'
import type {
  OperationItem,
  OperationQueryParams,
  OperationFormData,
} from '@/types/production/operation'

// 获取操作任务列表
export function getOperationList(params: OperationQueryParams) {
  return request({
    url: '/production/operation/list',
    method: 'get',
    params,
  })
}

// 获取操作任务详情
export function getOperationDetail(operationId: string) {
  return request({
    url: `/production/operation/${operationId}`,
    method: 'get',
  })
}

// 创建操作任务
export function createOperation(data: OperationFormData) {
  return request({
    url: '/production/operation',
    method: 'post',
    data,
  })
}

// 更新操作任务
export function updateOperation(data: OperationFormData) {
  return request({
    url: '/production/operation',
    method: 'put',
    data,
  })
}

// 删除操作任务
export function deleteOperation(operationId: string) {
  return request({
    url: `/production/operation/${operationId}`,
    method: 'delete',
  })
}

// 开始操作任务
export function startOperation(operationId: string) {
  return request({
    url: `/production/operation/${operationId}/start`,
    method: 'put',
  })
}

// 完成操作任务
export function completeOperation(
  operationId: string,
  data?: { completedQuantity?: number; qualityResult?: string }
) {
  return request({
    url: `/production/operation/${operationId}/complete`,
    method: 'put',
    data,
  })
}

// 取消操作任务
export function cancelOperation(operationId: string) {
  return request({
    url: `/production/operation/${operationId}/cancel`,
    method: 'put',
  })
}

// 获取操作统计数据
export function getOperationStats(params?: { startDate?: string; endDate?: string }) {
  return request({
    url: '/production/operation/stats',
    method: 'get',
    params,
  })
}

// 获取工单选项
export function getWorkOrderOptions() {
  return request({
    url: '/production/operation/work-order-options',
    method: 'get',
  })
}

// 获取工序选项
export function getStepOptions(workOrderId?: string) {
  return request({
    url: '/production/operation/step-options',
    method: 'get',
    params: { workOrderId },
  })
}

// 获取操作员选项
export function getOperatorOptions() {
  return request({
    url: '/production/operation/operator-options',
    method: 'get',
  })
}

// 获取设备选项
export function getEquipmentOptions() {
  return request({
    url: '/production/operation/equipment-options',
    method: 'get',
  })
}

// 导出操作数据
export function exportOperation(params: OperationQueryParams) {
  return request({
    url: '/production/operation/export',
    method: 'get',
    params,
    responseType: 'blob',
  })
}

// 批量更新操作状态
export function batchUpdateOperationStatus(data: { operationIds: string[]; status: string }) {
  return request({
    url: '/production/operation/batch-status',
    method: 'put',
    data,
  })
}

// 获取操作记录
export function getOperationRecords(operationId: string) {
  return request({
    url: `/production/operation/${operationId}/records`,
    method: 'get',
  })
}

// 记录操作参数
export function recordOperationParameters(operationId: string, parameters: Record<string, unknown>) {
  return request({
    url: `/production/operation/${operationId}/parameters`,
    method: 'put',
    data: { parameters },
  })
}

// 记录质量结果
export function recordQualityResult(operationId: string, qualityResult: string) {
  return request({
    url: `/production/operation/${operationId}/quality`,
    method: 'put',
    data: { qualityResult },
  })
}
