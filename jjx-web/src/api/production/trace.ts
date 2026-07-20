import request from '@/utils/request'
import type { PageResult, R } from '@/types'

export interface TraceQuery {
  pageNum: number
  pageSize: number
  traceCode?: string
  traceType?: string
  batchNo?: string
  orderId?: number
}

export interface TraceVO {
  traceId: number
  traceType: string
  traceTypeName: string
  traceCode: string
  batchNo: string
  orderId: number
  orderNo: string
  productName: string
  materialName: string
  operation: string
  operationName: string
  operator: string
  operateTime: string
  detail: string
  createBy: string
  createTime: string
}

export const traceApi = {
  /** 分页查询追溯记录 */
  page(params: TraceQuery) {
    return request.get<R<PageResult<TraceVO>>>('/production/trace/page', { params })
  },
  /** 正追溯 */
  traceForward(traceCode: string) {
    return request.get<R<TraceVO[]>>(`/production/trace/forward/${traceCode}`)
  },
  /** 反追溯 */
  traceBackward(traceCode: string) {
    return request.get<R<TraceVO[]>>(`/production/trace/backward/${traceCode}`)
  },
}
