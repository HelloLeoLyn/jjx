import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type { SysOperationLog, SysOperationLogQuery } from '@/types/system'

// 操作日志管理API
export const operationLogApi = {
  // 获取操作日志列表
  getLogs(params: SysOperationLogQuery) {
    return request.get<R<PageResult<SysOperationLog>>>('/logs/oper', { params })
  },

  // 获取操作日志详情
  getInfo(logId: number) {
    return request.get<R<SysOperationLog>>(`/logs/operation-log/${logId}`)
  },

  // 删除操作日志
  remove(logIds: number[]) {
    return request.delete<R<void>>(`/logs/operation-log/${logIds.join(',')}`)
  },

  // 清空操作日志
  clean() {
    return request.delete<R<void>>('/logs/operation-log/clean')
  },
}
