import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type { SalesDeliveryCreateDTO } from '@/api/sales/delivery'
// 添加以下 API 方法
export const orderStatusApi = {
  // ==================== 订单状态流转 ====================
  /** 提交审核 */
  submitOrderReview(orderId: number) {
    return request.put<R<void>>(`/sales/orders/${orderId}/status/submissions`)
  },

  /** 审核通过（2026-08-12：附带上传的确认书等附件ID，选填） */
  approveOrder(orderId: number, remark: string, attachments?: string) {
    return request.put<R<void>>(`/sales/orders/${orderId}/status/approval`, { orderId, remark, attachments })
  },

  /** 审核驳回 */
  rejectOrder(orderId: number, remark: string) {
    return request.put<R<void>>(`/sales/orders/${orderId}/status/rejection`, { orderId, remark })
  },

  /** 客户确认（2026-08-13 修正 URL：/confirm 不带 status） */
  confirmOrder(orderId: number, confirmedBy: string, confirmMethod?: string, remark?: string) {
    return request.put<R<void>>(`/sales/orders/${orderId}/confirm`, null, {
      params: { confirmedBy, confirmMethod, remark },
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

  // 生成生产计划（标准模式：SO→PLAN→审批→转工单）
  generatePlan: (orderId: number) =>
    request.put(`/sales/orders/${orderId}/status/generate-plan`),

  // 发货（7→8，触发 order.delivering 自动创建销售出库单扣库存，2026-08-12）
  shipOrder: (orderId: number, body?: SalesDeliveryCreateDTO) =>
    request.put(`/sales/orders/${orderId}/status/ship`, body),

  // 完成订单
  completeOrder: (orderId: number) => request.put(`/sales/orders/${orderId}/status/complete`),

  // 取消订单
  cancelOrder: (orderId: number, reason: string) =>
    request.delete(`/sales/orders/${orderId}/status`, { data: { reason } }),
}
