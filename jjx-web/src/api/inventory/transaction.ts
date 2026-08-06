import request from '@/utils/request'
import type { R } from '@/types'

export interface TransactionVO {
  transactionId: number
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

// DEV-661：按单据号查库存流水（出入库详情展示）
export function getTransactionsByDocNo(docNo: string) {
  return request.get<R<TransactionVO[]>>('/inventory/transaction/by-doc-no', {
    params: { docNo },
  })
}
