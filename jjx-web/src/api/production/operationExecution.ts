import request from '@/utils/request'
import type {
  OperationExecutionVO,
  OperationExecutionQuery,
  OperationExecutionCreateDTO,
  OperationExecutionUpdateDTO,
  OperationExecutionStats,
} from '@/types/production/operationExecution'
import type { PageResult, R } from '@/types'

// 工序执行 API
export const operationExecutionApi = {
  /**
   * 分页查询工序执行列表
   */
  list(params: OperationExecutionQuery) {
    return request.get<R<PageResult<OperationExecutionVO>>>(
      '/production/operation-execution/list',
      { params }
    )
  },

  /**
   * 查询工序执行列表（全量）
   */
  all(params?: OperationExecutionQuery) {
    return request.get<R<OperationExecutionVO[]>>('/production/operation-execution/all', { params })
  },

  /**
   * 获取工序执行详情
   */
  getInfo(executionId: number) {
    return request.get<R<OperationExecutionVO>>(`/production/operation-execution/${executionId}`)
  },

  /**
   * 创建工序执行
   */
  add(data: OperationExecutionCreateDTO) {
    return request.post<R<void>>('/production/operation-execution', data)
  },

  /**
   * 更新工序执行
   */
  edit(data: OperationExecutionUpdateDTO) {
    return request.put<R<void>>('/production/operation-execution', data)
  },

  /**
   * 删除工序执行
   */
  remove(executionIds: number[]) {
    return request.delete<R<void>>(`/production/operation-execution/${executionIds.join(',')}`)
  },

  /**
   * 开始工序执行
   */
  start(executionId: number) {
    return request.put<R<void>>(`/production/operation-execution/${executionId}/start`)
  },

  /**
   * 暂停工序执行
   */
  pause(executionId: number) {
    return request.put<R<void>>(`/production/operation-execution/${executionId}/pause`)
  },

  /**
   * 完成工序执行
   */
  complete(executionId: number) {
    return request.put<R<void>>(`/production/operation-execution/${executionId}/complete`)
  },

  /**
   * 取消工序执行
   */
  cancel(executionId: number) {
    return request.put<R<void>>(`/production/operation-execution/${executionId}/cancel`)
  },

  /**
   * 根据工单ID查询工序执行列表
   */
  getByOrderId(orderId: number) {
    return request.get<R<OperationExecutionVO[]>>(
      `/production/operation-execution/order/${orderId}`
    )
  },

  /**
   * 根据工序ID查询工序执行列表
   */
  getByProcessId(processId: number) {
    return request.get<R<OperationExecutionVO[]>>(
      `/production/operation-execution/process/${processId}`
    )
  },

  /**
   * 获取工序执行统计
   */
  getStats(params?: OperationExecutionQuery) {
    return request.get<R<OperationExecutionStats>>('/production/operation-execution/stats', {
      params,
    })
  },

  /**
   * 导出工序执行数据
   */
  exportData(params: OperationExecutionQuery) {
    return request.get('/production/operation-execution/export', {
      params,
      responseType: 'blob',
    })
  },

  /**
   * 导入工序执行数据
   */
  importData(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<R<void>>('/production/operation-execution/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
