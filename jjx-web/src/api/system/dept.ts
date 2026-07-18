import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type { SysDept } from '@/types/system'

// 部门管理API
export const deptApi = {
  // 获取部门列表
  list(params: SysDept & { pageNum?: number; pageSize?: number }) {
    return request.get<R<SysDept[]>>('/system/dept/list', { params })
  },

  // 获取部门详情
  getInfo(deptId: number) {
    return request.get<R<SysDept>>(`/system/dept/${deptId}`)
  },

  // 获取部门下拉树列表
  treeselect(params: SysDept) {
    return request.get<R<SysDept[]>>('/system/dept/treeselect', { params })
  },

  // 新增部门
  add(data: SysDept) {
    return request.post<R<void>>('/system/dept', data)
  },

  // 修改部门
  edit(data: SysDept) {
    return request.put<R<void>>('/system/dept', data)
  },

  // 删除部门
  remove(deptId: number) {
    return request.delete<R<void>>(`/system/dept/${deptId}`)
  },
}
