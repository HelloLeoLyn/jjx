import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type { SalesLogQueryDTO, SalesLogVO } from '@/types/sales/log'

/**
 * 销售订单操作日志API
 */
export const salesLogApi = {
  // ==================== 基础查询 ====================

  /** 分页查询操作日志 */
  getLogs(params: SalesLogQueryDTO) {
    return request.get<R<PageResult<SalesLogVO>>>('/sales/logs', { params })
  },

  /** 根据日志ID查询 */
  getLogById(logId: number) {
    return request.get<R<SalesLogVO>>(`/sales/logs/${logId}`)
  },

  /** 根据订单ID查询日志列表 */
  getLogsByOrderId(orderId: number) {
    return request.get<R<SalesLogVO[]>>(`/sales/logs/order/${orderId}`)
  },

  /** 根据订单号查询日志列表 */
  getLogsByOrderNo(orderNo: string) {
    return request.get<R<SalesLogVO[]>>(`/sales/logs/orderNo/${orderNo}`)
  },

  /** 查询订单的最新操作日志 */
  getLatestLogByOrderId(orderId: number) {
    return request.get<R<SalesLogVO>>(`/sales/logs/order/${orderId}/latest`)
  },

  // ==================== 条件查询 ====================

  /** 根据操作类型查询日志 */
  getLogsByOperationType(operationType: string, params?: { pageNum?: number; pageSize?: number }) {
    return request.get<R<PageResult<SalesLogVO>>>(`/sales/logs/type/${operationType}`, { params })
  },

  /** 根据操作人查询日志 */
  getLogsByOperator(operatorId: number, params?: { pageNum?: number; pageSize?: number }) {
    return request.get<R<PageResult<SalesLogVO>>>(`/sales/logs/operator/${operatorId}`, { params })
  },

  // ==================== 删除操作 ====================

  /** 删除指定订单的所有日志 */
  deleteLogsByOrderId(orderId: number) {
    return request.delete<R<void>>(`/sales/logs/order/${orderId}`)
  },

  /** 删除指定日志 */
  deleteLog(logId: number) {
    return request.delete<R<void>>(`/sales/logs/${logId}`)
  },

  /** 批量删除日志 */
  batchDeleteLogs(logIds: number[]) {
    return request.delete<R<void>>('/sales/logs/batch', { data: { logIds } })
  },

  // ==================== 导出操作 ====================

  /** 导出操作日志 */
  exportLogs(params: SalesLogQueryDTO) {
    return request.get('/sales/logs/export', {
      params,
      responseType: 'blob',
    })
  },

  // ==================== 统计分析 ====================

  /** 获取操作类型统计 */
  getOperationTypeStats(startTime?: string, endTime?: string) {
    return request.get<
      R<Array<{ operationType: string; operationTypeName: string; count: number }>>
    >('/sales/logs/stats/operation-type', {
      params: { startTime, endTime },
    })
  },

  /** 获取操作人统计 */
  getOperatorStats(startTime?: string, endTime?: string) {
    return request.get<R<Array<{ operatorId: number; operatorName: string; count: number }>>>(
      '/sales/logs/stats/operator',
      {
        params: { startTime, endTime },
      }
    )
  },

  // ==================== 枚举数据 ====================

  /** 获取操作类型枚举列表 */
  getOperationTypeEnums() {
    return request.get<R<Array<{ code: string; name: string }>>>(
      '/sales/logs/enums/operation-types'
    )
  },

  /** 获取操作结果枚举列表 */
  getOperationResultEnums() {
    return request.get<R<Array<{ code: string; name: string }>>>(
      '/sales/logs/enums/operation-results'
    )
  },
}
