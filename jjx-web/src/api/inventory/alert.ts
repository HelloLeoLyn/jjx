import request from '@/utils/request'

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
        status: params.processed === 'false' ? '0' : params.processed === 'true' ? '2' : undefined,
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

  // 处理预警
  process(alertId: number, processedBy: string, remark?: string) {
    return request.post(`/inventory/alert/process/${alertId}`, null, {
      params: { processedBy, remark: remark || undefined },
    })
  },

  // 订单齐套检查（按BOM算料缺料预警，手动重新检查）
  checkOrderShortage: (orderId: number) =>
    request.post(`/inventory/alert/check-order-shortage/${orderId}`),
}
