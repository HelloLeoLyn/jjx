import request from '@/utils/request'
import type { PageResult, R } from '@/types'

// 盘点单查询参数
export interface StocktakeQueryParams {
  current?: number
  pageSize?: number
  stocktakeNo?: string
  stocktakeType?: string
  warehouseId?: string
  orderStatus?: string
  approveStatus?: string
}

// 盘点明细
export interface StocktakeItemVO {
  itemId?: string
  materialId?: string
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
  batchNo?: string
  locationId?: string
  locationName?: string
  systemQuantity?: number
  actualQuantity?: number
  diffQuantity?: number
  unitCost?: number
  diffAmount?: number
  adjustStatus?: number
  reason?: string
  remark?: string
}

// 盘点单VO
export interface StocktakeVO {
  stocktakeId: string
  stocktakeNo?: string
  stocktakeType?: string
  stocktakeTypeName?: string
  warehouseId?: string
  warehouseName?: string
  materialCount?: number
  locationIds?: string
  materialIds?: string
  planStartTime?: string
  planEndTime?: string
  actualStartTime?: string
  actualEndTime?: string
  stocktakerName?: string
  supervisorName?: string
  totalSystemQuantity?: number
  totalActualQuantity?: number
  totalDiffQuantity?: number
  totalDiffAmount?: number
  orderStatus?: number
  orderStatusName?: string
  approveStatus?: number
  approveStatusName?: string
  approverName?: string
  approveTime?: string
  remark?: string
  createBy?: string
  createByName?: string
  createTime?: string
  items?: StocktakeItemVO[]
}

// 盘点单API
export const stocktakeApi = {
  // 分页查询
  list(params: StocktakeQueryParams) {
    return request.get<R<PageResult<StocktakeVO>>>('/inventory/stocktake/list', { params })
  },

  // 详情
  getById(stocktakeId: string) {
    return request.get<R<StocktakeVO>>(`/inventory/stocktake/${stocktakeId}`)
  },

  // 新建
  create(data: Record<string, unknown>) {
    return request.post<R<string>>('/inventory/stocktake/create', data)
  },

  // 开始盘点
  start(stocktakeId: string) {
    return request.post<R<boolean>>(`/inventory/stocktake/start/${stocktakeId}`)
  },

  // 录入实盘数
  inputData(stocktakeId: string, items: Record<string, unknown>[]) {
    return request.post<R<boolean>>(`/inventory/stocktake/input-data/${stocktakeId}`, items)
  },

  // 计算差异
  calculateDiff(stocktakeId: string) {
    return request.post<R<Record<string, unknown>>>(`/inventory/stocktake/calculate-diff/${stocktakeId}`)
  },

  // 确认结果
  confirmResult(stocktakeId: string) {
    return request.post<R<boolean>>(`/inventory/stocktake/confirm-result/${stocktakeId}`)
  },

  // 处理差异（生成出入库调整单）
  processDiff(stocktakeId: string, remark?: string) {
    return request.post<R<boolean>>(`/inventory/stocktake/process-diff/${stocktakeId}`, null, {
      params: { remark },
    })
  },

  // 关闭
  close(stocktakeId: string) {
    return request.post<R<boolean>>(`/inventory/stocktake/close/${stocktakeId}`)
  },

  // 提交审批
  submitApprove(stocktakeId: string) {
    return request.post<R<boolean>>(`/inventory/stocktake/submit-approve/${stocktakeId}`)
  },

  // 审批
  approve(stocktakeId: string, remark?: string) {
    return request.post<R<boolean>>(`/inventory/stocktake/approve/${stocktakeId}`, null, {
      params: { remark },
    })
  },
}
