import request from '@/utils/request'
import type { R } from '@/types'
import type { SysMenu } from '@/types/system'
import type { AsyncRouteConfig, SysMenuQuery } from '@/types/system'

// 菜单管理API
export const menuApi = {
  // 获取菜单列表
  list(params?: SysMenu) {
    return request.get<R<SysMenu[]>>('/system/menu/list', { params })
  },

  // 获取菜单详情
  getInfo(menuId: number) {
    return request.get<R<SysMenu>>(`/system/menu/${menuId}`)
  },

  // 获取菜单下拉树列表
  treeselect(params?: SysMenuQuery) {
    return request.get<R<SysMenu[]>>('/system/menu/treeselect', { params })
  },

  // 新增菜单
  add(data: SysMenu) {
    return request.post<R<void>>('/system/menu', data)
  },

  // 修改菜单
  edit(data: SysMenu) {
    return request.put<R<void>>('/system/menu', data)
  },

  // 删除菜单
  remove(menuIds: number[]) {
    return request.delete<R<void>>(`/system/menu/${menuIds.join(',')}`)
  },

  // 获取路由信息
  getRouters() {
    return request.get<R<AsyncRouteConfig[]>>('/system/menu/getRouters')
  },

  // 获取用户权限
  getPermissions() {
    return request.get<R<string[]>>('/system/menu/permissions')
  },

  // 获取菜单的角色权限
  getAuthRoles(menuId: number) {
    return request.get<R<number[]>>(`/system/menu/authRole/${menuId}`)
  },

  // 为菜单分配角色
  addAuthRoles(menuId: number, roleIds: number[]) {
    return request.put<R<void>>('/system/menu/authRole/selectAll', {
      menuId,
      roleIds,
    })
  },
  // 为菜单分配角色
  removeMenuRole(menuId: number, roleId: number) {
    return request.delete<R<void>>('/system/menu/role', {
      params: {
        menuId,
        roleId,
      },
    })
  },
}
