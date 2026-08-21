import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type {
  SysUser,
  SysUserRole,
  SysUserRoleQuery,
  SysUserDTO,
  SecurityUser,
  LoginForm,
  LoginResponse,
} from '@/types/system'
// 用户管理API
export const userApi = {
  // 获取用户列表
  list(params: SysUser & { pageNum?: number; pageSize?: number }) {
    return request.get<R<PageResult<SysUser>>>('/system/user/list', { params })
  },

  // 导出用户列表Excel（DEV-1014）
  exportUsers(params: SysUser & { pageNum?: number; pageSize?: number }) {
    return request.get('/system/user/export', { params, responseType: 'blob' })
  },

  // 获取销售负责人列表（2026-08-11 按 role_key 前缀 sales 匹配）
  salesPersons() {
    return request.get<R<SysUser[]>>('/system/user/sales-persons')
  },

  // 获取用户详情
  getInfo(userId: number) {
    return request.get<R<SysUser>>(`/system/user/${userId}`)
  },

  // 新增用户
  add(data: SysUserDTO) {
    return request.post<R<void>>('/system/user', data)
  },

  // 修改用户
  edit(data: SysUserDTO) {
    return request.put<R<void>>('/system/user', data)
  },

  // 删除用户
  remove(userIds: number[]) {
    return request.delete<R<void>>(`/system/user/${userIds.join(',')}`)
  },

  // 重置密码
  resetPwd(data: SecurityUser) {
    return request.put<R<void>>('/system/user/resetPwd', data)
  },

  // 状态修改
  changeStatus(data: { userId: number; status: string }) {
    return request.put<R<void>>('/system/user/changeStatus', data)
  },

  // 获取用户授权角色
  selectUserRoles(params: SysUserRoleQuery) {
    return request.get<R<PageResult<SysUserRole>>>(`/system/user/role`, {
      params,
    })
  },

  // 用户授权角色
  authRole(userId: number, roleIds: number[]) {
    return request.put<R<void>>('/system/user/authRole', null, {
      params: { userId, roleIds },
    })
  },

  // 获取当前用户信息
  getCurrentInfo() {
    return request.get<R<SysUser>>('/system/user/current')
  },

  // 修改用户个人信息
  profile(data: SysUser) {
    return request.put<R<void>>('/system/user/profile', data)
  },

  // 修改密码
  updatePwd(data: { oldPassword: string; newPassword: string }) {
    return request.put<R<void>>('/system/user/profile/updatePwd', data)
  },

  // 修改头像
  avatar(avatar: string) {
    return request.post<R<void>>('/system/user/profile/avatar', null, {
      params: { avatar },
    })
  },
}
