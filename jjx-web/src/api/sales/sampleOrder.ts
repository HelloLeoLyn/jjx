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
    hasAcceptor?: boolean
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

  // 样品单产品明细（DEV-781：报价转样品后详情展示）
  getProducts(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/products/${orderId}`,
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
  convertToProduction(orderId: number, items?: Array<{ orderProductId: number; productId: number }>): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/convert-to-production/${orderId}`,
      method: 'put',
      data: items || [],
    })
  },

  // 转量产 · 产品标准化窗口（DEV-xxx）
  convertCheck(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/convert-check/${orderId}`,
      method: 'get',
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

  // 更新打样当前工序（工序单元：材料+工艺说明+耗时；材料JSON走body，8-03避免长URL）
  updateProcess(orderId: number, process: string, materials?: string | null, processNote?: string, durationMinutes?: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/update-process/${orderId}`,
      method: 'put',
      data: { process, materials, processNote, durationMinutes },
    })
  },

  // 查询打样工序历史（roundNo 可选，DEV-500 按轮次过滤）
  listProcesses(orderId: number, roundNo?: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/processes/${orderId}`,
      method: 'get',
      params: roundNo ? { roundNo } : undefined,
    })
  },

  // 保存打样工序计划（多选作业项目，整单覆盖当前轮次）
  saveProcessPlan(orderId: number, data: any): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/processes/${orderId}/plan`,
      method: 'put',
      data,
    })
  },

  // 推进打样工序状态（开始/完成，可带耗时/说明/材料）
  updateProcessItemStatus(orderId: number, processId: number, data: any): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/processes/${orderId}/item/${processId}/status`,
      method: 'put',
      data,
    })
  },

  // 查询打样BOM物料清单
  listBom(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/bom/${orderId}`,
      method: 'get',
    })
  },

  // 保存打样BOM物料清单（覆盖当前轮次）
  saveBom(orderId: number, items: any[], roundNo?: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/bom/${orderId}`,
      method: 'put',
      params: roundNo ? { roundNo } : undefined,
      data: items,
    })
  },

  // 删除单条打样BOM
  deleteBomItem(bomId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/bom/${bomId}`,
      method: 'delete',
    })
  },

  // 录入打样成本/工时
  recordCost(orderId: number, cost?: number, workHours?: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/record-cost/${orderId}`,
      method: 'put',
      params: { cost, workHours },
    })
  },

  // 产品资料转移（DEV-505：建档产品/BOM/工艺路线）
  transfer(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/transfer/${orderId}`,
      method: 'post',
    })
  },

  // 查询打样轮次快照
  getRounds(orderId: number): AxiosPromise<any[]> {
    return request({
      url: `/sales/sample-order/rounds/${orderId}`,
      method: 'get',
    })
  },

  // 打样汇总（总工时+材料成本，DEV-526 打样平台进度展示用）
  getSummary(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/summary/${orderId}`,
      method: 'get',
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
