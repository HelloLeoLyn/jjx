import request from '@/utils/request'
import type { R } from '@/types'
import { InspectionResult, InspectionResultEnum, InspectionTypeEnum } from '@/enums/quality'

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

/**
 * 判定护栏（dev-20260923-021 一期）：可判合格上界与预填建议
 * guardAvailable=false 表示护栏降级（查询异常），前端应退回原行为、不阻塞
 */
export interface JudgementGuardVO {
  lotId: number
  lotNo?: string
  lotQuantity: number
  /** 链上已报废且未回收量（SCRAP 且 DONE） */
  scrappedQuantity: number
  /** 链上让步接收但客户未确认量（CONCESSION 且 DONE 且未确认） */
  concessionPendingQuantity: number
  /** 可判合格上限 */
  upperBound: number
  suggestedPass: number
  suggestedFail: number
  needWarning: boolean
  message?: string
  guardAvailable: boolean
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
  reviewStatus?: string
  reviewerId?: number
  reviewerName?: string
  reviewTime?: string
  reviewRemark?: string
  defectReason?: string
  parentLotId?: number
  version?: number
  /** dev-20260922-012（G5）：复检来源批号（列表接口补充的非持久化字段） */
  parentLotNo?: string
  /** dev-20260922-012（G5）：本批已有后继复检版本（已被取代，不能再录入/判定） */
  superseded?: boolean
  inspector?: string
  inspectTime?: string
  remark?: string
  createTime?: string
}

/**
 * 旧生产质检页面迁移期使用的展示模型。
 * 数据源统一为 quality_lot，但保留页面现有字段名，避免三个读口各写一套映射。
 */
export interface QualityLotView {
  inspectionId: number
  inspectionNo: string
  inspectionType: string
  inspectionTypeName: string
  status?: string
  reviewStatus?: string
  reviewerId?: number
  reviewerName?: string
  reviewTime?: string
  reviewRemark?: string
  defectReason?: string
  orderId?: number
  orderNo?: string
  executionId?: number
  processName?: string
  materialName?: string
  productName?: string
  inspector?: string
  inspectTime?: string
  result?: string
  resultName: string
  totalQty?: number
  passQty?: number
  failQty?: number
  defectDesc?: string
  remark?: string
  createTime?: string
}

/** quality_lot → 存量生产页面展示字段。 */
export function toQualityLotView(lot: QualityLot): QualityLotView {
  const result = String(lot.result || InspectionResult.PENDING).toLowerCase()
  return {
    inspectionId: lot.lotId,
    inspectionNo: lot.lotNo,
    inspectionType: lot.lotType,
    inspectionTypeName: InspectionTypeEnum.getLabel(lot.lotType),
    status: lot.status,
    orderId: lot.orderId,
    executionId: lot.executionId,
    materialName: lot.materialName,
    productName: lot.productName,
    inspector: lot.inspector,
    inspectTime: lot.inspectTime,
    result,
    resultName: InspectionResultEnum.getLabel(result),
    totalQty: lot.lotQuantity,
    passQty: lot.passQuantity,
    failQty: lot.failQuantity,
    remark: lot.remark,
    createTime: lot.createTime,
  }
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
  /** 展示用：检验批号 —— dev-20260923-036（台账列表不再显示「批 #11」） */
  lotNo?: string
  /** 展示用：工单号 —— dev-20260923-036 */
  orderNo?: string
  /** 展示用：来源批已被后继复检版本取代 —— dev-20260923-036 */
  lotSuperseded?: boolean
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
  reinspectionLotId?: number
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
  /**
   * 判定护栏（dev-20260923-021 一期）：可判合格上界 + 预填建议
   * 上界 = 批批量 − 链上已报废未回收 − 让步未客户确认；已报废的量不得重判为良品
   */
  judgementGuard(lotId: number) {
    return request.get<R<JudgementGuardVO>>(`/quality/lot/${lotId}/judgement-guard`)
  },
  /** dev-20260922-030：重开已关闭的检验批（必须给原因，留痕） */
  reopen(lotId: number, reason: string) {
    return request.post<R<QualityLot>>(`/quality/lot/${lotId}/reopen`, null, {
      params: { reason },
    })
  },
  syncFinish(orderId: number, reason?: string) {
    return request.post<R<number>>(`/quality/lot/order/${orderId}/sync-finish`, null, {
      params: { reason },
    })
  },
  create(data: Record<string, unknown>) {
    return request.post<R<QualityLot>>('/quality/lot', data)
  },
  report(lotId: number) {
    return request.get<R<Record<string, any>>>(`/quality/lot/${lotId}/report`)
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
  /**
   * 撤销已生效的处置（dev-20260923-022 二期）：受控动作 —— 必填原因，留痕；本期支持报废(SCRAP)
   */
  revokeAction(actionId: number, reason: string) {
    return request.post<R<QualityNcrAction>>(`/quality/ncr/action/${actionId}/revoke`, null, {
      params: { reason },
    })
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

export const qualityCapaApi = {
  page(params: Record<string, unknown>) {
    return request.get<R<{ records: Record<string, unknown>[]; total: number }>>('/quality/capa/page', { params })
  },
  create(data: Record<string, unknown>) {
    return request.post<R<Record<string, unknown>>>('/quality/capa', data)
  },
  advance(id: number, data: Record<string, unknown>) {
    return request.put<R<Record<string, unknown>>>(`/quality/capa/${id}/advance`, data)
  },
}
