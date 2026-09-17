import request from '@/utils/request'
import type { R } from '@/types'

/** 质量管理（检验批/不良台账/抽样方案）—— dev-20260917-009/010/012 */

export interface QualityLotItem {
  itemId?: number
  lotId?: number
  checkItem: string
  standard?: string
  inspectionMethod?: string
  equipment?: string
  sampleValues?: string
  actualValue?: string
  crQuantity?: number
  maQuantity?: number
  miQuantity?: number
  result?: string
  remark?: string
  sortOrder?: number
}

export interface QualityLot {
  lotId: number
  lotNo: string
  lotType: string
  sourceType?: string
  sourceId?: number
  sourceItemId?: number
  orderId?: number
  executionId?: number
  materialId?: number
  materialCode?: string
  materialName?: string
  productId?: number
  productCode?: string
  productName?: string
  batchNo?: string
  lotQuantity?: number
  inspectedQuantity?: number
  passQuantity?: number
  failQuantity?: number
  storedQuantity?: number
  disposedQuantity?: number
  sampleQuantity?: number
  acceptNumber?: number
  rejectNumber?: number
  result?: string
  status?: string
  parentLotId?: number
  version?: number
  inspector?: string
  inspectTime?: string
  remark?: string
}

export interface QualityNcr {
  ncrId: number
  ncrNo: string
  lotId: number
  lotType: string
  orderId?: number
  executionId?: number
  materialCode?: string
  materialName?: string
  productCode?: string
  productName?: string
  batchNo?: string
  defectQuantity?: number
  crQuantity?: number
  maQuantity?: number
  miQuantity?: number
  defectReason?: string
  status: string
  disposedQuantity?: number
  inspector?: string
}

export interface QualityNcrAction {
  actionId: number
  ncrId: number
  actionType: string
  quantity: number
  status: string
  customerConfirmed?: number
  approvedBy?: string
  resultRemark?: string
  reworkExecutionId?: number
}

export const qualityLotApi = {
  page(params: Record<string, unknown>) {
    return request.get<R<{ records: QualityLot[]; total: number }>>('/quality/lot/page', { params })
  },
  detail(lotId: number) {
    return request.get<R<QualityLot>>(`/quality/lot/${lotId}`)
  },
  items(lotId: number) {
    return request.get<R<QualityLotItem[]>>(`/quality/lot/${lotId}/items`)
  },
  saveItems(lotId: number, items: QualityLotItem[]) {
    return request.put<R<boolean>>(`/quality/lot/${lotId}/items`, items)
  },
  judge(lotId: number, data: Record<string, unknown>) {
    return request.post<R<QualityLot>>(`/quality/lot/${lotId}/judge`, data)
  },
  reinspect(lotId: number) {
    return request.post<R<QualityLot>>(`/quality/lot/${lotId}/reinspect`)
  },
  syncFinish(orderId: number, reason?: string) {
    return request.post<R<number>>(`/quality/lot/order/${orderId}/sync-finish`, null, {
      params: { reason },
    })
  },
  create(data: Record<string, unknown>) {
    return request.post<R<QualityLot>>('/quality/lot', data)
  },
}

export const qualityNcrApi = {
  page(params: Record<string, unknown>) {
    return request.get<R<{ records: QualityNcr[]; total: number }>>('/quality/ncr/page', { params })
  },
  actions(ncrId: number) {
    return request.get<R<QualityNcrAction[]>>(`/quality/ncr/${ncrId}/actions`)
  },
  dispose(ncrId: number, data: Record<string, unknown>) {
    return request.post<R<QualityNcrAction>>(`/quality/ncr/${ncrId}/dispose`, data)
  },
  completeAction(actionId: number, params?: Record<string, unknown>) {
    return request.post<R<QualityNcrAction>>(`/quality/ncr/action/${actionId}/complete`, null, { params })
  },
}

export const qualitySamplingApi = {
  list() {
    return request.get<R<Record<string, unknown>[]>>('/quality/sampling-plan/list')
  },
  save(data: Record<string, unknown>) {
    return request.post<R<Record<string, unknown>>>('/quality/sampling-plan', data)
  },
  remove(planId: number) {
    return request.delete<R<boolean>>(`/quality/sampling-plan/${planId}`)
  },
  match(lotType: string, quantity: number) {
    return request.get<R<Record<string, unknown>>>('/quality/sampling-plan/match', {
      params: { lotType, quantity },
    })
  },
}
