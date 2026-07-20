import request from '@/utils/request'
import type {
  MaterialCategoryQueryParams,
  MaterialCategoryFormData,
  MaterialCategoryListResult,
} from '@/types/inventory/material'
import type { PageResult, R } from '@/types'
// ==================== 材料分类API ====================

export const materialCategoryApi = {
  // 获取材料分类列表
  list(params: MaterialCategoryQueryParams) {
    return request.get<MaterialCategoryListResult>('/inventory/material-category/list', {
      params,
    })
  },

  // 获取材料分类树
  getTree(params: MaterialCategoryQueryParams) {
    return request.get<R<MaterialCategoryListResult[]>>('/inventory/material-category/tree', {
      params,
    })
  },

  // 获取材料分类详情
  getInfo(categoryId: number) {
    return request.get<R<MaterialCategoryFormData>>(`/inventory/material-category/${categoryId}`)
  },

  // 新增材料分类
  add(data: MaterialCategoryFormData) {
    return request.post<R<boolean>>('/inventory/material-category', data)
  },

  // 修改材料分类
  update(data: MaterialCategoryFormData) {
    return request.put<R<boolean>>('/inventory/material-category', data)
  },

  // 删除材料分类
  delete(categoryId: number) {
    return request.delete<R<boolean>>(`/inventory/material-category/${categoryId}`)
  },

  // 更新分类状态
  updateStatus(categoryId: number, status: string) {
    return request.put<R<boolean>>(`/inventory/material-category/${categoryId}/status`, null, {
      params: { status },
    })
  },
}
