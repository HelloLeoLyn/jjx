import request from '@/utils/request'
import type { R } from '@/types'
import type { LoginForm, LoginResponse } from '@/types/system'
// 用户管理API
export const authApi = {
  // 登录
  login(data: LoginForm) {
    return request.post<R<LoginResponse>>('/sessions/auth', data)
  },

  // 登出
  logout() {
    return request.delete<R<void>>('/sessions/current/out')
  },

  // 刷新token
  refreshToken() {
    return request.post<R<{ token: string }>>('/sessions/current/token')
  },

  // 获取用户信息
  getUserInfo() {
    return request.get<R<LoginResponse>>('/sessions/current')
  },

  // 获取用户权限
  getPermission() {
    return request.get<R<string[]>>('/sessions/permission')
  },
}
