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
  productDescription?: string
  keyCount?: number
  sizeDescription?: string
  materialRequirements?: string
  circuitRequirements?: string
  connectorRequirements?: string
  specialRequirements?: string
  hasDrawing?: number
  inquiryStatus: string
  convertedQuotationId?: number
  convertTime?: string
  remark?: string
  salesPersonId?: number
  salesPersonName?: string
}

// 询价单列表响应
export interface InquiryListResponse {
  rows: InquiryBase[]
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

  // 获取状态选项
  getStatusOptions(): AxiosPromise<Array<{ value: string; label: string }>> {
    return request({
      url: '/sales/inquiry/status-options',
      method: 'get',
    })
  },
}
