import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type { SysLoginLog, SysLoginLogQuery } from '@/types/system'

// 登录日志管理API
export const loginLogApi = {
  // 获取登录日志列表
  list(params: SysLoginLogQuery) {
    return request.get<R<PageResult<SysLoginLog>>>('/logs/login', { params })
  },

  // 获取登录日志详情
  getInfo(loginId: number) {
    return request.get<R<SysLoginLog>>(`/logs/login/${loginId}`)
  },
}
