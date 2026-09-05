import request from '@/utils/request'
import type { R } from '@/types'

// 库存预警 API
export const alertApi = {
  // 分页查询预警列表
  list(params: any) {
    return request.get('/inventory/alert/list', {
      params: {
        current: params.current ?? 1,
        size: params.pageSize ?? 10,
        alertType: params.alertType || undefined,
        alertLevel: params.alertLevel || undefined,
        // 2026-08-18：状态筛选 0未处理/1已上报/2已处理/3已解除，空=全部
        status: params.status === '' || params.status === undefined || params.status === null ? undefined : params.status,
      },
    })
  },

  // 查询未处理的预警（统计卡片用）
  unprocessed() {
    return request.get('/inventory/alert/unprocessed')
  },

  // 执行预警检查
  executeCheck() {
    return request.post('/inventory/alert/execute-check')
  },

  // 标记已读
  markRead(alertId: number) {
    return request.post(`/inventory/alert/mark-read/${alertId}`)
  },

  // 批量标记已读
  batchMarkRead(alertIds: number[]) {
    return request.post('/inventory/alert/batch-mark-read', alertIds)
  },

// 批量处理预警（采购计划确认后回写，关联采购订单号；materialIds 为按物料回写，2026-08-18）
  batchProcess(data: {
    alertIds: number[]
    materialIds?: number[]
    relatedOrderNo: string
    remark?: string
  }) {
    return request.post('/inventory/alert/batch-process', data)
  },

  // 订单齐套检查（按BOM算料缺料预警，手动重新检查）
  checkOrderShortage: (orderId: number) =>
    request.post(`/inventory/alert/check-order-shortage/${orderId}`),

  // 订单齐套检查只读预览（不生成预警）
  orderShortagePreview: (orderId: number) =>
    request.get(`/inventory/alert/order-shortage-preview/${orderId}`),

  // 查询订单未处理缺料预警数（DEV-583）
  countUnprocessedShortage: (orderId: number) =>
    request.get(`/inventory/alert/count-unprocessed-shortage/${orderId}`),
}
