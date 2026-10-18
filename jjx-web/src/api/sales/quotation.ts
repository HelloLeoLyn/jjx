import request from '@/utils/request'
import type { AxiosPromise } from 'axios'
import type { R } from '@/types'

// 报价单查询参数
export interface QuotationQueryParams {
  pageNum?: number
  pageSize?: number
  quotationNo?: string
  customerName?: string
  quotationStatus?: number
  startDate?: string
  endDate?: string
  orderByColumn?: string
  isAsc?: string
}

// 报价单基本信息
export interface QuotationBase {
  quotationId?: number
  quotationNo: string
  customerId: number
  customerName: string
  quotationDate: string
  validUntil?: string
  currency?: string
  exchangeRate?: number
  subtotalAmount?: number
  taxRate?: number
  taxAmount?: number
  totalAmount?: number
  shippingFee?: number
  discountAmount?: number
  finalAmount?: number
  quotationStatus?: string
  salesPersonId?: number
  salesPersonName?: string
  remark?: string
  attachmentIds?: number[]
}

// 报价单明细
export interface QuotationItem {
  itemId?: number
  quotationId?: number
  productId?: number
  productCode: string
  productName: string
  keyCount?: number
  width?: number
  height?: number
  thickness?: number
  materialType?: string
  color?: string
  circuitType?: string
  connectorType?: string
  quantity: number
  unitPrice: number
  unit?: string
  amount: number
  deliveryDays?: number
  estimatedDeliveryDate?: string
  customRequirements?: string
  logoRequirement?: string
  certificationRequirement?: string
  itemOrder?: number
}

// 完整的报价单信息
export interface QuotationInfo extends QuotationBase {
  items?: QuotationItem[]
  /** 链路追踪ID */
  traceId?: string
}

// 报价单列表响应
export interface QuotationListResponse {
  records: QuotationInfo[]
  total: number
}

// 附件归属使用的内部行键仅保留在页面/草稿中，提交副本不携带该字段。
function toQuotationPayload(data: QuotationInfo): QuotationInfo {
  return {
    ...data,
    items: data.items?.map((item) => {
      const payload: QuotationItem & { __rowKey?: string } = { ...item }
      delete payload.__rowKey
      return payload
    }),
  }
}

// 报价单API对象
export const quotationApi = {
  // 查询报价单列表
  list(params: QuotationQueryParams): AxiosPromise<QuotationListResponse> {
    return request({
      url: '/sales/quotation/list',
      method: 'get',
      params,
    })
  },

  // 获取报价单明细（新增样品单预览用）
  getItems(quotationId: number): AxiosPromise<QuotationItem[]> {
    return request({
      url: `/sales/quotation/${quotationId}/items`,
      method: 'get',
    })
  },

  // 获取报价单详细信息
  getInfo(quotationId: number): AxiosPromise<QuotationInfo> {
    return request({
      url: `/sales/quotation/${quotationId}`,
      method: 'get',
    })
  },

  // 新增报价单
  add(data: QuotationInfo): AxiosPromise<void> {
    return request({
      url: '/sales/quotation',
      method: 'post',
      data: toQuotationPayload(data),
    })
  },

  // 修改报价单
  edit(data: QuotationInfo): AxiosPromise<void> {
    return request({
      url: '/sales/quotation',
      method: 'put',
      data: toQuotationPayload(data),
    })
  },

  // 删除报价单
  remove(quotationIds: number | number[]): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/${quotationIds}`,
      method: 'delete',
    })
  },

  // 导出报价单
  export(params: QuotationQueryParams): AxiosPromise<Blob> {
    return request({
      url: '/sales/quotation/export',
      method: 'get',
      params,
      responseType: 'blob',
    })
  },

  // 发送报价单给客户（附件双写收口 dev-20261014-028：改传附件引用 ID，不再传老字段）
  send(quotationId: number, attachmentRefIds?: number[]): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/send/${quotationId}`,
      method: 'put',
      params: attachmentRefIds?.length ? { attachmentRefIds: attachmentRefIds.join(',') } : undefined,
    })
  },

  // 提交报价单审核（附件双写收口 dev-20261014-028：改传附件引用 ID，不再传老字段）
  submitReview(quotationId: number, attachmentRefIds?: number[]): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/submit-review/${quotationId}`,
      method: 'put',
      params: attachmentRefIds?.length ? { attachmentRefIds: attachmentRefIds.join(',') } : undefined,
    })
  },

  // 审核报价单（通过/驳回）
  review(quotationId: number, approved: boolean, remark?: string, attachmentRefIds?: number[]): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/review/${quotationId}`,
      method: 'put',
      params: { approved, remark, ...(attachmentRefIds?.length ? { attachmentRefIds: attachmentRefIds.join(',') } : {}) },
    })
  },

  // 更新报价单状态（客户确认/拒绝）
  changeStatus(quotationId: number, status: number, attachmentRefIds?: number[]): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/status/${quotationId}`,
      method: 'put',
      params: { status, ...(attachmentRefIds?.length ? { attachmentRefIds: attachmentRefIds.join(',') } : {}) },
    })
  },

  // 客户确认报价（触发事件）
  confirm(quotationId: number, attachmentRefIds?: number[]): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/confirm/${quotationId}`,
      method: 'put',
      params: attachmentRefIds?.length ? { attachmentRefIds: attachmentRefIds.join(',') } : undefined,
    })
  },

  // 客户拒绝报价（触发事件）
  reject(
    quotationId: number,
    reason?: string,
    attachmentRefIds?: number[]
  ): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/reject/${quotationId}`,
      method: 'put',
      params: {
        ...(reason ? { reason } : {}),
        ...(attachmentRefIds?.length
          ? { attachmentRefIds: attachmentRefIds.join(',') }
          : {}),
      },
    })
  },

  // 已完成报价单改单
  modify(quotationId: number, attachmentRefIds?: number[]): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/modify/${quotationId}`,
      method: 'put',
      params: attachmentRefIds?.length ? { attachmentRefIds: attachmentRefIds.join(',') } : undefined,
    })
  },

  // 获取报价单流转记录
  getFlowRecords(quotationId: number): AxiosPromise<any[]> {
    return request({
      url: `/sales/quotation/flow/${quotationId}`,
      method: 'get',
    })
  },

  getOrderDraft(quotationId: number): AxiosPromise<import('@/types/sales/order').QuotationOrderDraft> {
    return request({ url: `/sales/quotation/order-draft/${quotationId}`, method: 'get' })
  },
  // 报价单转为订单，仅在来源订单表单正式保存时调用。
  convert(
    quotationId: number,
    data?: import('@/types/sales/order').QuotationOrderDraft,
  ): AxiosPromise<number> {
    return request({
      url: `/sales/quotation/convert/${quotationId}`,
      method: 'post',
      data,
    })
  },

  // 导出报价单Excel（单张表单）
  exportExcel(quotationId: number): AxiosPromise<Blob> {
    return request({
      url: `/sales/quotation/export-excel/${quotationId}`,
      method: 'get',
      responseType: 'blob',
    })
  },

  // 获取报价单状态选项
  getStatusOptions(): AxiosPromise<Array<{ value: string; label: string }>> {
    return request({
      url: '/sales/quotation/status-options',
      method: 'get',
    })
  },

  // 获取币种选项
  getCurrencyOptions(): AxiosPromise<Array<{ value: string; label: string }>> {
    return request({
      url: '/sales/quotation/currency-options',
      method: 'get',
    })
  },

  // 获取指定币种汇率（相对CNY）
  getExchangeRate(currency: string) {
    return request.get<R<number>>('/system/exchange-rate/rate', { params: { currency } })
  },

    // 获取客户历史报价
  getCustomerHistory(customerId: number): AxiosPromise<QuotationInfo[]> {
    return request({
      url: `/sales/quotation/customer/${customerId}/history`,
      method: 'get',
    })
  },

  // 获取待新增的报价单副本（此接口不落库）
  copy(quotationId: number): AxiosPromise<QuotationInfo> {
    return request({
      url: `/sales/quotation/copy/${quotationId}`,
      method: 'post',
    })
  },

  // 报价单统计（DEV-594 接前端）
  statistics(): AxiosPromise {
    return request({
      url: '/sales/quotation/statistics',
      method: 'get',
    })
  },
}
