import request from '@/utils/request'
import type { R } from '@/types'

/**
 * 成品报废单 —— dev-20260924-006（报废线三期）。
 * 单据由报废处置生效时自动生成；本接口只做查询（打印凭据版式后置）。
 */
export interface QualityScrapOrder {
  scrapId: number
  scrapNo: string
  actionId: number
  ncrId: number
  ncrNo?: string
  lotId?: number
  lotNo?: string
  orderId?: number
  orderNo?: string
  productCode?: string
  productName?: string
  batchNo?: string
  quantity?: number
  defectItem?: string
  defectLevel?: string
  pieceRange?: string
  reason?: string
  status?: string
  applicant?: string
  approver?: string
  approveTime?: string
  remark?: string
  createTime?: string
}

export const qualityScrapOrderApi = {
  list(keyword?: string) {
    return request.get<R<QualityScrapOrder[]>>('/quality/scrap-order/list', { params: { keyword } })
  },
  detail(scrapId: number) {
    return request.get<R<QualityScrapOrder>>(`/quality/scrap-order/${scrapId}`)
  },
}
