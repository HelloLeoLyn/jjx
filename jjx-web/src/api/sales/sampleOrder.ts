import request from '@/utils/request'

/** 印刷工序历史输入联想（1225） */
export function getProcessHistory() {
  return request({
    url: '/sales/sample-order/process/history',
    method: 'get',
  })
}

/** 色号联想（2026-09-04：空=常用TOP10，有输入=字典模糊搜） */
export function suggestSampleColors(keyword?: string, limit?: number) {
  return request({
    url: '/sales/sample-order/process/color-suggest',
    method: 'get',
    params: { keyword, limit },
  })
}

/** 油墨联想（2026-09-04：空=常用TOP10，有输入=INK物料+历史模糊搜；返回 [{text, materialId}]） */
export function suggestSampleInks(keyword?: string, limit?: number) {
  return request({
    url: '/sales/sample-order/process/ink-suggest',
    method: 'get',
    params: { keyword, limit },
  })
}

import type { AxiosPromise } from 'axios'

// 样品单接口
export const sampleOrderApi = {
  // 从报价单创建样品单
  // 新增样品单（直接选客户+产品明细，报价单可选）
  create(data: {
    customerId: number
    quotationId?: number
    items?: Array<{
      productId?: number
      productCode?: string
      productName?: string
      quantity?: number
      unit?: string
    }>
    deliveryDate?: string
    contactPerson?: string
    contactPhone?: string
    techRequirement?: string
    remark?: string
  }): AxiosPromise<any> {
    return request({
      url: '/sales/sample-order',
      method: 'post',
      data,
    })
  },

  // 更新样品单（驳回后编辑：仅样品需求已创建状态可编辑，明细全量替换）
  update(
    orderId: number,
    data: {
      customerId: number
      items?: Array<{
        productId?: number
        productCode?: string
        productName?: string
        quantity?: number
        unit?: string
      }>
      deliveryDate?: string
      contactPerson?: string
      contactPhone?: string
      techRequirement?: string
      remark?: string
    }
  ): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/${orderId}`,
      method: 'put',
      data,
    })
  },

  createFromQuotation(
    quotationId: number,
    data?: {
      sampleQty?: number
      remark?: string
      deliveryDate?: string
      contactPerson?: string
      contactPhone?: string
      techRequirement?: string
    }
  ): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/create-from-quotation/${quotationId}`,
      method: 'post',
      params: data,
    })
  },

  // 复制样品单（DEV-1114：仅已完成/已取消终态单，一键生成新草稿单）
  copy(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/copy/${orderId}`,
      method: 'post',
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

  // 打样工作台「来源单据」摘要（任务1438）：按样品单关联链收敛，服务端已剔除价格等敏感数据
  // 无来源单据时 data=null
  getSourceQuotationSummary(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/${orderId}/source-quotation`,
      method: 'get',
    })
  },
  getSourceInquirySummary(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/${orderId}/source-inquiry`,
      method: 'get',
    })
  },

  // 提交审核
  submitRequest(orderId: number, attachmentIds?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/submit-request/${orderId}`,
      method: 'put',
      params: { attachmentIds },
    })
  },

  // 审核通过
  approve(orderId: number, remark?: string, attachmentIds?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/approve/${orderId}`,
      method: 'put',
      params: { remark, attachmentIds },
    })
  },

  // 审核驳回
  rejectReview(orderId: number, remark?: string, attachmentIds?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/reject-review/${orderId}`,
      method: 'put',
      params: { remark, attachmentIds },
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
  sendSample(orderId: number, trackingNo?: string, attachmentIds?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/send-sample/${orderId}`,
      method: 'put',
      params: { trackingNo, attachmentIds },
    })
  },

  // 客户确认
  confirm(orderId: number, clientName?: string, attachmentIds?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/confirm/${orderId}`,
      method: 'put',
      params: { clientName, attachmentIds },
    })
  },

  // 客户退回
  rejectSample(orderId: number, rejectReason?: string, attachmentIds?: string): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/reject-sample/${orderId}`,
      method: 'put',
      params: { rejectReason, attachmentIds },
    })
  },

  // 转量产
  convertToProduction(
    orderId: number,
    items?: Array<{ orderProductId: number; productId: number }>,
    extras?: {
      paymentTerms?: string
      deliveryTerms?: string
      deliveryAddress?: string
      contactPerson?: string
      contactPhone?: string
    }
  ): AxiosPromise<any> {
    const params = extras
      ? Object.fromEntries(Object.entries(extras).filter(([, value]) => value != null && value !== ''))
      : undefined
    return request({
      url: `/sales/sample-order/convert-to-production/${orderId}`,
      method: 'put',
      data: items ?? null,
      params,
    })
  },

  // 转量产 · 产品标准化窗口（DEV-xxx）
  convertCheck(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/convert-check/${orderId}`,
      method: 'get',
    })
  },

  // 转量产 · 资料转移提醒（DEV-1228：发布任务给工程执行资料转移，不再直接转移）
  transferRemind(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sample/transfer/remind/${orderId}`,
      method: 'post',
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
  acceptEngineering(orderId: number): AxiosPromise<any> {
    return request({
      url: `/sales/sample-order/accept-engineering/${orderId}`,
      method: 'put',
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
  updateProcess(
    orderId: number,
    process: string,
    materials?: string | null,
    processNote?: string,
    durationMinutes?: number
  ): AxiosPromise<any> {
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

  // 保存打样工序计划（多选标准工序，整单覆盖当前轮次）
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
  getStatusOptions(): AxiosPromise<
    Array<{ value: number; label: string; description: string; terminal: boolean }>
  > {
    return request({
      url: '/sales/sample-order/status-options',
      method: 'get',
    })
  },
}
