import request from '@/utils/request'
import type { PageResult, R } from '@/types'

export interface TransactionVO {
  transactionId: number
  inventoryItemId: number
  materialId: number
  materialCode: string
  materialName: string
  warehouseId?: number
  warehouseName?: string
  locationId?: number
  locationName?: string
  transactionType: string
  transactionTypeName?: string
  sourceType?: string
  sourceTypeName?: string
  sourceId?: number
  sourceNo?: string
  batchNo?: string
  quantity: number
  beforeQuantity?: number
  afterQuantity?: number
  unitCost?: number
  amount?: number
  transactionTime: string
  operatorId?: number
  operatorName?: string
  createTime?: string
  remark?: string
}

export interface TransactionQueryParams {
  current: number
  pageSize: number
  inventoryItemId?: string | number
  materialCode?: string
  materialName?: string
  warehouseId?: number
  transactionType?: string
  sourceType?: string
  sourceNo?: string
  batchNo?: string
  transactionTimeStart?: string
  transactionTimeEnd?: string
}

/** 收发明细分页（流水唯一真源）。 */
export function getTransactionPage(params: TransactionQueryParams) {
  const { pageSize, ...rest } = params
  return request.get<R<PageResult<TransactionVO>>>('/inventory/transaction/list', {
    params: { ...rest, size: pageSize },
  })
}

// DEV-661：按单据号查库存流水（出入库详情展示）
export function getTransactionsByDocNo(docNo: string) {
  return request.get<R<TransactionVO[]>>('/inventory/transaction/by-doc-no', {
    params: { docNo },
  })
}

/**
 * dev-20260923-017：按批次查库存流水（批次明细「变动流水」抽屉）
 * 返回按时间正序，带 变动前/变动后，可直接展示余额演变
 */
export function getTransactionsByBatch(inventoryItemId: string | number, batchNo: string) {
  return request.get<R<TransactionVO[]>>('/inventory/transaction/by-batch', {
    params: { inventoryItemId, batchNo },
  })
}
