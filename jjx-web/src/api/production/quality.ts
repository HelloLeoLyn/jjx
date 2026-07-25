import request from '@/utils/request'
import type { PageResult, R } from '@/types'

export interface QualityQuery {
  pageNum: number
  pageSize: number
  inspectionNo?: string
  inspectionType?: string
  orderId?: number
  result?: string
}

export interface InspectionItemVO {
  itemId?: number
  checkItem: string
  standard?: string
  actualValue?: string
  result?: string
  remark?: string
}

export interface QualityVO {
  inspectionId: number
  inspectionNo: string
  inspectionType: string
  inspectionTypeName: string
  orderId?: number
  orderNo?: string
  materialName?: string
  productName?: string
  inspector?: string
  inspectTime?: string
  result: string
  resultName: string
  totalQty?: number
  passQty?: number
  failQty?: number
  defectDesc?: string
  remark?: string
  createTime?: string
  items?: InspectionItemVO[]
}

export const qualityApi = {
  page(params: QualityQuery) {
    return request.get<R<PageResult<QualityVO>>>('/production/quality/page', { params })
  },
  getById(id: number) {
    return request.get<R<QualityVO>>(`/production/quality/${id}`)
  },
  create(data: any) {
    return request.post<R<number>>('/production/quality', data)
  },
  update(data: any) {
    return request.put<R<void>>('/production/quality', data)
  },
  remove(id: number) {
    return request.delete<R<void>>(`/production/quality/${id}`)
  },
  getStatistics() {
    return request.get<R<any>>('/production/quality/statistics')
  },
}
