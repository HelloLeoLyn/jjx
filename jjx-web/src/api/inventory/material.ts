import request from '@/utils/request'
import type {
  InventoryMaterial,
  InventoryMaterialQueryParams,
  MaterialSaveDTO,
  MaterialUpdateDTO,
  MaterialQueryDTO,
} from '@/types/inventory/material'
import type { PageResult, R } from '@/types'

// 物料管理API
export const materialApi = {
  // 获取物料列表
  list(params: MaterialQueryDTO) {
    return request.get<R<InventoryMaterial[]>>('/inventory/material/list', { params })
  },

  // 获取物料列表
  page(params: InventoryMaterialQueryParams) {
    return request.get<R<PageResult<InventoryMaterial>>>('/inventory/material/page', { params })
  },

  // 搜索物料（简化版，用于BOM编辑器的自动完成）
  search(params: MaterialQueryDTO) {
    return request.get<R<PageResult<InventoryMaterial>>>(`/inventory/material/search`, { params })
  },

  // 根据物料编码获取物料
  getByCode(materialCode: string) {
    const queryParams: {
      current: number
      pageSize: number
      materialCode: string
    } = {
      current: 1,
      pageSize: 1,
      materialCode: materialCode,
    }
    return request
      .get<R<PageResult<InventoryMaterial>>>('/inventory/material/page', { params: queryParams })
      .then((res) => {
        // 返回第一个匹配的物料
        if (res.data?.records && res.data.records.length > 0) {
          return {
            code: 200,
            msg: 'success',
            data: res.data.records[0],
          } as R<InventoryMaterial>
        } else {
          return {
            code: 404,
            msg: '物料不存在',
            data: null,
          } as R<InventoryMaterial>
        }
      })
  },

  // 获取物料详情
  getInfo(id: string) {
    return request.get<R<InventoryMaterial>>(`/inventory/material/${id}`)
  },

  // 新增物料
  add(data: MaterialSaveDTO) {
    return request.post<R<boolean>>('/inventory/material', data)
  },

  // 修改物料
  update(data: MaterialUpdateDTO) {
    return request.put<R<boolean>>('/inventory/material', data)
  },

  // 删除物料
  delete(ids: string[]) {
    return request.delete<R<boolean>>('/inventory/material', { data: ids })
  },

  // 更新物料状态
  updateStatus(id: string, status: string) {
    return request.put<R<boolean>>(`/inventory/material/${id}/status`, null, {
      params: { status },
    })
  },

  // 批量更新状态
  batchUpdateStatus(ids: string[], status: string) {
    return request.put<R<boolean>>('/inventory/material/batch-status', null, {
      params: { ids, status },
    })
  },

  // 检查物料编码是否重复
  checkCode(materialCode: string) {
    return request.get<R<boolean>>('/inventory/material/check-code', {
      params: { materialCode },
    })
  },

  // 获取低库存预警物料
  getLowStock() {
    return request.get<R<InventoryMaterial[]>>('/inventory/material/low-stock')
  },

  // 获取物料下拉选项
  getOptions(keyword?: string) {
    return request.get<R<InventoryMaterial[]>>('/inventory/material/options', {
      params: { keyword },
    })
  },

  // 导出物料
  export(params: InventoryMaterialQueryParams) {
    return request.get('/inventory/material/export', {
      params,
      responseType: 'blob',
    })
  },

  // 导入物料
  importExcel(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<string>('/inventory/material/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  // 下载导入模板
  downloadImportTemplate() {
    return request.post('/inventory/material/importTemplate', null, {
      responseType: 'blob',
    })
  },

  // 生成物料编码
  generateCode() {
    return request.get<R<string>>('/inventory/material/code')
  },
}
