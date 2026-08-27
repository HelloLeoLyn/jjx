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

  /** 全部工序视角；后端仅允许生产全局范围用户访问。 */
  globalList(params: OperationExecutionQuery) {
    return request.get<R<OperationExecutionVO[]>>(
      '/production/operation-execution/global-list', { params }
    )
  },

  /** 分页查询工序执行列表（派工管理树形主列表第一层，服务端分页） */
  page(params: OperationExecutionQuery) {
    return request.get<R<PageResult<OperationExecutionVO>>>(
      '/production/operation-execution/page',
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

  /** 工序首检/巡检（DEV-371） */
  qualityCheck(executionId: number, checkType: string, checkResult: string, checkItems?: string, remark?: string) {
    return request.put<R<void>>(`/production/operation-execution/${executionId}/quality-check`, null, {
      params: { checkType, checkResult, checkItems, remark },
    })
  },

  /**
   * 完成工序执行
   */
  complete(executionId: number) {
    return request.put<R<boolean>>(`/production/operation-execution/${executionId}/complete`)
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
    return request.get<R<OperationExecutionStats>>('/production/operation-execution/statistics', {
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
