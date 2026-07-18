import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type { SysExceptionLog, SysExceptionLogQuery } from '@/types/system'

// 异常日志管理API
export const exceptionLogApi = {
  // 获取异常日志列表
  list(params: SysExceptionLogQuery) {
    return request.get<R<PageResult<SysExceptionLog>>>('/logs/error', { params })
  },

  // 获取异常日志详情
  getInfo(exceptionId: number) {
    return request.get<R<SysExceptionLog>>(`/logs/error/${exceptionId}`)
  },
}
