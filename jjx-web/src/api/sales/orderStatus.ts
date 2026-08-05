import request from '@/utils/request'
import type { PageResult, R } from '@/types'
// 添加以下 API 方法
export const orderStatusApi = {
  // ==================== 订单状态流转 ====================
  /** 提交审核 */
  submitOrderReview(orderId: number) {
    return request.put<R<void>>(`/sales/orders/${orderId}/status/submissions`)
  },

  /** 审核通过 */
  approveOrder(orderId: number, remark: string) {
    return request.put<R<void>>(`/sales/orders/${orderId}/status/approval`, { orderId, remark })
  },

  /** 审核驳回 */
  rejectOrder(orderId: number, remark: string) {
    return request.put<R<void>>(`/sales/orders/${orderId}/status/rejection`, { orderId, remark })
  },

  /** 客户确认 */
  confirmOrder(orderId: number, confirmPerson: string) {
    return request.put<R<void>>(`/sales/orders/${orderId}/status/confirm`, null, {
      params: { confirmPerson },
    })
  },

  // 开始审核
  startReview: (orderId: number) => request.put(`/sales/orders/${orderId}/status/review`),

  // 取消审核
  cancelReview: (orderId: number) => request.delete(`/sales/orders/${orderId}/status/review`),

  // 重新提交
  resubmit: (orderId: number) => request.put(`/sales/orders/${orderId}/status/resubmissions`),

  // 发送客户确认
  sendToCustomer: (orderId: number, context?: string) =>
    request.put(`/sales/orders/${orderId}/status/send-to-customer`, {
      orderId,
      context: context || '',
    }),

  // 开始生产
  startProduction: (orderId: number) =>
    request.put(`/sales/orders/${orderId}/status/start-production`),

  // 完成订单
  completeOrder: (orderId: number) => request.put(`/sales/orders/${orderId}/status/complete`),

  // 取消订单
  cancelOrder: (orderId: number, reason: string) =>
    request.delete(`/sales/orders/${orderId}/status`, { data: { reason } }),
}
