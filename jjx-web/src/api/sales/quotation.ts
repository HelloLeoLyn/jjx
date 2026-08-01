import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

// 报价单查询参数
export interface QuotationQueryParams {
  pageNum?: number
  pageSize?: number
  quotationNo?: string
  customerName?: string
  quotationStatus?: string
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
  discountAmount?: number
  finalAmount?: number
  quotationStatus?: string
  salesPersonId?: number
  salesPersonName?: string
  remark?: string
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
}

// 报价单列表响应
export interface QuotationListResponse {
  rows: QuotationInfo[]
  total: number
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
      data,
    })
  },

  // 修改报价单
  edit(data: QuotationInfo): AxiosPromise<void> {
    return request({
      url: '/sales/quotation',
      method: 'put',
      data,
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

  // 发送报价单给客户
  send(quotationId: number, attachmentIds?: string): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/send/${quotationId}`,
      method: 'put',
      params: attachmentIds ? { attachmentIds } : undefined,
    })
  },

  // 提交报价单审核
  submitReview(quotationId: number, attachmentIds?: string): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/submit-review/${quotationId}`,
      method: 'put',
      params: attachmentIds ? { attachmentIds } : undefined,
    })
  },

  // 审核报价单（通过/驳回）
  review(quotationId: number, approved: boolean, remark?: string, attachmentIds?: string): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/review/${quotationId}`,
      method: 'put',
      params: { approved, remark, ...(attachmentIds ? { attachmentIds } : {}) },
    })
  },

  // 更新报价单状态（客户确认/拒绝）
  changeStatus(quotationId: number, status: number, attachmentIds?: string): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/status/${quotationId}`,
      method: 'put',
      params: { status, ...(attachmentIds ? { attachmentIds } : {}) },
    })
  },

  // 客户确认报价（触发事件）
  confirm(quotationId: number, attachmentIds?: string): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/confirm/${quotationId}`,
      method: 'put',
      params: attachmentIds ? { attachmentIds } : undefined,
    })
  },

  // 客户拒绝报价（触发事件）
  reject(quotationId: number, attachmentIds?: string): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/reject/${quotationId}`,
      method: 'put',
      params: attachmentIds ? { attachmentIds } : undefined,
    })
  },

  // 已完成报价单改单
  modify(quotationId: number, attachmentIds?: string): AxiosPromise<void> {
    return request({
      url: `/sales/quotation/modify/${quotationId}`,
      method: 'put',
      params: attachmentIds ? { attachmentIds } : undefined,
    })
  },

  // 获取报价单流转记录
  getFlowRecords(quotationId: number): AxiosPromise<any[]> {
    return request({
      url: `/sales/quotation/flow/${quotationId}`,
      method: 'get',
    })
  },

  // 报价单转为订单
  convert(
    quotationId: number,
  ): AxiosPromise<{ orderId: number; orderNo: string }> {
    return request({
      url: `/sales/quotation/convert/${quotationId}`,
      method: 'post',
    })
  },

  // 导出报价单PDF
  exportPdf(quotationId: number): AxiosPromise<Blob> {
    return request({
      url: `/sales/quotation/export-pdf/${quotationId}`,
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

  // 获取报价模板列表
  getTemplates(): AxiosPromise<
    Array<{ templateId: number; templateName: string }>
  > {
    return request({
      url: '/sales/quotation/templates',
      method: 'get',
    })
  },

  // 根据模板创建报价单
  createFromTemplate(
    templateId: number,
    customerId: number,
  ): AxiosPromise<QuotationInfo> {
    return request({
      url: `/sales/quotation/template/${templateId}`,
      method: 'post',
      params: { customerId },
    })
  },

  // 快速报价（基于产品）
  quickQuote(data: {
    customerId: number
    items: Array<{
      productId: number
      quantity: number
      customRequirements?: string
    }>
  }): AxiosPromise<QuotationInfo> {
    return request({
      url: '/sales/quotation/quick',
      method: 'post',
      data,
    })
  },

  // 获取客户历史报价
  getCustomerHistory(customerId: number): AxiosPromise<QuotationInfo[]> {
    return request({
      url: `/sales/quotation/customer/${customerId}/history`,
      method: 'get',
    })
  },

  // 复制报价单
  copy(quotationId: number): AxiosPromise<QuotationInfo> {
    return request({
      url: `/sales/quotation/${quotationId}/copy`,
      method: 'post',
    })
  },
}
