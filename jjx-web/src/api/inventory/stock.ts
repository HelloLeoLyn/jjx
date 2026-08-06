import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type { StockQueryParams, StockVO, StockSummaryVO, StockAlertInfo, StockDashboardData } from '@/types/inventory/stock'

// 库存汇总API
export const stockApi = {
  // 分页查询库存汇总列表
  list(params: StockQueryParams) {
    return request.get<R<PageResult<StockVO>>>('/inventory/stock/list', {
      params,
    })
  },

  // 获取库存汇总
  summary(params?: StockQueryParams) {
    return request.get<R<StockSummaryVO>>('/inventory/stock/summary', {
      params,
    })
  },

  // 获取库存汇总详情
  getById(stockId: string) {
    return request.get<R<StockVO>>(`/inventory/stock/${stockId}`)
  },

  // 根据物料查询库存汇总
  getByMaterial(materialId: string) {
    return request.get<R<StockVO>>(`/inventory/stock/material/${materialId}`)
  },

  // 根据仓库查询库存汇总
  getByWarehouse(warehouseId: string) {
    return request.get<R<StockVO[]>>(`/inventory/stock/warehouse/${warehouseId}`)
  },

  // 获取库存预警信息
  getAlertInfo() {
    return request.get<R<StockAlertInfo>>('/inventory/stock/alert')
  },

  // 查询低库存物料
  getLowStock() {
    return request.get<R<StockVO[]>>('/inventory/stock/low-stock')
  },

  // 查询临期库存
  getExpiring() {
    return request.get<R<StockVO[]>>('/inventory/stock/expiring')
  },

  // 查询呆滞库存
  getObsolete() {
    return request.get<R<StockVO[]>>('/inventory/stock/obsolete')
  },

  // 获取库存仪表板数据
  getDashboard() {
    return request.get<R<StockDashboardData>>('/inventory/stock/dashboard')
  },

  // 校验物料并解析仓库库位（用于导入）
  check(data: {
    materialName: string
    specification?: string
    supplierName?: string
    locationDesc?: string
    warehouseId?: number
  }) {
    return request.post<R<StockCheckVO>>('/inventory/stock/check', data)
  },

  // 批量导入库存数据（JSON格式）
  batchImport(data: Record<string, unknown>[], autoCreateLocation = false) {
    return request.post<R<StockImportResultVO>>('/inventory/stock/batch-import', data, {
      params: { autoCreateLocation },
    })
  },

  // 下载库存导入模板（DEV-672：后端生成，不再用静态文件）
  downloadImportTemplate() {
    return request.get('/inventory/stock/importTemplate', {
      responseType: 'blob',
    })
  },
}

// 库存校验结果类型
export interface StockCheckVO {
  materialId?: number
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  warehouseId?: number
  warehouseName?: string
  locationCode?: string
  locationName?: string
}

// 库存导入结果类型
export interface StockImportResultVO {
  successCount: number
  failCount: number
  failDetails: StockImportFailDetail[]
}

// 导入失败详情
export interface StockImportFailDetail {
  rowIndex: number
  materialName: string
  reason: string
}
