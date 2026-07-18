import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type { StockItemQueryParams, StockItemVO } from '@/types/inventory/stock'

// 库存批次明细API
export const stockItemApi = {
  // 分页查询库存批次明细
  list(params: StockItemQueryParams) {
    return request.get<R<PageResult<StockItemVO>>>('/inventory/stock-item/list', {
      params,
    })
  },

  // 获取库存批次明细详情
  getById(itemId: string) {
    return request.get<R<StockItemVO>>(`/inventory/stock-item/${itemId}`)
  },

  // 根据物料ID查询批次明细
  getByMaterial(materialId: string) {
    return request.get<R<StockItemVO[]>>(`/inventory/stock-item/material/${materialId}`)
  },

  // 根据物料ID和仓库ID查询批次明细
  getByMaterialAndWarehouse(materialId: string, warehouseId: string) {
    return request.get<R<StockItemVO[]>>(
      `/inventory/stock-item/material/${materialId}/warehouse/${warehouseId}`
    )
  },
}
