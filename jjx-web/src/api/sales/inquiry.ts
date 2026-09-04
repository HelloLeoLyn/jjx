import request from '@/utils/request'
import type { AxiosPromise } from 'axios'

// 询价单查询参数
export interface InquiryQueryParams {
  pageNum?: number
  pageSize?: number
  inquiryNo?: string
  customerName?: string
  inquiryStatus?: string
  startDate?: string
  endDate?: string
  orderByColumn?: string
  isAsc?: string
}

// 询价单基本信息
export interface InquiryBase {
  inquiryId?: number
  inquiryNo: string
  customerId: number
  customerName: string
  contactPerson?: string
  contactPhone?: string
  inquiryDate: string
  expectedQuantity?: number
  /** 预估单价（转报价继承，DEV-937） */
  unitPrice?: number
  productId?: number
  productCode?: string
  productName?: string
  customerShortName?: string
  productDescription?: string
  keyCount?: number
  sizeDescription?: string
  materialRequirements?: string
  circuitRequirements?: string
  connectorRequirements?: string
  specialRequirements?: string
  hasDrawing?: number
  inquiryStatus: string
  inquiryType?: number
  convertedQuotationId?: number
  convertTime?: string
  remark?: string
  salesPersonId?: number
  salesPersonName?: string
  attachmentIds?: number[]
}

// 询价单列表响应
export interface InquiryListResponse {
  records: InquiryBase[]
  total: number
}

// 询价单API对象
export const inquiryApi = {
  // 查询询价单列表
  list(params: InquiryQueryParams): AxiosPromise<InquiryListResponse> {
    return request({
      url: '/sales/inquiry/list',
      method: 'get',
      params,
    })
  },

  // 获取询价单详细信息
  getInfo(inquiryId: number): AxiosPromise<InquiryBase> {
    return request({
      url: `/sales/inquiry/${inquiryId}`,
      method: 'get',
    })
  },

  // 编码生成器：按客户简称取下一个流水号
  nextSerial(customerShort: string): AxiosPromise<string> {
    return request({
      url: '/sales/inquiry/next-serial',
      method: 'get',
      params: { customerShort },
    })
  },

  // 新增询价单
  add(data: InquiryBase): AxiosPromise<void> {
    return request({
      url: '/sales/inquiry',
      method: 'post',
      data,
    })
  },

  // 修改询价单
  edit(data: InquiryBase): AxiosPromise<void> {
    return request({
      url: '/sales/inquiry',
      method: 'put',
      data,
    })
  },

  // 删除询价单
  remove(inquiryIds: number | number[]): AxiosPromise<void> {
    return request({
      url: `/sales/inquiry/${inquiryIds}`,
      method: 'delete',
    })
  },

  // 询价转报价
  convert(inquiryId: number): AxiosPromise<{ quotationId: number }> {
    return request({
      url: `/sales/inquiry/convert/${inquiryId}`,
      method: 'post',
    })
  },

  // 发送询价（草稿/待处理 → 已发送）
  send(inquiryId: number): AxiosPromise<void> {
    return request({
      url: `/sales/inquiry/send/${inquiryId}`,
      method: 'put',
    })
  },

  // 客户确认询价（已发送 → 已确认）
  accept(inquiryId: number): AxiosPromise<void> {
    return request({
      url: `/sales/inquiry/accept/${inquiryId}`,
      method: 'put',
    })
  },

  // 客户拒绝询价（已发送 → 已拒绝）
  reject(inquiryId: number): AxiosPromise<void> {
    return request({
      url: `/sales/inquiry/reject/${inquiryId}`,
      method: 'put',
    })
  },

  // 获取状态选项
  getStatusOptions(): AxiosPromise<Array<{ value: string; label: string }>> {
    return request({
      url: '/sales/inquiry/status-options',
      method: 'get',
    })
  },

  // 导出询价单列表（DEV-591）
  export(params?: Record<string, any>): AxiosPromise<Blob> {
    return request({
      url: '/sales/inquiry/export',
      method: 'get',
      params,
      responseType: 'blob',
    })
  },
}
