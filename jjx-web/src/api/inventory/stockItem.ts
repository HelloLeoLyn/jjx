import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type { StockItemQueryParams, StockItemVO, BatchFlowSummaryVO } from '@/types/inventory/stock'

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

  /**
   * 批次收发存汇总（dev-20260923-017）：同一库存物品下每批次的 入库合计/出库合计/结存
   * 数据源 = 库存流水（唯一真源），不是批次表新增字段
   */
  batchSummary(inventoryItemId: string) {
    return request.get<R<BatchFlowSummaryVO[]>>('/inventory/stock-item/batch-summary', {
      params: { inventoryItemId },
    })
  },
}
