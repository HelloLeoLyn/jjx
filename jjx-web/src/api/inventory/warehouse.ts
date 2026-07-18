import request from '@/utils/request'
import type {
  InventoryWarehouse,
  InventoryWarehouseQueryParams,
  WarehouseSaveDTO,
  WarehouseUpdateDTO,
} from '@/types/inventory/warehouse'
import type { PageResult, R } from '@/types'

// 仓库管理API
export const warehouseApi = {
  // 获取仓库列表
  list(params: InventoryWarehouseQueryParams) {
    return request.get<R<PageResult<InventoryWarehouse>>>('/inventory/warehouse/page', { params })
  },

  // 获取仓库详情
  getInfo(id: number) {
    return request.get<R<InventoryWarehouse>>(`/inventory/warehouse/${id}`)
  },

  // 新增仓库
  add(data: WarehouseSaveDTO) {
    return request.post<R<boolean>>('/inventory/warehouse', data)
  },

  // 修改仓库
  update(data: WarehouseUpdateDTO) {
    return request.put<R<boolean>>('/inventory/warehouse', data)
  },

  // 删除仓库
  delete(ids: number[]) {
    return request.delete<R<boolean>>('/inventory/warehouse', { data: ids })
  },

  // 更新仓库状态
  updateStatus(id: number, status: string) {
    return request.put<R<boolean>>(`/inventory/warehouse/${id}/status`, null, {
      params: { status },
    })
  },

  // 批量更新状态
  batchUpdateStatus(ids: number[], status: string) {
    return request.put<R<boolean>>('/inventory/warehouse/batch-status', null, {
      params: { ids, status },
    })
  },

  // 检查仓库编码是否重复
  checkCode(warehouseCode: string) {
    return request.get<R<boolean>>('/inventory/warehouse/check-code', {
      params: { warehouseCode },
    })
  },

  // 导出仓库
  export(params: InventoryWarehouseQueryParams) {
    return request.get('/inventory/warehouse/export', {
      params,
      responseType: 'blob',
    })
  },

  // 获取仓库下拉选项
  getOptions(keyword?: string) {
    return request.get<R<InventoryWarehouse[]>>('/inventory/warehouse/options', {
      params: { keyword },
    })
  },

  // 获取所有启用的仓库
  getAllEnabled() {
    return request.get<R<InventoryWarehouse[]>>('/inventory/warehouse/enabled')
  },

  // 根据仓库类型获取仓库
  getByType(warehouseType: string) {
    return request.get<R<InventoryWarehouse[]>>('/inventory/warehouse/by-type', {
      params: { warehouseType },
    })
  },
}
