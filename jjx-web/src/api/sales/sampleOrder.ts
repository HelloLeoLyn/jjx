import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

// 样品单接口
export const sampleOrderApi = {
  // 从报价单创建样品单
  createFromQuotation(
    quotationId: number,
    data?: { sampleQty?: number; remark?: string }
  ): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/create-from-quotation/${quotationId}`,
      method: 'post',
      params: data,
    })
  },

  // 样品单列表
  list(params?: {
    customerId?: number
    sampleStatus?: number
    salesPersonId?: number
  }): AxiosPromise<any[]> {
    return request({
      url: '/sales/sample-order/list',
      method: 'get',
      params,
    })
  },

  // 样品单详情
  getInfo(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/${orderId}`,
      method: 'get',
    })
  },

  // 提交审核
  submitReview(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/submit-review/${orderId}`,
      method: 'put',
    })
  },

  // 审核通过
  approve(orderId: number, remark?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/approve/${orderId}`,
      method: 'put',
      params: { remark },
    })
  },

  // 审核驳回
  rejectReview(orderId: number, remark?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/reject-review/${orderId}`,
      method: 'put',
      params: { remark },
    })
  },

  // 工程接单
  startEngineering(orderId: number, engineeringNote?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/start-engineering/${orderId}`,
      method: 'put',
      params: { engineeringNote },
    })
  },

  // 工程标记样品完成
  markReady(orderId: number, sampleQty?: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/mark-ready/${orderId}`,
      method: 'put',
      params: { sampleQty },
    })
  },

  // 送样登记
  sendSample(orderId: number, trackingNo?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/send-sample/${orderId}`,
      method: 'put',
      params: { trackingNo },
    })
  },

  // 客户确认
  confirm(orderId: number, clientName?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/confirm/${orderId}`,
      method: 'put',
      params: { clientName },
    })
  },

  // 客户退回
  rejectSample(orderId: number, rejectReason?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/reject-sample/${orderId}`,
      method: 'put',
      params: { rejectReason },
    })
  },

  // 转量产
  convertToProduction(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/convert-to-production/${orderId}`,
      method: 'put',
    })
  },

  // 作废
  cancel(orderId: number, cancelReason?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/cancel/${orderId}`,
      method: 'put',
      params: cancelReason ? { cancelReason } : undefined,
    })
  },

  // 退回后重新打样
  restartEngineering(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/restart-engineering/${orderId}`,
      method: 'put',
    })
  },

  // 工程接单确认
  acceptEngineering(orderId: number, acceptorName?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/accept-engineering/${orderId}`,
      method: 'put',
      params: acceptorName ? { acceptorName } : undefined,
    })
  },

  // 工程拒单
  rejectEngineering(orderId: number, rejectReason: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/reject-engineering/${orderId}`,
      method: 'put',
      params: { rejectReason },
    })
  },

  // 更新打样当前工序
  updateProcess(orderId: number, process: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/update-process/${orderId}`,
      method: 'put',
      params: { process },
    })
  },

  // 状态选项
  getStatusOptions(): AxiosPromise<Array<{ value: number; label: string; description: string; terminal: boolean }>> {
    return request({
      url: '/sales/sample-order/status-options',
      method: 'get',
    })
  },
}
