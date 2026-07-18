import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type { SysRole, SysUser, SysUserVO, RoleUserQueryDTO } from '@/types/system'

// 角色管理API
export const roleApi = {
  // 获取角色列表
  list(params: Partial<SysRole> & { pageNum?: number; pageSize?: number }) {
    return request.get<R<SysRole[]>>('/system/role/list', { params })
  },
  // 获取角色列表
  page(params: Partial<SysRole> & { pageNum?: number; pageSize?: number }) {
    return request.get<R<PageResult<SysRole>>>('/system/role/page', { params })
  },
  // 获取角色详情
  getInfo(roleId: number) {
    return request.get<R<SysRole>>(`/system/role/${roleId}`)
  },

  // 新增角色
  add(data: SysRole) {
    return request.post<R<void>>('/system/role', data)
  },

  // 修改角色
  edit(data: SysRole) {
    return request.put<R<void>>('/system/role', data)
  },

  // 删除角色
  remove(roleIds: number[]) {
    return request.delete<R<void>>(`/system/role/${roleIds.join(',')}`)
  },

  // 修改数据权限
  dataScope(data: SysRole) {
    return request.put<R<void>>('/system/role/dataScope', data)
  },

  // 获取数据权限配置
  getDataScope(roleId: number) {
    return request.get<R<{ roleId: number; dataScope: number; deptIds: number[] }>>(
      `/system/role/dataScope/${roleId}`
    )
  },

  // 更新数据权限配置
  updateDataScope(data: { roleId: number; dataScope: number; deptIds: number[] }) {
    return request.put<R<void>>('/system/role/dataScope', data)
  },

  // 状态修改
  changeStatus(data: { roleId: number; status: string }) {
    return request.put<R<void>>('/system/role/changeStatus', data)
  },

  // 获取角色选择框列表
  optionselect() {
    return request.get<R<SysRole[]>>('/system/role/optionselect')
  },

  // 查询已分配用户角色列表
  allocatedList(params: RoleUserQueryDTO) {
    return request.get<R<PageResult<SysUserVO>>>('/system/role/authUser/allocatedList', { params })
  },

  // 查询未分配用户角色列表
  unallocatedList(params: RoleUserQueryDTO) {
    return request.get<R<PageResult<SysUserVO>>>('/system/role/authUser/unallocatedList', {
      params,
    })
  },

  // 批量授权用户
  addAuthUser(data: { roleId: number; userIds: number[] }) {
    return request.put<R<void>>('/system/role/authUser/selectAll', null, {
      params: { roleId: data.roleId, userIds: data.userIds.join(',') },
    })
  },

  // 取消授权用户
  cancelAuthUser(data: { roleId: number; userIds: number[] }) {
    return request.put<R<void>>('/system/role/authUser/cancelAll', null, {
      params: { roleId: data.roleId, userIds: data.userIds.join(',') },
    })
  },

  // 批量取消授权用户
  cancelAuthUserAll(roleId: number, userIds: number[]) {
    return request.put<R<void>>('/system/role/authUser/cancelAll', null, {
      params: { roleId, userIds },
    })
  },

  // 批量选择用户授权
  selectAuthUserAll(roleId: number, userIds: number[]) {
    return request.put<R<void>>('/system/role/authUser/selectAll', null, {
      params: { roleId, userIds },
    })
  },

  // 获取角色菜单权限
  getAuthMenu(roleId: number) {
    return request.get<R<number[]>>(`/system/role/authMenu/${roleId}`)
  },

  // 批量授权菜单
  addAuthMenu(data: { roleId: number; menuIds: number[] }) {
    const params: any = { roleId: data.roleId }
    if (data.menuIds && data.menuIds.length > 0) {
      params.menuIds = data.menuIds.join(',')
    }
    return request.put<R<void>>('/system/role/authMenu/selectAll', null, {
      params,
    })
  },
}
