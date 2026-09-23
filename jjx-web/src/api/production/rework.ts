import request from '@/utils/request'
import type { R } from '@/types'
import type { ReworkTraceVO } from '@/types/production/operationExecution'

/**
 * 返工链投影（只读）—— dev-20260923-031。
 * 三个入参任选其一：工单（工序执行页整单拿）/ 返工工序（工序详情）/ 不良单（不良台账）。
 */
export const reworkTraceApi = {
  trace(params: { orderId?: number; executionId?: number; ncrId?: number }) {
    return request.get<R<ReworkTraceVO[]>>('/production/rework/trace', { params })
  },
}
