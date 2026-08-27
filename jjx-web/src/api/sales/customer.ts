import request from '@/utils/request'
import type { PageResult, R } from '@/types'
import type {
  CustomerQueryParams,
  CustomerFormData,
  CustomerItem,
  CustomerDetail,
  CustomerSearchVO,
  CustomerVO,
} from '@/types/sales/customer'

// 客户管理API
export const customerApi = {
  /** 获取客户列表（分页） */
  getCustomers(params: CustomerQueryParams) {
    return request.get<R<PageResult<CustomerItem>>>('/sales/customers', { params })
  },

  /** 根据关键词搜索客户 */
  searchCustomers(keyword: string) {
    return request.get<R<CustomerSearchVO[]>>('/sales/customers/search', { params: { keyword } })
  },

  /** 获取客户详情 */
  getCustomer(customerId: number) {
    return request.get<R<CustomerDetail>>(`/sales/customers/${customerId}`)
  },

  /** 生成客户编码 */
  generateCode() {
    return request.get<R<string>>('/sales/customers/generate-code')
  },

  /** 新增客户 */
  addCustomer(data: CustomerFormData) {
    return request.post<R<void>>('/sales/customers', data)
  },

  /** 修改客户 */
  updateCustomer(customerId: number, data: CustomerFormData) {
    return request.put<R<void>>(`/sales/customers/${customerId}`, data)
  },

  /** 删除客户 */
  deleteCustomers(customerIds: number | number[]) {
    return request.delete<R<void>>(`/sales/customers/${customerIds}`)
  },

  /** 导出客户列表 */
  exportCustomers(params: CustomerQueryParams) {
    return request.get('/sales/customers/export', {
      params,
      responseType: 'blob',
    })
  },

  /** 获取客户下拉列表 */
  getCustomerDropdown() {
    return request.get<R<CustomerSearchVO[]>>('/sales/customers/dropdown')
  },

  /** 变更客户状态 */
  changeCustomerStatus(customerId: number, status: number) {
    return request.put<R<void>>(`/sales/customers/${customerId}/status`, null, {
      params: { status },
    })
  },

  /** 批量审核客户 */
  approveCustomers(customerIds: number[]) {
    return request.put<R<void>>('/sales/customers/approve', customerIds)
  },

  /** 获取客户统计信息 */
  getCustomerStatistics() {
    return request.get<R<Record<string, unknown>>>('/sales/customers/statistics')
  },

  /** 根据客户编码获取客户信息 */
  getCustomerByCode(customerCode: string) {
    return request.get<R<CustomerDetail>>(`/sales/customers/code/${customerCode}`)
  },

  /** 更新客户信用额度 */
  updateCustomerCreditLimit(customerId: number, creditLimit: number) {
    return request.put<R<void>>(`/sales/customers/${customerId}/credit`, null, {
      params: { creditLimit },
    })
  },

  /** 获取客户联系人列表 */
  getCustomerContacts(customerId: number) {
    return request.get<R<Record<string, unknown>[]>>(`/sales/customers/${customerId}/contacts`)
  },

  /** 获取客户历史订单 */
  getCustomerOrders(customerId: number) {
    return request.get<R<Record<string, unknown>[]>>(`/sales/customers/${customerId}/orders`)
  },

  /** 获取客户报价记录 */
  getCustomerQuotations(customerId: number) {
    return request.get<R<Record<string, unknown>[]>>(`/sales/customers/${customerId}/quotations`)
  },

  /** 导入客户数据 */
  importCustomers(data: FormData, updateSupport: boolean = false) {
    return request.post<R<void>>('/sales/customers/import', data, {
      params: { updateSupport },
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  /** 下载客户导入模板 */
  downloadCustomerTemplate() {
    return request.get('/sales/customers/importTemplate', {
      responseType: 'blob',
    })
  },

  /** 检查客户编码是否唯一 */
  checkCustomerCodeUnique(customerCode: string, customerId?: number) {
    return request.get<R<boolean>>('/sales/customers/checkCodeUnique', {
      params: { customerCode, customerId },
    })
  },

  /** 检查客户名称是否唯一 */
  checkCustomerNameUnique(customerName: string, customerId?: number) {
    return request.get<R<boolean>>('/sales/customers/checkNameUnique', {
      params: { customerName, customerId },
    })
  },
}
