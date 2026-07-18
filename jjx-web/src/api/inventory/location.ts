import request from '@/utils/request'
import type {
  InventoryStorageLocation,
  InventoryStorageLocationQueryParams,
  StorageLocationSaveDTO,
  StorageLocationUpdateDTO,
} from '@/types/inventory/location'
import type { PageResult, R } from '@/types'

// 库位管理API
export const locationApi = {
  // 获取库位列表
  list(params: InventoryStorageLocationQueryParams) {
    return request.get<R<PageResult<InventoryStorageLocation>>>(
      '/inventory/storage-location/page',
      { params }
    )
  },

  // 获取库位详情
  getInfo(id: number) {
    return request.get<R<InventoryStorageLocation>>(`/inventory/storage-location/${id}`)
  },

  // 新增库位
  add(data: StorageLocationSaveDTO) {
    return request.post<R<boolean>>('/inventory/storage-location', data)
  },

  // 修改库位
  update(data: StorageLocationUpdateDTO) {
    return request.put<R<boolean>>('/inventory/storage-location', data)
  },

  // 删除库位
  delete(id: number) {
    return request.delete<R<boolean>>(`/inventory/storage-location/${id}`)
  },

  // 更新库位状态
  updateStatus(id: number, status: string) {
    return request.put<R<boolean>>(`/inventory/storage-location/${id}/status`, null, {
      params: { status },
    })
  },

  // 批量更新状态
  batchUpdateStatus(ids: number[], status: string) {
    return request.put<R<boolean>>('/inventory/storage-location/batch-status', null, {
      params: { ids, status },
    })
  },

  // 获取仓库下的库位列表
  getByWarehouse(warehouseId: number) {
    return request.get<R<InventoryStorageLocation[]>>(
      `/inventory/storage-location/warehouse/${warehouseId}`
    )
  },

  // 导出库位列表
  export(params: InventoryStorageLocationQueryParams) {
    return request.get('/inventory/storage-location/export', {
      params,
      responseType: 'blob',
    })
  },

  // 导入库位数据
  importLocation(data: Record<string, unknown>[], warehouseId: number) {
    return request.post<R<string>>('/inventory/storage-location/import', data, {
      params: { warehouseId },
    })
  },

  // 下载库位导入模板
  downloadImportTemplate() {
    return request.get('/inventory/storage-location/importTemplate', {
      responseType: 'blob',
    })
  },
}
