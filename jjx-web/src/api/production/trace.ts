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

// ==================== P4-B：生产履历（只读） ====================

/** P4-B：Trace 事件类型常量（与后端 TraceEventType 一致） */
export const TraceEventType = {
  ORDER_CREATED: 'ORDER_CREATED',
  ORDER_STARTED: 'ORDER_STARTED',
  ORDER_COMPLETED: 'ORDER_COMPLETED',
  EXECUTION_STARTED: 'EXECUTION_STARTED',
  EXECUTION_COMPLETED: 'EXECUTION_COMPLETED',
  WORK_REPORT_SUBMITTED: 'WORK_REPORT_SUBMITTED',
  WORK_REPORT_CANCELLED: 'WORK_REPORT_CANCELLED',
  QUALITY_CREATED: 'QUALITY_CREATED',
  QUALITY_PASSED: 'QUALITY_PASSED',
  QUALITY_FAILED: 'QUALITY_FAILED',
} as const

export type TraceEventTypeValue = (typeof TraceEventType)[keyof typeof TraceEventType]

/** P4-B：事件类别（分类筛选用） */
export type TraceCategory = 'ORDER' | 'EXECUTION' | 'WORK_REPORT' | 'QUALITY'

/** P4-B：时间线事件 */
export interface TraceEventVO {
  eventType: string
  eventTime: string
  orderId: number
  executionId?: number | null
  workReportId?: number | null
  qualityInspectionId?: number | null
  actorId?: number | null
  actorName?: string | null
  title: string
  description?: string | null
  status?: string | null
  sourceType: string
  sourceId: number
}

/** P4-B：订单履历（订单头 + 事件） */
export interface OrderTraceVO {
  orderHeader: {
    orderId: number
    orderNo: string
    productCode?: string
    productName?: string
    productSpec?: string
    productUnit?: string
    plannedQuantity?: number
    orderStatus?: number
    orderStatusDesc?: string
    actualStartTime?: string
    actualEndTime?: string
  } | null
  events: TraceEventVO[]
}

export const productionTraceApi = {
  /** 生产订单履历（唯一聚合入口） */
  getOrderTrace(orderId: number, params?: { category?: TraceCategory | string; executionId?: number }) {
    return request.get<R<OrderTraceVO>>(`/production/trace/order/${orderId}`, { params })
  },
}
